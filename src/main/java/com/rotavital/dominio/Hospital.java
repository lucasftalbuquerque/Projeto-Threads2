package com.rotavital.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
public class Hospital extends Local {

    private String cnes;

    @OneToMany(mappedBy = "hospital")
    private final List<Requisicao> requisicoes = new ArrayList<>();

    protected Hospital() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Hospital(String id, String nome, String telefone, Endereco endereco, String cnes) {
        super(id, nome, telefone, endereco);
        this.cnes = cnes;
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
