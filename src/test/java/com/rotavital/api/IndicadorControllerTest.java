package com.rotavital.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IndicadorControllerTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        }
        return mockMvc;
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/estoque-por-tipo retorna status 200")
    void deveConsultarEstoquePorTipo() throws Exception {
        mvc().perform(get("/api/v1/indicadores/estoque-por-tipo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/estoque-por-componente retorna status 200")
    void deveConsultarEstoquePorComponente() throws Exception {
        mvc().perform(get("/api/v1/indicadores/estoque-por-componente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/descarte retorna status 200")
    void deveConsultarTaxaDescarte() throws Exception {
        mvc().perform(get("/api/v1/indicadores/descarte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/validade retorna status 200 com metricas")
    void deveConsultarDispersaoValidade() throws Exception {
        mvc().perform(get("/api/v1/indicadores/validade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").isNumber())
                .andExpect(jsonPath("$.mediaDias").isNumber())
                .andExpect(jsonPath("$.medianaDias").isNumber())
                .andExpect(jsonPath("$.desvioPadraoDias").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/criticas retorna status 200")
    void deveConsultarBolsasCriticas() throws Exception {
        mvc().perform(get("/api/v1/indicadores/criticas").param("limiteDias", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limiteDias").value(7))
                .andExpect(jsonPath("$.quantidade").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/cobertura retorna status 200")
    void deveConsultarCoberturaDemanda() throws Exception {
        mvc().perform(get("/api/v1/indicadores/cobertura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
