package com.rotavital.estruturas;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GrafoTest {

    @Test
    void grafoVazioNaoPossuiVertices() {
        Grafo<String> grafo = new Grafo<>();

        assertEquals(0, grafo.quantidadeVertices());
        assertTrue(grafo.listarVizinhos("A").isEmpty());
        assertTrue(grafo.vizinhosComPeso("A").isEmpty());
    }

    @Test
    void permiteInserirVerticeIsolado() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirVertice("Hemocentro Recife");

        assertTrue(grafo.contemVertice("Hemocentro Recife"));
        assertTrue(grafo.listarVizinhos("Hemocentro Recife").isEmpty());
    }

    @Test
    void grafoEhDirigidoPorPadrao() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("A", "B", 10.0);

        assertTrue(grafo.isDirecionado());
        assertEquals(Set.of("B"), grafo.listarVizinhos("A"));
        assertTrue(grafo.listarVizinhos("B").isEmpty());
    }

    @Test
    void arestaGuardaOPesoInformado() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("Hemocentro", "Hospital A", 12.5);

        assertEquals(12.5, grafo.pesoAresta("Hemocentro", "Hospital A"));
        assertTrue(grafo.contemAresta("Hemocentro", "Hospital A"));
    }

    @Test
    void pesoDeArestaInexistenteEhNulo() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("A", "B", 5.0);

        assertNull(grafo.pesoAresta("A", "C"));
        assertNull(grafo.pesoAresta("Z", "A"));
        assertFalse(grafo.contemAresta("B", "A"));
    }

    @Test
    void grafoNaoDirigidoCriaArestaNosDoisSentidos() {
        Grafo<String> grafo = new Grafo<>(false);

        grafo.inserirAresta("A", "B", 8.0);

        assertEquals(8.0, grafo.pesoAresta("A", "B"));
        assertEquals(8.0, grafo.pesoAresta("B", "A"));
    }

    @Test
    void reinserirArestaAtualizaOPeso() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("A", "B", 20.0);
        grafo.inserirAresta("A", "B", 15.0);

        assertEquals(1, grafo.listarVizinhos("A").size());
        assertEquals(15.0, grafo.pesoAresta("A", "B"));
    }

    @Test
    void rejeitaPesoNegativo() {
        Grafo<String> grafo = new Grafo<>();

        assertThrows(IllegalArgumentException.class,
                () -> grafo.inserirAresta("A", "B", -1.0));
    }

    @Test
    void rejeitaVerticeNulo() {
        Grafo<String> grafo = new Grafo<>();

        assertThrows(IllegalArgumentException.class, () -> grafo.inserirVertice(null));
        assertThrows(IllegalArgumentException.class, () -> grafo.inserirAresta(null, "B", 1.0));
        assertThrows(IllegalArgumentException.class, () -> grafo.inserirAresta("A", null, 1.0));
    }

    @Test
    void vizinhosComPesoDevolveTodasAsArestasDoVertice() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("Hemocentro", "Hospital A", 10.0);
        grafo.inserirAresta("Hemocentro", "Hospital B", 20.0);

        assertEquals(2, grafo.vizinhosComPeso("Hemocentro").size());
        assertEquals(10.0, grafo.vizinhosComPeso("Hemocentro").get("Hospital A"));
        assertEquals(20.0, grafo.vizinhosComPeso("Hemocentro").get("Hospital B"));
    }

    @Test
    void listarVerticesDevolveTodosOsVerticesInseridos() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("A", "B", 1.0);
        grafo.inserirAresta("B", "C", 2.0);
        grafo.inserirVertice("D");

        assertEquals(Set.of("A", "B", "C", "D"), grafo.listarVertices());
    }

    /**
     * Reproduz o exemplo de 5 unidades do escopo do grafo (PI3-13), onde o
     * caminho minimo do Hemocentro ao Hospital Destino e 23 minutos passando
     * por Hospital A e Hospital B. Aqui so verificamos que a estrutura guarda
     * os pesos corretamente; o calculo do caminho e o PI3-19.
     */
    @Test
    void representaAredeDeExemploDoEscopoDoGrafo() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("Hemocentro", "Hospital A", 10.0);
        grafo.inserirAresta("Hemocentro", "Hospital B", 20.0);
        grafo.inserirAresta("Hospital A", "Hospital B", 5.0);
        grafo.inserirAresta("Hospital A", "Hospital C", 15.0);
        grafo.inserirAresta("Hospital B", "Hospital Destino", 8.0);
        grafo.inserirAresta("Hospital C", "Hospital Destino", 5.0);

        assertEquals(5, grafo.quantidadeVertices());

        double caminhoEsperado = grafo.pesoAresta("Hemocentro", "Hospital A")
                + grafo.pesoAresta("Hospital A", "Hospital B")
                + grafo.pesoAresta("Hospital B", "Hospital Destino");
        assertEquals(23.0, caminhoEsperado);

        // Sendo dirigido, nao existe volta do Hospital A para o Hemocentro.
        assertFalse(grafo.contemAresta("Hospital A", "Hemocentro"));
    }
}
