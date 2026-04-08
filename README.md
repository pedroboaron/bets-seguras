# Bets Seguras

Sistema de detecção automática de **surebets** (arbitragem esportiva) em partidas de futebol brasileiro. Monitora odds de casas de apostas licenciadas no Brasil, detecta combinações lucrativas independente do resultado e notifica via Telegram em tempo real.

---

## O que é uma surebet?

Uma surebet ocorre quando as odds disponíveis em diferentes casas de apostas são altas o suficiente para que, apostando nos três resultados possíveis (vitória mandante, empate, vitória visitante), o retorno seja garantidamente maior do que o investimento — independente do que aconteça em campo.

**Condição matemática:**

```
1/odd1 + 1/oddX + 1/odd2 < 1.0
```

Quanto menor a soma, maior o lucro. Exemplo:

| Resultado | Casa | Odd | Stake |
|---|---|---|---|
| Vitória mandante | Bet365 | 2.20 | R$ 454,55 |
| Empate | Betano | 3.60 | R$ 277,78 |
| Vitória visitante | Superbet | 3.80 | R$ 263,16 |

Soma implícita: `1/2.20 + 1/3.60 + 1/3.80 = 0.9712` → **lucro garantido de 2.97%** sobre qualquer resultado.

---

## Stack técnica

| Componente | Tecnologia |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.4 + WebFlux (reativo) |
| Scraping | Playwright for Java 1.49.0 (Chromium headless) |
| Parsing HTML | Jsoup 1.18.1 |
| Notificações | Telegram Bot API |
| Documentação API | SpringDoc / Swagger UI |

---

## Fontes de odds

### OddsAgora.com.br (principal — casas brasileiras)

Site de comparação de odds exclusivamente de casas licenciadas no Brasil. A página é uma aplicação Vue.js que carrega os dados via XHR após a renderização inicial, tornando necessário um browser headless completo.

### The Odds API (opcional — casas internacionais)

API REST com odds de múltiplas casas. Habilitada via `the-odds-api.enabled: true`. Atualmente desativada pois o foco é em casas licenciadas no Brasil.

---

## Como o scraping funciona

### Fase 1 — Descoberta de partidas

**Arquivo:** `OddsAgoraBrazilProvider.java`

Para cada competição configurada (ex: `brasileirao-betano`), uma requisição é feita à página de listagem:

```
https://www.oddsagora.com.br/football/brazil/brasileirao-betano/
```

O `PlaywrightPageFetcher` abre essa URL com Chromium headless e aguarda `WaitUntilState.NETWORKIDLE` — garante que os XHRs de odds concluíram antes de capturar o HTML. Um seletor adicional (`div.eventRow`) confirma que o conteúdo dinâmico chegou.

**Problema crítico de estrutura resolvido:** o `date-header` na página do OddsAgora é *irmão* dos `eventRow` no DOM, não filho. Isso significa que a data não pode ser encontrada dentro de cada linha de jogo — é necessário percorrer todos os elementos juntos:

```java
// Seleciona data-headers e eventRows na ordem exata do DOM
Elements mixed = doc.select("[data-testid='date-header'], div.eventRow");

String currentDate = null;
for (Element el : mixed) {
    if ("date-header".equals(el.attr("data-testid"))) {
        currentDate = el.text().trim(); // ex: "Hoje, 02 Abr" ou "11 Abr 2026"
    } else {
        // eventRow herda a data do último date-header visto acima
        parseMatchInfo(el, competitionName, currentDate);
    }
}
```

De cada `eventRow` são extraídos: times (casa e visitante), data da partida e o path da página h2h do jogo.

O resultado é uma lista de `MatchInfo` — sem odds ainda, apenas metadados das partidas.

### Fase 2 — Scraping paralelo das páginas h2h

**Arquivo:** `OddsAgoraMatchScraper.java`

Para cada `MatchInfo`, três requisições são feitas em paralelo (pool configurável via `match-parallelism`), uma para cada mercado:

| Mercado | Sufixo na URL |
|---|---|
| Jogo todo | `#matchId:1X2;2` |
| 1º Tempo | `#matchId:1X2;3` |
| 2º Tempo | `#matchId:1X2;4` |

Exemplo de URL completa:
```
https://www.oddsagora.com.br/football/h2h/botafogo-.../mirassol-.../#pzkJrLoD:1X2;2
```

O seletor de espera muda para `a.odds-link` — que são os elementos que realmente contêm os valores das odds por casa de aposta.

**Estrutura HTML real das linhas de bookmaker (descoberta via debug dump):**

```html
<img alt="bet365" title="bet365" class="bookmaker-logo ...">
<!-- seguido de exatamente 3 links de odds -->
<a class="odds-link underline">2.20</a>  <!-- vitória mandante -->
<a class="odds-link underline">3.50</a>  <!-- empate -->
<a class="odds-link underline">3.10</a>  <!-- vitória visitante -->

<img alt="Betano.br" title="Betano.br" class="bookmaker-logo ...">
<a class="odds-link underline">2.27</a>
<a class="odds-link underline">3.50</a>
<a class="odds-link underline">3.10</a>
```

