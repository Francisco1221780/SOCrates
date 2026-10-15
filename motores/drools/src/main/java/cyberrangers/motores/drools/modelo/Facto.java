package cyberrangers.motores.drools.modelo;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.kie.api.definition.type.ClassReactive;

/**
 * Um facto generico e imutavel: predicado + argumentos. Dois factos sao iguais se tiverem o mesmo predicado e os
 * mesmos argumentos. O numero, a origem, o passo e a justificacao sao da sessao (ver {@code sessao.Registo}).
 *
 * <p>Nas regras DRL: {@code Facto(functor == "vetor_aberto/4", $ev : arg(0), $inst : arg(2))}.
 * Os factos nunca se modificam depois de inseridos, por isso a reatividade e por classe (os padroes usam arg(i)).
 */
@ClassReactive
public final class Facto {

    private final String predicado;
    private final List<Object> args;
    private final String functor;

    public Facto(String predicado, List<Object> args) {
        this.predicado = Objects.requireNonNull(predicado, "predicado");
        this.args = Termos.normaliza(args);
        this.functor = predicado + "/" + this.args.size();
    }

    public static Facto de(String predicado, Object... args) {
        return new Facto(predicado, Arrays.asList(args));
    }

    public String getPredicado() {
        return predicado;
    }

    public List<Object> getArgs() {
        return args;
    }

    /** Predicado e aridade, como o Prolog os distingue (ex.: {@code "versao/2"}). */
    public String getFunctor() {
        return functor;
    }

    public Object arg(int i) {
        return args.get(i);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Facto f && predicado.equals(f.predicado) && args.equals(f.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicado, args);
    }

    @Override
    public String toString() {
        return Termos.texto(new Termo(predicado, args));
    }
}
