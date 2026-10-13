package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("Configuração do estabelecimento")
class BusinessConfigTest {

    private static final BusinessProperties.FaqEntry PAGAMENTO =
            new BusinessProperties.FaqEntry("pagamento", List.of("pix"), "Aceitamos Pix.");
    private static final BusinessProperties.OpeningHours TERCA_A_SEXTA = new BusinessProperties.OpeningHours(
            Set.of(DayOfWeek.TUESDAY), LocalTime.of(9, 0), LocalTime.of(19, 0));

    @Test
    @DisplayName("O business.yml de exemplo descreve a mesma barbearia usada nos diálogos")
    void businessYmlDeveSerOMesmoDosDialogos() {
        assertThat(BusinessConfig.toProfile(lerBusinessYml())).isEqualTo(Barbearia.perfil());
    }

    @Test
    @DisplayName("Sem nome, endereço ou telefone, não sobe e diz o que falta")
    void dadosObrigatoriosDevemFaltarComMensagemClara() {
        BusinessProperties semNome = new BusinessProperties(" ", null, "(48) 3333-0000", "sempre",
                List.of(TERCA_A_SEXTA), List.of(), List.of(), List.of(PAGAMENTO));

        assertThatIllegalStateException().isThrownBy(() -> BusinessConfig.toProfile(semNome))
                .withMessageContaining("business.name é obrigatório")
                .withMessageContaining("business.address é obrigatório");
    }

    @Test
    @DisplayName("Abertura depois do fechamento não sobe")
    void aberturaDepoisDoFechamentoNaoDeveSubir() {
        BusinessProperties invertido = new BusinessProperties("Barbearia", "Rua A", "(48) 3333-0000", "sempre",
                List.of(new BusinessProperties.OpeningHours(Set.of(DayOfWeek.TUESDAY), LocalTime.of(19, 0),
                        LocalTime.of(9, 0))),
                List.of(), List.of(), List.of());

        assertThatIllegalStateException().isThrownBy(() -> BusinessConfig.toProfile(invertido))
                .withMessageContaining("19:00 precisa ser antes de 09:00");
    }

    @Test
    @DisplayName("Duas perguntas frequentes com o mesmo id não sobem")
    void idDePerguntaRepetidoNaoDeveSubir() {
        BusinessProperties repetido = new BusinessProperties("Barbearia", "Rua A", "(48) 3333-0000", "sempre",
                List.of(TERCA_A_SEXTA), List.of(), List.of(), List.of(PAGAMENTO, PAGAMENTO));

        assertThatIllegalStateException().isThrownBy(() -> BusinessConfig.toProfile(repetido))
                .withMessageContaining("o id 'pagamento' aparece mais de uma vez");
    }

    private static BusinessProperties lerBusinessYml() {
        try {
            List<PropertySource<?>> fontes =
                    new YamlPropertySourceLoader().load("business.yml", new ClassPathResource("business.yml"));
            MutablePropertySources propriedades = new MutablePropertySources();
            fontes.forEach(propriedades::addLast);
            return new Binder(ConfigurationPropertySources.from(propriedades))
                    .bind("business", BusinessProperties.class)
                    .get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
