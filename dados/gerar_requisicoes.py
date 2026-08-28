"""
Gera as requisicoes sinteticas dos hospitais.

Quem requisita: as agencias transfusionais. Elas tem banco de sangue proprio
(por isso tambem estocam, ver gerar_bolsas.py), mas quando falta um tipo
especifico recorrem aos hemocentros. Os hemocentros so enviam.

A semente e SEMENTE + 1: usar a mesma do gerador de bolsas repetiria a
sequencia de sorteios e criaria uma correlacao artificial entre o que existe
em estoque e o que e pedido.

Dados sinteticos. Nenhum dado real de paciente (LGPD).
"""

import random
from datetime import date, timedelta

from gerar_bolsas import sortear_por_distribuicao
from premissas import (
    COMPONENTES,
    DISTRIBUICAO_PRIORIDADE,
    DISTRIBUICAO_TIPO_SANGUINEO,
    JANELA_COLETA_DIAS,
    QTD_REQUISICOES,
    SEMENTE,
)
from unidades import UNIDADES

# Agencias transfusionais requisitam. Hemocentros apenas atendem.
UNIDADES_REQUISITANTES = [
    u for u in UNIDADES if u["tipo"] == "AGENCIA_TRANSFUSIONAL"
]

# Prazo de atendimento esperado por prioridade, em horas. Usado depois para
# avaliar se a roteirizacao consegue cumprir a janela de tempo.
PRAZO_HORAS = {
    "EMERGENCIA": 2,
    "URGENTE": 12,
    "ROTINA": 72,
}


def gerar_requisicoes(data_referencia=None):
    random.seed(SEMENTE + 1)
    if data_referencia is None:
        data_referencia = date.today()

    distribuicao_componentes = {
        nome: dados["proporcao"] for nome, dados in COMPONENTES.items()
    }

    requisicoes = []
    for i in range(1, QTD_REQUISICOES + 1):
        unidade = random.choice(UNIDADES_REQUISITANTES)
        prioridade = sortear_por_distribuicao(DISTRIBUICAO_PRIORIDADE)
        data_req = data_referencia - timedelta(
            days=random.randint(0, JANELA_COLETA_DIAS)
        )

        # Emergencia tende a pedir menos itens e mais volume de um so tipo;
        # rotina costuma reunir varios componentes num pedido programado.
        qtd_itens = 1 if prioridade == "EMERGENCIA" else random.randint(1, 3)

        itens = []
        for j in range(qtd_itens):
            itens.append({
                "id": "ITEM%05d-%d" % (i, j + 1),
                "tipo_sanguineo": sortear_por_distribuicao(DISTRIBUICAO_TIPO_SANGUINEO),
                "componente": sortear_por_distribuicao(distribuicao_componentes),
                "quantidade": random.randint(1, 4),
            })

        requisicoes.append({
            "id": "REQ%05d" % i,
            "unidade_id": unidade["id"],
            "cidade": unidade["cidade"],
            "prioridade": prioridade,
            "data": data_req,
            "prazo_horas": PRAZO_HORAS[prioridade],
            "itens": itens,
            "total_bolsas": sum(item["quantidade"] for item in itens),
        })

    return requisicoes


if __name__ == "__main__":
    reqs = gerar_requisicoes()
    print("requisicoes geradas: %d" % len(reqs))
    print("bolsas demandadas  : %d" % sum(r["total_bolsas"] for r in reqs))
    print()
    for p in ("EMERGENCIA", "URGENTE", "ROTINA"):
        qtd = sum(1 for r in reqs if r["prioridade"] == p)
        print("  %-11s %3d requisicoes (%.1f%%)" % (p, qtd, 100 * qtd / len(reqs)))
