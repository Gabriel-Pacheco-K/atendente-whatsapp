package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.application.CatalogProvider;
import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.flow.Pause;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ConversationConfig {

    @Bean
    ConversationSettings conversationSettings(AppProperties app) {
        AppProperties.Conversation conversation = app.conversation();
        AppProperties.Booking booking = app.booking();
        AppProperties.SchedulingApi scheduling = app.schedulingApi();
        return new ConversationSettings(app.timezone(), conversation.expiresAfter(),
                conversation.duplicateConfirmationWindow(), conversation.maxSlotOptions(),
                conversation.nextDaySearchDays(), booking.daysAhead(), booking.maxActivePerCustomer(),
                scheduling.reconciliationRetryDelay(), conversation.turnBudget(), scheduling.readTimeout(),
                scheduling.maxCallsPerTurn(), booking.minAdvance());
    }

    @Bean
    Pause pause() {
        return duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /** A recarga do catálogo roda numa thread virtual, fora da trava de qualquer conversa. */
    @Bean
    CatalogProvider catalogProvider(SchedulingPort scheduling, BusinessProfile business, Clock clock,
                                    AppProperties app) {
        return new CatalogProvider(scheduling, business, clock, app.schedulingApi().catalogTtl(),
                task -> Thread.ofVirtual().name("recarga-do-catalogo").start(task));
    }
}
