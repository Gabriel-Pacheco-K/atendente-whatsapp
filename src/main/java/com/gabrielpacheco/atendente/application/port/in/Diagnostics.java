package com.gabrielpacheco.atendente.application.port.in;

import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;

import java.util.Map;

/**
 * Os bastidores de um turno, para a página de demonstração: quem interpretou, o que entendeu e para onde a conversa
 * foi. As entidades vêm com os nomes do catálogo; o nome da pessoa aparece só como "informado".
 */
public record Diagnostics(InterpreterKind interpretedBy, Intent intent, double confidence, Map<String, String> entities,
                          String stateBefore, String stateAfter, String note) {

    public Diagnostics {
        entities = Map.copyOf(entities);
    }
}
