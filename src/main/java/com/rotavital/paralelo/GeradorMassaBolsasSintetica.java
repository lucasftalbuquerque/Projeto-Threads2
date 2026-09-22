package com.rotavital.paralelo;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera, em memoria, uma massa sintetica de bolsas para o benchmark de
 * paralelismo (PI3). Nao persiste nada no banco de proposito: o objetivo e
 * medir o custo do algoritmo de agregacao estatistica em si, nao o JPA/
 * Hibernate (mesmo cuidado tomado em {@code MedicaoDesempenhoTest}).
 *
 * <p>100% sintetico: tipo, grupo sanguineo, volume e datas sao sorteados a
 * partir de uma semente fixa, sem nenhuma relacao com doador, paciente ou
 * hospital real (LGPD). A compatibilidade ABO/Rh usada aqui e apenas a
 * enumeracao didatica do dominio, nao um protocolo hemoterapico validado.</p>
 */
public final class GeradorMassaBolsasSintetica {

    private GeradorMassaBolsasSintetica() {
    }

    /**
     * @param quantidade numero de bolsas sinteticas a gerar (ex.: 100_000 ou 1_000_000)
     * @param semente    semente do gerador aleatorio, para reprodutibilidade entre execucoes
     * @param hoje       data de referencia usada para distribuir datas de coleta
     */
    public static List<Bolsa> gerar(int quantidade, long semente, LocalDate hoje) {
        Random sorteio = new Random(semente);
        GrupoSanguineo[] grupos = GrupoSanguineo.values();
        TipoHemocomponente[] tipos = TipoHemocomponente.values();

        // Uma unica unidade ficticia compartilhada: o benchmark mede a agregacao
        // estatistica sobre o estoque, nao o cadastro de hemocentros.
        Hemocentro unidadeSintetica = new Hemocentro(
                "HC-BENCH", "Hemocentro sintetico (benchmark de desempenho)", "00000000000",
                new Endereco("Rua Sintetica", "0", "Bairro Sintetico", "Cidade Sintetica",
                        "PE", "00000-000", 0.0, 0.0),
                "00000000000000");

        List<Bolsa> bolsas = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            TipoHemocomponente tipo = tipos[sorteio.nextInt(tipos.length)];
            GrupoSanguineo grupo = grupos[sorteio.nextInt(grupos.length)];

            // Ate 2x a validade do componente: parte da massa nasce vencida de
            // proposito, para o filtro de "bolsa valida" ter trabalho real a
            // fazer -- como aconteceria numa rede operando ha meses em escala
            // nacional, e nao um estoque recem-carregado.
            int diasAtras = sorteio.nextInt(Math.max(1, tipo.getValidadeDias() * 2));
            int volume = 200 + sorteio.nextInt(201);

            bolsas.add(new Bolsa(
                    "BENCH" + i,
                    tipo,
                    grupo,
                    volume,
                    hoje.minusDays(diasAtras),
                    unidadeSintetica));
        }
        return bolsas;
    }
}
