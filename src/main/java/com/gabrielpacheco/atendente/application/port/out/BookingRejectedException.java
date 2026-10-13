package com.gabrielpacheco.atendente.application.port.out;

/** 422: uma regra de negócio do agendamento-api recusou o pedido; o detail pode ser mostrado ao cliente. */
public final class BookingRejectedException extends SchedulingException {

    public BookingRejectedException(String detail) {
        super(detail, null);
    }
}
