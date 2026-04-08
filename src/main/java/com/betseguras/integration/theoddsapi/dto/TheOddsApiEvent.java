package com.betseguras.integration.theoddsapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TheOddsApiEvent(
        @JsonProperty("id")             String id,
        @JsonProperty("sport_key")      String sportKey,
        @JsonProperty("sport_title")    String sportTitle,
        @JsonProperty("commence_time")  String commenceTime,
        @JsonProperty("home_team")      String homeTeam,
        @JsonProperty("away_team")      String awayTeam,
        @JsonProperty("bookmakers")     List<TheOddsApiBookmakerDto> bookmakers
) {}
