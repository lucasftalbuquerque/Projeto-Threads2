# Contrato de API REST — Hemobank

---

## 1. Modelos de dados

### Unidade (base de Hemocentro / Hospital)

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | Identificador único |
| `nome` | string | sim | Nome fantasia da unidade |
| `cidade` | string | sim | Cidade sintética |
| `uf` | string(2) | sim | UF |
| `telefone` | string | não | Telefone de contato sintético |
| `email` | string | não | E-mail de contato sintético |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

`HemocentroResumo` / `HospitalResumo`: `id`, `nome`, `cidade`, `uf`.
`HemocentroDetalhe` / `HospitalDetalhe`: todos os campos acima.

### Bolsa

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `hemocentroId` | UUID | sim | Hemocentro onde a bolsa está armazenada |
| `tipoHemocomponente` | enum | sim | `SANGUE_TOTAL`, `CONCENTRADO_HEMACIAS`, `PLASMA`, `PLAQUETAS`, `CRIOPRECIPITADO` |
| `grupoSanguineo` | string | sim | Notação ABO/Rh didática: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-` |
| `volumeMl` | integer | sim | Volume da bolsa em mililitros |
| `dataColeta` | date | sim | Data da coleta |
| `dataValidade` | date | sim | Data de validade (usada para priorização FEFO) |
| `status` | enum | gerado pelo backend (`DISPONIVEL` na criação) | `DISPONIVEL`, `RESERVADA`, `ALOCADA`, `EM_TRANSITO`, `UTILIZADA`, `DESCARTADA` |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

`BolsaResumo`: `id`, `tipoHemocomponente`, `grupoSanguineo`, `status`, `dataValidade`, `hemocentroId`.
`BolsaDetalhe`: todos os campos.

### Requisição e Item de Requisição

**Requisição**

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `hospitalId` | UUID | sim | Hospital requisitante |
| `prioridade` | enum | sim | `ROTINA`, `URGENTE`, `EMERGENCIA` |
| `observacao` | string | não | Texto livre, opcional |
| `status` | enum | gerado pelo backend (`ABERTA` na criação) | `ABERTA`, `PARCIALMENTE_ATENDIDA`, `ATENDIDA`, `CANCELADA` |
| `itens` | array de `ItemRequisicao` | sim (mín. 1 item) | Ver abaixo |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

**ItemRequisicao**

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `tipoHemocomponente` | enum | sim | Mesmo enum de `Bolsa.tipoHemocomponente` |
| `grupoSanguineo` | string | sim | Mesma notação de `Bolsa.grupoSanguineo` |
| `quantidadeSolicitada` | integer | sim (> 0) | Quantidade de bolsas necessárias |
| `quantidadeAlocada` | integer | calculado | Soma das alocações vinculadas a este item |
| `status` | enum | calculado | `PENDENTE`, `PARCIALMENTE_ALOCADO`, `ALOCADO` |

`RequisicaoResumo`: `id`, `hospitalId`, `status`, `prioridade`, `criadoEm`.
`RequisicaoDetalhe`: todos os campos de Requisição, com `itens` expandido (`ItemRequisicaoDetalhe`).

**AlocacaoDetalhe**: `id`, `itemRequisicaoId`, `bolsaId`, `alocadoEm`.

### Rota

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `hemocentroOrigemId` | UUID | sim | — |
| `hospitalDestinoId` | UUID | sim | — |
| `previsaoSaida` | date-time | sim | — |
| `previsaoChegada` | date-time | sim | — |
| `saidaReal` | date-time | não (preenchido em trânsito) | — |
| `chegadaReal` | date-time | não (preenchido na conclusão) | — |
| `status` | enum | gerado pelo backend (`PLANEJADA` na criação) | `PLANEJADA`, `EM_TRANSITO`, `CONCLUIDA`, `ATRASADA`, `CANCELADA` |
| `bolsas` | array de `BolsaResumo` | não (embarque via sub-recurso) | — |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

`RotaResumo`: `id`, `hemocentroOrigemId`, `hospitalDestinoId`, `status`, `previsaoSaida`, `previsaoChegada`.
`RotaDetalhe`: todos os campos.

### Indicadores (somente leitura)

**EstoqueIndicador** (item de `EstoqueIndicador[]`): `hemocentroId`, `hemocentroNome`, `tipoHemocomponente`,
`grupoSanguineo`, `quantidadeDisponivel`, `quantidadeProximaValidade72h`.

**RequisicaoIndicador**: `periodoInicio`, `periodoFim`, `totalRequisicoes`, `requisicoesAtendidas`,
`taxaAtendimentoPercentual`, `tempoMedioAtendimentoHoras`.

**RotaIndicador**: `periodoInicio`, `periodoFim`, `totalRotas`, `tempoMedioEntregaHoras`, `rotasAtrasadas`,
`percentualAtrasadas`.

---

## 2. Endpoints por recurso

### 2.1 Hemocentros — `/api/v1/hemocentros`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/hemocentros` | query: `cidade`, `uf`, `page`, `size`, `sort` | — | `Page<HemocentroResumo>` | 200 |
| GET | `/api/v1/hemocentros/{id}` | path: `id` | — | `HemocentroDetalhe` | 200, 404 |
| POST | `/api/v1/hemocentros` | — | `HemocentroDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HemocentroDetalhe` | 201, 400, 401, 403 |
| PUT | `/api/v1/hemocentros/{id}` | path: `id` | `HemocentroDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HemocentroDetalhe` | 200, 400, 404 |
| DELETE | `/api/v1/hemocentros/{id}` | path: `id` | — | — | 204, 404, 409 |
| GET | `/api/v1/hemocentros/{id}/bolsas` | path: `id`; query: `status`, `tipoHemocomponente`, `grupoSanguineo`, `page`, `size` | — | `Page<BolsaResumo>` | 200, 404 |
| GET | `/api/v1/hemocentros/{id}/rotas` | path: `id`; query: `status`, `page`, `size` | — | `Page<RotaResumo>` | 200, 404 |

