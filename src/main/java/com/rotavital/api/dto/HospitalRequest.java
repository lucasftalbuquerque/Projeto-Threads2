package com.rotavital.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HospitalRequest(
        @NotBlank(message = "nome e obrigatorio")
        String nome,

        String telefone,

        @NotBlank(message = "cnes e obrigatorio")
        String cnes,

        @NotNull(message = "endereco e obrigatorio")
        @Valid
        EnderecoDto endereco) {
}
