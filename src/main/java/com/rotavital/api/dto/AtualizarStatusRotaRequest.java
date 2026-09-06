package com.rotavital.api.dto;

import com.rotavital.dominio.enums.StatusRota;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AtualizarStatusRotaRequest(
        @NotNull(message = "status e obrigatorio")
        StatusRota status,

        LocalDateTime saidaReal,
        LocalDateTime chegadaReal) {
}
