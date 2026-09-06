package com.rotavital.dominio.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PrioridadeRequisicao {

    ROTINA("Rotina"),
    URGENTE("Urgente"),
    EMERGENCIA("Emergencia");

    private final String descricao;

    PrioridadeRequisicao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * O contrato da API usa o nome da constante ({@code URGENTE}), nao a
     * descricao. Sem isto o Jackson seguiria o toString() abaixo, que existe
     * para exibicao, e o JSON sairia com texto legivel em vez do enum.
     */
    @JsonValue
    public String comoJson() {
        return name();
    }

    @Override
    public String toString() {
        return descricao;
    }
}
