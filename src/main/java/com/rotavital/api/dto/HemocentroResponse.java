package com.rotavital.api.dto;

import com.rotavital.dominio.Hemocentro;

public record HemocentroResponse(
        String id,
        String nome,
        String telefone,
        String cnpj,
        EnderecoDto endereco) {

    public static HemocentroResponse de(Hemocentro hemocentro) {
        return new HemocentroResponse(
                hemocentro.getId(),
                hemocentro.getNome(),
                hemocentro.getTelefone(),
                hemocentro.getCnpj(),
                MapeadorEndereco.paraDto(hemocentro.getEndereco()));
    }
}
