package com.betseguras.service;

import com.betseguras.config.ArbitrageConfig;
import com.betseguras.domain.ArbitrageOpportunity;
import com.betseguras.domain.ArbitrageOpportunity.StrategyType;
import com.betseguras.domain.Match;
import com.betseguras.domain.Odds;
import com.betseguras.domain.StakeDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Core arbitrage detection and stake calculation engine.
 *
 * Cálculo do investimento máximo por saldo:
 *   Para cada outcome k com bookmaker b_k, odd o_k e saldo bal_k:
 *     stake_k = investimento / (o_k × sum)  ≤  bal_k
 *     → investimento  ≤  bal_k × o_k × sum
 *   Logo: investimento_max = min(bal_k × o_k × sum)
 */
@Service
public class ArbitrageEngine {

    private static final Logger log = LoggerFactory.getLogger(ArbitrageEngine.class);

    private final ArbitrageConfig config;
    private final BookmakerRegistry registry;

    public ArbitrageEngine(ArbitrageConfig config, BookmakerRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public java.util.Optional<ArbitrageOpportunity> analyze(Match match) {
        List<Odds> oddsList = match.getOddsPerBookmaker();
        if (oddsList == null || oddsList.isEmpty()) return java.util.Optional.empty();

        if (oddsList.size() < 2) return java.util.Optional.empty();

        Odds bestHome = oddsList.stream().max(Comparator.comparingDouble(Odds::homeWin)).orElseThrow();
        Odds bestDraw = oddsList.stream().max(Comparator.comparingDouble(Odds::draw)).orElseThrow();
        Odds bestAway = oddsList.stream().max(Comparator.comparingDouble(Odds::awayWin)).orElseThrow();

        double oddA = bestHome.homeWin();
        double oddX = bestDraw.draw();
        double oddB = bestAway.awayWin();

        double sum = (1.0 / oddA) + (1.0 / oddX) + (1.0 / oddB);

        if (sum >= 1.0) {
            log.debug("No surebet for {}: sum={} — trying favorite-safe", match.getMatchKey(), sum);
            return tryFavoriteSafe(match, bestHome, bestDraw, bestAway, oddA, oddX, oddB);
        }

        double profitPct = ((1.0 / sum) - 1.0) * 100.0;

        if (profitPct < config.getMinProfitPercentage()) {
            log.debug("Profit {}% below minimum for {}", profitPct, match.getMatchKey());
            return java.util.Optional.empty();
        }

        double wA = (1.0 / oddA) / sum, wX = (1.0 / oddX) / sum, wB = (1.0 / oddB) / sum;
        double investment = calculateInvestment(bestHome.bookmaker(), wA, bestDraw.bookmaker(), wX, bestAway.bookmaker(), wB);

        double stakeA = roundToNatural((investment / oddA) / sum);
        double stakeX = roundToNatural((investment / oddX) / sum);
        double stakeB = roundToNatural((investment / oddB) / sum);
        double totalInvestment = stakeA + stakeX + stakeB;
        double guaranteedReturn = Math.min(stakeA * oddA, Math.min(stakeX * oddX, stakeB * oddB));
        double profit = guaranteedReturn - totalInvestment;

        List<StakeDistribution> stakes = new ArrayList<>();
        stakes.add(StakeDistribution.builder().bookmaker(bestHome.bookmaker()).outcome("HOME_WIN").odd(oddA).stake(round2(stakeA)).build());
        stakes.add(StakeDistribution.builder().bookmaker(bestDraw.bookmaker()).outcome("DRAW").odd(oddX).stake(round2(stakeX)).build());
        stakes.add(StakeDistribution.builder().bookmaker(bestAway.bookmaker()).outcome("AWAY_WIN").odd(oddB).stake(round2(stakeB)).build());

        log.info("Surebet: {} vs {} — {}% | investimento R$ {} ({})",
                match.getHomeTeam(), match.getAwayTeam(),
                round2(profitPct), round2(totalInvestment),
                config.getBalances().isEmpty() ? "global" : "por saldo");

        return java.util.Optional.of(ArbitrageOpportunity.builder()
                .matchKey(match.getMatchKey())
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .competition(match.getCompetition())
                .matchDate(match.getMatchDate())
                .oddHomeWin(oddA).bookmakerHomeWin(bestHome.bookmaker())
                .oddDraw(oddX).bookmakerDraw(bestDraw.bookmaker())
                .oddAwayWin(oddB).bookmakerAwayWin(bestAway.bookmaker())
                .arbitrageSum(round4(sum))
                .profitPercentage(round2(profitPct))
                .totalInvestment(round2(totalInvestment))
                .guaranteedReturn(round2(guaranteedReturn))
                .guaranteedProfit(round2(profit))
                .strategyType(StrategyType.SUREBET)
                .stakes(stakes)
                .detectedAt(System.currentTimeMillis())
                .build());
    }

    /**
     * Detecta oportunidade FAVORITE_SAFE:
     *   — não-favoritos: stake = investment / odd  (break-even exato)
     *   — favorito (menor odd): fica com o restante
     *   — condição: o favorito deve render lucro real
     */
    private java.util.Optional<ArbitrageOpportunity> tryFavoriteSafe(
            Match match, Odds bestHome, Odds bestDraw, Odds bestAway,
            double oddA, double oddX, double oddB) {

        // Identifica o favorito (menor odd = mais provável)
        double minOdd = Math.min(oddA, Math.min(oddX, oddB));

        String favOutcome, bmFav, bmOth1, bmOth2;
        double oddFav, oddOth1, oddOth2;

        if (minOdd == oddA) {
            favOutcome = "HOME_WIN"; oddFav = oddA; bmFav = bestHome.bookmaker();
            oddOth1 = oddX; bmOth1 = bestDraw.bookmaker();
            oddOth2 = oddB; bmOth2 = bestAway.bookmaker();
        } else if (minOdd == oddX) {
            favOutcome = "DRAW"; oddFav = oddX; bmFav = bestDraw.bookmaker();
            oddOth1 = oddA; bmOth1 = bestHome.bookmaker();
            oddOth2 = oddB; bmOth2 = bestAway.bookmaker();
        } else {
            favOutcome = "AWAY_WIN"; oddFav = oddB; bmFav = bestAway.bookmaker();
            oddOth1 = oddA; bmOth1 = bestHome.bookmaker();
            oddOth2 = oddX; bmOth2 = bestDraw.bookmaker();
        }

        double sumOthers = 1.0 / oddOth1 + 1.0 / oddOth2;
        if (sumOthers >= 1.0) return java.util.Optional.empty();

        double wFav = 1.0 - sumOthers;   // fração do investimento que vai ao favorito
        double favPayout = wFav * oddFav; // retorno por unidade investida se favorito vencer
        if (favPayout <= 1.0) return java.util.Optional.empty();

        double profitPct = (favPayout - 1.0) * 100.0;
        if (profitPct < config.getMinProfitPercentage()) return java.util.Optional.empty();

        double investment = calculateInvestment(bmFav, wFav, bmOth1, 1.0 / oddOth1, bmOth2, 1.0 / oddOth2);

        double stakeFav  = roundToNatural(investment * wFav);
        double stakeOth1 = roundToNatural(investment / oddOth1);
        double stakeOth2 = roundToNatural(investment / oddOth2);
        double totalInvestment = stakeFav + stakeOth1 + stakeOth2;

        // Retornos por outcome após arredondamento
        double retFav  = stakeFav * oddFav;
        double retOth1 = stakeOth1 * oddOth1;
        double retOth2 = stakeOth2 * oddOth2;

        // Valida que não há prejuízo em nenhum outcome
        if (retOth1 < totalInvestment || retOth2 < totalInvestment) return java.util.Optional.empty();

        double profit = retFav - totalInvestment;

        // Monta stakes na ordem HOME_WIN / DRAW / AWAY_WIN
        List<StakeDistribution> stakes = new ArrayList<>();
        if ("HOME_WIN".equals(favOutcome)) {
            stakes.add(StakeDistribution.builder().bookmaker(bmFav) .outcome("HOME_WIN").odd(oddFav) .stake(stakeFav) .build());
            stakes.add(StakeDistribution.builder().bookmaker(bmOth1).outcome("DRAW")    .odd(oddOth1).stake(stakeOth1).build());
            stakes.add(StakeDistribution.builder().bookmaker(bmOth2).outcome("AWAY_WIN").odd(oddOth2).stake(stakeOth2).build());
        } else if ("DRAW".equals(favOutcome)) {
            stakes.add(StakeDistribution.builder().bookmaker(bmOth1).outcome("HOME_WIN").odd(oddOth1).stake(stakeOth1).build());
            stakes.add(StakeDistribution.builder().bookmaker(bmFav) .outcome("DRAW")    .odd(oddFav) .stake(stakeFav) .build());
            stakes.add(StakeDistribution.builder().bookmaker(bmOth2).outcome("AWAY_WIN").odd(oddOth2).stake(stakeOth2).build());
        } else {
            stakes.add(StakeDistribution.builder().bookmaker(bmOth1).outcome("HOME_WIN").odd(oddOth1).stake(stakeOth1).build());
            stakes.add(StakeDistribution.builder().bookmaker(bmOth2).outcome("DRAW")    .odd(oddOth2).stake(stakeOth2).build());
            stakes.add(StakeDistribution.builder().bookmaker(bmFav) .outcome("AWAY_WIN").odd(oddFav) .stake(stakeFav) .build());
        }

        log.info("Favorito-safe: {} vs {} — {}% se {} | investimento R$ {}",
                match.getHomeTeam(), match.getAwayTeam(),
                round2(profitPct), favOutcome, round2(totalInvestment));

        return java.util.Optional.of(ArbitrageOpportunity.builder()
                .matchKey(match.getMatchKey())
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .competition(match.getCompetition())
                .matchDate(match.getMatchDate())
                .oddHomeWin(oddA).bookmakerHomeWin(bestHome.bookmaker())
                .oddDraw(oddX).bookmakerDraw(bestDraw.bookmaker())
                .oddAwayWin(oddB).bookmakerAwayWin(bestAway.bookmaker())
                .arbitrageSum(round4(1.0 / oddOth1 + 1.0 / oddOth2 + wFav))
                .profitPercentage(round2(profitPct))
                .totalInvestment(round2(totalInvestment))
                .guaranteedReturn(round2(totalInvestment))   // pior caso = empate
                .guaranteedProfit(0.0)
                .strategyType(StrategyType.FAVORITE_SAFE)
                .stakes(stakes)
                .detectedAt(System.currentTimeMillis())
                .build());
    }

    /**
     * Calcula o investimento máximo respeitando saldos configurados por casa.
     * weight_i = fração do investimento total destinada à casa i.
     * Se saldo configurado: investment ≤ balance_i / weight_i.
     */
    private double calculateInvestment(
            String bm1, double w1,
            String bm2, double w2,
            String bm3, double w3) {

        double cap = config.getMaxInvestment();
        OptionalDouble bal1 = config.balanceFor(bm1);
        OptionalDouble bal2 = config.balanceFor(bm2);
        OptionalDouble bal3 = config.balanceFor(bm3);
        if (bal1.isPresent()) cap = Math.min(cap, bal1.getAsDouble() / w1);
        if (bal2.isPresent()) cap = Math.min(cap, bal2.getAsDouble() / w2);
        if (bal3.isPresent()) cap = Math.min(cap, bal3.getAsDouble() / w3);
        return cap;
    }

    /**
     * Arredonda para múltiplo de 50 (preferido) ou 10 (fallback),
     * sempre para baixo, para parecer aposta humana e dificultar detecção.
     */
    private double roundToNatural(double stake) {
        double rounded50 = Math.floor(stake / 50.0) * 50.0;
        if (rounded50 >= 1.0) {
            return rounded50;
        }
        return Math.floor(stake / 10.0) * 10.0;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
