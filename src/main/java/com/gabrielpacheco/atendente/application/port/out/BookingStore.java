package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Os agendamentos que este atendente criou, por cliente. */
public interface BookingStore {

    void save(Booking booking);

    /** Ativos, começando depois de {@code now}, em ordem de início. */
    List<Booking> upcoming(long customerId, LocalDateTime now);

    int countActiveUpcoming(long customerId, LocalDateTime now);

    void updateStatus(long appointmentId, BookingStatus status);

    boolean belongsTo(long appointmentId, long customerId);

    boolean exists(long appointmentId);
}
