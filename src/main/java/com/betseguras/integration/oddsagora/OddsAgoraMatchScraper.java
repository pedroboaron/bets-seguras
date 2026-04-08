package com.betseguras.integration.oddsagora;

import com.betseguras.config.OddsAgoraProperties;
import com.betseguras.domain.Match;
import com.betseguras.domain.Odds;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scraper paralelo para páginas individuais de jogos (h2h) do OddsAgora.
 *
 * Cada thread do pool tem seu próprio Playwright + Browser (thread-safe por design).
 * O número de threads simultâneas é configurável em oddsagora.match-parallelism.
 *
 * Mercados suportados (via hash na URL):
 *   Jogo todo: #matchId:1X2;2
 *   1º Tempo:  #matchId:1X2;3
 *   2º Tempo:  #matchId:1X2;4
 *
 * Estrutura HTML esperada na página h2h (a ser validada no primeiro run):
 *   Cada linha de casa de aposta: div com nome + 3 odds p[data-testid="odd-container-default"]
 */
@Component
@ConditionalOnProperty(name = "oddsagora.enabled", havingValue = "true")
public class OddsAgoraMatchScraper {

    private static final Logger log = LoggerFactory.getLogger(OddsAgoraMatchScraper.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** Sufixos de hash para cada mercado: jogo todo, 1T, 2T. */
    private static final String MARKET_FULL  = ":1X2;2";
    private static final String MARKET_HALF1 = ":1X2;3";
    private static final String MARKET_HALF2 = ":1X2;4";

    private final OddsAgoraProperties props;

    private ExecutorService executor;
    private Scheduler scheduler;

    // ThreadLocal: cada thread do pool tem seu próprio Playwright + Browser
    private final ThreadLocal<Playwright> tlPlaywright = new ThreadLocal<>();
    private final ThreadLocal<Browser>    tlBrowser    = new ThreadLocal<>();

    // Registro de todas as instâncias criadas para cleanup no shutdown
    private final List<Browser>    allBrowsers    = Collections.synchronizedList(new ArrayList<>());
    private final List<Playwright> allPlaywrights = Collections.synchronizedList(new ArrayList<>());

    // Flag para salvar HTML de debug na primeira execução
    private final AtomicBoolean debugDumped = new AtomicBoolean(false);

    public OddsAgoraMatchScraper(OddsAgoraProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        int n = props.getMatchParallelism();
        executor  = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "pw-match-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        scheduler = Schedulers.fromExecutorService(executor, "pw-match");
        log.info("OddsAgoraMatchScraper: pool de {} threads Playwright para scraping paralelo de jogos", n);
    }

    /** Scheduler para uso com subscribeOn() no reactive chain. */
    public Scheduler scheduler() {
        return scheduler;
    }

    /**
     * Scraping completo de um jogo: busca odds do jogo todo, 1T e 2T.
     * Retorna até 3 Match objects (um por mercado).
     * Método bloqueante — deve ser chamado via subscribeOn(scheduler()).
     */
    public List<Match> scrapeMatch(MatchInfo info) {
        Browser browser = getOrInitBrowser();
        List<Match> results = new ArrayList<>();

        String matchId = extractMatchId(info.matchPath());

        // Delay anti-bot aleatório
        sleep(ThreadLocalRandom.current().nextInt(500, 1500));

        try {
            // Mercado: jogo todo
            List<Odds> fullOdds = fetchMarketOdds(browser, info, matchId, MARKET_FULL, "Jogo Todo");
            if (!fullOdds.isEmpty()) {
                results.add(buildMatch(info, info.competition(), fullOdds));
            }

            sleep(500);

            // Mercado: 1º Tempo
            List<Odds> half1Odds = fetchMarketOdds(browser, info, matchId, MARKET_HALF1, "1º Tempo");
            if (!half1Odds.isEmpty()) {
                results.add(buildMatch(info, info.competition() + " - 1º Tempo", half1Odds));
            }

            sleep(500);

            // Mercado: 2º Tempo
            List<Odds> half2Odds = fetchMarketOdds(browser, info, matchId, MARKET_HALF2, "2º Tempo");
            if (!half2Odds.isEmpty()) {
                results.add(buildMatch(info, info.competition() + " - 2º Tempo", half2Odds));
            }

        } catch (Exception e) {
            log.warn("OddsAgora h2h: erro em {} vs {} — {}", info.homeTeam(), info.awayTeam(), e.getMessage());
        }

        log.debug("OddsAgora h2h: {} vs {} → {} mercados", info.homeTeam(), info.awayTeam(), results.size());
        return results;
    }

    // ── Playwright fetch ──────────────────────────────────────────────────────

    private List<Odds> fetchMarketOdds(Browser browser, MatchInfo info,
                                        String matchId, String marketSuffix, String marketLabel) {
        // Monta URL com hash do mercado específico
        String path = buildMarketPath(info.matchPath(), matchId, marketSuffix);
        String url  = props.getBaseUrl() + path;

        try (BrowserContext ctx = browser.newContext(
                new Browser.NewContextOptions()
                        .setUserAgent(USER_AGENT)
                        .setViewportSize(1366, 768)
                        .setExtraHTTPHeaders(Map.of("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8"))
        )) {
            try (Page page = ctx.newPage()) {
                page.setDefaultTimeout(props.getPageTimeoutMs());
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(props.getPageTimeoutMs()));

                // Aguarda elementos de odds aparecerem (ou desiste se não tiver mercado)
                try {
                    page.waitForSelector("a.odds-link",
                            new Page.WaitForSelectorOptions().setTimeout(8000));
                } catch (Exception e) {
                    log.debug("OddsAgora h2h: {} vs {} — {} sem odds (mercado provavelmente indisponível)",
                            info.homeTeam(), info.awayTeam(), marketLabel);
                    return List.of();
                }

                String html = page.content();

                // Debug: salva o HTML do primeiro jogo completo para análise dos seletores
                if (debugDumped.compareAndSet(false, true)) {
                    dumpDebugHtml(html, matchId);
                }

                return parseOddsTable(html, info.homeTeam(), info.awayTeam(), marketLabel);
            }
        }
    }

