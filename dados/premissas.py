"""
Premissas usadas na geracao dos dados sinteticos do Rota Vital.

Todo numero aqui e uma decisao que afeta a analise estatistica. Estao
reunidos num arquivo so para que as premissas fiquem explicitas e possam ser
questionadas, em vez de espalhadas pelo codigo.

IMPORTANTE: dados sinteticos, nenhum dado real de doador ou paciente (LGPD).
"""

# ---------------------------------------------------------------------------
# Distribuicao de tipos sanguineos
# ---------------------------------------------------------------------------
# Frequencias aproximadas na populacao brasileira. Valores de referencia
# arredondados; para a apresentacao, citar fonte (Ministerio da Saude /
# Hemobras) ou declarar que sao aproximacoes didaticas.
#
# Por que isso importa: sortear os 8 tipos com igual probabilidade (12,5%
# cada) produziria um estoque irreal e esconderia o problema central do
# dominio, que e a escassez de O- (doador universal, so 9% da populacao).
DISTRIBUICAO_TIPO_SANGUINEO = {
    "O_POS":  0.360,
    "A_POS":  0.340,
    "B_POS":  0.080,
    "O_NEG":  0.090,
    "A_NEG":  0.080,
    "AB_POS": 0.025,
    "B_NEG":  0.020,
    "AB_NEG": 0.005,
}

# ---------------------------------------------------------------------------
# Componentes: proporcao no estoque e validade
# ---------------------------------------------------------------------------
# A validade vem do dominio (TipoHemocomponente.java) e nao deve divergir.
# A proporcao reflete a pratica: hemacias e o componente mais coletado e
# transfundido; crioprecipitado e o mais raro.
COMPONENTES = {
    "CONCENTRADO_HEMACIAS":    {"proporcao": 0.55, "validade_dias": 42,  "volume_ml": (270, 330)},
    "CONCENTRADO_PLAQUETAS":   {"proporcao": 0.20, "validade_dias": 5,   "volume_ml": (50, 60)},
    "PLASMA_FRESCO_CONGELADO": {"proporcao": 0.20, "validade_dias": 365, "volume_ml": (200, 250)},
    "CRIOPRECIPITADO":         {"proporcao": 0.05, "validade_dias": 365, "volume_ml": (15, 20)},
}

# ---------------------------------------------------------------------------
# Prioridade das requisicoes
# ---------------------------------------------------------------------------
# A maioria das requisicoes e programada (rotina). Emergencia e minoria, mas
# e o caso que define o prazo mais apertado para a roteirizacao.
DISTRIBUICAO_PRIORIDADE = {
    "ROTINA":     0.65,
    "URGENTE":    0.28,
    "EMERGENCIA": 0.07,
}

# ---------------------------------------------------------------------------
# Volume da simulacao
# ---------------------------------------------------------------------------
# Janela de 60 dias para tras: garante bolsas em todos os estagios de
# validade (vencidas, proximas do vencimento e recentes) sem que precisemos
# forcar isso na mao. A taxa de descarte emerge da distribuicao das datas.
JANELA_COLETA_DIAS = 60
# 2000 bolsas para 12 unidades da rede (~166 por unidade), volume compativel
# com a demanda acumulada de 60 dias. Com oferta e demanda na mesma ordem de
# grandeza, a cobertura passa a variar por tipo sanguineo em vez de refletir
# apenas a razao entre os dois totais que escolhemos.
QTD_BOLSAS = 2000
QTD_REQUISICOES = 300

# Semente fixa: o mesmo comando gera sempre os mesmos dados, para que os
# numeros da apresentacao sejam reproduziveis por qualquer pessoa do time.
SEMENTE = 2026
