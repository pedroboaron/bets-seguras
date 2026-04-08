package com.betseguras.domain;

/**
 * Odds for a 1X2 market from a single bookmaker.
 */
public record Odds(
        String bookmaker,
        double homeWin,
        double draw,
        double awayWin,
        long collectedAt
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String bookmaker;
        private double homeWin;
        private double draw;
        private double awayWin;
        private long collectedAt;

        public Builder bookmaker(String v)    { this.bookmaker = v; return this; }
        public Builder homeWin(double v)      { this.homeWin = v; return this; }
        public Builder draw(double v)         { this.draw = v; return this; }
        public Builder awayWin(double v)      { this.awayWin = v; return this; }
        public Builder collectedAt(long v)    { this.collectedAt = v; return this; }
        public Odds build() { return new Odds(bookmaker, homeWin, draw, awayWin, collectedAt); }
    }
}
