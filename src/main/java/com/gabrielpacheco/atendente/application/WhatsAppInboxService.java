package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.ReceiveWhatsAppMessage;
import com.gabrielpacheco.atendente.application.port.in.RetryLaterException;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.privacy.PersonalDataMasker;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.Arrays;
import java.util.List;

/**
 * O WhatsApp sobre o núcleo da conversa. Não tem transação própria: o turno faz commit ao sair de
 * {@link ConversationService#handle}, e só então as respostas são enviadas. Assim, nada é enviado de um turno que
 * acabou desfeito, e uma falha no envio não desfaz o turno.
 */
@Service
public class WhatsAppInboxService implements ReceiveWhatsAppMessage {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboxService.class);

    private final ConversationService conversations;
    private final MessageSender sender;
    private final BusinessProfile business;

    public WhatsAppInboxService(ConversationService conversations, MessageSender sender, BusinessProfile business) {
        this.conversations = conversations;
        this.sender = sender;
        this.business = business;
    }

    @Override
    public void receive(IncomingMessage message) {
        ConversationReply reply = processOrApologize(message);
        if (!reply.duplicate()) {
            reply.replies().forEach(r -> sender.send(message.customer().externalId(), r));
        }
    }

    @Override
    public void warnRateLimited(String waId) {
        sender.send(waId, Texts.rateLimited());
    }

    private ConversationReply processOrApologize(IncomingMessage message) {
        try {
            return conversations.handle(message);
        } catch (RuntimeException e) {
            rethrowIfTransient(e, message);
            // Um bug: reprocessar daria o mesmo erro, e a Meta reenviaria a mensagem por dias.
            log.error("Falha inesperada ao processar mensagem do WhatsApp (cliente {}): {} em {}",
                    PersonalDataMasker.maskPhone(message.customer().phone()), e.getClass().getName(), origin(e));
            markAsFailed(message);
            return new ConversationReply(List.of(Texts.genericError(business.humanContactPhone())), null, false);
        }
    }

    private void markAsFailed(IncomingMessage message) {
        try {
            conversations.registerFailed(message);
        } catch (RuntimeException e) {
            rethrowIfTransient(e, message);
            throw e;
        }
    }

    /**
     * Banco fora, transação que não abriu, conversa travada: o turno foi desfeito por inteiro, inclusive o registro
     * de idempotência, então o reenvio da Meta é processado do zero. Violação de integridade não entra aqui, porque
     * repetir daria a mesma violação.
     */
    private static void rethrowIfTransient(RuntimeException e, IncomingMessage message) {
        boolean transientFailure = (e instanceof DataAccessException && !(e instanceof DataIntegrityViolationException))
                || e instanceof TransactionException;
        if (transientFailure) {
            log.warn("Mensagem do WhatsApp do cliente {} não processada ({}); a Meta vai reenviar",
                    PersonalDataMasker.maskPhone(message.customer().phone()), e.getClass().getSimpleName());
            throw new RetryLaterException(e);
        }
    }

    /** O primeiro ponto do nosso código por onde a exceção passou; a mensagem dela pode trazer dados do cliente. */
    private static String origin(RuntimeException e) {
        return Arrays.stream(e.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.gabrielpacheco."))
                .findFirst()
                .map(StackTraceElement::toString)
                .orElse("?");
    }
}
