package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Intent;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.gabrielpacheco.atendente.domain.nlu.Intent.ADDRESS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.AFFIRM;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.BOOK;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.CANCEL_BOOKING;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.DENY;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.FAQ;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.GREETING;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.HUMAN;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.MY_BOOKINGS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.OPENING_HOURS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.PRICES;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.RESTART;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.THANKS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.UNKNOWN;

/**
 * Soma os pesos das pistas de cada intenção e escolhe a maior. A confiança mede a folga sobre a segunda colocada.
 */
final class IntentClassifier {

    record Classification(Intent intent, double confidence, boolean yesNoConflict) {
    }

    /** Em empate, vence quem aparece antes: pedir uma pessoa nunca perde para uma saudação. */
    private static final List<Intent> TIE_BREAK = List.of(HUMAN, CANCEL_BOOKING, RESTART, MY_BOOKINGS, BOOK, PRICES,
            OPENING_HOURS, ADDRESS, FAQ, AFFIRM, DENY, THANKS, GREETING);
    private static final int MIN_WINNING_SCORE = 2;
    private static final int BOOKING_DATA_BONUS = 2;
    private static final int FAQ_KEYWORD_WEIGHT = 3;
    private static final double SCORE_FOR_FULL_CONFIDENCE = 4.0;
    private static final int CLEAR_LEAD = 2;
    private static final double NARROW_LEAD_FACTOR = 0.7;
    private static final double ENTITIES_ONLY_CONFIDENCE = 0.8;
    private static final double CORRECTED_WORD_FACTOR = 0.9;
    private static final int NEGATION_REACH = 2;
    /** Acompanham um sim ou um não sem mudar o sentido: "sim, pode marcar", "não, obrigado", "oi, pode ser". */
    private static final Set<Intent> GO_WITH_AN_ANSWER = EnumSet.of(AFFIRM, DENY, BOOK, THANKS, GREETING);

    Classification classify(Correction text, Expectation expectation, Entities found, int faqKeywords) {
        Scoreboard board = new Scoreboard();
        scoreCues(text, expectation, board);
        if (found.hasBookingData()) {
            board.addBonus(BOOK, BOOKING_DATA_BONUS);
        }
        if (faqKeywords > 0) {
            board.addCue(FAQ, FAQ_KEYWORD_WEIGHT * faqKeywords, false);
        }
        boolean conflict = board.score(AFFIRM) > 0 && board.score(DENY) > 0;
        Intent intent = decide(board, conflict, expectation);
        return new Classification(intent, confidence(intent, board, conflict, expectation, found), conflict);
    }

    private static void scoreCues(Correction text, Expectation expectation, Scoreboard board) {
        Spans spans = new Spans(text.tokens());
        Set<Integer> negating = negatingTokens(text);
        for (Cue cue : PortugueseLexicon.CUES) {
            for (Spans.Range range : spans.consumeAll(cue.phrase())) {
                boolean corrected = text.touches(range);
                if (isNegatable(cue) && isNegated(range, negating)) {
                    board.addCue(DENY, cue.weight(), corrected);
                } else {
                    board.addCue(cue.intent(), weightIn(expectation, cue), corrected);
                }
            }
        }
    }

    private static Set<Integer> negatingTokens(Correction text) {
        Spans spans = new Spans(text.tokens());
        Set<Integer> negating = new HashSet<>();
        for (String negation : PortugueseLexicon.NEGATIONS) {
            spans.consumeAll(negation).forEach(range ->
                    IntStream.range(range.start(), range.end()).forEach(negating::add));
        }
        return negating;
    }

    /** Agendar e cancelar podem ser negados; "nao vou poder ir" já é, em si, um pedido de cancelamento. */
    private static boolean isNegatable(Cue cue) {
        return cue.intent() == BOOK || cue.intent() == CANCEL_BOOKING && !cue.phrase().startsWith("nao ");
    }

    /** "nao quero agendar" e "nao quero mais marcar" são recusas; em "nao sei se da para agendar", o "sei" quebra. */
    private static boolean isNegated(Spans.Range cue, Set<Integer> negating) {
        return IntStream.rangeClosed(1, NEGATION_REACH).anyMatch(back -> negating.contains(cue.start() - back));
    }

