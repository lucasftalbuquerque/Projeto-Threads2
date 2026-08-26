# Documento de escopo — Modelagem do grafo da rede de distribuição de hemocomponentes

## Contexto do projeto

A rede de distribuição de sangue precisa entregar o componente certo (compatível e dentro da validade), no lugar certo, no tempo certo e na temperatura certa. Este documento define a estrutura de grafo sobre a qual o cálculo de rota vai rodar, para que a plataforma possa alocar bolsas e planejar entregas entre hemocentros e hospitais.

## Objetivo

Definir como a rede de distribuição de hemocomponentes vira um grafo, para que o cálculo de rota tenha uma estrutura sobre a qual rodar.

## Decisões

### 1. Vértice

**Definição:** unidade da rede (hemocentro, banco de sangue regional ou hospital/ponto de entrega).

Cada unidade física da operação é representada por exatamente um vértice. A representação interna da unidade (atributos, identificadores) é tratada em PI3-12; este documento assume que cada unidade já chega com um identificador único a ser usado como chave do vértice.

### 2. Aresta

**Definição:** trecho viário direto entre duas unidades — um caminho que não passa por nenhuma outra unidade da rede no meio do percurso.

Se existe uma rota física entre a unidade A e a unidade B que passa por uma terceira unidade C, isso é modelado como duas arestas (A–C e C–B), não uma aresta única A–B.

### 3. Peso da aresta

**Proposta: tempo estimado de percurso (em minutos).**

Justificativa:
- Hemocomponentes têm validade curta e exigem manutenção de cadeia fria — o que importa minimizar é o tempo até a entrega, não a distância percorrida. Tempo é a métrica que conecta diretamente o cálculo de rota ao risco de descarte por vencimento e ao risco ao paciente.
- Distância pura é um proxy fraco: dois trechos com a mesma distância podem ter tempos de percurso bem diferentes dependendo do tipo de via, trânsito ou restrições para veículos.
- Custo (combustível, pedágio) é uma dimensão de otimização distinta de tempo. Combinar tempo e custo em um único peso escalar exige normalizar unidades diferentes de forma arbitrária, o que enfraquece a justificativa da rota escolhida.
- **Recomendação:** registrar custo como um atributo adicional da aresta (não como peso), permitindo no futuro uma função de otimização multi-objetivo, sem misturar as métricas nesta sprint.

> O peso é o que o algoritmo minimiza. Escolher distância ou tempo muda qual rota o sistema considera melhor — por isso a justificativa importa mais que a escolha, especialmente quando o que está em jogo é a validade de uma bolsa de sangue.

### 4. Grafo dirigido ou não dirigido

**Decisão: dirigido (digrafo).**

Justificativa: o tempo (e o custo) de ida e de volta entre duas unidades não é necessariamente simétrico — vias de mão única, sentido de tráfego, restrições de horário, ou retorno vazio vs. carregado. Um grafo não dirigido assumiria uma simetria que não existe na prática operacional.

### 5. Representação escolhida

**Decisão: lista de adjacência.**

Justificativa pela densidade esperada: a rede de hemocentros e hospitais é esparsa — cada unidade se conecta diretamente a poucas outras unidades vizinhas geograficamente, não a todas as unidades da rede. Para grafos esparsos, a lista de adjacência ocupa O(V + E) de espaço, contra O(V²) da matriz de adjacência. Além disso, o algoritmo de caminho mínimo escolhido (Dijkstra com fila de prioridade) itera sobre os vizinhos diretos de cada vértice, o que a lista de adjacência favorece naturalmente. Isso também está alinhado com a complexidade controlada definida para o projeto (poucos componentes, grafo limitado).

### 6. Algoritmo de caminho mínimo

**Decisão: Dijkstra, com fila de prioridade (heap binário).**

Justificativa: os pesos das arestas (tempo estimado) são sempre não negativos — condição necessária e suficiente para a corretude do Dijkstra. Com heap binário, a complexidade é O((V + E) log V), adequada ao tamanho esperado da rede. Não há necessidade de Bellman-Ford, que resolve o caso de pesos negativos, inexistente neste domínio.

### 7. Janelas de tempo e cadeia fria

**Decisão: registrar como fora de escopo desta sprint, dependência crítica para W06.**

Justificativa: o Dijkstra clássico assume pesos estáticos por aresta. Janela de tempo (chegar dentro de um intervalo) e cadeia fria (não exceder tempo máximo fora de refrigeração) são restrições de viabilidade sobre o caminho — uma camada de filtragem ou penalização aplicada depois (ou durante) o cálculo do caminho mínimo, não um peso simples de aresta. Incorporar isso agora ampliaria o escopo desta sprint sem necessidade.

Vale registrar que, dado o domínio (hemocomponentes), essa não é uma restrição secundária: cadeia fria rompida ou janela de tempo perdida pode inviabilizar a bolsa mesmo que o caminho seja o mais rápido em tempo puro. A modelagem completa dessas restrições (provavelmente como validação pós-Dijkstra ou como penalização de custo) fica marcada como dependência prioritária para a W06, já que se conecta diretamente com a competência de arquitetura de redes e telemetria (monitoramento de temperatura) do projeto.

## Esboço da rede — exemplo pequeno (5 unidades)

Grafo dirigido de exemplo com 5 vértices (Hemocentro, Hospital A, Hospital B, Hospital C, Hospital Destino) e pesos em tempo estimado (minutos):

| Origem | Destino | Peso (min) |
|--------|---------|------------|
| Hemocentro | Hospital A | 10 |
| Hemocentro | Hospital B | 20 |
| Hospital A | Hospital B | 5 |
| Hospital A | Hospital C | 15 |
| Hospital B | Hospital Destino | 8 |
| Hospital C | Hospital Destino | 5 |

### Caminho mínimo calculado à mão (Dijkstra a partir do Hemocentro)

- **Hemocentro → Hospital A → Hospital B → Hospital Destino = 10 + 5 + 8 = 23** ✅ (caminho mínimo)
- Hemocentro → Hospital B → Hospital Destino = 20 + 8 = 28
- Hemocentro → Hospital A → Hospital C → Hospital Destino = 10 + 15 + 5 = 30

O caminho de menor custo passa por Hospital A e Hospital B mesmo parecendo um desvio geométrico, porque o peso é tempo, não distância — o que reforça por que a escolha do peso (seção 3) é a decisão mais crítica deste documento, sobretudo com hemocomponentes envolvidos.

## Critérios de aceite

- [x] Vértice, aresta e peso definidos sem ambiguidade
- [x] Escolha entre lista e matriz justificada pela densidade esperada
- [x] Algoritmo escolhido e justificado
- [x] Exemplo pequeno (5 unidades) com caminho calculado a mão

## Depende de

- PI3-12 — representação da unidade da rede

## Fora de escopo

- Implementação do algoritmo (PI3-17 e PI3-19)
