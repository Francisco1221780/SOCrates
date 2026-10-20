package cyberrangers.motores.drools.explicacao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * O "como?", o "porque?" e o "porque?" de uma pergunta montados so a partir da resposta de um motor (o JSON do
 * infere, com {@code factos} e {@code perguntas}), sem correr nada. Serve os dois motores: a resposta do wrapper.pl tem
 * o mesmo formato que a do Drools (ParidadeTest). Entra e sai em texto JSON, para quem chama nao depender do Jackson
 * deste modulo.
 *
 * <p>As respostas tem o {@code motor} e a {@code versaoKB} da resposta explicada. Uma resposta de erro sai tal como
 * veio; uma resposta de outra versaoKB que a do regras.json da erro, porque as regras nao seriam as que a produziram.
 */
public final class Explicacao {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Explicacao() {
    }

    /**
     * A arvore de uma conclusao da resposta (o {@link Arvore}). O facto escolhe-se por {@code {"numero": N}} ou por
     * {@code {"predicado", "args"}} (o primeiro que existir). Com {@code comRegras} e o "porque?": cada no concluido
     * traz a regra completa. A resposta e {@code {estado, motor, versaoKB, facto, arvore, erro}}.
     * @param resposta
     * @param facto
     * @param comRegras
     * @return
     */
    public static String como(String resposta, String facto, boolean comRegras) {
        JsonNode r = null;
        try {
            r = JSON.readTree(resposta);
            String erro = verifica(r);
            if (erro != null) {
                return erro;
            }
            JsonNode f = procuraFacto(r.get("factos"), JSON.readTree(facto));
            if (f == null) {
                return erro(r, "esse facto nao esta na memoria de trabalho (experimente o porque nao)");
            }
            ObjectNode o = envelope(r);
            o.set("facto", f);
            o.set("arvore", Arvore.de(r.get("factos"), f.get("numero").asInt(), comRegras));
            o.putNull("erro");
            return o.toString();
        } catch (JsonProcessingException e) {
            return erro(r, "JSON invalido: " + e.getOriginalMessage());
        } catch (RuntimeException e) {
            return erro(r, "erro na explicacao: " + e.getMessage());
        }
    }

    /**
     * O "porque?" de uma pergunta ao analista: a regra que a gerou, escrita por inteiro, com os factos que a levaram
     * ({@code geradaPor}, null se a pergunta foi introduzida a mao), e as regras que usam a resposta. A pergunta e a da
     * resposta, por ativo, evento e pergunta. A resposta e
     * {@code {estado, motor, versaoKB, pergunta, geradaPor, regrasQueUsamAResposta, erro}}.
     * @param resposta
     * @param ativo
     * @param evento
     * @param pergunta
     * @return
     */
    public static String porquePergunta(String resposta, String ativo, String evento, String pergunta) {
        JsonNode r = null;
        try {
            r = JSON.readTree(resposta);
            String erro = verifica(r);
            if (erro != null) {
                return erro;
            }
            JsonNode p = null;
            for (JsonNode x : r.get("perguntas")) {
                if (x.get("ativo").asText().equals(ativo) && x.get("evento").asText().equals(evento)
                        && x.get("pergunta").asText().equals(pergunta)) {
                    p = x;
                }
            }
            if (p == null) {
                return erro(r, "essa pergunta nao esta por responder");
            }
            ObjectNode o = envelope(r);
            o.set("pergunta", p);
            ObjectNode gerada = Arvore.de(r.get("factos"), p.get("facto").asInt(), true);
            if (gerada == null || gerada.get("regra").isNull()) {
                o.putNull("geradaPor");
            } else {
                o.set("geradaPor", gerada);
            }
            ObjectNode termo = JSON.createObjectNode().put("f", "resposta_analista");
            termo.putArray("args").add(JSON.createObjectNode().put("var", "_"))
                    .add(JSON.createObjectNode().put("var", "_")).add(pergunta)
                    .add(JSON.createObjectNode().put("var", "_"));
            o.set("regrasQueUsamAResposta", JSON.valueToTree(PorqueNao.regrasComCondicao(termo)));
            o.putNull("erro");
            return o.toString();
        } catch (JsonProcessingException e) {
            return erro(r, "JSON invalido: " + e.getOriginalMessage());
        } catch (RuntimeException e) {
            return erro(r, "erro na explicacao: " + e.getMessage());
        }
    }

    /**
     * A versaoKB das regras com que se explica (a do regras.json).
     * @return
     */
    public static String versaoRegras() {
        return PorqueNao.regras().versaoKB();
    }

    /**
     * Null se a resposta se pode explicar; senao, o que se devolve: a propria resposta se for um erro do motor, ou um
     * erro se nao for uma resposta do infere ou for de outra versaoKB que a das regras.
     * @param r
     * @return
     */
    private static String verifica(JsonNode r) {
        if (r == null || !r.isObject()) {
            return erro(null, "a resposta do motor nao e um objeto JSON");
        }
        if (r.path("estado").asText().equals("erro")) {
            return r.toString();
        }
        if (!r.path("factos").isArray() || !r.path("perguntas").isArray()) {
            return erro(r, "a resposta do motor nao tem factos e perguntas");
        }
        String versao = r.path("versaoKB").asText();
        if (!versao.equals(versaoRegras())) {
            return erro(r, "a resposta e da versaoKB " + versao + " e as regras das explicacoes da "
                    + versaoRegras());
        }
        return null;
    }

    /**
     * O facto da resposta que o JSON escolhe, por {@code numero} ou por {@code predicado} e {@code args}; null se nao
     * ha.
     * @param factos
     * @param escolha
     * @return
     */
    private static JsonNode procuraFacto(JsonNode factos, JsonNode escolha) {
        for (JsonNode f : factos) {
            if (escolha.has("numero") ? f.get("numero").asInt() == escolha.get("numero").asInt()
                    : f.get("predicado").equals(escolha.path("predicado")) && f.get("args").equals(escolha.path("args"))) {
                return f;
            }
        }
        return null;
    }

    /**
     * O inicio de uma resposta concluida, com o motor e a versaoKB da resposta explicada.
     * @param r
     * @return
     */
    private static ObjectNode envelope(JsonNode r) {
        ObjectNode o = JSON.createObjectNode();
        o.put("estado", "concluido");
        o.set("motor", r.get("motor"));
        o.set("versaoKB", r.get("versaoKB"));
        return o;
    }

    /**
     * Uma resposta de erro, com o motor e a versaoKB da resposta explicada (null se nao se leu).
     * @param r
     * @param mensagem
     * @return
     */
    private static String erro(JsonNode r, String mensagem) {
        ObjectNode o = JSON.createObjectNode();
        o.put("estado", "erro");
        o.set("motor", r == null || !r.isObject() ? null : r.get("motor"));
        o.set("versaoKB", r == null || !r.isObject() ? null : r.get("versaoKB"));
        o.put("erro", mensagem);
        return o.toString();
    }
}
