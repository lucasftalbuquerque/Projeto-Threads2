package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.FilaPrioridadeFefo;
import com.rotavital.estruturas.IndiceEstoque;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Selecao FEFO (First Expired, First Out) da bolsa a ser alocada em uma
 * requisicao.
 *
 * <p>Atende os criterios de aceite da PI3-19 sobre alocacao: a bolsa escolhida
 * e sempre a de validade mais proxima entre as que podem ser alocadas, bolsa
 * vencida ou indisponivel nunca e escolhida, e o desempate e deterministico.</p>
 *
 * <p>FEFO existe para reduzir descarte por vencimento: consumindo primeiro o
 * que vence antes, sobra no estoque o que tem mais prazo. E o mesmo motivo
 * pelo qual o peso do grafo e tempo e nao distancia (secao 3 do escopo do
 * grafo) - o que esta em jogo e a validade da bolsa.</p>
 *
 * <p>A ordenacao reusa a {@link FilaPrioridadeFefo}, que ja ordena por data de
 * validade com desempate por codigo de rastreio. Escrever um comparador novo
 * aqui abriria espaco para as duas ordens divergirem.</p>
 *
 * <p><b>Complexidade:</b> O(n log n) para n bolsas candidatas, custo de
 * alimentar a fila de prioridade. O filtro de alocaveis e O(n) e nao altera a
 * ordem de grandeza.</p>
 */
public final class SelecaoFefo {

    private SelecaoFefo() {
        throw new AssertionError("Classe utilitaria, nao deve ser instanciada");
    }

    /**
     * Indica se a bolsa pode ser alocada na data de referencia.
     *
     * <p>Sao duas condicoes independentes: a bolsa nao pode estar vencida e
     * precisa estar {@link StatusBolsa#DISPONIVEL}. Bolsa ja reservada, em
     * transito, entregue ou descartada esta comprometida com outra requisicao
     * ou fora de circulacao, e por isso nao entra na selecao mesmo que ainda
     * esteja dentro da validade.</p>
     *
     * <p>Bolsa nula nao e alocavel, em vez de lancar excecao: lista de
     * candidatas com buraco e situacao de dado, nao erro de programa.</p>
     *
     * <p>Vale registrar a borda, porque e regra de negocio: bolsa cuja
     * validade e exatamente a data de referencia ainda e alocavel. Quem decide
     * isso e o {@link Bolsa#estaVencida(LocalDate)}, que usa
     * {@code isAfter} - a bolsa so vence no dia seguinte a validade.</p>
     */
    public static boolean alocavel(Bolsa bolsa, LocalDate dataReferencia) {
        if (bolsa == null) {
            return false;
        }
        return !bolsa.estaVencida(dataReferencia)
                && bolsa.getStatus() == StatusBolsa.DISPONIVEL;
    }

    /**
     * Candidatas alocaveis em ordem FEFO, da validade mais proxima para a mais
     * distante.
     *
     * <p>As nao alocaveis sao descartadas, nao apenas rebaixadas: uma bolsa
     * vencida nao e uma opcao pior, e uma opcao invalida.</p>
     *
     * @return lista imutavel; vazia se a entrada for nula, vazia ou se nenhuma
     *         candidata for alocavel
     */
    public static List<Bolsa> ordenar(List<Bolsa> candidatas, LocalDate dataReferencia) {
        FilaPrioridadeFefo fila = filaDeAlocaveis(candidatas, dataReferencia);

        List<Bolsa> ordenadas = new ArrayList<>(fila.tamanho());
        while (!fila.estaVazia()) {
            ordenadas.add(fila.retirarProxima());
        }

        return List.copyOf(ordenadas);
    }

    /**
     * A bolsa a ser alocada: a primeira da ordem FEFO entre as alocaveis.
     *
     * <p>Devolve {@link Optional#empty()} quando nenhuma candidata serve, em
     * vez de {@code null} ou excecao. Estoque sem bolsa utilizavel e situacao
     * normal da operacao, e quem chama decide o que fazer com isso.</p>
     */
    public static Optional<Bolsa> escolher(List<Bolsa> candidatas, LocalDate dataReferencia) {
        // consultarProxima() ja devolve o topo da fila, entao nao ha por que
        // materializar a ordem inteira so para ler o primeiro elemento.
        return Optional.ofNullable(filaDeAlocaveis(candidatas, dataReferencia).consultarProxima());
    }

    /**
     * A bolsa a ser alocada para um grupo sanguineo e tipo de hemocomponente,
     * buscando no indice de estoque.
     *
     * <p>E a forma usada pelo fluxo de requisicao: o indice resolve "quais
     * bolsas existem desta combinacao" em O(1), e a selecao FEFO decide qual
     * delas sai.</p>
     *
     * <p>Esta sobrecarga nao faz compatibilidade entre grupos sanguineos:
     * busca exatamente o grupo pedido. Doador universal e assunto de outra
     * regra, fora do escopo desta selecao.</p>
     *
     * @return {@link Optional#empty()} se o indice for nulo, se nao houver
     *         bolsa da combinacao pedida ou se nenhuma for alocavel
     */
    public static Optional<Bolsa> escolher(IndiceEstoque estoque,
                                           GrupoSanguineo grupo,
                                           TipoHemocomponente tipo,
                                           LocalDate dataReferencia) {
        if (estoque == null) {
            return Optional.empty();
        }
        return escolher(estoque.buscar(grupo, tipo), dataReferencia);
    }

    /**
     * Fila FEFO contendo apenas as candidatas alocaveis.
     *
     * <p>E o unico lugar onde o filtro e a ordenacao se encontram, para que
     * {@link #ordenar(List, LocalDate)} e {@link #escolher(List, LocalDate)}
     * nao possam divergir na regra.</p>
     */
    private static FilaPrioridadeFefo filaDeAlocaveis(List<Bolsa> candidatas,
                                                      LocalDate dataReferencia) {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();

        if (candidatas == null || candidatas.isEmpty()) {
            return fila;
        }

        // ATENCAO: o filtro vem ANTES da ordenacao, e essa ordem nao pode ser
        // invertida. FEFO poe no topo quem vence primeiro, e uma bolsa vencida
        // vence antes de todas as validas. Se a fila fosse montada com todas as
        // candidatas para so depois descartar as nao alocaveis, a vencida
        // ocuparia o topo em toda selecao. Filtrando aqui, ela nunca entra.
        for (Bolsa candidata : candidatas) {
            if (alocavel(candidata, dataReferencia)) {
                fila.adicionar(candidata);
            }
        }

        return fila;
    }
}
