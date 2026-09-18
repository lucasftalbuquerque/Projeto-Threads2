# Guia de Estudos — Portabilidade de Indicadores e Estatística (W06)

Este documento foi elaborado para consolidar o aprendizado sobre a arquitetura de software, estatística descritiva e práticas de engenharia aplicadas durante a implementação da história **W06-Indicadores (EST)** no projeto **Rota Vital**.

---

## 1. O Problema de Negócio e o Desafio

Imagine um grande centro de distribuição de produtos perecíveis. No entanto, em vez de iogurtes ou frutas, lidamos com **hemocomponentes** (bolsas de sangue). 
O desafio possui três complicadores simultâneos:
1. **Prazos de validade extremamente desiguais:** Uma bolsa de plaquetas dura apenas **5 dias** (como um pão fresco artesanal), enquanto o plasma congelado dura **365 dias** (como um enlatado no congelador).
2. **Compatibilidade biológica:** Nem todo receptor pode receber qualquer tipo sanguíneo.
3. **Vida humana em jogo:** Se faltar sangue no hospital, cirurgias param; se sobrar no hemocentro sem uso, o sangue é descartado no lixo biológico.

Antes da nossa entrega, todos os cálculos estatísticos desse cenário existiam apenas em um script Python isolado (`dados/analise.py`). Nosso objetivo foi **portar essa inteligência para o backend Java em produção**, calculando métricas em tempo real sobre os dados do banco e expondo-as via API REST.

---

## 2. Analogia Geral da Arquitetura em Camadas

Para entender o que foi feito no código, pense na estrutura do backend como uma **cozinha de restaurante profissional**:

```
[ Cliente / Frontend / Postman ]
               │ (Pedido via HTTP)
               ▼
   [ 1. Controller (Garçom) ] ──────────► IndicadorController
               │ (Encaminha o pedido)
               ▼
   [ 2. DTO (Cardápio / Prato) ] ───────► EstoquePorTipoResponse, ValidadeResponse, etc.
               │ (Formato padronizado)
               ▼
   [ 3. Service (Chef de Cozinha) ] ────► IndicadorServico
               │ (Executa a receita matemática)
               ▼
   [ 4. Repository (Despenseiro) ] ─────► BolsaRepository, RequisicaoRepository
               │ (Busca os ingredientes no estoque)
               ▼
   [ 5. Banco de Dados (Despensa) ] ────► Tabelas Bolsa, Requisicao, ItemRequisicao
```

---

## 3. O Passo a Passo Técnico com Exemplos e Analogias

### Subtarefa 1: Modelagem dos DTOs (Data Transfer Objects)
* **O que são?** São objetos que definem **exatamente o que sai da nossa API**.
* **Analogia da "Caixa de Entrega":** Se você compra uma pizza, não quer receber na sua casa o forno ou a espátula do pizzaiolo; você quer apenas a pizza arrumada na caixa certa. O DTO é essa caixa limpa e sem segredos internos da entidade do banco.
* **Exemplo de código (`record` do Java 21):**
  ```java
  public record EstoquePorTipoResponse(
          GrupoSanguineo grupoSanguineo,
          long quantidade,
          double percentual) {}
  ```
* **Por que `record`?** Em Java moderno, `record` cria classes imutáveis automaticamente com getters, `equals`, `hashCode` e `toString` em apenas 4 linhas, sem boilerplate.

---

### Subtarefa 2: Consultas Otimizadas no Repositório (O problema do N+1)
* **O que foi feito?** Adicionamos consultas JPQL com `JOIN FETCH`.
* **Analogia da "Viagem ao Supermercado":**
  - **Sem JOIN FETCH (Problema N+1):** Você vai ao mercado comprar 100 bolsas de sangue. Em vez de trazer tudo de uma vez com os dados do hospital, você faz 1 viagem para pegar as 100 bolsas e depois faz **mais 100 viagens** (uma para cada bolsa) só para descobrir o nome da unidade de origem. Isso destrói o desempenho do banco.
  - **Com `JOIN FETCH`:** Você vai ao mercado com um caminhão e traz as bolsas **junto com os dados do hemocentro e das requisições em uma única viagem**.
* **Exemplo:**
  ```java
  @Query("SELECT b FROM Bolsa b LEFT JOIN FETCH b.hemocentroOrigem")
  List<Bolsa> findAllComHemocentro();
  ```

---

### Subtarefa 3: A Camada de Serviço e a Matemática Estatística
Aqui reside o "cérebro" da aplicação (`IndicadorServico.java`). Portamos os cálculos de Python para Java puro sem depender de bibliotecas externas complexas.

#### A) Média vs. Mediana — Por que a média pode enganar?
* **Analogia dos Salários no Bar:** 
  Imagine um bar com 4 pessoas ganhando R$ 2.000 cada. A média e a mediana são R$ 2.000. De repente, entra o Elon Musk no bar. 
  - A **média** dispara para milhões (dando a falsa impressão de que todos ali são ricos).
  - A **mediana** (o valor do meio da fila) continua sendo R$ 2.000.
* **No Rota Vital:**
  - Temos **Plaquetas** que duram 5 dias e **Plasma** que dura 365 dias.
  - A **média global** deu **129,2 dias** (parece que temos estoque para 4 meses!).
  - Mas a **mediana** deu apenas **32,0 dias**! 
  - *Lição:* Metade das bolsas tem menos de um mês de validade. Avaliar o estoque apenas pela média causaria desabastecimento hospitalar grave.

