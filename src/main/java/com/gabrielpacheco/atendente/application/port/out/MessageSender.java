package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.reply.Reply;

/** Envia uma resposta pelo canal de saída (WhatsApp). Falhas são registradas, nunca repetidas. */
public interface MessageSender {

    void send(String to, Reply reply);
}
