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
public class MockBet365Provider implements OddsProvider {

    @Override
    public String getName() {
        return "Bet365";
    }

    @Override
    public Flux<Match> fetchOdds() {
        long now = System.currentTimeMillis();
        return Flux.fromIterable(List.of(
            Match.builder()
                .homeTeam("Flamengo").awayTeam("Palmeiras")
                .competition("Brasileirao Serie A").matchDate("2024-08-10")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Bet365").homeWin(2.20).draw(3.20).awayWin(3.60)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Corinthians").awayTeam("São Paulo")
                .competition("Brasileirao Serie A").matchDate("2024-08-10")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Bet365").homeWin(2.50).draw(3.10).awayWin(2.90)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Santos").awayTeam("Grêmio")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Bet365").homeWin(2.80).draw(3.30).awayWin(2.50)
                    .collectedAt(now).build()))
                .build(),
            Match.builder()
                .homeTeam("Atletico Mineiro").awayTeam("Fluminense")
                .competition("Brasileirao Serie A").matchDate("2024-08-11")
                .oddsPerBookmaker(List.of(Odds.builder()
                    .bookmaker("Bet365").homeWin(1.90).draw(3.50).awayWin(4.00)
                    .collectedAt(now).build()))
                .build()
        ));
    }
}
