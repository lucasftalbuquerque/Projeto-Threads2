package com.rotavital.api.dto;

import com.rotavital.dominio.Endereco;

/**
 * Converte entre o objeto de valor do dominio e o DTO da API.
 *
 * <p>Existe para que a conversao fique num lugar so: as duas direcoes sao
 * usadas por hemocentro e hospital.</p>
 */
public final class MapeadorEndereco {

    private MapeadorEndereco() {
        // Classe utilitaria: nao deve ser instanciada.
    }

    public static EnderecoDto paraDto(Endereco endereco) {
        if (endereco == null) {
            return null;
        }
        return new EnderecoDto(
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getEstado(),
                endereco.getCep(),
                endereco.getLatitude(),
                endereco.getLongitude());
    }

    public static Endereco paraDominio(EnderecoDto dto) {
        if (dto == null) {
            return null;
        }
        return new Endereco(
                dto.logradouro(),
                dto.numero(),
                dto.bairro(),
                dto.cidade(),
                dto.uf(),
                dto.cep(),
                dto.latitude(),
                dto.longitude());
    }
}
