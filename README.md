# Rota Vital

Plataforma web para distribuição de hemocomponentes: gestão de estoque, alocação de bolsas
compatíveis e roteirização respeitando cadeia fria e janelas de tempo.

## Descrição do projeto

A rede de sangue precisa garantir o componente certo (compatível e dentro da validade), no
lugar certo, no tempo certo e na temperatura certa. Falhas geram desabastecimento, descarte
por vencimento e risco ao paciente. Hoje falta uma plataforma que integre estoque,
compatibilidade e roteirização em um só lugar.

O Rota Vital é uma aplicação web que:

- gerencia o estoque de hemocomponentes;
- recebe requisições hospitalares;
- aloca bolsas compatíveis priorizando validade;
- calcula rotas de distribuição respeitando cadeia fria e janelas de tempo;
- apresenta painéis de indicadores de estoque/demanda e de monitoramento de temperatura.

### Competências trabalhadas

POO e Spring Boot; grafos e caminhos mínimos; matching de compatibilidade; filas de
prioridade e hash; estatística descritiva e probabilidade aplicadas; concorrência; CI/CD e
nuvem; arquitetura de redes e telemetria; trabalho em equipe.

### Limites e cuidados

- Apenas dados sintéticos, sem informações reais de doadores ou pacientes (LGPD).
- A telemetria de temperatura/GPS é simulada.
- A compatibilidade ABO/Rh é didática e não substitui protocolo clínico.

## Tecnologias usadas

| Camada | Tecnologia |
|---|---|
| Linguagem | Java |
| Framework web | Spring Boot |
| Build | (a definir: Maven ou Gradle) |
| Banco de dados | (a definir) |
| Front-end | (a definir) |
| Controle de versão | Git / GitHub |

## Entregas

### Entrega 01

- **Status:** em andamento
- **Artefatos:** _(adicionar links)_
- **Screenshots:** _(adicionar imagens)_

### Entrega 02

- **Status:** não iniciada
- **Artefatos:** _(adicionar links)_
- **Screenshots:** _(adicionar imagens)_

### Entrega 03

- **Status:** não iniciada
- **Artefatos:** _(adicionar links)_
- **Screenshots:** _(adicionar imagens)_

## Como rodar o projeto

> Obrigatório a partir da segunda entrega. Preencher assim que o esqueleto Spring Boot existir.

```bash
# clonar o repositório
git clone https://github.com/rsc3-pixel/Projeto3-2026.2.git
cd Projeto3-2026.2

# rodar a aplicação
# ./mvnw spring-boot:run
```

A aplicação ficará disponível em `http://localhost:8080`.

## Equipe

| Nome completo | E-mail da school |
|---|---|
| Renato Santos Chong | rsc3@cesar.school |
| Cauã Rego Tavares Leite Duarte | crtld@cesar.school |
| Fernando Andrade Leandro Peixoto | falp2@cesar.school |
| Gabriel Brito Ferreira Dias | gbfd@cesar.school |
| Guilherme Alves de Souza | gas6@cesar.school |
| Lucas Ferreira Torres de Albuquerque | lfta@cesar.school |
| Maria Eduarda Vasconcelos | mevs@cesar.school |
| Victor Barros Roma | vbr@cesar.school |


## Membros anteriores / novos

| Nome completo | E-mail da school | Entrada | Saída |
|---|---|---|---|
| _(nenhum até o momento)_ | | | |
