package com.rotavital.dominio;

import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRequisicao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Requisicao {

    private final String id;
    private final Hospital hospital;
    private final PrioridadeRequisicao prioridade;
    private final LocalDateTime dataHoraCriacao;
    private LocalDateTime prazoEntrega;
    private StatusRequisicao status;
    private final List<ItemRequisicao> itens;

    public Requisicao(String id, Hospital hospital, PrioridadeRequisicao prioridade,
                      LocalDateTime dataHoraCriacao, LocalDateTime prazoEntrega) {
        this.id = id;
        this.hospital = hospital;
        this.prioridade = prioridade;
        this.dataHoraCriacao = dataHoraCriacao;
        this.prazoEntrega = prazoEntrega;
        this.status = StatusRequisicao.PENDENTE;
        this.itens = new ArrayList<>();
    }

    public String getId()                     { return id; }
    public Hospital getHospital()             { return hospital; }
    public PrioridadeRequisicao getPrioridade() { return prioridade; }
    public LocalDateTime getDataHoraCriacao() { return dataHoraCriacao; }
    public LocalDateTime getPrazoEntrega()    { return prazoEntrega; }
    public StatusRequisicao getStatus()       { return status; }

    public void setPrazoEntrega(LocalDateTime prazoEntrega) { this.prazoEntrega = prazoEntrega; }
    public void setStatus(StatusRequisicao status)          { this.status = status; }

    public List<ItemRequisicao> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public void adicionarItem(ItemRequisicao item) {
        itens.add(item);
    }

    public boolean estaCompleta() {
        return !itens.isEmpty() && itens.stream().allMatch(ItemRequisicao::estaAtendido);
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
