package com.gabrielpacheco.atendente.adapter.out.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.SlotTakenException;
import com.gabrielpacheco.atendente.application.port.out.UnknownBookingOutcomeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduz as respostas de erro do agendamento-api para as exceções da porta. O mesmo status muda de sentido conforme
 * a operação: um 404 no cancelamento é "já não existe", mas no catálogo é um contrato quebrado. O corpo é lido com
 * tolerância (ProblemDetail, JSON padrão do Spring Boot ou HTML de um proxy) e nunca vai para o log.
 */
final class ProblemDetailMapper {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailMapper.class);
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Map<Integer, String> EXPECTED_TITLES = Map.of(
            400, "Dados inválidos",
            404, "Recurso não encontrado",
            409, "Horário indisponível",
            422, "Regra de negócio violada");

    private record Problem(String title, String detail, List<String> fieldsWithErrors) {

        static final Problem EMPTY = new Problem(null, null, List.of());
    }

    private final ObjectMapper json = new ObjectMapper();

    SchedulingException fromResponse(Operation operation, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        Problem problem = read(response);
        warnIfContractChanged(status, problem.title());
        return map(operation, status, problem);
    }

    /** Sem resposta: num POST ou PATCH, só uma falha ao conectar garante que nada foi gravado. */
    SchedulingException fromIoFailure(Operation operation, Exception failure) {
        log.warn("Falha de rede com o agendamento-api em {} ({})", operation, rootCause(failure).getClass().getName());
        String detail = "Sem resposta do agendamento-api em " + operation;
        if (operation.writes() && !neverReachedTheServer(failure)) {
            return new UnknownBookingOutcomeException(detail, failure);
        }
        return new SchedulingUnavailableException(detail, failure);
    }

    /** Um corpo de sucesso que não dá para ler: a API mudou o formato. */
    SchedulingException unreadableBody(Operation operation, Exception failure) {
        log.error("Resposta do agendamento-api em {} com formato inesperado ({})", operation,
                failure.getClass().getName());
        return new SchedulingContractException("Resposta ilegível do agendamento-api em " + operation);
    }

    /** 502, 503 e 504 vêm de um proxy ou de uma instância reiniciando: passam sozinhos. */
    static boolean isGatewayFailure(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private SchedulingException map(Operation operation, int status, Problem problem) {
        String summary = "Status " + status + " do agendamento-api em " + operation;
        if (status == 409 && operation == Operation.BOOK) {
            return new SlotTakenException(problem.detail());
        }
        if (status == 422 && operation.writes()) {
            return new BookingRejectedException(problem.detail());
        }
        if (status == 404 && (operation == Operation.FIND || operation == Operation.CANCEL
                || operation == Operation.UPDATE_STATUS)) {
            return new AppointmentNotFoundException(problem.detail());
        }
        if (status >= 400 && status < 500) {
            log.error("O agendamento-api recusou o pedido em {} com status {} (campos: {})", operation, status,
                    problem.fieldsWithErrors());
            return new SchedulingContractException(summary);
        }
        log.warn("O agendamento-api respondeu {} em {}", status, operation);
        if (isGatewayFailure(status) && operation.writes()) {
            return new UnknownBookingOutcomeException(summary);
        }
        return new SchedulingUnavailableException(summary);
    }

    private Problem read(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            JsonNode node = json.readTree(body.readNBytes(MAX_BODY_BYTES));
            if (node == null || !node.isObject()) {
                return Problem.EMPTY;
            }
            List<String> fields = new ArrayList<>();
            node.path("errors").fieldNames().forEachRemaining(fields::add);
            return new Problem(text(node, "title"), text(node, "detail"), fields);
        } catch (IOException e) {
            return Problem.EMPTY;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static void warnIfContractChanged(int status, String title) {
        String expected = EXPECTED_TITLES.get(status);
        if (expected != null && !expected.equals(title)) {
            log.warn("Possível mudança de contrato no agendamento-api: status {} com title '{}'", status, title);
        }
    }

    private static boolean neverReachedTheServer(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
