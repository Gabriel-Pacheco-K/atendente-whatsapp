package com.gabrielpacheco.atendente.application.port.out;

public final class AppointmentNotFoundException extends SchedulingException {

    public AppointmentNotFoundException(String detail) {
        super(detail, null);
    }
}
