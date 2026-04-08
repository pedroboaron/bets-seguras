package com.betseguras.integration;

import com.betseguras.domain.Match;
import reactor.core.publisher.Flux;

public interface OddsProvider {
    String getName();
    Flux<Match> fetchOdds();
}
