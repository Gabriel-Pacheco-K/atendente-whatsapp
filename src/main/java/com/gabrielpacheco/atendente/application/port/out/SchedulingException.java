package com.gabrielpacheco.atendente.application.port.out;

/**
 * Falha ao falar com a agenda. Os fluxos tratam cada tipo com uma resposta própria; nenhuma desfaz a transação.
 */
public abstract sealed class SchedulingException extends RuntimeException permits SlotTakenException,
        BookingRejectedException, AppointmentNotFoundException, SchedulingUnavailableException,
        UnknownBookingOutcomeException, SchedulingContractException {

    private final String detail;

    protected SchedulingException(String detail, Throwable cause) {
        super(detail, cause);
        this.detail = detail;
    }

    /** O detail do ProblemDetail do agendamento-api, quando existir. */
    public String detail() {
        return detail;
    }
}
