package com.gabrielpacheco.atendente.adapter.out.whatsapp;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import com.gabrielpacheco.atendente.application.port.out.OwnerNotifier;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Manda para o WhatsApp do dono um aviso a cada horário marcado pelo atendente. A Meta só entrega mensagem livre
 * para quem falou com o número nas últimas 24 horas: o dono manda um "oi" para o número do atendente uma vez por dia.
 */
public class WhatsAppOwnerNotifier implements OwnerNotifier {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppOwnerNotifier.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR"));

    private final MessageSender sender;
    private final String ownerPhone;

    public WhatsAppOwnerNotifier(MessageSender sender, String ownerPhone) {
        this.sender = sender;
        this.ownerPhone = ownerPhone.replaceAll("[^0-9]", "");
    }

    @Override
    public void newBooking(AppointmentInfo appointment) {
        try {
            sender.send(ownerPhone, Reply.of(text(appointment)));
        } catch (RuntimeException e) {
            log.warn("Não deu para avisar o dono do agendamento #{} ({})", appointment.id(),
                    e.getClass().getSimpleName());
        }
    }

    static String text(AppointmentInfo a) {
        return "Novo horário marcado pelo atendente:\n"
                + a.customerName() + " · " + a.serviceName() + " com " + a.professionalName() + "\n"
                + WHEN.format(a.startAt()) + " · tel. " + a.customerPhone();
    }
}
