package com.betseguras.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "oddsagora")
public class OddsAgoraProperties {

    private boolean enabled = false;
    private String baseUrl = "https://www.oddsagora.com.br";

    /**
     * Slugs de competições a serem consultadas.
     * Formato: caminho após /football/brazil/ na URL do site.
     * Ex: "brasileirao-betano" → https://www.oddsagora.com.br/football/brazil/brasileirao-betano/
     */
    private List<String> competitions = List.of(
            "brasileirao-betano",
            "brasileirao-serie-b-superbet",
            "copa-betano-do-brasil"
    );

    /** Tempo máximo de espera pelo carregamento da página (ms). */
    private int pageTimeoutMs = 15000;

    /** Número de threads Playwright para scraping paralelo das páginas h2h. */
    private int matchParallelism = 3;

    public boolean isEnabled()                      { return enabled; }
    public void setEnabled(boolean v)               { this.enabled = v; }

    public String getBaseUrl()                      { return baseUrl; }
    public void setBaseUrl(String v)                { this.baseUrl = v; }

    public List<String> getCompetitions()           { return competitions; }
    public void setCompetitions(List<String> v)     { this.competitions = v; }

    public int getPageTimeoutMs()                   { return pageTimeoutMs; }
    public void setPageTimeoutMs(int v)             { this.pageTimeoutMs = v; }

    public int getMatchParallelism()                { return matchParallelism; }
    public void setMatchParallelism(int v)          { this.matchParallelism = v; }
}
