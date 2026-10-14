package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.in.OwnerAgenda;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.DailyRevenue;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.StatusChange;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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

    @Override
    public Catalog catalog() {
        return catalog.current();
    }

    @Override
    public List<LocalTime> freeSlots(long professionalId, long serviceId, LocalDate date) {
        return scheduling.freeSlots(professionalId, serviceId, date);
    }

    @Override
    public AppointmentInfo book(long professionalId, long serviceId, LocalDateTime startAt, String name,
                                String phone) {
        return scheduling.book(new NewAppointment(professionalId, serviceId, startAt, name, phone));
    }

    @Override
    public AppointmentInfo block(long professionalId, long serviceId, LocalDateTime startAt) {
        return scheduling.book(new NewAppointment(professionalId, serviceId, startAt, BLOCKED_NAME, BLOCKED_PHONE));
    }

    @Override
    public YearRevenue revenue(int year) {
        List<DailyRevenue> days = scheduling.revenue(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        List<MonthRevenue> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int m = month;
            List<DailyRevenue> inMonth = days.stream().filter(d -> d.date().getMonthValue() == m).toList();
            months.add(new MonthRevenue(month, inMonth.stream().mapToInt(DailyRevenue::completed).sum(),
                    inMonth.stream().map(DailyRevenue::revenue).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return new YearRevenue(year, months.stream().mapToInt(MonthRevenue::completed).sum(),
                months.stream().map(MonthRevenue::revenue).reduce(BigDecimal.ZERO, BigDecimal::add), months);
    }

    private static Totals totals(List<ProfessionalDay> professionals) {
        List<AppointmentInfo> all = professionals.stream().flatMap(day -> day.appointments().stream()).toList();
        List<AppointmentInfo> counted = all.stream()
                .filter(a -> !OwnerAgenda.isBlock(a))
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
