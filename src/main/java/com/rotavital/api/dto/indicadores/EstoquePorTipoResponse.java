package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.GrupoSanguineo;

public record EstoquePorTipoResponse(
        GrupoSanguineo grupoSanguineo,
        long quantidade,
        double percentual) {
}
