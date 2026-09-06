package com.rotavital.api;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o caminho que atravessa o sistema inteiro: hospital pede, hemocentro
 * aloca, e o estado da bolsa e da requisicao acompanham.
 *
 * <p>E o teste que mais protege o projeto, porque e onde as regras de negocio
 * se cruzam. Um erro aqui e um erro de dominio, nao de formato.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FluxoRequisicaoTest {

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
                                "Hemocentro do Fluxo", "(81) 3000-1000", "10101010000101",
                                endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital do Fluxo", "(81) 3000-2000", "7777777",
                                endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Alocar bolsa compativel reserva a bolsa e move a requisicao para PARCIALMENTE_ATENDIDA")
    void alocacaoCompativel() throws Exception {
        String bolsaId = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_POS, 2);

        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bolsaId").value(bolsaId));

        mvc.perform(get("/api/v1/bolsas/" + bolsaId))
                .andExpect(jsonPath("$.status").value("RESERVADA"));

        mvc.perform(get("/api/v1/requisicoes/" + requisicaoId))
                .andExpect(jsonPath("$.status").value("PARCIALMENTE_ATENDIDA"))
                .andExpect(jsonPath("$.itens[0].quantidadeAlocada").value(1))
                .andExpect(jsonPath("$.itens[0].status").value("PARCIALMENTE_ALOCADO"));
    }

    @Test
    @DisplayName("Atender toda a quantidade marca a requisicao como ATENDIDA")
    void requisicaoTotalmenteAtendida() throws Exception {
        String primeira = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);
        String segunda = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);

        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.A_POS, 2);
        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        alocar(requisicaoId, itemId, primeira);
        alocar(requisicaoId, itemId, segunda);

        mvc.perform(get("/api/v1/requisicoes/" + requisicaoId))
                .andExpect(jsonPath("$.status").value("ATENDIDA"))
                .andExpect(jsonPath("$.itens[0].status").value("ALOCADO"));
    }

    @Test
    @DisplayName("Bolsa de grupo sanguineo diferente e recusada com 409")
    void recusaGrupoIncompativel() throws Exception {
        String bolsaId = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_NEG);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_NEG, 1);

        mvc.perform(post("/api/v1/requisicoes/" + requisicao.get("id").asString()
                                + "/itens/" + requisicao.get("itens").get(0).get("id").asString()
                                + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Hemocomponente diferente do pedido e recusado com 409")
    void recusaComponenteIncompativel() throws Exception {
        String bolsaId = criarBolsa(TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_POS, 1);

        mvc.perform(post("/api/v1/requisicoes/" + requisicao.get("id").asString()
                                + "/itens/" + requisicao.get("itens").get(0).get("id").asString()
                                + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A mesma bolsa nao pode ser alocada duas vezes")
    void naoAlocaBolsaJaReservada() throws Exception {
        String bolsaId = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.B_POS);

        JsonNode primeira = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.B_POS, 1);
        alocar(primeira.get("id").asString(), primeira.get("itens").get(0).get("id").asString(), bolsaId);

        JsonNode segunda = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.B_POS, 1);

        mvc.perform(post("/api/v1/requisicoes/" + segunda.get("id").asString()
                                + "/itens/" + segunda.get("itens").get(0).get("id").asString()
                                + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cancelar a requisicao devolve a bolsa ao estoque")
    void cancelamentoLiberaBolsa() throws Exception {
        String bolsaId = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.AB_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.AB_POS, 1);
        String requisicaoId = requisicao.get("id").asString();

        alocar(requisicaoId, requisicao.get("itens").get(0).get("id").asString(), bolsaId);

        mvc.perform(patch("/api/v1/requisicoes/" + requisicaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        mvc.perform(get("/api/v1/bolsas/" + bolsaId))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"));
    }

    @Test
    @DisplayName("Nao cancela requisicao cuja bolsa ja saiu do hemocentro")
    void naoCancelaComBolsaDespachada() throws Exception {
        String bolsaId = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_NEG, 1);
        String requisicaoId = requisicao.get("id").asString();

        alocar(requisicaoId, requisicao.get("itens").get(0).get("id").asString(), bolsaId);

        // Simula o embarque: a bolsa deixa a reserva e entra em transito.
        mvc.perform(patch("/api/v1/bolsas/" + bolsaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_TRANSITO\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/requisicoes/" + requisicaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Requisicao sem item algum e recusada com 400")
    void recusaRequisicaoSemItens() throws Exception {
        mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.ROTINA, null, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.itens").exists());
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private String criarBolsa(TipoHemocomponente tipo, GrupoSanguineo grupo) throws Exception {
        String resposta = mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, tipo, grupo, 450, LocalDate.now().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idDe(resposta);
    }

    private JsonNode criarRequisicao(TipoHemocomponente tipo, GrupoSanguineo grupo, int quantidade)
            throws Exception {
        String resposta = mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                                List.of(new ItemRequisicaoRequest(tipo, grupo, quantidade))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    private void alocar(String requisicaoId, String itemId, String bolsaId) throws Exception {
        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isCreated());
    }

    private String idDe(String respostaJson) throws Exception {
        return json.readTree(respostaJson).get("id").asString();
    }

    private EnderecoDto endereco() {
        return new EnderecoDto("Rua do Fluxo", "10", "Centro", "Recife", "PE",
                "50000-000", -8.05, -34.90);
    }
}
