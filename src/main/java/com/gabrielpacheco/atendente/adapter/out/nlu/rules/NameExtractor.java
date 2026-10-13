package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.conversation.PersonName;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tira o nome de respostas como "me chamo joão da silva". Trabalha sobre o texto original, para manter os acentos,
 * e recusa o que parece comando ("sim", "cancelar") ou o rótulo de uma opção ("Outro nome").
 */
final class NameExtractor {

    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'.-]*");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private static final List<List<String>> LEADING_GREETINGS = phrases(
            "bom dia", "boa tarde", "boa noite", "e ai", "oi", "ola", "opa");
    private static final List<List<String>> PREFIXES = phrases(
            "meu nome completo e", "meu nome e", "eu me chamo", "me chamo", "eu sou o", "eu sou a", "eu sou",
            "sou o", "sou a", "sou", "aqui e o", "aqui e a", "aqui e", "pode colocar", "coloca", "no nome de");
    private static final List<List<String>> TRAILING = phrases("por favor");
    private static final Set<String> NOT_A_FIRST_WORD =
            Set.of("da", "de", "do", "dos", "das", "e", "o", "a", "com", "para", "no", "na", "um", "uma");
    private static final Set<String> NOT_A_NAME = Stream.concat(
                    PortugueseLexicon.singleWordCues().stream(), Stream.of("outro", "nome"))
            .collect(Collectors.toUnmodifiableSet());

    Optional<String> extract(String original, List<String> offeredOptions) {
        if (DIGIT.matcher(original).find() || matchesAnOption(original, offeredOptions)) {
            return Optional.empty();
        }
        List<String> words = new ArrayList<>();
        Matcher word = WORD.matcher(original);
        while (word.find()) {
            words.add(word.group());
        }
        List<String> keys = words.stream().map(NameExtractor::key).toList();
        int start = skipLeading(keys, 0, LEADING_GREETINGS);
        start = skipLeading(keys, start, PREFIXES);
        int end = keys.size() - trailingLength(keys.subList(start, keys.size()));
        if (start >= end || !looksLikeAName(keys.subList(start, end))) {
            return Optional.empty();
        }
        String name = String.join(" ", words.subList(start, end));
        return PersonName.isValid(name) ? Optional.of(PersonName.titleCase(name)) : Optional.empty();
    }

    private static boolean matchesAnOption(String original, List<String> offeredOptions) {
        String text = TextNormalizer.normalize(original).text();
        return offeredOptions.stream().anyMatch(label -> OptionExtractor.matchesLabel(text, label));
    }

    private static boolean looksLikeAName(List<String> keys) {
        return !NOT_A_FIRST_WORD.contains(keys.get(0)) && keys.stream().noneMatch(NOT_A_NAME::contains);
    }

    /** Pula prefixos repetidos: "pode colocar no nome de Ana" tira "pode colocar" e depois "no nome de". */
    private static int skipLeading(List<String> keys, int start, List<List<String>> phrases) {
        boolean skipped = true;
        while (skipped) {
            skipped = false;
            for (List<String> phrase : phrases) {
                if (startsWith(keys, start, phrase)) {
                    start += phrase.size();
                    skipped = true;
                    break;
                }
            }
        }
        return start;
    }

    private static int trailingLength(List<String> keys) {
        for (List<String> phrase : TRAILING) {
            int from = keys.size() - phrase.size();
            if (from >= 0 && startsWith(keys, from, phrase)) {
                return phrase.size();
            }
        }
        return 0;
    }

    private static boolean startsWith(List<String> keys, int start, List<String> phrase) {
        return start + phrase.size() <= keys.size() && keys.subList(start, start + phrase.size()).equals(phrase);
    }

    private static String key(String word) {
        return TextNormalizer.fold(word);
    }

    private static List<List<String>> phrases(String... phrases) {
        return Stream.of(phrases).map(phrase -> List.of(phrase.split(" "))).toList();
    }
}
