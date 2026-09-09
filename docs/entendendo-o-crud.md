# Entendendo o CRUD do Rota Vital

Guia do código entregue na PI3-18. A ideia é que você abra este arquivo ao
lado do editor e acompanhe: cada seção aponta o arquivo e explica **por que**
está daquele jeito, não só o que faz.

A ordem aqui é a ordem em que uma requisição HTTP atravessa o sistema.

---

## Sumário

1. [O caminho de uma requisição](#1-o-caminho-de-uma-requisição)
2. [JPA e Hibernate: quem é quem](#2-jpa-e-hibernate-quem-é-quem)
3. [As entidades](#3-as-entidades)
4. [Herança: por que SINGLE_TABLE](#4-herança-por-que-single_table)
5. [Relacionamentos e o `mappedBy`](#5-relacionamentos-e-o-mappedby)
6. [Repositórios: a mágica do Spring Data](#6-repositórios-a-mágica-do-spring-data)
7. [DTOs: por que não devolver a entidade](#7-dtos-por-que-não-devolver-a-entidade)
8. [Serviços: onde moram as regras](#8-serviços-onde-moram-as-regras)
9. [Controllers: só HTTP](#9-controllers-só-http)
10. [Tratamento de erros](#10-tratamento-de-erros)
11. [Os três bugs que apareceram](#11-os-três-bugs-que-apareceram)
12. [Glossário de anotações](#12-glossário-de-anotações)

---

## 1. O caminho de uma requisição

Antes de qualquer detalhe, o mapa. Quando alguém faz:

```
POST /api/v1/bolsas
{"hemocentroId": "HC01", "tipoHemocomponente": "CONCENTRADO_HEMACIAS", ...}
```

o pedido atravessa cinco camadas:

```
        JSON entra
            │
            ▼
┌───────────────────────────┐
│  BolsaController          │  traduz HTTP: lê o corpo, escolhe o status
│  api/BolsaController.java │  de resposta. Nenhuma regra de negócio.
└───────────┬───────────────┘
            │  BolsaRequest (DTO)
            ▼
┌───────────────────────────┐
│  BolsaServico             │  as regras: bolsa pode nascer assim?
│  servico/BolsaServico.java│  o hemocentro existe? a validade fecha?
└───────────┬───────────────┘
            │  Bolsa (entidade)
            ▼
┌───────────────────────────┐
│  BolsaRepository          │  interface sem implementação:
│  repositorio/...java      │  o Spring Data gera o código
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────┐
│  Hibernate                │  monta o SQL, executa, devolve objetos
└───────────┬───────────────┘
            │  INSERT INTO bolsa ...
            ▼
        Banco (H2)
```

**Por que tantas camadas?** Cada uma tem um motivo prático:

- Se a regra de negócio estivesse no controller, você não conseguiria
  reaproveitá-la fora do HTTP (numa tarefa agendada, por exemplo).
- Se o controller falasse direto com o repositório, qualquer mudança no banco
  vazaria para a API.
- Se a entidade fosse devolvida direto no JSON, o formato da resposta ficaria
  amarrado ao formato da tabela.

A regra prática: **o controller não sabe o que é banco, o serviço não sabe o
que é HTTP.**

---

## 2. JPA e Hibernate: quem é quem

**JPA** é uma especificação. Um documento que define anotações (`@Entity`,
`@Id`, `@OneToMany`) e interfaces. Papel, não código executável.

**Hibernate** é a implementação: o código que lê essas anotações, gera o SQL,
abre conexão e transforma linha de tabela em objeto Java.

> Analogia: JPA é a regra do futebol, Hibernate é o time que joga. A regra
> diz que gol vale ponto; o time é quem chuta.

Você já viu o Hibernate se apresentar. Suba a aplicação e olhe o log:

```
org.hibernate.orm.core : Hibernate ORM core version 7.4.5.Final
Database dialect: H2Dialect
```

Aquele `H2Dialect` importa: cada banco tem SQL ligeiramente diferente, e o
Hibernate carrega um "dialeto" para falar a língua certa. É por isso que
trocar H2 por PostgreSQL na Entrega 02 vai ser quase indolor — muda a URL,
ele carrega outro dialeto, e o mesmo código Java gera SQL de Postgres.

**Onde isso está configurado:** `src/main/resources/application.properties`.

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

- `ddl-auto=update` faz o Hibernate criar as tabelas a partir das suas
  entidades anotadas. É ótimo para desenvolvimento e **perigoso em produção**
  (uma renomeação de campo pode criar coluna nova em vez de renomear).
- `show-sql=true` imprime as queries no console. **Ligue e olhe** — é a melhor
  forma de entender o que acontece por baixo.

---

## 3. As entidades

Arquivos em `src/main/java/com/rotavital/dominio/`.

Uma entidade é uma classe Java que vira tabela. O exemplo mais simples é o
`Veiculo`:

```java
@Entity
public class Veiculo {

    @Id
    private String id;
    private String placa;
    // ...
}
```

- `@Entity` diz: "isto vira tabela".
- `@Id` marca a chave primária.

Campos sem anotação viram coluna automaticamente. Só o que foge do padrão
precisa ser dito.

### O construtor sem argumentos

Toda entidade tem um construtor assim:

```java
protected Veiculo() {
    // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
}
```

**Por que ele existe?** Quando o Hibernate lê uma linha do banco, ele precisa
criar o objeto antes de saber o que colocar dentro. Ele faz isso em dois
passos: primeiro instancia vazio, depois preenche campo a campo por reflexão.
Sem construtor vazio, o primeiro passo é impossível.

**Por que `protected` e não `public`?** Para desencorajar o uso. Um
`new Veiculo()` no seu código criaria um objeto sem id, sem placa, inválido.
`protected` permite que o Hibernate use (ele está no mesmo mecanismo de
reflexão) mas sinaliza para o time: não chame isto.

### Por que os `final` sumiram

O domínio original tinha:

```java
private final String codigoRastreio;   // versão antes do JPA
```

`final` em Java significa: **este campo recebe valor uma vez e nunca mais
muda a referência**. É uma boa prática, porque um objeto imutável não pode ser
corrompido depois de criado.

O problema: o Hibernate cria o objeto vazio e preenche depois. Com `final`, o
campo teria que ser preenchido no construtor, e o construtor vazio não tem
como preencher. Conflito direto.

Então tirei o `final` dos campos que o Hibernate preenche. **Onde o `final`
ficou:** nas coleções, que são inicializadas na própria declaração:

```java
@OneToMany(mappedBy = "hemocentroOrigem")
private final List<Bolsa> estoque = new ArrayList<>();
```

Aqui `final` funciona e protege: ninguém pode trocar a lista inteira por
outra. O conteúdo dela continua mutável (dá para adicionar e remover), que é
exatamente o que se quer.

> **Detalhe que confunde:** `final` trava a *referência*, não o *conteúdo*.
> Uma `final List` não pode apontar para outra lista, mas aceita `add()` e
> `remove()` normalmente. É como um endereço fixo: a casa é sempre a mesma, os
> móveis mudam.

### Encapsulamento das coleções

Repare no getter:

```java
public List<Bolsa> getEstoque() {
    return Collections.unmodifiableList(estoque);
}

public void adicionarBolsa(Bolsa bolsa) {
    estoque.add(bolsa);
}
```

O getter devolve uma **visão só de leitura**. Se alguém tentar
`hemocentro.getEstoque().add(bolsa)`, recebe exceção. A única porta de entrada
é o `adicionarBolsa()`.

**Por que isso importa?** Porque amanhã você pode precisar validar algo antes
de aceitar a bolsa (capacidade máxima, tipo compatível com o hemocentro). Com
o getter aberto, não haveria onde colocar a validação — o código já estaria
espalhado por toda parte chamando `.add()` direto.

### `Endereco` é diferente: `@Embeddable`

```java
@Embeddable
public class Endereco {
    private String logradouro;
    private String cidade;
    // ...
}
```

`Endereco` **não é entidade**. Não tem `@Id`, não tem tabela própria.

A diferença conceitual: um endereço não tem identidade. Dois endereços com os
mesmos dados são o mesmo endereço. Já dois hemocentros com o mesmo nome são
duas instituições diferentes, porque cada um tem seu CNPJ.

Na prática, `@Embeddable` faz as colunas do endereço serem gravadas **dentro
da tabela da unidade**:

```
tabela LOCAL
┌──────┬──────────────┬─────────────┬──────────┬─────────┐
│ id   │ nome         │ logradouro  │ cidade   │ cep     │
├──────┼──────────────┼─────────────┼──────────┼─────────┤
│ HC01 │ HEMOPE       │ R. Joaquim… │ Recife   │ 52011…  │
└──────┴──────────────┴─────────────┴──────────┴─────────┘
       └── de Local ──┘└──── de Endereco (embutido) ─────┘
```

Sem tabela separada, sem chave estrangeira, sem `JOIN` para buscar o endereço.
Do lado do Java continuam sendo duas classes; do lado do banco, uma tabela só.

No `Local`, o campo é marcado com `@Embedded`:

```java
@Embedded
private Endereco endereco;
```

---

## 4. Herança: por que SINGLE_TABLE

Arquivo: `dominio/Local.java`.

O domínio tem `Local` como classe abstrata, e `Hemocentro` e `Hospital`
herdando dela. Bancos relacionais não têm herança, então o JPA oferece três
estratégias de tradução. Escolhi uma:

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class Local {
```

### As três opções

**`SINGLE_TABLE` (escolhida):** uma tabela só, com todas as colunas das duas
subclasses. Uma coluna extra (`DTYPE`) diz qual é qual.

```
tabela LOCAL
┌──────┬─────────────┬──────────────┬────────────┬─────────┐
│ id   │ DTYPE       │ nome         │ cnpj       │ cnes    │
├──────┼─────────────┼──────────────┼────────────┼─────────┤
│ HC01 │ Hemocentro  │ HEMOPE       │ 11222333…  │ (null)  │
│ HO04 │ Hospital    │ Real Port.   │ (null)     │ 2400001 │
└──────┴─────────────┴──────────────┴────────────┴─────────┘
```

Vantagem: consulta rápida, sem `JOIN`. Desvantagem: colunas nulas — o
hemocentro nunca preenche `cnes`, o hospital nunca preenche `cnpj`.

**`JOINED`:** uma tabela para `Local` e uma para cada subclasse, ligadas por
chave estrangeira. Sem colunas nulas, mas toda consulta precisa de `JOIN`.

**`TABLE_PER_CLASS`:** uma tabela completa por subclasse, sem tabela de base.
Rápido para buscar um tipo específico, péssimo para buscar "todos os locais".

### Por que SINGLE_TABLE aqui

Três razões, na ordem em que pesaram:

1. **As subclasses têm quase os mesmos campos.** `Hemocentro` acrescenta
   `cnpj`, `Hospital` acrescenta `cnes`. Duas colunas nulas é um custo
   pequeno. Se a diferença fosse de 15 campos cada, `JOINED` seria melhor.

2. **O grafo de distribuição trata todos como nós.** O `Grafo` (PI3-17) não
   distingue hemocentro de hospital: são vértices. Consultar todos os locais
   é operação frequente, e em `SINGLE_TABLE` é um `SELECT` simples.

3. **É a estratégia padrão do JPA.** Quando não há motivo forte para
   divergir, seguir o padrão facilita a vida de quem lê depois.

> **Se o professor perguntar:** o argumento contra é normalização. Uma coluna
> que só se aplica a metade das linhas é, tecnicamente, um cheiro de má
> modelagem. A defesa é pragmática: são duas colunas, o ganho de performance é
> real, e o banco é de um projeto acadêmico com dados sintéticos.

---

## 5. Relacionamentos e o `mappedBy`

Este é o ponto que mais gera confusão em JPA, então vale devagar.

### O problema

No mundo Java, um relacionamento tem dois lados:

```java
// Em Hemocentro
private List<Bolsa> estoque;      // "tenho muitas bolsas"

// Em Bolsa
private Hemocentro hemocentroOrigem;   // "pertenço a um hemocentro"
```

No mundo do banco, existe **uma coluna só**: `bolsa.hemocentro_origem_id`.

Se o Hibernate tratasse os dois lados como independentes, ele tentaria gravar
a mesma informação duas vezes, e poderia criar uma tabela de junção
desnecessária.

### A solução: um lado é dono

```java
// Bolsa.java — o lado DONO
@ManyToOne
@JoinColumn(name = "hemocentro_origem_id")
private Hemocentro hemocentroOrigem;

// Hemocentro.java — o lado INVERSO
@OneToMany(mappedBy = "hemocentroOrigem")
private final List<Bolsa> estoque = new ArrayList<>();
```

`mappedBy = "hemocentroOrigem"` diz: *"eu não controlo esta relação. O
controle está no campo chamado `hemocentroOrigem`, lá na classe `Bolsa`."*

**Como decidir quem é o dono?** Quem tem a chave estrangeira. Aqui a coluna
está na tabela `bolsa`, então `Bolsa` é o dono. Regra prática: em
`@OneToMany`/`@ManyToOne`, o lado `@ManyToOne` é sempre o dono.

### A armadilha do lado inverso

Isto **não** grava nada:

```java
hemocentro.adicionarBolsa(bolsa);   // só mexe na lista em memória
repositorio.save(hemocentro);       // o Hibernate ignora, não é o dono
```

O que grava de verdade é mexer no lado dono. Por isso, em
`Requisicao.adicionarItem()`, eu amarro os dois lados:

```java
public void adicionarItem(ItemRequisicao item) {
    itens.add(item);              // lado inverso: a lista em memória
    item.setRequisicao(this);     // lado dono: a chave estrangeira
}
```

Sem a segunda linha, o item seria gravado com `requisicao_id` nulo e ficaria
órfão. **Este é o erro mais comum de quem começa em JPA.**

### `cascade` e `orphanRemoval`

```java
@OneToMany(mappedBy = "requisicao", cascade = CascadeType.ALL, orphanRemoval = true)
private final List<ItemRequisicao> itens = new ArrayList<>();
```

- **`cascade = ALL`**: o que acontece com a requisição acontece com os itens.
  Salvou a requisição, salva os itens junto. Apagou, apaga os itens.
- **`orphanRemoval = true`**: item removido da lista é apagado do banco, não
  fica solto.

**Por que aqui e não em `Hemocentro`/`Bolsa`?** Porque um item de requisição
não existe sem a requisição — é parte dela. Já uma bolsa existe por si: apagar
o hemocentro **não** deve apagar o estoque, deve ser impedido. E é exatamente
o que o `HemocentroServico.remover()` faz.

### O caso especial da Rota

```java
@ManyToMany
@JoinTable(
        name = "rota_bolsa",
        joinColumns = @JoinColumn(name = "rota_id"),
        inverseJoinColumns = @JoinColumn(name = "bolsa_codigo_rastreio"))
private final List<Bolsa> bolsas = new ArrayList<>();
```

Aqui usei tabela de junção própria. **Por quê?** A `Bolsa` já tem um
`@ManyToOne` para o hemocentro de origem. Se a rota também gravasse na tabela
`bolsa`, seriam duas relações disputando o mesmo espaço. Uma tabela separada
(`rota_bolsa`, com duas colunas) resolve sem ambiguidade.

---

## 6. Repositórios: a mágica do Spring Data

Arquivos em `repositorio/`. Olhe o `HemocentroRepository`:

```java
public interface HemocentroRepository extends JpaRepository<Hemocentro, String> {

    List<Hemocentro> findByEnderecoCidadeIgnoreCase(String cidade);

    boolean existsByCnpj(String cnpj);
}
```

**É uma interface. Não existe classe implementando ela.** Nenhum arquivo
`HemocentroRepositoryImpl` foi escrito.

O Spring Data lê o nome dos métodos, entende o que você quer e gera a
implementação em tempo de execução. Você escreveu a *intenção*, ele escreveu
o SQL.

### Como o nome vira consulta

```
findByEnderecoCidadeIgnoreCase
│   │  │        │      └─ ignorando maiúscula/minúscula
│   │  │        └─ ...o campo cidade
│   │  └─ dentro do campo endereco...
│   └─ filtrando por
└─ me traga
```

Vira, mais ou menos:

```sql
SELECT * FROM local WHERE UPPER(cidade) = UPPER(?)
```

Repare que `EnderecoCidade` funciona porque `Endereco` é `@Embeddable`: as
colunas estão na própria tabela.

`JpaRepository` já traz de graça: `save`, `findById`, `findAll`, `delete`,
`count`, `existsById`. Você só declara o que foge disso.

**Vale testar:** ligue `spring.jpa.show-sql=true`, chame
`GET /api/v1/hemocentros?cidade=Recife` e veja o SQL aparecer no console.

### Por que os repositórios são tão pequenos

Na primeira versão eu tinha escrito uns 15 métodos de consulta. Depois
verifiquei quais eram realmente chamados e **apaguei 12**. Sobrou o que se
usa.

Método de repositório não usado não é "preparação para o futuro", é código
morto: mais uma coisa para ler, manter e explicar, sem nenhum ganho. Se
precisar depois, escreve-se em 30 segundos.

---

## 7. DTOs: por que não devolver a entidade

Arquivos em `api/dto/`.

DTO = *Data Transfer Object*. É a forma dos dados na fronteira da API, que não
precisa ser igual à forma no banco.

```java
public record BolsaRequest(
        @NotNull(message = "hemocentroId e obrigatorio")
        String hemocentroId,

        @Positive(message = "volumeMl deve ser maior que zero")
        int volumeMl,

        @PastOrPresent(message = "dataColeta nao pode ser futura")
        LocalDate dataColeta) {
}
```

### Três motivos para não devolver a entidade direto

**1. Segurança.** A entidade tem tudo. Se o cliente pudesse mandar qualquer
campo, poderia mandar `{"status": "ENTREGUE"}` numa bolsa recém-criada e
pular todo o fluxo. Por isso `BolsaRequest` **não tem** `status` nem
`dataValidade` — esses o servidor calcula:

```java
// Em Bolsa.java, no construtor:
this.dataValidade = dataColeta.plusDays(tipo.getValidadeDias());
this.status = StatusBolsa.DISPONIVEL;
```

O cliente não escolhe a validade. Ela vem do tipo de hemocomponente, conforme
a regra do domínio (plaquetas 5 dias, hemácias 42, plasma 365).

**2. Laço infinito.** `Hemocentro` tem lista de `Bolsa`, e cada `Bolsa` tem
referência ao `Hemocentro`. Se o Jackson serializasse a entidade direto, ele
entraria em recursão até estourar a memória.

**3. Desacoplamento.** Renomear uma coluna no banco não pode quebrar o
contrato com o front-end. Com DTO, você ajusta o mapeamento e a API continua
igual.

### Por que `record` e não classe

```java
public record EnderecoDto(String logradouro, String cidade, ...) { }
```

Um `record` gera automaticamente: construtor, getters, `equals`, `hashCode` e
`toString`. Como um DTO só carrega dados e não tem comportamento, é o formato
ideal — e são imutáveis por natureza.

A mesma coisa como classe teria umas 60 linhas de código repetitivo.

### O mapeamento

Cada `*Response` tem um método estático `de(...)`:

```java
public static BolsaResponse de(Bolsa bolsa) {
    return new BolsaResponse(
            bolsa.getCodigoRastreio(),
            bolsa.getHemocentroOrigem() == null ? null : bolsa.getHemocentroOrigem().getId(),
            // ...
    );
}
```

Repare no `== null ? null :`. Sem isso, uma bolsa sem hemocentro (possível em
teste) causaria `NullPointerException` na serialização.

---

## 8. Serviços: onde moram as regras

Arquivos em `servico/`. É a camada mais importante do que foi entregue, e a
que você mais vai precisar defender numa apresentação.

### Injeção por construtor

```java
@Service
public class HemocentroServico {

    private final HemocentroRepository hemocentros;
    private final BolsaRepository bolsas;

    public HemocentroServico(HemocentroRepository hemocentros, BolsaRepository bolsas) {
        this.hemocentros = hemocentros;
        this.bolsas = bolsas;
    }
```

`@Service` diz ao Spring: "crie uma instância disto e guarde". Quando ele cria,
vê que o construtor pede dois repositórios e passa os dois automaticamente.
Isso é **injeção de dependência**.

**Por que isso é melhor que `new HemocentroRepository()`?** Porque a classe
não precisa saber *como* construir suas dependências. Em teste, você pode
passar uma implementação falsa. E os campos podem ser `final`, porque só o
construtor os preenche.

### `@Transactional`

```java
@Transactional
public Alocacao alocar(String requisicaoId, String itemId, AlocacaoRequest dados) {
```

Uma transação é um bloco tudo-ou-nada. A alocação muda três coisas: cria a
`Alocacao`, incrementa o contador do item e muda o status da bolsa para
`RESERVADA`. Se o método falhar no meio, **nada** é gravado.

Sem transação, você poderia ter uma bolsa marcada como reservada sem alocação
correspondente — estoque fantasma.

`@Transactional(readOnly = true)` nas consultas: avisa o Hibernate que nada
vai mudar, e ele otimiza (não precisa rastrear alterações).

### A máquina de estados

`BolsaServico.java`, linhas iniciais:

```java
private static final Map<StatusBolsa, Set<StatusBolsa>> TRANSICOES = new EnumMap<>(StatusBolsa.class);

static {
    TRANSICOES.put(StatusBolsa.DISPONIVEL, Set.of(StatusBolsa.RESERVADA, StatusBolsa.DESCARTADA));
    TRANSICOES.put(StatusBolsa.RESERVADA, Set.of(StatusBolsa.DISPONIVEL, StatusBolsa.EM_TRANSITO, StatusBolsa.DESCARTADA));
    TRANSICOES.put(StatusBolsa.EM_TRANSITO, Set.of(StatusBolsa.ENTREGUE, StatusBolsa.DESCARTADA));
    TRANSICOES.put(StatusBolsa.ENTREGUE, Set.of());
    TRANSICOES.put(StatusBolsa.DESCARTADA, Set.of());
}
```

Lendo o mapa como diagrama:

```
   DISPONIVEL ──────► RESERVADA ──────► EM_TRANSITO ──────► ENTREGUE ✗
       │   ▲              │                   │
       │   └──────────────┘                   │
       │                                      │
       └──────────► DESCARTADA ✗ ◄────────────┘
                    (também a partir de RESERVADA)

   ✗ = estado final, não sai mais
```

**Por que um mapa e não uma sequência de `if`?** Porque a regra fica visível
num lugar só. Para saber o que pode acontecer com uma bolsa entregue, você lê
uma linha: `Set.of()` — nada. Com `if` espalhado, teria que caçar pelo código.

`EnumMap` em vez de `HashMap`: é otimizado para chaves enum, usa um array
interno indexado pela posição da constante.

### As regras de compatibilidade

`RequisicaoServico.alocar()` é o coração do sistema. É onde estoque e demanda
se encontram:

```java
if (bolsa.getStatus() != StatusBolsa.DISPONIVEL) { ... }
if (bolsa.estaVencida(LocalDate.now())) { ... }
if (bolsa.getTipo() != item.getTipo()) { ... }
if (bolsa.getGrupoSanguineo() != item.getGrupoSanguineo()) { ... }
```

Quatro guardas, na ordem do mais barato para o mais específico. Todas lançam
`OperacaoInvalidaException`, que vira HTTP 409.

> **Limitação consciente:** a compatibilidade aqui é por igualdade exata.
> Na prática transfusional, O− é doador universal e AB+ é receptor universal.
> Implementar isso é outra story, e o README já registra que a compatibilidade
> é didática.

### Status derivado, não informado

```java
public void recalcularStatus() {
    if (status == StatusRequisicao.CANCELADA) {
        return;
    }
    if (estaCompleta()) {
        status = StatusRequisicao.ATENDIDA;
    } else if (itens.stream().anyMatch(i -> i.getQuantidadeAlocada() > 0)) {
        status = StatusRequisicao.PARCIALMENTE_ATENDIDA;
    } else {
        status = StatusRequisicao.PENDENTE;
    }
}
```

O cliente **não escolhe** o status da requisição. Ele é calculado a partir das
alocações, e recalculado sempre que uma alocação é criada ou desfeita.

Por isso o `PATCH /api/v1/requisicoes/{id}` só aceita `CANCELADA`. Qualquer
outro valor é recusado com 409 e uma mensagem explicando.

**Por quê?** Se o cliente pudesse mandar `{"status": "ATENDIDA"}` numa
requisição sem nenhuma bolsa alocada, o sistema mentiria. O estado tem que ser
consequência dos fatos, não uma declaração.

---

## 9. Controllers: só HTTP

Arquivos em `api/`. Compare o tamanho: `HemocentroController` tem 109 linhas,
`RequisicaoServico` tem 269. Isso é proposital.

```java
@PostMapping
public ResponseEntity<HemocentroResponse> criar(@Valid @RequestBody HemocentroRequest dados) {
    Hemocentro criado = hemocentros.criar(dados);
    return ResponseEntity
            .created(URI.create("/api/v1/hemocentros/" + criado.getId()))
            .body(HemocentroResponse.de(criado));
}
```

O método faz três coisas, todas de protocolo:

1. `@Valid` dispara a validação das anotações do DTO
2. Chama o serviço
3. Monta a resposta 201 com o cabeçalho `Location`

**Nenhuma decisão de negócio.** Se você precisar dessa lógica fora do HTTP,
chama o serviço direto.

### `ResponseEntity.created()`

Devolve 201 e o cabeçalho `Location` apontando para o recurso criado. É
convenção REST: o cliente descobre onde o novo recurso mora sem precisar
adivinhar.

### Por que `PATCH` e não `PUT` nas bolsas

- `PUT` substitui o recurso inteiro
- `PATCH` altera parte dele

Uma bolsa não se edita por inteiro: o código de rastreio, o tipo e a data de
coleta são imutáveis por natureza. O que muda ao longo da vida dela é o
status. Por isso `PATCH /api/v1/bolsas/{id}` com `{"status": "..."}`, e
nenhum `PUT`.

Já hemocentro e hospital têm `PUT`, porque faz sentido reescrever os dados
cadastrais inteiros.

### Sub-recursos aninhados

```
/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes
```

Um item de requisição não existe fora de uma requisição. Uma alocação não
existe fora de um item. A URL reflete essa hierarquia, em vez de ter
`/api/v1/itens` solto.

---

## 10. Tratamento de erros

Arquivo: `api/ManipuladorDeErros.java`.

```java
@RestControllerAdvice
public class ManipuladorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Recurso nao encontrado");
        return problema;
    }
```

`@RestControllerAdvice` é um interceptador global: qualquer exceção que suba
de qualquer controller passa por aqui.

**Sem isso**, cada método de cada controller precisaria de `try/catch`. Com
isso, o serviço só lança a exceção e alguém lá em cima traduz.

### O mapeamento

| Exceção | HTTP | Quando |
|---|---|---|
| `RecursoNaoEncontradoException` | 404 | id não existe |
| `OperacaoInvalidaException` | 409 | existe, mas o estado não permite |
| `MethodArgumentNotValidException` | 400 | corpo malformado |

A distinção entre 404 e 409 importa: 404 diz "não achei", 409 diz "achei, mas
não pode". São problemas diferentes e o cliente reage diferente.

### `ProblemDetail` (RFC 7807)

Formato padronizado de erro de API. A resposta sai assim:

```json
{
  "status": 409,
  "title": "Operacao nao permitida",
  "detail": "Transicao invalida: ENTREGUE -> DESCARTADA",
  "instance": "/api/v1/bolsas/fc2830af-..."
}
```

Para erro de validação, adicionei um campo com o detalhe por campo:

```json
{
  "status": 400,
  "title": "Dados invalidos",
  "detail": "Ha campos invalidos na requisicao",
  "campos": {
    "nome": "nome e obrigatorio",
    "endereco.uf": "uf deve ter 2 letras"
  }
}
```

O front-end consegue marcar exatamente o campo errado no formulário.

---

## 11. Os três bugs que apareceram

Esta seção existe porque errar e consertar ensina mais que acertar de primeira.

### Bug 1: o Jackson e o `toString()`

**Sintoma:** `POST /api/v1/bolsas` devolvia 400 sem explicação útil.

**Log:**

```
Cannot deserialize value of type TipoHemocomponente from String
"CONCENTRADO_HEMACIAS": not one of the values accepted for Enum class:
[Plasma Fresco Congelado, Crioprecipitado, Concentrado de Plaquetas, ...]
```

**Causa:** os enums do domínio sobrescrevem `toString()`:

```java
@Override
public String toString() {
    return descricao;    // "Concentrado de Hemacias"
}
```

O Jackson usa `toString()` para serializar enum. Então a API esperava
`"Concentrado de Hemacias"`, mas o contrato pede `CONCENTRADO_HEMACIAS`.

**Correção:** anotar um método com `@JsonValue`, que tem prioridade sobre o
`toString()`:

```java
@JsonValue
public String comoJson() {
    return name();
}
```

**Por que não simplesmente apagar o `toString()`?** Porque ele é dos colegas e
serve para exibição — `Bolsa.toString()` usa `getDescricao()` para log legível.
Apagar resolveria o meu problema criando outro. `@JsonValue` separa as duas
responsabilidades: `toString()` para humano, `@JsonValue` para máquina.

**A lição:** o Jackson usa `toString()` em enum. Se você sobrescreve
`toString()` num enum que trafega em JSON, ou anota `@JsonValue`, ou o
contrato quebra.

### Bug 2: uma regra de negócio minha estava errada

**Sintoma:** o teste `cancelamentoLiberaBolsa` falhou com `expected:<200> but
was:<409>`.

**O que eu tinha escrito:**

```java
if (requisicao.getStatus() == StatusRequisicao.ATENDIDA) {
    throw new OperacaoInvalidaException("Requisicao ja atendida nao pode ser cancelada");
}
```

Parecia razoável. Mas o teste criava uma requisição de **1 bolsa**, alocava
essa bolsa (o que deixa a requisição `ATENDIDA`) e tentava cancelar.

**O raciocínio errado:** confundi "atendida" com "entregue". `ATENDIDA` só
significa que todas as bolsas foram **reservadas**. Elas ainda estão no
hemocentro. Uma reserva se desfaz sem problema nenhum.

**A regra certa** não olha o status da requisição, olha o das bolsas:

```java
boolean bolsaJaSaiu = requisicao.getItens().stream()
        .flatMap(item -> item.getAlocacoes().stream())
        .anyMatch(a -> a.getBolsa().getStatus() != StatusBolsa.RESERVADA);

if (bolsaJaSaiu) {
    throw new OperacaoInvalidaException(
            "Ha bolsas ja despachadas para esta requisicao. Cancele a rota antes.");
}
```

Enquanto tudo está `RESERVADA`, cancela e devolve ao estoque. Se alguma já
está `EM_TRANSITO` ou `ENTREGUE`, aí não — seria fingir que a entrega não
aconteceu.

**A lição:** o teste não pegou um erro de digitação, pegou um erro de
raciocínio sobre o domínio. É exatamente para isso que teste de integração
serve. Um teste unitário com mock provavelmente teria passado, porque eu
mesmo teria montado o mock com a suposição errada.

### Bug 3: o que 46 testes verdes não pegaram

Este apareceu depois, na PI3-21, e é o mais interessante dos três.

**Sintoma:** `GET /api/v1/requisicoes` respondia **HTTP 500 em produção** e
funcionava normalmente em desenvolvimento. Todos os testes passando.

**Log:**

```
org.hibernate.LazyInitializationException: Cannot lazily initialize
collection of role 'com.rotavital.dominio.Requisicao.itens'
with key 'REQ00001' (no session)
```

**A causa.** Os controllers convertiam entidade em DTO:

```java
// RequisicaoController — como estava
return requisicoes.listar(status, hospitalId, prioridade).stream()
        .map(RequisicaoResponse::de)     // <- aqui
        .toList();
```

O `@Transactional` do serviço termina quando o método do serviço retorna. O
`.map()` roda **depois disso**, já no controller. E o `RequisicaoResponse.de()`
percorre `requisicao.getItens()`, que é uma coleção carregada por demanda:
o Hibernate só vai ao banco buscá-la quando alguém a acessa.

Nesse instante a sessão já fechou. Não há como buscar. Exceção.

**Por que só em produção?** Por causa de uma configuração chamada
`spring.jpa.open-in-view`, que vem **ligada por padrão**. Ela mantém a sessão
do banco aberta até a resposta HTTP terminar de ser escrita, o que faz o
acesso tardio funcionar — disparando uma consulta invisível, fora de qualquer
transação.

No perfil de produção eu desliguei:

```properties
spring.jpa.open-in-view=false
```

E o bug, que sempre esteve lá, apareceu.

**A correção:** converter para DTO **dentro** do serviço, com a transação
ainda aberta.

```java
// RequisicaoServico — como ficou
@Transactional(readOnly = true)
public List<RequisicaoResponse> listar(...) {
    return requisicoes.findAll().stream()
            .filter(...)
            .map(RequisicaoResponse::de)   // agora dentro da transação
            .toList();
}
```

O controller passou a só repassar o que recebe.

**Três lições, e a terceira é a que interessa:**

1. **`open-in-view` ligado esconde problema.** Ele é conveniente e por isso é
   o padrão, mas transforma erro de arquitetura em consulta silenciosa. Muita
   gente descobre isso quando o sistema fica lento e ninguém sabe por quê.

2. **A conversão para DTO pertence ao serviço.** Ela toca as entidades, e
   tocar entidade fora da transação é território de erro. O controller deve
   receber DTO pronto.

3. **Teste verde não é prova de que está certo.** Os 46 testes passavam porque
   `@Transactional` na classe de teste mantém uma transação aberta durante o
   método inteiro, incluindo a serialização. O ambiente de teste era mais
   permissivo que o de produção, e o bug morava justamente nessa diferença.

   O que pegou o erro não foi teste nenhum: foi rodar o jar de verdade, com o
   perfil de verdade, e clicar nos endpoints. É por isso que a PI3-21 termina
   com o workflow chamando `/actuator/health` e `/h2-console` na URL pública,
   em vez de confiar que o build verde basta.

---

## 12. Glossário de anotações

Referência rápida do que apareceu no código.

### JPA (persistência)

| Anotação | O que faz |
|---|---|
| `@Entity` | a classe vira tabela |
| `@Id` | marca a chave primária |
| `@Embeddable` | classe cujas colunas são embutidas em outra tabela |
| `@Embedded` | o campo é uma classe `@Embeddable` |
| `@Inheritance(strategy = ...)` | como traduzir herança para tabelas |
| `@Enumerated(EnumType.STRING)` | grava o enum como texto, não como número |
| `@OneToMany(mappedBy = "x")` | lado inverso: o dono é o campo `x` da outra classe |
| `@ManyToOne` | lado dono, tem a chave estrangeira |
| `@OneToOne` | um para um |
| `@ManyToMany` + `@JoinTable` | muitos para muitos, com tabela de junção |
| `@JoinColumn(name = "...")` | nome da coluna de chave estrangeira |
| `cascade = CascadeType.ALL` | operações se propagam para os filhos |
| `orphanRemoval = true` | filho removido da lista é apagado do banco |

### Spring (estrutura)

| Anotação | O que faz |
|---|---|
| `@Service` | componente de regra de negócio, gerenciado pelo Spring |
| `@Component` | componente genérico gerenciado pelo Spring |
| `@Transactional` | o método é tudo-ou-nada |
| `@Transactional(readOnly = true)` | só leitura, permite otimização |
| `@Profile("!test")` | só ativo fora do perfil de teste |

### Spring Web (HTTP)

| Anotação | O que faz |
|---|---|
| `@RestController` | classe que responde HTTP devolvendo JSON |
| `@RequestMapping("/caminho")` | prefixo de URL da classe |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping` | método HTTP |
| `@PathVariable` | pega valor da URL (`/bolsas/{id}`) |
| `@RequestParam` | pega valor da query string (`?cidade=Recife`) |
| `@RequestBody` | converte o JSON do corpo em objeto |
| `@Valid` | dispara a validação das anotações do DTO |
| `@RestControllerAdvice` | interceptador global de exceções |
| `@ExceptionHandler(X.class)` | trata a exceção X |

### Validação (Jakarta Bean Validation)

| Anotação | O que valida |
|---|---|
| `@NotNull` | não pode ser nulo |
| `@NotBlank` | texto não nulo e não vazio |
| `@NotEmpty` | coleção com pelo menos um item |
| `@Positive` | número maior que zero |
| `@Size(min, max)` | tamanho do texto ou coleção |
| `@PastOrPresent` | data não pode ser futura |

### Jackson (JSON)

| Anotação | O que faz |
|---|---|
| `@JsonValue` | o método marcado define como o objeto vira JSON |

---

## Para explorar por conta

Três exercícios que ensinam mais que ler:

**1. Ver o SQL.** Suba a aplicação com `show-sql=true` (já está ligado), faça
`GET /api/v1/hemocentros?cidade=Recife` e leia o `SELECT` que o Hibernate
gerou a partir do nome do método. É o momento em que a ficha costuma cair.

**2. Quebrar de propósito.** Em `Requisicao.adicionarItem()`, comente a linha
`item.setRequisicao(this)` e suba a aplicação. Depois olhe a coluna
`requisicao_id` no H2 console:

```sql
SELECT id, requisicao_id FROM ITEM_REQUISICAO;
```

Ela vem nula. O item foi gravado (o `cascade = ALL` cuidou disso), mas sem o
vínculo — órfão no banco.

> **Curiosidade que eu descobri testando:** os testes de integração continuam
> passando com a linha comentada. Motivo: dentro de uma transação, o Hibernate
> mantém os objetos em memória e responde as consultas dali, sem ir ao banco.
> O problema só aparece na próxima transação, quando ele tenta reconstruir o
> objeto a partir das colunas. É por isso que olhar a tabela ensina mais que
> olhar o teste, nesse caso específico.

**3. Olhar a tabela.** Abra `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:rotavital`, usuário `sa`, senha vazia) e rode
`SELECT * FROM LOCAL`. Você vai ver a coluna `DTYPE` e as colunas nulas da
estratégia `SINGLE_TABLE`, exatamente como no diagrama da seção 4.

---

## Onde cada coisa está

```
src/main/java/com/rotavital/
├── api/                    controllers + manipulador de erros
│   └── dto/                DTOs de entrada e saída
├── config/
│   └── CargaInicial.java   dados sintéticos ao subir
├── dominio/                entidades JPA
│   └── enums/
├── estruturas/             grafo, FEFO, índice (PI3-17)
├── repositorio/            interfaces JpaRepository
└── servico/                regras de negócio
    └── excecao/

src/test/java/com/rotavital/
├── api/                    testes de integração (MockMvc)
└── estruturas/             testes unitários das estruturas
```
