package com.gabrielpacheco.atendente.adapter.out.nlu;

import com.gabrielpacheco.atendente.application.port.out.InterpretationUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * As regras primeiro; o Claude só quando agrega. Ele nunca é chamado numa confirmação nem quando o cliente diz o
 * nome, nem quando as regras já estão confiantes, nem para textos com menos de 3 letras. Se falhar, vale o que as
 * regras entenderam.
 */
public final class HybridInterpreter implements MessageInterpreter {

    private static final Logger log = LoggerFactory.getLogger(HybridInterpreter.class);

    /** O "sim" que agenda e o nome do cliente ficam sempre com as regras: determinísticos e sem sair do servidor. */
    private static final Set<Expectation> RULES_ONLY = Set.of(Expectation.YES_NO, Expectation.NAME);
    private static final int MIN_LETTERS = 3;
    private static final double MIN_CLAUDE_INTENT_CONFIDENCE = 0.6;

    private final MessageInterpreter rules;
    private final Optional<MessageInterpreter> claude;
    private final double rulesConfidenceThreshold;

    public HybridInterpreter(MessageInterpreter rules, Optional<MessageInterpreter> claude,
                             double rulesConfidenceThreshold) {
        this.rules = rules;
        this.claude = claude;
        this.rulesConfidenceThreshold = rulesConfidenceThreshold;
    }

    @Override
    public Interpretation interpret(String text, InterpretationContext context) {
        Interpretation byRules = rules.interpret(text, context);
        if (claude.isEmpty() || !worthAsking(text, context, byRules)) {
            return byRules;
        }
        try {
            return combine(claude.get().interpret(text, context), byRules);
        } catch (InterpretationUnavailableException e) {
            log.warn("Claude indisponível ({}); usando o motor de regras", e.reason());
            return new Interpretation(byRules.intent(), byRules.confidence(), byRules.entities(),
                    InterpreterKind.RULES_AFTER_FAILURE);
        }
    }

    private boolean worthAsking(String text, InterpretationContext context, Interpretation byRules) {
        return !RULES_ONLY.contains(context.expectation())
                && byRules.confidence() < rulesConfidenceThreshold
                && text.codePoints().filter(Character::isLetter).count() >= MIN_LETTERS;
    }

    /** A intenção do Claude vale quando ele tem certeza; as entidades dele completam as das regras. */
    private static Interpretation combine(Interpretation byClaude, Interpretation byRules) {
        boolean claudeIsSure = byClaude.intent() != Intent.UNKNOWN
                && byClaude.confidence() >= MIN_CLAUDE_INTENT_CONFIDENCE;
        return new Interpretation(claudeIsSure ? byClaude.intent() : byRules.intent(),
                Math.max(byClaude.confidence(), byRules.confidence()),
                byClaude.entities().or(byRules.entities()),
                InterpreterKind.CLAUDE);
    }
}
