package com.betseguras.integration.theoddsapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TheOddsApiOutcomeDto(
        @JsonProperty("name")  String name,
        @JsonProperty("price") double price
) {}
