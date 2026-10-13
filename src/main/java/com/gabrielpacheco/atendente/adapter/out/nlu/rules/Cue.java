package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Intent;

/** Uma palavra ou expressão (já normalizada) que pesa a favor de uma intenção. */
record Cue(Intent intent, String phrase, int weight) {

    int words() {
        return phrase.split(" ").length;
    }
}
