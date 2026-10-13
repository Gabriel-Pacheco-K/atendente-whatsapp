package com.gabrielpacheco.atendente.domain.conversation;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Regras para um nome de pessoa que pode ir para o agendamento: digitado no chat ou vindo do perfil do WhatsApp. */
public final class PersonName {

    private static final Pattern SHAPE = Pattern.compile("^[\\p{L}][\\p{L} '.-]{1,59}$");
    private static final int MAX_WORDS = 4;
    private static final Set<String> CONNECTIVES = Set.of("da", "de", "do", "dos", "das", "e");

    private PersonName() {
    }

    /** De 1 a 4 palavras, só letras (e ' . -), até 60 caracteres e pelo menos 2 letras. */
    public static boolean isValid(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.strip().replaceAll("\\s+", " ");
        return SHAPE.matcher(trimmed).matches()
                && trimmed.split(" ").length <= MAX_WORDS
                && trimmed.codePoints().filter(Character::isLetter).count() >= 2;
    }

    /** "joão da silva" vira "João da Silva". */
    public static String titleCase(String name) {
        String[] words = name.strip().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder titled = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                titled.append(' ');
            }
            titled.append(i > 0 && CONNECTIVES.contains(words[i]) ? words[i] : capitalizeParts(words[i]));
        }
        return titled.toString();
    }

    private static String capitalizeParts(String word) {
        StringBuilder capitalized = new StringBuilder(word.length());
        boolean startOfPart = true;
        for (char c : word.toCharArray()) {
            capitalized.append(startOfPart ? Character.toUpperCase(c) : c);
            startOfPart = c == '-' || c == '\'';
        }
        return capitalized.toString();
    }
}
