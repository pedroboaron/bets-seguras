package com.betseguras.controller;

import com.betseguras.config.ArbitrageConfig;
import com.betseguras.controller.dto.ArbitrageOpportunityResponse;
import com.betseguras.domain.ArbitrageOpportunity;
import com.betseguras.domain.StakeDistribution;
import com.betseguras.service.ArbitrageService;
import com.betseguras.service.BookmakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Arbitragem", description = "Detecção de surebets — odds ao vivo de múltiplas casas de apostas")
public class ArbitrageController {

    private final ArbitrageService arbitrageService;
    private final ArbitrageConfig config;
    private final BookmakerRegistry bookmakerRegistry;

    public ArbitrageController(ArbitrageService arbitrageService,
                               ArbitrageConfig config,
                               BookmakerRegistry bookmakerRegistry) {
        this.arbitrageService = arbitrageService;
        this.config = config;
        this.bookmakerRegistry = bookmakerRegistry;
    }

    @GetMapping("/oportunidades/hoje")
    @Operation(
        summary = "Oportunidades de arbitragem — jogos de HOJE",
        description = """
            Retorna oportunidades de surebet dos jogos que acontecem hoje.
            Cache atualizado no intervalo curto configurado em arbitrage.today-refresh-seconds.

            Use `?investimento=2000` para simular um valor diferente do padrão.
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> getOpportunitiesToday(
            @Parameter(description = "Valor total a investir em R$ (padrão: configurado no servidor)")
            @RequestParam(required = false) Double investimento) {
        return arbitrageService.getOpportunitiesToday()
                .map(opps -> buildResponse(opps, investimento));
    }

    @GetMapping("/oportunidades/proximos")
    @Operation(
        summary = "Oportunidades de arbitragem — jogos FUTUROS",
        description = """
            Retorna oportunidades de surebet dos jogos agendados para amanhã em diante.
            Cache atualizado no intervalo longo configurado em arbitrage.future-refresh-seconds.

            Use `?investimento=2000` para simular um valor diferente do padrão.
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> getOpportunitiesFuture(
            @Parameter(description = "Valor total a investir em R$ (padrão: configurado no servidor)")
            @RequestParam(required = false) Double investimento) {
        return arbitrageService.getOpportunitiesFuture()
                .map(opps -> buildResponse(opps, investimento));
    }

    @GetMapping("/brasileirao/oportunidades")
    @Operation(
        summary = "Oportunidades de arbitragem (todos os jogos)",
        description = "Retorna hoje + futuros combinados. Mantido por compatibilidade."
    )
    public Mono<ResponseEntity<Map<String, Object>>> getOpportunitiesAll(
            @Parameter(description = "Valor total a investir em R$ (padrão: configurado no servidor)")
            @RequestParam(required = false) Double investimento) {
        return arbitrageService.refreshAll()
                .map(opps -> buildResponse(opps, investimento));
    }

    @GetMapping("/arbitrage-opportunities")
    @Operation(
        summary = "Oportunidades (formato raw)",
        description = "Dados brutos do domínio sem transformação de apresentação."
    )
    public Mono<ResponseEntity<List<ArbitrageOpportunity>>> getRaw() {
        return arbitrageService.getOpportunitiesToday().map(ResponseEntity::ok);
    }

    @GetMapping("/arbitrage-opportunities/refresh")
    @Operation(
        summary = "Forçar atualização completa das odds",
        description = "Dispara nova coleta imediata (hoje + futuros) e recalcula as oportunidades."
    )
    public Mono<ResponseEntity<Map<String, Object>>> refresh() {
        return arbitrageService.refreshAll()
                .map(opps -> ResponseEntity.ok(Map.of(
                        "oportunidadesEncontradas", opps.size(),
                        "oportunidades", opps.stream()
                                .map(opp -> ArbitrageOpportunityResponse.from(opp, bookmakerRegistry))
                                .toList()
                )));
    }

    @GetMapping("/calcular")
    @Operation(
        summary = "Calculadora de arbitragem",
        description = """
            Informe as três melhores odds disponíveis (1, X, 2) e calcule na hora
            se existe oportunidade de surebet e quanto apostar em cada resultado.

            Parâmetros obrigatórios: odd1, oddX, odd2
            Parâmetros opcionais: casa1, casaX, casa2, investimento

            Exemplo:
              /calcular?odd1=2.10&oddX=3.40&odd2=3.80&casa1=Bet365&casaX=Betano&casa2=Pinnacle&investimento=1000
            """
    )
    public ResponseEntity<Map<String, Object>> calcular(
            @Parameter(description = "Odd vitória mandante (1)")  @RequestParam double odd1,
            @Parameter(description = "Odd empate (X)")            @RequestParam double oddX,
            @Parameter(description = "Odd vitória visitante (2)") @RequestParam double odd2,
            @Parameter(description = "Casa de aposta para 1")     @RequestParam(required = false, defaultValue = "Casa 1") String casa1,
            @Parameter(description = "Casa de aposta para X")     @RequestParam(required = false, defaultValue = "Casa X") String casaX,
            @Parameter(description = "Casa de aposta para 2")     @RequestParam(required = false, defaultValue = "Casa 2") String casa2,
            @Parameter(description = "Investimento total em R$")  @RequestParam(required = false) Double investimento) {

        if (odd1 <= 1.0 || oddX <= 1.0 || odd2 <= 1.0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Odds devem ser maiores que 1.0"));
        }

        double inv = (investimento != null && investimento > 0) ? investimento : config.getMaxInvestment();
        double sum = (1.0 / odd1) + (1.0 / oddX) + (1.0 / odd2);
        boolean temArbitragem = sum < 1.0;
        double lucroPercentual = temArbitragem ? round2(((1.0 / sum) - 1.0) * 100.0) : 0.0;
        double retorno = round2(inv / sum);
        double lucro   = round2(retorno - inv);

        double stake1 = round2((inv / odd1) / sum);
        double stakeX = round2((inv / oddX) / sum);
        double stake2 = round2((inv / odd2) / sum);

        Map<String, Object> aposta1 = new LinkedHashMap<>();
        aposta1.put("resultado", "Vitória mandante");
        aposta1.put("casa", casa1);
        aposta1.put("odd", odd1);
        aposta1.put("valorApostar", String.format("R$ %.2f", stake1));
        if (temArbitragem) aposta1.put("seguranca", oddSafety(odd1, oddX, odd2));

        Map<String, Object> apostaX = new LinkedHashMap<>();
        apostaX.put("resultado", "Empate");
        apostaX.put("casa", casaX);
        apostaX.put("odd", oddX);
        apostaX.put("valorApostar", String.format("R$ %.2f", stakeX));
        if (temArbitragem) apostaX.put("seguranca", oddSafety(oddX, odd1, odd2));

        Map<String, Object> aposta2 = new LinkedHashMap<>();
        aposta2.put("resultado", "Vitória visitante");
        aposta2.put("casa", casa2);
        aposta2.put("odd", odd2);
        aposta2.put("valorApostar", String.format("R$ %.2f", stake2));
        if (temArbitragem) aposta2.put("seguranca", oddSafety(odd2, odd1, oddX));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temArbitragem", temArbitragem);
        result.put("somaImplicita", round4(sum));
        result.put("lucroPercentual", lucroPercentual + "%");
        result.put("investimento", String.format("R$ %.2f", inv));
        result.put("retornoGarantido", String.format("R$ %.2f", retorno));
        result.put("lucroGarantido", String.format("R$ %.2f", lucro));
        result.put("apostas", List.of(aposta1, apostaX, aposta2));

        if (!temArbitragem) {
            result.put("mensagem", String.format(
                    "Sem arbitragem. Margem da casa: %.2f%%. Reduza as odds ou busque valores maiores.",
                    round2((sum - 1.0) * 100.0)));
        }

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> buildResponse(List<ArbitrageOpportunity> opps, Double investimento) {
        List<ArbitrageOpportunity> list = (investimento != null && investimento > 0)
                ? opps.stream().map(opp -> recalculate(opp, investimento)).toList()
                : opps;

        List<ArbitrageOpportunityResponse> response = list.stream()
                .map(opp -> ArbitrageOpportunityResponse.from(opp, bookmakerRegistry))
                .toList();

        double invUsed = investimento != null ? investimento : config.getMaxInvestment();

        return ResponseEntity.ok(Map.of(
                "oportunidadesEncontradas", response.size(),
                "investimentoBase", String.format("R$ %.2f", invUsed),
                "minLucroConfigurado", String.format("%.2f%%", config.getMinProfitPercentage()),
                "oportunidades", response
        ));
    }

    private ArbitrageOpportunity recalculate(ArbitrageOpportunity opp, double investment) {
        double sum = opp.arbitrageSum();

        List<StakeDistribution> stakes = opp.stakes().stream()
                .map(s -> new StakeDistribution(
                        s.bookmaker(), s.outcome(), s.odd(),
                        round2((investment / s.odd()) / sum)))
                .toList();

        double guaranteed = round2(stakes.get(0).stake() * stakes.get(0).odd());

        return ArbitrageOpportunity.builder()
                .matchKey(opp.matchKey()).homeTeam(opp.homeTeam()).awayTeam(opp.awayTeam())
                .competition(opp.competition()).matchDate(opp.matchDate())
                .oddHomeWin(opp.oddHomeWin()).bookmakerHomeWin(opp.bookmakerHomeWin())
                .oddDraw(opp.oddDraw()).bookmakerDraw(opp.bookmakerDraw())
                .oddAwayWin(opp.oddAwayWin()).bookmakerAwayWin(opp.bookmakerAwayWin())
                .arbitrageSum(opp.arbitrageSum())
                .profitPercentage(opp.profitPercentage())
                .totalInvestment(round2(investment))
                .guaranteedReturn(guaranteed)
                .guaranteedProfit(round2(guaranteed - investment))
                .stakes(stakes)
                .detectedAt(opp.detectedAt())
                .build();
    }

    /**
     * Calcula o range de segurança de uma odd, mantendo as outras duas fixas.
     *
     * A odd pode cair até: oddMin = 1 / (1 - 1/outroA - 1/outroB)
     * Abaixo disso a soma implícita passa de 1.0 e a arbitragem some.
     *
     * @param odd     a odd analisada
     * @param outroA  uma das outras duas odds (fixas)
     * @param outroB  a outra odd fixa
     */
    private Map<String, Object> oddSafety(double odd, double outroA, double outroB) {
        double denominador = 1.0 - (1.0 / outroA) - (1.0 / outroB);
        // denominador <= 0 significa que as outras duas já somam >= 1 sozinhas — sem floor definido
        if (denominador <= 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("observacao", "Margem depende exclusivamente desta odd — qualquer queda elimina a arbitragem");
            return m;
        }

        double oddMin = 1.0 / denominador;
        double podeCair = round2(odd - oddMin);
        double margemPct = round2((podeCair / odd) * 100.0);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("minimaParaArbitragem", round2(oddMin));
        m.put("podeCair", podeCair);
        m.put("margemSeguranca", margemPct + "%");
        return m;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
