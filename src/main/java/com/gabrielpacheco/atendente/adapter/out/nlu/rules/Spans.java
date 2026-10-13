package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Controla quais palavras da mensagem já foram usadas por um extrator. As palavras consumidas aparecem
 * mascaradas com '#' em {@link #text()}, mantendo as posições, para que o próximo extrator não as reaproveite:
 * depois que "15h" vira hora, ninguém mais lê "15" como dia.
 */
final class Spans {

    record Range(int start, int end) {
    }

    private static final char MASK = '#';

    private final List<String> tokens;
    private final int[] offsets;
    private final boolean[] consumed;
    private final char[] masked;

    Spans(List<String> tokens) {
        this.tokens = List.copyOf(tokens);
        this.offsets = new int[tokens.size()];
        this.consumed = new boolean[tokens.size()];
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                text.append(' ');
            }
            offsets[i] = text.length();
            text.append(tokens.get(i));
        }
        this.masked = text.toString().toCharArray();
    }

    String text() {
        return new String(masked);
    }

    /** As palavras ainda livres, separadas por espaço. */
    String remaining() {
        return IntStream.range(0, tokens.size())
                .filter(i -> !consumed[i])
                .mapToObj(tokens::get)
                .collect(Collectors.joining(" "));
    }

    /** Consome as palavras que tocam o trecho [startChar, endChar) de {@link #text()}. */
    void consume(int startChar, int endChar) {
        if (endChar > startChar) {
            consumeTokens(tokenAt(startChar), tokenAt(endChar - 1) + 1);
        }
    }

    /** Consome tudo: a mensagem inteira foi a resposta (um número de opção, um dia, uma hora). */
    void consumeRemaining() {
        consumeTokens(0, tokens.size());
    }

    /** Consome cada ocorrência livre da frase (palavras separadas por espaço) e devolve onde ela estava. */
    List<Range> consumeAll(String phrase) {
        List<String> words = List.of(phrase.split(" "));
        List<Range> found = new ArrayList<>();
        int i = 0;
        while (i + words.size() <= tokens.size()) {
            int end = i + words.size();
            if (isFree(i, end) && tokens.subList(i, end).equals(words)) {
                consumeTokens(i, end);
                found.add(new Range(i, end));
                i = end;
            } else {
                i++;
            }
        }
        return found;
    }

    private boolean isFree(int from, int to) {
        return IntStream.range(from, to).noneMatch(i -> consumed[i]);
    }

    private int tokenAt(int charOffset) {
        int index = Arrays.binarySearch(offsets, charOffset);
        return index >= 0 ? index : -index - 2;
    }

    private void consumeTokens(int from, int to) {
        for (int i = from; i < to; i++) {
            consumed[i] = true;
            Arrays.fill(masked, offsets[i], offsets[i] + tokens.get(i).length(), MASK);
        }
    }
}
