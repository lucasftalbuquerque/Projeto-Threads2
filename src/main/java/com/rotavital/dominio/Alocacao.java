package com.rotavital.dominio;

import java.time.LocalDateTime;
import java.util.Objects;

public class Alocacao {

    private final String id;
    private final Bolsa bolsa;
    private final ItemRequisicao itemRequisicao;
    private final LocalDateTime dataHoraAlocacao;

    public Alocacao(String id, Bolsa bolsa, ItemRequisicao itemRequisicao,
                    LocalDateTime dataHoraAlocacao) {
        this.id = id;
        this.bolsa = bolsa;
        this.itemRequisicao = itemRequisicao;
        this.dataHoraAlocacao = dataHoraAlocacao;
    }

    public String getId()                      { return id; }
    public Bolsa getBolsa()                    { return bolsa; }
    public ItemRequisicao getItemRequisicao()  { return itemRequisicao; }
    public LocalDateTime getDataHoraAlocacao() { return dataHoraAlocacao; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alocacao)) return false;
        Alocacao alocacao = (Alocacao) o;
        return Objects.equals(id, alocacao.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Alocacao[" + id + "] bolsa=" + bolsa.getCodigoRastreio()
                + " -> item=" + itemRequisicao.getId();
    }
}
