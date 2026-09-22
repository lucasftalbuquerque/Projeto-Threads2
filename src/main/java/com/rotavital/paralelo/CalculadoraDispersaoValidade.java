package com.rotavital.paralelo;

import com.rotavital.dominio.Bolsa;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Calcula {@link ResumoValidade} (medidas de tendencia central e dispersao
 * dos dias ate o vencimento) sobre uma massa de bolsas, em duas versoes:
 * sequencial e particionada por threads.
 *
 * <h2>Por que esta operacao e um bom caso de particionamento</h2>
 *
 * <p>Na escala nacional (milhoes de bolsas), o custo de {@code
 * IndicadorServico.dispersaoValidade} deixa de ser a consulta ao banco (uma
 * unica leitura em lote) e passa a ser as varreduras em memoria sobre a
 * lista inteira: filtrar vencidas O(n), somar O(n) e, principalmente,
 * ordenar para achar a mediana O(n log n) -- e a ordenacao domina o tempo
 * total. Cada bolsa e independente das demais para esse calculo (nenhuma
 * decisao sobre a bolsa X depende do resultado da bolsa Y), entao a lista
 * pode ser cortada em fatias contiguas e cada fatia processada por uma
 * thread separada, sem nenhum estado compartilhado durante o processamento.</p>
 *
 * <h2>Como a soma exata evita divergencia entre as duas versoes</h2>
 *
 * <p>Para que a versao sequencial e a paralela devolvam exatamente a mesma
 * resposta (arestas de ponto flutuante poderiam, em tese, mascarar uma
 * race condition), a combinacao das fatias usa apenas long: soma dos dias e
 * soma dos quadrados dos dias. A adicao de inteiros e associativa e
 * comutativa em aritmetica exata -- diferente da soma de double, que pode
 * mudar no ultimo bit conforme a ordem -- entao o total independe de quantas
 * fatias existirem ou da ordem em que as threads terminam. A media e o
 * desvio-padrao saem de uma unica formula fechada (formula computacional da
 * variancia: soma dos quadrados menos o quadrado da soma dividido por n)
 * aplicada aos mesmos long, e por isso os dois caminhos (1 fatia = versao
 * sequencial, N fatias = versao paralela) produzem o mesmo double, bit a
 * bit. A mediana usa uma intercalacao (k-way merge, como um merge sort) das
 * fatias ja ordenadas em vez de reordenar tudo de novo.</p>
 *
 * <p>Dado sintetico e didatico: os hemocomponentes e a validade seguem as
 * mesmas regras de {@code TipoHemocomponente}, mas a massa de bolsas do
 * benchmark ({@link GeradorMassaBolsasSintetica}) e gerada em memoria, sem
 * qualquer relacao com doador ou paciente real (LGPD).</p>
 */
public final class CalculadoraDispersaoValidade {

    /**
     * Versao sequencial (uma thread, sem ExecutorService): baseline para a
     * comparacao com {@link #calcularParalelo}. Complexidade O(n log n),
     * dominada pela ordenacao da fatia unica antes de achar a mediana.
     */
    public ResumoValidade calcularSequencial(List<Bolsa> bolsas, LocalDate hoje) {
        if (bolsas.isEmpty()) {
            return ResumoValidade.vazio();
        }
        FatiaParcial unica = processarFatia(bolsas, hoje);
        return combinar(List.of(unica));
    }

    /**
     * Versao com threads: particiona {@code bolsas} em ate {@code numThreads}
     * fatias contiguas e processa cada fatia em uma thread do pool, depois
     * combina os resultados parciais. Complexidade por thread
     * O((n/p) log(n/p)) para ordenar a propria fatia, mais O(n log p) para a
     * intercalacao final da mediana e O(p) para somar os totais -- por isso o
     * ganho nao e linear em p (ver docs/analise-paralelismo.md).
     *
     * @param numThreads quantidade de threads, 2 ou mais (para 1 thread use
     *                   {@link #calcularSequencial}, que nao paga o custo do
     *                   pool)
     */
    public ResumoValidade calcularParalelo(List<Bolsa> bolsas, LocalDate hoje, int numThreads) {
        if (numThreads < 2) {
            throw new IllegalArgumentException(
                    "calcularParalelo exige 2 ou mais threads; para 1 thread use calcularSequencial");
        }
        if (bolsas.isEmpty()) {
            return ResumoValidade.vazio();
        }

        int threads = Math.min(numThreads, bolsas.size());
        List<List<Bolsa>> fatias = particionar(bolsas, threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<FatiaParcial>> futuros = new ArrayList<>(threads);
            for (List<Bolsa> fatia : fatias) {
                futuros.add(pool.submit(() -> processarFatia(fatia, hoje)));
            }
            return combinar(colher(futuros));
        } finally {
            pool.shutdown();
        }
    }

    // --- Fase por fatia (roda dentro de cada thread, sem estado compartilhado) ---

    /**
     * Filtra as bolsas vencidas, converte a data de validade em "dias ate
     * vencer" e ja ordena a fatia -- a ordenacao por fatia e o que faz a
     * mediana final custar O(n log p) em vez de O(n log n) na intercalacao.
     */
    private FatiaParcial processarFatia(List<Bolsa> fatia, LocalDate hoje) {
        List<Long> dias = new ArrayList<>(fatia.size());
        long soma = 0;
        long somaQuadrados = 0;

        for (Bolsa bolsa : fatia) {
            if (!bolsa.estaVencida(hoje)) {
                long d = ChronoUnit.DAYS.between(hoje, bolsa.getDataValidade());
                dias.add(d);
                soma += d;
                somaQuadrados += d * d;
            }
        }
        Collections.sort(dias);
        return new FatiaParcial(dias, soma, somaQuadrados);
    }

