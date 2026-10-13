package com.gabrielpacheco.atendente.domain.catalog;

import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.BARBA;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE_E_BARBA;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Reconhecimento do catálogo")
class CatalogMatcherTest {

    private final CatalogMatcher matcher = new CatalogMatcher(Barbearia.catalogo());

    @Test
    @DisplayName("\"corte e barba\" vence \"corte\" e \"barba\", porque a frase mais longa ganha")
    void fraseMaisLongaDeveVencer() {
        assertThat(servico("quero corte e barba amanha")).contains(CORTE_E_BARBA.name());
        assertThat(servico("um corte")).contains(CORTE.name());
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "fazer um degrade, Corte masculino",
            "aparar o bigode, Barba",
            "o combo completo, Corte + barba",
            "corte masculino, Corte masculino",
            "sobrancelha, Sobrancelha"
    })
    @DisplayName("Reconhece o nome do serviço e os apelidos")
    void deveReconhecerApelidos(String texto, String esperado) {
        assertThat(servico(texto)).contains(esperado);
    }

    @Test
    @DisplayName("Informa o trecho que o serviço ocupa no texto")
    void deveInformarOTrecho() {
        Hit<ServiceType> hit = matcher.matchService("quero corte e barba").orElseThrow();

        assertThat("quero corte e barba".substring(hit.start(), hit.end())).isEqualTo("corte e barba");
    }

    @Test
    @DisplayName("Uma palavra que só contém o apelido não conta: \"barbearia\" não é barba")
    void naoDeveReconhecerPedacoDePalavra() {
        assertThat(matcher.matchService("onde fica a barbearia")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "com o joao, João Batista",
            "com o joao batista, João Batista",
            "com o pedro henrique, Pedro Henrique",
            "com o joaoo, João Batista",
            "com o pedor, Pedro Henrique"
    })
    @DisplayName("Reconhece o profissional pelo nome completo ou pelo primeiro nome, mesmo com erro de digitação")
    void deveReconhecerOProfissional(String texto, String esperado) {
        assertThat(matcher.matchProfessional(texto).map(hit -> hit.value().name())).contains(esperado);
    }

    @Test
    @DisplayName("Palavra parecida com outra inicial não vira profissional")
    void naoDeveConfundirProfissional() {
        assertThat(matcher.matchProfessional("vou pedir um corte")).isEmpty();
    }

    @Test
    @DisplayName("Encontra o serviço pelo nome sem ligar para acento ou caixa")
    void deveBuscarServicoPeloNome() {
        assertThat(matcher.serviceByName("CORTE + BARBA")).contains(CORTE_E_BARBA);
        assertThat(matcher.serviceByName("barba")).contains(BARBA);
        assertThat(matcher.serviceByName("Massagem")).isEmpty();
    }

    @Test
    @DisplayName("O catálogo encontra serviço e profissional pelo id")
    void deveBuscarPeloId() {
        Catalog catalogo = Barbearia.catalogo();

        assertThat(catalogo.service(2)).contains(BARBA);
        assertThat(catalogo.professional(2)).contains(PEDRO);
        assertThat(catalogo.professional(99)).isEmpty();
        assertThat(Catalog.offline().online()).isFalse();
    }

    @Test
    @DisplayName("O profissional sabe o primeiro nome e os dias em que trabalha")
    void profissionalDeveDerivarPrimeiroNomeEDias() {
        assertThat(JOAO.firstName()).isEqualTo("João");
        assertThat(JOAO.workingDays()).containsExactly(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY);
    }

    private Optional<String> servico(String texto) {
        return matcher.matchService(texto).map(hit -> hit.value().name());
    }
}
