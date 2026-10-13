package com.gabrielpacheco.atendente.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Relógio no fuso do estabelecimento: o agendamento-api guarda horários locais, sem fuso. */
    @Bean
    Clock clock(@Value("${app.timezone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
