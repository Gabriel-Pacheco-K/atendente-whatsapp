package com.gabrielpacheco.atendente.adapter.in.http;

import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter.Decision;
import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter.Outcome;
import com.gabrielpacheco.atendente.fixtures.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Limitador de taxa")
class RateLimiterTest {

    private final MutableClock relogio = MutableClock.naReferencia();
    private final RateLimiter limitador = new RateLimiter(20, Duration.ofMinutes(1), relogio);

    @Test
    @DisplayName("A 21ª no minuto é negada, com o tempo que falta para a janela reabrir")
    void vigesimaPrimeiraDeveSerNegada() {
        consumir("sessao", 20);
        relogio.avancar(Duration.ofSeconds(20));

        Decision negada = limitador.tryAcquire("sessao");

        assertThat(negada.allowed()).isFalse();
        assertThat(negada.retryAfter()).isEqualTo(Duration.ofSeconds(40));
        assertThat(negada.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    @DisplayName("Só a primeira negação da janela pede aviso; as seguintes são silenciosas")
    void soAPrimeiraNegacaoDevePedirAviso() {
        consumir("5548999990000", 20);

        assertThat(limitador.tryAcquire("5548999990000").outcome()).isEqualTo(Outcome.DENIED_NOTIFY);
        assertThat(limitador.tryAcquire("5548999990000").outcome()).isEqualTo(Outcome.DENIED);
        assertThat(limitador.tryAcquire("5548999990000").outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    @DisplayName("Passado o minuto, a janela reinicia, e o aviso volta a valer")
    void janelaDeveReiniciar() {
        consumir("sessao", 21);
        relogio.avancar(Duration.ofMinutes(1));

        assertThat(limitador.tryAcquire("sessao").allowed()).isTrue();
        consumir("sessao", 19);
        assertThat(limitador.tryAcquire("sessao").outcome()).isEqualTo(Outcome.DENIED_NOTIFY);
    }

    @Test
    @DisplayName("Cada chave tem a sua janela")
    void chavesDevemSerIndependentes() {
        consumir("sessao-a", 20);

        assertThat(limitador.tryAcquire("sessao-a").allowed()).isFalse();
        assertThat(limitador.tryAcquire("sessao-b").allowed()).isTrue();
    }

    @Test
    @DisplayName("Janelas vencidas saem da memória no pedido seguinte")
    void janelasVencidasDevemSairDaMemoria() {
        consumir("10.0.0.1", 1);
        consumir("10.0.0.2", 1);
        consumir("10.0.0.3", 1);
        relogio.avancar(Duration.ofMinutes(2));

        limitador.tryAcquire("10.0.0.4");

        assertThat(limitador.trackedKeys()).isEqualTo(1);
    }

    @Test
    @DisplayName("Acima de 10 mil chaves, a mais antiga sai: um flood de IPs não esgota a memória")
    void chaveMaisAntigaDeveSairNoTeto() {
        RateLimiter umPorMinuto = new RateLimiter(1, Duration.ofMinutes(1), relogio);
        umPorMinuto.tryAcquire("primeiro");
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
            umPorMinuto.tryAcquire("ip-" + i);
        }

        assertThat(umPorMinuto.trackedKeys()).isEqualTo(RateLimiter.MAX_KEYS);
        assertThat(umPorMinuto.tryAcquire("primeiro").allowed()).isTrue();
    }

    @Test
    @DisplayName("O Retry-After arredonda para cima e nunca é zero")
    void retryAfterDeveArredondarParaCima() {
        assertThat(new Decision(Outcome.DENIED, Duration.ofMillis(1500)).retryAfterSeconds()).isEqualTo(2);
        assertThat(new Decision(Outcome.DENIED, Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
    }

    private void consumir(String chave, int vezes) {
        for (int i = 0; i < vezes; i++) {
            limitador.tryAcquire(chave);
        }
    }
}
