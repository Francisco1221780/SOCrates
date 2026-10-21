package socrates.backend.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import socrates.backend.config.PrologProperties;
import socrates.backend.engine.drools.DroolsAdapter;
import socrates.backend.engine.prolog.PrologAdapter;
import socrates.backend.engine.prolog.PrologInstaller;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * "Porque nao?" igual nos dois motores, pelos adaptadores do backend: para cada facto de porque_nao/factos.txt (os do
 * PorqueNaoTest do Drools), depois das sequencias base e normal, o PrologAdapter (modo whynot do wrapper.pl) e o
 * DroolsAdapter (MotorDrools.porqueNao) dao as mesmas linhas, campo a campo. So os numeros dos factos ficam de fora:
 * mudam entre motores.
 *
 * <p>Cada chamada volta a correr as duas sequencias (no Prolog, ~4 s num swipl novo), por isso os factos correm em
 * paralelo, tantos de cada vez quantos os lugares do PrologAdapter.
 */
class PorqueNaoParidadeTest {

    private static final Path RAIZ = Path.of("../..");
    private static final Path FACTOS = RAIZ.resolve("motores/drools/src/test/resources/porque_nao/factos.txt");
    private static final int EM_PARALELO = 4;

    private static final JsonMapper json = JsonMapper.builder().build();
    private static PrologInstaller installer;
    private static PrologAdapter prolog;
    private static DroolsAdapter drools;

    @BeforeAll
    static void criaAdaptadores() {
        PrologProperties props = new PrologProperties("swipl", RAIZ, Duration.ofSeconds(20), EM_PARALELO,
                Duration.ofSeconds(30));
        installer = new PrologInstaller(props);
        prolog = new PrologAdapter(props, json, installer);
        drools = new DroolsAdapter();
    }

    @AfterAll
    static void apagaACopia() {
        installer.destroy();
    }

    @Test
    void osDoisMotoresDaoAMesmaExplicacao() throws Exception {
        String pedido = pedidoBaseENormal();
        List<String> factos = Files.readAllLines(FACTOS, StandardCharsets.UTF_8).stream()
                .map(String::strip).filter(l -> !l.isEmpty()).toList();
        assertTrue(factos.size() >= 10, "factos.txt com " + factos.size() + " factos");

        List<Future<String>> comparacoes = new ArrayList<>();
        try (ExecutorService paralelo = Executors.newFixedThreadPool(EM_PARALELO)) {
            for (String texto : factos) {
                comparacoes.add(paralelo.submit(() -> compara(pedido, texto)));
            }
        }
        List<String> diferentes = new ArrayList<>();
        for (Future<String> c : comparacoes) {
            String diferenca = c.get();
            if (diferenca != null) {
                diferentes.add(diferenca);
            }
        }
        assertTrue(diferentes.isEmpty(), "explicacoes diferentes:\n" + String.join("\n", diferentes));
    }

    /**
     * Pede o porque nao do facto aos dois motores e compara as linhas.
     * @return null se forem iguais; senao, o facto e as linhas de cada motor
     */
    private static String compara(String pedido, String texto) {
        String facto = json.writeValueAsString(factoJson(texto));
        JsonNode p = le(prolog.porqueNao(pedido, facto, 4), "prolog", texto);
        JsonNode d = le(drools.porqueNao(pedido, facto, 4), "drools", texto);
        assertEquals(json.readTree(facto), p.path("facto"), texto);
        assertTrue(!p.path("explicacao").isEmpty(), texto + ": explicacao vazia no prolog");
        JsonNode lp = semNumeros(p.path("explicacao"));
        JsonNode ld = semNumeros(d.path("explicacao"));
        return lp.equals(ld) ? null : texto + "\n  prolog: " + lp + "\n  drools: " + ld;
    }

    /**
     * A resposta como JSON, que tem de ser concluido.
     */
    private static JsonNode le(String resposta, String motor, String facto) {
        JsonNode r = json.readTree(resposta);
        assertEquals("concluido", r.path("estado").asString(), motor + ", " + facto + ": " + resposta);
        return r;
    }

    /**
     * O pedido com os passos das sequencias base e normal do catalogo de cenarios, por esta ordem (como a opcao 13
     * do menu, nos dois motores).
     */
    private static String pedidoBaseENormal() throws IOException {
        JsonNode catalogo;
        try (InputStream in = PorqueNaoParidadeTest.class.getResourceAsStream("/generated/cenarios.json")) {
            catalogo = json.readTree(in);
        }
        ObjectNode pedido = json.createObjectNode().put("versaoKB", catalogo.path("versaoKB").asString());
        ArrayNode passos = pedido.putArray("passos");
        for (String seq : List.of("base", "normal")) {
            for (JsonNode id : catalogo.path("sequencias").path(seq)) {
                for (JsonNode c : catalogo.path("cenarios")) {
                    if (c.path("id").asString().equals(id.asString())) {
                        passos.add(c.path("passo"));
                    }
                }
            }
        }
        assertTrue(passos.size() > 40, "sequencias base e normal com " + passos.size() + " passos");
        return json.writeValueAsString(pedido);
    }

    /**
     * Um facto do factos.txt como {"predicado", "args"}: os argumentos sao atomos ou variaveis (comecam por
     * maiuscula ou _), como {"var": nome}. So os termos simples que o ficheiro tem.
     */
    private static ObjectNode factoJson(String texto) {
        int abre = texto.indexOf('(');
        assertTrue(abre > 0 && texto.endsWith(")") && texto.indexOf('(', abre + 1) < 0,
                "facto que o teste nao sabe ler: " + texto);
        ObjectNode f = json.createObjectNode().put("predicado", texto.substring(0, abre));
        ArrayNode args = f.putArray("args");
        for (String a : texto.substring(abre + 1, texto.length() - 1).split(",")) {
            String arg = a.strip();
            if (Character.isUpperCase(arg.charAt(0)) || arg.charAt(0) == '_') {
                args.addObject().put("var", arg);
            } else {
                args.add(arg);
            }
        }
        return f;
    }

    /**
     * Uma copia do JSON sem os campos numero (o numero de um facto muda entre motores).
     */
    private static JsonNode semNumeros(JsonNode n) {
        JsonNode c = n.deepCopy();
        tiraNumeros(c);
        return c;
    }

    private static void tiraNumeros(JsonNode n) {
        if (n instanceof ObjectNode o) {
            o.remove("numero");
        }
        for (JsonNode filho : n) {
            tiraNumeros(filho);
        }
    }
}
