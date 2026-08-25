package com.rotavital.dominio;

import com.rotavital.dominio.enums.StatusRequisicao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Requisicao {

    private final String id;
    private final Hospital hospital;
    private final LocalDateTime dataHoraCriacao;
    private LocalDateTime prazoEntrega;
    private StatusRequisicao status;
    private final List<ItemRequisicao> itens;

    public Requisicao(String id, Hospital hospital,
                      LocalDateTime dataHoraCriacao, LocalDateTime prazoEntrega) {
        this.id = id;
        this.hospital = hospital;
        this.dataHoraCriacao = dataHoraCriacao;
        this.prazoEntrega = prazoEntrega;
        this.status = StatusRequisicao.PENDENTE;
        this.itens = new ArrayList<>();
    }

    public String getId()                     { return id; }
    public Hospital getHospital()             { return hospital; }
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
}
