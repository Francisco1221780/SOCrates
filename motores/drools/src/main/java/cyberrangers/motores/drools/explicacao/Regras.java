package cyberrangers.motores.drools.explicacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * As regras do soc_regras.txt como dados (o regras.json que o gera_json.pl escreve no modulo) e a lista das condicoes
 * calculadas do soc_suporte.txt. Servem o "porque nao?": o Drools corre as regras compiladas do DRL, mas para explicar
 * porque uma regra nao disparou e preciso o "se" e o "entao" da regra como termos.
 */
final class Regras {

    static final String RECURSO = "/regras.json";

    /** Uma condicao do "se": um facto (ou condicao calculada), {@code nao X} ou {@code avalia(X)}. */
    sealed interface Condicao permits Positiva, Nao, Avalia {
        Object termo();
    }

    record Positiva(Object termo) implements Condicao {
    }

    record Nao(Object termo) implements Condicao {
    }

    record Avalia(Object termo) implements Condicao {
    }

    /** Uma regra com variaveis novas (cada {@link Regra#instancia} da variaveis diferentes, como uma clausula). */
    record Instancia(int id, List<Condicao> se, List<Object> entao) {
    }

    record Regra(int id, JsonNode se, JsonNode entao) {
        Instancia instancia() {
            Map<String, Var> nomes = new HashMap<>();
            List<Condicao> cs = new ArrayList<>();
            for (JsonNode c : se) {
                if (c.has("nao")) {
                    cs.add(new Nao(Unificacao.deJson(c.get("nao"), nomes)));
                } else if (c.has("avalia")) {
                    cs.add(new Avalia(Unificacao.deJson(c.get("avalia"), nomes)));
                } else {
                    cs.add(new Positiva(Unificacao.deJson(c, nomes)));
                }
            }
            List<Object> rs = new ArrayList<>();
            entao.forEach(r -> rs.add(Unificacao.deJson(r, nomes)));
            return new Instancia(id, cs, rs);
        }
    }

    private final List<Regra> regras = new ArrayList<>();
    private final Set<String> calculadas = new HashSet<>();

    private Regras(JsonNode json) {
        json.get("regras").forEach(r -> regras.add(new Regra(r.get("id").asInt(), r.get("se"), r.get("entao"))));
        json.get("calculadas").forEach(c -> calculadas.add(c.asText()));
    }

    static Regras carrega() {
        try (InputStream in = Regras.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("nao existe " + RECURSO.substring(1) + " no classpath (gera_json.pl)");
            }
            return new Regras(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Pela ordem do soc_regras.txt (a ordem em que o Prolog as encontra). */
    List<Regra> todas() {
        return regras;
    }

    /** Se o termo e uma condicao calculada (o condicao_calculada/1 do soc_suporte.txt, por nome e aridade). */
    boolean calculada(Object termo) {
        String f = Unificacao.functor(termo);
        return f != null && calculadas.contains(f);
    }
}
