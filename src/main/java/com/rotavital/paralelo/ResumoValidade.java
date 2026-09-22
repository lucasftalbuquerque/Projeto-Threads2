package com.rotavital.paralelo;

/**
 * Medidas de tendencia central e dispersao dos dias ate o vencimento de um
 * conjunto de bolsas validas: quantidade (n), media, mediana, desvio-padrao
 * amostral (divisao por n-1, igual ao {@code statistics.stdev} do Python),
 * minimo e maximo.
 *
 * <p>E o mesmo indicador de {@code IndicadorServico.dispersaoValidade}
 * (W06-EST), recalculado aqui de forma particionavel para o estudo de
 * paralelismo da PI3 (ver {@link CalculadoraDispersaoValidade}). A
 * compatibilidade ABO/Rh e a validade por hemocomponente usadas na massa de
 * dados sao didaticas, nao substituem protocolo hemoterapico real.</p>
 */
public record ResumoValidade(
        long n,
        double media,
        double mediana,
        double desvioPadraoAmostral,
        long min,
        long max) {

    private static final ResumoValidade VAZIO = new ResumoValidade(0, 0.0, 0.0, 0.0, 0, 0);

    public static ResumoValidade vazio() {
        return VAZIO;
    }
}
