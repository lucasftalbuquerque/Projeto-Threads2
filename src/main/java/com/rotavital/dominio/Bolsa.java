package com.rotavital.dominio;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.time.LocalDate;
import java.util.Objects;

public class Bolsa {

    private final String codigoRastreio;
    private final TipoHemocomponente tipo;
    private final GrupoSanguineo grupoSanguineo;
    private final LocalDate dataColeta;
    private final LocalDate dataValidade;
    private final Hemocentro hemocentroOrigem;
    private StatusBolsa status;

    public Bolsa(String codigoRastreio, TipoHemocomponente tipo,
                 GrupoSanguineo grupoSanguineo, LocalDate dataColeta,
                 Hemocentro hemocentroOrigem) {
        this.codigoRastreio = codigoRastreio;
        this.tipo = tipo;
        this.grupoSanguineo = grupoSanguineo;
        this.dataColeta = dataColeta;
        this.dataValidade = dataColeta.plusDays(tipo.getValidadeDias());
        this.hemocentroOrigem = hemocentroOrigem;
        this.status = StatusBolsa.DISPONIVEL;
    }

    public String getCodigoRastreio()         { return codigoRastreio; }
    public TipoHemocomponente getTipo()       { return tipo; }
    public GrupoSanguineo getGrupoSanguineo() { return grupoSanguineo; }
    public LocalDate getDataColeta()          { return dataColeta; }
    public LocalDate getDataValidade()        { return dataValidade; }
    public Hemocentro getHemocentroOrigem()   { return hemocentroOrigem; }
    public StatusBolsa getStatus()            { return status; }

    public void setStatus(StatusBolsa status) { this.status = status; }

    public boolean estaVencida(LocalDate dataReferencia) {
        return dataReferencia.isAfter(dataValidade);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bolsa)) return false;
        Bolsa bolsa = (Bolsa) o;
        return Objects.equals(codigoRastreio, bolsa.codigoRastreio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigoRastreio);
    }

    @Override
    public String toString() {
        return "Bolsa[" + codigoRastreio + "] "
                + tipo.getDescricao() + " " + grupoSanguineo.getDescricao()
                + " val:" + dataValidade + " status:" + status;
    }
}
