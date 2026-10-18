package cyberrangers.motores.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Paridade com o Prolog (AC14 e AC15): para cada variante de cenarios, o Drools tem de dar os mesmos factos que o
 * wrapper.pl, com a mesma regra, as mesmas premissas e no mesmo passo. Compara-se pelo conteudo dos factos.
 * <ul>
 *   <li>Nivel A-G+N: so as regras R1-R34 e R89-R92 ativas, contra src/test/resources/referencia_agn/ (gerada por
 *       motores/drools/ferramentas/gera_referencia_agn.pl). E o "feito quando" da issue #13.</li>
 *   <li>Nivel completo: as 92 regras, contra motores/prolog/referencia_prolog/.</li>
 * </ul>
 * Os passos de cada cenario vem do cenarios.json do modulo (classpath), que o gera_json.pl escreve igual ao do backend,
 * o que os geradores das referencias usam.
 */
class ParidadeTest {

    private static final Path MODULO = Path.of("").toAbsolutePath();
    private static final Path REF_AGN = MODULO.resolve("src/test/resources/referencia_agn");
    private static final Path REF_COMPLETA = MODULO.resolve("../prolog/referencia_prolog");

    /** As variantes do gera_referencias.pl (e do gera_referencia_agn.pl). */
    private static final List<String> VARIANTES = List.of("base", "base_e_normais_30_a_34", "base_e_atualizacoes",
            "r7b_sim_19b_sim", "r7b_nao", "r7b_nao_sei_19b_nao", "respostas_3b_10d_17b", "c29c_em_vez_de_29b",
            "c35d_em_vez_de_35c", "c36c_em_vez_de_36b");

    private static final Predicate<String> REGRAS_AGN = nome -> {
        int n = Integer.parseInt(nome.substring(1));
        return n <= 34 || n >= 89;
    };

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, MotorDrools> MOTORES = new HashMap<>();

    static Stream<Arguments> casos() {
        return Stream.of("agn", "completo").flatMap(nivel -> VARIANTES.stream().map(v -> Arguments.of(nivel, v)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("casos")
    void drools_da_os_mesmos_factos_que_o_prolog(String nivel, String variante) throws IOException {
        JsonNode referencia = le((nivel.equals("agn") ? REF_AGN : REF_COMPLETA).resolve(variante + ".json"));
        MotorDrools motor = MOTORES.computeIfAbsent(nivel,
                n -> n.equals("agn") ? new MotorDrools("factos_iniciais.json", REGRAS_AGN) : new MotorDrools());
        JsonNode drools = JSON.readTree(motor.infere(pedido(referencia.get("cenarios"))));

        List<ComparaRespostas.Diferenca> diferencas = ComparaRespostas.compara(referencia.get("resposta"), drools);
        if (nivel.equals("completo")) {
            diferencas = semCausasHMConhecidas(diferencas, referencia.get("resposta"), drools);
        }
        List<ComparaRespostas.Diferenca> restam = diferencas;
        assertTrue(restam.isEmpty(), () -> restam.size() + " diferencas em " + nivel + "/" + variante + ":\n"
                + restam.stream().map(d -> "  " + d).collect(Collectors.joining("\n")));
    }

    /**
     * Nivel completo: tira as diferencas que vem das regras H-M (35-88, PR #31), cada uma com a causa conhecida. Uma
     * diferenca com causa numa regra H-M que nao esteja aqui, ou sem causa H-M, continua a falhar o teste. O nivel
     * A-G+N nao tem lista nenhuma. Quando a #15 corrigir uma causa, sai daqui.
     */
    /** Causas conhecidas de diferenca nas regras H-M: vazio quando a #15 estiver completamente corrigida. */
    private static final Map<Integer, String> CAUSAS_H_M = Map.of();

    /** Respostas em erro no nivel completo por causa das regras H-M: vazio quando a #15 estiver completamente corrigida. */
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

    /** As duas referencias tem de ter corrido os mesmos cenarios em cada variante (os geradores repetem a lista). */
    @Test
    void as_duas_referencias_tem_os_mesmos_cenarios() throws IOException {
        for (String v : VARIANTES) {
            assertEquals(le(REF_COMPLETA.resolve(v + ".json")).get("cenarios"), le(REF_AGN.resolve(v + ".json")).get("cenarios"), v);
        }
    }

    private static String pedido(JsonNode idsCenarios) throws IOException {
        JsonNode todos;
        try (var in = ParidadeTest.class.getResourceAsStream("/cenarios.json")) {
            todos = JSON.readTree(in);
        }
        Map<String, JsonNode> passos = new HashMap<>();
        todos.get("cenarios").forEach(c -> passos.put(c.get("id").asText(), c.get("passo")));
        ObjectNode pedido = JSON.createObjectNode();
        pedido.put("versaoKB", todos.get("versaoKB").asText());
        ArrayNode lista = pedido.putArray("passos");
        idsCenarios.forEach(id -> lista.add(passos.get(id.asText())));
        return JSON.writeValueAsString(pedido);
    }

    private static JsonNode le(Path p) throws IOException {
        return JSON.readTree(Files.readString(p));
    }
}
