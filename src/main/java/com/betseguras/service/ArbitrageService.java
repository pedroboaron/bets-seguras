package com.betseguras.service;

import com.betseguras.config.ArbitrageConfig;
import com.betseguras.domain.ArbitrageOpportunity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@Service
public class ArbitrageService {

    private static final Logger log = LoggerFactory.getLogger(ArbitrageService.class);

    private final OddsCollectorService oddsCollector;
    private final ArbitrageEngine engine;
    private final TelegramNotificationService telegram;
    private final ArbitrageConfig config;

    /** Cache de oportunidades dos jogos de HOJE. */
    private final CopyOnWriteArrayList<ArbitrageOpportunity> todayCache = new CopyOnWriteArrayList<>();

    /** Cache de oportunidades dos jogos FUTUROS (a partir de amanhã). */
    private final CopyOnWriteArrayList<ArbitrageOpportunity> futureCache = new CopyOnWriteArrayList<>();

    public ArbitrageService(OddsCollectorService oddsCollector,
                            ArbitrageEngine engine,
                            TelegramNotificationService telegram,
                            ArbitrageConfig config) {
        this.oddsCollector = oddsCollector;
        this.engine = engine;
        this.telegram = telegram;
        this.config = config;
    }

    // ── Acesso público ────────────────────────────────────────────────────────

    public Mono<List<ArbitrageOpportunity>> getOpportunitiesToday() {
        if (todayCache.isEmpty()) return refreshToday();
        return Mono.just(sorted(todayCache));
    }

    public Mono<List<ArbitrageOpportunity>> getOpportunitiesFuture() {
        if (futureCache.isEmpty()) return refreshFuture();
        return Mono.just(sorted(futureCache));
    }

    /** Força atualização completa (hoje + futuros) — usado pelo endpoint de refresh. */
    public Mono<List<ArbitrageOpportunity>> refreshAll() {
        return refreshToday().flatMap(today ->
                refreshFuture().map(future -> {
                    List<ArbitrageOpportunity> all = new ArrayList<>(today);
                    all.addAll(future);
                    return sorted(all);
                }));
    }

    // ── Refresh por contexto ──────────────────────────────────────────────────

    public Mono<List<ArbitrageOpportunity>> refreshToday() {
        return collectAndAnalyze(m -> isToday(m.getMatchDate()),
                todayCache, TelegramNotificationService.Contexto.HOJE);
    }

    public Mono<List<ArbitrageOpportunity>> refreshFuture() {
        return collectAndAnalyze(m -> isFuture(m.getMatchDate()),
                futureCache, TelegramNotificationService.Contexto.PROXIMOS);
    }

    /**
     * Coleta odds, filtra partidas pelo predicado, detecta arbitragem e:
     *   1. Envia alerta imediato para cada oportunidade encontrada.
     *   2. Atualiza o cache.
     *   3. Envia resumo final com o total do ciclo.
     */
    private Mono<List<ArbitrageOpportunity>> collectAndAnalyze(
            Predicate<com.betseguras.domain.Match> filter,
            CopyOnWriteArrayList<ArbitrageOpportunity> cache,
            TelegramNotificationService.Contexto contexto) {

        return oddsCollector.collectAndNormalize()
                .map(matches -> matches.stream()
                        .filter(filter)
                        .map(engine::analyze)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList())
                .flatMap(opps -> {
                    List<ArbitrageOpportunity> sorted = sorted(opps);
                    cache.clear();
                    cache.addAll(sorted);
                    log.info("{} refresh: {} oportunidades", contexto, sorted.size());

                    // 1. Alerta imediato para cada oportunidade (concatMap = em sequência)
                    return Flux.fromIterable(sorted)
                            .concatMap(opp -> telegram.notifyOpportunity(opp, contexto))
                            // 2. Resumo final
                            .then(telegram.notifySummary(sorted, contexto))
                            .thenReturn(sorted);
                });
    }

    // ── Agendamentos ──────────────────────────────────────────────────────────

    /** Jogos de hoje — intervalo curto (padrão 120 s). */
    @Scheduled(fixedDelayString = "#{${arbitrage.today-refresh-seconds:120} * 1000}")
    public void scheduledTodayRefresh() {
        refreshToday().block();
    }

    /** Jogos futuros — intervalo longo (padrão 3600 s). */
    @Scheduled(fixedDelayString = "#{${arbitrage.future-refresh-seconds:1200} * 1000}")
    public void scheduledFutureRefresh() {
        refreshFuture().block();
    }

    // ── Helpers de data ───────────────────────────────────────────────────────

    /**
     * Retorna true se o jogo é de hoje.
     *
     * Formatos reconhecidos:
     *   OddsAgora: "Hoje, 01 Abr"  → contém "hoje"
     *   The Odds API: "2026-04-01" → começa com a data ISO de hoje
     *   null                       → tratado como hoje (seguro para odds ao vivo)
     */
    private boolean isToday(String matchDate) {
        if (matchDate == null) return false; // data desconhecida → vai para futuros
        String lower = matchDate.toLowerCase();
        if (lower.contains("hoje")) return true;
        String todayIso = LocalDate.now().toString(); // "2026-04-02"
        return matchDate.startsWith(todayIso);
    }

    /**
     * Retorna true se o jogo é de amanhã em diante.
     */
    private boolean isFuture(String matchDate) {
        return !isToday(matchDate);
    }

    private List<ArbitrageOpportunity> sorted(List<ArbitrageOpportunity> list) {
        return list.stream()
                .sorted(Comparator.comparingDouble(ArbitrageOpportunity::profitPercentage).reversed())
                .toList();
    }
}
