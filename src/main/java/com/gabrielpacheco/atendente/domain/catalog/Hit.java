package com.gabrielpacheco.atendente.domain.catalog;

/** Algo do catálogo reconhecido no texto, com o trecho [start, end) que ele ocupa. */
public record Hit<T>(T value, int start, int end) {
}
