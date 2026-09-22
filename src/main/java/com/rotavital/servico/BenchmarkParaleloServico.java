package com.rotavital.servico;

import com.rotavital.api.dto.ResultadoBenchmarkResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.paralelo.CalculadoraDispersaoValidade;
import com.rotavital.paralelo.GeradorMassaBolsasSintetica;
import com.rotavital.paralelo.ResumoValidade;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestra o benchmark de paralelismo da PI3: gera (ou reaproveita) a massa
 * sintetica pedida e mede, em milissegundos, apenas a etapa de calculo -- a
 * geracao da massa fica de fora da medicao de proposito, pelo mesmo motivo
 * que a carga inicial nao entra na medicao de {@code MedicaoDesempenhoTest}
 * (senao mediriamos o gerador de dados, nao o algoritmo de agregacao).
 *
 * <p>A massa e cacheada em memoria por (tamanhoAmostra, semente): chamadas
 * repetidas com os mesmos parametros mas threads diferentes comparam o
 * mesmo conjunto de dados, como pede o roteiro ("as duas versoes devem
 * devolver exatamente a mesma resposta"). Esse cache e deliberadamente
 * simples (um {@code Map} em memoria, sem limite de entradas nem
 * expiracao) -- adequado para o benchmark didatico desta atividade, mas o
 * primeiro ponto a rever se a operacao virar definitiva (ver
 * docs/analise-paralelismo.md, secao "quando 8 threads nao bastam").</p>
 *
 * <p>Dado sintetico, sem qualquer relacao com doador, paciente ou hospital
 * real (LGPD) -- ver {@link GeradorMassaBolsasSintetica}.</p>
 */
@Service
public class BenchmarkParaleloServico {

    /** Limite de seguranca para nao esgotar a heap com uma amostra descontrolada. */
    private static final int TAMANHO_MAXIMO_AMOSTRA = 2_000_000;

    private final CalculadoraDispersaoValidade calculadora = new CalculadoraDispersaoValidade();
    private final Map<String, List<Bolsa>> massasCacheadas = new ConcurrentHashMap<>();

    public ResultadoBenchmarkResponse medirDispersaoValidade(
            int tamanhoAmostra, int threads, long semente, LocalDate dataReferencia) {

        if (tamanhoAmostra <= 0) {
            throw new OperacaoInvalidaException("tamanhoAmostra deve ser maior que zero");
        }
        if (tamanhoAmostra > TAMANHO_MAXIMO_AMOSTRA) {
            throw new OperacaoInvalidaException(
                    "tamanhoAmostra nao pode passar de " + TAMANHO_MAXIMO_AMOSTRA);
        }
        if (threads < 1) {
            throw new OperacaoInvalidaException("threads deve ser 1 (sequencial) ou maior (paralelo)");
        }

        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> massa = massasCacheadas.computeIfAbsent(
                tamanhoAmostra + ":" + semente,
                chave -> GeradorMassaBolsasSintetica.gerar(tamanhoAmostra, semente, hoje));

        long inicio = System.nanoTime();
        ResumoValidade resultado = threads == 1
                ? calculadora.calcularSequencial(massa, hoje)
                : calculadora.calcularParalelo(massa, hoje, threads);
        double tempoMs = (System.nanoTime() - inicio) / 1_000_000.0;

        String modo = threads == 1 ? "SEQUENCIAL" : "PARALELO";
        return new ResultadoBenchmarkResponse(tamanhoAmostra, modo, threads, tempoMs, resultado);
    }
}
