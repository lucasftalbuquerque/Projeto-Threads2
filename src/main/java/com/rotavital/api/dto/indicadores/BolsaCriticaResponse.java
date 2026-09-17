package com.rotavital.api.dto.indicadores;

import com.rotavital.api.dto.BolsaResponse;

import java.util.List;

public record BolsaCriticaResponse(
        int limiteDias,
        long quantidade,
        double percentualDoEstoqueValido,
        List<BolsaResponse> bolsas) {
}
