package com.rotavital.api.dto;

import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

public record ItemRequisicaoResponse(
        String id,
        TipoHemocomponente tipoHemocomponente,
        GrupoSanguineo grupoSanguineo,
        int quantidadeSolicitada,
        int quantidadeAlocada,
        String status) {

    public static ItemRequisicaoResponse de(ItemRequisicao item) {
        return new ItemRequisicaoResponse(
                item.getId(),
                item.getTipo(),
                item.getGrupoSanguineo(),
                item.getQuantidadeSolicitada(),
                item.getQuantidadeAlocada(),
                statusDe(item));
    }

    /**
     * O contrato pede um status calculado para o item. E derivado das
     * alocacoes, entao nao vira coluna no banco: e computado na resposta.
     */
    private static String statusDe(ItemRequisicao item) {
        if (item.estaAtendido()) {
            return "ALOCADO";
        }
        return item.getQuantidadeAlocada() > 0 ? "PARCIALMENTE_ALOCADO" : "PENDENTE";
    }
}
