package com.rotavital.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Hospital extends Local {

    private String cnes;
    private final List<Requisicao> requisicoes;

    public Hospital(String id, String nome, String telefone, Endereco endereco, String cnes) {
        super(id, nome, telefone, endereco);
        this.cnes = cnes;
        this.requisicoes = new ArrayList<>();
    }

    public String getCnes() { return cnes; }
    public void setCnes(String cnes) { this.cnes = cnes; }

    public List<Requisicao> getRequisicoes() {
        return Collections.unmodifiableList(requisicoes);
    }

    public void adicionarRequisicao(Requisicao requisicao) {
        requisicoes.add(requisicao);
    }
}
