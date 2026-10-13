package com.gabrielpacheco.atendente.domain.business;

import java.util.List;

public record Faq(String id, List<String> keywords, String answer) {

    public Faq {
        keywords = List.copyOf(keywords);
    }
}
