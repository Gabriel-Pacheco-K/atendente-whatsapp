package com.gabrielpacheco.atendente.domain.nlu;

import java.time.LocalDate;

/**
 * O que foi reconhecido numa mensagem. Campos ausentes ficam nulos (ou false).
 * {@code yesNoConflict} marca um "sim" e um "não" na mesma mensagem.
 */
public record Entities(Long serviceId, Long professionalId, boolean anyProfessional, LocalDate date,
                       DateIssue dateIssue, TimePreference time, Integer option, String personName, String faqId,
                       boolean rescheduleRequested, boolean yesNoConflict) {

    private static final Entities NONE =
            new Entities(null, null, false, null, null, null, null, null, null, false, false);

    public static Entities none() {
        return NONE;
    }

    public boolean hasBookingData() {
        return serviceId != null || professionalId != null || anyProfessional
                || date != null || dateIssue != null || time != null;
    }

    /**
     * Campo a campo: o meu valor quando presente, senão o do {@code fallback}. Dois pares andam juntos, para o
     * resultado não se contradizer: o profissional e o "tanto faz", e a data e o problema de data.
     */
    public Entities or(Entities fallback) {
        boolean mentionsProfessional = professionalId != null || anyProfessional;
        boolean mentionsDate = date != null || dateIssue != null;
        return new Entities(
                firstPresent(serviceId, fallback.serviceId),
                mentionsProfessional ? professionalId : fallback.professionalId,
                mentionsProfessional ? anyProfessional : fallback.anyProfessional,
                mentionsDate ? date : fallback.date,
                mentionsDate ? dateIssue : fallback.dateIssue,
                firstPresent(time, fallback.time),
                firstPresent(option, fallback.option),
                firstPresent(personName, fallback.personName),
                firstPresent(faqId, fallback.faqId),
                rescheduleRequested || fallback.rescheduleRequested,
                yesNoConflict || fallback.yesNoConflict);
    }

    private static <T> T firstPresent(T mine, T fallback) {
        return mine != null ? mine : fallback;
    }
}
