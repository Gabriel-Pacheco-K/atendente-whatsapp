package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Preços, endereço, horário de funcionamento e perguntas frequentes. Só os preços dependem da agenda; o resto vem
 * do business.yml e continua funcionando com a agenda fora do ar.
 */
@Component
public class InfoResponder {

    private final BusinessProfile business;

    public InfoResponder(BusinessProfile business) {
        this.business = business;
    }

    /** Vazio quando a intenção não é informativa ou a pergunta frequente não existe. */
    public Optional<Reply> answer(Intent intent, Entities found, Catalog catalog) {
        return switch (intent) {
            case PRICES -> Optional.of(priceFor(found.serviceId(), catalog));
            case ADDRESS -> Optional.of(Texts.address(business.address()));
            case OPENING_HOURS -> Optional.of(Texts.openingHours(business.openingHours()));
            case FAQ -> business.faqs().stream()
                    .filter(faq -> faq.id().equals(found.faqId()))
                    .findFirst()
                    .map(Texts::faq);
            default -> Optional.empty();
        };
    }

    public Reply prices(Catalog catalog) {
        return catalog.online() ? Texts.prices(catalog.services()) : Texts.schedulingOffline(business.humanContactPhone());
    }

    public Reply addressAndHours() {
        return Texts.addressAndHours(business.address(), business.openingHours());
    }

    private Reply priceFor(Long serviceId, Catalog catalog) {
        if (serviceId == null || !catalog.online()) {
            return prices(catalog);
        }
        return catalog.service(serviceId).map(Texts::priceOf).orElseGet(() -> prices(catalog));
    }
}
