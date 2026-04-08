package com.betseguras.integration.mock;

import com.betseguras.domain.Match;
import com.betseguras.domain.Odds;
import com.betseguras.integration.OddsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@ConditionalOnProperty(name = "mock.odds.enabled", havingValue = "true", matchIfMissing = true)
public class MockSportingbetProvider implements OddsProvider {

    @Override
    public String getName() {
        return "Sportingbet";
    }

    @Override
    public Flux<Match> fetchOdds() {
        long now = System.currentTimeMillis();
        return Flux.fromIterable(List.of(
            // Flamengo x Palmeiras — odds ligeiramente diferentes (possível arb)
            Match.builder()
                .homeTeam("Flamengo").awayTeam("Palmeiras")
                .competition("Brasileirao Serie A").matchDate("2024-08-10")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Sportingbet").homeWin(2.05).draw(3.60).awayWin(3.75)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Corinthians").awayTeam("Sao Paulo")
                .competition("Brasileirao Serie A").matchDate("2024-08-10")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Sportingbet").homeWin(2.60).draw(3.20).awayWin(2.80)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Santos").awayTeam("Gremio")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Sportingbet").homeWin(2.90).draw(3.40).awayWin(2.80)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Atletico Mineiro").awayTeam("Fluminense")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Sportingbet").homeWin(2.15).draw(3.30).awayWin(3.90)
                    .collectedAt(now).build()))
                .build(),
            // Jogo exclusivo Sportingbet
            Match.builder()
                .homeTeam("Internacional").awayTeam("Vasco")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Sportingbet").homeWin(2.20).draw(3.10).awayWin(3.30)
                    .collectedAt(now).build()))
                .build()
        ));
    }
}
