package com.betseguras.service;

import com.betseguras.config.NotificationProperties;
import com.betseguras.domain.ArbitrageOpportunity;
import com.betseguras.domain.ArbitrageOpportunity.StrategyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final String TELEGRAM_API = "https://api.telegram.org";

    private final NotificationProperties props;
    private final WebClient webClient;
    private final BookmakerRegistry registry;

    public TelegramNotificationService(NotificationProperties props,
                                       WebClient.Builder builder,
                                       BookmakerRegistry registry) {
        this.props = props;
        this.webClient = builder.baseUrl(TELEGRAM_API).build();
        this.registry = registry;
    }

    public enum Contexto { HOJE, PROXIMOS }

    /**
     * Envia alerta imediato para uma única oportunidade encontrada.
     * Chamado assim que cada oportunidade é detectada durante o ciclo de coleta.
     */
    public Mono<Void> notifyOpportunity(ArbitrageOpportunity opp, Contexto contexto) {
        NotificationProperties.Telegram cfg = props.getTelegram();
        if (!cfg.isEnabled() || cfg.getBotToken().isBlank() || cfg.getChatIds().isEmpty()) {
            return Mono.empty();
        }
        String message = buildAlertMessage(opp, contexto);
        return broadcast(cfg, message);
    }

    /**
     * Envia resumo ao final do ciclo com o total de oportunidades encontradas.
     * Enviado mesmo quando não há oportunidades (a menos que only-when-found=true).
     */
    public Mono<Void> notifySummary(List<ArbitrageOpportunity> opportunities, Contexto contexto) {
        NotificationProperties.Telegram cfg = props.getTelegram();
        if (!cfg.isEnabled() || cfg.getBotToken().isBlank() || cfg.getChatIds().isEmpty()) {
            return Mono.empty();
        }
        if (cfg.isOnlyWhenFound() && opportunities.isEmpty()) {
            log.debug("Sem oportunidades — resumo Telegram ignorado.");
            return Mono.empty();
        }
        String message = buildSummaryMessage(opportunities, contexto);
        return broadcast(cfg, message);
    }

    // ── Formatação ────────────────────────────────────────────────────────────

    /** Linha de stake por outcome dentro de um cenário. */
    private record StakeRow(String bookmaker, String outcome, double odd, double stake) {}
    /** Cenário de apostas: conjunto de stakes e total de investimento. */
    private record Scenario(double total, List<StakeRow> rows) {}

    /** Alerta imediato: mensagem compacta para uma única oportunidade. */
    private String buildAlertMessage(ArbitrageOpportunity opp, Contexto contexto) {
        String scope = contexto == Contexto.HOJE ? "hoje" : "próximos dias";
        StringBuilder sb = new StringBuilder();

        boolean isFavSafe = opp.strategyType() == StrategyType.FAVORITE_SAFE;
        String header = isFavSafe ? "⚡ <b>Favorito seguro! (%s)</b>" : "🚨 <b>Surebet encontrada! (%s)</b>";
        sb.append(String.format(header + "\n\n", scope));

        sb.append(String.format("<b>%s x %s</b>\n", opp.homeTeam(), opp.awayTeam()));
        sb.append(String.format("🏆 %s%s\n",
                opp.competition(),
                opp.matchDate() != null ? " · " + opp.matchDate() : ""));

        if (isFavSafe) {
            String favLabel = favOutcomeLabel(opp);
            sb.append(String.format("💰 Lucro se <b>%s</b>: <b>+%.2f%%</b>\n\n", favLabel, opp.profitPercentage()));
        } else {
            sb.append(String.format("💹 <b>+%.2f%%</b> garantido em qualquer resultado\n\n", opp.profitPercentage()));
        }

        Scenario base1000 = buildBase1000Scenario(opp);
        Scenario rounded  = buildRoundedScenario(opp);

        sb.append("━━ <b>R$ 1.000 exato</b> ━━\n");
        appendScenarioRows(sb, base1000, opp);

        sb.append(String.format("\n━━ <b>Mínimo garantido — R$ %.0f</b> ━━\n", rounded.total()));
        appendScenarioRows(sb, rounded, opp);

        boolean temNaoLicenciada = opp.stakes().stream()
                .anyMatch(s -> !registry.findOrUnknown(s.bookmaker()).licenciadaBrasil());
        if (temNaoLicenciada) {
            sb.append("\n<i>⚠️ Uma ou mais casas não possuem licença SPA/Brasil. Verifique antes de apostar.</i>");
        }

        return sb.toString();
    }

    private void appendScenarioRows(StringBuilder sb, Scenario scenario, ArbitrageOpportunity opp) {
        boolean isFavSafe = opp.strategyType() == StrategyType.FAVORITE_SAFE;
        double favOdd = Math.min(opp.oddHomeWin(), Math.min(opp.oddDraw(), opp.oddAwayWin()));

        for (StakeRow row : scenario.rows()) {
            String label = switch (row.outcome()) {
                case "HOME_WIN" -> "1";
                case "DRAW"     -> "X";
                case "AWAY_WIN" -> "2";
                default         -> row.outcome();
            };
            BookmakerRegistry.BookmakerInfo info = registry.findOrUnknown(row.bookmaker());
            String badge = info.licenciadaBrasil() ? "✅" : "⚠️";
            String casaLink = (info.linkFutebol() != null)
                    ? String.format("<a href=\"%s\">%s</a>", info.linkFutebol(), row.bookmaker())
                    : row.bookmaker();

            double payout = row.stake() * row.odd();
            double result = payout - scenario.total();
            String resultStr;
            if (isFavSafe && row.odd() != favOdd) {
                // não-favorito: esperado empate
                resultStr = result >= 0
                        ? String.format("empate +R$ %.0f", result)
                        : String.format("⚠ -R$ %.0f", Math.abs(result));
            } else {
                resultStr = result >= 0
                        ? String.format("+R$ %.0f", result)
                        : String.format("⚠ -R$ %.0f", Math.abs(result));
            }

            sb.append(String.format("  %s %s %s @%.2f → R$ %.0f | retorno R$ %.0f (<b>%s</b>)\n",
                    label, casaLink, badge, row.odd(), row.stake(), payout, resultStr));
        }
    }

    // ── Cálculo de cenários ───────────────────────────────────────────────────

    /**
     * Pesos de cada outcome conforme a estratégia:
     * SUREBET: proporcional a 1/odd (payouts iguais).
     * FAVORITE_SAFE: não-favoritos em break-even exato, favorito leva o restante.
     */
    private double[] computeWeights(ArbitrageOpportunity opp) {
        double oddA = opp.oddHomeWin(), oddX = opp.oddDraw(), oddB = opp.oddAwayWin();
        if (opp.strategyType() == StrategyType.SUREBET) {
            double sum = 1.0 / oddA + 1.0 / oddX + 1.0 / oddB;
            return new double[]{ (1.0 / oddA) / sum, (1.0 / oddX) / sum, (1.0 / oddB) / sum };
        }
        // FAVORITE_SAFE: identifica favorito (menor odd)
        double minOdd = Math.min(oddA, Math.min(oddX, oddB));
        double wA, wX, wB;
        if (minOdd == oddA) {
            wX = 1.0 / oddX; wB = 1.0 / oddB; wA = 1.0 - wX - wB;
        } else if (minOdd == oddX) {
            wA = 1.0 / oddA; wB = 1.0 / oddB; wX = 1.0 - wA - wB;
        } else {
            wA = 1.0 / oddA; wX = 1.0 / oddX; wB = 1.0 - wA - wX;
        }
        return new double[]{ wA, wX, wB };
    }

    private Scenario buildBase1000Scenario(ArbitrageOpportunity opp) {
        double[] w = computeWeights(opp);
        double[] odds = { opp.oddHomeWin(), opp.oddDraw(), opp.oddAwayWin() };
        String[] bookmakers = { opp.bookmakerHomeWin(), opp.bookmakerDraw(), opp.bookmakerAwayWin() };
        String[] outcomes   = { "HOME_WIN", "DRAW", "AWAY_WIN" };
        List<StakeRow> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(new StakeRow(bookmakers[i], outcomes[i], odds[i], w[i] * 1000.0));
        }
        return new Scenario(1000.0, rows);
    }

    /**
     * Encontra o menor total (múltiplo de `unit`) onde nenhum outcome gera prejuízo.
     *
     * Algoritmo: itera sumN (total em unidades). Para SUREBET, calcula n_i = ceil(sumN/odd_i).
     * Se sum(n_i) == sumN, chegamos ao ponto fixo mínimo onde cada aposta × odd ≥ total.
     * Isso é matematicamente garantido sem perdas — ao contrário de round() que pode arredondar
     * para baixo e criar déficit.
     */
    private Scenario buildRoundedScenario(ArbitrageOpportunity opp) {
        double[] odds = { opp.oddHomeWin(), opp.oddDraw(), opp.oddAwayWin() };
        String[] bookmakers = { opp.bookmakerHomeWin(), opp.bookmakerDraw(), opp.bookmakerAwayWin() };
        String[] outcomes   = { "HOME_WIN", "DRAW", "AWAY_WIN" };

        for (int unit : new int[]{ 50, 10 }) {
            double[] stakes = opp.strategyType() == StrategyType.FAVORITE_SAFE
                    ? findMinRoundedFavSafe(odds, unit, favIdxOf(opp))
                    : findMinRoundedSurebet(odds, unit);
            if (stakes != null) {
                double total = Arrays.stream(stakes).sum();
                List<StakeRow> rows = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    rows.add(new StakeRow(bookmakers[i], outcomes[i], odds[i], stakes[i]));
                }
                return new Scenario(total, rows);
            }
        }
        return buildBase1000Scenario(opp);
    }

    /**
     * SUREBET: menor sumN onde ceil(sumN/odd_i) para cada i somam exatamente sumN.
     * Nesse ponto: n_i × odd_i ≥ sumN para todo i → nenhuma perna com prejuízo.
     */
    private double[] findMinRoundedSurebet(double[] odds, int unit) {
        for (int sumN = odds.length; sumN <= 10000; sumN++) {
            long check = 0;
            long[] n = new long[odds.length];
            for (int i = 0; i < odds.length; i++) {
                n[i] = (long) Math.ceil((double) sumN / odds[i]);
                check += n[i];
            }
            if (check == sumN) {
                double[] stakes = new double[odds.length];
                for (int i = 0; i < odds.length; i++) stakes[i] = n[i] * unit;
                return stakes;
            }
        }
        return null;
    }

    /**
     * FAVORITE_SAFE: não-favoritos recebem ceil(sumN/odd_i) (break-even garantido),
     * favorito fica com o restante. Aceito quando favorito ainda dá lucro real.
     */
    private double[] findMinRoundedFavSafe(double[] odds, int unit, int favIdx) {
        for (int sumN = odds.length; sumN <= 10000; sumN++) {
            long[] n = new long[odds.length];
            long othersSum = 0;
            for (int i = 0; i < odds.length; i++) {
                if (i != favIdx) {
                    n[i] = (long) Math.ceil((double) sumN / odds[i]);
                    othersSum += n[i];
                }
            }
            if (othersSum >= sumN) continue;
            n[favIdx] = sumN - othersSum;
            if (n[favIdx] * odds[favIdx] > sumN) {
                double[] stakes = new double[odds.length];
                for (int i = 0; i < odds.length; i++) stakes[i] = n[i] * unit;
                return stakes;
            }
        }
        return null;
    }

    private int favIdxOf(ArbitrageOpportunity opp) {
        double minOdd = Math.min(opp.oddHomeWin(), Math.min(opp.oddDraw(), opp.oddAwayWin()));
        if (opp.oddHomeWin() == minOdd) return 0;
        if (opp.oddDraw()    == minOdd) return 1;
        return 2;
    }

    /** Rótulo legível do outcome favorito (o de menor odd). */
    private String favOutcomeLabel(ArbitrageOpportunity opp) {
        double minOdd = Math.min(opp.oddHomeWin(), Math.min(opp.oddDraw(), opp.oddAwayWin()));
        if (minOdd == opp.oddHomeWin()) return opp.homeTeam() + " vencer";
        if (minOdd == opp.oddDraw())    return "empate";
        return opp.awayTeam() + " vencer";
    }

    /** Resumo final do ciclo. */
    private String buildSummaryMessage(List<ArbitrageOpportunity> opportunities, Contexto contexto) {
        String scope = contexto == Contexto.HOJE ? "hoje" : "próximos dias";
        if (opportunities.isEmpty()) {
            return String.format("🔍 <b>Ciclo encerrado (%s)</b> — nenhuma oportunidade encontrada.", scope);
        }
        return String.format(
                "📊 <b>Ciclo encerrado (%s)</b> — %d oportunidade(s) enviada(s) acima.",
                scope, opportunities.size());
    }

    // ── Envio ─────────────────────────────────────────────────────────────────

    private Mono<Void> broadcast(NotificationProperties.Telegram cfg, String message) {
        return Flux.fromIterable(cfg.getChatIds())
                .concatMap(chatId -> sendMessage(cfg.getBotToken(), chatId, message))
                .then();
    }

    private Mono<Void> sendMessage(String token, String chatId, String text) {
        return webClient.post()
                .uri("/bot{token}/sendMessage", token)
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "parse_mode", "HTML",
                        "disable_web_page_preview", true
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("Telegram enviado para chat {}", chatId))
                .doOnError(e -> log.error("Erro ao enviar Telegram para chat {}: {}", chatId, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
