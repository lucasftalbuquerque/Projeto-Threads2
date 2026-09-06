package com.rotavital.api.dto;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.time.LocalDate;

public record BolsaResponse(
        String id,
        String hemocentroId,
        TipoHemocomponente tipoHemocomponente,
        GrupoSanguineo grupoSanguineo,
        int volumeMl,
        LocalDate dataColeta,
        LocalDate dataValidade,
        StatusBolsa status) {

    public static BolsaResponse de(Bolsa bolsa) {
        return new BolsaResponse(
                bolsa.getCodigoRastreio(),
                bolsa.getHemocentroOrigem() == null ? null : bolsa.getHemocentroOrigem().getId(),
                bolsa.getTipo(),
                bolsa.getGrupoSanguineo(),
                bolsa.getVolumeMl(),
                bolsa.getDataColeta(),
                bolsa.getDataValidade(),
                bolsa.getStatus());
    }
}
