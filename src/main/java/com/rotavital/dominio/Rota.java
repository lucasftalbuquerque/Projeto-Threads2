package com.rotavital.dominio;

import com.rotavital.dominio.enums.StatusRota;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Rota {

    private final String id;
    private final Hemocentro origem;
    private final Hospital destino;
    private final LocalDateTime dataHoraSaida;
    private LocalDateTime dataHoraChegadaPrevista;
    private StatusRota status;
    private final List<Bolsa> bolsas;

    public Rota(String id, Hemocentro origem, Hospital destino,
                LocalDateTime dataHoraSaida, LocalDateTime dataHoraChegadaPrevista) {
        this.id = id;
        this.origem = origem;
        this.destino = destino;
        this.dataHoraSaida = dataHoraSaida;
        this.dataHoraChegadaPrevista = dataHoraChegadaPrevista;
        this.status = StatusRota.PLANEJADA;
        this.bolsas = new ArrayList<>();
    }

    public String getId()                                { return id; }
    public Hemocentro getOrigem()                        { return origem; }
    public Hospital getDestino()                         { return destino; }
    public LocalDateTime getDataHoraSaida()              { return dataHoraSaida; }
    public LocalDateTime getDataHoraChegadaPrevista()    { return dataHoraChegadaPrevista; }
    public StatusRota getStatus()                        { return status; }

    public void setDataHoraChegadaPrevista(LocalDateTime dataHoraChegadaPrevista) {
        this.dataHoraChegadaPrevista = dataHoraChegadaPrevista;
    }

    public void setStatus(StatusRota status) {
        this.status = status;
    }

    public List<Bolsa> getBolsas() {
        return Collections.unmodifiableList(bolsas);
    }

    public void adicionarBolsa(Bolsa bolsa) {
        bolsas.add(bolsa);
    }

    public void removerBolsa(Bolsa bolsa) {
        bolsas.remove(bolsa);
    }
}
