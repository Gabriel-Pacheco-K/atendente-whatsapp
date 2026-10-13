package com.gabrielpacheco.atendente.domain.nlu;

import com.gabrielpacheco.atendente.domain.business.Faq;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** O que o interpretador precisa saber da conversa: data de referência, catálogo e as opções na tela. */
public record InterpretationContext(LocalDate today, LocalTime now, Catalog catalog, List<Faq> faqs,
                                    Expectation expectation, List<String> offeredOptions, int maxDaysAhead) {

    public InterpretationContext {
        faqs = List.copyOf(faqs);
        offeredOptions = List.copyOf(offeredOptions);
    }
}
