"""
Analise descritiva dos dados sinteticos do Rota Vital.

Responde as perguntas da fase de entendimento dos dados (CRISP-DM):
  1. Como esta distribuido o estoque?
  2. Quanto do estoque esta vencido, e por que?
  3. O estoque disponivel cobre a demanda de cada tipo sanguineo?
  4. Como estoque e demanda se distribuem geograficamente?
"""

import statistics
from collections import defaultdict

from gerar_bolsas import gerar_bolsas
from gerar_requisicoes import gerar_requisicoes
from unidades import UNIDADES

# Mapa id -> unidade, para cruzar bolsa/requisicao com cidade.
UNIDADE_POR_ID = {u["id"]: u for u in UNIDADES}


# ---------------------------------------------------------------------------
# 1. Distribuicao do estoque
# ---------------------------------------------------------------------------

def contar_por(itens, chave):
    """
    Conta quantos itens existem para cada valor da chave.

    Exemplo: contar_por(bolsas, "tipo_sanguineo") devolve
    {"O_POS": 290, "A_POS": 274, ...}
    """
    contagem = defaultdict(int)
    for item in itens:
        contagem[item[chave]] += 1
    return dict(contagem)


def estoque_por_tipo_sanguineo(bolsas):
    return contar_por(bolsas, "tipo_sanguineo")


def estoque_por_componente(bolsas):
    return contar_por(bolsas, "componente")


# ---------------------------------------------------------------------------
# 2. Descarte por vencimento
# ---------------------------------------------------------------------------

def taxa_descarte_por_componente(bolsas):
    """
    Para cada componente: total, quantas venceram e a taxa.

    Devolve {"CONCENTRADO_PLAQUETAS": {"total": 158, "vencidas": 139,
                                        "taxa": 88.0}, ...}
    """
    resultado = {}
    for componente in set(b["componente"] for b in bolsas):
        do_componente = [b for b in bolsas if b["componente"] == componente]
        vencidas = [b for b in do_componente if b["vencida"]]
        resultado[componente] = {
            "total": len(do_componente),
            "vencidas": len(vencidas),
            "taxa": 100 * len(vencidas) / len(do_componente),
        }
    return resultado


# ---------------------------------------------------------------------------
# 3. Medidas de tendencia central e dispersao
# ---------------------------------------------------------------------------
def dispersao_validade(bolsas):
    """
    Media, mediana e desvio padrao dos dias restantes ate o vencimento,
    considerando apenas as bolsas ainda validas.

    A distancia entre media e mediana e informativa: como plasma e
    crioprecipitado duram 365 dias e hemacias 42, a distribuicao e
    fortemente assimetrica e a media sozinha enganaria.
    """
    dias = [b["dias_ate_vencer"] for b in bolsas if not b["vencida"]]
    return {
        "n": len(dias),
        "media": statistics.mean(dias),
        "mediana": statistics.median(dias),
        "desvio": statistics.stdev(dias),
        "minimo": min(dias),
        "maximo": max(dias),
    }


def dispersao_validade_por_componente(bolsas):
    """Mesmas medidas, separadas por componente."""
    resultado = {}
    for componente in set(b["componente"] for b in bolsas):
        dias = [b["dias_ate_vencer"] for b in bolsas
                if b["componente"] == componente and not b["vencida"]]
        if len(dias) < 2:
            continue
        resultado[componente] = {
            "n": len(dias),
            "media": statistics.mean(dias),
            "mediana": statistics.median(dias),
            "desvio": statistics.stdev(dias),
        }
    return resultado


def bolsas_proximas_do_vencimento(bolsas, limite_dias=7):
    """Bolsas validas que vencem dentro do limite. Alvo prioritario do FEFO."""
    return [b for b in bolsas
            if not b["vencida"] and b["dias_ate_vencer"] <= limite_dias]


# ---------------------------------------------------------------------------
# 4. Cobertura de demanda
# ---------------------------------------------------------------------------
def estoque_disponivel_por_tipo(bolsas):
    """Bolsas ainda validas, agrupadas por tipo sanguineo."""
    disponivel = defaultdict(int)
    for bolsa in bolsas:
        if not bolsa["vencida"]:
            disponivel[bolsa["tipo_sanguineo"]] += 1
    return dict(disponivel)


def demanda_por_tipo(requisicoes):
    """
    Bolsas pedidas por tipo sanguineo.

    Laco duplo: cada requisicao tem varios itens, e o que interessa e a
    quantidade de cada item, nao a contagem de requisicoes.
    """
    demanda = defaultdict(int)
    for requisicao in requisicoes:
        for item in requisicao["itens"]:
            demanda[item["tipo_sanguineo"]] += item["quantidade"]
    return dict(demanda)


