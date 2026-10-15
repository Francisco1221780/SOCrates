package cyberrangers.motores.drools.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Premissa;
import cyberrangers.motores.drools.modelo.Termo;
import cyberrangers.motores.drools.sessao.BaseFactos;
import cyberrangers.motores.drools.sessao.Registo;
import java.util.List;
import java.util.Objects;

/**
 * A resposta no formato do wrapper.pl (monta_resposta/2), campo a campo; so o "motor" muda. As listas vao por ordem
 * do numero do facto, para o mesmo pedido dar sempre a mesma resposta.
 */
public final class Resposta {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private Resposta() {
    }

    public static ObjectNode monta(BaseFactos base, String motor, String versaoKB) {
        List<Facto> factos = base.factos();
        ArrayNode perguntas = perguntas(base, factos);
        ObjectNode r = JSON.objectNode();
        r.put("estado", perguntas.isEmpty() ? "concluido" : "aguarda_resposta");
        r.put("motor", motor);
        r.put("versaoKB", versaoKB);
        r.set("perguntas", perguntas);
        r.set("incidentes", incidentes(base, factos));
        r.set("tickets", tickets(base, factos));
        ArrayNode todos = r.putArray("factos");
        for (Facto f : factos) {
            todos.add(facto(base, f));
        }
        r.putNull("erro");
        return r;
    }

    public static ObjectNode erro(String motor, String versaoKB, String mensagem) {
        ObjectNode r = JSON.objectNode();
        r.put("estado", "erro");
        r.put("motor", motor);
        r.put("versaoKB", versaoKB);
        r.put("erro", mensagem);
        return r;
    }

    /** Perguntas ao analista sem resposta e sem pergunta_retirada, com o texto e as opcoes que estao na base. */
    private static ArrayNode perguntas(BaseFactos base, List<Facto> factos) {
        ArrayNode l = JSON.arrayNode();
        for (Facto f : factos) {
            if (!f.getFunctor().equals("pergunta_analista/3")) {
                continue;
            }
            Object ativo = f.arg(0);
            Object evento = f.arg(1);
            Object pergunta = f.arg(2);
            boolean respondida = factos.stream().anyMatch(x -> x.getFunctor().equals("resposta_analista/4")
                    && x.arg(0).equals(ativo) && x.arg(1).equals(evento) && x.arg(2).equals(pergunta));
            boolean retirada = factos.stream().anyMatch(x -> x.getFunctor().equals("pergunta_retirada/3")
                    && x.getArgs().equals(f.getArgs()));
            if (respondida || retirada) {
                continue;
            }
            ObjectNode o = l.addObject();
            o.put("facto", base.registo(f).numero());
            o.set("ativo", TermosJson.json(ativo));
            o.set("evento", TermosJson.json(evento));
            o.set("pergunta", TermosJson.json(pergunta));
            o.set("texto", factos.stream()
                    .filter(x -> x.getFunctor().equals("texto_pergunta/2") && x.arg(0).equals(pergunta))
                    .findFirst().map(x -> TermosJson.json(x.arg(1))).orElse(JSON.nullNode()));
            ArrayNode opcoes = o.putArray("opcoes");
            factos.stream()
                    .filter(x -> x.getFunctor().equals("opcao_resposta/3") && x.arg(0).equals(pergunta))
                    .forEach(x -> {
                        ObjectNode op = opcoes.addObject();
                        op.set("valor", TermosJson.json(x.arg(1)));
                        op.set("texto", TermosJson.json(x.arg(2)));
                    });
        }
        return l;
    }

    private static ArrayNode incidentes(BaseFactos base, List<Facto> factos) {
        ArrayNode l = JSON.arrayNode();
        for (Facto f : factos) {
            if (f.getFunctor().equals("incidente/4")) {
                ObjectNode o = l.addObject();
                o.put("facto", base.registo(f).numero());
                o.set("id", TermosJson.json(f.arg(0)));
                o.set("tipo", TermosJson.json(f.arg(1)));
                o.set("ativo", TermosJson.json(f.arg(2)));
                o.set("impactados", TermosJson.json(f.arg(3)));
            }
        }
        return l;
    }

    /** ticket(Incidente, Ativo, AcaoPedida, Gravidade, Prioridade, AtivosImpactados); fechado: o motivo, ou null. */
    private static ArrayNode tickets(BaseFactos base, List<Facto> factos) {
        ArrayNode l = JSON.arrayNode();
        for (Facto f : factos) {
            if (f.getFunctor().equals("ticket/6")) {
                Object inc = f.arg(0);
                ObjectNode o = l.addObject();
                o.put("facto", base.registo(f).numero());
                o.set("incidente", TermosJson.json(inc));
                o.set("ativo", TermosJson.json(f.arg(1)));
                o.set("acao", TermosJson.json(f.arg(2)));
                o.set("gravidade", TermosJson.json(f.arg(3)));
                o.set("prioridade", TermosJson.json(f.arg(4)));
                o.set("impactados", TermosJson.json(f.arg(5)));
                o.set("fechado", factos.stream()
                        .filter(x -> x.getFunctor().equals("ticket_fechado/2") && x.arg(0).equals(inc))
                        .findFirst().map(x -> TermosJson.json(x.arg(1))).orElse(JSON.nullNode()));
            }
        }
        return l;
    }

    /** Um facto com a origem; os concluidos levam a regra e as premissas (facto_json/4 do wrapper). */
    private static ObjectNode facto(BaseFactos base, Facto f) {
        Registo r = base.registo(f);
        ObjectNode o = JSON.objectNode();
        o.put("numero", r.numero());
        o.put("predicado", f.getPredicado());
        o.set("args", TermosJson.json(f.getArgs()));
        o.put("origem", r.origem().json());
        o.put("passo", r.passo());
        o.put("regra", r.regra());
        o.set("premissas", premissas(base, r.premissas()));
        return o;
    }

    /** Facto -> numero; nao X -> {"f":"nao","args":[X]}; condicao calculada -> o termo. Variaveis: A, B, ... */
    static ArrayNode premissas(BaseFactos base, List<Premissa> premissas) {
        TermosJson.NomesVariaveis nomes = new TermosJson.NomesVariaveis();
        ArrayNode l = JSON.arrayNode();
        for (Premissa p : premissas) {
            JsonNode j;
            if (p instanceof Premissa.DoFacto d) {
                j = JSON.numberNode(base.registo(d.facto()).numero());
            } else if (p instanceof Premissa.Negacao n) {
                j = TermosJson.json(Termo.de("nao", n.termo()), nomes);
            } else {
                j = TermosJson.json(((Premissa.Calculada) p).termo(), nomes);
            }
            l.add(Objects.requireNonNull(j));
        }
        return l;
    }
}
