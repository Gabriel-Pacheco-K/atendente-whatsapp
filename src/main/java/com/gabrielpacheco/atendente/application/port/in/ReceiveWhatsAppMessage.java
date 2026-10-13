package com.gabrielpacheco.atendente.application.port.in;

/** Processa uma mensagem do WhatsApp e envia as respostas depois do commit. */
public interface ReceiveWhatsAppMessage {

    void receive(IncomingMessage message);
}
