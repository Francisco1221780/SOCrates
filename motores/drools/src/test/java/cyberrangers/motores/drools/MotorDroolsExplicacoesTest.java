package cyberrangers.motores.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** O "como?", o "porque?", o "porque?" de uma pergunta, os cenarios e os predicados, como os chama quem usa a API. */
class MotorDroolsExplicacoesTest {

    private static final MotorDrools MOTOR = new MotorDrools();
    private final ObjectMapper json = new ObjectMapper();

    /** Um pedido com os passos dos cenarios pedidos, do cenarios.json do modulo. */
    private String pedido(String... ids) throws Exception {
        JsonNode cenarios = json.readTree(MOTOR.cenarios());
        ObjectNode p = json.createObjectNode().put("versaoKB", MotorDrools.VERSAO_KB);
        for (String id : ids) {
            for (JsonNode c : cenarios.get("cenarios")) {
                if (c.get("id").asText().equals(id)) {
                    p.withArray("passos").add(c.get("passo"));
                }
            }
        }
        return p.toString();
    }

    private static final String INCIDENTE = "{\"predicado\":\"incidente\",\"args\":[{\"f\":\"inc\",\"args\":"
            + "[\"versao_por_confirmar\",\"ev_f1\"]},\"versao_por_confirmar\",\"web_srv_05\",[\"web_srv_05\"]]}";

    @Test
    void comoDevolveAArvoreDeUmaConclusao() throws Exception {
        JsonNode r = json.readTree(MOTOR.como(pedido("c6"), INCIDENTE));
        assertEquals("concluido", r.get("estado").asText());
        assertEquals("drools", r.get("motor").asText());
        JsonNode raiz = r.get("arvore");
        assertEquals(7, raiz.get("regra").asInt());
        assertEquals("incidente", raiz.get("predicado").asText());
        assertFalse(raiz.has("regraCompleta"), "o como nao leva as regras");
        JsonNode premissas = raiz.get("premissas");
        assertEquals("facto", premissas.get(0).get("tipo").asText());
        assertEquals(4, premissas.get(0).get("no").get("regra").asInt());
        assertEquals("nao", premissas.get(1).get("tipo").asText());
        assertEquals("versao", premissas.get(1).get("termo").get("f").asText());
    }

    @Test
    void porqueLevaARegraCompletaEmCadaNoConcluido() throws Exception {
        JsonNode r = json.readTree(MOTOR.porque(pedido("c6"), INCIDENTE));
        JsonNode raiz = r.get("arvore");
        JsonNode regra = raiz.get("regraCompleta");
        assertEquals(7, regra.get("id").asInt());
        assertEquals("vetor_aberto", regra.get("se").get(0).get("f").asText());
        assertEquals("Ev", regra.get("se").get(0).get("args").get(0).get("var").asText());
        assertTrue(regra.get("se").get(1).has("nao"));
        assertEquals(4, raiz.get("premissas").get(0).get("no").get("regraCompleta").get("id").asInt());
    }

    @Test
    void factoPeloNumeroEDadoDeEntrada() throws Exception {
        JsonNode como = json.readTree(MOTOR.como(pedido("c6"), INCIDENTE));
        int numero = como.get("facto").get("numero").asInt();
        assertEquals(como.get("arvore"), json.readTree(MOTOR.como(pedido("c6"), "{\"numero\":" + numero + "}")).get("arvore"));
        JsonNode dado = json.readTree(MOTOR.como(pedido("c6"), "{\"numero\":1}")).get("arvore");
        assertTrue(dado.get("regra").isNull());
        assertEquals(0, dado.get("premissas").size());
    }

    @Test
    void umNoJaExplicadoNaoRepeteAsPremissas() throws Exception {
        String pedido = pedido("c1", "c2", "c3", "c6", "c7");
        JsonNode resposta = json.readTree(MOTOR.infere(pedido));
        int conferidos = 0;
        for (JsonNode f : resposta.get("factos")) {
            if (f.get("regra").isNull()) {
                continue;
            }
            JsonNode r = json.readTree(MOTOR.como(pedido, "{\"numero\":" + f.get("numero").asInt() + "}"));
            for (JsonNode n : nos(r.get("arvore"))) {
                if (n.get("jaExplicado").asBoolean()) {
                    assertEquals(0, n.get("premissas").size());
                    conferidos++;
                }
            }
        }
        assertTrue(conferidos > 0, "algum facto tem de aparecer duas vezes na mesma arvore");
    }

    private static List<JsonNode> nos(JsonNode no) {
        List<JsonNode> r = new java.util.ArrayList<>(List.of(no));
        no.get("premissas").forEach(p -> {
            if (p.get("tipo").asText().equals("facto")) {
                r.addAll(nos(p.get("no")));
            }
        });
        return r;
    }

    @Test
    void erros() throws Exception {
        assertEquals("erro", json.readTree(MOTOR.como(pedido("c6"), "{\"numero\":999999}")).get("estado").asText());
        assertEquals("erro", json.readTree(MOTOR.porque(pedido("c6"),
                "{\"predicado\":\"tempo\",\"args\":[\"ev_nao_existe\",1]}")).get("estado").asText());
        assertEquals("erro", json.readTree(MOTOR.como("{\"passos\":[]}", "{\"numero\":1}")).get("estado").asText());
        assertEquals("erro", json.readTree(MOTOR.como(pedido("c6"), "isto nao e json")).get("estado").asText());
    }

    @Test
    void porquePerguntaDizAReGraQueAGerouEAsQueUsamAResposta() throws Exception {
        JsonNode r = json.readTree(MOTOR.porquePergunta(pedido("c7"), "web_srv_04", "ev_e1", "shell_servico"));
        assertEquals("concluido", r.get("estado").asText());
        assertEquals(31, r.get("geradaPor").get("regraCompleta").get("id").asInt());
        assertEquals(json.readTree("[32,33,34,50,51]"), r.get("regrasQueUsamAResposta"));
        assertEquals("shell_servico", r.get("pergunta").get("pergunta").asText());
        JsonNode inexistente = json.readTree(MOTOR.porquePergunta(pedido("c7"), "web_srv_04", "ev_e1", "outra"));
        assertEquals("erro", inexistente.get("estado").asText());
    }

    @Test
    void cenariosEPredicados() throws Exception {
        JsonNode c = json.readTree(MOTOR.cenarios());
        assertTrue(c.get("cenarios").size() > 60, "os cenarios da base");
        assertEquals("c6", c.get("cenarios").get(5).get("id").asText());
        assertNotNull(c.get("cenarios").get(5).get("passo").get("factos"));
        assertTrue(c.get("sequencias").has("base") && c.get("sequencias").has("normal"));
        JsonNode p = json.readTree(MOTOR.predicadosConcluidos());
        List<String> nomes = new java.util.ArrayList<>();
        p.get("predicados").forEach(x -> nomes.add(x.asText()));
        assertTrue(nomes.contains("ataque_dirigido") && nomes.contains("incidente"), nomes.toString());
    }
}
