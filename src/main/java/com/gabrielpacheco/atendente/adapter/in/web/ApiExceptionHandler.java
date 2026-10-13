package com.gabrielpacheco.atendente.adapter.in.web;

import com.gabrielpacheco.atendente.adapter.in.http.ProblemResponses;
import com.gabrielpacheco.atendente.application.port.in.InvalidPhoneException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo erro da API sai em ProblemDetail com título em PT-BR (os do próprio Spring vêm do messages.properties).
 * Os dois casos de corpo inválido sobrescrevem os métodos da superclasse, que já trata essas exceções; um
 * {@code @ExceptionHandler} para elas tornaria o mapeamento ambíguo.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String INVALID_DATA = "Dados inválidos";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return handleExceptionInternal(ex, invalidData("Um ou mais campos são inválidos.", errors), headers,
                HttpStatus.BAD_REQUEST, request);
    }

    /** A mensagem da exceção traz um trecho do corpo, isto é, o texto do cliente: não vai para a resposta nem para o log. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return handleExceptionInternal(ex, invalidData("O corpo da requisição não é um JSON válido.", Map.of()),
                headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InvalidPhoneException.class)
    ResponseEntity<ProblemDetail> invalidPhone() {
        return ResponseEntity.badRequest()
                .body(invalidData("Um ou mais campos são inválidos.", Map.of("phone", "telefone inválido")));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ProblemDetail> sessionNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemResponses.problem(HttpStatus.NOT_FOUND,
                "Sessão não encontrada", "Essa sessão não existe ou expirou. Abra uma nova conversa."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(ProblemResponses.problem(HttpStatus.TOO_MANY_REQUESTS, e.title(),
                        "Espere " + e.retryAfterSeconds() + " s antes de tentar de novo."));
    }

    /** Outra mensagem do mesmo cliente está sendo processada e não terminou dentro do lock_timeout. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> busy() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(ProblemResponses.problem(HttpStatus.SERVICE_UNAVAILABLE, "Atendente ocupado",
                        "Ainda estou respondendo a sua mensagem anterior. Tente de novo em instantes."));
    }

    /**
     * Um bug. O log leva o tipo da exceção e o ponto do nosso código onde ela passou, sem a mensagem, que pode
     * carregar dados do cliente; a rota vai como padrão, porque o caminho real traz o id da sessão.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        log.error("Erro inesperado em {} {}: {} em {}", request.getMethod(),
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE), e.getClass().getName(),
                origin(e));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ProblemResponses.problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Algo deu errado do nosso lado. Tente de novo em instantes."));
    }

    private static String origin(Exception e) {
        return Arrays.stream(e.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.gabrielpacheco."))
                .findFirst()
                .map(StackTraceElement::toString)
                .orElse("?");
    }

    private static ProblemDetail invalidData(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemResponses.problem(HttpStatus.BAD_REQUEST, INVALID_DATA, detail);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
