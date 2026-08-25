package com.rotavital.dominio;

import java.time.LocalDateTime;

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
    public String toString() {
        return "Alocacao[" + id + "] bolsa=" + bolsa.getCodigoRastreio()
                + " -> item=" + itemRequisicao.getId();
    }
}
