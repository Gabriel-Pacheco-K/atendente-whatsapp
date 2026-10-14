package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.gabrielpacheco.atendente.application.port.out.InterpretationUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;

/**
 * Interpreta com o Claude, que só classifica a intenção e extrai entidades num esquema fechado: o texto dele nunca
 * chega ao cliente. Sem uma interpretação utilizável, lança {@link InterpretationUnavailableException}.
 */
public final class ClaudeInterpreter implements MessageInterpreter {

    private final ClaudeGateway gateway;
    private final ClaudePrompts prompts;

    public ClaudeInterpreter(ClaudeGateway gateway, String businessName) {
        this.gateway = gateway;
        this.prompts = new ClaudePrompts(businessName);
    }

    @Override
    public Interpretation interpret(String text, InterpretationContext context) {
        ClaudeResult result = gateway.extract(prompts.system(context.catalog(), context.faqs()),
                prompts.user(text, context));
        return switch (result) {
            case ClaudeResult.Success success -> ClaudeExtractionMapper.toInterpretation(success.extraction(), context);
            case ClaudeResult.NotUsable notUsable -> throw new InterpretationUnavailableException(notUsable.reason());
            case ClaudeResult.Failed failed ->
                    throw new InterpretationUnavailableException(failed.reason(), failed.cause());
        };
    }
}
