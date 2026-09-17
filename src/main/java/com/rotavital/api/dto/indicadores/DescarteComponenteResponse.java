package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.TipoHemocomponente;

public record DescarteComponenteResponse(
        TipoHemocomponente tipoHemocomponente,
        long total,
        long vencidas,
        double taxaDescarte) {
}
