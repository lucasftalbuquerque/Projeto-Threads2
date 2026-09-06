package com.rotavital.dominio.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GrupoSanguineo {

    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-");

    private final String descricao;

    GrupoSanguineo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * O contrato da API usa o nome da constante ({@code O_POS}), nao a
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
