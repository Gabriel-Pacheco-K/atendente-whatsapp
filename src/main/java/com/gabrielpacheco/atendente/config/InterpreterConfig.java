package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.in.web.InterpreterInfo;
import com.gabrielpacheco.atendente.adapter.out.nlu.HybridInterpreter;
import com.gabrielpacheco.atendente.adapter.out.nlu.claude.ClaudeInterpreter;
import com.gabrielpacheco.atendente.adapter.out.nlu.claude.ClaudeSettings;
import com.gabrielpacheco.atendente.adapter.out.nlu.rules.RuleBasedInterpreter;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

/**
 * O motor de regras sempre existe; o Claude só com {@code ANTHROPIC_API_KEY} (ver {@code ClaudeAdapterConfig}). A
 * conversa usa o interpretador híbrido, que só consulta o Claude quando as regras têm dúvida.
 */
@Configuration(proxyBeanMethods = false)
public class InterpreterConfig {

    private static final Logger log = LoggerFactory.getLogger(InterpreterConfig.class);

    @Bean
    RuleBasedInterpreter ruleBasedInterpreter() {
        return new RuleBasedInterpreter();
    }

    @Bean
    ClaudeSettings claudeSettings(AppProperties app) {
        AppProperties.Claude claude = app.ai().claude();
        return new ClaudeSettings(claude.apiKey(), claude.model(), claude.effort(), claude.maxTokens(),
                claude.timeout(), claude.baseUrl());
    }

    @Bean
    @Primary
    MessageInterpreter messageInterpreter(RuleBasedInterpreter rules, ObjectProvider<ClaudeInterpreter> claude,
                                          AppProperties app) {
        Optional<MessageInterpreter> optionalClaude = Optional.ofNullable(claude.getIfAvailable());
        AppProperties.Claude properties = app.ai().claude();
        if (optionalClaude.isPresent()) {
            log.info("Interpretação: motor de regras + Claude ({}, effort {}) só quando as regras têm dúvida",
                    properties.model(), properties.effort());
        } else {
            log.info("Interpretação de mensagens: motor de regras (custo zero)");
        }
        return new HybridInterpreter(rules, optionalClaude, properties.rulesConfidenceThreshold());
    }

    @Bean
    InterpreterInfo interpreterInfo(ObjectProvider<ClaudeInterpreter> claude) {
        return new InterpreterInfo(claude.getIfAvailable() == null ? "RULES" : "RULES_WITH_CLAUDE");
    }
}