    private static int weightIn(Expectation expectation, Cue cue) {
        boolean yesOrNo = cue.intent() == AFFIRM || cue.intent() == DENY;
        return expectation == Expectation.YES_NO && yesOrNo ? cue.weight() * 2 : cue.weight();
    }

    private static Intent decide(Scoreboard board, boolean conflict, Expectation expectation) {
        Intent leader = board.leader();
        if (leader == null || board.score(leader) < MIN_WINNING_SCORE) {
            return UNKNOWN;
        }
        boolean yesOrNo = leader == AFFIRM || leader == DENY;
        if (conflict && yesOrNo) {
            return UNKNOWN;
        }
        if (expectation == Expectation.YES_NO && yesOrNo) {
            // Na confirmação, o sim (que agenda ou cancela) só vale sozinho: "pode cancelar" é cancelar, e
            // "ok, me manda o menu" é o menu, por mais que o "pode" e o "ok" valham o dobro aqui.
            return board.leaderAmong(intent -> !GO_WITH_AN_ANSWER.contains(intent)).orElse(leader);
        }
        return leader;
    }

    private static double confidence(Intent intent, Scoreboard board, boolean conflict, Expectation expectation,
                                     Entities found) {
        double confidence;
        if (answersExpectation(intent, conflict, expectation, found)) {
            confidence = expectation == Expectation.DATE ? 0.95 : 1.0;
        } else if (intent == UNKNOWN) {
            return 0;
        } else if (!board.anyCue()) {
            confidence = ENTITIES_ONLY_CONFIDENCE;
        } else {
            int best = board.score(intent);
            int runnerUp = board.bestRivalCues(intent);
            double lead = best - runnerUp >= CLEAR_LEAD ? 1.0 : NARROW_LEAD_FACTOR;
            confidence = Math.min(1.0, best / SCORE_FOR_FULL_CONFIDENCE) * lead;
        }
        return board.cameFromCorrection(intent) ? confidence * CORRECTED_WORD_FACTOR : confidence;
    }

    /** A mensagem trouxe exatamente o que o passo pediu: a opção, o nome, a data ou um sim (ou não) sem conflito. */
    private static boolean answersExpectation(Intent intent, boolean conflict, Expectation expectation,
                                              Entities found) {
        return switch (expectation) {
            case OPTION -> found.option() != null;
            case NAME -> found.personName() != null;
            case DATE -> found.date() != null || found.dateIssue() != null;
            case YES_NO -> !conflict && (intent == AFFIRM || intent == DENY);
            case NONE -> false;
        };
    }

    private static final class Scoreboard {

        private final Map<Intent, Integer> scores = new EnumMap<>(Intent.class);
        private final Map<Intent, Integer> bonuses = new EnumMap<>(Intent.class);
        private final Set<Intent> fromCorrection = EnumSet.noneOf(Intent.class);
        private boolean anyCue;

        void addCue(Intent intent, int points, boolean corrected) {
            scores.merge(intent, points, Integer::sum);
            anyCue = true;
            if (corrected) {
                fromCorrection.add(intent);
            }
        }

        void addBonus(Intent intent, int points) {
            scores.merge(intent, points, Integer::sum);
            bonuses.merge(intent, points, Integer::sum);
        }

        int score(Intent intent) {
            return scores.getOrDefault(intent, 0);
        }

        /**
         * A maior pontuação entre as outras intenções, só com as pistas delas. O bônus das entidades ajuda BOOK a
         * vencer, mas não disputa a certeza de outra intenção: em "quanto custa a barba?", a barba é o assunto do
         * preço, e a dúvida chamaria o Claude à toa.
         */
        int bestRivalCues(Intent intent) {
            return scores.keySet().stream()
                    .filter(other -> other != intent)
                    .mapToInt(other -> score(other) - bonuses.getOrDefault(other, 0))
                    .max()
                    .orElse(0);
        }

        Intent leader() {
            return leaderAmong(intent -> true).orElse(null);
        }

        Optional<Intent> leaderAmong(Predicate<Intent> eligible) {
            return scores.keySet().stream()
                    .filter(eligible)
                    .min(Comparator.comparingInt((Intent intent) -> -score(intent)).thenComparingInt(TIE_BREAK::indexOf));
        }

        boolean anyCue() {
            return anyCue;
        }

        boolean cameFromCorrection(Intent intent) {
            return fromCorrection.contains(intent);
        }
    }
}
