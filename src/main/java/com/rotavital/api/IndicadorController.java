package com.rotavital.api;

import com.rotavital.api.dto.indicadores.BolsaCriticaResponse;
import com.rotavital.api.dto.indicadores.CoberturaResponse;
import com.rotavital.api.dto.indicadores.DescarteComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorTipoResponse;
import com.rotavital.api.dto.indicadores.ValidadeResponse;
import com.rotavital.servico.IndicadorServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints para consulta de indicadores operacionais e estatisticos de hemocomponentes.
 *
 * <p>Exposicao das metricas analiticas portadas de {@code dados/analise.py} sob o path
 * {@code /api/v1/indicadores/*}.</p>
 */
@Tag(name = "Indicadores", description = "Indicadores analiticos de estoque, validade, descarte e cobertura de demanda")
@RestController
@RequestMapping("/api/v1/indicadores")
public class IndicadorController {

    private final IndicadorServico indicadorServico;

    public IndicadorController(IndicadorServico indicadorServico) {
        this.indicadorServico = indicadorServico;
    }

    @Operation(summary = "Distribuicao do estoque por tipo sanguineo",
               description = "Retorna a contagem e proporcao percentual do estoque de bolsas por grupo sanguineo.")
    @GetMapping("/estoque-por-tipo")
    public List<EstoquePorTipoResponse> estoquePorTipo() {
        return indicadorServico.estoquePorTipoSanguineo();
    }

    @Operation(summary = "Distribuicao do estoque por hemocomponente",
               description = "Retorna a contagem e proporcao percentual do estoque de bolsas por tipo de hemocomponente.")
    @GetMapping("/estoque-por-componente")
    public List<EstoquePorComponenteResponse> estoquePorComponente() {
        return indicadorServico.estoquePorComponente();
    }

    @Operation(summary = "Taxa de descarte por vencimento",
               description = "Calcula total, bolsas vencidas e taxa de descarte percentual para cada hemocomponente.")
    @GetMapping("/descarte")
    public List<DescarteComponenteResponse> taxaDescarte(
            @Parameter(description = "Data de referencia para calculo do vencimento (padrao: hoje)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia) {
        return indicadorServico.taxaDescartePorComponente(dataReferencia);
    }

    @Operation(summary = "Medidas de tendencia central e dispersao da validade",
               description = "Média, mediana, desvio-padrao amostral, minimo e maximo dos dias restantes ate o vencimento (geral e por componente).")
    @GetMapping("/validade")
    public ValidadeResponse dispersaoValidade(
            @Parameter(description = "Data de referencia para calculo do vencimento (padrao: hoje)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia) {
        return indicadorServico.dispersaoValidade(dataReferencia);
    }

    @Operation(summary = "Bolsas criticas proximas do vencimento",
               description = "Lista e totaliza bolsas validas com vencimento dentro do limite de dias especificado (alvo FEFO).")
    @GetMapping("/criticas")
    public BolsaCriticaResponse bolsasCriticas(
            @Parameter(description = "Limite de dias restantes ate a validade (padrao: 7)")
            @RequestParam(defaultValue = "7") int limiteDias,
            @Parameter(description = "Data de referencia para calculo do vencimento (padrao: hoje)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia) {
        return indicadorServico.bolsasProximasDoVencimento(limiteDias, dataReferencia);
    }

    @Operation(summary = "Cobertura de demanda por tipo sanguineo",
               description = "Compara estoque valido disponivel versus quantidade solicitada em requisicoes, calculando percentual de cobertura e deficit.")
    @GetMapping("/cobertura")
    public List<CoberturaResponse> coberturaPorTipo(
            @Parameter(description = "Data de referencia para calculo do vencimento (padrao: hoje)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia) {
        return indicadorServico.coberturaPorTipo(dataReferencia);
    }
}
