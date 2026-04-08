package com.betseguras.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "arbitrage")
public class ArbitrageConfig {

    private double maxInvestment = 1000.0;
    private double minProfitPercentage = 0.5;
    private int oddsRefreshIntervalSeconds = 300;

    /** Intervalo de atualização dos jogos de HOJE (segundos). */
    private int todayRefreshSeconds = 120;

    /** Intervalo de atualização dos jogos FUTUROS (segundos). */
    private int futureRefreshSeconds = 3600;

    /**
     * Saldo disponível por casa de apostas (chave = nome exato da casa).
     * Quando configurado, o investimento máximo é calculado automaticamente
     * respeitando o limite de cada conta.
     *
     * Exemplo:
     *   balances:
     *     Bet365: 500.0
     *     KTO: 800.0
     *     Pinnacle: 300.0
     */
    private Map<String, Double> balances = new HashMap<>();

    public double getMaxInvestment()                 { return maxInvestment; }
    public void setMaxInvestment(double v)           { this.maxInvestment = v; }

    public double getMinProfitPercentage()           { return minProfitPercentage; }
    public void setMinProfitPercentage(double v)     { this.minProfitPercentage = v; }

    public int getOddsRefreshIntervalSeconds()       { return oddsRefreshIntervalSeconds; }
    public void setOddsRefreshIntervalSeconds(int v) { this.oddsRefreshIntervalSeconds = v; }

    public int getTodayRefreshSeconds()              { return todayRefreshSeconds; }
    public void setTodayRefreshSeconds(int v)        { this.todayRefreshSeconds = v; }

    public int getFutureRefreshSeconds()             { return futureRefreshSeconds; }
    public void setFutureRefreshSeconds(int v)       { this.futureRefreshSeconds = v; }

    public Map<String, Double> getBalances()         { return balances; }
    public void setBalances(Map<String, Double> v)   { this.balances = v; }

    /**
     * Retorna o saldo disponível para uma casa específica.
     * Case-insensitive. Retorna empty se não configurado.
     */
    public java.util.OptionalDouble balanceFor(String bookmaker) {
        if (bookmaker == null || balances.isEmpty()) return java.util.OptionalDouble.empty();
        return balances.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(bookmaker))
                .mapToDouble(Map.Entry::getValue)
                .findFirst();
    }
}
