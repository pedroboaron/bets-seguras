package com.betseguras.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A football match with odds collected from one or more bookmakers.
 * Mutable to allow merging odds from multiple providers.
 */
public class Match {
    private final String matchKey;
    private final String homeTeam;
    private final String awayTeam;
    private final String competition;
    private final String matchDate;
    private final List<Odds> oddsPerBookmaker;

    private Match(Builder b) {
        this.matchKey = b.matchKey;
        this.homeTeam = b.homeTeam;
        this.awayTeam = b.awayTeam;
        this.competition = b.competition;
        this.matchDate = b.matchDate;
        this.oddsPerBookmaker = b.oddsPerBookmaker != null ? new ArrayList<>(b.oddsPerBookmaker) : new ArrayList<>();
    }

    public String getMatchKey()                  { return matchKey; }
    public String getHomeTeam()                  { return homeTeam; }
    public String getAwayTeam()                  { return awayTeam; }
    public String getCompetition()               { return competition; }
    public String getMatchDate()                 { return matchDate; }
    public List<Odds> getOddsPerBookmaker()      { return oddsPerBookmaker; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String matchKey;
        private String homeTeam;
        private String awayTeam;
        private String competition;
        private String matchDate;
        private List<Odds> oddsPerBookmaker;

        public Builder matchKey(String v)               { this.matchKey = v; return this; }
        public Builder homeTeam(String v)               { this.homeTeam = v; return this; }
        public Builder awayTeam(String v)               { this.awayTeam = v; return this; }
        public Builder competition(String v)            { this.competition = v; return this; }
        public Builder matchDate(String v)              { this.matchDate = v; return this; }
        public Builder oddsPerBookmaker(List<Odds> v)   { this.oddsPerBookmaker = v; return this; }
        public Match build()                            { return new Match(this); }
    }
}
