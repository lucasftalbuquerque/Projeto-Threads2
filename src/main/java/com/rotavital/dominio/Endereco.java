package com.rotavital.dominio;

public class Endereco {

    private final String logradouro;
    private final String numero;
    private final String bairro;
    private final String cidade;
    private final String estado;
    private final String cep;
    private final double latitude;
    private final double longitude;

    public Endereco(String logradouro, String numero, String bairro,
                    String cidade, String estado, String cep,
                    double latitude, double longitude) {
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cidade = cidade;
        this.estado = estado;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getLogradouro() { return logradouro; }
    public String getNumero()     { return numero; }
    public String getBairro()     { return bairro; }
    public String getCidade()     { return cidade; }
    public String getEstado()     { return estado; }
    public String getCep()        { return cep; }
    public double getLatitude()   { return latitude; }
    public double getLongitude()  { return longitude; }

    @Override
    public String toString() {
        return logradouro + ", " + numero + " - " + bairro
                + ", " + cidade + "/" + estado + " - CEP " + cep;
    }
}
