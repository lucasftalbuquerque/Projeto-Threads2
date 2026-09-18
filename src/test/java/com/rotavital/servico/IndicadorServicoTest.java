package com.rotavital.servico;

import com.rotavital.api.dto.indicadores.BolsaCriticaResponse;
import com.rotavital.api.dto.indicadores.CoberturaResponse;
import com.rotavital.api.dto.indicadores.DescarteComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorTipoResponse;
import com.rotavital.api.dto.indicadores.ValidadeResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicadorServicoTest {

    @Mock
    private BolsaRepository bolsaRepository;

    @Mock
    private RequisicaoRepository requisicaoRepository;

    @InjectMocks
    private IndicadorServico indicadorServico;

    private final LocalDate hoje = LocalDate.of(2026, 9, 17);

    @Test
    @DisplayName("Deve calcular estoque por tipo sanguíneo e percentuais corretamente")
    void deveCalcularEstoquePorTipo() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        List<Bolsa> bolsas = List.of(
                new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h),
                new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h),
                new Bolsa("B3", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS, 450, hoje, h),
                new Bolsa("B4", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.B_NEG, 450, hoje, h)
        );

        when(bolsaRepository.findAllComHemocentro()).thenReturn(bolsas);

        List<EstoquePorTipoResponse> resultado = indicadorServico.estoquePorTipoSanguineo();

        assertThat(resultado).isNotEmpty();
        EstoquePorTipoResponse oPos = resultado.stream()
                .filter(r -> r.grupoSanguineo() == GrupoSanguineo.O_POS)
                .findFirst().orElseThrow();

        assertThat(oPos.quantidade()).isEqualTo(2);
        assertThat(oPos.percentual()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Deve calcular taxa de descarte por componente considerando bolsas vencidas")
    void deveCalcularTaxaDescartePorComponente() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        // Plaquetas valem 5 dias. Coletadas ha 10 dias ja estao vencidas. Coletadas hoje sao validas.
        Bolsa bVencida = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS, 250, hoje.minusDays(10), h);
        Bolsa bValida = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS, 250, hoje, h);

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(bVencida, bValida));

        List<DescarteComponenteResponse> resultado = indicadorServico.taxaDescartePorComponente(hoje);

        DescarteComponenteResponse plaquetas = resultado.stream()
                .filter(r -> r.tipoHemocomponente() == TipoHemocomponente.CONCENTRADO_PLAQUETAS)
                .findFirst().orElseThrow();

        assertThat(plaquetas.total()).isEqualTo(2);
        assertThat(plaquetas.vencidas()).isEqualTo(1);
        assertThat(plaquetas.taxaDescarte()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Deve calcular medidas de dispersao e tendencia central (media, mediana e desvio padrao amostral)")
    void deveCalcularDispersaoValidade() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        // Bolsas com diferentes validades
        Bolsa b1 = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h); // vence em 42 dias
        Bolsa b2 = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje.minusDays(10), h); // vence em 32 dias
        Bolsa b3 = new Bolsa("B3", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje.minusDays(20), h); // vence em 22 dias

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(b1, b2, b3));

        ValidadeResponse resultado = indicadorServico.dispersaoValidade(hoje);

        assertThat(resultado.n()).isEqualTo(3);
        // dias restantes: 42, 32, 22 -> media = 32.0, mediana = 32.0, desvio amostral = 10.0
        assertThat(resultado.mediaDias()).isEqualTo(32.0);
        assertThat(resultado.medianaDias()).isEqualTo(32.0);
        assertThat(resultado.desvioPadraoDias()).isEqualTo(10.0);
        assertThat(resultado.minimoDias()).isEqualTo(22);
        assertThat(resultado.maximoDias()).isEqualTo(42);
    }

    @Test
    @DisplayName("Deve filtrar bolsas criticas dentro do limite de dias configurado")
    void deveFiltrarBolsasCriticas() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        // Plaquetas valem 5 dias -> coletada hoje vence em 5 dias (critica <= 7 dias)
        Bolsa critica = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS, 250, hoje, h);
        // Hemacias valem 42 dias -> coletada hoje vence em 42 dias (nao critica)
        Bolsa naoCritica = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h);

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(critica, naoCritica));

        BolsaCriticaResponse resultado = indicadorServico.bolsasProximasDoVencimento(7, hoje);

        assertThat(resultado.quantidade()).isEqualTo(1);
        assertThat(resultado.percentualDoEstoqueValido()).isEqualTo(50.0);
        assertThat(resultado.bolsas()).hasSize(1);
        assertThat(resultado.bolsas().get(0).id()).isEqualTo("B1");
    }

    @Test
    @DisplayName("Deve calcular cobertura de demanda e deficit acumulado por tipo sanguíneo")
    void deveCalcularCoberturaDemanda() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        Hospital hosp = new Hospital("hosp-1", "Hospital 1", "999", null, "22.222.222/0001-22");

        Bolsa b1 = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h);
        Bolsa b2 = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h);

        Requisicao req = new Requisicao("req-1", hosp, PrioridadeRequisicao.ROTINA, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        req.adicionarItem(new ItemRequisicao("item-1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 5)); // demandou 5, tem 2

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(b1, b2));
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(List.of(req));

        List<CoberturaResponse> resultado = indicadorServico.coberturaPorTipo(hoje);

        CoberturaResponse oPos = resultado.stream()
                .filter(r -> r.grupoSanguineo() == GrupoSanguineo.O_POS)
                .findFirst().orElseThrow();

        assertThat(oPos.disponivel()).isEqualTo(2);
        assertThat(oPos.demandado()).isEqualTo(5);
        assertThat(oPos.coberturaPercentual()).isEqualTo(40.0);
        assertThat(oPos.deficit()).isEqualTo(3);
    }

    @Test
    @DisplayName("Deve lidar com casos de borda: listas vazias sem disparar excecao")
    void deveLidarComListasVazias() {
        when(bolsaRepository.findAllComHemocentro()).thenReturn(Collections.emptyList());
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(Collections.emptyList());

        ValidadeResponse val = indicadorServico.dispersaoValidade(hoje);
        assertThat(val.n()).isEqualTo(0);
        assertThat(val.mediaDias()).isEqualTo(0.0);
        assertThat(val.desvioPadraoDias()).isEqualTo(0.0);

        List<CoberturaResponse> cob = indicadorServico.coberturaPorTipo(hoje);
        assertThat(cob).allMatch(c -> c.disponivel() == 0 && c.demandado() == 0 && c.coberturaPercentual() == null);
    }
}
