package com.rotavital.dominio;

import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRequisicao;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class Requisicao {

    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Enumerated(EnumType.STRING)
    private PrioridadeRequisicao prioridade;

    private LocalDateTime dataHoraCriacao;
    private LocalDateTime prazoEntrega;
    private String observacao;

    @Enumerated(EnumType.STRING)
    private StatusRequisicao status;

    @OneToMany(mappedBy = "requisicao", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ItemRequisicao> itens = new ArrayList<>();

    protected Requisicao() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Requisicao(String id, Hospital hospital, PrioridadeRequisicao prioridade,
                      LocalDateTime dataHoraCriacao, LocalDateTime prazoEntrega) {
        this.id = id;
        this.hospital = hospital;
        this.prioridade = prioridade;
        this.dataHoraCriacao = dataHoraCriacao;
        this.prazoEntrega = prazoEntrega;
        this.status = StatusRequisicao.PENDENTE;
    }

    public String getId()                       { return id; }
    public Hospital getHospital()               { return hospital; }
    public PrioridadeRequisicao getPrioridade() { return prioridade; }
    public LocalDateTime getDataHoraCriacao()   { return dataHoraCriacao; }
    public LocalDateTime getPrazoEntrega()      { return prazoEntrega; }
    public String getObservacao()               { return observacao; }
    public StatusRequisicao getStatus()         { return status; }

    public void setPrazoEntrega(LocalDateTime prazoEntrega) { this.prazoEntrega = prazoEntrega; }
    public void setObservacao(String observacao)            { this.observacao = observacao; }
    public void setStatus(StatusRequisicao status)          { this.status = status; }

    public List<ItemRequisicao> getItens() {
        return Collections.unmodifiableList(itens);
    }

    /**
     * Adiciona o item e amarra os dois lados da relacao. Sem o
     * {@code item.setRequisicao(this)} o lado dono ficaria nulo e o JPA
     * gravaria a linha sem a chave estrangeira.
     */
    public void adicionarItem(ItemRequisicao item) {
        itens.add(item);
        item.setRequisicao(this);
    }

    public boolean removerItem(ItemRequisicao item) {
        return itens.remove(item);
    }

    public boolean estaCompleta() {
        return !itens.isEmpty() && itens.stream().allMatch(ItemRequisicao::estaAtendido);
    }

    /**
     * Recalcula o status a partir das alocacoes dos itens. Chamado sempre que
     * uma alocacao e criada ou desfeita. Requisicao cancelada nao muda.
     */
    public void recalcularStatus() {
        if (status == StatusRequisicao.CANCELADA) {
            return;
        }
        if (estaCompleta()) {
            status = StatusRequisicao.ATENDIDA;
        } else if (itens.stream().anyMatch(i -> i.getQuantidadeAlocada() > 0)) {
            status = StatusRequisicao.PARCIALMENTE_ATENDIDA;
        } else {
            status = StatusRequisicao.PENDENTE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Requisicao)) return false;
        Requisicao requisicao = (Requisicao) o;
        return Objects.equals(id, requisicao.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
