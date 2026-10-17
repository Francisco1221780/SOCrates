package cyberrangers.cyber.engine.prolog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cyberrangers.cyber.config.PrologProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


/**
 * Testes de unidade do PrologAdapter, que chama o wrapper.pl real.
 */
class PrologAdapterTest {

    private static final Path RAIZ = Path.of("../..");
    private static final String PEDIDO = """
            {"versaoKB":"6","passos":[{"id":"cenario_06","factos":[
              {"predicado":"vetor_aberto","args":["ev_f1","web_srv_05","web_srv_05_apache","cve_2021_41773"]}]}]}
            """;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<PrologInstaller> installers = new ArrayList<>();

    private PrologAdapter adapter(String swipl, Path home) {
        return adapter(new PrologProperties(swipl, home, Duration.ofSeconds(10), 4, Duration.ofSeconds(2)));
    }

    private PrologAdapter adapter(PrologProperties prologProperties) {
        return new PrologAdapter(prologProperties, json, installer(new PrologInstaller(prologProperties)));
    }

    /** Guarda o installer para o destroy() no fim do teste: fora do Spring ninguem apaga a pasta temporaria. */
    private PrologInstaller installer(PrologInstaller installer) {
        installers.add(installer);
        return installer;
    }

    /**
     * Lê a resposta JSON do wrapper.pl e devolve o JsonNode correspondente. Se a resposta nao for um objecto JSON, falha o teste.
     * @param resposta
     * @return
     */
    private JsonNode le(String resposta) {
        JsonNode n = json.readTree(resposta);
        assertTrue(n.isObject(), resposta);
        return n;
    }

    /**
     * Verifica que a resposta JSON do wrapper.pl indica erro de contrato, com estado "erro", motor "prolog" e versaoKB null.
     * @param resposta
     */
    private static void assertErro(JsonNode resposta) {
        assertEquals("erro", resposta.path("estado").asString(), resposta.toString());
        assertEquals("prolog", resposta.path("motor").asString(), resposta.toString());
        assertTrue(resposta.path("versaoKB").isNull(), resposta.toString());
    }

    /**
     * Verifica que nao ficaram processos filhos vivos depois de cada teste. Se houver algum, falha o teste.
     */
    @AfterEach
    void naoFicamProcessosVivos() {
        installers.forEach(PrologInstaller::destroy);
        assertTrue(ProcessHandle.current().children().noneMatch(ProcessHandle::isAlive),
                "ficaram processos filhos vivos depois do teste");
    }

    /**
     * Verifica que o nome do motor de inferencia é "prolog", como definido no contrato
     */
    @Test
    void chamaSeProlog() {
        assertEquals("prolog", adapter("swipl", RAIZ).nome());
    }

    /**
     * AC6: sem cyber.prolog.home, o PrologInstaller copia o wrapper.pl e a base do classpath (prolog-dist/) para uma
     * pasta temporaria, e o adaptador responde a partir dela como responderia a partir do repositorio.
     */
    @Test
    void semHomeUsaACopiaDoClasspath() {
        JsonNode r = le(adapter("swipl", null).infere(PEDIDO));
        assertEquals("concluido", r.path("estado").asString(), r.toString());
        assertTrue(r.toString().contains("\"regra\":7"), r.toString());
    }

    /** Se a copia falhou, o adaptador responde com a razao do PrologInstaller, no formato de erro do contrato. */
    @Test
    void semPastaDoMotorDaErroDoContratoComARazao() {
        PrologProperties prologProperties = new PrologProperties("swipl", RAIZ, Duration.ofSeconds(10), 4, Duration.ofSeconds(2));
        PrologInstaller semPasta = new PrologInstaller(prologProperties) {
            @Override
            public Path home() {
                return null;
            }

            @Override
            public String erro() {
                return "copia falhou";
            }
        };
        JsonNode r = le(new PrologAdapter(prologProperties, json, semPasta).infere(PEDIDO));
        assertErro(r);
        assertEquals("copia falhou", r.path("erro").asString());
    }

