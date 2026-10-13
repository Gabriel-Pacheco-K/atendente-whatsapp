package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
class JpaBookingStore implements BookingStore {

    private static final Set<BookingStatus> ACTIVE = Set.of(BookingStatus.SCHEDULED, BookingStatus.CONFIRMED);

    private final BookingRepository repository;
    private final Clock clock;

    JpaBookingStore(BookingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void save(Booking booking) {
        repository.save(new BookingEntity(booking, clock.instant()));
    }

    @Override
    public List<Booking> upcoming(long customerId, LocalDateTime now) {
        return repository.findByCustomerIdAndStatusInAndStartAtAfterOrderByStartAt(customerId, ACTIVE, now).stream()
                .map(BookingEntity::toDomain)
                .toList();
    }

    @Override
    public int countActiveUpcoming(long customerId, LocalDateTime now) {
        return repository.countByCustomerIdAndStatusInAndStartAtAfter(customerId, ACTIVE, now);
    }

    @Override
    public void updateStatus(long appointmentId, BookingStatus status) {
        repository.findByAppointmentId(appointmentId).ifPresent(row -> row.changeStatus(status, clock.instant()));
    }

    @Override
    public boolean belongsTo(long appointmentId, long customerId) {
        return repository.existsByAppointmentIdAndCustomerId(appointmentId, customerId);
    }

    @Override
    public boolean exists(long appointmentId) {
        return repository.existsByAppointmentId(appointmentId);
    }
}
