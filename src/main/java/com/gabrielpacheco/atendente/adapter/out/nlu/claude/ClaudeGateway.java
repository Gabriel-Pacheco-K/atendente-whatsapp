package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

/** Uma chamada ao Claude com saída estruturada. Nunca lança exceção: toda falha vira {@link ClaudeResult.Failed}. */
@FunctionalInterface
public interface ClaudeGateway {

    ClaudeResult extract(String system, String user);
}
