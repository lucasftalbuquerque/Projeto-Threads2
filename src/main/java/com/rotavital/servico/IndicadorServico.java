package com.rotavital.servico;

import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.api.dto.indicadores.BolsaCriticaResponse;
import com.rotavital.api.dto.indicadores.CoberturaResponse;
import com.rotavital.api.dto.indicadores.DescarteComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorTipoResponse;
import com.rotavital.api.dto.indicadores.ValidadeResponse;
import com.rotavital.api.dto.indicadores.ValidadeResponse.DispersaoComponenteDto;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servico responsavel pelo calculo dinamico de indicadores operacionais e estatisticos.
 *
 * <p>Porta a logica de analise estatistica de {@code dados/analise.py} sobre as entidades do banco,
 * garantindo fidelidade nas metricas de tendencia central, dispersao, descarte e cobertura.</p>
 */
@Service
@Transactional(readOnly = true)
public class IndicadorServico {

    private final BolsaRepository bolsaRepository;
    private final RequisicaoRepository requisicaoRepository;

    public IndicadorServico(BolsaRepository bolsaRepository, RequisicaoRepository requisicaoRepository) {
        this.bolsaRepository = bolsaRepository;
        this.requisicaoRepository = requisicaoRepository;
    }

    /**
     * Conta bolsas por grupo sanguineo com percentuais sobre o estoque total.
     */
    public List<EstoquePorTipoResponse> estoquePorTipoSanguineo() {
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        long total = bolsas.size();

        Map<GrupoSanguineo, Long> contagem = new EnumMap<>(GrupoSanguineo.class);
        for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
            contagem.put(grupo, 0L);
        }

        for (Bolsa b : bolsas) {
            if (b.getGrupoSanguineo() != null) {
                contagem.put(b.getGrupoSanguineo(), contagem.get(b.getGrupoSanguineo()) + 1);
            }
        }

