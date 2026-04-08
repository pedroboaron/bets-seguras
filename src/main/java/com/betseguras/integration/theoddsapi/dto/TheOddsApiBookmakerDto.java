package com.betseguras.integration.theoddsapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TheOddsApiBookmakerDto(
        @JsonProperty("key")     String key,
        @JsonProperty("title")   String title,
        @JsonProperty("markets") List<TheOddsApiMarketDto> markets
) {}
