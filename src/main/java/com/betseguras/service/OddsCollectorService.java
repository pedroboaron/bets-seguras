package com.betseguras.service;

import com.betseguras.domain.Match;
import com.betseguras.integration.OddsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Collects odds from all registered providers in parallel and normalizes them.
 */
@Service
public class OddsCollectorService {

    private static final Logger log = LoggerFactory.getLogger(OddsCollectorService.class);

    private final List<OddsProvider> providers;
    private final MatchNormalizerService normalizer;

    public OddsCollectorService(List<OddsProvider> providers, MatchNormalizerService normalizer) {
        this.providers = providers;
        this.normalizer = normalizer;
    }

    public Mono<List<Match>> collectAndNormalize() {
        log.info("Collecting odds from {} providers...", providers.size());
        return Flux.fromIterable(providers)
                .flatMap(provider -> provider.fetchOdds()
                        .doOnError(e -> log.error("Error from {}: {}", provider.getName(), e.getMessage()))
                        .onErrorResume(e -> Flux.empty()))
                .collectList()
                .map(normalizer::groupAndNormalize)
                .doOnSuccess(matches -> log.info("Normalized {} matches", matches.size()));
    }
}
