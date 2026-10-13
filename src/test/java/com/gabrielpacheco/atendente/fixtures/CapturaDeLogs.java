package com.gabrielpacheco.atendente.fixtures;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda o que um logger (e os filhos dele) registrou durante o teste, em vez de ler a saída inteira, que traz o
 * ruído do WireMock e do Testcontainers. Use com try-with-resources ou feche no fim do teste.
 */
public final class CapturaDeLogs implements AutoCloseable {

    private final Logger logger;
    private final Level nivelAnterior;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturaDeLogs(String nomeDoLogger, Level nivel) {
        this.logger = (Logger) LoggerFactory.getLogger(nomeDoLogger);
        this.nivelAnterior = logger.getLevel();
        logger.setLevel(nivel);
        appender.start();
        logger.addAppender(appender);
    }

    public static CapturaDeLogs de(Class<?> origem) {
        return new CapturaDeLogs(origem.getName(), Level.DEBUG);
    }

    /** Tudo o que a aplicação registra, inclusive em DEBUG. */
    public static CapturaDeLogs daAplicacao() {
        return new CapturaDeLogs("com.gabrielpacheco", Level.DEBUG);
    }

    public List<ILoggingEvent> eventos() {
        return List.copyOf(appender.list);
    }

    public List<String> mensagens(Level nivel) {
        return eventos().stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Mensagens formatadas, valores de MDC e mensagens de exceção (com as causas): tudo o que vai para o log. */
    public List<String> tudoQueFoiEscrito() {
        List<String> escrito = new ArrayList<>();
        for (ILoggingEvent evento : eventos()) {
            escrito.add(evento.getFormattedMessage());
            escrito.addAll(evento.getMDCPropertyMap().values());
            for (IThrowableProxy erro = evento.getThrowableProxy(); erro != null; erro = erro.getCause()) {
                escrito.add(String.valueOf(erro.getMessage()));
            }
        }
        return escrito;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(nivelAnterior);
    }
}
