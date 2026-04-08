package com.betseguras.integration.oddsagora;

import com.betseguras.config.OddsAgoraProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Busca HTML de páginas via Playwright (headless Chromium).
 *
 * Todas as operações do Playwright rodam em uma única thread dedicada
 * (playwrightThread) para garantir thread-safety total.
 * Os chamadores submetem tarefas via Future e aguardam o resultado.
 */
@Component
@ConditionalOnProperty(name = "oddsagora.enabled", havingValue = "true")
public class PlaywrightPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightPageFetcher.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final OddsAgoraProperties props;

    /** Thread única que executa TUDO do Playwright — garante thread-safety. */
    private ExecutorService playwrightThread;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightPageFetcher(OddsAgoraProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        playwrightThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "playwright-thread");
            t.setDaemon(true);
            return t;
        });

        // Inicializa Playwright e Browser na thread dedicada
        submit(() -> {
            log.info("Iniciando Playwright/Chromium na thread dedicada...");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of(
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage",
                                    "--disable-blink-features=AutomationControlled"
                            ))
            );
            log.info("Playwright pronto.");
            return null;
        });
    }

    /**
     * Busca o HTML completamente renderizado da URL indicada.
     * Bloqueia o chamador até o Playwright terminar.
     * Deve ser chamado de Schedulers.boundedElastic().
     */
    public String fetchRenderedHtml(String url) {
        // Delay aleatório entre requisições (1–3 s) para reduzir chance de bloqueio
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return submit(() -> doFetch(url));
    }

    private String doFetch(String url) throws Exception {
        log.debug("Playwright → {}", url);

        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setUserAgent(USER_AGENT)
                        .setViewportSize(1366, 768)
                        .setExtraHTTPHeaders(Map.of(
                                "Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8"
                        ))
        )) {
            try (Page page = context.newPage()) {
                page.setDefaultTimeout(props.getPageTimeoutMs());

                // networkidle garante que os XHRs de odds foram concluídos
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(props.getPageTimeoutMs()));

                // Confirma que pelo menos um eventRow existe no DOM
                page.waitForSelector("div.eventRow",
                        new Page.WaitForSelectorOptions().setTimeout(5000));

                String html = page.content();
                log.debug("Playwright: {} chars de HTML de {}", html.length(), url);
                return html;
            }
        }
    }


    private <T> T submit(Callable<T> task) {
        try {
            Future<T> future = playwrightThread.submit(task);
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Playwright task failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        submit(() -> {
            if (browser != null) try { browser.close(); } catch (Exception ignored) {}
            if (playwright != null) try { playwright.close(); } catch (Exception ignored) {}
            log.info("Playwright encerrado.");
            return null;
        });
        playwrightThread.shutdown();
    }
}
