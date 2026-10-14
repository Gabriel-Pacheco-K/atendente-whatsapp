package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * O corpo do webhook da WhatsApp Cloud API, só com os campos usados. A Meta acrescenta campos com o tempo; os
 * desconhecidos são ignorados na leitura.
 */
public record WhatsAppPayload(String object, List<Entry> entry) {

    public record Entry(String id, List<Change> changes) {
    }

    public record Change(String field, Value value) {
    }

    public record Value(@JsonProperty("messaging_product") String messagingProduct, Metadata metadata,
                        List<Contact> contacts, List<InboundMessage> messages, List<Status> statuses) {
    }

    public record Metadata(@JsonProperty("display_phone_number") String displayPhoneNumber,
                           @JsonProperty("phone_number_id") String phoneNumberId) {
    }

    public record Contact(Profile profile, @JsonProperty("wa_id") String waId) {
    }

    public record Profile(String name) {
    }

    /** {@code timestamp} vem em segundos desde a época, como texto. */
    public record InboundMessage(String from, String id, String timestamp, String type, Text text) {
    }

    public record Text(String body) {
    }

    public record Status(String id, String status, String timestamp) {
    }
}
