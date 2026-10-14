package com.gabrielpacheco.atendente.application.port.in;

/**
 * A mensagem não foi processada por uma falha passageira, e nada dela ficou gravado: o canal deve pedir o reenvio
 * (no WhatsApp, responder 503 para a Meta mandar de novo).
 */
public class RetryLaterException extends RuntimeException {

    public RetryLaterException(Throwable cause) {
        super("Falha passageira; a mensagem pode ser reenviada", cause);
    }
}
