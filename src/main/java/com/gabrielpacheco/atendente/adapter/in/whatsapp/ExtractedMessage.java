package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.gabrielpacheco.atendente.application.port.in.MessageKind;

import java.time.Instant;

/**
 * Uma mensagem do cliente já tirada do payload. {@code from} é o wa_id, exatamente como chegou: é para ele que a
 * resposta vai. {@code text} é nulo quando a mensagem não é de texto.
 */
public record ExtractedMessage(String from, String profileName, String id, Instant sentAt, MessageKind kind,
                               String text) {
}
