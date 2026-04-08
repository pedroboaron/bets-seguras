package com.betseguras.domain;

/**
 * How much to bet on a single outcome at a given bookmaker.
 */
public record StakeDistribution(
        String bookmaker,
        String outcome,   // "HOME_WIN", "DRAW", "AWAY_WIN"
        double odd,
        double stake      // amount in BRL
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String bookmaker;
        private String outcome;
        private double odd;
        private double stake;

        public Builder bookmaker(String v) { this.bookmaker = v; return this; }
        public Builder outcome(String v)   { this.outcome = v; return this; }
        public Builder odd(double v)       { this.odd = v; return this; }
        public Builder stake(double v)     { this.stake = v; return this; }
        public StakeDistribution build()   { return new StakeDistribution(bookmaker, outcome, odd, stake); }
    }
}
