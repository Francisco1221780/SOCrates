package socrates.motores.drools.consola;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import socrates.motores.drools.MotorDrools;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * "Porque nao?" igual ao whynot/1 do Prolog: para cada facto de porque_nao/factos.txt, depois das sequencias base e
 * normal (a opcao 13 do menu, nos dois), o texto do Drools e o de porque_nao/prolog.txt (gerado por
 * motores/drools/ferramentas/gera_porque_nao.pl). Os numeros dos factos mudam entre motores, por isso nao se comparam;
 * a dica da profundidade aponta para a opcao do menu de cada um.
 */
class PorqueNaoTest {

    private static final MotorDrools MOTOR = new MotorDrools();
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<Arguments> factos() throws Exception {
        Map<String, String> prolog = new LinkedHashMap<>();
        String atual = null;
        StringBuilder texto = new StringBuilder();
        for (String l : le("/porque_nao/prolog.txt").split("\n")) {
            if (l.startsWith("#### ")) {
                if (atual != null) {
                    prolog.put(atual, texto.toString());
                }
                atual = l.substring(5);
                texto = new StringBuilder();
            } else {
                texto.append(l).append('\n');
            }
        }
        prolog.put(atual, texto.toString());
        List<Arguments> r = new ArrayList<>();
        for (String f : le("/porque_nao/factos.txt").split("\n")) {
            if (!f.isBlank()) {
                r.add(Arguments.of(f.strip(), prolog.get(f.strip())));
            }
        }
        return r.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factos")
    void igualAoWhynotDoProlog(String facto, String esperado) throws Exception {
        String r = MOTOR.porqueNao(pedidoBaseENormal(), TermoTexto.factos(facto, true).get(0).toString(), 4);
        StringBuilder drools = new StringBuilder();
        JSON.readTree(r).get("explicacao").forEach(l -> drools.append(Explicacoes.linhaPorqueNaoProlog(l)).append('\n'));
        assertEquals(normaliza(esperado.replace("define_opcao(profundidade_whynot, N)", "opcao 12, profundidade_whynot")),
                normaliza(drools.toString()), facto);
    }

    private static String normaliza(String s) {
        return s.replaceAll("\\(facto n\\. \\d+\\)", "(facto n. N)");
    }

    private static String pedidoBaseENormal() throws Exception {
        JsonNode c = JSON.readTree(le("/cenarios.json"));
        Map<String, JsonNode> porId = new LinkedHashMap<>();
        c.get("cenarios").forEach(x -> porId.put(x.get("id").asText(), x.get("passo")));
        ObjectNode pedido = JSON.createObjectNode().put("versaoKB", "6");
        var passos = pedido.putArray("passos");
        for (String seq : new String[] {"base", "normal"}) {
            c.get("sequencias").get(seq).forEach(id -> passos.add(porId.get(id.asText())));
        }
        return pedido.toString();
    }

    private static String le(String recurso) throws Exception {
        try (InputStream in = PorqueNaoTest.class.getResourceAsStream(recurso)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "");
        }
    }
}
