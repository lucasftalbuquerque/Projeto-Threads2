package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.GrupoSanguineo;

public record CoberturaResponse(
        GrupoSanguineo grupoSanguineo,
        long disponivel,
        long demandado,
        Double coberturaPercentual,
        long deficit) {
}
