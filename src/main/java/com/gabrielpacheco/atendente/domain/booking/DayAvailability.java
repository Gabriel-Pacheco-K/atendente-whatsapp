package com.gabrielpacheco.atendente.domain.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * O resultado da busca de horários: o dia encontrado ({@code date}), que pode ser posterior ao pedido.
 * Sem horários, {@code slots} fica vazio e {@code searchedUntil} diz até onde a busca foi.
 * {@code bookableFrom} é o primeiro instante que a agenda ainda aceita (agora mais a antecedência mínima).
 */
public record DayAvailability(LocalDate requested, LocalDate date, List<ProfessionalDaySlots> slots,
                              RequestedDayStatus requestedStatus, String closedReason, LocalDate searchedUntil,
                              LocalDateTime bookableFrom) {

    public DayAvailability {
        slots = List.copyOf(slots);
    }

    public boolean hasFreeSlots() {
        return slots.stream().anyMatch(day -> !day.free().isEmpty());
    }

    public boolean movedFromRequestedDay() {
        return !date.equals(requested);
    }
}
