package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

/** O período do dia na saída do Claude. {@code NONE} existe porque enum opcional não passa na validação do esquema. */
public enum ClaudePartOfDay {
    NONE, MORNING, AFTERNOON, EVENING
}
