package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;

/** Transforma o texto do cliente em intenção e entidades. Não decide nada da conversa nem tem efeitos colaterais. */
public interface MessageInterpreter {

    Interpretation interpret(String text, InterpretationContext context);
}
