package com.rotavital.dominio.enums;

public enum TipoHemocomponente {

    CONCENTRADO_HEMACIAS("Concentrado de Hemacias", 1.0, 6.0, 42),
    PLASMA_FRESCO_CONGELADO("Plasma Fresco Congelado", -25.0, -18.0, 365),
    CONCENTRADO_PLAQUETAS("Concentrado de Plaquetas", 20.0, 24.0, 5),
    CRIOPRECIPITADO("Crioprecipitado", -25.0, -18.0, 365);

    private final String descricao;
    private final double tempMinCelsius;
    private final double tempMaxCelsius;
    private final int validadeDias;

    TipoHemocomponente(String descricao, double tempMinCelsius, double tempMaxCelsius, int validadeDias) {
        this.descricao = descricao;
        this.tempMinCelsius = tempMinCelsius;
        this.tempMaxCelsius = tempMaxCelsius;
        this.validadeDias = validadeDias;
    }

    public String getDescricao()        { return descricao; }
    public double getTempMinCelsius()   { return tempMinCelsius; }
    public double getTempMaxCelsius()   { return tempMaxCelsius; }
    public int getValidadeDias()        { return validadeDias; }

    public boolean temperaturaAdequada(double tempLeitura) {
        return tempLeitura >= tempMinCelsius && tempLeitura <= tempMaxCelsius;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
