package com.rotavital.api.dto;

import com.rotavital.dominio.enums.StatusRequisicao;
import jakarta.validation.constraints.NotNull;

public record AtualizarStatusRequisicaoRequest(
        @NotNull(message = "status e obrigatorio")
        StatusRequisicao status) {
}
