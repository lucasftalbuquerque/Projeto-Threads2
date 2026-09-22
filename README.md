# Rota Vital — Paralelismo na camada de aplicação

Sistema: Rota Vital (Hemobank) — distribuição de hemocomponentes · Atividade: escolha,
implementação e medição de uma operação paralelizável em escala nacional.

Este repositório foi reduzido ao recorte dessa atividade: mantém só o código e a
documentação necessários para rodar e entender o benchmark de paralelismo (o restante do
produto Rota Vital — estoque, requisições, rotas, rede de distribuição — não faz parte
deste recorte).

## Equipe

| Nome completo | E-mail da school |
|---|---|
| Cauã Rego Tavares Leite Duarte | crtld@cesar.school |
| Fernando Andrade Leandro Peixoto | falp2@cesar.school |
| Gabriel Brito Ferreira Dias | gbfd@cesar.school |
| Guilherme Alves de Souza | gas6@cesar.school |
| Lucas Ferreira Torres de Albuquerque | lfta@cesar.school |
| Maria Eduarda Vasconcelos | mevs@cesar.school |
| Renato Santos Chong | rsc3@cesar.school |
| Victor Barros Roma | vbr@cesar.school |

## 1. Justificativa

**Operação escolhida: dispersão de validade.** Calcula, sobre o conjunto de bolsas válidas
(não vencidas): quantidade (n), média, mediana, desvio-padrão amostral, mínimo e máximo dos
dias até o vencimento. É o indicador que sustenta a história HU-06 ("acompanhar indicadores
da rede") — não é um cálculo artificial criado só para o exercício.

**Onde está o gargalo, em escala nacional.** A operação lê o estoque inteiro em uma única
consulta e processa tudo em memória, em uma thread: filtra vencidas O(n), soma para a média
O(n), ordena para achar a mediana O(n log n) e soma os quadrados dos desvios O(n). Com
milhões de bolsas em estoque, o processamento em memória cresce com n e é dominado pela
ordenação — melhorar a consulta SQL não muda a ordem de grandeza do problema, porque o custo
está no algoritmo que roda depois que os dados já chegaram.

**Por que os dados são particionáveis.** O cálculo de cada bolsa é independente do cálculo
de qualquer outra: decidir se a bolsa X está vencida, ou qual é o dia até o vencimento de X,
não depende do resultado de nenhuma outra bolsa. Isso permite cortar a lista em fatias
contíguas e processar cada fatia em uma thread separada, sem estado compartilhado durante o
processamento — só a combinação final (soma, contagem, mínimo, máximo e a fusão das fatias
já ordenadas para achar a mediana) precisa reunir os resultados parciais.

## 2. O serviço

Pacote `com.rotavital.paralelo` — mantém o algoritmo puro (sem dependência de Spring/JPA),
testável isoladamente.

| Classe | Responsabilidade |
|---|---|
| `CalculadoraDispersaoValidade` | Algoritmo em si: `calcularSequencial` (baseline, 1 thread, sem `ExecutorService`) e `calcularParalelo` (particiona em N fatias, `ExecutorService` com pool fixo). |
| `GeradorMassaBolsasSintetica` | Gera, em memória, a massa sintética de bolsas para o benchmark (100 mil / 1 milhão), sem tocar o banco — mede o algoritmo, não o Hibernate. |
| `ResumoValidade` | Record de saída: n, média, mediana, desvio-padrão amostral, mínimo, máximo. |

**Endpoint:**

```
GET /api/v1/benchmark/dispersao-validade?tamanhoAmostra=1000000&threads=4&semente=42
```

Implementado em `BenchmarkController` → `BenchmarkParaleloServico` →
`CalculadoraDispersaoValidade`. `threads=1` roda a versão sequencial; `threads=2|4|8` roda a
versão paralela. A resposta traz `tempoMs` (tempo do cálculo) e `resultado` (o
`ResumoValidade`).

## 3. As duas versões e a garantia de que não há race condition

O ponto mais delicado do exercício: "as duas devem devolver exatamente a mesma resposta —
se divergirem, há uma race condition." Soma de `double` não é associativa — somar os mesmos
números em ordens diferentes pode mudar o resultado no último bit, o que faria a versão
paralela "quase" bater com a sequencial, mas não exatamente, mascarando o problema real.

Para eliminar essa fonte de divergência, a combinação das fatias usa apenas `long` (soma dos
dias e soma dos quadrados dos dias). Adição de inteiros é associativa e comutativa em
aritmética exata, então o total não depende de quantas fatias existem nem da ordem em que as
threads terminam. Média e desvio-padrão saem de uma única fórmula fechada aplicada a esses
`long` — sequencial (1 fatia) e paralelo (N fatias) produzem o mesmo `double`, bit a bit. A
mediana usa uma intercalação (k-way merge com fila de prioridade) das fatias já ordenadas,
em vez de reordenar tudo de novo.

**Testes que verificam isso:**
- `CalculadoraDispersaoValidadeTest.sequencialEParaleloDevolvemExatamenteAMesmaResposta` —
  roda sequencial e paralelo (2, 4 e 8 threads) sobre massas de 0 a 50.000 bolsas e várias
  sementes, comparando o resultado completo com `assertEquals`.
- `MedicaoParalelismoTest` — repete a mesma verificação sobre as massas de 100 mil e 1 milhão
  usadas na medição oficial.

## 4. Medições

Tempo médio de 15 execuções, após aquecimento:

| Amostra | Modo | Threads | Tempo médio (ms) | Speedup |
|---|---|---|---|---|
| 100.000 | Sequencial | 1 | 11,78 | 1,00x |
| 100.000 | Paralelo | 2 | 12,82 | 0,92x |
| 100.000 | Paralelo | 4 | 10,85 | 1,09x |
| 100.000 | Paralelo | 8 | 13,67 | 0,86x |
| 1.000.000 | Sequencial | 1 | 121,41 | 1,00x |
| 1.000.000 | Paralelo | 2 | 68,55 | 1,77x |
| 1.000.000 | Paralelo | 4 | 67,21 | 1,81x |
| 1.000.000 | Paralelo | 8 | 67,21 | 1,81x |

Gráficos: [docs/grafico-tempos.png](docs/grafico-tempos.png) ·
[docs/grafico-speedup.png](docs/grafico-speedup.png)

**Leitura rápida:** com 100 mil bolsas o paralelismo não ajuda — o cálculo sequencial já é
tão rápido (~12 ms) que o overhead de criar threads e sincronizar o resultado consome o
ganho. Com 1 milhão de bolsas o cenário muda: o sequencial sobe para ~121 ms e 2 threads
entregam ~1,8x de speedup, um ganho real e reprodutível. O ganho não é linear (4 e 8 threads
não melhoram sobre 2) por três motivos: a Lei de Amdahl (a fusão final das fatias é uma
etapa sequencial obrigatória), o ambiente de medição ter só 2 núcleos físicos, e o custo fixo
de criar um `ExecutorService` novo a cada chamada. A complexidade assintótica do trabalho
total não muda — continua O(n log n) —, o que muda é o tempo de parede, que cai para perto de
O((n log n)/p) na prática.

Quando nem 8 threads bastarem (rede nacional real, dezenas de milhões de bolsas por
consulta), o próximo degrau não é mais threads na mesma JVM: é particionar entre múltiplas
instâncias da aplicação com os resultados agregados atrás de uma fila de trabalho, adotar um
motor de processamento distribuído, ou manter um agregado incremental (soma, soma dos
quadrados, contagem, percentil aproximado) atualizado a cada bolsa em vez de recalcular tudo
a cada chamada.

## Como rodar

### Pré-requisitos

- **JDK 21** (LTS). Confira com `java -version`.
- Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw`).

### Executando

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows (PowerShell ou cmd)
.\mvnw.cmd spring-boot:run
```

A aplicação sobe em `http://localhost:8080`. Teste o benchmark:

```
http://localhost:8080/api/v1/benchmark/dispersao-validade?tamanhoAmostra=1000000&threads=4&semente=42
```

### Testes

```bash
./mvnw test
```

## Estrutura do projeto

```
src/main/java/com/rotavital/
├── RotaVitalApplication.java
├── paralelo/                        # algoritmo puro (sequencial + paralelo)
│   ├── CalculadoraDispersaoValidade.java
│   ├── GeradorMassaBolsasSintetica.java
│   └── ResumoValidade.java
├── api/
│   ├── BenchmarkController.java     # endpoint /api/v1/benchmark/dispersao-validade
│   ├── ManipuladorDeErros.java      # traduz OperacaoInvalidaException em HTTP 409
│   └── dto/ResultadoBenchmarkResponse.java
├── servico/
│   ├── BenchmarkParaleloServico.java
│   └── excecao/
└── dominio/                         # só o necessário para Bolsa existir (tipo usado no benchmark)
    ├── Bolsa.java
    ├── Hemocentro.java
    ├── Local.java
    ├── Endereco.java
    └── enums/
```

## Tecnologias usadas

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework web | Spring Boot 4.1.1 |
| Build | Maven, via wrapper (`mvnw`) |
| Testes | JUnit 5 |
