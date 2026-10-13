package com.gabrielpacheco.atendente.application.port.out;

import java.time.LocalDateTime;

public record NewAppointment(long professionalId, long serviceId, LocalDateTime startAt, String customerName,
                             String customerPhone) {
}
