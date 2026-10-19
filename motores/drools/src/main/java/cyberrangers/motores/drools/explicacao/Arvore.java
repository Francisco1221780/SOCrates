package cyberrangers.motores.drools.explicacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A arvore de uma conclusao, a partir dos factos da resposta do motor (cada um com {@code regra} e {@code premissas}):
 * e o "como?" e, com as regras, o "porque?". Cada no e
 * {@code {numero, predicado, args, regra, jaExplicado, premissas, regraCompleta?}} e cada premissa e
 * {@code {tipo: "facto", no}}, {@code {tipo: "nao", termo}} ou {@code {tipo: "calculada", termo}}. Um facto que ja foi
 * explicado antes na mesma arvore fica com {@code jaExplicado: true} e sem premissas (como o "ja explicado acima" do
 * explica_facto/2 do Prolog); um dado de entrada tem {@code regra: null}.
 */
public final class Arvore {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Map<Integer, JsonNode> porNumero = new HashMap<>();
    private final Set<Integer> explicados = new HashSet<>();
    private final boolean comRegras;

    private Arvore(JsonNode factos, boolean comRegras) {
        factos.forEach(f -> porNumero.put(f.get("numero").asInt(), f));
        this.comRegras = comRegras;
    }

    /**
     * O no do facto {@code numero} e tudo o que o levou, sobre os {@code factos} da resposta do motor. Com
     * {@code comRegras}, cada no concluido por uma regra traz tambem {@code regraCompleta} (a regra do soc_regras.txt,
     * com os nomes das variaveis). Null se nao ha esse facto.
     */
    public static ObjectNode de(JsonNode factos, int numero, boolean comRegras) {
        Arvore a = new Arvore(factos, comRegras);
        JsonNode f = a.porNumero.get(numero);
        return f == null ? null : a.no(f);
    }

    private ObjectNode no(JsonNode f) {
        int numero = f.get("numero").asInt();
        ObjectNode n = JSON.objectNode();
        n.put("numero", numero);
        n.set("predicado", f.get("predicado"));
        n.set("args", f.get("args"));
        n.set("regra", f.get("regra"));
        boolean repetido = !explicados.add(numero);
        n.put("jaExplicado", repetido);
        ArrayNode premissas = n.putArray("premissas");
        if (repetido || f.get("regra").isNull()) {
            return n;
        }
        if (comRegras) {
            n.set("regraCompleta", PorqueNao.regra(f.get("regra").asInt()));
        }
        for (JsonNode p : f.get("premissas")) {
            ObjectNode o = premissas.addObject();
            if (p.isInt()) {
                o.put("tipo", "facto");
                o.set("no", no(porNumero.get(p.asInt())));
            } else if (p.isObject() && "nao".equals(p.path("f").asText()) && p.get("args").size() == 1) {
                o.put("tipo", "nao");
                o.set("termo", p.get("args").get(0));
            } else {
                o.put("tipo", "calculada");
                o.set("termo", p);
            }
        }
        return n;
    }
}
