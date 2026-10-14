package com.gabrielpacheco.atendente.application.port.in;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.StatusChange;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** O painel do dono: a agenda do dia de cada profissional e o que ele marca depois do horário. */
public interface OwnerAgenda {

    DayAgenda day(LocalDate date);

    AppointmentInfo mark(long appointmentId, StatusChange change);

    AppointmentInfo cancel(long appointmentId);

    Catalog catalog();

    List<LocalTime> freeSlots(long professionalId, long serviceId, LocalDate date);

    /** Marca pelo dono: um cliente que ligou ou passou na porta. */
    AppointmentInfo book(long professionalId, long serviceId, LocalDateTime startAt, String name, String phone);

    /** Ocupa o horário sem cliente (almoço, folga, compromisso). Não entra no previsto. */
    AppointmentInfo block(long professionalId, long serviceId, LocalDateTime startAt);

    /** Nome com que um bloqueio aparece na agenda. */
    String BLOCKED_NAME = "Horário bloqueado";
    /** O agendamento-api exige um telefone; este nunca é de um cliente de verdade. */
    String BLOCKED_PHONE = "00000000";

    static boolean isBlock(AppointmentInfo appointment) {
        return BLOCKED_NAME.equals(appointment.customerName()) && BLOCKED_PHONE.equals(appointment.customerPhone());
    }

    YearRevenue revenue(int year);

    record YearRevenue(int year, int completed, BigDecimal revenue, List<MonthRevenue> months) {
    }

    record MonthRevenue(int month, int completed, BigDecimal revenue) {
    }

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
