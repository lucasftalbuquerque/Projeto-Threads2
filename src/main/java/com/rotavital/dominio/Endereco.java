package com.rotavital.dominio;

import jakarta.persistence.Embeddable;

/**
 * Endereco de uma unidade da rede.
 *
 * <p>E um objeto de valor, nao uma entidade: nao tem identidade propria e so
 * existe como parte de um Local. Por isso {@code @Embeddable}, que faz as
 * colunas serem gravadas dentro da tabela da propria unidade em vez de criar
 * uma tabela separada com chave estrangeira.</p>
 */
@Embeddable
public class Endereco {

    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String estado;
    private String cep;
    private double latitude;
    private double longitude;

    protected Endereco() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

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
