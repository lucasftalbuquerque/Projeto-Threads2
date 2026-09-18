# Interpretação dos Indicadores Operacionais e Estatísticos (W06 - EST)

## 1. Contexto e Objetivo

Este documento consolida a portabilidade e a interpretação escrita dos cálculos de `dados/analise.py` para a aplicação Java (sob o contrato `/api/v1/indicadores/*`), conforme os critérios da história **W06-Indicadores (EST)**.

Todos os indicadores são computados dinamicamente a partir dos dados persistidos no banco de dados e refletem a massa sintética de referência composta por:
- **2.000 bolsas** coletadas em uma janela de 60 dias;
- **300 requisições hospitalares** totalizando **1.487 bolsas solicitadas**;
- Rede de distribuição localizada na Região Metropolitana do Recife (Recife, Olinda e Jaboatão dos Guararapes).

---

## 2. Metodologia, Fórmulas e Unidades dos Indicadores

### 2.1. Distribuição de Estoque por Tipo Sanguíneo e por Componente

* **Endpoint:** `GET /api/v1/indicadores/estoque-por-tipo` e `GET /api/v1/indicadores/estoque-por-componente`
* **Unidade:** Quantidade absoluta de bolsas ($un$) e percentual sobre o estoque total ($\%$)
* **Fórmula:**
  $$\text{Percentual}(c) = \frac{\text{Quantidade}(c)}{N_{\text{total}}} \times 100$$
  * Onde $N_{\text{total}} = 2.000$ bolsas e $c$ representa o grupo sanguíneo ou tipo de hemocomponente.

### 2.2. Taxa de Descarte por Vencimento por Componente

* **Endpoint:** `GET /api/v1/indicadores/descarte`
* **Unidade:** Taxa percentual ($\%$)
* **Fórmula:**
  $$\text{TaxaDescarte}(comp) = \frac{\text{Bolsas Vencidas}(comp)}{\text{Total Coletado}(comp)} \times 100$$
  * Critério de vencimento: $\text{dataValidade} < \text{dataReferencia}$ (ou $\text{diasAteVencer} < 0$).

### 2.3. Medidas de Tendência Central e Dispersão da Validade

* **Endpoint:** `GET /api/v1/indicadores/validade`
* **Unidade:** Dias ($dias$)
* **População considerada:** Apenas bolsas válidas ($\text{dataValidade} \ge \text{dataReferencia}$), totalizando $N = 1.345$ bolsas.
* **Fórmulas:**
  * **Média Aritmética ($\bar{x}$):**
    $$\bar{x} = \frac{1}{n} \sum_{i=1}^{n} x_i$$
  * **Mediana ($\tilde{x}$):** Valor central da distribuição ordenada de dias restantes:
    $$\tilde{x} = x_{(n+1)/2} \quad \text{(para } n \text{ ímpar)}, \quad \tilde{x} = \frac{x_{(n/2)} + x_{(n/2 + 1)}}{2} \quad \text{(para } n \text{ par)}$$
  * **Desvio-Padrão Amostral ($s$):**
    $$s = \sqrt{\frac{1}{n - 1} \sum_{i=1}^{n} (x_i - \bar{x})^2}$$
    *(Utilizado $n - 1$ para garantir consistência com o `statistics.stdev` do Python e correção de Bessel para amostras).*
  * **Faixa:** Valores $\min(x)$ e $\max(x)$.

### 2.4. Bolsas Próximas do Vencimento (Estoque Crítico / Alvo FEFO)

* **Endpoint:** `GET /api/v1/indicadores/criticas?limiteDias=7`
* **Unidade:** Quantidade de bolsas ($un$) e percentual sobre o estoque válido ($\%$)
* **Fórmula:**
  $$\text{Estoque Crítico} = \{ b \in \text{Bolsas Válidas} \mid 0 \le \text{diasAteVencer}(b) \le \text{limiteDias} \}$$
  $$\text{Percentual Crítico} = \frac{|\text{Estoque Crítico}|}{N_{\text{válidas}}} \times 100$$

### 2.5. Cobertura de Demanda por Tipo Sanguíneo

