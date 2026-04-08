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
public class MockKTOProvider implements OddsProvider {

    @Override
    public String getName() {
        return "KTO";
    }

    @Override
    public Flux<Match> fetchOdds() {
        long now = System.currentTimeMillis();
        return Flux.fromIterable(List.of(
            // Flamengo x Palmeiras — odds que criam arbitragem quando combinadas com Bet365/Sportingbet
            Match.builder()
                .homeTeam("Flamengo").awayTeam("Palmeiras")
                .competition("Brasileirao Serie A").matchDate("2024-08-10")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("KTO").homeWin(2.15).draw(3.50).awayWin(4.00)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Atletico Mineiro").awayTeam("Fluminense")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("KTO").homeWin(2.00).draw(3.60).awayWin(4.20)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Santos").awayTeam("Gremio")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("KTO").homeWin(3.00).draw(3.50).awayWin(2.40)
                    .collectedAt(now).build()))
                .build()
        ));
    }
}
