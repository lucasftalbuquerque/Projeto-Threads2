package com.rotavital.api.dto;

import com.rotavital.dominio.Hospital;

public record HospitalResponse(
        String id,
        String nome,
        String telefone,
        String cnes,
        EnderecoDto endereco) {

    public static HospitalResponse de(Hospital hospital) {
        return new HospitalResponse(
                hospital.getId(),
                hospital.getNome(),
                hospital.getTelefone(),
                hospital.getCnes(),
                MapeadorEndereco.paraDto(hospital.getEndereco()));
    }
}
