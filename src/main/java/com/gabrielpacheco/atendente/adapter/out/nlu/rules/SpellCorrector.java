package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.text.EditDistance;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Corrige erros de digitação contra um vocabulário fechado: pistas do léxico, calendário, apelidos dos serviços e
 * primeiros nomes dos profissionais. Só troca a palavra quando a distância cabe na tolerância do tamanho dela.
 */
final class SpellCorrector {

    /** Em empate de distância, vence a fonte que vem primeiro. */
    private enum Source { LEXICON, CALENDAR, CATALOG, NAMES }

    private record Candidate(String word, Source source) {
    }

    private final Map<String, Source> vocabulary = new HashMap<>();

    SpellCorrector(Catalog catalog) {
        register(PortugueseLexicon.words(), Source.LEXICON);
        register(PortugueseLexicon.CALENDAR, Source.CALENDAR);
        register(catalogWords(catalog), Source.CATALOG);
        register(catalog.professionals().stream().map(p -> normalize(p.firstName())).toList(), Source.NAMES);
    }

    Correction correct(List<String> tokens) {
        List<String> corrected = new ArrayList<>(tokens.size());
        Set<Integer> changed = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String fixed = isCorrectable(token) ? bestCandidate(token) : token;
            if (!fixed.equals(token)) {
                changed.add(i);
            }
            corrected.add(fixed);
        }
        return new Correction(corrected, changed);
    }

    private boolean isCorrectable(String token) {
        return EditDistance.typoTolerance(token.length()) > 0
                && token.chars().noneMatch(Character::isDigit)
                && !vocabulary.containsKey(token)
                && !PortugueseLexicon.STOPWORDS.contains(token)
                && !PortugueseLexicon.KNOWN_WORDS.contains(token);
    }

    /**
     * Em empate de distância, a fonte que vem primeiro e depois a palavra do mesmo tamanho: "cancelra" é "cancelar"
     * com duas letras trocadas, não "cancela" com uma letra a mais.
     */
    private String bestCandidate(String token) {
        Comparator<Candidate> tieBreak = Comparator.comparing(Candidate::source)
                .thenComparingInt((Candidate candidate) -> Math.abs(candidate.word().length() - token.length()))
                .thenComparing(Candidate::word);
        int tolerance = EditDistance.typoTolerance(token.length());
        Candidate best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, Source> entry : vocabulary.entrySet()) {
            String word = entry.getKey();
            int distance = EditDistance.startsAlike(token, word) ? EditDistance.osa(token, word, tolerance) : tolerance + 1;
            if (distance > tolerance) {
                continue;
            }
            Candidate candidate = new Candidate(word, entry.getValue());
            if (distance < bestDistance || (distance == bestDistance && tieBreak.compare(candidate, best) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? token : best.word();
    }

    private void register(Collection<String> words, Source source) {
        words.forEach(word -> vocabulary.putIfAbsent(word, source));
    }

    private static List<String> catalogWords(Catalog catalog) {
        return catalog.services().stream()
                .flatMap(service -> Stream.concat(Stream.of(service.name()), service.aliases().stream()))
                .flatMap(phrase -> Arrays.stream(normalize(phrase).split(" ")))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String normalize(String text) {
        return TextNormalizer.normalize(text).text();
    }
}
