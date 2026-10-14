package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.booking.BookingStatus;

/** O que o dono marca no painel depois do horário: o cliente veio ou faltou. */
public enum StatusChange {
    COMPLETE(BookingStatus.COMPLETED),
    NO_SHOW(BookingStatus.NO_SHOW);

    private final BookingStatus result;

    StatusChange(BookingStatus result) {
        this.result = result;
    }

    public BookingStatus result() {
        return result;
    }
}
