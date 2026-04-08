package com.betseguras.service;

import com.betseguras.domain.Match;
import com.betseguras.domain.Odds;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes team names and groups odds from different bookmakers for the same match.
 */
@Service
public class MatchNormalizerService {

    private static final Map<String, String> TEAM_ALIASES = new HashMap<>();

    static {
        // Normalizacao de nomes de times
        TEAM_ALIASES.put("atletico mineiro", "Atletico Mineiro");
        TEAM_ALIASES.put("atletico-mg", "Atletico Mineiro");
        TEAM_ALIASES.put("galo", "Atletico Mineiro");
        TEAM_ALIASES.put("flamengo", "Flamengo");
        TEAM_ALIASES.put("mengao", "Flamengo");
        TEAM_ALIASES.put("palmeiras", "Palmeiras");
        TEAM_ALIASES.put("verdao", "Palmeiras");
        TEAM_ALIASES.put("corinthians", "Corinthians");
        TEAM_ALIASES.put("timao", "Corinthians");
        TEAM_ALIASES.put("sao paulo", "São Paulo");
        TEAM_ALIASES.put("sao paulo fc", "São Paulo");
        TEAM_ALIASES.put("santos", "Santos");
        TEAM_ALIASES.put("gremio", "Grêmio");
        TEAM_ALIASES.put("gremio fbpa", "Grêmio");
        TEAM_ALIASES.put("fluminense", "Fluminense");
        TEAM_ALIASES.put("vasco", "Vasco");
        TEAM_ALIASES.put("vasco da gama", "Vasco");
        TEAM_ALIASES.put("internacional", "Internacional");
        TEAM_ALIASES.put("inter", "Internacional");
        TEAM_ALIASES.put("botafogo", "Botafogo");
    }

    public String normalizeTeamName(String raw) {
        if (raw == null) return "";
        String stripped = Normalizer.normalize(raw.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return TEAM_ALIASES.getOrDefault(stripped, toTitleCase(raw.trim()));
    }

    public String buildMatchKey(String homeTeam, String awayTeam, String matchDate, String competition) {
        String home = normalizeTeamName(homeTeam).toLowerCase().replaceAll("\\s+", "_");
        String away = normalizeTeamName(awayTeam).toLowerCase().replaceAll("\\s+", "_");
        String date = matchDate != null ? matchDate : "unknown";
        String period = extractPeriod(competition);
        return home + "_vs_" + away + "_" + date + "_" + period;
    }

    /**
     * Extrai o período do nome da competição para ser usado na chave de agrupamento.
     * Garante que odds de jogo todo, 1º tempo e 2º tempo nunca sejam mescladas.
     */
    private String extractPeriod(String competition) {
        if (competition == null) return "full";
        String lower = competition.toLowerCase();
        if (lower.contains("1") && lower.contains("tempo")) return "1t";
        if (lower.contains("2") && lower.contains("tempo")) return "2t";
        return "full";
    }

    /**
     * Groups multiple Match objects (one per bookmaker) by their match key,
     * merging all odds into a single Match with multiple Odds entries.
     */
    public List<Match> groupAndNormalize(List<Match> rawMatches) {
        Map<String, Match> grouped = new HashMap<>();

        for (Match raw : rawMatches) {
            String homeNorm = normalizeTeamName(raw.getHomeTeam());
            String awayNorm = normalizeTeamName(raw.getAwayTeam());
            String key = buildMatchKey(homeNorm, awayNorm, raw.getMatchDate(), raw.getCompetition());

            grouped.compute(key, (k, existing) -> {
                if (existing == null) {
                    List<Odds> oddsList = new ArrayList<>(raw.getOddsPerBookmaker());
                    return Match.builder()
                            .matchKey(k)
                            .homeTeam(homeNorm)
                            .awayTeam(awayNorm)
                            .competition(raw.getCompetition())
                            .matchDate(raw.getMatchDate())
                            .oddsPerBookmaker(oddsList)
                            .build();
                } else {
                    existing.getOddsPerBookmaker().addAll(raw.getOddsPerBookmaker());
                    return existing;
                }
            });
        }

        return grouped.values().stream()
                .sorted(Comparator.comparing(Match::getMatchKey))
                .toList();
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