Os logos `img.bookmaker-logo` e os links `a.odds-link` aparecem intercalados no DOM exatamente nessa ordem. A extração agrupa 3 odds para cada logo, por posição:

```java
Elements bookmakerEls = doc.select("img[class*=bookmaker-logo][alt]");
Elements oddsEls      = doc.select("a.odds-link");

for (int i = 0; i < bookmakerEls.size(); i++) {
    String nome    = bookmakerEls.get(i).attr("alt");   // "bet365"
    double home    = parse(oddsEls.get(i * 3).text());  // 2.20
    double draw    = parse(oddsEls.get(i * 3 + 1).text()); // 3.50
    double away    = parse(oddsEls.get(i * 3 + 2).text()); // 3.10
}
```

Cada mercado gera um objeto `Match` separado, com competition distinta:
- `"Brasileirão Série A"` → jogo todo
- `"Brasileirão Série A - 1º Tempo"` → primeiro tempo
- `"Brasileirão Série A - 2º Tempo"` → segundo tempo

### Thread-safety do Playwright

Playwright não é thread-safe. A solução usa dois padrões distintos:

- **`PlaywrightPageFetcher`** (fase 1): uma única instância Playwright em uma `SingleThreadExecutor`. Todas as operações são submetidas via `Future.get()` — o chamador bloqueia até o resultado.
- **`OddsAgoraMatchScraper`** (fase 2): pool fixo de N threads, cada uma com seu próprio Playwright+Browser via `ThreadLocal`. Isso permite paralelismo real entre jogos sem compartilhar estado.

Medidas anti-bot aplicadas em ambos:
- User-Agent realista (Chrome 131 Windows)
- Viewport 1366×768
- Header `Accept-Language: pt-BR`
- `--disable-blink-features=AutomationControlled`
- Delay aleatório entre requisições (500ms–1500ms)
- `WaitUntilState.NETWORKIDLE` para garantir carregamento completo

---

## Como as odds são validadas

### 1. Filtro de regulamentação

**Arquivo:** `ArbitrageEngine.java`

Antes de qualquer cálculo, o engine filtra as odds para manter apenas casas **licenciadas no Brasil** pela SPA (Secretaria de Prêmios e Apostas — Lei 14.790/2023):

```java
oddsList = oddsList.stream()
    .filter(o -> registry.find(o.bookmaker())
        .map(BookmakerRegistry.BookmakerInfo::licenciadaBrasil)
        .orElse(false))
    .toList();
```

Casas aceitas: Bet365, Betano, Sportingbet, KTO, Betfair, Superbet, Novibet, Bwin e todas as casas scrapeadas diretamente do OddsAgora (que são brasileiras por natureza). Pinnacle, 1xBet, Unibet e similares são descartadas.

### 2. Normalização e agrupamento

**Arquivo:** `MatchNormalizerService.java`

Partidas vindas de fontes diferentes são agrupadas pela chave:

```
{time_casa}_vs_{time_visitante}_{data}_{periodo}
```

O período é extraído do nome da competição — `"_full"`, `"_1t"` ou `"_2t"` — impedindo que odds do jogo todo sejam misturadas com odds do primeiro ou segundo tempo. Isso é crítico: uma surebet calculada com odds de mercados diferentes não seria executável.

Nomes de times são normalizados (remoção de acentos, aliases como `"Galo"→"Atletico Mineiro"`) para garantir que `"Atlético-MG"` (The Odds API) case com `"Atletico Mineiro"` (OddsAgora).

### 3. Detecção de arbitragem

**Arquivo:** `ArbitrageEngine.java`

Para cada partida agrupada, o engine seleciona a melhor odd disponível em cada resultado:

```java
Odds bestHome = oddsList.stream().max(Comparator.comparingDouble(Odds::homeWin)).orElseThrow();
Odds bestDraw = oddsList.stream().max(Comparator.comparingDouble(Odds::draw)).orElseThrow();
Odds bestAway = oddsList.stream().max(Comparator.comparingDouble(Odds::awayWin)).orElseThrow();

double sum = 1/bestHome.homeWin() + 1/bestDraw.draw() + 1/bestAway.awayWin();
```

Se `sum < 1.0` e o lucro percentual supera o mínimo configurado, calcula as stakes:

```
stake_k = investimento / (odd_k × sum)
```

O investimento máximo respeita os saldos configurados por casa (se houver):

```
investimento_max = min(saldo_k × odd_k × sum)  para cada casa k
```

### 4. Separação por data

**Arquivo:** `ArbitrageService.java`

As oportunidades são separadas em dois caches independentes:

- **Hoje:** `matchDate` contém `"hoje"` (OddsAgora) ou começa com a data ISO atual (The Odds API). Datas `null` vão para futuros — nunca assumidas como hoje.
- **Futuros:** tudo que não é hoje.

Dois agendamentos independentes (Spring `@Scheduled` com `fixedDelay`) garantem que jogos de hoje sejam verificados com mais frequência sem desperdiçar scraping em jogos distantes.

