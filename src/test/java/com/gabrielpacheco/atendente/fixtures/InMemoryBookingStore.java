package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryBookingStore implements BookingStore {

    private final Map<Long, Booking> porAgendamento = new LinkedHashMap<>();

    @Override
    public synchronized void save(Booking booking) {
        porAgendamento.put(booking.appointmentId(), booking);
    }

    @Override
    public synchronized List<Booking> upcoming(long customerId, LocalDateTime now) {
        return porAgendamento.values().stream()
                .filter(b -> b.customerId() == customerId && b.status().isActive() && b.startAt().isAfter(now))
                .sorted(Comparator.comparing(Booking::startAt))
                .toList();
    }

    @Override
    public synchronized int countActiveUpcoming(long customerId, LocalDateTime now) {
        return upcoming(customerId, now).size();
    }

    @Override
    public synchronized void updateStatus(long appointmentId, BookingStatus status) {
        Booking b = porAgendamento.get(appointmentId);
        if (b != null) {
            porAgendamento.put(appointmentId, new Booking(b.appointmentId(), b.customerId(), b.serviceId(),
                    b.serviceName(), b.professionalId(), b.professionalName(), b.price(), b.startAt(), b.endAt(),
                    status));
        }
    }

    @Override
    public synchronized boolean belongsTo(long appointmentId, long customerId) {
        Booking b = porAgendamento.get(appointmentId);
        return b != null && b.customerId() == customerId;
    }

    @Override
    public synchronized boolean exists(long appointmentId) {
        return porAgendamento.containsKey(appointmentId);
    }

    public synchronized Booking doAgendamento(long appointmentId) {
        return porAgendamento.get(appointmentId);
    }
}
