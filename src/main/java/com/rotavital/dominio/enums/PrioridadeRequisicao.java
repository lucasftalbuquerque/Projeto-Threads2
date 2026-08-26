package com.rotavital.dominio.enums;

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

    @Override
    public String toString() {
        return descricao;
    }
}
