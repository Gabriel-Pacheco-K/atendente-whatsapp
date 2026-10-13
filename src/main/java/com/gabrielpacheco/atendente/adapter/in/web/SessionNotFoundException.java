package com.gabrielpacheco.atendente.adapter.in.web;

class SessionNotFoundException extends RuntimeException {

    SessionNotFoundException() {
        super("Sessão não encontrada");
    }
}
