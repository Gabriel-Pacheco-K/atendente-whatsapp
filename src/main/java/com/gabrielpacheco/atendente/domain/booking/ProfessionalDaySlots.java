package com.gabrielpacheco.atendente.domain.booking;

import com.gabrielpacheco.atendente.domain.catalog.Professional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Os horários livres de um profissional num dia, em ordem crescente. */
public record ProfessionalDaySlots(Professional professional, LocalDate date, List<LocalTime> free) {

    public ProfessionalDaySlots {
        free = free.stream().sorted().toList();
    }
}
