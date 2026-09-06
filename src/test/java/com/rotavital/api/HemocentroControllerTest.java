package com.rotavital.api;

import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
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

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o CRUD de hemocentro de ponta a ponta: entra JSON pela porta HTTP,
 * passa pelo servico e chega no banco.
 *
 * <p>{@code @Transactional} desfaz o que cada teste escreveu, entao a ordem
 * de execucao nao importa.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HemocentroControllerTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        }
        return mockMvc;
    }

    private HemocentroRequest requisicaoValida(String cnpj) {
        return new HemocentroRequest(
                "Hemocentro de Teste",
                "(81) 3333-4444",
                cnpj,
                new EnderecoDto("Rua das Flores", "100", "Centro", "Recife", "PE",
                        "50000-000", -8.05, -34.90));
    }

    @Test
    @DisplayName("POST cria hemocentro e devolve 201 com Location")
    void criaHemocentro() throws Exception {
        mvc().perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requisicaoValida("99888777000166"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Hemocentro de Teste"))
                .andExpect(jsonPath("$.endereco.cidade").value("Recife"));
    }

    @Test
    @DisplayName("POST sem nome devolve 400 apontando o campo")
    void rejeitaNomeVazio() throws Exception {
        HemocentroRequest invalido = new HemocentroRequest(
                "", "(81) 3333-4444", "99888777000167",
                new EnderecoDto("Rua das Flores", "100", "Centro", "Recife", "PE",
                        "50000-000", -8.05, -34.90));

        mvc().perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists());
    }

    @Test
    @DisplayName("POST com CNPJ repetido devolve 409")
    void rejeitaCnpjDuplicado() throws Exception {
        String corpo = json.writeValueAsString(requisicaoValida("99888777000168"));

        mvc().perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        mvc().perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET de id inexistente devolve 404")
    void buscaInexistente() throws Exception {
        mvc().perform(get("/api/v1/hemocentros/nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT atualiza os dados do hemocentro")
    void atualizaHemocentro() throws Exception {
        String resposta = mvc().perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requisicaoValida("99888777000169"))))
                .andReturn().getResponse().getContentAsString();

        String id = json.readTree(resposta).get("id").asString();

        HemocentroRequest alterado = new HemocentroRequest(
                "Hemocentro Renomeado", "(81) 9999-0000", "99888777000169",
                new EnderecoDto("Rua Nova", "200", "Boa Viagem", "Recife", "PE",
                        "51020-000", -8.12, -34.90));

        mvc().perform(put("/api/v1/hemocentros/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(alterado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Hemocentro Renomeado"))
                .andExpect(jsonPath("$.endereco.bairro").value("Boa Viagem"));
    }

    @Test
    @DisplayName("DELETE de hemocentro sem vinculos devolve 204")
    void removeHemocentro() throws Exception {
        String resposta = mvc().perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requisicaoValida("99888777000170"))))
                .andReturn().getResponse().getContentAsString();

        String id = json.readTree(resposta).get("id").asString();

        mvc().perform(delete("/api/v1/hemocentros/" + id))
                .andExpect(status().isNoContent());

        mvc().perform(get("/api/v1/hemocentros/" + id))
                .andExpect(status().isNotFound());
    }
}
