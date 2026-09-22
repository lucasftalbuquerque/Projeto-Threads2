package com.rotavital.api;

import com.rotavital.api.dto.ResultadoBenchmarkResponse;
import com.rotavital.servico.BenchmarkParaleloServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoint de benchmark de paralelismo (PI3): mede o tempo da agregacao
 * estatistica de validade -- a mesma logica de
 * {@code IndicadorServico.dispersaoValidade} (media, mediana, desvio-padrao
 * amostral, minimo e maximo dos dias ate o vencimento) -- comparando a
 * versao sequencial com a versao particionada por threads sobre uma massa
 * sintetica de escala nacional (ex.: 100 mil ou 1 milhao de bolsas).
 *
 * <p>Requisicao chega, o servidor gera/reaproveita a massa e calcula, a
 * resposta volta com o resultado e o tempo medido -- nao e um script
 * solto. Ver {@code docs/analise-paralelismo.md} para a justificativa da
 * escolha desta operacao, a metodologia de medicao e a analise dos
 * resultados.</p>
 *
 * <p>Dado sintetico e didatico: a massa gerada nao tem qualquer relacao com
 * doador, paciente ou hospital real (LGPD), e a compatibilidade/validade
 * seguem apenas a regra didatica do dominio, sem validar protocolo
 * hemoterapico real.</p>
 */
@Tag(name = "Benchmark", description = "Medicao de desempenho sequencial vs. paralelo (particionamento com threads)")
@RestController
@RequestMapping("/api/v1/benchmark")
public class BenchmarkController {

    private final BenchmarkParaleloServico benchmarkServico;

    public BenchmarkController(BenchmarkParaleloServico benchmarkServico) {
        this.benchmarkServico = benchmarkServico;
    }

    @Operation(
            summary = "Mede a dispersao de validade (media, mediana, desvio-padrao) sequencial ou em paralelo",
            description = "Gera (ou reaproveita) uma massa sintetica de 'tamanhoAmostra' bolsas e calcula as "
                    + "medidas de tendencia central e dispersao dos dias ate o vencimento. threads=1 roda a "
                    + "versao sequencial (baseline, sem ExecutorService); threads=2, 4 ou 8 roda a versao "
                    + "particionada. O campo 'resultado' deve sair identico entre os modos para a mesma "
                    + "amostra -- so 'tempoMs' muda.")
    @GetMapping("/dispersao-validade")
    public ResultadoBenchmarkResponse dispersaoValidade(
            @Parameter(description = "Quantidade de bolsas sinteticas geradas para o benchmark (ex.: 100000 ou 1000000)")
            @RequestParam(defaultValue = "100000") int tamanhoAmostra,
            @Parameter(description = "1 = versao sequencial; 2, 4 ou 8 = versao paralela com essa quantidade de threads")
            @RequestParam(defaultValue = "1") int threads,
            @Parameter(description = "Semente do gerador aleatorio, para reprodutibilidade entre chamadas")
            @RequestParam(defaultValue = "42") long semente,
            @Parameter(description = "Data de referencia para o calculo de vencimento (padrao: hoje)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia) {
        return benchmarkServico.medirDispersaoValidade(tamanhoAmostra, threads, semente, dataReferencia);
    }
}
