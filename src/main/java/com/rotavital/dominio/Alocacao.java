package com.rotavital.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
public class Alocacao {

    @Id
    private String id;

    @OneToOne
    @JoinColumn(name = "bolsa_id")
    private Bolsa bolsa;

    @ManyToOne
    @JoinColumn(name = "item_requisicao_id")
    private ItemRequisicao itemRequisicao;

    private LocalDateTime dataHoraAlocacao;

    protected Alocacao() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

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
