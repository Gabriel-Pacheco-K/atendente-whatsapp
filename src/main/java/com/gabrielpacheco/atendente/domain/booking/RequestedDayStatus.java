package com.gabrielpacheco.atendente.domain.booking;

/** Por que o dia pedido tem (ou não tem) horários. */
public enum RequestedDayStatus {
    AVAILABLE, CLOSED_WEEKDAY, CLOSED_DATE, NOT_WORKING, FULL
}
