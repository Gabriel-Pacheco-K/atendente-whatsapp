package com.gabrielpacheco.atendente.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * O leitor do corpo do webhook, que chega da internet. É um {@link ObjectReader}, e não outro ObjectMapper, para
     * não tomar o lugar do mapper do Spring Boot.
     */
    @Bean
    ObjectReader webhookReader(ObjectMapper objectMapper) {
        return webhookObjectMapper(objectMapper).reader();
    }

    /**
     * Uma cópia do mapper do Spring com limites no próprio parser: JSON fundo demais, textos enormes ou números
     * gigantes são recusados antes de virar objeto. Campos novos que a Meta acrescentar são ignorados.
     */
    public static ObjectMapper webhookObjectMapper(ObjectMapper base) {
        ObjectMapper webhook = base.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        webhook.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(20)
                .maxStringLength(8192)
                .maxNumberLength(30)
                .build());
        return webhook;
    }
}
