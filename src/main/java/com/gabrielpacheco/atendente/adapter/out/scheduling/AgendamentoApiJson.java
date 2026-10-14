package com.gabrielpacheco.atendente.adapter.out.scheduling;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.DailyRevenue;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.catalog.WorkingPeriod;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * O JSON do agendamento-api, do jeito que ele fala. Nada daqui sai deste pacote: o resto do sistema só vê os tipos
 * da porta. Campos novos na API são ignorados.
 */
final class AgendamentoApiJson {

    private AgendamentoApiJson() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfessionalJson(long id, String name, String specialty, List<WorkingHoursJson> workingHours) {

        Professional toDomain() {
            List<WorkingHoursJson> hours = workingHours == null ? List.of() : workingHours;
            return new Professional(id, name, hours.stream().map(WorkingHoursJson::toDomain).toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WorkingHoursJson(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {

        WorkingPeriod toDomain() {
            return new WorkingPeriod(dayOfWeek, start, end);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ServiceJson(long id, String name, int durationMinutes, BigDecimal price) {

        /** Sem apelidos: quem junta os do business.yml é o catálogo. */
        ServiceType toDomain() {
            return new ServiceType(id, name, durationMinutes, price, List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AvailabilityJson(long professionalId, long serviceId, LocalDate date, int durationMinutes,
                            List<LocalTime> slots) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppointmentRequestJson(long professionalId, long serviceId,
                                  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startAt,
                                  String customerName, String customerPhone) {

        static AppointmentRequestJson from(NewAppointment appointment) {
            return new AppointmentRequestJson(appointment.professionalId(), appointment.serviceId(),
                    appointment.startAt(), appointment.customerName(), appointment.customerPhone());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppointmentJson(long id, long professionalId, String professionalName, String service, BigDecimal price,
                           String customerName, String customerPhone, LocalDateTime startAt, LocalDateTime endAt,
                           String status, String cancellationReason) {

        AppointmentInfo toDomain() {
            return new AppointmentInfo(id, professionalId, professionalName, service, price, customerName,
                    customerPhone, startAt, endAt, BookingStatus.parse(status));
        }
    }

    record CancelJson(String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RevenueJson(LocalDate date, int completed, BigDecimal revenue) {

        DailyRevenue toDomain() {
            return new DailyRevenue(date, completed, revenue == null ? BigDecimal.ZERO : revenue);
        }
    }
}