    // ── HTML Parsing ─────────────────────────────────────────────────────────

    /**
     * Extrai odds por casa de aposta da página h2h.
     *
     * Estrutura real do OddsAgora h2h:
     *   - Bookmaker logo: img[class*="bookmaker-logo"][alt="NomeDaCasa"]
     *   - Odds: a.odds-link (3 por casa, na mesma ordem que os logos no DOM)
     *
     * Os elementos aparecem ordenados no DOM: BK1, odd1, odd2, odd3, BK2, odd4, odd5, odd6, ...
     * Basta intercalar por posição.
     */
    private List<Odds> parseOddsTable(String html, String homeTeam, String awayTeam, String marketLabel) {
        Document doc = Jsoup.parse(html);
        List<Odds> oddsList = new ArrayList<>();

        Elements bookmakerEls = doc.select("img[class*=bookmaker-logo][alt]");
        Elements oddsEls      = doc.select("a.odds-link");

        if (bookmakerEls.isEmpty() || oddsEls.isEmpty()) {
            log.debug("OddsAgora h2h: nenhuma odd encontrada para {} vs {} mercado={}", homeTeam, awayTeam, marketLabel);
            return List.of();
        }

        // Cada bookmaker tem exatamente 3 odds consecutivas (1, X, 2)
        int bkCount = bookmakerEls.size();
        if (oddsEls.size() < bkCount * 3) {
            log.debug("OddsAgora h2h: {} vs {} [{}] — odds insuficientes ({} casas, {} odds)",
                    homeTeam, awayTeam, marketLabel, bkCount, oddsEls.size());
            return List.of();
        }

        for (int i = 0; i < bkCount; i++) {
            try {
                String bookmakerName = bookmakerEls.get(i).attr("alt").trim();
                double homeWin = parseOdd(oddsEls.get(i * 3).text());
                double draw    = parseOdd(oddsEls.get(i * 3 + 1).text());
                double awayWin = parseOdd(oddsEls.get(i * 3 + 2).text());

                if (homeWin <= 0 || draw <= 0 || awayWin <= 0) continue;

                Odds odds = Odds.builder()
                        .bookmaker(bookmakerName)
                        .homeWin(homeWin)
                        .draw(draw)
                        .awayWin(awayWin)
                        .collectedAt(System.currentTimeMillis())
                        .build();

                oddsList.add(odds);
                log.debug("OddsAgora h2h: {} | {} vs {} [{}] 1={} X={} 2={}",
                        bookmakerName, homeTeam, awayTeam, marketLabel, homeWin, draw, awayWin);

            } catch (Exception e) {
                log.debug("OddsAgora h2h: erro ao parsear bookmaker índice {}: {}", i, e.getMessage());
            }
        }

        log.debug("OddsAgora h2h: {} vs {} [{}] → {} casas extraídas",
                homeTeam, awayTeam, marketLabel, oddsList.size());
        return oddsList;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Extrai o ID do jogo do path h2h (ex: #pzkJrLoD → pzkJrLoD). */
    private String extractMatchId(String path) {
        int hashIdx = path.indexOf('#');
        if (hashIdx < 0) return "";
        String fragment = path.substring(hashIdx + 1).replace("/", "");
        // Se já tem sufixo de mercado, remove
        int colonIdx = fragment.indexOf(':');
        return colonIdx >= 0 ? fragment.substring(0, colonIdx) : fragment;
    }

    /** Constrói o path com o sufixo de mercado correto. */
    private String buildMarketPath(String basePath, String matchId, String marketSuffix) {
        // Remove hash existente e reconstrói com o mercado
        int hashIdx = basePath.indexOf('#');
        String pathOnly = hashIdx >= 0 ? basePath.substring(0, hashIdx) : basePath;
        return pathOnly + "#" + matchId + marketSuffix;
    }

    private Match buildMatch(MatchInfo info, String competition, List<Odds> odds) {
        return Match.builder()
                .homeTeam(info.homeTeam())
                .awayTeam(info.awayTeam())
                .competition(competition)
                .matchDate(info.matchDate())
                .oddsPerBookmaker(odds)
                .build();
    }

    private double parseOdd(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void dumpDebugHtml(String html, String matchId) {
        try {
            String path = System.getProperty("java.io.tmpdir") + "/oddsagora-h2h-" + matchId + ".html";
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), html);
            log.info("OddsAgora h2h debug HTML salvo em: {}", path);
        } catch (Exception e) {
            log.warn("Não foi possível salvar debug HTML: {}", e.getMessage());
        }
    }

    // ── Browser lifecycle ─────────────────────────────────────────────────────

    private Browser getOrInitBrowser() {
        if (tlBrowser.get() == null) {
            Playwright pw = Playwright.create();
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage",
                                     "--disable-blink-features=AutomationControlled")));
            tlPlaywright.set(pw);
            tlBrowser.set(browser);
            allPlaywrights.add(pw);
            allBrowsers.add(browser);
            log.debug("OddsAgoraMatchScraper: Playwright inicializado em thread {}", Thread.currentThread().getName());
        }
        return tlBrowser.get();
    }

    @PreDestroy
    public void shutdown() {
        log.info("OddsAgoraMatchScraper: encerrando pool...");
        allBrowsers.forEach(b -> { try { b.close(); } catch (Exception ignored) {} });
        allPlaywrights.forEach(pw -> { try { pw.close(); } catch (Exception ignored) {} });
        if (executor != null) executor.shutdown();
    }
}
