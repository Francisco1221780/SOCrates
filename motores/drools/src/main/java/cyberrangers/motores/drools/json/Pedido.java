package cyberrangers.motores.drools.json;

import com.fasterxml.jackson.databind.JsonNode;
import cyberrangers.motores.drools.modelo.Facto;
import java.util.ArrayList;
import java.util.List;

/**
 * O pedido validado e convertido, igual ao valida_pedido/2 do wrapper.pl (mesmas mensagens). Tudo e convertido antes
 * do primeiro passo, para um pedido mal formado nunca dar uma inferencia a meio.
 */
public record Pedido(List<Passo> passos) {

    public record Passo(String id, List<Facto> factos) {
    }

    public static Pedido de(JsonNode pedido, String versaoKB) {
        if (pedido == null || !pedido.isObject()) {
            throw new PedidoInvalido("o pedido tem de ser um objeto JSON");
        }
        JsonNode v = pedido.get("versaoKB");
        String versao = v == null || v.isNull() ? "" : v.asText();
        if (!versaoKB.equals(versao)) {
            throw new PedidoInvalido("versaoKB desconhecida: \"" + versao + "\" (carregada: " + versaoKB + ")");
        }
        JsonNode passos = pedido.get("passos");
        if (passos == null || !passos.isArray() || passos.isEmpty()) {
            throw new PedidoInvalido("o pedido tem de ter pelo menos um passo");
        }
        List<Passo> r = new ArrayList<>(passos.size());
        int i = 1;
        for (JsonNode p : passos) {
            r.add(passo(p, i++));
        }
        return new Pedido(List.copyOf(r));
    }

    private static Passo passo(JsonNode p, int i) {
        if (!p.isObject()) {
            throw new PedidoInvalido("o passo " + i + " nao e um objeto JSON");
        }
        JsonNode idJson = p.get("id");
        String id = idJson == null || idJson.isNull() ? "passo_" + i : idJson.asText();
        JsonNode factos = p.get("factos");
        if (factos == null || !factos.isArray()) {
            throw new PedidoInvalido("o passo " + id + " nao tem a lista \"factos\"");
        }
        List<Facto> fs = new ArrayList<>(factos.size());
        for (JsonNode f : factos) {
            fs.add(TermosJson.facto(f, id));
        }
        return new Passo(id, List.copyOf(fs));
    }
}
