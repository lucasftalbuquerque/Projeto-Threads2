package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.TipoHemocomponente;

import java.util.Map;

public record ValidadeResponse(
        long n,
        double mediaDias,
        double medianaDias,
        double desvioPadraoDias,
        long minimoDias,
        long maximoDias,
        Map<TipoHemocomponente, DispersaoComponenteDto> porComponente) {

    public record DispersaoComponenteDto(
            long n,
            double mediaDias,
            double medianaDias,
            double desvioPadraoDias) {
    }
}
