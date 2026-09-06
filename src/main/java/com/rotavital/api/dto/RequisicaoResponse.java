package com.rotavital.api.dto;

import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRequisicao;

import java.time.LocalDateTime;
import java.util.List;

public record RequisicaoResponse(
        String id,
        String hospitalId,
        PrioridadeRequisicao prioridade,
        StatusRequisicao status,
        LocalDateTime dataHoraCriacao,
        LocalDateTime prazoEntrega,
        String observacao,
        List<ItemRequisicaoResponse> itens) {

    public static RequisicaoResponse de(Requisicao requisicao) {
        return new RequisicaoResponse(
                requisicao.getId(),
                requisicao.getHospital() == null ? null : requisicao.getHospital().getId(),
                requisicao.getPrioridade(),
                requisicao.getStatus(),
                requisicao.getDataHoraCriacao(),
                requisicao.getPrazoEntrega(),
                requisicao.getObservacao(),
                requisicao.getItens().stream().map(ItemRequisicaoResponse::de).toList());
    }
}
