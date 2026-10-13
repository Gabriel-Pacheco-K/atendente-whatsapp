package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    List<BookingEntity> findByCustomerIdAndStatusInAndStartAtAfterOrderByStartAt(long customerId,
                                                                                 Collection<BookingStatus> statuses,
                                                                                 LocalDateTime after);

    int countByCustomerIdAndStatusInAndStartAtAfter(long customerId, Collection<BookingStatus> statuses,
                                                    LocalDateTime after);

    Optional<BookingEntity> findByAppointmentId(long appointmentId);

    boolean existsByAppointmentId(long appointmentId);

    boolean existsByAppointmentIdAndCustomerId(long appointmentId, long customerId);
}
