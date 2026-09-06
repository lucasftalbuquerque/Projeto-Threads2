package com.rotavital.api.dto;

import com.rotavital.dominio.Alocacao;

import java.time.LocalDateTime;

public record AlocacaoResponse(
        String id,
        String itemRequisicaoId,
        String bolsaId,
        LocalDateTime alocadoEm) {

    public static AlocacaoResponse de(Alocacao alocacao) {
        return new AlocacaoResponse(
                alocacao.getId(),
                alocacao.getItemRequisicao().getId(),
                alocacao.getBolsa().getCodigoRastreio(),
                alocacao.getDataHoraAlocacao());
    }
}
