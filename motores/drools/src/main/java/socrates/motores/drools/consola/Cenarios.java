package socrates.motores.drools.consola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Os cenarios da base, do cenarios.json que o gera_json.pl escreve no modulo (igual ao do backend): cada um com o
 * passo pronto a enviar, o grupo, as dependencias e as alternativas, e as sequencias do soc_cenarios.pl.
 */
final class Cenarios {

    static final String RECURSO = "/cenarios.json";

    record Cenario(String id, String grupo, String descricao, String ficheiro, List<String> depende,
            List<String> alternativas, JsonNode passo) {
    }

    private final Map<String, Cenario> porId = new LinkedHashMap<>();
    private final Map<String, List<String>> sequencias = new LinkedHashMap<>();

    private Cenarios(JsonNode json) {
        for (JsonNode c : json.get("cenarios")) {
            porId.put(c.get("id").asText(), new Cenario(c.get("id").asText(), c.get("grupo").asText(),
                    c.get("descricao").asText(), c.get("ficheiro").asText(), textos(c.get("depende")),
                    textos(c.get("alternativas")), c.get("passo")));
        }
        json.get("sequencias").fields().forEachRemaining(e -> sequencias.put(e.getKey(), textos(e.getValue())));
    }

    /** Os cenarios do {@code cenarios.json} (o {@code MotorDrools.cenarios()}). */
    static Cenarios de(JsonNode json) {
        return new Cenarios(json);
    }

    static Cenarios carrega(ObjectMapper json) {
        try (InputStream in = Cenarios.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("nao existe " + RECURSO.substring(1) + " no classpath (gera_json.pl)");
            }
            return new Cenarios(json.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> textos(JsonNode lista) {
        List<String> r = new ArrayList<>();
        if (lista != null) {
            lista.forEach(x -> r.add(x.asText()));
        }
        return r;
    }

    /** Pela ordem do indice. */
    List<Cenario> todos() {
        return List.copyOf(porId.values());
    }

    Cenario cenario(String id) {
        return porId.get(id);
    }

    List<String> sequencia(String nome) {
        return sequencias.getOrDefault(nome, Collections.emptyList());
    }
}
