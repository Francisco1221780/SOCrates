package cyberrangers.cyber.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.NestedExceptionUtils;

class PrologPropertiesTest {

    private static PrologProperties liga(Map<String, String> valores) {
        Binder binder = new Binder(new MapConfigurationPropertySource(valores));
        return binder.bindOrCreate("cyber.prolog", PrologProperties.class);
    }

    @Test
    void semConfiguracaoFicamOsValoresDeOmissao() {
        PrologProperties p = liga(Map.of());
        assertEquals("swipl", p.swipl());
        assertNull(p.home());
        assertEquals(Duration.ofSeconds(10), p.timeout());
        assertEquals(4, p.maxProcessos());
        assertEquals(Duration.ofSeconds(2), p.espera());
    }

    @Test
    void lePropriedadesComOsNomesDoYaml() {
        PrologProperties p = liga(Map.of(
                "cyber.prolog.swipl", "/usr/bin/swipl",
                "cyber.prolog.home", "../..",
                "cyber.prolog.timeout", "500ms",
                "cyber.prolog.max-processos", "2",
                "cyber.prolog.espera", "0s"));
        assertEquals("/usr/bin/swipl", p.swipl());
        assertEquals(Path.of("../.."), p.home());
        assertEquals(Duration.ofMillis(500), p.timeout());
        assertEquals(2, p.maxProcessos());
        assertEquals(Duration.ZERO, p.espera());
    }

    @Test
    void recusaValoresInvalidos() {
        for (Map<String, String> invalido : List.of(
                Map.of("cyber.prolog.swipl", " "),
                Map.of("cyber.prolog.timeout", "0s"),
                Map.of("cyber.prolog.max-processos", "0"),
                Map.of("cyber.prolog.espera", "-1s"))) {
            BindException e = assertThrows(BindException.class, () -> liga(invalido), invalido.toString());
            assertInstanceOf(IllegalArgumentException.class, NestedExceptionUtils.getRootCause(e), invalido.toString());
        }
    }
}
