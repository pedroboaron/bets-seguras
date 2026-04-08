package com.betseguras.integration.theoddsapi;

import com.betseguras.config.TheOddsApiProperties;
import com.betseguras.domain.Match;
import com.betseguras.domain.Odds;
import com.betseguras.integration.OddsProvider;
import com.betseguras.integration.theoddsapi.dto.TheOddsApiBookmakerDto;
import com.betseguras.integration.theoddsapi.dto.TheOddsApiEvent;
import com.betseguras.integration.theoddsapi.dto.TheOddsApiOutcomeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches 1X2 odds from The Odds API for all configured Brazilian league sports.
 * Activated when the-odds-api.enabled=true in application.yml.
 */
@Component
@ConditionalOnProperty(name = "the-odds-api.enabled", havingValue = "true")
public class TheOddsApiProvider implements OddsProvider {

    private static final Logger log = LoggerFactory.getLogger(TheOddsApiProvider.class);

    private static final Map<String, String> SPORT_NAMES = Map.of(
            "soccer_brazil_campeonato", "Brasileirão Série A",
            "soccer_brazil_serie_b",    "Brasileirão Série B",
            "soccer_brazil_serie_c",    "Brasileirão Série C"
    );

    private final WebClient webClient;
    private final TheOddsApiProperties props;

    public TheOddsApiProvider(WebClient oddsApiWebClient, TheOddsApiProperties props) {
        this.webClient = oddsApiWebClient;
        this.props = props;
    }

    @Override
    public String getName() {
        return "TheOddsApi";
    }

    @Override
    public Flux<Match> fetchOdds() {
        // flatMap concurrency=1 garante que erros de um sport não cancelem os outros
        return Flux.fromIterable(props.getSports())
                .flatMap(this::fetchSport, 1);
    }

    private Flux<Match> fetchSport(String sport) {
        log.debug("Fetching odds for sport: {}", sport);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v4/sports/{sport}/odds/")
                        .queryParam("apiKey", props.getApiKey())
                        .queryParam("regions", props.getRegions())
                        .queryParam("markets", "h2h")
                        .queryParam("oddsFormat", "decimal")
                        .build(sport))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    if (response.statusCode().value() == 404) {
                                        log.warn("Sport '{}' não disponível na API (404) — ignorando", sport);
                                    } else {
                                        log.error("The Odds API {} ao buscar {}: {}", response.statusCode(), sport, body);
                                    }
                                    return Mono.error(new RuntimeException(body));
                                }))
                .bodyToFlux(TheOddsApiEvent.class)
                .map(event -> toMatch(event, sport))
                .filter(m -> !m.getOddsPerBookmaker().isEmpty())
                .doOnNext(m -> log.debug("Match: {} vs {} ({} casas)",
                        m.getHomeTeam(), m.getAwayTeam(), m.getOddsPerBookmaker().size()))
                // isola o erro: este sport retorna vazio sem cancelar os outros
                .onErrorResume(e -> {
                    log.debug("Sport {} pulado: {}", sport, e.getMessage());
                    return Flux.empty();
                });
    }

    private Match toMatch(TheOddsApiEvent event, String sport) {
        long now = System.currentTimeMillis();

        List<Odds> oddsList = new ArrayList<>();
        for (TheOddsApiBookmakerDto bookmaker : event.bookmakers()) {
            bookmaker.markets().stream()
                    .filter(m -> "h2h".equals(m.key()))
                    .findFirst()
                    .ifPresent(market -> {
                        Odds odds = buildOdds(
                                bookmaker.title(),
                                event.homeTeam(),
                                event.awayTeam(),
                                market.outcomes(),
                                now);
                        if (odds != null) oddsList.add(odds);
                    });
        }

        String competition = SPORT_NAMES.getOrDefault(sport, event.sportTitle());
        String matchDate = event.commenceTime() != null && event.commenceTime().length() >= 10
                ? event.commenceTime().substring(0, 10)
                : null;

        return Match.builder()
                .homeTeam(event.homeTeam())
                .awayTeam(event.awayTeam())
                .competition(competition)
                .matchDate(matchDate)
                .oddsPerBookmaker(oddsList)
                .build();
    }

    /**
     * Maps the h2h outcomes list to an Odds object.
     * Returns null if any of the three outcomes (home, draw, away) is missing.
     */
    private Odds buildOdds(String bookmaker, String homeTeam, String awayTeam,
                           List<TheOddsApiOutcomeDto> outcomes, long collectedAt) {
        double homeWin = -1, draw = -1, awayWin = -1;

        for (TheOddsApiOutcomeDto o : outcomes) {
            if (homeTeam.equalsIgnoreCase(o.name())) {
                homeWin = o.price();
            } else if (awayTeam.equalsIgnoreCase(o.name())) {
                awayWin = o.price();
            } else if ("Draw".equalsIgnoreCase(o.name())) {
                draw = o.price();
            }
        }

        if (homeWin <= 0 || draw <= 0 || awayWin <= 0) {
            log.debug("Skipping bookmaker {} — incomplete 1X2 outcomes", bookmaker);
            return null;
        }

        return Odds.builder()
                .bookmaker(bookmaker)
                .homeWin(homeWin)
                .draw(draw)
                .awayWin(awayWin)
                .collectedAt(collectedAt)
                .build();
    }
}
