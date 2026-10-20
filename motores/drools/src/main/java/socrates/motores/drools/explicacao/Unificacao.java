package socrates.motores.drools.explicacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import socrates.motores.drools.modelo.Facto;
import socrates.motores.drools.modelo.Termo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Termos com variaveis e unificacao, como no Prolog. Um termo e um atomo ({@code String}), um inteiro ({@code Long}),
 * uma lista, um termo composto ({@link Termo} nos factos, {@link Comp} nos padroes) ou uma {@link Var}. As ligacoes
 * vao num mapa imutavel por convencao: {@link #unifica} devolve um mapa novo, ou null se nao unificar.
 */
final class Unificacao {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private Unificacao() {
    }

    /** Termo composto com variaveis (o {@link Termo} do modelo so aceita termos sem variaveis). */
    record Comp(String f, List<Object> args) {
    }

    static Object deref(Object t, Map<Var, Object> s) {
        while (t instanceof Var v && s.containsKey(v)) {
            t = s.get(v);
        }
        return t;
    }

    /** Unifica a e b com as ligacoes s. Devolve as ligacoes novas, ou null. */
    static Map<Var, Object> unifica(Object a, Object b, Map<Var, Object> s) {
        a = deref(a, s);
        b = deref(b, s);
        if (a instanceof Var va) {
            if (a == b) {
                return s;
            }
            Map<Var, Object> r = new HashMap<>(s);
            r.put(va, b);
            return r;
        }
        if (b instanceof Var) {
            return unifica(b, a, s);
        }
        if (a instanceof Integer i) {
            a = i.longValue();
        }
        if (b instanceof Integer i) {
            b = i.longValue();
        }
        String fa = functor(a);
        String fb = functor(b);
        if (fa != null || fb != null) {
            if (fa == null || !fa.equals(fb)) {
                return null;
            }
            return unificaListas(args(a), args(b), s);
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            return unificaListas(la, lb, s);
        }
        return a.equals(b) ? s : null;
    }

    private static Map<Var, Object> unificaListas(List<?> a, List<?> b, Map<Var, Object> s) {
        if (a.size() != b.size()) {
            return null;
        }
        for (int i = 0; i < a.size() && s != null; i++) {
            s = unifica(a.get(i), b.get(i), s);
        }
        return s;
    }

    /** Nome/aridade de um termo composto ou de um facto, ou null. */
    static String functor(Object t) {
        if (t instanceof Comp c) {
            return c.f() + "/" + c.args().size();
        }
        if (t instanceof Termo c) {
            return c.f() + "/" + c.args().size();
        }
        if (t instanceof Facto f) {
            return f.getFunctor();
        }
        return null;
    }

    static List<Object> args(Object t) {
        if (t instanceof Comp c) {
            return c.args();
        }
        if (t instanceof Termo c) {
            return c.args();
        }
        return ((Facto) t).getArgs();
    }

    static String nome(Object t) {
        if (t instanceof Comp c) {
            return c.f();
        }
        if (t instanceof Termo c) {
            return c.f();
        }
        return t instanceof Facto f ? f.getPredicado() : null;
    }

    /** O termo com as variaveis ligadas substituidas (as que ficam por ligar continuam Var). */
    static Object resolve(Object t, Map<Var, Object> s) {
        t = deref(t, s);
        if (t instanceof Var) {
            return t;
        }
        if (functor(t) != null) {
            List<Object> r = new ArrayList<>();
            for (Object a : args(t)) {
                r.add(resolve(a, s));
            }
            return new Comp(nome(t), r);
        }
        if (t instanceof List<?> l) {
            List<Object> r = new ArrayList<>();
            for (Object a : l) {
                r.add(resolve(a, s));
            }
            return r;
        }
        return t instanceof Integer i ? i.longValue() : t;
    }

    static boolean temVariaveis(Object t, Map<Var, Object> s) {
        t = deref(t, s);
        if (t instanceof Var) {
            return true;
        }
        if (functor(t) != null) {
            return args(t).stream().anyMatch(a -> temVariaveis(a, s));
        }
        return t instanceof List<?> l && l.stream().anyMatch(a -> temVariaveis(a, s));
    }

    static void variaveis(Object t, Map<Var, Object> s, List<Var> acc) {
        t = deref(t, s);
        if (t instanceof Var v) {
            if (acc.stream().noneMatch(x -> x == v)) {
                acc.add(v);
            }
        } else if (functor(t) != null) {
            args(t).forEach(a -> variaveis(a, s, acc));
        } else if (t instanceof List<?> l) {
            l.forEach(a -> variaveis(a, s, acc));
        }
    }

    /**
     * O termo em JSON do contrato, com as variaveis por ligar chamadas A, B, ... pela ordem em que aparecem, como o
     * legivel/2 do soc_motor.pl (numbervars sobre o termo que se escreve).
     */
    static JsonNode json(Object t, Map<Var, Object> s) {
        return json(t, s, new IdentityHashMap<>(), "");
    }

    private static JsonNode json(Object t, Map<Var, Object> s, Map<Var, String> nomes, String marca) {
        t = deref(t, s);
        if (t instanceof Var v) {
            return JSON.textNode(marca + nomes.computeIfAbsent(v, k -> nomeVariavel(nomes.size())));
        }
        if (functor(t) != null) {
            ObjectNode o = JSON.objectNode();
            o.put("f", nome(t));
            ArrayNode a = o.putArray("args");
            args(t).forEach(x -> a.add(json(x, s, nomes, marca)));
            return o;
        }
        if (t instanceof List<?> l) {
            ArrayNode a = JSON.arrayNode();
            l.forEach(x -> a.add(json(x, s, nomes, marca)));
            return a;
        }
        if (t instanceof Number n) {
            return JSON.numberNode(n.longValue());
        }
        return JSON.textNode(String.valueOf(t));
    }

    /** A, B, ..., Z, A1, B1, ... como o numbervars. */
    static String nomeVariavel(int i) {
        String letra = String.valueOf((char) ('A' + i % 26));
        return i < 26 ? letra : letra + (i / 26);
    }

    /**
     * Um termo JSON (o do regras.json, ou um facto pedido) com as variaveis {@code {"var": "Nome"}} trocadas por
     * {@link Var}. O mesmo nome da a mesma Var dentro de {@code nomes}; cada "_" e uma Var nova.
     */
    static Object deJson(JsonNode j, Map<String, Var> nomes) {
        if (j.isObject() && j.has("var")) {
            String n = j.get("var").asText();
            return n.equals("_") ? new Var("_") : nomes.computeIfAbsent(n, Var::new);
        }
        if (j.isObject()) {
            List<Object> a = new ArrayList<>();
            j.get("args").forEach(x -> a.add(deJson(x, nomes)));
            return new Comp(j.has("predicado") ? j.get("predicado").asText() : j.get("f").asText(), a);
        }
        if (j.isArray()) {
            List<Object> a = new ArrayList<>();
            j.forEach(x -> a.add(deJson(x, nomes)));
            return a;
        }
        if (j.isIntegralNumber()) {
            return j.asLong();
        }
        return j.asText();
    }

    /** Chave para comparar dois termos a menos dos nomes das variaveis (o =@= do Prolog). */
    static String variante(Object t, Map<Var, Object> s) {
        // as variaveis levam um prefixo que nenhum atomo tem, para 'A' (atomo) e A (variavel) nao se confundirem
        return json(t, s, new IdentityHashMap<>(), "\u0000var:").toString();
    }
}
