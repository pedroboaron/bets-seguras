package com.betseguras.integration.oddsagora;

import com.betseguras.config.OddsAgoraProperties;
import com.betseguras.domain.Match;
import com.betseguras.integration.OddsProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Coleta odds de casas de apostas brasileiras via OddsAgora.com.br.
 *
 * Funciona em duas fases:
 *   Fase 1 — Scraping sequencial das páginas de competição:
 *             extrai lista de MatchInfo (times, data, path h2h).
 *   Fase 2 — Scraping paralelo das páginas h2h individuais:
 *             para cada MatchInfo, busca odds por casa para jogo todo / 1T / 2T.
 *
 * Estrutura HTML relevante (após renderização JS, fase 1):
 *   div.eventRow                         → um jogo
 *     [data-testid="event-participants"]
 *       .participant-name (×2)           → nome dos times (casa e fora)
 *     a[href*="/h2h/"]                   → URL do jogo
 *     [data-testid="date-header"]        → data do jogo
 */
@Component
@ConditionalOnProperty(name = "oddsagora.enabled", havingValue = "true")
public class OddsAgoraBrazilProvider implements OddsProvider {

    private static final Logger log = LoggerFactory.getLogger(OddsAgoraBrazilProvider.class);

    private static final Map<String, String> COMPETITION_NAMES = Map.of(
            "brasileirao-betano",          "Brasileirão Série A",
            "brasileirao-serie-b-superbet","Brasileirão Série B",
            "copa-betano-do-brasil",       "Copa do Brasil",
            "serie-c",                     "Brasileirão Série C"
    );

    private final OddsAgoraProperties props;
    private final PlaywrightPageFetcher fetcher;
    private final OddsAgoraMatchScraper matchScraper;

    public OddsAgoraBrazilProvider(OddsAgoraProperties props,
                                   PlaywrightPageFetcher fetcher,
                                   OddsAgoraMatchScraper matchScraper) {
        this.props        = props;
        this.fetcher      = fetcher;
        this.matchScraper = matchScraper;
    }

    @Override
    public String getName() {
        return "OddsAgora";
    }

    @Override
    public Flux<Match> fetchOdds() {
        // Fase 1: percorre competições sequencialmente e extrai MatchInfo de cada uma
        Flux<MatchInfo> matchInfoFlux = Flux.fromIterable(props.getCompetitions())
                .concatMap(slug ->
                        Mono.fromCallable(() -> discoverMatches(slug))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMapMany(Flux::fromIterable)
                                .onErrorResume(e -> {
                                    log.warn("OddsAgora: falha ao coletar '{}': {}", slug, e.getMessage());
                                    return Flux.empty();
                                })
                );

        // Fase 2: para cada jogo, scraping paralelo da página h2h (jogo todo + 1T + 2T)
        return matchInfoFlux
                .flatMap(info ->
                        Mono.fromCallable(() -> matchScraper.scrapeMatch(info))
                                .subscribeOn(matchScraper.scheduler())
                                .flatMapMany(Flux::fromIterable)
                                .onErrorResume(e -> {
                                    log.warn("OddsAgora h2h: falha em {} vs {}: {}",
                                            info.homeTeam(), info.awayTeam(), e.getMessage());
                                    return Flux.empty();
                                }),
                        props.getMatchParallelism()
                );
    }

    // ── Fase 1: descoberta de jogos ───────────────────────────────────────────

    private List<MatchInfo> discoverMatches(String slug) {
        String url = props.getBaseUrl() + "/football/brazil/" + slug + "/";
        log.info("OddsAgora scraping (fase 1): {}", url);

        String html = fetcher.fetchRenderedHtml(url);
        List<MatchInfo> matches = parseMatchInfos(html, slug);

        log.info("OddsAgora '{}': {} jogos encontrados para scraping h2h", slug, matches.size());
        return matches;
    }

    /**
     * Itera date-headers e eventRows na ordem em que aparecem no DOM.
     * O date-header é irmão do eventRow (não filho), então não pode ser encontrado
     * com selectFirst dentro do row — precisa ser propagado conforme os elementos avançam.
     *
     * Estrutura na página:
     *   [data-testid="date-header"]  → "Hoje, 02 Abr"
     *   div.eventRow                 → jogo 1  (herda a data acima)
     *   div.eventRow                 → jogo 2
     *   [data-testid="date-header"]  → "11 Abr 2026"
     *   div.eventRow                 → jogo 3  (herda a nova data)
     */
    private List<MatchInfo> parseMatchInfos(String html, String slug) {
        Document doc = Jsoup.parse(html);
        List<MatchInfo> result = new ArrayList<>();

        String competitionName = COMPETITION_NAMES.getOrDefault(slug, toDisplayName(slug));

        // Seleciona date-headers e eventRows juntos em ordem de DOM
        Elements mixed = doc.select("[data-testid='date-header'], div.eventRow");
        if (mixed.isEmpty()) {
            log.warn("OddsAgora: nenhum eventRow encontrado em '{}' — estrutura pode ter mudado", slug);
            return result;
        }

        String currentDate = null;
        for (Element el : mixed) {
            if ("date-header".equals(el.attr("data-testid"))) {
                currentDate = el.text().trim();
                log.debug("OddsAgora: data atual = '{}'", currentDate);
            } else {
                try {
                    MatchInfo info = parseMatchInfo(el, competitionName, currentDate);
                    if (info != null) result.add(info);
                } catch (Exception e) {
                    log.debug("OddsAgora: erro ao parsear eventRow — {}", e.getMessage());
                }
            }
        }
        return result;
    }

    private MatchInfo parseMatchInfo(Element row, String competitionName, String matchDate) {
        // Times
        Elements teamEls = row.select("[data-testid='event-participants'] .participant-name");
        if (teamEls.size() < 2) return null;

        String homeTeam = teamEls.get(0).text().trim();
        String awayTeam = teamEls.get(1).text().trim();
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) return null;

        // URL do jogo (path relativo para a página h2h)
        Element matchLink = row.selectFirst("a[href*='/h2h/']");
        if (matchLink == null) {
            log.debug("OddsAgora: {} vs {} — sem link h2h, ignorando", homeTeam, awayTeam);
            return null;
        }
        String matchPath = matchLink.attr("href");

        log.debug("OddsAgora fase 1: {} vs {} | data='{}' path={}", homeTeam, awayTeam, matchDate, matchPath);
        return new MatchInfo(homeTeam, awayTeam, competitionName, matchDate, matchPath);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String toDisplayName(String slug) {
        return slug.replace('-', ' ')
                .substring(0, 1).toUpperCase()
                + slug.replace('-', ' ').substring(1);
    }
}
