package com.betseguras.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Registry of known bookmakers with regulatory status, deposit info and direct links.
 *
 * Links aprofundados (jogo específico) não são viáveis: cada casa usa IDs internos
 * próprios. Os links abaixo levam direto à seção de futebol de cada casa — o mais
 * prático sem acesso às APIs privadas.
 *
 * Status regulatório: SPA (Secretaria de Prêmios e Apostas) — Lei 14.790/2023.
 */
@Service
public class BookmakerRegistry {

    public record BookmakerInfo(
            String nome,
            boolean licenciadaBrasil,
            boolean aceitaPix,
            boolean aceitaBrasileiros,
            String deposito,
            String observacao,
            String linkFutebol      // link direto para a seção de futebol
    ) {}

    private static final Map<String, BookmakerInfo> REGISTRY = Map.ofEntries(
        // ── Licenciadas no Brasil (SPA) ────────────────────────────────────
        entry("bet365",
            true, true, true,
            "PIX, cartão, Skrill",
            "Licenciada no Brasil. Maior casa do mundo.",
            "https://www.bet365.com.br/#/AS/B1/"),
        entry("betano",
            true, true, true,
            "PIX, cartão",
            "Licenciada no Brasil. Patrocinadora de vários clubes brasileiros.",
            "https://www.betano.com.br/sport/futebol/"),
        entry("sportingbet",
            true, true, true,
            "PIX, cartão, boleto",
            "Licenciada no Brasil. Opera há anos no mercado nacional.",
            "https://www.sportingbet.com.br/futebol"),
        entry("kto",
            true, true, true,
            "PIX, cartão",
            "Licenciada no Brasil. Foco no mercado brasileiro.",
            "https://www.kto.com/pt/sports/futebol"),
        entry("betfair",
            true, true, true,
            "PIX, cartão, Skrill",
            "Licenciada no Brasil. Exchange — odds geralmente superiores.",
            "https://www.betfair.com.br/exchange/plus/futebol"),
        entry("superbet",
            true, true, true,
            "PIX, cartão",
            "Licenciada no Brasil.",
            "https://superbet.com.br/apostas-esportivas/futebol"),
        entry("novibet",
            true, true, true,
            "PIX, cartão",
            "Licenciada no Brasil.",
            "https://www.novibet.com.br/apostas-esportivas/futebol"),
        entry("bwin",
            true, true, true,
            "PIX, cartão, Skrill",
            "Licenciada no Brasil.",
            "https://sports.bwin.com.br/pt/sports/futebol-4"),
        // ── Internacionais sem licença BR — aceitam brasileiros ────────────
        entry("pinnacle",
            false, false, true,
            "Skrill, Neteller, crypto",
            "Sem licença BR mas aceita brasileiros. Melhores odds do mercado — sem limitação de apostadores.",
            "https://www.pinnacle.com/pt/soccer/matchups/"),
        entry("unibet",
            false, false, true,
            "Skrill, Neteller, cartão",
            "Sem licença BR. Aceita brasileiros via Skrill/Neteller.",
            "https://www.unibet.com/betting/sports/filter/football/all/matches"),
        entry("william hill",
            false, false, true,
            "Skrill, cartão",
            "Sem licença BR. Aceita brasileiros.",
            "https://sports.williamhill.com/betting/en-gb/football"),
        entry("888sport",
            false, false, true,
            "Skrill, Neteller, cartão",
            "Sem licença BR. Aceita brasileiros.",
            "https://www.888sport.com/football/"),
        entry("marathonbet",
            false, false, true,
            "Skrill, crypto",
            "Sem licença BR. Aceita brasileiros. Odds competitivas em mercados europeus.",
            "https://www.marathonbet.com/en/betting/Football/"),
        entry("1xbet",
            false, false, true,
            "PIX, crypto, Skrill",
            "Sem licença BR. Muito popular no Brasil. Atenção: histórico de reclamações de saques.",
            "https://1xbet.com/pt/line/football"),
        entry("betway",
            false, false, true,
            "Skrill, Neteller, cartão",
            "Sem licença BR. Aceita brasileiros.",
            "https://www.betway.com/sports/evt/football/"),
        entry("betmgm",
            false, false, false,
            "Cartão (USD)",
            "Licença apenas nos EUA. Não aceita brasileiros.",
            "https://sports.betmgm.com/en/sports/soccer-4"),
        entry("draftkings",
            false, false, false,
            "Cartão (USD)",
            "Licença apenas nos EUA. Não aceita brasileiros.",
            "https://sportsbook.draftkings.com/leagues/soccer"),
        entry("fanduel",
            false, false, false,
            "Cartão (USD)",
            "Licença apenas nos EUA. Não aceita brasileiros.",
            "https://sportsbook.fanduel.com/soccer"),
        entry("bovada",
            false, false, false,
            "Crypto, cartão",
            "Foco no mercado americano. Não recomendada para brasileiros.",
            "https://www.bovada.lv/sports/soccer"),
        // ── Agregador de odds brasileiras (OddsAgora) ──────────────────────
        entry("oddsagora br",
            true, true, true,
            "PIX (varia por casa)",
            "Melhor odd disponível entre casas de apostas brasileiras via OddsAgora.com.br. Verifique a comparação completa no link.",
            "https://www.oddsagora.com.br/football/brazil/")
    );

    public Optional<BookmakerInfo> find(String bookmakerTitle) {
        if (bookmakerTitle == null) return Optional.empty();
        String key = bookmakerTitle.toLowerCase().trim();
        BookmakerInfo info = REGISTRY.get(key);
        if (info != null) return Optional.of(info);
        // Tenta sem sufixo ".br" (ex: "betano.br" → "betano", "kto.br" → "kto")
        if (key.endsWith(".br")) {
            info = REGISTRY.get(key.substring(0, key.length() - 3));
            if (info != null) return Optional.of(info);
        }
        return Optional.empty();
    }

    public BookmakerInfo findOrUnknown(String bookmakerTitle) {
        return find(bookmakerTitle).orElse(new BookmakerInfo(
                bookmakerTitle,
                false,
                false,
                true,
                "Verificar no site",
                "Casa não mapeada — verifique disponibilidade para brasileiros antes de se cadastrar.",
                null
        ));
    }

    private static Map.Entry<String, BookmakerInfo> entry(
            String key, boolean licBR, boolean pix, boolean aceitaBR,
            String deposito, String obs, String link) {
        return Map.entry(key, new BookmakerInfo(key, licBR, pix, aceitaBR, deposito, obs, link));
    }
}
