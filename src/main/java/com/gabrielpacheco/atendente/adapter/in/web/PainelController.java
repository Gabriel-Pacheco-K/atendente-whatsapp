package com.gabrielpacheco.atendente.adapter.in.web;

import com.gabrielpacheco.atendente.adapter.in.http.ClientIpResolver;
import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.application.port.in.OwnerAgenda;
import com.gabrielpacheco.atendente.application.port.in.OwnerAgenda.DayAgenda;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.StatusChange;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * O painel do dono: login com a senha de {@code PAINEL_SENHA}, a agenda do dia e os botões de concluído, não veio e
 * cancelar. Sem a senha configurada, o painel não existe (404).
 */
@RestController
@RequestMapping("/api/painel")
@ConditionalOnExpression("'${app.painel.password:}' != ''")
class PainelController {

    static final String COOKIE = "painel";

    private final OwnerAgenda agenda;
    private final PainelSessions sessions;
    private final RateLimiter loginAttempts;
    private final Clock clock;

    PainelController(OwnerAgenda agenda, Clock clock, @Value("${app.painel.password}") String password) {
        this.agenda = agenda;
        this.clock = clock;
        this.sessions = new PainelSessions(password, clock);
        this.loginAttempts = new RateLimiter(5, Duration.ofMinutes(1), clock);
    }

    record LoginRequest(String password) {
    }

    record AppointmentView(long id, String start, String end, String service, BigDecimal price, String customer,
                           String phone, String status) {
        static AppointmentView of(AppointmentInfo a) {
            return new AppointmentView(a.id(), a.startAt().toLocalTime().toString(), a.endAt().toLocalTime().toString(),
                    a.serviceName(), a.price(), a.customerName(), a.customerPhone(), a.status().name());
        }
    }

    record ProfessionalView(long id, String name, List<AppointmentView> appointments) {
    }

    record AgendaView(LocalDate date, List<ProfessionalView> professionals, int appointments, int completed,
                      BigDecimal expected, BigDecimal done) {
        static AgendaView of(DayAgenda day) {
            return new AgendaView(day.date(),
                    day.professionals().stream()
                            .map(p -> new ProfessionalView(p.professionalId(), p.name(),
                                    p.appointments().stream().map(AppointmentView::of).toList()))
                            .toList(),
                    day.totals().appointments(), day.totals().completed(), day.totals().expected(),
                    day.totals().done());
        }
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(@RequestBody LoginRequest login, HttpServletRequest request) {
        RateLimiter.Decision decision = loginAttempts.tryAcquire(ClientIpResolver.rateLimitKey(request));
        if (!decision.allowed()) {
            throw new RateLimitExceededException("Muitas tentativas de login", decision.retryAfterSeconds());
        }
        if (!sessions.passwordMatches(login.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie(sessions.open(), PainelSessions.LIFETIME, request).toString())
                .build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.close(token(request).orElse(null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request).toString())
                .build();
    }

    @GetMapping("/agenda")
    ResponseEntity<AgendaView> agenda(@RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      HttpServletRequest request) {
        if (!authenticated(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LocalDate day = date != null ? date : LocalDateTime.now(clock).toLocalDate();
        return ResponseEntity.ok(AgendaView.of(agenda.day(day)));
    }

    @PostMapping("/agendamentos/{id}/{action}")
    ResponseEntity<AppointmentView> act(@PathVariable long id, @PathVariable String action,
                                        HttpServletRequest request) {
        if (!authenticated(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppointmentInfo updated = switch (action) {
            case "concluir" -> agenda.mark(id, StatusChange.COMPLETE);
            case "nao-veio" -> agenda.mark(id, StatusChange.NO_SHOW);
            case "cancelar" -> agenda.cancel(id);
            default -> null;
        };
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(AppointmentView.of(updated));
    }

    @ExceptionHandler(BookingRejectedException.class)
    ProblemDetail rejected(BookingRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Não foi possível alterar o agendamento");
        return problem;
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    ProblemDetail notFound() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Agendamento não encontrado");
        return problem;
    }

    @ExceptionHandler(SchedulingException.class)
    ProblemDetail unavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A agenda não respondeu. Tente de novo em instantes.");
        problem.setTitle("Agenda indisponível");
        return problem;
    }

    private boolean authenticated(HttpServletRequest request) {
        return sessions.isValid(token(request).orElse(null));
    }

    private static Optional<String> token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? Optional.empty() : Arrays.stream(cookies)
                .filter(c -> COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private static ResponseCookie cookie(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path("/api/painel")
                .maxAge(maxAge)
                .build();
    }
}
