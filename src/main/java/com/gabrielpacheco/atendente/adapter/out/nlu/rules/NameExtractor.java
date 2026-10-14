package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.catalog.CatalogMatcher;
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
 * e recusa o que parece comando ("sim", "cancelar"), conversa ("eu mesmo", "tchau", "kkkk"), um serviço, uma
 * pergunta ou o rótulo de uma opção ("Outro nome").
 */
final class NameExtractor {

    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'.-]*");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern VOWEL = Pattern.compile("[aeiouy]");

    private static final List<List<String>> LEADING_GREETINGS = phrases(
            "bom dia", "boa tarde", "boa noite", "e ai", "oi", "ola", "opa");
    /** As apresentações que deixam claro que vem um nome, mesmo fora da pergunta do nome. */
    private static final List<List<String>> INTRODUCTIONS = phrases(
            "meu nome completo e", "meu nome e", "eu me chamo", "me chamo", "no nome de", "no nome da", "no nome do");
    private static final List<List<String>> PREFIXES = Stream.concat(INTRODUCTIONS.stream(), phrases(
                    "eu sou o", "eu sou a", "eu sou", "sou o", "sou a", "sou", "aqui e o", "aqui e a", "aqui e",
                    "pode colocar", "coloca").stream())
            .toList();
    private static final List<List<String>> TRAILING = phrases("por favor");
    private static final Set<String> NOT_A_FIRST_WORD = Set.of("da", "de", "do", "dos", "das", "e", "o", "a", "com",
            "para", "pra", "pro", "no", "na", "um", "uma", "meu", "minha");
    private static final Set<String> NOT_A_NAME = Stream.of(
                    PortugueseLexicon.singleWordCues().stream(),
                    PortugueseLexicon.OPTION_WORDS.stream(),
                    Stream.of("outro", "nome", "eu", "mim", "mesmo", "mesma", "voce", "como", "assim", "espera", "ver",
                            "teste"))
            .flatMap(words -> words)
            .collect(Collectors.toUnmodifiableSet());

    /** A resposta à pergunta do nome. */
    Optional<String> extract(String original, List<String> offeredOptions, CatalogMatcher catalog) {
        if (DIGIT.matcher(original).find() || isQuestion(original) || matchesAnOption(original, offeredOptions)) {
            return Optional.empty();
        }
        List<String> words = words(original);
        List<String> keys = keys(words);
        int start = skipLeading(keys, skipLeading(keys, 0, LEADING_GREETINGS), PREFIXES);
        return nameFrom(words, keys, start, catalog);
    }

    /** Fora da pergunta do nome, só vale um nome apresentado: "meu nome é Ana", "sim, no nome de Ana". */
    Optional<String> extractIntroduced(String original, CatalogMatcher catalog) {
        if (DIGIT.matcher(original).find() || isQuestion(original)) {
            return Optional.empty();
        }
        List<String> words = words(original);
        List<String> keys = keys(words);
        for (int i = 0; i < keys.size(); i++) {
            for (List<String> introduction : INTRODUCTIONS) {
                if (startsWith(keys, i, introduction)) {
                    return nameFrom(words, keys, i + introduction.size(), catalog);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> nameFrom(List<String> words, List<String> keys, int start,
                                             CatalogMatcher catalog) {
        int end = keys.size() - trailingLength(keys.subList(start, keys.size()));
        if (start >= end || !looksLikeAName(keys.subList(start, end))) {
            return Optional.empty();
        }
        String name = String.join(" ", words.subList(start, end));
        boolean namesAService = catalog.matchService(TextNormalizer.normalize(name).text()).isPresent();
        return PersonName.isValid(name) && !namesAService ? Optional.of(PersonName.titleCase(name)) : Optional.empty();
    }

    private static boolean isQuestion(String original) {
        return original.strip().endsWith("?");
    }

    private static boolean matchesAnOption(String original, List<String> offeredOptions) {
        String text = TextNormalizer.normalize(original).text();
        return offeredOptions.stream().anyMatch(label -> OptionExtractor.matchesLabel(text, label));
    }

    private static boolean looksLikeAName(List<String> keys) {
        return !NOT_A_FIRST_WORD.contains(keys.get(0))
                && keys.stream().noneMatch(NOT_A_NAME::contains)
                && keys.stream().allMatch(key -> VOWEL.matcher(key).find());
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

    private static List<String> words(String original) {
        List<String> words = new ArrayList<>();
        Matcher word = WORD.matcher(original);
        while (word.find()) {
            words.add(word.group());
        }
        return words;
    }

    private static List<String> keys(List<String> words) {
        return words.stream().map(TextNormalizer::fold).toList();
    }

    private static List<List<String>> phrases(String... phrases) {
        return Stream.of(phrases).map(phrase -> List.of(phrase.split(" "))).toList();
    }
}
