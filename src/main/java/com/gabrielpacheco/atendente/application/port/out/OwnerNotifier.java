package com.gabrielpacheco.atendente.application.port.out;

/** Avisa o dono quando um cliente marca sozinho. Nunca lança: um aviso perdido não pode desfazer o agendamento. */
@FunctionalInterface
public interface OwnerNotifier {

    OwnerNotifier NONE = appointment -> { };

    void newBooking(AppointmentInfo appointment);
}
