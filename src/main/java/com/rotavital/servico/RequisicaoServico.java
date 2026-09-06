package com.rotavital.servico;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.dominio.Alocacao;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RequisicaoServico {

    private final RequisicaoRepository requisicoes;
    private final BolsaRepository bolsas;
    private final HospitalServico hospitalServico;

    public RequisicaoServico(RequisicaoRepository requisicoes,
                             BolsaRepository bolsas,
                             HospitalServico hospitalServico) {
        this.requisicoes = requisicoes;
        this.bolsas = bolsas;
        this.hospitalServico = hospitalServico;
    }

    // -----------------------------------------------------------------------
    // Requisicao
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Requisicao> listar(StatusRequisicao status, String hospitalId,
                                   PrioridadeRequisicao prioridade) {
        return requisicoes.findAll().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> prioridade == null || r.getPrioridade() == prioridade)
                .filter(r -> hospitalId == null
                        || (r.getHospital() != null && hospitalId.equals(r.getHospital().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Requisicao> listarPorHospital(String hospitalId, StatusRequisicao status) {
        hospitalServico.buscar(hospitalId);
        return listar(status, hospitalId, null);
    }

    @Transactional(readOnly = true)
    public Requisicao buscar(String id) {
        return requisicoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Requisicao", id));
    }

    @Transactional
    public Requisicao criar(RequisicaoRequest dados) {
        Hospital hospital = hospitalServico.buscar(dados.hospitalId());

        Requisicao requisicao = new Requisicao(
                UUID.randomUUID().toString(),
                hospital,
                dados.prioridade(),
                LocalDateTime.now(),
                dados.prazoEntrega());
        requisicao.setObservacao(dados.observacao());

        for (ItemRequisicaoRequest item : dados.itens()) {
            requisicao.adicionarItem(novoItem(item));
        }

        return requisicoes.save(requisicao);
    }

    /**
     * O contrato so preve o cancelamento pelo PATCH. Os demais status sao
     * calculados a partir das alocacoes, entao aceitar qualquer valor aqui
     * deixaria o estado inconsistente com os itens.
     */
    @Transactional
    public Requisicao atualizarStatus(String id, StatusRequisicao novoStatus) {
        Requisicao requisicao = buscar(id);

        if (novoStatus != StatusRequisicao.CANCELADA) {
            throw new OperacaoInvalidaException(
                    "Somente o cancelamento pode ser feito manualmente. "
                    + "Os demais status derivam das alocacoes.");
        }
        if (requisicao.getStatus() == StatusRequisicao.CANCELADA) {
            return requisicao;
        }

        // O que impede o cancelamento nao e o status da requisicao, e o das
        // bolsas: estar ATENDIDA so significa que tudo foi reservado, e
        // reserva se desfaz. Depois que a bolsa embarca, nao.
        boolean bolsaJaSaiu = requisicao.getItens().stream()
                .flatMap(item -> item.getAlocacoes().stream())
                .anyMatch(a -> a.getBolsa().getStatus() != StatusBolsa.RESERVADA);

        if (bolsaJaSaiu) {
            throw new OperacaoInvalidaException(
                    "Ha bolsas ja despachadas para esta requisicao. Cancele a rota antes.");
        }

        // Libera as bolsas que estavam presas a esta requisicao.
        for (ItemRequisicao item : requisicao.getItens()) {
            for (Alocacao alocacao : List.copyOf(item.getAlocacoes())) {
                alocacao.getBolsa().setStatus(StatusBolsa.DISPONIVEL);
                item.removerAlocacao(alocacao);
            }
        }

        requisicao.setStatus(StatusRequisicao.CANCELADA);
        return requisicoes.save(requisicao);
    }

    // -----------------------------------------------------------------------
    // Itens
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ItemRequisicao> listarItens(String requisicaoId) {
        return buscar(requisicaoId).getItens();
    }

    @Transactional
    public ItemRequisicao adicionarItem(String requisicaoId, ItemRequisicaoRequest dados) {
        Requisicao requisicao = buscar(requisicaoId);
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = novoItem(dados);
        requisicao.adicionarItem(item);
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);
        return item;
    }

    @Transactional
    public void removerItem(String requisicaoId, String itemId) {
        Requisicao requisicao = buscar(requisicaoId);
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = itemDe(requisicao, itemId);
        if (item.getQuantidadeAlocada() > 0) {
            throw new OperacaoInvalidaException(
                    "Item possui bolsas alocadas. Desfaca as alocacoes antes de remover.");
        }

        requisicao.removerItem(item);
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);
    }

    // -----------------------------------------------------------------------
    // Alocacoes
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Alocacao> listarAlocacoes(String requisicaoId, String itemId) {
        return itemDe(buscar(requisicaoId), itemId).getAlocacoes();
    }

    /**
     * Vincula uma bolsa a um item. E o ponto onde estoque e demanda se
     * encontram, entao concentra as regras de compatibilidade: mesmo tipo,
     * mesmo grupo, bolsa disponivel e dentro da validade.
     */
    @Transactional
    public Alocacao alocar(String requisicaoId, String itemId, AlocacaoRequest dados) {
        Requisicao requisicao = buscar(requisicaoId);
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = itemDe(requisicao, itemId);
        if (item.estaAtendido()) {
            throw new OperacaoInvalidaException("Item ja esta totalmente atendido");
        }

        Bolsa bolsa = bolsas.findById(dados.bolsaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa", dados.bolsaId()));

        if (bolsa.getStatus() != StatusBolsa.DISPONIVEL) {
            throw new OperacaoInvalidaException(
                    "Bolsa nao esta disponivel. Status atual: " + bolsa.getStatus());
        }
        if (bolsa.estaVencida(LocalDate.now())) {
            throw new OperacaoInvalidaException(
                    "Bolsa vencida em " + bolsa.getDataValidade());
        }
        if (bolsa.getTipo() != item.getTipo()) {
            throw new OperacaoInvalidaException(
                    "Hemocomponente incompativel: item pede " + item.getTipo()
                    + " e a bolsa e " + bolsa.getTipo());
        }
        if (bolsa.getGrupoSanguineo() != item.getGrupoSanguineo()) {
            throw new OperacaoInvalidaException(
                    "Grupo sanguineo incompativel: item pede " + item.getGrupoSanguineo()
                    + " e a bolsa e " + bolsa.getGrupoSanguineo());
        }

        Alocacao alocacao = new Alocacao(
                UUID.randomUUID().toString(), bolsa, item, LocalDateTime.now());

        item.adicionarAlocacao(alocacao);
        bolsa.setStatus(StatusBolsa.RESERVADA);
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);

        return alocacao;
    }

    @Transactional
    public void desfazerAlocacao(String requisicaoId, String itemId, String alocacaoId) {
        Requisicao requisicao = buscar(requisicaoId);
        ItemRequisicao item = itemDe(requisicao, itemId);

        Alocacao alocacao = item.getAlocacoes().stream()
                .filter(a -> a.getId().equals(alocacaoId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao", alocacaoId));

        if (alocacao.getBolsa().getStatus() != StatusBolsa.RESERVADA) {
            throw new OperacaoInvalidaException(
                    "A bolsa ja saiu para entrega e a alocacao nao pode ser desfeita. "
                    + "Status atual: " + alocacao.getBolsa().getStatus());
        }

        alocacao.getBolsa().setStatus(StatusBolsa.DISPONIVEL);
        item.removerAlocacao(alocacao);
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private ItemRequisicao novoItem(ItemRequisicaoRequest dados) {
        return new ItemRequisicao(
                UUID.randomUUID().toString(),
                dados.tipoHemocomponente(),
                dados.grupoSanguineo(),
                dados.quantidadeSolicitada());
    }

    private ItemRequisicao itemDe(Requisicao requisicao, String itemId) {
        return requisicao.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("ItemRequisicao", itemId));
    }

    private void exigirRequisicaoAberta(Requisicao requisicao) {
        if (requisicao.getStatus() == StatusRequisicao.CANCELADA) {
            throw new OperacaoInvalidaException("Requisicao cancelada nao aceita alteracoes");
        }
    }
}
