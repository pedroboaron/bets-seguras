package com.betseguras.integration.oddsagora;

/**
 * Dados básicos de um jogo extraídos da página de competição.
 * Usado como entrada para o scraping individual da página h2h.
 *
 * @param homeTeam    Nome do time da casa
 * @param awayTeam    Nome do time visitante
 * @param competition Nome da competição (ex: "Brasileirão Série A")
 * @param matchDate   Data do jogo (texto, ex: "01 Abr")
 * @param matchPath   Caminho relativo da página h2h (ex: /football/h2h/botafogo-.../mirassol-.../#id)
 */
public record MatchInfo(
        String homeTeam,
        String awayTeam,
        String competition,
        String matchDate,
        String matchPath
) {}
