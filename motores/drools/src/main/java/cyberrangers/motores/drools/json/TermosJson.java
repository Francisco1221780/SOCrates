package cyberrangers.motores.drools.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Termo;
import cyberrangers.motores.drools.modelo.Variavel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Termos Prolog em JSON (issue #6), igual ao json_termo/2 e ao termo_json/2 do wrapper.pl:
 * string = atomo, numero = numero, array = lista, {"f","args"} = {@link Termo}; uma {@link Variavel} sai com nome.
 */
final class TermosJson {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private TermosJson() {
    }

    /** JSON -> facto: {"predicado": nome, "args": [...]}. */
    static Facto facto(JsonNode j, String passo) {
        JsonNode p = j.get("predicado");
        JsonNode as = j.get("args");
        if (!j.isObject() || p == null || !p.isTextual() || as == null || !as.isArray()) {
            throw new PedidoInvalido("facto invalido no passo " + passo + " (tem de ter \"predicado\" e \"args\")");
        }
        List<Object> args = new ArrayList<>();
        for (JsonNode a : as) {
            args.add(termo(a, passo));
        }
        return new Facto(p.asText(), args);
    }

    /** JSON -> termo: objeto {"f","args"} -> Termo; array -> lista; string -> atomo; numero -> numero. */
    static Object termo(JsonNode n, String passo) {
        if (n.isObject()) {
            JsonNode f = n.get("f");
            JsonNode as = n.get("args");
            if (n.size() != 2 || f == null || !f.isTextual() || as == null || !as.isArray()) {
                throw new PedidoInvalido("termo invalido no passo " + passo
                        + ": um objeto tem de ser {\"f\": nome, \"args\": [...]}");
            }
            List<Object> args = new ArrayList<>();
            for (JsonNode a : as) {
                args.add(termo(a, passo));
            }
            return new Termo(f.asText(), args);
        }
        if (n.isArray()) {
            List<Object> l = new ArrayList<>();
            for (JsonNode a : n) {
                l.add(termo(a, passo));
            }
            return List.copyOf(l);
        }
        if (n.isIntegralNumber()) {
            return n.asLong();
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        // string, e tambem true/false/null, que o wrapper le como atomos
        return n.asText();
    }

    /** Termo -> JSON. As variaveis livres recebem nome em {@code nomes}, pela ordem em que aparecem. */
    static JsonNode json(Object t, NomesVariaveis nomes) {
        if (t instanceof String s) {
            return JSON.textNode(s);
        }
        if (t instanceof Long l) {
            return JSON.numberNode(l);
        }
        if (t instanceof Double d) {
            return JSON.numberNode(d);
        }
        if (t instanceof Variavel v) {
            return JSON.textNode(nomes.nome(v));
        }
        if (t instanceof List<?> l) {
            ArrayNode a = JSON.arrayNode();
            for (Object x : l) {
                a.add(json(x, nomes));
            }
            return a;
        }
        if (t instanceof Termo c) {
            ObjectNode o = JSON.objectNode();
            o.put("f", c.f());
            o.set("args", json(c.args(), nomes));
            return o;
        }
        throw new IllegalArgumentException("termo nao suportado: " + t);
    }

    static JsonNode json(Object t) {
        return json(t, new NomesVariaveis());
    }

    /** Nomes das variaveis livres de uma justificacao: A, B, ..., Z, A1, B1, ... (como o numbervars do SWI-Prolog). */
    static final class NomesVariaveis {

        private final Map<Variavel, String> nomes = new HashMap<>();

        String nome(Variavel v) {
            String n = nomes.get(v);
            if (n == null) {
                int i = nomes.size();
                n = (char) ('A' + i % 26) + (i >= 26 ? Integer.toString(i / 26) : "");
                nomes.put(v, n);
            }
            return n;
        }
    }
}
