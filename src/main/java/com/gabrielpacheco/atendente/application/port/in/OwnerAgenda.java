package com.gabrielpacheco.atendente.application.port.in;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.StatusChange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** O painel do dono: a agenda do dia de cada profissional e o que ele marca depois do horário. */
public interface OwnerAgenda {

    DayAgenda day(LocalDate date);

    AppointmentInfo mark(long appointmentId, StatusChange change);

    AppointmentInfo cancel(long appointmentId);

    record DayAgenda(LocalDate date, List<ProfessionalDay> professionals, Totals totals) {
    }

    record ProfessionalDay(long professionalId, String name, List<AppointmentInfo> appointments) {
    }

    /**
     * {@code expected} soma o que ainda está marcado e o que já foi feito; {@code done} só o que foi concluído.
     * Cancelados e faltas não entram em nenhum dos dois.
     */
    record Totals(int appointments, int completed, BigDecimal expected, BigDecimal done) {
    }
}
