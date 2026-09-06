package com.rotavital.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Endereco no corpo das requisicoes e respostas.
 *
 * <p>Record: classe imutavel com construtor, getters, equals, hashCode e
 * toString gerados pelo compilador. Para DTO e o formato ideal, porque um
 * DTO nao tem comportamento, so carrega dados.</p>
 */
public record EnderecoDto(
        @NotBlank(message = "logradouro e obrigatorio")
        String logradouro,

        @NotBlank(message = "numero e obrigatorio")
        String numero,

        String bairro,

        @NotBlank(message = "cidade e obrigatoria")
        String cidade,

        @NotBlank(message = "uf e obrigatoria")
        @Size(min = 2, max = 2, message = "uf deve ter 2 letras")
        String uf,

        String cep,
        double latitude,
        double longitude) {
}
