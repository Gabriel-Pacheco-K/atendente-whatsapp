package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.in.OwnerAgenda;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.StatusChange;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class OwnerAgendaService implements OwnerAgenda {

    static final String CANCEL_REASON = "Cancelado pelo estabelecimento";

    private final SchedulingPort scheduling;
    private final CatalogProvider catalog;

    public OwnerAgendaService(SchedulingPort scheduling, CatalogProvider catalog) {
        this.scheduling = scheduling;
        this.catalog = catalog;
    }

    @Override
    public DayAgenda day(LocalDate date) {
        List<ProfessionalDay> professionals = catalog.current().professionals().stream()
                .sorted(Comparator.comparing(Professional::name))
                .map(professional -> new ProfessionalDay(professional.id(), professional.name(),
                        scheduling.dayAgenda(professional.id(), date).stream()
                                .sorted(Comparator.comparing(AppointmentInfo::startAt))
                                .toList()))
                .toList();
        return new DayAgenda(date, professionals, totals(professionals));
    }

    @Override
    public AppointmentInfo mark(long appointmentId, StatusChange change) {
        return scheduling.updateStatus(appointmentId, change);
    }

    @Override
    public AppointmentInfo cancel(long appointmentId) {
        return scheduling.cancel(appointmentId, CANCEL_REASON);
    }

    private static Totals totals(List<ProfessionalDay> professionals) {
        List<AppointmentInfo> all = professionals.stream().flatMap(day -> day.appointments().stream()).toList();
        List<AppointmentInfo> counted = all.stream()
                .filter(a -> a.status().isActive() || a.status() == BookingStatus.COMPLETED)
                .toList();
        List<AppointmentInfo> completed = counted.stream()
                .filter(a -> a.status() == BookingStatus.COMPLETED)
                .toList();
        return new Totals(counted.size(), completed.size(), sum(counted), sum(completed));
    }

    private static BigDecimal sum(List<AppointmentInfo> appointments) {
        return appointments.stream()
                .map(AppointmentInfo::price)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
