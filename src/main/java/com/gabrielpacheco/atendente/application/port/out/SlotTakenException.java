package com.gabrielpacheco.atendente.application.port.out;

/** 409: o horário já está ocupado. */
public final class SlotTakenException extends SchedulingException {

    public SlotTakenException(String detail) {
        super(detail, null);
    }
}