    /**
     * Verifica que se o home for uma pasta sem wrapper.pl nem base_conhecimento, o swipl nao e lancado e a resposta
     * JSON indica erro de contrato, com a mensagem contendo o caminho do home.
     * @param vazia
     */
    @Test
    void homeSemWrapperNemBaseDaErroComOCaminho(@TempDir Path vazia) {
        JsonNode r = le(adapter("swipl", vazia).infere(PEDIDO));
        assertErro(r);
        String erro = r.path("erro").asString();
        assertTrue(erro.contains("nao contem wrapper.pl"), erro);
        assertTrue(erro.contains(vazia.toAbsolutePath().normalize().toString()), erro);
    }

    /**
     * Verifica que se o home tiver o wrapper.pl mas nao tiver a base_conhecimento, o wrapper.pl nao é chamado e a resposta JSON indica erro de contrato, com a mensagem contendo "base_conhecimento".
     * @param home
     * @throws Exception
     */
    @Test
    void homeComWrapperMasSemBaseDaErro(@TempDir Path home) throws Exception {
        Path wrapper = home.resolve("motores/prolog/wrapper.pl");
        Files.createDirectories(wrapper.getParent());
        Files.writeString(wrapper, "% vazio\n");
        JsonNode r = le(adapter("swipl", home).infere(PEDIDO));
        assertErro(r);
        assertTrue(r.path("erro").asString().contains("base_conhecimento"), r.toString());
    }

    /**
     * Verifica que se o caminho do home tiver aspas e barras invertidas, a resposta de erro (home sem wrapper.pl)
     * continua a ser JSON valido e a mensagem traz o caminho intacto.
     * @param tmp
     * @throws Exception
     */
    @Test
    void erroComAspasEBarrasContinuaAserJsonValido(@TempDir Path tmp) throws Exception {
        Path estranha = Files.createDirectory(tmp.resolve("a \"b\" c\\d"));
        JsonNode r = le(adapter("swipl", estranha).infere(PEDIDO));
        assertErro(r);
        assertTrue(r.path("erro").asString().contains("a \"b\" c\\d"), r.toString());
    }

    /**
     * Verifica que se o executavel do SWI-Prolog nao existir, o wrapper.pl nao é chamado e a resposta JSON indica erro de contrato, com a mensagem contendo "swipl nao encontrado" e o nome do executavel.
     */
    @Test
    void executavelEmFaltaDaErro() {
        JsonNode r = le(adapter("swipl-que-nao-existe", RAIZ).infere(PEDIDO));
        assertErro(r);
        String erro = r.path("erro").asString();
        assertTrue(erro.contains("swipl nao encontrado"), erro);
        assertTrue(erro.contains("swipl-que-nao-existe"), erro);
    }

    /**
     * Verifica que cada pedido liberta a sua licenca: com um so lugar e sem espera, tres pedidos seguidos nunca dao
     * "ocupado". O home vazio faz cada pedido falhar logo, sem lancar o swipl.
     * @param vazia
     */
    @Test
    void pedidosSeguidosNaoGastamLicencas(@TempDir Path vazia) {
        PrologAdapter a = adapter(new PrologProperties("swipl", vazia, Duration.ofSeconds(10), 1, Duration.ZERO));
        for (int i = 0; i < 3; i++) {
            String erro = le(a.infere(PEDIDO)).path("erro").asString();
            assertTrue(erro.contains("nao contem wrapper.pl"), "pedido " + i + ": " + erro);
        }
    }

    /**
     * Verifica que o wrapper.pl é chamado com sucesso e devolve uma resposta JSON com estado "concluido", motor "prolog", versaoKB "6" e regra 7.
     */
    @Test
    void respostaNormalDoWrapper() {
        JsonNode r = le(adapter("swipl", RAIZ).infere(PEDIDO));
        assertEquals("concluido", r.path("estado").asString(), r.toString());
        assertEquals("prolog", r.path("motor").asString());
        assertEquals("6", r.path("versaoKB").asString());
        assertTrue(r.toString().contains("\"regra\":7"), r.toString());
    }
}
