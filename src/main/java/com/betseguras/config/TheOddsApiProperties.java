package com.betseguras.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "the-odds-api")
public class TheOddsApiProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String baseUrl = "https://api.the-odds-api.com";
    private String regions = "eu";
    private List<String> sports = List.of("soccer_brazil_campeonato", "soccer_brazil_serie_b");

    public boolean isEnabled()                { return enabled; }
    public void setEnabled(boolean v)         { this.enabled = v; }

    public String getApiKey()                 { return apiKey; }
    public void setApiKey(String v)           { this.apiKey = v; }

    public String getBaseUrl()                { return baseUrl; }
    public void setBaseUrl(String v)          { this.baseUrl = v; }

    public String getRegions()                { return regions; }
    public void setRegions(String v)          { this.regions = v; }

    public List<String> getSports()           { return sports; }
    public void setSports(List<String> v)     { this.sports = v; }
}
