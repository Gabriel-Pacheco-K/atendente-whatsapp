package com.gabrielpacheco.atendente.adapter.out.whatsapp;

import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import com.gabrielpacheco.atendente.domain.privacy.PersonalDataMasker;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** O envio enquanto a Graph API não está configurada: registra para quem e o tamanho da resposta, nunca o texto. */
public class LoggingMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageSender.class);

    @Override
    public void send(String to, Reply reply) {
        long lines = reply.text().lines().count();
        log.info("Resposta para {} ({} {})", PersonalDataMasker.maskPhone(to), lines, lines == 1 ? "linha" : "linhas");
    }
}
