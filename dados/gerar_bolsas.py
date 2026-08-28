"""
Gera as bolsas sinteticas de hemocomponentes.

A ideia central: nao decidimos quantas bolsas estao vencidas. Espalhamos as
datas de coleta numa janela de 60 dias e deixamos a validade de cada
componente decidir. A taxa de descarte emerge dos dados em vez de ser um
numero que escolhemos.

Dados sinteticos. Nenhum dado real de doador (LGPD).
"""

import random
from datetime import date, timedelta

from premissas import (
    COMPONENTES,
    DISTRIBUICAO_TIPO_SANGUINEO,
    JANELA_COLETA_DIAS,
    QTD_BOLSAS,
    SEMENTE,
)
from unidades import UNIDADES

# Todas as unidades desta rede armazenam estoque: hemocentros por definicao
# e agencias transfusionais porque tem banco de sangue proprio. A diferenca
# esta em quem requisita (ver gerar_requisicoes.py): hemocentro so atende,
# agencia tambem pede quando falta um tipo especifico.
UNIDADES_COM_ESTOQUE = [
    u for u in UNIDADES if u["tipo"] in ("HEMOCENTRO", "AGENCIA_TRANSFUSIONAL")
]


def sortear_por_distribuicao(distribuicao):
    """Sorteia uma chave respeitando os pesos declarados nas premissas."""
    chaves = list(distribuicao.keys())
    pesos = list(distribuicao.values())
    return random.choices(chaves, weights=pesos, k=1)[0]


def gerar_bolsas(data_referencia=None):
    """
    Produz a lista de bolsas.

    Cada bolsa recebe:
      - tipo sanguineo sorteado pela distribuicao populacional brasileira
      - componente sorteado pela proporcao tipica de estoque
      - data de coleta uniforme nos ultimos JANELA_COLETA_DIAS
      - validade calculada a partir do componente, como faz o dominio
      - volume dentro da faixa do componente
    """
    random.seed(SEMENTE)
    if data_referencia is None:
        data_referencia = date.today()

    distribuicao_componentes = {
        nome: dados["proporcao"] for nome, dados in COMPONENTES.items()
    }

    bolsas = []
    for i in range(1, QTD_BOLSAS + 1):
        componente = sortear_por_distribuicao(distribuicao_componentes)
        dados_componente = COMPONENTES[componente]

        dias_atras = random.randint(0, JANELA_COLETA_DIAS)
        data_coleta = data_referencia - timedelta(days=dias_atras)
        data_validade = data_coleta + timedelta(days=dados_componente["validade_dias"])

        volume_min, volume_max = dados_componente["volume_ml"]
        unidade = random.choice(UNIDADES_COM_ESTOQUE)

        bolsas.append({
            "codigo": "BOL%05d" % i,
            "tipo_sanguineo": sortear_por_distribuicao(DISTRIBUICAO_TIPO_SANGUINEO),
            "componente": componente,
            "volume_ml": random.randint(volume_min, volume_max),
            "data_coleta": data_coleta,
            "data_validade": data_validade,
            "unidade_id": unidade["id"],
            "dias_ate_vencer": (data_validade - data_referencia).days,
            "vencida": data_validade < data_referencia,
        })

    return bolsas


if __name__ == "__main__":
    bolsas = gerar_bolsas()
    print("bolsas geradas: %d" % len(bolsas))
    print("vencidas      : %d" % sum(1 for b in bolsas if b["vencida"]))
    print()
    print("exemplo:")
    for b in bolsas[:3]:
        print("  %s | %-24s | %-6s | vence em %4d dias" % (
            b["codigo"], b["componente"], b["tipo_sanguineo"], b["dias_ate_vencer"]))
