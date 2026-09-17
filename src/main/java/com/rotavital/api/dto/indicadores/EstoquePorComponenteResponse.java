package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.TipoHemocomponente;

public record EstoquePorComponenteResponse(
        TipoHemocomponente tipoHemocomponente,
        long quantidade,
        double percentual) {
}
