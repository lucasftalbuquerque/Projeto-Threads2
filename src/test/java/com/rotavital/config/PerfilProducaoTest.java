package com.rotavital.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava as decisoes de seguranca do perfil de producao.
 *
 * <p>Le o arquivo direto do classpath em vez de subir a aplicacao com
 * {@code @ActiveProfiles("prod")}. O motivo: subir de verdade em modo prod
 * dispararia a carga de dados e o {@code ddl-auto=create}, deixando o teste
 * lento e acoplado ao banco. O que precisa ser verificado aqui e o conteudo do
 * arquivo, e ele basta.</p>
 *
 * <p>Se alguem religar o console do H2 para depurar e esquecer de desligar,
 * este teste quebra o build antes do deploy. E a diferenca entre uma regra
 * escrita num documento e uma regra que o pipeline cobra.</p>
 */
class PerfilProducaoTest {

    private Properties producao() throws IOException {
        Properties propriedades = new Properties();
        try (InputStream entrada = new ClassPathResource("application-prod.properties").getInputStream()) {
            propriedades.load(entrada);
        }
        return propriedades;
    }

    @Test
    @DisplayName("Console do H2 esta desligado em producao")
    void consoleDoH2Desligado() throws IOException {
        assertThat(producao().getProperty("spring.h2.console.enabled"))
                .as("o console do H2 e um terminal SQL sem senha; nao pode ficar exposto")
                .isEqualTo("false");
    }

    @Test
    @DisplayName("Erro em producao nao devolve stack trace nem mensagem interna")
    void erroNaoVazaDetalhe() throws IOException {
        Properties prod = producao();

        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-message")).isEqualTo("never");
    }

    @Test
    @DisplayName("Actuator expoe apenas o health")
    void actuatorRestrito() throws IOException {
        Properties prod = producao();

        assertThat(prod.getProperty("management.endpoints.web.exposure.include"))
                .as("/actuator/env lista variaveis de ambiente; /actuator/beans mapeia a aplicacao")
                .isEqualTo("health");

        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("SQL nao vai para o log em producao")
    void semSqlNoLog() throws IOException {
        assertThat(producao().getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }

    @Test
    @DisplayName("Nenhuma senha esta escrita no arquivo de producao")
    void semSegredoVersionado() throws IOException {
        Properties prod = producao();

        for (String chave : prod.stringPropertyNames()) {
            boolean pareceSegredo = chave.contains("password")
                    || chave.contains("secret")
                    || chave.contains("token");

            if (pareceSegredo) {
                assertThat(prod.getProperty(chave))
                        .as("segredo deve vir de variavel de ambiente, nunca do arquivo: " + chave)
                        .matches("^\\$\\{.*\\}$|^$");
            }
        }
    }
}
