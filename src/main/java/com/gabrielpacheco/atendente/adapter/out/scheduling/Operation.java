package com.gabrielpacheco.atendente.adapter.out.scheduling;

/** O que estava sendo feito no agendamento-api: o mesmo status de erro significa coisas diferentes em cada caso. */
enum Operation {
    CATALOG, AVAILABILITY, BOOK, FIND, CANCEL, UPDATE_STATUS, DAY_AGENDA, REVENUE;

    /** POST e PATCH mudam a agenda: um resultado perdido pode ter sido gravado, e por isso nunca são repetidos. */
    boolean writes() {
        return this == BOOK || this == CANCEL || this == UPDATE_STATUS;
    }
}
