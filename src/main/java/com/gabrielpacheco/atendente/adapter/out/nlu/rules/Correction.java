package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** As palavras depois da correção de digitação, lembrando quais foram trocadas. */
record Correction(List<String> tokens, Set<Integer> correctedIndexes) {

    Correction {
        tokens = List.copyOf(tokens);
        correctedIndexes = Set.copyOf(correctedIndexes);
    }

    static Correction unchanged(List<String> tokens) {
        return new Correction(tokens, Set.of());
    }

    String text() {
        return String.join(" ", tokens);
    }

    boolean touches(Spans.Range range) {
        return IntStream.range(range.start(), range.end()).anyMatch(correctedIndexes::contains);
    }
}
