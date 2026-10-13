package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.domain.booking.Note;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.reply.Reply;

import java.util.ArrayList;
import java.util.List;

/**
 * O resultado de um turno: o próximo estado e as respostas. {@code understood} falso conta como mensagem não
 * entendida; {@code note} explica algo fora do comum para os bastidores (horário ocupado, reconciliação...).
 */
public record StepResult(ConversationState next, List<Reply> replies, String customerNameToSave, boolean understood,
                         String note) {

    public static final String RECONCILED = "RECONCILED";
    public static final String SAFETY_RESTART = "SAFETY_RESTART";
    public static final String UNSUPPORTED = "UNSUPPORTED";

    public StepResult {
        replies = List.copyOf(replies);
    }

    public static StepResult of(ConversationState next, Reply... replies) {
        return new StepResult(next, List.of(replies), null, true, null);
    }

    public static StepResult notUnderstood(ConversationState next, Reply... replies) {
        return new StepResult(next, List.of(replies), null, false, null);
    }

    public StepResult withNote(String note) {
        return new StepResult(next, replies, customerNameToSave, understood, note);
    }

    public StepResult withNote(Note slotNote) {
        return withNote(slotNote == Note.NONE ? null : slotNote.name());
    }

    public StepResult savingName(String name) {
        return new StepResult(next, replies, name, understood, note);
    }

    /** Põe {@code prefix} na frente da primeira resposta. */
    public StepResult prefixed(String prefix) {
        if (prefix.isEmpty() || replies.isEmpty()) {
            return this;
        }
        List<Reply> prefixed = new ArrayList<>(replies);
        prefixed.set(0, replies.get(0).prefixedWith(prefix));
        return new StepResult(next, prefixed, customerNameToSave, understood, note);
    }

    public StepResult plus(Reply extra) {
        List<Reply> more = new ArrayList<>(replies);
        more.add(extra);
        return new StepResult(next, more, customerNameToSave, understood, note);
    }
}
