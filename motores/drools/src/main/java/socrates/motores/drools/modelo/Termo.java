package socrates.motores.drools.modelo;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Um termo composto Prolog, ex.: {@code v(2,4,49)} ou {@code inc(versao_por_confirmar,ev_e1)}.
 * Em JSON e {@code {"f": nome, "args": [...]}} (issue #6). Os argumentos sao termos normalizados por {@link TermosJson}.
 */
public record Termo(String f, List<Object> args) {

    public Termo {
        Objects.requireNonNull(f, "f");
        args = Termos.normaliza(args);
    }

    public static Termo de(String f, Object... args) {
        return new Termo(f, Arrays.asList(args));
    }

    @Override
    public String toString() {
        return Termos.texto(this);
    }
}
