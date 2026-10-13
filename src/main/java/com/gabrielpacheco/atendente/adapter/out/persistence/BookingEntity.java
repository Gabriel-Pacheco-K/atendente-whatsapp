package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Um agendamento que este atendente criou. Os horários são locais do estabelecimento, como no agendamento-api. */
@Entity
@Table(name = "bookings")
class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private long appointmentId;

    @Column(name = "service_id", nullable = false)
    private long serviceId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "professional_id", nullable = false)
    private long professionalId;

    @Column(name = "professional_name", nullable = false, length = 100)
    private String professionalName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingEntity() {
    }

    BookingEntity(Booking booking, Instant now) {
        this.customerId = booking.customerId();
        this.appointmentId = booking.appointmentId();
        this.serviceId = booking.serviceId();
        this.serviceName = booking.serviceName();
        this.professionalId = booking.professionalId();
        this.professionalName = booking.professionalName();
        this.price = booking.price();
        this.startAt = booking.startAt();
        this.endAt = booking.endAt();
        this.status = booking.status();
        this.createdAt = now;
        this.updatedAt = now;
    }

    void changeStatus(BookingStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    Booking toDomain() {
        return new Booking(appointmentId, customerId, serviceId, serviceName, professionalId, professionalName, price,
                startAt, endAt, status);
    }
}
