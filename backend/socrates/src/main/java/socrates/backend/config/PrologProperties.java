package socrates.backend.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracao do motor Prolog ({@code socrates.prolog.*}), usada pelo PrologAdapter para lancar o wrapper.pl.
 *
 * @param swipl        executavel do SWI-Prolog: um nome no PATH ou um caminho (AC6)
 * @param home         raiz com {@code motores/prolog/wrapper.pl} e {@code base_conhecimento/}; sem valor, usa-se a
 *                     copia empacotada no jar (AC6)
 * @param timeout      tempo maximo de um pedido; ao fim dele o processo e terminado (AC5)
 * @param maxProcessos processos swipl em simultaneo (AC7)
 * @param espera       quanto tempo um pedido espera por um lugar livre antes de dar erro (AC7)
 */
@ConfigurationProperties("socrates.prolog")
public record PrologProperties(
        @DefaultValue("swipl") String swipl,
        Path home,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("4") int maxProcessos,
        @DefaultValue("2s") Duration espera) {

    public PrologProperties {
        if (swipl == null || swipl.isBlank()) {
            throw new IllegalArgumentException("socrates.prolog.swipl nao pode ficar vazio");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("socrates.prolog.timeout tem de ser positivo: " + timeout);
        }
        if (maxProcessos < 1) {
            throw new IllegalArgumentException("socrates.prolog.max-processos tem de ser pelo menos 1: " + maxProcessos);
        }
        if (espera.isNegative()) {
            throw new IllegalArgumentException("socrates.prolog.espera nao pode ser negativa: " + espera);
        }
    }
}
