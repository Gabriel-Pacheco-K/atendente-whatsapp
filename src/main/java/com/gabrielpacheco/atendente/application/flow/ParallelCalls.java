package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Chamadas independentes à agenda feitas ao mesmo tempo, uma thread virtual por chamada. Só a chamada roda em
 * paralelo: quem grava no banco é sempre a thread do turno, que é a dona da transação.
 */
final class ParallelCalls {

    private ParallelCalls() {
    }

    /** Os resultados na ordem de {@code items}; a primeira falha é relançada. */
    static <T, R> List<R> each(List<T> items, Function<T, R> call) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> pending = items.stream().map(item -> pool.submit(() -> call.apply(item))).toList();
            List<R> results = new ArrayList<>(items.size());
            for (Future<R> result : pending) {
                results.add(resultOf(result));
            }
            return results;
        }
    }

    private static <R> R resultOf(Future<R> result) {
        try {
            return result.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SchedulingUnavailableException("Consulta à agenda interrompida", e);
        }
    }
}
