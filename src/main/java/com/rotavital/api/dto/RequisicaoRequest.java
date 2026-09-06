package com.rotavital.api.dto;

import com.rotavital.dominio.enums.PrioridadeRequisicao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record RequisicaoRequest(
        @NotNull(message = "hospitalId e obrigatorio")
        String hospitalId,

        @NotNull(message = "prioridade e obrigatoria")
        PrioridadeRequisicao prioridade,

        LocalDateTime prazoEntrega,

        String observacao,

        @NotEmpty(message = "a requisicao precisa de pelo menos um item")
        @Valid
        List<ItemRequisicaoRequest> itens) {
}
