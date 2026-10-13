package com.gabrielpacheco.atendente.domain.nlu;

public record Interpretation(Intent intent, double confidence, Entities entities, InterpreterKind interpretedBy) {
}
