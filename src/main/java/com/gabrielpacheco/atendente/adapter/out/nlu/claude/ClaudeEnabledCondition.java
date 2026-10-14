package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** O Claude só existe com uma chave da API e sem {@code app.ai.claude.enabled=false}. Sem chave, o custo é zero. */
public class ClaudeEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        boolean hasApiKey = !environment.getProperty("app.ai.claude.api-key", "").isBlank();
        boolean enabled = environment.getProperty("app.ai.claude.enabled", Boolean.class, true);
        return hasApiKey && enabled;
    }
}
