package socrates.motores.drools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Paridade com o Prolog (AC14 e AC15): para cada variante de cenarios, o Drools tem de dar os mesmos factos que o
 * wrapper.pl, com a mesma regra, as mesmas premissas e no mesmo passo. Compara-se pelo conteudo dos factos, com as
 * 107 regras, contra motores/prolog/referencia_prolog/ (gerada pelo motores/prolog/ferramentas/gera_referencias.pl).
 * Havia tambem um nivel so com os grupos A a G e N (issue #13), do tempo em que o Drools so tinha essas regras; saiu
 * na F0008, porque este ja compara todas.
 * Os passos de cada cenario vem do cenarios.json do modulo (classpath), que o gera_json.pl escreve igual ao do backend,
 * o que os geradores das referencias usam.
 */
class ParidadeTest {

    private static final Path MODULO = Path.of("").toAbsolutePath();
    private static final Path REF_COMPLETA = MODULO.resolve("../prolog/referencia_prolog");

    /** As variantes do gera_referencias.pl. */
    private static final List<String> VARIANTES = List.of("base", "base_e_normais_30_a_34", "base_e_atualizacoes",
            "r7_sim_19_sim", "r7_nao", "r7_nao_sei_19_nao", "respostas_3_10b_17", "r29_em_vez_de_29a",
            "c35c_em_vez_de_35b", "c36b_em_vez_de_36a", "fecho_sim", "fecho_nao", "ataque_intermedio_tardio",
            "c2_antes_do_porto");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MotorDrools MOTOR = new MotorDrools();

    static Stream<String> casos() {
        return VARIANTES.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void drools_da_os_mesmos_factos_que_o_prolog(String variante) throws IOException {
        JsonNode referencia = le(REF_COMPLETA.resolve(variante + ".json"));
        JsonNode drools = JSON.readTree(MOTOR.infere(pedido(referencia)));

        List<ComparaRespostas.Diferenca> diferencas = semCausasHMConhecidas(
                ComparaRespostas.compara(referencia.get("resposta"), drools), referencia.get("resposta"), drools);
        List<ComparaRespostas.Diferenca> restam = diferencas;
        assertTrue(restam.isEmpty(), () -> restam.size() + " diferencas em " + variante + ":\n"
                + restam.stream().map(d -> "  " + d).collect(Collectors.joining("\n")));
    }

    /**
     * Tira as diferencas que vem das regras H-M (35-88, PR #31), cada uma com a causa conhecida. Uma
     * diferenca com causa numa regra H-M que nao esteja aqui, ou sem causa H-M, continua a falhar o teste. Quando a #15 corrigir uma causa, sai daqui.
     */
    /** Causas conhecidas de diferenca nas regras H-M: vazio quando a #15 estiver completamente corrigida. */
    private static final Map<Integer, String> CAUSAS_H_M = Map.of();

    /** Respostas em erro por causa das regras H-M: vazio quando a #15 estiver completamente corrigida. */
    private static final Map<String, String> ERROS_H_M = Map.of();

    private static List<ComparaRespostas.Diferenca> semCausasHMConhecidas(List<ComparaRespostas.Diferenca> diferencas,
            JsonNode prolog, JsonNode drools) {
        if ("erro".equals(drools.path("estado").asText())
                && ERROS_H_M.keySet().stream().anyMatch(m -> drools.path("erro").asText().contains(m))) {
            return List.of();
        }
        Map<ComparaRespostas.Diferenca, java.util.Set<Integer>> causas =
                ComparaRespostas.causasNaOutraMetade(diferencas, prolog, drools, r -> r >= 35 && r <= 88);
        return diferencas.stream()
                .filter(d -> !causas.containsKey(d) || !CAUSAS_H_M.keySet().containsAll(causas.get(d)))
                .map(d -> causas.containsKey(d)
                        ? new ComparaRespostas.Diferenca(d.facto(), d.regra(), d + " [causa H-M por listar: R" + causas.get(d) + "]")
                        : d)
                .toList();
    }

    /**
     * O pedido de uma variante: os passos dos cenarios, do cenarios.json, e os das respostas do analista, que nao sao
     * cenarios (o motor faz a pergunta): esses refazem-se com os factos de entrada que a referencia tem nesse passo.
     */
    private static String pedido(JsonNode referencia) throws IOException {
        JsonNode idsCenarios = referencia.get("cenarios");
        JsonNode todos;
        try (var in = ParidadeTest.class.getResourceAsStream("/cenarios.json")) {
            todos = JSON.readTree(in);
        }
        Map<String, JsonNode> passos = new HashMap<>();
        todos.get("cenarios").forEach(c -> passos.put(c.get("id").asText(), c.get("passo")));
        ObjectNode pedido = JSON.createObjectNode();
        pedido.put("versaoKB", todos.get("versaoKB").asText());
        ArrayNode lista = pedido.putArray("passos");
        idsCenarios.forEach(id -> lista.add(passos.containsKey(id.asText())
                ? passos.get(id.asText()) : passoDeResposta(referencia.get("resposta"), id.asText())));
        return JSON.writeValueAsString(pedido);
    }

    private static JsonNode passoDeResposta(JsonNode resposta, String id) {
        ObjectNode passo = JSON.createObjectNode().put("id", id);
        ArrayNode factos = passo.putArray("factos");
        resposta.get("factos").forEach(f -> {
            if ("entrada".equals(f.path("origem").asText()) && id.equals(f.path("passo").asText())) {
                factos.addObject().put("predicado", f.get("predicado").asText()).set("args", f.get("args"));
            }
        });
        return passo;
    }

    private static JsonNode le(Path p) throws IOException {
        return JSON.readTree(Files.readString(p));
    }
}
