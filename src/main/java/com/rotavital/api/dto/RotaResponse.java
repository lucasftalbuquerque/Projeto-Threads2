package com.rotavital.api.dto;

import com.rotavital.dominio.Rota;
import com.rotavital.dominio.enums.StatusRota;

import java.time.LocalDateTime;
import java.util.List;

public record RotaResponse(
        String id,
        String hemocentroOrigemId,
        String hospitalDestinoId,
        LocalDateTime previsaoSaida,
        LocalDateTime previsaoChegada,
        LocalDateTime saidaReal,
        LocalDateTime chegadaReal,
        StatusRota status,
        List<BolsaResponse> bolsas) {

    public static RotaResponse de(Rota rota) {
        return new RotaResponse(
                rota.getId(),
                rota.getOrigem() == null ? null : rota.getOrigem().getId(),
                rota.getDestino() == null ? null : rota.getDestino().getId(),
                rota.getDataHoraSaida(),
                rota.getDataHoraChegadaPrevista(),
                rota.getSaidaReal(),
                rota.getChegadaReal(),
                rota.getStatus(),
                rota.getBolsas().stream().map(BolsaResponse::de).toList());
    }
}
