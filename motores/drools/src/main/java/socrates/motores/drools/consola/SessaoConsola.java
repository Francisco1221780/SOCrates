package socrates.motores.drools.consola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import socrates.motores.drools.MotorDrools;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * O que a consola ja injetou: a lista de passos, por ordem. O motor nao guarda estado entre pedidos (README da raiz),
 * por isso cada acao junta passos e volta a enviar o pedido inteiro ao {@link MotorDrools#infere}, como o backend
 * faria. A resposta e a mesma que o backend e o comparador recebem.
 */
final class SessaoConsola {

    /** O que mudou numa corrida: os factos novos (pelo conteudo) ou o erro do motor. */
    record Mudancas(List<JsonNode> factosNovos, String erro) {
    }

    private final MotorDrools motor;
    private final ObjectMapper json;
    private final List<JsonNode> passos = new ArrayList<>();
    private JsonNode resposta;

    SessaoConsola(MotorDrools motor, ObjectMapper json) {
        this.motor = motor;
        this.json = json;
    }

    /** Volta ao estado inicial (so os dados fixos) e corre o motor. */
    Mudancas recomeca() {
        passos.clear();
        resposta = null;
        return corre();
    }

    /**
     * Junta os passos e corre o motor. Se o motor responder com um erro (ex.: um facto mal formado), os passos saem
     * outra vez, para um erro nao estragar a sessao.
     */
    Mudancas junta(List<JsonNode> novos) {
        int antes = passos.size();
        passos.addAll(novos);
        Mudancas m = corre();
        if (m.erro() != null) {
            passos.subList(antes, passos.size()).clear();
            corre();
        }
        return m;
    }

    Mudancas junta(String id, List<? extends JsonNode> factos) {
        ObjectNode passo = json.createObjectNode();
        passo.put("id", id);
        ArrayNode lista = passo.putArray("factos");
        factos.forEach(lista::add);
        return junta(List.of(passo));
    }

    /** O pedido com os passos todos. Sem passos, um passo vazio (o pedido tem de ter pelo menos um). */
    String pedido() {
        ObjectNode pedido = json.createObjectNode();
        pedido.put("versaoKB", MotorDrools.VERSAO_KB);
        ArrayNode lista = pedido.putArray("passos");
        if (passos.isEmpty()) {
            lista.addObject().put("id", "estado_inicial").putArray("factos");
        } else {
            passos.forEach(lista::add);
        }
        return pedido.toString();
    }

    /** Corre outra vez o pedido inteiro. */
    Mudancas corre() {
        Set<String> antes = chaves(resposta);
        JsonNode r;
        try {
            r = json.readTree(motor.infere(pedido()));
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        if ("erro".equals(r.path("estado").asText())) {
            return new Mudancas(List.of(), r.path("erro").asText());
        }
        boolean primeira = resposta == null;
        resposta = r;
        List<JsonNode> novos = new ArrayList<>();
        if (!primeira) {
            for (JsonNode f : r.get("factos")) {
                if (!antes.contains(chave(f))) {
                    novos.add(f);
                }
            }
        }
        return new Mudancas(novos, null);
    }

    private static Set<String> chaves(JsonNode resposta) {
        Set<String> r = new HashSet<>();
        if (resposta != null) {
            resposta.get("factos").forEach(f -> r.add(chave(f)));
        }
        return r;
    }

    static String chave(JsonNode facto) {
        return facto.get("predicado").asText() + facto.get("args").toString();
    }

    /** A ultima resposta do motor (nunca null depois do primeiro {@link #corre}). */
    JsonNode resposta() {
        return resposta;
    }

    List<String> idsDosPassos() {
        List<String> r = new ArrayList<>();
        passos.forEach(p -> r.add(p.path("id").asText()));
        return r;
    }
}