### 2.2 Hospitais — `/api/v1/hospitais`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/hospitais` | query: `cidade`, `uf`, `page`, `size`, `sort` | — | `Page<HospitalResumo>` | 200 |
| GET | `/api/v1/hospitais/{id}` | path: `id` | — | `HospitalDetalhe` | 200, 404 |
| POST | `/api/v1/hospitais` | — | `HospitalDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HospitalDetalhe` | 201, 400, 401, 403 |
| PUT | `/api/v1/hospitais/{id}` | path: `id` | `HospitalDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HospitalDetalhe` | 200, 400, 404 |
| DELETE | `/api/v1/hospitais/{id}` | path: `id` | — | — | 204, 404, 409 |
| GET | `/api/v1/hospitais/{id}/requisicoes` | path: `id`; query: `status`, `page`, `size` | — | `Page<RequisicaoResumo>` | 200, 404 |

### 2.3 Bolsas — `/api/v1/bolsas`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/bolsas` | query: `status`, `tipoHemocomponente`, `grupoSanguineo`, `hemocentroId`, `page`, `size`, `sort` (padrão `dataValidade,asc`) | — | `Page<BolsaResumo>` | 200 |
| GET | `/api/v1/bolsas/{id}` | path: `id` | — | `BolsaDetalhe` | 200, 404 |
| POST | `/api/v1/bolsas` | — | `BolsaDetalhe` (sem `id`/`status`/`criadoEm`/`atualizadoEm`) | `BolsaDetalhe` | 201, 400, 401, 403 |
| PATCH | `/api/v1/bolsas/{id}` | path: `id` | `{ "status": "..." }` | `BolsaDetalhe` | 200, 400, 404, 409 |
| DELETE | `/api/v1/bolsas/{id}` | path: `id` | — | — | 204, 404, 409 |

