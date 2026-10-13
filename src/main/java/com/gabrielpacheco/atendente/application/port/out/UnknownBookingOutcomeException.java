package com.gabrielpacheco.atendente.application.port.out;

/** Um POST ou PATCH saiu, mas a resposta não chegou (timeout, conexão caída, 502/503/504): pode ter sido gravado. */
public final class UnknownBookingOutcomeException extends SchedulingException {

    public UnknownBookingOutcomeException(String detail) {
        super(detail, null);
    }

    public UnknownBookingOutcomeException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
