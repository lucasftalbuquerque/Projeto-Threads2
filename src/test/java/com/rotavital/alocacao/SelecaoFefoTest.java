package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.IndiceEstoque;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelecaoFefoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 10);

    @Test
    void entreDuasEquivalentesSaiADeValidadeMaisProxima() {
        Bolsa maisNova = bolsaQueVenceEm("B001", HOJE.plusDays(30));
        Bolsa maisProxima = bolsaQueVenceEm("B002", HOJE.plusDays(3));

        Optional<Bolsa> escolhida = SelecaoFefo.escolher(List.of(maisNova, maisProxima), HOJE);

        assertTrue(escolhida.isPresent());
        assertEquals("B002", escolhida.get().getCodigoRastreio());
    }

    /**
     * O caso que justifica filtrar antes de ordenar: a vencida e a de validade
     * mais proxima de todas, entao ficaria no topo da fila FEFO se entrasse
     * nela.
     */
    @Test
    void bolsaVencidaNuncaEhEscolhidaMesmoSendoAMaisProxima() {
        Bolsa vencida = bolsaQueVenceEm("B001", HOJE.minusDays(1));
        Bolsa valida = bolsaQueVenceEm("B002", HOJE.plusDays(20));

        Optional<Bolsa> escolhida = SelecaoFefo.escolher(List.of(vencida, valida), HOJE);

        assertTrue(escolhida.isPresent());
        assertEquals("B002", escolhida.get().getCodigoRastreio());
        assertFalse(SelecaoFefo.alocavel(vencida, HOJE));
    }

    /**
     * Estoque inteiro vencido: nao ha o que alocar, e a resposta e tratada.
     */
    @Test
    void todasVencidasDevolveOptionalVazio() {
        List<Bolsa> candidatas = List.of(
                bolsaQueVenceEm("B001", HOJE.minusDays(1)),
                bolsaQueVenceEm("B002", HOJE.minusDays(10)));

        assertTrue(SelecaoFefo.escolher(candidatas, HOJE).isEmpty());
        assertTrue(SelecaoFefo.ordenar(candidatas, HOJE).isEmpty());
    }

    /**
     * Bolsa comprometida com outra requisicao, ou ja fora de circulacao, nao
     * volta para a selecao mesmo dentro da validade.
     */
    @Test
    void bolsaComStatusDiferenteDeDisponivelNuncaEhEscolhida() {
        List<StatusBolsa> indisponiveis = List.of(
                StatusBolsa.RESERVADA, StatusBolsa.EM_TRANSITO,
                StatusBolsa.ENTREGUE, StatusBolsa.DESCARTADA);

        for (StatusBolsa status : indisponiveis) {
            Bolsa comprometida = bolsaQueVenceEm("B001", HOJE.plusDays(1));
            comprometida.setStatus(status);
            Bolsa disponivel = bolsaQueVenceEm("B002", HOJE.plusDays(30));

            Optional<Bolsa> escolhida =
                    SelecaoFefo.escolher(List.of(comprometida, disponivel), HOJE);

            assertFalse(SelecaoFefo.alocavel(comprometida, HOJE),
                    "Status " + status + " nao deveria ser alocavel");
            assertEquals("B002", escolhida.orElseThrow().getCodigoRastreio(),
                    "Status " + status + " foi escolhido indevidamente");
        }
    }

    /**
     * Duas bolsas com a mesma validade nao podem sair em ordem que dependa de
     * como a lista chegou: o desempate por codigo de rastreio tem de dar
     * sempre o mesmo resultado, senao a alocacao vira loteria e nao ha como
     * auditar a escolha.
     */
    @Test
    void selecaoEhDeterministicaQuandoAsValidadesEmpatam() {
        LocalDate mesmaValidade = HOJE.plusDays(7);
        List<Bolsa> candidatas = new ArrayList<>(List.of(
                bolsaQueVenceEm("B001", mesmaValidade),
                bolsaQueVenceEm("B002", mesmaValidade),
                bolsaQueVenceEm("B003", mesmaValidade)));

        Random embaralhador = new Random(42);
        Set<String> escolhidos = new HashSet<>();
        for (int execucao = 0; execucao < 100; execucao++) {
            Collections.shuffle(candidatas, embaralhador);
            escolhidos.add(SelecaoFefo.escolher(candidatas, HOJE).orElseThrow().getCodigoRastreio());
        }

        assertEquals(Set.of("B001"), escolhidos,
                "A escolha variou conforme a ordem de entrada");
    }

    /**
     * Requisicao sem candidatas: resposta tratada, nao excecao.
     */
    @Test
    void listaVaziaOuNulaDevolveOptionalVazio() {
        assertTrue(SelecaoFefo.escolher(List.of(), HOJE).isEmpty());
        assertTrue(SelecaoFefo.escolher((List<Bolsa>) null, HOJE).isEmpty());
        assertTrue(SelecaoFefo.ordenar(null, HOJE).isEmpty());
        assertTrue(SelecaoFefo.escolher(null, GrupoSanguineo.O_POS,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE).isEmpty());
    }

    /**
     * Regra de negocio explicita: a bolsa vence no dia seguinte a validade,
     * nao no proprio dia. Como {@code estaVencida} usa {@code isAfter}, uma
     * bolsa cuja validade e exatamente a data de referencia ainda pode ser
     * alocada - e, sendo a mais proxima do vencimento, e justamente a que FEFO
     * manda usar primeiro.
     */
    @Test
    void bolsaQueVenceHojeAindaEhAlocavel() {
        Bolsa venceHoje = bolsaQueVenceEm("B001", HOJE);
        Bolsa venceDepois = bolsaQueVenceEm("B002", HOJE.plusDays(5));
        Bolsa venceuOntem = bolsaQueVenceEm("B003", HOJE.minusDays(1));

        assertTrue(SelecaoFefo.alocavel(venceHoje, HOJE));
        assertFalse(SelecaoFefo.alocavel(venceuOntem, HOJE));

        Optional<Bolsa> escolhida =
                SelecaoFefo.escolher(List.of(venceDepois, venceHoje, venceuOntem), HOJE);
        assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio());
    }

    @Test
    void ordenarDevolveAlocaveisEmOrdemDeValidadeEOmiteAsDemais() {
        Bolsa vencida = bolsaQueVenceEm("B001", HOJE.minusDays(2));
        Bolsa reservada = bolsaQueVenceEm("B002", HOJE.plusDays(1));
        reservada.setStatus(StatusBolsa.RESERVADA);
        Bolsa terceira = bolsaQueVenceEm("B003", HOJE.plusDays(30));
        Bolsa primeira = bolsaQueVenceEm("B004", HOJE.plusDays(2));
        Bolsa segunda = bolsaQueVenceEm("B005", HOJE.plusDays(10));

        List<Bolsa> ordenadas = SelecaoFefo.ordenar(
                List.of(vencida, reservada, terceira, primeira, segunda), HOJE);

        assertEquals(List.of("B004", "B005", "B003"),
                ordenadas.stream().map(Bolsa::getCodigoRastreio).toList());
        assertThrows(UnsupportedOperationException.class, () -> ordenadas.add(terceira));
    }

    /**
     * A busca no indice tem de respeitar grupo e tipo: transfundir o
     * hemocomponente errado, ou grupo incompativel, e dano ao paciente.
     */
    @Test
    void escolherPeloIndiceIgnoraOutroGrupoEOutroTipo() {
        IndiceEstoque estoque = new IndiceEstoque();

        Bolsa alvo = bolsaQueVenceEm("B001", HOJE.plusDays(20),
                GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS);
        Bolsa outroGrupo = bolsaQueVenceEm("B002", HOJE.plusDays(1),
                GrupoSanguineo.A_POS, TipoHemocomponente.CONCENTRADO_HEMACIAS);
        Bolsa outroTipo = bolsaQueVenceEm("B003", HOJE.plusDays(1),
                GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_PLAQUETAS);

        estoque.adicionar(alvo);
        estoque.adicionar(outroGrupo);
        estoque.adicionar(outroTipo);

        Optional<Bolsa> escolhida = SelecaoFefo.escolher(estoque,
                GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio());

        // Combinacao que nao existe no estoque tambem devolve resposta tratada.
        assertTrue(SelecaoFefo.escolher(estoque, GrupoSanguineo.AB_NEG,
                TipoHemocomponente.CRIOPRECIPITADO, HOJE).isEmpty());
    }

    private Bolsa bolsaQueVenceEm(String codigo, LocalDate dataValidade) {
        return bolsaQueVenceEm(codigo, dataValidade,
                GrupoSanguineo.O_POS, TipoHemocomponente.CONCENTRADO_HEMACIAS);
    }

    /**
     * A validade nao e informada direto no construtor: a Bolsa a deriva de
     * {@code dataColeta + validade do tipo}. Entao a coleta e calculada de
     * tras para frente, para que o teste possa fixar a validade que quer.
     */
    private Bolsa bolsaQueVenceEm(String codigo, LocalDate dataValidade,
                                  GrupoSanguineo grupo, TipoHemocomponente tipo) {
        Hemocentro hemocentro = new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100"
        );

        LocalDate dataColeta = dataValidade.minusDays(tipo.getValidadeDias());

        Bolsa bolsa = new Bolsa(codigo, tipo, grupo, 450, dataColeta, hemocentro);
        assertEquals(dataValidade, bolsa.getDataValidade(),
                "Fixture nao produziu a validade pedida");
        return bolsa;
    }
}
