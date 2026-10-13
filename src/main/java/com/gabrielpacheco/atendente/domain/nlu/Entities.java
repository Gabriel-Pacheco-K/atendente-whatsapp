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

    /** Campo a campo: o meu valor quando presente, senão o do {@code fallback}. */
    public Entities or(Entities fallback) {
        return new Entities(
                firstPresent(serviceId, fallback.serviceId),
                firstPresent(professionalId, fallback.professionalId),
                anyProfessional || fallback.anyProfessional,
                firstPresent(date, fallback.date),
                firstPresent(dateIssue, fallback.dateIssue),
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
