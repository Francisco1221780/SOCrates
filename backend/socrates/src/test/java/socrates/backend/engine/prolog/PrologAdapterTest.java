package socrates.backend.engine.prolog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import socrates.backend.config.PrologProperties;
import socrates.backend.domain.Metaconhecimento;
import socrates.backend.domain.SaudeMotor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;


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

    /**
     * Cria um adaptador com o executavel e o home dados, e os valores de omissao para o resto.
     * @param swipl
     * @param home
     * @return
     */
    private PrologAdapter adapter(String swipl, Path home) {
        return adapter(new PrologProperties(swipl, home, Duration.ofSeconds(10), 4, Duration.ofSeconds(2)));
    }

    /**
     * Cria um adaptador com as propriedades dadas e o seu PrologInstaller.
     * @param prologProperties
     * @return
     */
    private PrologAdapter adapter(PrologProperties prologProperties) {
        return new PrologAdapter(prologProperties, json, installer(new PrologInstaller(prologProperties)));
    }

    /**
     * Guarda o installer para o destroy() no fim do teste: fora do Spring ninguem apaga a pasta temporaria.
     * @param installer
     * @return
     */
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
     * Verifica que sem socrates.prolog.home o PrologInstaller copia o wrapper.pl e a base do classpath (prolog-dist/)
     * para uma pasta temporaria, e o adaptador responde a partir dela como a partir do repositorio (AC6).
     */
    @Test
    void semHomeUsaACopiaDoClasspath() {
        JsonNode r = le(adapter("swipl", null).infere(PEDIDO));
        assertEquals("concluido", r.path("estado").asString(), r.toString());
        assertTrue(r.toString().contains("\"regra\":7"), r.toString());
    }

    /**
     * Verifica que se a copia falhou, o adaptador responde com a razao do PrologInstaller, no formato de erro do
     * contrato.
     */
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
     * continua a ser JSON valido e a mensagem traz o caminho intacto. No Windows um nome nao pode ter aspas e a barra
     * invertida e o separador: a pasta leva plicas e as barras invertidas sao as do proprio caminho.
     * @param tmp
     * @throws Exception
     */
    @Test
    void erroComAspasEBarrasContinuaAserJsonValido(@TempDir Path tmp) throws Exception {
        String nome = OS.WINDOWS.isCurrentOs() ? "a 'b' c" : "a \"b\" c\\d";
        Path estranha = Files.createDirectory(tmp.resolve(nome));
        JsonNode r = le(adapter("swipl", estranha).infere(PEDIDO));
        assertErro(r);
        assertTrue(r.path("erro").asString().contains(estranha.toString()), r.toString());
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

    /**
     * Cria uma pasta temporaria com o wrapper.pl contendo o programa Prolog dado, e a base_conhecimento vazia.
     * @param tmp
     * @param programaProlog
     * @return
     * @throws IOException
     */
    private static Path homeFalsa(Path tmp, String programaProlog) throws IOException {
        Path wrapper = tmp.resolve("motores/prolog/wrapper.pl");
        Files.createDirectories(wrapper.getParent());
        Files.createDirectories(tmp.resolve("base_conhecimento"));
        Files.writeString(wrapper, ":- initialization(main, main).\n" + programaProlog + "\n");
        return tmp;
    }

    /**
     * Cria um adaptador com o swipl do PATH, o home dado e os limites dados.
     * @param home
     * @param timeout
     * @param maxProcessos
     * @param espera
     * @return
     */
    private PrologAdapter adapter(Path home, Duration timeout, int maxProcessos, Duration espera) {
        return adapter(new PrologProperties("swipl", home, timeout, maxProcessos, espera));
    }

    /**
     * Verifica que ao fim do timeout o processo e terminado e a resposta e o erro "timeout", sem esperar que ele
     * acabe (AC5).
     * @param tmp
     * @throws Exception
     */
    @Test
    void timeoutMataOProcesso(@TempDir Path tmp) throws Exception {
        PrologAdapter a = adapter(homeFalsa(tmp, "main :- sleep(30)."), Duration.ofMillis(500), 1, Duration.ZERO);
        long inicio = System.nanoTime();
        JsonNode r = le(a.infere(PEDIDO));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        assertErro(r);
        assertEquals("timeout", r.path("erro").asString());
        // a margem cobre um arranque lento do swipl na CI; o que interessa e nao ter esperado os 30 s
        assertTrue(ms < 5_000, "demorou " + ms + " ms");
        // o @AfterEach confirma que o processo morreu
    }

    /**
     * Verifica que um motor que morre sem JSON da erro do contrato com o codigo de saida e a ultima linha do stderr.
     * @param tmp
     * @throws Exception
     */
    @Test
    void motorSemJsonDaErroComOStderr(@TempDir Path tmp) throws Exception {
        PrologAdapter a = adapter(homeFalsa(tmp, "main :- format(user_error, \"rebentou~n\", []), halt(2)."),
                Duration.ofSeconds(10), 1, Duration.ZERO);
        JsonNode r = le(a.infere(PEDIDO));
        assertErro(r);
        String erro = r.path("erro").asString();
        assertTrue(erro.contains("sem resposta JSON"), erro);
        assertTrue(erro.contains("codigo 2"), erro);
        assertTrue(erro.contains("rebentou"), erro);
    }

    /**
     * Verifica que um stdout que nao e um objeto JSON nunca chega ao cliente: da erro do contrato.
     * @param tmp
     * @throws Exception
     */
    @Test
    void stdoutQueNaoEJsonDaErro(@TempDir Path tmp) throws Exception {
        PrologAdapter a = adapter(homeFalsa(tmp, "main :- write(ola), nl."), Duration.ofSeconds(10), 1, Duration.ZERO);
        JsonNode r = le(a.infere(PEDIDO));
        assertErro(r);
        String erro = r.path("erro").asString();
        assertTrue(erro.contains("sem resposta JSON"), erro);
        assertTrue(erro.contains("codigo 0"), erro);
    }

    /**
     * Verifica que o JSON de erro do wrapper real (pedido invalido, codigo de saida 1) vai tal como veio (M9).
     */
    @Test
    void erroDoWrapperVemTalComoVeio() {
        String pedido = """
                {"versaoKB":"5","passos":[{"id":"p1","factos":[]}]}
                """;
        JsonNode r = le(adapter("swipl", RAIZ).infere(pedido));
        assertEquals("erro", r.path("estado").asString(), r.toString());
        assertEquals("prolog", r.path("motor").asString(), r.toString());
        // a mensagem e a do wrapper.pl (valida_pedido), nao uma do adaptador
        assertTrue(r.path("erro").asString().contains("versaoKB desconhecida"), r.toString());
    }

    /**
     * Verifica que com o unico lugar ocupado e sem espera, o segundo pedido da "ocupado" e o primeiro termina
     * normalmente (AC7).
     * @param tmp
     * @throws Exception
     */
    @Test
    void ocupadoQuandoOsLugaresEstaoCheios(@TempDir Path tmp) throws Exception {
        String lento = "main :- sleep(2), write('{\"estado\":\"concluido\",\"motor\":\"prolog\"}'), nl.";
        PrologAdapter a = adapter(homeFalsa(tmp, lento), Duration.ofSeconds(10), 1, Duration.ZERO);

        CompletableFuture<String> primeiro = CompletableFuture.supplyAsync(() -> a.infere(PEDIDO));
        // espera ate o swipl do primeiro pedido existir (e portanto ter o lugar), sem depender de um sleep
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ProcessHandle.current().children().noneMatch(ProcessHandle::isAlive)) {
            assertTrue(System.nanoTime() < limite, "o primeiro pedido nao lancou o swipl");
            Thread.sleep(10);
        }

        JsonNode segundo = le(a.infere(PEDIDO));
        assertErro(segundo);
        assertTrue(segundo.path("erro").asString().startsWith("ocupado"), segundo.toString());

        JsonNode r1 = le(primeiro.get(10, TimeUnit.SECONDS));
        assertEquals("concluido", r1.path("estado").asString(), r1.toString());
    }

    /**
     * Verifica que a sequencia base inteira, com cerca de 250 KB de stdout (muito acima do buffer de um pipe, 64 KiB),
     * termina antes do timeout, porque o stdout e o stderr sao lidos em paralelo com o processo (AC4).
     * @throws Exception
     */
    @Test
    void respostaGrandeNaoBloqueia() throws Exception {
        JsonNode catalogo;
        try (InputStream in = getClass().getResourceAsStream("/generated/cenarios.json")) {
            catalogo = json.readTree(in);
        }
        ObjectNode pedido = json.createObjectNode().put("versaoKB", "6");
        ArrayNode passos = pedido.putArray("passos");
        for (JsonNode id : catalogo.path("sequencias").path("base")) {
            for (JsonNode c : catalogo.path("cenarios")) {
                if (c.path("id").asString().equals(id.asString())) {
                    passos.add(c.path("passo"));
                }
            }
        }
        assertTrue(passos.size() > 40, "sequencia base com " + passos.size() + " passos");

        String resposta = adapter("swipl", RAIZ).infere(json.writeValueAsString(pedido));
        assertTrue(resposta.length() > 64 * 1024, "resposta com " + resposta.length() + " caracteres");
        JsonNode r = le(resposta);
        assertEquals("aguarda_resposta", r.path("estado").asString(), r.path("erro").asString());
    }

    /**
     * Verifica que um facto com acentos e simbolos vai pelo stdin e volta igual no stdout (UTF-8 nos dois sentidos,
     * AC2).
     */
    @Test
    void acentosVaoEVoltam() {
        String pedido = """
                {"versaoKB":"6","passos":[{"id":"p1","factos":[{"predicado":"nota","args":["ação ü €"]}]}]}
                """;
        JsonNode r = le(adapter("swipl", RAIZ).infere(pedido));
        assertEquals("concluido", r.path("estado").asString(), r.toString());
        boolean encontrado = false;
        for (JsonNode f : r.path("factos")) {
            if (f.path("predicado").asString().equals("nota")) {
                assertEquals("ação ü €", f.path("args").path(0).asString());
                encontrado = true;
            }
        }
        assertTrue(encontrado, "o facto nota/1 nao voltou na resposta");
    }

    /**
     * Verifica que com a base do repositorio o estado e pronto: a base carregou, o wrapper tem a versao 6 e o
     * metaconhecimento esta em dia (AC13).
     */
    @Test
    void estadoProntoComABaseDoRepositorio() {
        SaudeMotor e = adapter("swipl", RAIZ).estado();
        assertEquals("prolog", e.motor());
        assertTrue(e.pronto(), e.toString());
        assertEquals("6", e.versaoKB());
        assertTrue(e.baseCarregada(), e.toString());
        assertEquals(Metaconhecimento.EM_DIA, e.metaconhecimento());
        assertTrue(e.erros().isEmpty(), e.toString());
    }

    /**
     * Verifica que a copia do classpath (o que vai no jar) tambem traz o gera_meta.pl: sem socrates.prolog.home o
     * metaconhecimento verifica-se na mesma (AC6, AC13).
     */
    @Test
    void estadoProntoComACopiaDoClasspath() {
        SaudeMotor e = adapter("swipl", null).estado();
        assertTrue(e.pronto(), e.toString());
        assertEquals(Metaconhecimento.EM_DIA, e.metaconhecimento());
    }

    /**
     * Verifica que o estado e verificado uma vez e fica guardado: a segunda chamada nao lanca processos.
     */
    @Test
    void estadoFicaGuardado() {
        PrologAdapter a = adapter("swipl", RAIZ);
        assertSame(a.estado(), a.estado());
    }

    /**
     * Copia para a pasta dada o que o motor precisa (wrapper.pl, soc_motor.pl, gera_meta.pl e a base), para os
     * testes estragarem a copia e nao o repositorio.
     * @param tmp
     * @param comGeraMeta
     * @return
     * @throws IOException
     */
    private static Path copiaDoMotor(Path tmp, boolean comGeraMeta) throws IOException {
        List<String> ficheiros = new ArrayList<>(List.of("motores/prolog/wrapper.pl", "motores/prolog/soc_motor.pl",
                "base_conhecimento/soc_factos.txt", "base_conhecimento/soc_regras.txt",
                "base_conhecimento/soc_suporte.txt"));
        if (comGeraMeta) {
            ficheiros.add("motores/prolog/ferramentas/gera_meta.pl");
        }
        for (String f : ficheiros) {
            Path alvo = tmp.resolve(f);
            Files.createDirectories(alvo.getParent());
            Files.copy(RAIZ.resolve(f), alvo);
        }
        return tmp;
    }

    /**
     * Verifica que uma base com um erro de sintaxe nao conta como carregada, mesmo que o wrapper responda, e que o
     * erro do consult vem nos erros.
     * @param tmp
     * @throws Exception
     */
    @Test
    void baseComErroDeSintaxeNaoEstaCarregada(@TempDir Path tmp) throws Exception {
        Path home = copiaDoMotor(tmp, true);
        Files.writeString(home.resolve("base_conhecimento/soc_factos.txt"), "\nisto nao e prolog (\n",
                StandardOpenOption.APPEND);
        SaudeMotor e = adapter("swipl", home).estado();
        assertFalse(e.pronto(), e.toString());
        assertFalse(e.baseCarregada(), e.toString());
        assertTrue(e.erros().stream().anyMatch(l -> l.startsWith("base: ERROR") && l.contains("soc_factos.txt")),
                e.toString());
    }

    /**
     * Verifica que com uma linha do facto_dispara_regras/2 diferente do que as regras dao, o metaconhecimento fica
     * desatualizado, o motor nao fica pronto e o erro diz que linha e como se corrige (AC13).
     * @param tmp
     * @throws Exception
     */
    @Test
    void metaconhecimentoDesatualizadoNaoEstaPronto(@TempDir Path tmp) throws Exception {
        Path home = copiaDoMotor(tmp, true);
        Path suporte = home.resolve("base_conhecimento/soc_suporte.txt");
        String original = Files.readString(suporte);
        String linha = "facto_dispara_regras(acesso_suspeito(_, _), [46]).";
        assertTrue(original.contains(linha), "o soc_suporte.txt mudou: escolher outra linha para o teste");
        Files.writeString(suporte, original.replace(linha, "facto_dispara_regras(acesso_suspeito(_, _), [999])."));

        SaudeMotor e = adapter("swipl", home).estado();
        assertFalse(e.pronto(), e.toString());
        assertTrue(e.baseCarregada(), e.toString());
        assertEquals("6", e.versaoKB());
        assertEquals(Metaconhecimento.DESATUALIZADO, e.metaconhecimento());
        assertTrue(e.erros().stream().anyMatch(l -> l.contains("Desatualizado: acesso_suspeito")), e.toString());
        assertTrue(e.erros().getLast().contains("gera_meta.pl"), e.toString());
    }

    /**
     * Verifica que sem o gera_meta.pl o metaconhecimento fica desconhecido e o motor nao fica pronto, mas a base e a
     * versao continuam verificadas.
     * @param tmp
     * @throws Exception
     */
    @Test
    void semGeraMetaOMetaconhecimentoEDesconhecido(@TempDir Path tmp) throws Exception {
        SaudeMotor e = adapter("swipl", copiaDoMotor(tmp, false)).estado();
        assertFalse(e.pronto(), e.toString());
        assertTrue(e.baseCarregada(), e.toString());
        assertEquals("6", e.versaoKB());
        assertEquals(Metaconhecimento.DESCONHECIDO, e.metaconhecimento());
        assertTrue(e.erros().getFirst().contains("gera_meta.pl"), e.toString());
    }

    /**
     * Verifica que um wrapper que nao responde dentro do timeout deixa o motor nao pronto, sem processos vivos.
     * @param tmp
     * @throws Exception
     */
    @Test
    void wrapperQueNaoRespondeNaoEstaPronto(@TempDir Path tmp) throws Exception {
        PrologAdapter a = adapter(homeFalsa(tmp, "main :- sleep(30)."), Duration.ofMillis(500), 1, Duration.ZERO);
        SaudeMotor e = a.estado();
        assertFalse(e.pronto(), e.toString());
        assertFalse(e.baseCarregada(), e.toString());
        assertNull(e.versaoKB());
        assertTrue(e.erros().getFirst().contains("timeout"), e.toString());
        // o @AfterEach confirma que o processo morreu
    }

    /**
     * Verifica que sem o executavel do swipl o estado nao e pronto e o erro diz porque.
     */
    @Test
    void estadoNaoProntoSemSwipl() {
        SaudeMotor e = adapter("swipl-que-nao-existe", RAIZ).estado();
        assertFalse(e.pronto(), e.toString());
        assertEquals(1, e.erros().size(), e.toString());
        assertTrue(e.erros().getFirst().contains("swipl nao encontrado"), e.toString());
    }

    /**
     * Verifica que se o swipl terminar com erro, o estado nao e pronto e o erro traz o codigo de saida. O swipl falso
     * e um script sh, ou um .cmd no Windows.
     * @param tmp
     * @throws Exception
     */
    @Test
    void estadoNaoProntoSeOSwiplFalhar(@TempDir Path tmp) throws Exception {
        boolean windows = OS.WINDOWS.isCurrentOs();
        Path falso = tmp.resolve(windows ? "swipl-falso.cmd" : "swipl-falso");
        Files.writeString(falso, windows ? "@exit /b 3\r\n" : "#!/bin/sh\nexit 3\n");
        assertTrue(windows || falso.toFile().setExecutable(true));
        SaudeMotor e = adapter(falso.toString(), RAIZ).estado();
        assertFalse(e.pronto(), e.toString());
        assertTrue(e.erros().getFirst().contains("codigo 3"), e.toString());
    }

    /**
     * Verifica que sem a pasta do motor (copia falhada), o estado nao e pronto e o erro e o do PrologInstaller.
     */
    @Test
    void estadoNaoProntoSemPastaDoMotor() {
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
        SaudeMotor e = new PrologAdapter(prologProperties, json, semPasta).estado();
        assertFalse(e.pronto(), e.toString());
        assertEquals(List.of("copia falhou"), e.erros());
    }

    // --- porque nao (modo whynot do wrapper) -------------------------------------------------------------------

    private static final String EXPLORACAO = "{\"predicado\":\"exploracao_possivel\",\"args\":[\"ev_f1\",\"web_srv_05\",{\"var\":\"V\"}]}";

    /**
     * Verifica que o porque nao corre o wrapper real no modo whynot: depois do c6, exploracao_possivel(ev_f1,
     * web_srv_05, V) nao se concluiu, e a explicacao passa pela regra 6, com o se (cada condicao com verificada) e o
     * entao. O facto volta como veio, com a variavel.
     */
    @Test
    void porqueNaoDoWrapper() {
        JsonNode r = le(adapter("swipl", RAIZ).porqueNao(PEDIDO, EXPLORACAO, 4));
        assertEquals("concluido", r.path("estado").asString(), r.toString());
        assertEquals("prolog", r.path("motor").asString());
        assertEquals("6", r.path("versaoKB").asString());
        assertEquals(json.readTree(EXPLORACAO), r.path("facto"));
        JsonNode regra6 = null;
        for (JsonNode linha : r.path("explicacao")) {
            if (linha.path("tipo").asString().equals("regra") && linha.path("regra").asInt() == 6) {
                regra6 = linha;
            }
        }
        assertTrue(regra6 != null, r.toString());
        assertTrue(regra6.path("se").isArray() && !regra6.path("se").isEmpty(), regra6.toString());
        for (JsonNode c : regra6.path("se")) {
            assertTrue(c.path("verificada").isBoolean(), c.toString());
        }
        assertTrue(regra6.path("entao").isArray() && !regra6.path("entao").isEmpty(), regra6.toString());
    }

    /**
     * Verifica que a profundidade chega ao wrapper: com 1, a explicacao para mais cedo (tem uma linha profundidade e
     * menos linhas que com 4).
     */
    @Test
    void porqueNaoLevaAProfundidade() {
        PrologAdapter a = adapter("swipl", RAIZ);
        JsonNode r1 = le(a.porqueNao(PEDIDO, EXPLORACAO, 1));
        JsonNode r4 = le(a.porqueNao(PEDIDO, EXPLORACAO, 4));
        assertEquals("concluido", r1.path("estado").asString(), r1.toString());
        boolean parou = false;
        for (JsonNode linha : r1.path("explicacao")) {
            parou |= linha.path("tipo").asString().equals("profundidade");
        }
        assertTrue(parou, r1.toString());
        assertTrue(r1.path("explicacao").size() < r4.path("explicacao").size(),
                r1.path("explicacao").size() + " linhas com 1, " + r4.path("explicacao").size() + " com 4");
    }

    /**
     * Verifica que o erro do wrapper no modo whynot (um facto sem args, que o RequestValidator ja apanharia) vem tal
     * como veio (M9).
     */
    @Test
    void porqueNaoComErroDoWrapper() {
        JsonNode r = le(adapter("swipl", RAIZ).porqueNao(PEDIDO, "{\"predicado\":\"x\"}", 4));
        assertEquals("erro", r.path("estado").asString(), r.toString());
        assertEquals("prolog", r.path("motor").asString(), r.toString());
        assertTrue(r.path("erro").asString().startsWith("o facto do porque nao"), r.toString());
    }

    /**
     * Verifica que um pedido que nao e um objeto JSON da erro do contrato sem lancar o swipl.
     */
    @Test
    void porqueNaoComPedidoQueNaoEObjeto() {
        JsonNode r = le(adapter("swipl-que-nao-existe", RAIZ).porqueNao("[1]", EXPLORACAO, 4));
        assertErro(r);
        assertEquals("o pedido tem de ser um objeto JSON", r.path("erro").asString());
    }

    /**
     * Verifica que o porque nao usa os mesmos lugares que o infere (AC7): com o unico lugar ocupado por um infere e
     * sem espera, o porque nao da "ocupado".
     * @param tmp
     * @throws Exception
     */
    @Test
    void porqueNaoPartilhaOsLugaresComOInfere(@TempDir Path tmp) throws Exception {
        String lento = "main :- sleep(2), write('{\"estado\":\"concluido\",\"motor\":\"prolog\"}'), nl.";
        PrologAdapter a = adapter(homeFalsa(tmp, lento), Duration.ofSeconds(10), 1, Duration.ZERO);

        CompletableFuture<String> primeiro = CompletableFuture.supplyAsync(() -> a.infere(PEDIDO));
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ProcessHandle.current().children().noneMatch(ProcessHandle::isAlive)) {
            assertTrue(System.nanoTime() < limite, "o primeiro pedido nao lancou o swipl");
            Thread.sleep(10);
        }

        JsonNode segundo = le(a.porqueNao(PEDIDO, EXPLORACAO, 4));
        assertErro(segundo);
        assertTrue(segundo.path("erro").asString().startsWith("ocupado"), segundo.toString());
        assertEquals("concluido", le(primeiro.get(10, TimeUnit.SECONDS)).path("estado").asString());
    }
}