### 2.4 Requisições — `/api/v1/requisicoes`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/requisicoes` | query: `status`, `hospitalId`, `prioridade`, `page`, `size`, `sort` | — | `Page<RequisicaoResumo>` | 200 |
| GET | `/api/v1/requisicoes/{id}` | path: `id` | — | `RequisicaoDetalhe` | 200, 404 |
| POST | `/api/v1/requisicoes` | — | `{ hospitalId, prioridade, observacao?, itens: [{ tipoHemocomponente, grupoSanguineo, quantidadeSolicitada }] }` | `RequisicaoDetalhe` | 201, 400, 401, 403 |
| PATCH | `/api/v1/requisicoes/{id}` | path: `id` | `{ "status": "CANCELADA" }` | `RequisicaoDetalhe` | 200, 400, 404, 409 |
| GET | `/api/v1/requisicoes/{id}/itens` | path: `id` | — | `List<ItemRequisicaoDetalhe>` | 200, 404 |
| POST | `/api/v1/requisicoes/{id}/itens` | path: `id` | `{ tipoHemocomponente, grupoSanguineo, quantidadeSolicitada }` | `ItemRequisicaoDetalhe` | 201, 400, 404, 409 |
| DELETE | `/api/v1/requisicoes/{id}/itens/{itemId}` | path: `id`, `itemId` | — | — | 204, 404, 409 |
| GET | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes` | path: `id`, `itemId` | — | `List<AlocacaoDetalhe>` | 200, 404 |
| POST | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes` | path: `id`, `itemId` | `{ "bolsaId": "..." }` | `AlocacaoDetalhe` | 201, 400, 404, 409 |
| DELETE | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes/{alocacaoId}` | path: `id`, `itemId`, `alocacaoId` | — | — | 204, 404, 409 |

### 2.5 Rotas — `/api/v1/rotas`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/rotas` | query: `status`, `hemocentroId`, `hospitalId`, `page`, `size`, `sort` | — | `Page<RotaResumo>` | 200 |
| GET | `/api/v1/rotas/{id}` | path: `id` | — | `RotaDetalhe` | 200, 404 |
| POST | `/api/v1/rotas` | — | `{ hemocentroOrigemId, hospitalDestinoId, previsaoSaida, previsaoChegada }` | `RotaDetalhe` | 201, 400, 401, 403 |
| PATCH | `/api/v1/rotas/{id}` | path: `id` | `{ "status": "EM_TRANSITO" }` (com `saidaReal`/`chegadaReal` quando aplicável) | `RotaDetalhe` | 200, 400, 404, 409 |
| GET | `/api/v1/rotas/{id}/bolsas` | path: `id` | — | `List<BolsaResumo>` | 200, 404 |
| POST | `/api/v1/rotas/{id}/bolsas` | path: `id` | `{ "bolsaId": "..." }` | `BolsaResumo` | 201, 400, 404, 409 |
| DELETE | `/api/v1/rotas/{id}/bolsas/{bolsaId}` | path: `id`, `bolsaId` | — | — | 204, 404, 409 |

### 2.6 Indicadores — `/api/v1/indicadores`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/indicadores/estoque` | query: `hemocentroId?`, `tipoHemocomponente?`, `grupoSanguineo?` | — | `EstoqueIndicador[]` | 200, 400 |
| GET | `/api/v1/indicadores/requisicoes` | query: `periodoInicio`, `periodoFim`, `hospitalId?` | — | `RequisicaoIndicador` | 200, 400 |
| GET | `/api/v1/indicadores/rotas` | query: `periodoInicio`, `periodoFim`, `hemocentroId?` | — | `RotaIndicador` | 200, 400 |

---

## 3. Exemplos completos de request/response

### 3.1 `POST /api/v1/bolsas` — registrar bolsa coletada

**Request**

