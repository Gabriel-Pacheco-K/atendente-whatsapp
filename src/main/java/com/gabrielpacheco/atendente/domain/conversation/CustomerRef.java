package com.gabrielpacheco.atendente.domain.conversation;

/**
 * Quem mandou a mensagem, do jeito que o canal identifica: o id da sessão na web ou o wa_id no WhatsApp.
 * {@code profileName} é o nome do perfil do WhatsApp, quando houver.
 */
public record CustomerRef(Channel channel, String externalId, String phone, String profileName) {
}