* **Endpoint:** `GET /api/v1/indicadores/cobertura`
* **Unidade:** Cobertura percentual ($\%$) e Déficit ($un$)
* **Fórmula:**
  $$\text{Cobertura}(g) = \frac{\text{Estoque Disponível Válido}(g)}{\text{Demanda Total Solicitada}(g)} \times 100$$
  $$\text{Déficit}(g) = \max(0, \text{Demanda Total Solicitada}(g) - \text{Estoque Disponível Válido}(g))$$
  * Onde $g \in \{ \text{A+}, \text{A-}, \text{B+}, \text{B-}, \text{AB+}, \text{AB-}, \text{O+}, \text{O-} \}$.

---

## 3. Apresentação e Interpretação dos Resultados

### 3.1. Distribuição de Estoque e Composição da Rede

Na base sintética de 2.000 bolsas, o estoque apresenta a seguinte proporção populacional:
* **O+:** 747 bolsas (37,4%)
* **A+:** 652 bolsas (32,6%)
* **O-:** 184 bolsas (9,2%)
* **A-:** 160 bolsas (8,0%)
* **B+:** 153 bolsas (7,6%)
* **AB+:** 50 bolsas (2,5%)
* **B-:** 47 bolsas (2,4%)
* **AB-:** 7 bolsas (0,4%)

Em relação aos componentes:
* **Concentrado de Hemácias:** 1.128 bolsas (56,4%)
* **Concentrado de Plaquetas:** 407 bolsas (20,4%)
* **Plasma Fresco Congelado:** 369 bolsas (18,4%)
* **Crioprecipitado:** 96 bolsas (4,8%)

**Interpretação:** A composição reflete diretamente as prevalências genéticas da população brasileira (predomínio de O+ e A+, escassez extrema de AB- e B-). Essa distribuição impõe desafios logísticos severos: tipos raros precisam de alocação estratégica imediata, pois a chance de recomposição rápida do estoque é baixa.

---

### 3.2. Análise de Descarte por Vencimento

* **Geral:** Das 2.000 bolsas, **655 venceram**, resultando em uma taxa global de descarte de **32,8%**.
* **Por Componente:**
  * **Concentrado de Plaquetas:** 407 total, 367 vencidas $\rightarrow$ **90,2% de descarte**.
  * **Concentrado de Hemácias:** 1.128 total, 288 vencidas $\rightarrow$ **25,5% de descarte**.
  * **Plasma Fresco Congelado:** 369 total, 0 vencidas $\rightarrow$ **0,0% de descarte**.
  * **Crioprecipitado:** 96 total, 0 vencidas $\rightarrow$ **0,0% de descarte**.

**Interpretação:**
1. O descarte na hemorrede não é homogêneo, concentrando-se quase integralmente em componentes de curta vida útil.
2. O **Concentrado de Plaquetas (validade de 5 dias)** possui uma taxa de perda crítica de 90,2%. Em um cenário de coleta contínua em janela de 60 dias sem política ativa de distribuição, as plaquetas vencem antes de serem requisitadas.
3. Isso evidencia a urgência da estratégia **FEFO (First Expire, First Out)** e do despacho dinâmico prioritário de plaquetas para hospitais com maior rotatividade cirúrgica e oncológica.
4. Hemácias (42 dias) sofrem descarte moderado (25,5%), enquanto Plasma e Crio (365 dias) não apresentam perda nessa janela.

---

### 3.3. Medidas de Tendência Central e Dispersão da Validade

Considerando as **1.345 bolsas válidas**:
* **Geral:**
  * Média: **129,2 dias**
  * Mediana: **32,0 dias**
  * Desvio-Padrão Amostral: **150,7 dias**
  * Amplitude (Mín - Máx): **0 a 365 dias**
* **Por Componente:**
  * **Concentrado de Plaquetas ($n = 40$):** Média: 2,3 dias | Mediana: 3,0 dias | Desvio: 1,7 dias
  * **Concentrado de Hemácias ($n = 840$):** Média: 21,0 dias | Mediana: 21,0 dias | Desvio: 12,3 dias
  * **Plasma Fresco Congelado ($n = 369$):** Média: 335,1 dias | Mediana: 335,0 dias | Desvio: 17,4 dias
  * **Crioprecipitado ($n = 96$):** Média: 336,5 dias | Mediana: 337,5 dias | Desvio: 18,1 dias

