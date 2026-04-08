package com.betseguras.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient oddsApiWebClient(TheOddsApiProperties props, WebClient.Builder builder) {
        return builder
                .baseUrl(props.getBaseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2 MB
                .build();
    }
}
