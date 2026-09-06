package com.rotavital.repositorio;

import com.rotavital.dominio.Bolsa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BolsaRepository extends JpaRepository<Bolsa, String> {

    List<Bolsa> findByHemocentroOrigemId(String hemocentroId);
}
