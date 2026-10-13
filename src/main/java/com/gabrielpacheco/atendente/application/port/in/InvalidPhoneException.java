package com.gabrielpacheco.atendente.application.port.in;

/** O telefone informado não serve para agendar: formato errado ou mais de 20 caracteres. */
public class InvalidPhoneException extends RuntimeException {

    public InvalidPhoneException() {
        super("Telefone inválido");
    }
}
