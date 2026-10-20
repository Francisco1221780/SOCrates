package socrates.motores.drools.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Termos Prolog em Java (issue #6): atomo = String, inteiro = Long, lista = List imutavel,
 * termo composto = {@link Termo}, variavel livre = {@link Variavel} (so em premissas).
 */
final class Termos {

    private Termos() {
    }

    /** Normaliza uma lista de argumentos: inteiros passam a Long, listas a listas imutaveis. */
    static List<Object> normaliza(List<?> args) {
        Objects.requireNonNull(args, "args");
        List<Object> r = new ArrayList<>(args.size());
        for (Object a : args) {
            r.add(normaliza(a));
        }
        return List.copyOf(r);
    }

    private static Object normaliza(Object o) {
        if (o instanceof String || o instanceof Long || o instanceof Double
                || o instanceof Termo || o instanceof Variavel || o instanceof Facto) {
            return o;
        }
        if (o instanceof Integer || o instanceof Short || o instanceof Byte) {
            return ((Number) o).longValue();
        }
        if (o instanceof Float f) {
            return f.doubleValue();
        }
        if (o instanceof List<?> l) {
            return normaliza(l);
        }
        throw new IllegalArgumentException("termo nao suportado: " + o + (o == null ? "" : " (" + o.getClass().getName() + ")"));
    }

    /** Texto ao estilo Prolog, so para mensagens e toString. */
    static String texto(Object t) {
        if (t instanceof List<?> l) {
            return l.stream().map(Termos::texto).collect(Collectors.joining(",", "[", "]"));
        }
        if (t instanceof Termo c) {
            return c.args().isEmpty() ? c.f()
                    : c.args().stream().map(Termos::texto).collect(Collectors.joining(",", c.f() + "(", ")"));
        }
        return String.valueOf(t);
    }
}
