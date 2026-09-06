package com.rotavital.api.dto;

import com.rotavital.dominio.enums.StatusBolsa;
import jakarta.validation.constraints.NotNull;

public record AtualizarStatusBolsaRequest(
        @NotNull(message = "status e obrigatorio")
        StatusBolsa status) {
}
