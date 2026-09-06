package com.rotavital.repositorio;

import com.rotavital.dominio.Requisicao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequisicaoRepository extends JpaRepository<Requisicao, String> {

    long countByHospitalId(String hospitalId);
}
