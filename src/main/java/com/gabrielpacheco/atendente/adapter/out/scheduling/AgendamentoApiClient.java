package com.gabrielpacheco.atendente.adapter.out.scheduling;

import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiJson.AppointmentJson;
import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiJson.AppointmentRequestJson;
import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiJson.AvailabilityJson;
import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiJson.CancelJson;
import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiJson.ProfessionalJson;
import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiJson.ServiceJson;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.StatusChange;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * O agendamento-api por HTTP. Uma consulta (GET) que falha por rede ou com 502/503/504 é repetida uma vez, se a
 * repetição ainda couber no prazo do turno. POST e PATCH nunca são repetidos: a primeira tentativa pode ter gravado.
 * Cada chamada, com o corpo da resposta, leva no máximo o read timeout, que é o que o prazo do turno supõe.
 */
public class AgendamentoApiClient implements SchedulingPort {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoApiClient.class);
    private static final ExecutorService CALLS =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("agendamento-api-", 0).factory());

    private static final ParameterizedTypeReference<List<ProfessionalJson>> PROFESSIONALS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<ServiceJson>> SERVICES =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<AppointmentJson>> APPOINTMENTS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<AvailabilityJson> AVAILABILITY =
            ParameterizedTypeReference.forType(AvailabilityJson.class);
    private static final ParameterizedTypeReference<AppointmentJson> APPOINTMENT =
            ParameterizedTypeReference.forType(AppointmentJson.class);

    /** O resultado de uma tentativa: o corpo, ou a falha já traduzida e se vale tentar de novo. */
    private record Attempt<T>(T body, SchedulingException failure, boolean worthRetrying) {

        static <T> Attempt<T> succeeded(T body) {
            return new Attempt<>(body, null, false);
        }

        static <T> Attempt<T> failed(SchedulingException failure, boolean worthRetrying) {
            return new Attempt<>(null, failure, worthRetrying);
        }

        T bodyOrThrow() {
            if (failure != null) {
                throw failure;
            }
            return body;
        }
    }

    private final RestClient http;
    private final ProblemDetailMapper errors;
    private final Duration readTimeout;
    private final Duration retryDelay;
    private final Deadline deadline;

    public AgendamentoApiClient(RestClient http, Duration readTimeout, Duration retryDelay) {
        this(http, new ProblemDetailMapper(), readTimeout, retryDelay, Deadline.unlimited());
    }

    private AgendamentoApiClient(RestClient http, ProblemDetailMapper errors, Duration readTimeout,
                                 Duration retryDelay, Deadline deadline) {
        this.http = http;
        this.errors = errors;
        this.readTimeout = readTimeout;
        this.retryDelay = retryDelay;
        this.deadline = deadline;
    }

    @Override
    public List<Professional> professionals() {
        return get(Operation.CATALOG, PROFESSIONALS, "/api/professionals").stream()
                .map(ProfessionalJson::toDomain)
                .toList();
    }

    @Override
    public List<ServiceType> services() {
        return get(Operation.CATALOG, SERVICES, "/api/services").stream()
                .map(ServiceJson::toDomain)
                .toList();
    }

    @Override
    public List<LocalTime> freeSlots(long professionalId, long serviceId, LocalDate date) {
        AvailabilityJson availability = get(Operation.AVAILABILITY, AVAILABILITY,
                "/api/professionals/{id}/availability?serviceId={serviceId}&date={date}",
                professionalId, serviceId, date);
        return availability.slots() == null ? List.of() : availability.slots();
    }

    @Override
    public AppointmentInfo book(NewAppointment appointment) {
        RestClient.RequestHeadersSpec<?> request = http.post().uri("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppointmentRequestJson.from(appointment));
        return attempt(Operation.BOOK, request, APPOINTMENT).bodyOrThrow().toDomain();
    }

    @Override
    public AppointmentInfo find(long appointmentId) {
        return get(Operation.FIND, APPOINTMENT, "/api/appointments/{id}", appointmentId).toDomain();
    }

    @Override
    public AppointmentInfo cancel(long appointmentId, String reason) {
        RestClient.RequestHeadersSpec<?> request = http.patch().uri("/api/appointments/{id}/cancel", appointmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelJson(reason));
        return attempt(Operation.CANCEL, request, APPOINTMENT).bodyOrThrow().toDomain();
    }

    @Override
    public AppointmentInfo updateStatus(long appointmentId, StatusChange change) {
        String action = switch (change) {
            case COMPLETE -> "complete";
            case NO_SHOW -> "no-show";
        };
        RestClient.RequestHeadersSpec<?> request =
                http.patch().uri("/api/appointments/{id}/{action}", appointmentId, action);
        return attempt(Operation.UPDATE_STATUS, request, APPOINTMENT).bodyOrThrow().toDomain();
    }

    @Override
    public List<AppointmentInfo> dayAgenda(long professionalId, LocalDate date) {
        return get(Operation.DAY_AGENDA, APPOINTMENTS, "/api/appointments?professionalId={id}&date={date}",
                professionalId, date).stream()
                .map(AppointmentJson::toDomain)
                .toList();
    }

    @Override
    public SchedulingPort within(Deadline turnDeadline) {
        return new AgendamentoApiClient(http, errors, readTimeout, retryDelay, turnDeadline);
    }

    private <T> T get(Operation operation, ParameterizedTypeReference<T> type, String uri, Object... variables) {
        Attempt<T> attempt = attempt(operation, http.get().uri(uri, variables), type);
        if (attempt.worthRetrying() && deadline.allows(retryDelay.plus(readTimeout)) && pauseBeforeRetry()) {
            log.info("Repetindo a consulta {} ao agendamento-api", operation);
            attempt = attempt(operation, http.get().uri(uri, variables), type);
        }
        return attempt.bodyOrThrow();
    }

    /**
     * O read timeout do cliente do JDK vale só até os cabeçalhos: um corpo que chega aos poucos passaria do prazo com
     * a conversa travada. Por isso a chamada inteira roda numa thread virtual, interrompida ao fim do read timeout. A
     * falha de rede é traduzida (e registrada) só aqui, para uma chamada abandonada não deixar rastro no log.
     */
    private <T> Attempt<T> attempt(Operation operation, RestClient.RequestHeadersSpec<?> request,
                                   ParameterizedTypeReference<T> type) {
        Future<Attempt<T>> call = CALLS.submit(() -> request.exchange((sent, response) ->
                response.getStatusCode().isError()
                        ? Attempt.failed(errors.fromResponse(operation, response),
                        ProblemDetailMapper.isGatewayFailure(response.getStatusCode().value()))
                        : Attempt.succeeded(response.bodyTo(type))));
        try {
            return call.get(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            return failed(operation, e.getCause());
        } catch (TimeoutException e) {
            call.cancel(true);
            return Attempt.failed(errors.fromIoFailure(operation, e), true);
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            return Attempt.failed(errors.fromIoFailure(operation, e), false);
        }
    }

    private <T> Attempt<T> failed(Operation operation, Throwable failure) {
        return switch (failure) {
            case ResourceAccessException e -> Attempt.failed(errors.fromIoFailure(operation, e), true);
            case RestClientException e -> Attempt.failed(errors.unreadableBody(operation, e), false);
            case RuntimeException e -> throw e;
            default -> throw new IllegalStateException(failure);
        };
    }

    private boolean pauseBeforeRetry() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