        return contagem.entrySet().stream()
                .map(e -> {
                    double pct = total > 0 ? (100.0 * e.getValue() / total) : 0.0;
                    return new EstoquePorTipoResponse(e.getKey(), e.getValue(), arredondar1Casa(pct));
                })
                .sorted((a, b) -> Long.compare(b.quantidade(), a.quantidade()))
                .toList();
    }

    /**
     * Conta bolsas por componente com percentuais sobre o estoque total.
     */
    public List<EstoquePorComponenteResponse> estoquePorComponente() {
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        long total = bolsas.size();

        Map<TipoHemocomponente, Long> contagem = new EnumMap<>(TipoHemocomponente.class);
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            contagem.put(tipo, 0L);
        }

        for (Bolsa b : bolsas) {
            if (b.getTipo() != null) {
                contagem.put(b.getTipo(), contagem.get(b.getTipo()) + 1);
            }
        }

        return contagem.entrySet().stream()
                .map(e -> {
                    double pct = total > 0 ? (100.0 * e.getValue() / total) : 0.0;
                    return new EstoquePorComponenteResponse(e.getKey(), e.getValue(), arredondar1Casa(pct));
                })
                .sorted((a, b) -> Long.compare(b.quantidade(), a.quantidade()))
                .toList();
    }

    /**
     * Calcula total, quantidade vencida e taxa de descarte (%) por componente.
     */
    public List<DescarteComponenteResponse> taxaDescartePorComponente(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();

        Map<TipoHemocomponente, List<Bolsa>> porComponente = new EnumMap<>(TipoHemocomponente.class);
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            porComponente.put(tipo, new ArrayList<>());
        }

        for (Bolsa b : bolsas) {
            if (b.getTipo() != null) {
                porComponente.get(b.getTipo()).add(b);
            }
        }

        return porComponente.entrySet().stream()
                .map(e -> {
                    List<Bolsa> doComponente = e.getValue();
                    long total = doComponente.size();
                    long vencidas = doComponente.stream().filter(b -> b.estaVencida(hoje)).count();
                    double taxa = total > 0 ? (100.0 * vencidas / total) : 0.0;
                    return new DescarteComponenteResponse(e.getKey(), total, vencidas, arredondar1Casa(taxa));
                })
                .sorted((a, b) -> Double.compare(b.taxaDescarte(), a.taxaDescarte()))
                .toList();
    }

    /**
     * Medidas de tendencia central e dispersao (n, media, mediana, desvio-padrao amostral, min, max)
     * dos dias ate o vencimento considerando apenas as bolsas validas.
     */
    public ValidadeResponse dispersaoValidade(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();

        List<Long> diasGerais = bolsas.stream()
                .filter(b -> !b.estaVencida(hoje))
                .map(b -> ChronoUnit.DAYS.between(hoje, b.getDataValidade()))
                .toList();

        long n = diasGerais.size();
        double media = calcularMedia(diasGerais);
        double mediana = calcularMediana(diasGerais);
        double desvio = calcularDesvioPadraoAmostral(diasGerais, media);
        long min = diasGerais.isEmpty() ? 0 : Collections.min(diasGerais);
        long max = diasGerais.isEmpty() ? 0 : Collections.max(diasGerais);

        Map<TipoHemocomponente, DispersaoComponenteDto> porComponente = new LinkedHashMap<>();
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            List<Long> diasComp = bolsas.stream()
                    .filter(b -> b.getTipo() == tipo && !b.estaVencida(hoje))
                    .map(b -> ChronoUnit.DAYS.between(hoje, b.getDataValidade()))
                    .toList();

            if (diasComp.size() >= 2) {
                double mediaComp = calcularMedia(diasComp);
                double medianaComp = calcularMediana(diasComp);
                double desvioComp = calcularDesvioPadraoAmostral(diasComp, mediaComp);
                porComponente.put(tipo, new DispersaoComponenteDto(
                        diasComp.size(),
                        arredondar1Casa(mediaComp),
                        arredondar1Casa(medianaComp),
                        arredondar1Casa(desvioComp)
                ));
            }
        }

        return new ValidadeResponse(
                n,
                arredondar1Casa(media),
                arredondar1Casa(mediana),
                arredondar1Casa(desvio),
                min,
                max,
                porComponente
        );
    }

    /**
     * Bolsas validas que vencem dentro do limite de dias especificado (alvo prioritario do FEFO).
     */
    public BolsaCriticaResponse bolsasProximasDoVencimento(int limiteDias, LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> todas = bolsaRepository.findAllComHemocentro();

        List<Bolsa> validas = todas.stream()
                .filter(b -> !b.estaVencida(hoje))
                .toList();

        List<Bolsa> criticas = validas.stream()
                .filter(b -> {
                    long dias = ChronoUnit.DAYS.between(hoje, b.getDataValidade());
                    return dias <= limiteDias;
                })
                .toList();

        double percentual = validas.isEmpty() ? 0.0 : (100.0 * criticas.size() / validas.size());

        List<BolsaResponse> dtos = criticas.stream()
                .map(BolsaResponse::de)
                .toList();

        return new BolsaCriticaResponse(limiteDias, criticas.size(), arredondar1Casa(percentual), dtos);
    }

    /**
     * Razao entre o estoque disponivel (bolsas validas) e o total demandado por tipo sanguineo.
     */
    public List<CoberturaResponse> coberturaPorTipo(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        List<Requisicao> requisicoes = requisicaoRepository.findAllComHospitalEItens();

        Map<GrupoSanguineo, Long> disponivel = new EnumMap<>(GrupoSanguineo.class);
        Map<GrupoSanguineo, Long> demandado = new EnumMap<>(GrupoSanguineo.class);
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            disponivel.put(g, 0L);
            demandado.put(g, 0L);
        }

        for (Bolsa b : bolsas) {
            if (!b.estaVencida(hoje) && b.getGrupoSanguineo() != null) {
                disponivel.put(b.getGrupoSanguineo(), disponivel.get(b.getGrupoSanguineo()) + 1);
            }
        }

        for (Requisicao r : requisicoes) {
            for (ItemRequisicao item : r.getItens()) {
                if (item.getGrupoSanguineo() != null) {
                    demandado.put(item.getGrupoSanguineo(),
                            demandado.get(item.getGrupoSanguineo()) + item.getQuantidadeSolicitada());
                }
            }
        }

        List<CoberturaResponse> lista = new ArrayList<>();
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            long disp = disponivel.get(g);
            long dem = demandado.get(g);
            Double cobPct = dem > 0 ? arredondar1Casa(100.0 * disp / dem) : null;
            long deficit = Math.max(0, dem - disp);
            lista.add(new CoberturaResponse(g, disp, dem, cobPct, deficit));
        }

        lista.sort((a, b) -> {
            if (a.coberturaPercentual() == null && b.coberturaPercentual() == null) return 0;
            if (a.coberturaPercentual() == null) return 1;
            if (b.coberturaPercentual() == null) return -1;
            return Double.compare(a.coberturaPercentual(), b.coberturaPercentual());
        });

        return lista;
    }

    // Metodos auxiliares para estatistica descritiva

    private double calcularMedia(List<Long> valores) {
        if (valores.isEmpty()) return 0.0;
        double soma = 0;
        for (Long v : valores) soma += v;
        return soma / valores.size();
    }

    private double calcularMediana(List<Long> valores) {
        if (valores.isEmpty()) return 0.0;
        List<Long> ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);
        int n = ordenados.size();
        if (n % 2 == 1) {
            return ordenados.get(n / 2);
        } else {
            return (ordenados.get((n / 2) - 1) + ordenados.get(n / 2)) / 2.0;
        }
    }

    /**
     * Calcula o desvio-padrao amostral (divisao por N - 1), identico ao {@code statistics.stdev} do Python.
     */
    private double calcularDesvioPadraoAmostral(List<Long> valores, double media) {
        if (valores.size() < 2) return 0.0;
        double somaQuadrados = 0;
        for (Long v : valores) {
            double diff = v - media;
            somaQuadrados += diff * diff;
        }
        return Math.sqrt(somaQuadrados / (valores.size() - 1));
    }

    private double arredondar1Casa(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
