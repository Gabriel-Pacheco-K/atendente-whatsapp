package com.gabrielpacheco.atendente.domain.text;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normaliza o português de chat: minúsculas, sem acentos nem pontuação, letras repetidas encolhidas e
 * abreviações expandidas. ":", "/" e "-" entre dígitos sobrevivem, para "15:30", "15/10" e "20-10".
 */
public final class TextNormalizer {

    private static final int MAX_LENGTH = 1000;
    private static final Pattern THUMBS_UP_OR_OK = Pattern.compile("[\\x{1F44D}\\x{1F44C}]");
    private static final Pattern EMOJI_PRESENTATION = Pattern.compile("[\\x{FE0F}\\x{20E3}]");
    private static final Pattern SLASH_SHORTHAND = Pattern.compile("(?<![\\p{L}\\p{N}])([cps])/");
    private static final Map<String, String> SLASH_WORDS = Map.of("c", " com ", "p", " para ", "s", " sem ");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern LONG_LETTER_RUNS = Pattern.compile("([a-z])\\1{2,}");

    private TextNormalizer() {
    }

    public static NormalizedText normalize(String raw) {
        String original = raw == null ? "" : truncate(raw);
        String text = Normalizer.normalize(original, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        text = THUMBS_UP_OR_OK.matcher(text).replaceAll(" sim ");
        text = EMOJI_PRESENTATION.matcher(text).replaceAll("");
        text = SLASH_SHORTHAND.matcher(text).replaceAll(match -> SLASH_WORDS.get(match.group(1)));
        text = COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        text = LONG_LETTER_RUNS.matcher(keepLettersAndDigits(text)).replaceAll("$1");
        List<String> tokens = Abbreviations.expand(tokenize(text));
        return new NormalizedText(original, String.join(" ", tokens), tokens);
    }

    /** Só minúsculas e sem acentos: "João" vira "joao". Útil para comparar palavras soltas, como nomes. */
    public static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    private static String truncate(String raw) {
        return raw.length() <= MAX_LENGTH ? raw : raw.substring(0, MAX_LENGTH);
    }

    private static String keepLettersAndDigits(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || isDigit(c) || isSeparatorBetweenDigits(text, i);
            kept.append(keep ? c : ' ');
        }
        return kept.toString();
    }

    private static boolean isSeparatorBetweenDigits(String text, int i) {
        char c = text.charAt(i);
        return (c == ':' || c == '/' || c == '-')
                && i > 0 && i + 1 < text.length()
                && isDigit(text.charAt(i - 1)) && isDigit(text.charAt(i + 1));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static List<String> tokenize(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split(" +"));
    }
}
