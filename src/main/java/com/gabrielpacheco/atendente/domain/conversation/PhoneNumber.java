package com.gabrielpacheco.atendente.domain.conversation;

import java.util.regex.Pattern;

/**
 * O telefone que o cliente digita na web, no mesmo formato que o agendamento-api aceita. O limite de 20 caracteres
 * vem das colunas: aqui e lá o telefone é VARCHAR(20), e a expressão sozinha deixaria passar 21.
 */
public final class PhoneNumber {

    public static final String PATTERN = "^\\+?[0-9 ()-]{8,20}$";
    public static final int MAX_LENGTH = 20;

    private static final Pattern SHAPE = Pattern.compile(PATTERN);

    private PhoneNumber() {
    }

    public static boolean isValid(String phone) {
        return phone != null && phone.length() <= MAX_LENGTH && SHAPE.matcher(phone).matches();
    }
}
