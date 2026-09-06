package com.rotavital.api;

import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.api.dto.RotaResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Rota;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.servico.RotaServico;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de rota, conforme a secao 2.5 do contrato_api.md.
 */
@RestController
@RequestMapping("/api/v1/rotas")
public class RotaController {

    private final RotaServico rotas;

    public RotaController(RotaServico rotas) {
        this.rotas = rotas;
    }

    @GetMapping
    public List<RotaResponse> listar(
            @RequestParam(required = false) StatusRota status,
            @RequestParam(required = false) String hemocentroId,
            @RequestParam(required = false) String hospitalId) {
        return rotas.listar(status, hemocentroId, hospitalId).stream()
                .map(RotaResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    public RotaResponse buscar(@PathVariable String id) {
        return RotaResponse.de(rotas.buscar(id));
    }

    @PostMapping
    public ResponseEntity<RotaResponse> criar(@Valid @RequestBody RotaRequest dados) {
        Rota criada = rotas.criar(dados);
        return ResponseEntity
                .created(URI.create("/api/v1/rotas/" + criada.getId()))
                .body(RotaResponse.de(criada));
    }

    @PatchMapping("/{id}")
    public RotaResponse atualizarStatus(@PathVariable String id,
                                        @Valid @RequestBody AtualizarStatusRotaRequest dados) {
        return RotaResponse.de(rotas.atualizarStatus(id, dados));
    }

    @GetMapping("/{id}/bolsas")
    public List<BolsaResponse> listarBolsas(@PathVariable String id) {
        return rotas.listarBolsas(id).stream()
                .map(BolsaResponse::de)
                .toList();
    }

    @PostMapping("/{id}/bolsas")
    public ResponseEntity<BolsaResponse> embarcar(@PathVariable String id,
                                                  @Valid @RequestBody EmbarqueBolsaRequest dados) {
        Bolsa bolsa = rotas.embarcar(id, dados);
        return ResponseEntity
                .created(URI.create("/api/v1/rotas/" + id + "/bolsas/" + bolsa.getCodigoRastreio()))
                .body(BolsaResponse.de(bolsa));
    }

    @DeleteMapping("/{id}/bolsas/{bolsaId}")
    public ResponseEntity<Void> desembarcar(@PathVariable String id,
                                            @PathVariable String bolsaId) {
        rotas.desembarcar(id, bolsaId);
        return ResponseEntity.noContent().build();
    }
}
