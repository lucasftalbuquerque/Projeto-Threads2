package com.rotavital.dominio;

public abstract class Local {

    private final String id;
    private String nome;
    private String telefone;
    private Endereco endereco;

    protected Local(String id, String nome, String telefone, Endereco endereco) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    public String getId()         { return id; }
    public String getNome()       { return nome; }
    public String getTelefone()   { return telefone; }
    public Endereco getEndereco() { return endereco; }

    public void setNome(String nome)           { this.nome = nome; }
    public void setTelefone(String telefone)   { this.telefone = telefone; }
    public void setEndereco(Endereco endereco) { this.endereco = endereco; }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "] " + nome;
    }
}
