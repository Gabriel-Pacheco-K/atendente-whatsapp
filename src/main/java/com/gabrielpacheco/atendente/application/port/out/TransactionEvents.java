package com.gabrielpacheco.atendente.application.port.out;

/** Ganchos no fim da transação do turno. */
public interface TransactionEvents {

    /** Roda {@code action} se a transação corrente for desfeita. */
    void onRollback(Runnable action);
}
