package com.gabrielpacheco.atendente.domain.conversation;

public enum Channel {
    WEB("chat web"), WHATSAPP("WhatsApp");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    /** Como o canal aparece para a equipe, por exemplo no motivo de um cancelamento. */
    public String label() {
        return label;
    }
}
