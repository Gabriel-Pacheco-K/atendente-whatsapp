package com.gabrielpacheco.atendente.application.port.out;

/** A agenda não respondeu ou falhou de forma definitiva: nada foi gravado. */
public final class SchedulingUnavailableException extends SchedulingException {

    public SchedulingUnavailableException(String detail) {
        super(detail, null);
    }

    public SchedulingUnavailableException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
