package com.rotavital.dominio;

public class Veiculo {

    private final String id;
    private String placa;
    private String modelo;
    private int capacidadeMaxima;
    private boolean disponivel;

    public Veiculo(String id, String placa, String modelo, int capacidadeMaxima) {
        this.id = id;
        this.placa = placa;
        this.modelo = modelo;
        this.capacidadeMaxima = capacidadeMaxima;
        this.disponivel = true;
    }

    public String getId()            { return id; }
    public String getPlaca()         { return placa; }
    public String getModelo()        { return modelo; }
    public int getCapacidadeMaxima() { return capacidadeMaxima; }
    public boolean isDisponivel()    { return disponivel; }

    public void setPlaca(String placa)              { this.placa = placa; }
    public void setModelo(String modelo)            { this.modelo = modelo; }
    public void setCapacidadeMaxima(int capacidade) { this.capacidadeMaxima = capacidade; }
    public void setDisponivel(boolean disponivel)   { this.disponivel = disponivel; }

    @Override
    public String toString() {
        return "Veiculo[" + id + "] " + placa + " " + modelo
                + " cap:" + capacidadeMaxima + " disponivel:" + disponivel;
    }
}
