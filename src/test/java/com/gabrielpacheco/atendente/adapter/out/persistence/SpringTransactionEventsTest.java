package com.gabrielpacheco.atendente.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ganchos de fim de transação")
class SpringTransactionEventsTest {

    private final SpringTransactionEvents eventos = new SpringTransactionEvents();
    private final AtomicInteger execucoes = new AtomicInteger();

    @AfterEach
    void encerrarTransacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("A ação roda quando a transação é desfeita")
    void acaoDeveRodarNoRollback() {
        TransactionSynchronizationManager.initSynchronization();
        eventos.onRollback(execucoes::incrementAndGet);

        terminar(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(execucoes).hasValue(1);
    }

    @Test
    @DisplayName("A ação não roda quando a transação é confirmada")
    void acaoNaoDeveRodarNoCommit() {
        TransactionSynchronizationManager.initSynchronization();
        eventos.onRollback(execucoes::incrementAndGet);

        terminar(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(execucoes).hasValue(0);
    }

    @Test
    @DisplayName("Fora de uma transação, não há o que desfazer")
    void foraDeTransacaoNaoDeveFazerNada() {
        eventos.onRollback(execucoes::incrementAndGet);

        assertThat(execucoes).hasValue(0);
    }

    private static void terminar(int status) {
        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(), status);
    }
}
