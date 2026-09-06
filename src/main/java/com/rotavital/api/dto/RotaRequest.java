package com.rotavital.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RotaRequest(
        @NotNull(message = "hemocentroOrigemId e obrigatorio")
        String hemocentroOrigemId,

        @NotNull(message = "hospitalDestinoId e obrigatorio")
        String hospitalDestinoId,

        @NotNull(message = "previsaoSaida e obrigatoria")
        LocalDateTime previsaoSaida,

        @NotNull(message = "previsaoChegada e obrigatoria")
        LocalDateTime previsaoChegada) {
}
