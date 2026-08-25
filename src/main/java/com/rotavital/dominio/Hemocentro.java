package com.rotavital.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Hemocentro extends Local {

    private String cnpj;
    private final List<Bolsa> estoque;

    public Hemocentro(String id, String nome, String telefone, Endereco endereco, String cnpj) {
        super(id, nome, telefone, endereco);
        this.cnpj = cnpj;
        this.estoque = new ArrayList<>();
    }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public List<Bolsa> getEstoque() {
        return Collections.unmodifiableList(estoque);
    }

    public void adicionarBolsa(Bolsa bolsa) {
        estoque.add(bolsa);
    }

    public boolean removerBolsa(Bolsa bolsa) {
        return estoque.remove(bolsa);
    }
}
