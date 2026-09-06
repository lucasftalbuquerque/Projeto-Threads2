package com.rotavital.api;

import com.rotavital.servico.excecao.OperacaoInvalidaException;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduz excecao em resposta HTTP, em um lugar so.
 *
 * <p>Sem isso cada controlador precisaria de try/catch. Com {@code
 * @RestControllerAdvice} o Spring intercepta o que subiu de qualquer
 * controlador e devolve o status certo.</p>
 *
 * <p>O formato e o {@code ProblemDetail} da RFC 7807, padrao do Spring Boot 3+
 * para erro de API.</p>
 */
@RestControllerAdvice
public class ManipuladorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Recurso nao encontrado");
        return problema;
    }

    @ExceptionHandler(OperacaoInvalidaException.class)
    public ProblemDetail operacaoInvalida(OperacaoInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Operacao nao permitida");
        return problema;
    }

    /**
     * Disparada pelo {@code @Valid} quando o corpo da requisicao nao passa nas
     * anotacoes de validacao. Devolve campo a campo, para o front saber onde
     * marcar o erro.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail dadosInvalidos(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.put(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Ha campos invalidos na requisicao");
        problema.setTitle("Dados invalidos");
        problema.setProperty("campos", campos);
        return problema;
    }
}