---

## Ciclo de notificações Telegram

A cada ciclo de coleta:

1. **Alerta imediato** — enviado individualmente assim que cada oportunidade é detectada:

```
🚨 Surebet encontrada! (hoje)

Botafogo x Mirassol
🏆 Brasileirão Série A · 02 Abr
💹 +2.97% · R$ 1000 → R$ 1029.70 (lucro R$ 29.70)

Onde apostar:
  1 Bet365 ✅ @2.20 → R$ 454.55
  X Betano ✅ @3.60 → R$ 277.78
  2 Superbet ✅ @3.80 → R$ 263.16
```

2. **Resumo ao final** do ciclo:

```
📊 Ciclo encerrado (hoje) — 3 oportunidade(s) enviada(s) acima.
```

---

## Endpoints REST

| Método | Path | Descrição |
|---|---|---|
| GET | `/api/v1/oportunidades/hoje` | Surebets dos jogos de hoje |
| GET | `/api/v1/oportunidades/proximos` | Surebets dos jogos futuros |
| GET | `/api/v1/arbitrage-opportunities/refresh` | Força atualização completa |
| GET | `/api/v1/calcular` | Calculadora manual de arbitragem |
| GET | `/swagger-ui.html` | Documentação interativa |

### Calculadora manual

```
GET /api/v1/calcular?odd1=2.20&oddX=3.60&odd2=3.80&casa1=Bet365&casaX=Betano&casa2=Superbet&investimento=1000
```

Além do resultado e das stakes, retorna o **range de segurança** de cada odd — até onde ela pode cair (mantendo as outras duas fixas) antes de a arbitragem desaparecer:

```json
"seguranca": {
  "minimaParaArbitragem": 2.07,
  "podeCair": 0.13,
  "margemSeguranca": "5.91%"
}
```

---

## Configuração

```yaml
arbitrage:
  max-investment: 1000.0          # teto global de investimento
  min-profit-percentage: 0.5      # lucro mínimo para notificar (%)
  today-refresh-seconds: 120      # intervalo de atualização — jogos de hoje
  future-refresh-seconds: 3600    # intervalo de atualização — jogos futuros

oddsagora:
  enabled: true
  base-url: https://www.oddsagora.com.br
  page-timeout-ms: 15000
  match-parallelism: 3            # threads simultâneas para scraping h2h
  competitions:
    - brasileirao-betano           # Brasileirão Série A
    - brasileirao-serie-b-superbet # Brasileirão Série B
    - copa-betano-do-brasil        # Copa do Brasil

notification:
  telegram:
    enabled: true
    bot-token: SEU_TOKEN_AQUI
    chat-ids:
      - "SEU_CHAT_ID"
    only-when-found: true          # false = notifica mesmo sem oportunidades
```

Intervalos podem ser sobrescritos por variáveis de ambiente:
```bash
TODAY_REFRESH_SECONDS=60 FUTURE_REFRESH_SECONDS=1800 java -jar bets-seguras.jar
```

---

## Estrutura do projeto

```
src/main/java/com/betseguras/
├── config/
│   ├── ArbitrageConfig.java         # Parâmetros de arbitragem e intervalos
│   ├── OddsAgoraProperties.java     # Config do scraper OddsAgora
│   └── NotificationProperties.java  # Config do Telegram
├── domain/
│   ├── Match.java                   # Partida com odds de múltiplas casas
│   ├── Odds.java                    # Odds de uma casa para um jogo
│   ├── ArbitrageOpportunity.java    # Oportunidade detectada com stakes
│   └── StakeDistribution.java       # Quanto apostar em cada resultado/casa
├── integration/
│   ├── oddsagora/
│   │   ├── OddsAgoraBrazilProvider.java  # Fase 1: lista jogos por competição
│   │   ├── OddsAgoraMatchScraper.java    # Fase 2: odds por jogo (h2h paralelo)
│   │   ├── PlaywrightPageFetcher.java    # Browser headless thread-safe
│   │   └── MatchInfo.java               # DTO entre fase 1 e fase 2
│   └── theoddsapi/
│       └── TheOddsApiProvider.java      # Integração API REST (opcional)
└── service/
    ├── ArbitrageEngine.java         # Cálculo da surebet e distribuição de stakes
    ├── ArbitrageService.java        # Orquestra coleta, cache e agendamentos
    ├── MatchNormalizerService.java  # Normaliza nomes e agrupa partidas
    ├── BookmakerRegistry.java       # Cadastro de casas (licença, PIX, links)
    ├── OddsCollectorService.java    # Agrega todos os providers
    └── TelegramNotificationService.java # Alertas e resumos
```

---

## Executando

**Pré-requisito — instalar o Chromium do Playwright:**
```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

**Build e execução:**
```bash
mvn clean package -DskipTests
java -jar target/bets-seguras-0.0.1-SNAPSHOT.jar
```

Na primeira execução com `oddsagora.enabled: true`, o scraper salva o HTML da primeira página h2h em `%TEMP%/oddsagora-h2h-<matchId>.html` para facilitar validação dos seletores.
