package com.betseguras.domain;

import java.util.List;

/**
 * A detected arbitrage opportunity with stake distribution and profit metrics.
 */
public record ArbitrageOpportunity(
        String matchKey,
        String homeTeam,
        String awayTeam,
        String competition,
        String matchDate,

        double oddHomeWin,
        String bookmakerHomeWin,
        double oddDraw,
        String bookmakerDraw,
        double oddAwayWin,
        String bookmakerAwayWin,

        double arbitrageSum,        // < 1.0 for SUREBET; may be >= 1.0 for FAVORITE_SAFE
        double profitPercentage,    // e.g. 2.5 means 2.5% — for FAVORITE_SAFE: profit when favorite wins
        double totalInvestment,
        double guaranteedReturn,    // for FAVORITE_SAFE equals totalInvestment (break-even worst case)
        double guaranteedProfit,    // for FAVORITE_SAFE is 0 (no guaranteed profit, only no loss)

        StrategyType strategyType,
        List<StakeDistribution> stakes,
        long detectedAt
) {
    public enum StrategyType {
        /** All outcomes yield profit. */
        SUREBET,
        /** Only the most probable outcome yields profit; others break even. */
        FAVORITE_SAFE
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String matchKey, homeTeam, awayTeam, competition, matchDate;
        private double oddHomeWin, oddDraw, oddAwayWin;
        private String bookmakerHomeWin, bookmakerDraw, bookmakerAwayWin;
        private double arbitrageSum, profitPercentage, totalInvestment, guaranteedReturn, guaranteedProfit;
        private StrategyType strategyType = StrategyType.SUREBET;
        private List<StakeDistribution> stakes;
        private long detectedAt;

        public Builder matchKey(String v)             { matchKey = v; return this; }
        public Builder homeTeam(String v)             { homeTeam = v; return this; }
        public Builder awayTeam(String v)             { awayTeam = v; return this; }
        public Builder competition(String v)          { competition = v; return this; }
        public Builder matchDate(String v)            { matchDate = v; return this; }
        public Builder oddHomeWin(double v)           { oddHomeWin = v; return this; }
        public Builder bookmakerHomeWin(String v)     { bookmakerHomeWin = v; return this; }
        public Builder oddDraw(double v)              { oddDraw = v; return this; }
        public Builder bookmakerDraw(String v)        { bookmakerDraw = v; return this; }
        public Builder oddAwayWin(double v)           { oddAwayWin = v; return this; }
        public Builder bookmakerAwayWin(String v)     { bookmakerAwayWin = v; return this; }
        public Builder arbitrageSum(double v)         { arbitrageSum = v; return this; }
        public Builder profitPercentage(double v)     { profitPercentage = v; return this; }
        public Builder totalInvestment(double v)      { totalInvestment = v; return this; }
        public Builder guaranteedReturn(double v)     { guaranteedReturn = v; return this; }
        public Builder guaranteedProfit(double v)     { guaranteedProfit = v; return this; }
        public Builder strategyType(StrategyType v)   { strategyType = v; return this; }
        public Builder stakes(List<StakeDistribution> v) { stakes = v; return this; }
        public Builder detectedAt(long v)             { detectedAt = v; return this; }

        public ArbitrageOpportunity build() {
            return new ArbitrageOpportunity(matchKey, homeTeam, awayTeam, competition, matchDate,
                    oddHomeWin, bookmakerHomeWin, oddDraw, bookmakerDraw, oddAwayWin, bookmakerAwayWin,
                    arbitrageSum, profitPercentage, totalInvestment, guaranteedReturn, guaranteedProfit,
                    strategyType, stakes, detectedAt);
        }
    }
}
