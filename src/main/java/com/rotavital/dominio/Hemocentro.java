package com.rotavital.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
public class Hemocentro extends Local {

    private String cnpj;

    @OneToMany(mappedBy = "hemocentroOrigem")
    private final List<Bolsa> estoque = new ArrayList<>();

    protected Hemocentro() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Hemocentro(String id, String nome, String telefone, Endereco endereco, String cnpj) {
        super(id, nome, telefone, endereco);
        this.cnpj = cnpj;
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