#### B) Desvio-Padrão Amostral ($N - 1$)
* **O que mede?** O grau de dispersão ou "espalhamento" dos dados em torno da média.
* **Por que dividir por $N-1$ em vez de $N$?**
  Em estatística (correção de Bessel), quando você analisa uma amostra (e não toda a população existente no universo), a divisão por $N-1$ corrige a tendência da amostra de subestimar a variabilidade real. O `statistics.stdev` do Python usa essa fórmula, e nós a replicamos no Java:
  ```java
  private double calcularDesvioPadraoAmostral(List<Long> valores, double media) {
      if (valores.size() < 2) return 0.0;
      double somaQuadrados = 0;
      for (Long v : valores) {
          double diff = v - media;
          somaQuadrados += diff * diff;
      }
      return Math.sqrt(somaQuadrados / (valores.size() - 1));
  }
  ```

#### C) FEFO (First Expire, First Out) e Bolsas Críticas
* **Analogia da Geladeira de Casa:** O que vence primeiro precisa ser consumido primeiro.
* Calculamos dinamicamente as bolsas válidas que vencem em **até 7 dias** (`ChronoUnit.DAYS.between(hoje, dataValidade) <= 7`). Descobrimos que **187 bolsas (13,9% do estoque útil)** estão nessa faixa crítica e precisam ser despachadas imediatamente.

---

### Subtarefa 4: Exposição via API REST (`IndicadorController`)
* Conectamos o Garçom (Controller) ao Chef (Service), expondo rotas limpas:
  - `GET /api/v1/indicadores/estoque-por-tipo`
  - `GET /api/v1/indicadores/estoque-por-componente`
  - `GET /api/v1/indicadores/descarte`
  - `GET /api/v1/indicadores/validade`
  - `GET /api/v1/indicadores/criticas?limiteDias=7`
  - `GET /api/v1/indicadores/cobertura`
* Usamos anotações do Swagger/OpenAPI (`@Operation`, `@Parameter`, `@Tag`) para que os endpoints fiquem documentados de forma interativa.

---

### Subtarefa 5: Testes Automatizados (A Rede de Segurança)
* **Analogia do Trapezista de Circo:** Você não faz acrobacias em altura sem uma rede de proteção. Testes automatizados garantem que, se alguém alterar uma linha de código no futuro, o sistema avisa imediatamente se algo quebrou.
* **Dois níveis de testes criados:**
  1. **Testes Unitários (`IndicadorServicoTest`):** Testam a matemática pura de forma isolada, usando mocks (`Mockito`) para simular o banco de dados sem precisar subir o servidor.
  2. **Testes de Integração (`IndicadorControllerTest`):** Usam o `MockMvc` para simular uma requisição HTTP real entrando na porta da API e validam se o status retornado é 200 OK e se o JSON vem formatado corretamente.
* **Resultado:** **60 de 60 testes** passando com sucesso (`BUILD SUCCESS`).

---

### Subtarefa 6: Interpretação e Documentação no `/docs`
* Elaboramos o documento analítico formal em `docs/interpretacao_indicadores.md` com fórmulas em LaTeX e conclusões de negócio.
* Integramos a tag e o recurso no `OpenApiConfig.java`, garantindo que ao acessar `/docs` (interface interativa do Scalar) a seção de **Indicadores** apareça organizada na barra de navegação lateral.

---

## 4. Resumo das Fórmulas para Memorizar

| Indicador | Fórmula Matemática | O que nos ensina? |
|---|---|---|
| **Taxa de Descarte** | $\frac{\text{Vencidas}}{\text{Total Coletado}} \times 100$ | Revelou que plaquetas perdem **90,2%** do estoque se não houver despacho rápido. |
| **Média ($\bar{x}$)** | $\frac{1}{n} \sum x_i$ | Ponto de equilíbrio numérico (distorcido por extremos). |
| **Mediana ($\tilde{x}$)** | Elemento central da fila ordenada | O "divisor de águas": 50% das bolsas duram menos que ela e 50% duram mais. |
| **Desvio-Padrão ($s$)** | $\sqrt{\frac{\sum (x_i - \bar{x})^2}{n - 1}}$ | Quanto maior o valor, mais imprevisível e heterogêneo é o estoque. |
| **Cobertura (%)** | $\frac{\text{Disponível Válido}}{\text{Demandado}} \times 100$ | Aponta onde há déficit (A+ com déficit de 95 bolsas) ou folga (O- com 122%). |
| **Déficit** | $\max(0, \text{Demandado} - \text{Disponível})$ | A quantidade exata de bolsas que faltarão para os hospitais. |

---

## 5. Boas Práticas de Git e Engenharia Aplicadas

1. **Branching por subtarefa:** Cada funcionalidade nasceu em uma branch isolada (`feature/W06.x-...`), garantindo que o código na `main` continue sempre estável e passível de deploy.
2. **Commits Semânticos:** Uso de prefixos claros (`feat:`, `test:`, `docs:`) facilitando a auditoria e o histórico do repositório.
3. **Imutabilidade e DTOs:** Uso de `record` do Java 21 para garantir segurança de tipos e clareza de contrato.
4. **Sem N+1 queries:** Consultas pensadas para alta performance em ambientes relacionais.
