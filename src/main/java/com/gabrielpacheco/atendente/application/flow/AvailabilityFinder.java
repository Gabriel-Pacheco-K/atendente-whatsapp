package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.DayAvailability;
import com.gabrielpacheco.atendente.domain.booking.ProfessionalDaySlots;
import com.gabrielpacheco.atendente.domain.booking.RequestedDayStatus;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.business.ClosedDate;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Procura o primeiro dia com horário livre a partir do pedido, pulando feriados, dias fechados e dias em que o
 * profissional não trabalha. Nunca passa de {@code daysAhead}, do teto de chamadas nem do prazo do turno.
 */
@Component
public class AvailabilityFinder {

    private final SchedulingPort scheduling;
    private final BusinessProfile business;
    private final ConversationSettings settings;

    public AvailabilityFinder(SchedulingPort scheduling, BusinessProfile business, ConversationSettings settings) {
        this.scheduling = scheduling;
        this.business = business;
        this.settings = settings;
    }

    /** @throws SchedulingUnavailableException se a agenda falhar ou o prazo do turno acabar antes de achar um dia */
    public DayAvailability find(BookingDraft draft, Catalog catalog, LocalDateTime now, Deadline deadline) {
        List<Professional> candidates = candidates(draft, catalog);
        LocalDate today = now.toLocalDate();
        LocalDate requested = draft.date().isBefore(today) ? today : draft.date();
        LocalDate lastDay = min(requested.plusDays(settings.nextDaySearchDays()), today.plusDays(settings.daysAhead()));
        LocalDateTime bookableFrom = now.plus(settings.minAdvance());

        RequestedDayStatus requestedStatus = RequestedDayStatus.AVAILABLE;
        String closedReason = null;
        LocalDate searchedUntil = requested.minusDays(1);
        int calls = 0;
        for (LocalDate day = requested; !day.isAfter(lastDay); day = day.plusDays(1)) {
            Optional<ClosedDate> closed = business.closedOn(day);
            List<Professional> working = working(candidates, day);
            RequestedDayStatus status;
            if (closed.isPresent()) {
                status = RequestedDayStatus.CLOSED_DATE;
            } else if (!business.opensOn(day)) {
                status = RequestedDayStatus.CLOSED_WEEKDAY;
            } else if (working.isEmpty()) {
                status = RequestedDayStatus.NOT_WORKING;
            } else {
                if (calls > 0 && calls + working.size() > settings.maxCallsPerTurn()) {
                    break;
                }
                if (!deadline.allows(settings.schedulingCallTimeout())) {
                    throw new SchedulingUnavailableException("O prazo do turno acabou antes de achar um dia livre");
                }
                List<ProfessionalDaySlots> slots = freeSlots(working, draft.serviceId(), day, now, deadline);
                calls += working.size();
                DayAvailability found = new DayAvailability(requested, day, slots,
                        day.equals(requested) ? RequestedDayStatus.AVAILABLE : requestedStatus, closedReason, null,
                        bookableFrom);
                if (found.hasFreeSlots()) {
                    return found;
                }
                status = RequestedDayStatus.FULL;
            }
            if (day.equals(requested)) {
                requestedStatus = status;
                closedReason = closed.map(ClosedDate::reason).orElse(null);
            }
            searchedUntil = day;
        }
        return new DayAvailability(requested, searchedUntil, List.of(), requestedStatus, closedReason, searchedUntil,
                bookableFrom);
    }

    private static List<Professional> candidates(BookingDraft draft, Catalog catalog) {
        if (draft.anyProfessional()) {
            return catalog.professionals();
        }
        return catalog.professional(draft.professionalId()).map(List::of).orElse(List.of());
    }

    private static List<Professional> working(List<Professional> candidates, LocalDate day) {
        return candidates.stream().filter(p -> p.workingDays().contains(day.getDayOfWeek())).toList();
    }

    /** Uma chamada por profissional, em paralelo; só ficam os horários que ainda não passaram. */
    private List<ProfessionalDaySlots> freeSlots(List<Professional> working, long serviceId, LocalDate day,
                                                 LocalDateTime now, Deadline deadline) {
        SchedulingPort agenda = scheduling.within(deadline);
        return ParallelCalls.each(working, professional -> new ProfessionalDaySlots(professional, day,
                agenda.freeSlots(professional.id(), serviceId, day).stream()
                        .filter(time -> day.atTime(time).isAfter(now))
                        .toList()));
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
