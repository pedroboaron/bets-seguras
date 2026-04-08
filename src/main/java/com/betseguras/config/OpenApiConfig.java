package com.betseguras.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bets Seguras — Arbitragem Esportiva")
                        .description("""
                                Sistema de detecção de surebets no Brasileirão (Série A, B e C).

                                **Como funciona:**
                                Coleta odds de múltiplas casas de apostas, identifica situações onde
                                apostar em todos os resultados possíveis garante lucro independente
                                do resultado do jogo.

                                **Fórmula:** soma = Σ(1/odd) < 1.0 → há arbitragem
                                **Lucro (%)** = (1/soma − 1) × 100
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Bets Seguras")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")));
    }
}
