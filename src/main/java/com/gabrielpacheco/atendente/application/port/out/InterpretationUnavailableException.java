package com.gabrielpacheco.atendente.application.port.out;

/** O interpretador externo não respondeu algo utilizável; {@code reason} é curto e nunca traz o texto do cliente. */
public class InterpretationUnavailableException extends RuntimeException {

    private final String reason;

    public InterpretationUnavailableException(String reason) {
        super("Interpretação indisponível: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
