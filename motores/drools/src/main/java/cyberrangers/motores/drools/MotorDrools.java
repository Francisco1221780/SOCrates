package cyberrangers.motores.drools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

/**
 * Motor Drools: recebe o pedido JSON (lista de passos), corre cada passo numa KieSession nova
 * e devolve a resposta JSON. Mesmo contrato do wrapper Prolog.
 */
public class MotorDrools {

    public static final String VERSAO_KB = "5.1";

    private static final String SESSAO = "sessao";

    private final ObjectMapper json = new ObjectMapper();
    private final KieContainer container = KieServices.Factory.get().getKieClasspathContainer();

    public String infere(String pedidoJson) {
        try {
            JsonNode pedido = json.readTree(pedidoJson);
            String versao = pedido.path("versaoKB").asText("");
            if (!VERSAO_KB.equals(versao)) {
                return erro("versaoKB desconhecida: \"" + versao + "\" (carregada: " + VERSAO_KB + ")");
            }
            JsonNode passos = pedido.path("passos");
            if (!passos.isArray() || passos.isEmpty()) {
                return erro("o pedido tem de ter pelo menos um passo");
            }
            return corre(passos);
        } catch (JsonProcessingException e) {
            return erro("pedido JSON invalido: " + e.getOriginalMessage());
        } catch (RuntimeException e) {
            return erro("erro do motor: " + e.getMessage());
        }
    }

    private String corre(JsonNode passos) throws JsonProcessingException {
        KieSession sessao = container.newKieSession(SESSAO);
        try {
            for (JsonNode passo : passos) {
                for (JsonNode f : passo.path("factos")) {
                    List<Object> args = new ArrayList<>();
                    for (JsonNode a : f.path("args")) {
                        args.add(json.treeToValue(a, Object.class));
                    }
                    sessao.insert(new Facto(f.path("predicado").asText(), args));
                }
                sessao.fireAllRules();
            }
            ObjectNode resposta = json.createObjectNode();
            resposta.put("estado", "concluido");
            resposta.put("motor", "drools");
            resposta.put("versaoKB", VERSAO_KB);
            ArrayNode factos = resposta.putArray("factos");
            for (Object o : sessao.getObjects(x -> x instanceof Facto)) {
                factos.add(json.valueToTree(o));
            }
            resposta.putNull("erro");
            return json.writeValueAsString(resposta);
        } finally {
            sessao.dispose();
        }
    }

    private String erro(String mensagem) {
        ObjectNode resposta = json.createObjectNode();
        resposta.put("estado", "erro");
        resposta.put("motor", "drools");
        resposta.put("versaoKB", VERSAO_KB);
        resposta.put("erro", mensagem);
        return resposta.toString();
    }
}
