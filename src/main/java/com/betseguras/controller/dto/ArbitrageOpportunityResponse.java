package com.betseguras.controller.dto;

import com.betseguras.domain.ArbitrageOpportunity;
import com.betseguras.service.BookmakerRegistry;

import java.util.List;

/**
 * Human-friendly response for a single arbitrage opportunity.
 */
public record ArbitrageOpportunityResponse(
        String jogo,
        String divisao,
        String data,
        double lucroPercentual,
        String lucroPercentualFormatado,
        double investimentoTotal,
        double retornoGarantido,
        double lucroAbsoluto,
        boolean todasCasasAceitamBrasileiros,
        boolean todasCasasLicenciadasBrasil,
        String resumo,
        List<BetInstruction> apostas
) {
    public static ArbitrageOpportunityResponse from(ArbitrageOpportunity opp, BookmakerRegistry registry) {
        List<BetInstruction> apostas = opp.stakes().stream()
                .map(s -> {
                    BookmakerRegistry.BookmakerInfo info = registry.findOrUnknown(s.bookmaker());
                    return new BetInstruction(
                            s.bookmaker(),
                            info.licenciadaBrasil(),
                            info.aceitaPix(),
                            info.aceitaBrasileiros(),
                            info.deposito(),
                            info.observacao(),
                            info.linkFutebol(),
                            translateOutcome(s.outcome()),
                            s.odd(),
                            s.stake());
                })
                .toList();

        boolean todasAceitamBR = apostas.stream().allMatch(BetInstruction::aceitaBrasileiros);
        boolean todasLicenciadasBR = apostas.stream().allMatch(BetInstruction::casaLicenciadaBrasil);

        String avisoAcesso = todasAceitamBR
                ? ""
                : " ⚠ Verifique disponibilidade: nem todas as casas aceitam brasileiros.";

        String resumo;
        if (opp.strategyType() == ArbitrageOpportunity.StrategyType.FAVORITE_SAFE) {
            resumo = String.format(
                    "Aposte R$ %.2f em %d casas. Lucro de %.2f%% se o favorito vencer; outros resultados devolvem o investimento.%s",
                    opp.totalInvestment(), apostas.size(), opp.profitPercentage(), avisoAcesso);
        } else {
            resumo = String.format(
                    "Aposte R$ %.2f em %d casas. Qualquer resultado garante R$ %.2f de lucro (%.2f%%).%s",
                    opp.totalInvestment(), apostas.size(),
                    opp.guaranteedProfit(), opp.profitPercentage(),
                    avisoAcesso);
        }

        return new ArbitrageOpportunityResponse(
                opp.homeTeam() + " x " + opp.awayTeam(),
                opp.competition(),
                opp.matchDate() != null ? opp.matchDate() : "A confirmar",
                opp.profitPercentage(),
                String.format("%.2f%%", opp.profitPercentage()),
                opp.totalInvestment(),
                opp.guaranteedReturn(),
                opp.guaranteedProfit(),
                todasAceitamBR,
                todasLicenciadasBR,
                resumo,
                apostas);
    }

    private static String translateOutcome(String outcome) {
        return switch (outcome) {
            case "HOME_WIN" -> "Vitória mandante";
            case "DRAW"     -> "Empate";
            case "AWAY_WIN" -> "Vitória visitante";
            default         -> outcome;
        };
    }
}
