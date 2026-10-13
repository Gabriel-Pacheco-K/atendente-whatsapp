package com.gabrielpacheco.atendente.domain.text;

import java.util.List;

public record NormalizedText(String original, String text, List<String> tokens) {

    public NormalizedText {
        tokens = List.copyOf(tokens);
    }
}