def cobertura_por_tipo(bolsas, requisicoes):
    """
    Razao entre o que existe disponivel e o que foi pedido, por tipo.

    Cobertura abaixo de 100% significa que o estoque valido nao daria conta
    da demanda acumulada, mesmo ignorando compatibilidade e logistica.
    """
    disponivel = estoque_disponivel_por_tipo(bolsas)
    demanda = demanda_por_tipo(requisicoes)

    resultado = {}
    for tipo in set(list(disponivel.keys()) + list(demanda.keys())):
        tem = disponivel.get(tipo, 0)
        pedido = demanda.get(tipo, 0)
        resultado[tipo] = {
            "disponivel": tem,
            "demandado": pedido,
            "cobertura": (100 * tem / pedido) if pedido else None,
            "deficit": max(0, pedido - tem),
        }
    return resultado


# ---------------------------------------------------------------------------
# 5. Distribuicao geografica
# ---------------------------------------------------------------------------
def estoque_por_cidade(bolsas):
    """Bolsas validas por cidade, cruzando unidade_id com o cadastro."""
    por_cidade = defaultdict(int)
    for bolsa in bolsas:
        if not bolsa["vencida"]:
            cidade = UNIDADE_POR_ID[bolsa["unidade_id"]]["cidade"]
            por_cidade[cidade] += 1
    return dict(por_cidade)


def demanda_por_cidade(requisicoes):
    """Bolsas pedidas por cidade."""
    por_cidade = defaultdict(int)
    for requisicao in requisicoes:
        por_cidade[requisicao["cidade"]] += requisicao["total_bolsas"]
    return dict(por_cidade)


if __name__ == "__main__":
    bolsas = gerar_bolsas()
    requisicoes = gerar_requisicoes()

    print("=" * 60)
    print("ESTOQUE POR TIPO SANGUINEO")
    print("=" * 60)
    for tipo, qtd in sorted(estoque_por_tipo_sanguineo(bolsas).items(),
                            key=lambda x: -x[1]):
        print("  %-8s %4d  (%.1f%%)" % (tipo, qtd, 100 * qtd / len(bolsas)))

    print()
    print("=" * 60)
    print("DESCARTE POR COMPONENTE")
    print("=" * 60)
    dados = taxa_descarte_por_componente(bolsas)
    for comp, d in sorted(dados.items(), key=lambda x: -x[1]["taxa"]):
        print("  %-26s %4d total, %4d vencidas -> %5.1f%%" % (
            comp, d["total"], d["vencidas"], d["taxa"]))

    vencidas = sum(1 for b in bolsas if b["vencida"])
    print("  %-26s %4d total, %4d vencidas -> %5.1f%%" % (
        "GERAL", len(bolsas), vencidas, 100 * vencidas / len(bolsas)))

    print()
    print("=" * 60)
    print("DIAS ATE O VENCIMENTO (bolsas validas)")
    print("=" * 60)
    d = dispersao_validade(bolsas)
    print("  n = %d bolsas validas de %d" % (d["n"], len(bolsas)))
    print("  media   %7.1f dias" % d["media"])
    print("  mediana %7.1f dias" % d["mediana"])
    print("  desvio  %7.1f dias" % d["desvio"])
    print("  faixa   %d a %d dias" % (d["minimo"], d["maximo"]))
    print()
    print("  por componente:")
    for comp, e in sorted(dispersao_validade_por_componente(bolsas).items(),
                          key=lambda x: x[1]["media"]):
        print("    %-26s n=%3d  media %6.1f  mediana %6.1f  desvio %6.1f" % (
            comp, e["n"], e["media"], e["mediana"], e["desvio"]))

    criticas = bolsas_proximas_do_vencimento(bolsas, 7)
    print()
    print("  vencem em ate 7 dias: %d bolsas (%.1f%% do estoque valido)" % (
        len(criticas), 100 * len(criticas) / d["n"]))

    print()
    print("=" * 60)
    print("COBERTURA DE DEMANDA POR TIPO SANGUINEO")
    print("=" * 60)
    print("  %-8s %11s %10s %10s %9s" % (
        "tipo", "disponivel", "demandado", "cobertura", "deficit"))
    cob = cobertura_por_tipo(bolsas, requisicoes)
    for tipo, c in sorted(cob.items(), key=lambda x: x[1]["cobertura"] or 0):
        texto = "%.1f%%" % c["cobertura"] if c["cobertura"] is not None else "-"
        alerta = "  <-- deficit" if c["deficit"] > 0 else ""
        print("  %-8s %11d %10d %10s %9d%s" % (
            tipo, c["disponivel"], c["demandado"], texto, c["deficit"], alerta))

    total_disp = sum(c["disponivel"] for c in cob.values())
    total_dem = sum(c["demandado"] for c in cob.values())
    print()
    print("  total: %d disponiveis para %d demandadas -> %.1f%%" % (
        total_disp, total_dem, 100 * total_disp / total_dem))

    print()
    print("=" * 60)
    print("DISTRIBUICAO GEOGRAFICA")
    print("=" * 60)
    est = estoque_por_cidade(bolsas)
    dem = demanda_por_cidade(requisicoes)
    print("  %-28s %10s %10s %10s" % ("cidade", "estoque", "demanda", "saldo"))
    for cidade in sorted(set(list(est.keys()) + list(dem.keys()))):
        e = est.get(cidade, 0)
        m = dem.get(cidade, 0)
        print("  %-28s %10d %10d %+10d" % (cidade, e, m, e - m))