```http
POST /api/v1/bolsas HTTP/1.1
Authorization: Bearer <token-hemocentro>
Content-Type: application/json

{
  "hemocentroId": "5b1e2a10-2c3d-4e5f-8a9b-0c1d2e3f4a5b",
  "tipoHemocomponente": "CONCENTRADO_HEMACIAS",
  "grupoSanguineo": "O-",
  "volumeMl": 350,
  "dataColeta": "2026-08-20",
  "dataValidade": "2026-09-24"
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/bolsas/9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d
Content-Type: application/json

{
  "id": "9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d",
  "hemocentroId": "5b1e2a10-2c3d-4e5f-8a9b-0c1d2e3f4a5b",
  "tipoHemocomponente": "CONCENTRADO_HEMACIAS",
  "grupoSanguineo": "O-",
  "volumeMl": 350,
  "dataColeta": "2026-08-20",
  "dataValidade": "2026-09-24",
  "status": "DISPONIVEL",
  "criadoEm": "2026-08-26T14:02:11Z",
  "atualizadoEm": "2026-08-26T14:02:11Z"
}
```

*(Dado sintético — bolsa, datas e IDs fictícios para fins didáticos.)*

---

### 3.2 `POST /api/v1/requisicoes` — hospital cria requisição com itens

**Request**

```http
POST /api/v1/requisicoes HTTP/1.1
Authorization: Bearer <token-hospital>
Content-Type: application/json

{
  "hospitalId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "prioridade": "URGENTE",
  "observacao": "Paciente em cirurgia eletiva — dado fictício de exemplo",
  "itens": [
    { "tipoHemocomponente": "CONCENTRADO_HEMACIAS", "grupoSanguineo": "O-", "quantidadeSolicitada": 2 },
    { "tipoHemocomponente": "PLASMA", "grupoSanguineo": "O-", "quantidadeSolicitada": 1 }
  ]
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/requisicoes/7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f
Content-Type: application/json

{
  "id": "7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f",
  "hospitalId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "prioridade": "URGENTE",
  "observacao": "Paciente em cirurgia eletiva — dado fictício de exemplo",
  "status": "ABERTA",
  "itens": [
    {
      "id": "aa11bb22-cc33-dd44-ee55-ff6677889900",
      "tipoHemocomponente": "CONCENTRADO_HEMACIAS",
      "grupoSanguineo": "O-",
      "quantidadeSolicitada": 2,
      "quantidadeAlocada": 0,
      "status": "PENDENTE"
    },
    {
      "id": "bb22cc33-dd44-ee55-ff66-778899001122",
      "tipoHemocomponente": "PLASMA",
      "grupoSanguineo": "O-",
      "quantidadeSolicitada": 1,
      "quantidadeAlocada": 0,
      "status": "PENDENTE"
    }
  ],
  "criadoEm": "2026-08-26T14:10:03Z",
  "atualizadoEm": "2026-08-26T14:10:03Z"
}
```

---

### 3.3 `POST /api/v1/requisicoes/{id}/itens/{itemId}/alocacoes` — hemocentro aloca bolsa a um item

**Request**

```http
POST /api/v1/requisicoes/7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f/itens/aa11bb22-cc33-dd44-ee55-ff6677889900/alocacoes HTTP/1.1
Authorization: Bearer <token-hemocentro>
Content-Type: application/json

{
  "bolsaId": "9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d"
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/requisicoes/7f8e9d0c.../itens/aa11bb22.../alocacoes/cc33dd44-ee55-ff66-7788-990011223344
Content-Type: application/json

{
  "id": "cc33dd44-ee55-ff66-7788-990011223344",
  "itemRequisicaoId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "bolsaId": "9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d",
  "alocadoEm": "2026-08-26T14:15:47Z"
}
```

Efeito colateral que vale documentar para os testes: a bolsa referenciada passa de `status: "DISPONIVEL"` para
`"ALOCADA"`, e o item de requisição passa a refletir `quantidadeAlocada: 1`, `status: "PARCIALMENTE_ALOCADO"`.

*(Todos os dados dos exemplos acima são sintéticos, para fins didáticos.)*