    // --- Combinacao (roda na thread principal, custo O(p) + O(n log p)) ---

    private ResumoValidade combinar(List<FatiaParcial> fatias) {
        long n = 0;
        long soma = 0;
        long somaQuadrados = 0;
        for (FatiaParcial fatia : fatias) {
            n += fatia.dias().size();
            soma += fatia.soma();
            somaQuadrados += fatia.somaQuadrados();
        }
        if (n == 0) {
            return ResumoValidade.vazio();
        }

        double media = soma / (double) n;
        double desvio = 0.0;
        if (n >= 2) {
            // Formula computacional da variancia amostral: sum(x^2) - (sum(x))^2/n, sobre n-1.
            // Matematicamente igual a sum((x-media)^2)/(n-1), mas usa apenas os dois
            // acumuladores long (soma, somaQuadrados), que independem da ordem/particao.
            double variancia = (somaQuadrados - (soma * (double) soma) / n) / (n - 1);
            desvio = Math.sqrt(Math.max(0.0, variancia));
        }

        long min = fatias.stream()
                .filter(f -> !f.dias().isEmpty())
                .mapToLong(f -> f.dias().get(0))
                .min().orElse(0);
        long max = fatias.stream()
                .filter(f -> !f.dias().isEmpty())
                .mapToLong(f -> f.dias().get(f.dias().size() - 1))
                .max().orElse(0);
        double mediana = medianaPorIntercalacao(fatias, n);

        return new ResumoValidade(n, media, mediana, desvio, min, max);
    }

    /**
     * Acha a mediana intercalando as fatias ja ordenadas com uma fila de
     * prioridade (mesma tecnica da fila FEFO do dominio: a raiz da heap e
     * sempre o menor candidato). Para de avancar assim que a(s) posicao(oes)
     * central(is) sao encontradas, sem materializar a lista inteira
     * intercalada -- O(n log p) no pior caso, nao O(n log n).
     */
    private double medianaPorIntercalacao(List<FatiaParcial> fatias, long n) {
        PriorityQueue<Cursor> fila = new PriorityQueue<>();
        for (FatiaParcial f : fatias) {
            if (!f.dias().isEmpty()) {
                fila.add(new Cursor(f.dias()));
            }
        }

        boolean par = n % 2 == 0;
        long posicaoImpar = (n + 1) / 2;       // 1-indexada
        long posicaoParEsquerda = n / 2;       // 1-indexada
        long posicaoAtual = 0;
        long valorEsquerda = 0;

        while (!fila.isEmpty()) {
            Cursor menor = fila.poll();
            posicaoAtual++;
            long valor = menor.valorAtual();

            if (!par && posicaoAtual == posicaoImpar) {
                return valor;
            }
            if (par && posicaoAtual == posicaoParEsquerda) {
                valorEsquerda = valor;
            }
            if (par && posicaoAtual == posicaoParEsquerda + 1) {
                return (valorEsquerda + valor) / 2.0;
            }

            menor.avancar();
            if (menor.temProximo()) {
                fila.add(menor);
            }
        }

        throw new IllegalStateException("intercalacao nao encontrou a mediana - fatias inconsistentes");
    }

    // --- Particionamento e utilitarios de concorrencia ---

    /** Divide a lista em {@code partes} fatias contiguas o mais equilibradas possivel. */
    private List<List<Bolsa>> particionar(List<Bolsa> bolsas, int partes) {
        int total = bolsas.size();
        int tamanhoBase = total / partes;
        int resto = total % partes;

        List<List<Bolsa>> fatias = new ArrayList<>(partes);
        int inicio = 0;
        for (int i = 0; i < partes; i++) {
            int tamanho = tamanhoBase + (i < resto ? 1 : 0);
            int fim = inicio + tamanho;
            fatias.add(bolsas.subList(inicio, fim));
            inicio = fim;
        }
        return fatias;
    }

    private <T> List<T> colher(List<Future<T>> futuros) {
        List<T> resultado = new ArrayList<>(futuros.size());
        for (Future<T> futuro : futuros) {
            resultado.add(obter(futuro));
        }
        return resultado;
    }

    private <T> T obter(Future<T> futuro) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("thread de calculo interrompida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("falha ao calcular fatia em paralelo", e.getCause());
        }
    }

    /** Resultado parcial de uma fatia: dias validos ja ordenados, soma e soma dos quadrados. */
    private record FatiaParcial(List<Long> dias, long soma, long somaQuadrados) {
    }

    /** Cursor de leitura sobre uma fatia ja ordenada, usado na intercalacao k-way da mediana. */
    private static final class Cursor implements Comparable<Cursor> {
        private final List<Long> lista;
        private int indice;

        private Cursor(List<Long> lista) {
            this.lista = lista;
            this.indice = 0;
        }

        long valorAtual() {
            return lista.get(indice);
        }

        void avancar() {
            indice++;
        }

        boolean temProximo() {
            return indice < lista.size();
        }

        @Override
        public int compareTo(Cursor outro) {
            return Long.compare(this.valorAtual(), outro.valorAtual());
        }
    }
}
