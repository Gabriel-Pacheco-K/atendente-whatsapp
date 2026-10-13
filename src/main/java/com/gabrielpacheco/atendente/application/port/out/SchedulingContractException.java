package com.gabrielpacheco.atendente.application.port.out;

/** Uma resposta que o contrato com o agendamento-api não prevê, como um 404 de profissional do catálogo. */
public final class SchedulingContractException extends SchedulingException {

    public SchedulingContractException(String detail) {
        super(detail, null);
    }
}
