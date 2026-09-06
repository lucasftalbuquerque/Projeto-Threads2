package com.rotavital.api.dto;

import jakarta.validation.constraints.NotNull;

public record EmbarqueBolsaRequest(
        @NotNull(message = "bolsaId e obrigatorio")
        String bolsaId) {
}
