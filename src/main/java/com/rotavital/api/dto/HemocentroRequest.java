package com.rotavital.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HemocentroRequest(
        @NotBlank(message = "nome e obrigatorio")
        String nome,

        String telefone,

        @NotBlank(message = "cnpj e obrigatorio")
        String cnpj,

        @NotNull(message = "endereco e obrigatorio")
        @Valid
        EnderecoDto endereco) {
}
