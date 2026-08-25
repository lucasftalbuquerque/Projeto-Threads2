package com.rotavital.dominio;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.util.Objects;

public class ItemRequisicao {

    private final String id;
    private final TipoHemocomponente tipo;
    private final GrupoSanguineo grupoSanguineo;
    private final int quantidadeSolicitada;
    private int quantidadeAlocada;

    public ItemRequisicao(String id, TipoHemocomponente tipo,
                          GrupoSanguineo grupoSanguineo, int quantidadeSolicitada) {
        this.id = id;
        this.tipo = tipo;
        this.grupoSanguineo = grupoSanguineo;
        this.quantidadeSolicitada = quantidadeSolicitada;
        this.quantidadeAlocada = 0;
    }

    public String getId()                     { return id; }
    public TipoHemocomponente getTipo()       { return tipo; }
    public GrupoSanguineo getGrupoSanguineo() { return grupoSanguineo; }
    public int getQuantidadeSolicitada()      { return quantidadeSolicitada; }
    public int getQuantidadeAlocada()         { return quantidadeAlocada; }

    public void incrementarAlocada() {
        this.quantidadeAlocada++;
    }

    public boolean estaAtendido() {
        return quantidadeAlocada >= quantidadeSolicitada;
    }

    public int quantidadePendente() {
        return quantidadeSolicitada - quantidadeAlocada;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemRequisicao)) return false;
        ItemRequisicao item = (ItemRequisicao) o;
        return Objects.equals(id, item.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
