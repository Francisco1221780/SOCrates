package cyberrangers.motores.drools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * Compara duas respostas no formato do wrapper.pl pelo conteudo dos factos, nunca pelos numeros nem pela ordem
 * (README da raiz). Para cada facto: se existe nos dois lados, a origem, o passo e, nos concluidos, a regra e as
 * premissas. Uma premissa que aponta para um facto compara-se pelo conteudo desse facto.
 */
final class ComparaRespostas {

    /** Uma diferenca: o facto (como texto JSON), a regra que o concluiu num dos lados (ou null) e a descricao. */
    record Diferenca(String facto, Integer regra, String descricao) {
        @Override
        public String toString() {
            return descricao;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private ComparaRespostas() {
    }

    static List<Diferenca> compara(JsonNode prolog, JsonNode drools) {
        List<Diferenca> r = new ArrayList<>();
        if (!prolog.path("estado").equals(drools.path("estado"))) {
            r.add(new Diferenca(null, null, "estado: prolog " + prolog.path("estado") + ", drools " + drools.path("estado")
                    + (drools.path("erro").isNull() ? "" : " (" + drools.path("erro").asText() + ")")));
            if (!drools.has("factos")) {
                return r;
            }
        }
        Map<String, ObjectNode> p = canonicos(prolog);
        Map<String, ObjectNode> d = canonicos(drools);
        for (Map.Entry<String, ObjectNode> e : p.entrySet()) {
            ObjectNode fd = d.get(e.getKey());
            ObjectNode fp = e.getValue();
            if (fd == null) {
                r.add(new Diferenca(e.getKey(), regra(fp), "so no prolog: " + e.getKey() + " " + justificacao(fp)));
                continue;
            }
            for (String campo : List.of("origem", "passo", "regra", "premissas")) {
                if (!fp.path(campo).equals(fd.path(campo))) {
                    r.add(new Diferenca(e.getKey(), regra(fp) != null ? regra(fp) : regra(fd),
                            campo + " diferente em " + e.getKey() + ": prolog " + fp.path(campo)
                                    + ", drools " + fd.path(campo)));
                }
            }
        }
        for (Map.Entry<String, ObjectNode> e : d.entrySet()) {
            if (!p.containsKey(e.getKey())) {
                r.add(new Diferenca(e.getKey(), regra(e.getValue()),
                        "so no drools: " + e.getKey() + " " + justificacao(e.getValue())));
            }
        }
        return r;
    }

    /**
     * A causa de raiz de cada diferenca: segue-se as premissas (dos dois lados) ate aos primeiros factos diferentes
     * cujas premissas ja sao iguais, e a causa e a regra que concluiu esses factos. Ex.: a gravidade de um incidente que
     * so existe no Drools tem como causa a regra que criou o incidente a mais, nao a R76. No mapa so ficam as
     * diferencas cuja causa de raiz e uma regra que {@code daOutraMetade} aceita (no nivel completo, as regras H-M).
     * As diferencas sem facto (estado) nunca ficam no mapa.
     */
    static Map<Diferenca, Set<Integer>> causasNaOutraMetade(List<Diferenca> diferencas, JsonNode prolog, JsonNode drools,
            IntPredicate daOutraMetade) {
        Map<String, ObjectNode> p = canonicos(prolog);
        Map<String, ObjectNode> d = canonicos(drools);
        Set<String> diferentes = new HashSet<>();
        diferencas.forEach(dif -> {
            if (dif.facto() != null) {
                diferentes.add(dif.facto());
            }
        });
        Map<String, Set<Integer>> raizes = new HashMap<>();
        Map<Diferenca, Set<Integer>> r = new LinkedHashMap<>();
        for (Diferenca dif : diferencas) {
            if (dif.facto() == null) {
                continue;
            }
            Set<Integer> c = raiz(dif.facto(), p, d, diferentes, raizes, new HashSet<>());
            if (!c.isEmpty() && c.stream().allMatch(daOutraMetade::test)) {
                r.put(dif, c);
            }
        }
        return r;
    }

    private static Set<Integer> raiz(String facto, Map<String, ObjectNode> p, Map<String, ObjectNode> d,
            Set<String> diferentes, Map<String, Set<Integer>> raizes, Set<String> aVisitar) {
        if (raizes.containsKey(facto)) {
            return raizes.get(facto);
        }
        Set<Integer> c = new TreeSet<>();
        if (aVisitar.add(facto)) {
            for (Map<String, ObjectNode> lado : List.of(p, d)) {
                ObjectNode f = lado.get(facto);
                if (f == null) {
                    continue;
                }
                for (JsonNode premissa : f.get("premissas")) {
                    if (premissa.isTextual() && diferentes.contains(premissa.asText())) {
                        c.addAll(raiz(premissa.asText(), p, d, diferentes, raizes, aVisitar));
                    }
                }
            }
            if (c.isEmpty()) {
                for (Map<String, ObjectNode> lado : List.of(p, d)) {
                    Integer regra = lado.containsKey(facto) ? regra(lado.get(facto)) : null;
                    if (regra != null) {
                        c.add(regra);
                    }
                }
            }
        }
        raizes.put(facto, c);
        return c;
    }

    private static Integer regra(ObjectNode f) {
        return f.path("regra").isInt() ? f.get("regra").asInt() : null;
    }

    private static String justificacao(ObjectNode f) {
        return "(" + f.path("origem").asText() + ", passo " + f.path("passo")
                + (f.path("regra").isNull() ? "" : ", R" + f.path("regra") + " " + f.path("premissas")) + ")";
    }

    /** Facto (texto JSON de predicado + args) -> origem, passo, regra e premissas com os numeros trocados pelos factos. */
    private static Map<String, ObjectNode> canonicos(JsonNode resposta) {
        Map<Integer, String> porNumero = new HashMap<>();
        for (JsonNode f : resposta.path("factos")) {
            porNumero.put(f.get("numero").asInt(), chave(f));
        }
        Map<String, ObjectNode> r = new LinkedHashMap<>();
        for (JsonNode f : resposta.path("factos")) {
            ObjectNode c = JsonNodeFactory.instance.objectNode();
            c.set("origem", f.get("origem"));
            c.set("passo", f.get("passo"));
            c.set("regra", f.get("regra"));
            ArrayNode premissas = c.putArray("premissas");
            for (JsonNode pr : f.path("premissas")) {
                if (pr.isInt()) {
                    String alvo = porNumero.get(pr.asInt());
                    premissas.add(alvo == null ? "facto #" + pr.asInt() + " que nao existe" : alvo);
                } else {
                    premissas.add(pr);
                }
            }
            r.put(chave(f), c);
        }
        return r;
    }

    private static String chave(JsonNode f) {
        ObjectNode k = JsonNodeFactory.instance.objectNode();
        k.set("predicado", f.get("predicado"));
        k.set("args", f.get("args"));
        try {
            return JSON.writeValueAsString(k);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
