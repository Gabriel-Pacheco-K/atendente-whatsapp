package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;

/**
 * O que a conferência na agenda descobriu sobre uma tentativa de agendar com resultado desconhecido. "Não está lá" e
 * "não deu para ver" são coisas diferentes: só a primeira permite dizer ao cliente que nada foi marcado.
 */
public sealed interface Reconciliation {

    Reconciliation NOT_FOUND = new NotFound();
    Reconciliation UNKNOWN = new Unknown();

    /** A tentativa foi gravada. */
    record Found(AppointmentInfo appointment) implements Reconciliation {
    }

    /** A agenda respondeu, e a tentativa não está nela. */
    record NotFound() implements Reconciliation {
    }

    /** A agenda falhou ou o turno ficou sem tempo para conferir. */
    record Unknown() implements Reconciliation {
    }
}
