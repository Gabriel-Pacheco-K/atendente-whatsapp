package com.gabrielpacheco.atendente.domain.booking;

import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;

import java.time.LocalDate;
import java.util.Objects;

/**
 * O agendamento que está sendo montado. {@code chosen} é o horário que o cliente vai confirmar; com "tanto faz",
 * o profissional fica só nele, e {@code professionalId} continua nulo.
 */
public record BookingDraft(Long serviceId, Long professionalId, boolean anyProfessional, LocalDate date,
                           TimePreference time, SlotOption chosen) {

    public static final BookingDraft EMPTY = new BookingDraft(null, null, false, null, null, null);

    public BookingDraft {
        if (anyProfessional && professionalId != null) {
            throw new IllegalArgumentException("Com \"tanto faz\" não pode haver profissional escolhido");
        }
        if (chosen != null && !chosen.startAt().toLocalDate().equals(date)) {
            throw new IllegalArgumentException("O horário escolhido precisa ser do dia do rascunho");
        }
    }

    /**
     * Sobrescreve só o que a mensagem trouxe. Qualquer mudança de serviço, profissional, "tanto faz", data ou hora
     * desfaz o horário escolhido; trocar a data mantém a hora pedida.
     */
    public BookingDraft merge(Entities found) {
        Long service = found.serviceId() != null ? found.serviceId() : serviceId;
        Long professional = professionalId;
        boolean any = anyProfessional;
        if (found.professionalId() != null) {
            professional = found.professionalId();
            any = false;
        } else if (found.anyProfessional()) {
            professional = null;
            any = true;
        }
        LocalDate day = found.date() != null ? found.date() : date;
        TimePreference preference = found.time() != null ? found.time() : time;
        boolean changed = !Objects.equals(service, serviceId) || !Objects.equals(professional, professionalId)
                || any != anyProfessional || !Objects.equals(day, date) || !Objects.equals(preference, time);
        return new BookingDraft(service, professional, any, day, preference, changed ? null : chosen);
    }

    public BookingDraft withService(long id) {
        return new BookingDraft(id, professionalId, anyProfessional, date, time, null);
    }

    public BookingDraft withProfessional(long id) {
        return new BookingDraft(serviceId, id, false, date, time, null);
    }

    public BookingDraft withAnyProfessional() {
        return new BookingDraft(serviceId, null, true, date, time, null);
    }

    public BookingDraft withChosen(SlotOption slot) {
        Long professional = anyProfessional ? null : Long.valueOf(slot.professionalId());
        return new BookingDraft(serviceId, professional, anyProfessional, slot.startAt().toLocalDate(), time, slot);
    }

    public BookingDraft withDate(LocalDate day) {
        return new BookingDraft(serviceId, professionalId, anyProfessional, day, time, null);
    }

    public BookingDraft withTime(TimePreference preference) {
        return new BookingDraft(serviceId, professionalId, anyProfessional, date, preference, null);
    }

    public BookingDraft withoutDateAndTime() {
        return new BookingDraft(serviceId, professionalId, anyProfessional, null, null, null);
    }
}