**Interpretação Estatística:**
1. **Assimetria e Distorção da Média Global:** A média geral (129,2 dias) é mais de **4 vezes superior** à mediana (32,0 dias), com um desvio-padrão altíssimo (150,7 dias). Se a gestão hospitalar avaliasse o estoque apenas pela média, acreditaria falsamente que a rede dispõe de mais de 4 meses de suprimento seguro.
2. A distribuição é bimodal/multimodal e fortemente assimétrica à direita devido à convivência de produtos com prazos de validade em ordens de grandeza discrepantes (5 dias para plaquetas vs. 365 dias para plasma e crioprecipitado).
3. **Comportamento por Componente:** Ao estratificar por componente, a média e a mediana convergem perfeitamente (Hemácias: 21,0 dias média e mediana; Plasma: 335,1 média vs 335,0 mediana). Isso prova que a dispersão global é fruto da mistura de componentes e valida a necessidade técnica de analisar a validade separadamente por hemocomponente.

---

### 3.4. Bolsas Próximas do Vencimento (Janela de 7 Dias)

* **Quantidade de bolsas críticas ($\le 7$ dias restantes):** **187 bolsas**
* **Proporção do estoque válido:** **13,9%**

**Interpretação:**
Quase 14% de todo o estoque útil da rede vence em até uma semana. Essas 187 bolsas são o **alvo prioritário absoluto do algoritmo de despacho FEFO**. Caso não sejam alocadas e transportadas imediatamente para atender às requisições pendentes, transformar-se-ão em descarte evitável nos próximos 7 dias.

---

### 3.5. Cobertura de Demanda e Déficit por Tipo Sanguíneo

Comparativo entre o estoque válido disponível ($1.345$ bolsas) e a demanda acumulada das requisições ($1.487$ bolsas solicitadas):

| Tipo Sanguíneo | Disponível | Demandado | Cobertura (%) | Déficit (un) | Situação |
|:---:|:---:|:---:|:---:|:---:|:---:|
| **AB-** | 5 | 10 | 50,0% | 5 | Déficit crítico |
| **AB+** | 30 | 44 | 68,2% | 14 | Déficit |
| **A+** | 454 | 549 | 82,7% | 95 | Déficit absoluto severo |
| **B+** | 105 | 118 | 89,0% | 13 | Déficit |
| **A-** | 110 | 122 | 90,2% | 12 | Déficit |
| **O+** | 492 | 518 | 95,0% | 26 | Déficit |
| **B-** | 30 | 29 | 103,4% | 0 | Cobertura total |
| **O-** | 119 | 97 | 122,7% | 0 | Cobertura total (Superávit) |
| **TOTAL** | **1.345** | **1.487** | **90,5%** | **165** | **Déficit Líquido Global** |

**Interpretação:**
1. **Déficit Sistêmico:** Globalmente, a rede atende a 90,5% da demanda solicitada, restando um déficit não atendido de 165 bolsas se considerada apenas compatibilidade idêntica (isogrupo).
2. **Gargalo nos tipos AB e A+:**
   - O tipo **AB-** possui a menor cobertura percentual (50,0%), reflexo de sua raridade.
   - O tipo **A+** concentra o maior déficit em volume absoluto (**95 bolsas em falta**). Apesar de representar 32,6% das doações, a demanda hospitalar consumiu mais do que o estoque válido acumulado.
3. **Papel Estratégico do O- (Doador Universal):** O tipo O- apresenta cobertura de **122,7%** (superávit de 22 bolsas). Na prática hemoterápica, esse excedente de O- é o recurso vital que o motor de alocação utilizará para mitigar os déficits dos tipos com cobertura abaixo de 100% (como A-, B- e até tipos positivos em emergências).

---

## 4. Conclusão Operacional

A portabilidade dos indicadores de `dados/analise.py` para a aplicação Java fornece a base empírica e analítica necessária para a tomada de decisão no Rota Vital:

1. **Priorização FEFO:** Justificada pelo volume de 187 bolsas críticas a vencer em 7 dias e pela taxa de descarte de 90,2% em plaquetas.
2. **Matriz de Compatibilidade:** Justificada pelo déficit de 165 bolsas em tipos específicos (A+, AB-, AB+, O+), demonstrando que a alocação não pode depender apenas de equivalência exata de tipo sanguíneo.
3. **Métricas de Dispersão no Dashboard:** A divergência brutal entre média (129 dias) e mediana (32 dias) comprova a obrigatoriedade de apresentar medidas de dispersão e valores desagregados por componente para a equipe médica e de suprimentos.
