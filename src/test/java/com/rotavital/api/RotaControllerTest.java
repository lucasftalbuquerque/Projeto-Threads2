package com.rotavital.api;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o ciclo de vida da rota e o efeito dela sobre o estoque: embarcar,
 * sair, concluir. E o trecho logistico do dominio.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RotaControllerTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;

    private String hemocentroId;
    private String hospitalId;

    @BeforeEach
    void prepararCenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        hemocentroId = idDe(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                "Hemocentro da Rota", "(81) 3000-3000", "20202020000202",
                                endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital da Rota", "(81) 3000-4000", "8888888",
                                endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("POST cria rota PLANEJADA")
    void criaRota() throws Exception {
        mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(rotaValida())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANEJADA"))
                .andExpect(jsonPath("$.hemocentroOrigemId").value(hemocentroId))
                .andExpect(jsonPath("$.bolsas").isEmpty());
    }

    @Test
    @DisplayName("Chegada anterior a saida e recusada com 409")
    void recusaJanelaInvertida() throws Exception {
        RotaRequest invalida = new RotaRequest(
                hemocentroId, hospitalId,
                LocalDateTime.now().plusHours(5),
                LocalDateTime.now().plusHours(1));

        mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(invalida)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rota vazia nao pode sair")
    void rotaVaziaNaoSai() throws Exception {
        String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(rotaValida())))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(patch("/api/v1/rotas/" + rotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_TRANSITO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Bolsa DISPONIVEL nao embarca: precisa estar reservada")
    void naoEmbarcaBolsaDisponivel() throws Exception {
        String bolsaId = criarBolsa();
        String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(rotaValida())))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmbarqueBolsaRequest(bolsaId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Ciclo completo: embarca, sai em transito e conclui entregando a bolsa")
    void cicloCompletoDaRota() throws Exception {
        String bolsaId = criarBolsaReservada();

        String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(rotaValida())))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmbarqueBolsaRequest(bolsaId))))
                .andExpect(status().isCreated());

        mvc.perform(patch("/api/v1/rotas/" + rotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_TRANSITO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saidaReal").exists());

        mvc.perform(get("/api/v1/bolsas/" + bolsaId))
                .andExpect(jsonPath("$.status").value("EM_TRANSITO"));

        mvc.perform(patch("/api/v1/rotas/" + rotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONCLUIDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chegadaReal").exists());

        mvc.perform(get("/api/v1/bolsas/" + bolsaId))
                .andExpect(jsonPath("$.status").value("ENTREGUE"));
    }

    @Test
    @DisplayName("Rota concluida nao volta para PLANEJADA")
    void naoRetrocedeStatus() throws Exception {
        String bolsaId = criarBolsaReservada();
        String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(rotaValida())))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmbarqueBolsaRequest(bolsaId))))
                .andExpect(status().isCreated());

        mvc.perform(patch("/api/v1/rotas/" + rotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_TRANSITO\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/rotas/" + rotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PLANEJADA\"}"))
                .andExpect(status().isConflict());
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private RotaRequest rotaValida() {
        return new RotaRequest(
                hemocentroId, hospitalId,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3));
    }

    private String criarBolsa() throws Exception {
        return idDe(mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                GrupoSanguineo.O_POS, 450, LocalDate.now().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** Bolsa passa a RESERVADA pelo caminho real: alocada a uma requisicao. */
    private String criarBolsaReservada() throws Exception {
        String bolsaId = criarBolsa();

        String resposta = mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                                List.of(new ItemRequisicaoRequest(
                                        TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                        GrupoSanguineo.O_POS, 1))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode requisicao = json.readTree(resposta);

        mvc.perform(post("/api/v1/requisicoes/" + requisicao.get("id").asString()
                                + "/itens/" + requisicao.get("itens").get(0).get("id").asString()
                                + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isCreated());

        return bolsaId;
    }

    private String idDe(String respostaJson) {
        return json.readTree(respostaJson).get("id").asString();
    }

    private EnderecoDto endereco() {
        return new EnderecoDto("Rua da Rota", "20", "Centro", "Recife", "PE",
                "50000-000", -8.05, -34.90);
    }
}
