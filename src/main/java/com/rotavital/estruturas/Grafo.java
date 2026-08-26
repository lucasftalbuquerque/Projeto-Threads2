package com.rotavital.estruturas;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Grafo ponderado em memoria usando lista de adjacencia.
 *
 * <p>Segue as decisoes registradas no escopo do grafo (PI3-13):</p>
 * <ul>
 *   <li>vertice: unidade da rede (hemocentro ou hospital);</li>
 *   <li>aresta: trecho direto entre duas unidades;</li>
 *   <li>peso: tempo estimado de percurso, em minutos;</li>
 *   <li>grafo dirigido por padrao, ja que ida e volta entre duas unidades
 *       nao levam necessariamente o mesmo tempo.</li>
 * </ul>
 *
 * <p>A representacao e lista de adjacencia porque a rede e esparsa: cada
 * unidade se conecta a poucas vizinhas, entao o espaco fica em O(V + E) em vez
 * dos O(V^2) de uma matriz. O Dijkstra (PI3-19) itera sobre os vizinhos
 * diretos de cada vertice, o que essa representacao favorece.</p>
 */
public class Grafo<T> {

    private final Map<T, Map<T, Double>> adjacencias = new HashMap<>();
    private final boolean direcionado;

    /**
     * Cria um grafo dirigido, como definido no PI3-13.
     */
    public Grafo() {
        this(true);
    }

    public Grafo(boolean direcionado) {
        this.direcionado = direcionado;
    }

    public void inserirVertice(T vertice) {
        if (vertice == null) {
            throw new IllegalArgumentException("Vertice nao pode ser nulo");
        }
        adjacencias.putIfAbsent(vertice, new LinkedHashMap<>());
    }

    /**
     * Insere uma aresta entre dois vertices, criando-os se ainda nao existirem.
     *
     * @param pesoMinutos tempo estimado de percurso; nao pode ser negativo,
     *                    condicao exigida pelo Dijkstra
     */
    public void inserirAresta(T origem, T destino, double pesoMinutos) {
        if (origem == null || destino == null) {
            throw new IllegalArgumentException("Origem e destino nao podem ser nulos");
        }
        if (pesoMinutos < 0) {
            throw new IllegalArgumentException("Peso nao pode ser negativo: " + pesoMinutos);
        }

        inserirVertice(origem);
        inserirVertice(destino);

        adjacencias.get(origem).put(destino, pesoMinutos);
        if (!direcionado) {
            adjacencias.get(destino).put(origem, pesoMinutos);
        }
    }

    /**
     * Vizinhos diretos do vertice, sem os pesos.
     */
    public Set<T> listarVizinhos(T vertice) {
        Map<T, Double> vizinhos = adjacencias.get(vertice);
        if (vizinhos == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(vizinhos.keySet());
    }

    /**
     * Vizinhos diretos do vertice com o peso de cada aresta. E o que o
     * Dijkstra consome ao relaxar as arestas.
     */
    public Map<T, Double> vizinhosComPeso(T vertice) {
        Map<T, Double> vizinhos = adjacencias.get(vertice);
        if (vizinhos == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(vizinhos);
    }

    /**
     * Peso da aresta entre dois vertices.
     *
     * @return o peso, ou {@code null} se a aresta nao existir
     */
    public Double pesoAresta(T origem, T destino) {
        Map<T, Double> vizinhos = adjacencias.get(origem);
        if (vizinhos == null) {
            return null;
        }
        return vizinhos.get(destino);
    }

    public boolean contemAresta(T origem, T destino) {
        return pesoAresta(origem, destino) != null;
    }

    public boolean contemVertice(T vertice) {
        return adjacencias.containsKey(vertice);
    }

    /**
     * Todos os vertices do grafo. O Dijkstra precisa disso para inicializar
     * as distancias.
     */
    public Set<T> listarVertices() {
        return Collections.unmodifiableSet(adjacencias.keySet());
    }

    public int quantidadeVertices() {
        return adjacencias.size();
    }

    public boolean isDirecionado() {
        return direcionado;
    }
}
