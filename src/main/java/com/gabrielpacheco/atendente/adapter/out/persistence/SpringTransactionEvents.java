package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.application.port.out.TransactionEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class SpringTransactionEvents implements TransactionEvents {

    /** Sem transação aberta não há o que desfazer, e a ação nunca roda. */
    @Override
    public void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
