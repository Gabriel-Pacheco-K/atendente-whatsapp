package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.in.Diagnostics;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.domain.reply.PtBr;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Monta os bastidores de um turno sem expor dados pessoais: o nome informado aparece só como "informado". */
final class TurnDiagnostics {

    private TurnDiagnostics() {
    }

    static Diagnostics of(Interpretation interpretation, Catalog catalog, String stateBefore, String stateAfter,
                          String note) {
        if (interpretation == null) {
            return new Diagnostics(null, null, 0, Map.of(), stateBefore, stateAfter, note);
        }
        return new Diagnostics(interpretation.interpretedBy(), interpretation.intent(), interpretation.confidence(),
                entities(interpretation.entities(), catalog), stateBefore, stateAfter, note);
    }

    private static Map<String, String> entities(Entities found, Catalog catalog) {
        Map<String, String> described = new LinkedHashMap<>();
        if (found.serviceId() != null) {
            described.put("service", catalog.service(found.serviceId()).map(ServiceType::name)
                    .orElse(String.valueOf(found.serviceId())));
        }
        if (found.professionalId() != null) {
            described.put("professional", catalog.professional(found.professionalId()).map(Professional::name)
                    .orElse(String.valueOf(found.professionalId())));
        }
        if (found.hasBookingData()) {
            described.put("anyProfessional", String.valueOf(found.anyProfessional()));
        }
        if (found.date() != null) {
            described.put("date", found.date().toString());
        }
        if (found.dateIssue() != null) {
            described.put("dateIssue", found.dateIssue().name());
        }
        if (found.time() instanceof TimePreference.Exact exact) {
            described.put("time", PtBr.time(exact.time()));
        }
        if (found.time() instanceof TimePreference.Period period) {
            described.put("period", period.part().name());
        }
        if (found.option() != null) {
            described.put("option", String.valueOf(found.option()));
        }
        if (found.faqId() != null) {
            described.put("faq", found.faqId());
        }
        if (found.personName() != null) {
            described.put("personName", "informado");
        }
        return described;
    }

    static String confidence(Interpretation interpretation) {
        return interpretation == null ? "-" : String.format(Locale.ROOT, "%.2f", interpretation.confidence());
    }
}
