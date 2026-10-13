package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.in.web.InterpreterInfo;
import com.gabrielpacheco.atendente.adapter.out.nlu.rules.RuleBasedInterpreter;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InterpreterConfig {

    @Bean
    MessageInterpreter messageInterpreter() {
        return new RuleBasedInterpreter();
    }

    @Bean
    InterpreterInfo interpreterInfo() {
        return new InterpreterInfo("RULES");
    }
}
