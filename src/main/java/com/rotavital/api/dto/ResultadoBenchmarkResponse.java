package com.rotavital.api.dto;

import com.rotavital.paralelo.ResumoValidade;

/**
 * Resposta do endpoint de benchmark: alem do resultado estatistico
 * ({@code resultado}), devolve o tempo medido do calculo em si
 * ({@code tempoMs}) e os parametros usados na chamada, para comparar
 * diretamente sequencial x paralelo, tamanho de entrada e numero de threads.
 *
 * <p>{@code resultado} deve ser identico entre {@code modo=SEQUENCIAL} e
 * {@code modo=PARALELO} para a mesma amostra -- se divergir, ha uma race
 * condition na versao paralela (ver {@code CalculadoraDispersaoValidade}).</p>
 */
public record ResultadoBenchmarkResponse(
        int tamanhoAmostra,
        String modo,
        int threads,
        double tempoMs,
        ResumoValidade resultado) {
}
