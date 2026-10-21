package socrates.backend.engine.prolog;

import socrates.backend.application.MotorInferencia;
import socrates.backend.config.PrologProperties;
import socrates.backend.domain.SaudeMotor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class PrologAdapter implements MotorInferencia {
    private static final Logger log = LoggerFactory.getLogger(PrologAdapter.class);

    /**
     * Pedido com uma versaoKB que nenhuma base tem: o wrapper carrega a base, recusa o pedido e responde com a versao
     * que tem carregada. Assim a verificacao usa o wrapper tal como os pedidos o usam.
     */
    private static final String SONDA = "{\"versaoKB\":\"\",\"passos\":[]}";
    private static final int MAX_LINHAS = 10;

    private final PrologProperties prologProperties;
    private final JsonMapper json;
    private final Semaphore lugares;
    private final PrologInstaller prologInstaller;
    private volatile SaudeMotor estado;

    public PrologAdapter(PrologProperties prologProperties, JsonMapper json, PrologInstaller prologInstaller) {
        this.prologProperties = prologProperties;
        this.json = json;
        this.prologInstaller = prologInstaller;
        this.lugares = new Semaphore(prologProperties.maxProcessos(), true);
    }

    @Override
    public String nome() {
        return "prolog";
    }

    /**
     * Executa o wrapper.pl com o pedido JSON se houver um lugar livre, devolvendo a resposta JSON ou uma mensagem de erro.
     * Se os socrates.prolog.max-processos lugares estiverem ocupados, espera no maximo socrates.prolog.espera e depois
     * devolve o erro "ocupado".
     * @param pedidoJson
     * @return
     */
    @Override
    public String infere(String pedidoJson) {
        return comLugar(() -> corre(pedidoJson, List.of()));
    }

    /**
     * Corre o wrapper.pl no modo whynot ("porque nao?", issue #7): o pedido vai pelo stdin com o facto e a profundidade
     * (nunca na linha de comandos, AC1), e a resposta e a do wrapper, no formato do MotorDrools.porqueNao. Usa os mesmos
     * lugares que o infere (AC7).
     * @param pedidoJson
     * @param factoJson
     * @param profundidade
     * @return
     */
    @Override
    public String porqueNao(String pedidoJson, String factoJson, int profundidade) {
        String pedido;
        try {
            JsonNode corpo = json.readTree(pedidoJson);
            if (!(corpo instanceof ObjectNode o)) {
                return erro("o pedido tem de ser um objeto JSON");
            }
            o.set("facto", json.readTree(factoJson));
            o.put("profundidade", profundidade);
            pedido = json.writeValueAsString(o);
        } catch (JacksonException e) {
            return erro("pedido JSON invalido: " + e.getOriginalMessage());
        }
        return comLugar(() -> corre(pedido, List.of("whynot")));
    }

    /**
     * Corre o pedido se houver um lugar livre. Se os socrates.prolog.max-processos lugares estiverem ocupados, espera
     * no maximo socrates.prolog.espera e depois devolve o erro "ocupado".
     * @param pedido
     * @return
     */
    private String comLugar(Supplier<String> pedido) {
        boolean temLugar;
        try {
            temLugar = lugares.tryAcquire(prologProperties.espera().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return erro("interrompido");
        }
        if (!temLugar) {
            return erro("ocupado: " + prologProperties.maxProcessos() + " pedidos prolog em curso");
        }
        try {
            return pedido.get();
        } finally {
            lugares.release();
        }
    }

    /**
     * Verifica o motor no arranque, para o estado ficar no log e o primeiro /api/health nao ter de esperar.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verificaNoArranque() {
        SaudeMotor e = estado();
        if (e.pronto()) {
            log.info("Motor prolog pronto: base {} carregada", e.versaoKB());
        } else {
            log.warn("Motor prolog nao esta pronto: {}", String.join("; ", e.erros()));
        }
    }

    /**
     * O estado do motor (AC13): se a base carrega sem erros e a versao que o wrapper tem carregada. A verificacao lanca
     * um processo swipl, por isso corre uma vez e fica guardada: a base nao muda com a aplicacao a correr (para ver uma alteracao na pasta de socrates.prolog.home,
     * reinicia-se a aplicacao).
     * @return
     */
    @Override
    public SaudeMotor estado() {
        SaudeMotor e = estado;
        if (e != null) {
            return e;
        }
        synchronized (this) {
            if (estado == null) {
                try {
                    estado = verifica();
                } catch (InterruptedException ex) {
                    // nao fica guardado: a proxima chamada verifica outra vez
                    Thread.currentThread().interrupt();
                    return new SaudeMotor(nome(), false, null, false, List.of("interrompido"));
                }
            }
            return estado;
        }
    }

    /**
     * Corre a sonda no wrapper.pl: a base carrega sem erros e qual e a versao.
     * @return
     * @throws InterruptedException
     */
    private SaudeMotor verifica() throws InterruptedException {
        String erroPastas = verificaPastas();
        if (erroPastas != null) {
            return new SaudeMotor(nome(), false, null, false, List.of(erroPastas));
        }
        List<String> erros = new ArrayList<>();
        Saida sonda;
        try {
            sonda = executa(List.of(prologProperties.swipl(), wrapper().toString(), base().toString()), SONDA);
        } catch (IOException e) {
            return new SaudeMotor(nome(), false, null, false, List.of("swipl nao encontrado: " + e.getMessage()));
        } catch (TimeoutException e) {
            erros.add("a base nao carregou dentro do timeout (" + prologProperties.timeout() + ")");
            sonda = null;
        } catch (ExecutionException e) {
            erros.add("erro do adaptador ao ler a resposta: " + e.getCause());
            sonda = null;
        }

        String versao = null;
        boolean carregada = false;
        if (sonda != null) {
            // o consult escreve os erros de sintaxe da base no stderr, como "ERROR: ...", e continua
            List<String> errosBase = limita(sonda.stderr().stream()
                    .filter(l -> l.startsWith("ERROR")).map(l -> "base: " + l).toList());
            JsonNode r = objetoJson(sonda.stdout());
            String erroWrapper = r == null ? "" : r.path("erro").asString();
            if (erroWrapper.startsWith("versaoKB desconhecida")) {
                JsonNode v = r.path("versaoKB");
                versao = v.isString() ? v.asString() : null;
                carregada = errosBase.isEmpty();
            } else {
                erros.add("a base nao carregou: " + (erroWrapper.isBlank() ? semJson(sonda) : erroWrapper));
            }
            erros.addAll(errosBase);
        }

        boolean pronto = carregada && erros.isEmpty();
        return new SaudeMotor(nome(), pronto, versao, carregada, erros);
    }

    /**
     * As primeiras MAX_LINHAS linhas e, se houver mais, uma linha a dizer quantas ficaram de fora.
     * @param linhas
     * @return
     */
    private static List<String> limita(List<String> linhas) {
        if (linhas.size() <= MAX_LINHAS) {
            return linhas;
        }
        List<String> r = new ArrayList<>(linhas.subList(0, MAX_LINHAS));
        r.add("(... e mais " + (linhas.size() - MAX_LINHAS) + ")");
        return r;
    }

    private String erro(String mensagem) {
        return json.writeValueAsString(json.createObjectNode().put("estado", "erro").put("motor", nome()).putNull("versaoKB").put("erro", mensagem));
    }

    private Path raiz() {
        return prologInstaller.home().toAbsolutePath().normalize();
    }

    private Path wrapper() {
        return raiz().resolve("motores/prolog/wrapper.pl");
    }

    private Path base() {
        return raiz().resolve("base_conhecimento");
    }

    /**
     * Confirma que ha pasta do motor e que tem o wrapper.pl e a base_conhecimento.
     * @return a razao se faltar alguma coisa; null se estiver tudo
     */
    private String verificaPastas() {
        if (prologInstaller.home() == null) {
            return prologInstaller.erro();
        }
        if (!Files.isRegularFile(wrapper()) || !Files.isDirectory(base())) {
            return "socrates.prolog.home nao contem wrapper.pl ou base_conhecimento: " + raiz();
        }
        return null;
    }

    /**
     * Executa o wrapper.pl com o pedido JSON e o modo dado (nenhum para inferir, "whynot" para o porque nao),
     * devolvendo a resposta JSON ou uma mensagem de erro.
     * @param pedidoJson
     * @param modo
     * @return
     */
    private String corre(String pedidoJson, List<String> modo) {
        String erroPastas = verificaPastas();
        if (erroPastas != null) {
            return erro(erroPastas);
        }
        Saida s;
        try {
            List<String> comando = new ArrayList<>(
                    List.of(prologProperties.swipl(), wrapper().toString(), base().toString()));
            comando.addAll(modo);
            s = executa(comando, pedidoJson);
        } catch (IOException e) {
            return erro("swipl nao encontrado ou falhou ao iniciar: " + e.getMessage());
        } catch (TimeoutException e) {
            return erro("timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return erro("interrompido");
        } catch (ExecutionException e) {
            return erro("erro do adaptador ao ler a resposta: " + e.getCause());
        }
        if (objetoJson(s.stdout()) != null) {
            return s.stdout().strip();
        }
        return erro(semJson(s));
    }

    private static String semJson(Saida s) {
        String ultima = s.stderr().isEmpty() ? "stderr vazio" : s.stderr().getLast();
        return "o motor prolog terminou sem resposta JSON (codigo " + s.codigo() + "): " + ultima;
    }

    /** O que um processo deixou: o codigo de saida, o stdout inteiro e as linhas do stderr. */
    private record Saida(int codigo, String stdout, List<String> stderr) { }

    /**
     * Lanca o comando com argumentos separados (AC1), escreve a entrada no stdin e le o stdout e o stderr em paralelo
     * (AC4), tudo em UTF-8 (AC2). Ao fim do socrates.prolog.timeout o processo e terminado (AC5). Em qualquer caso, nao
     * fica nenhum processo vivo.
     * @param comando
     * @param entrada
     * @return
     * @throws IOException          o processo nao arrancou (ex.: swipl em falta)
     * @throws TimeoutException     o processo nao terminou dentro do timeout (ja foi terminado)
     * @throws InterruptedException
     * @throws ExecutionException   falhou a leitura do stdout ou do stderr
     */
    private Saida executa(List<String> comando, String entrada)
            throws IOException, TimeoutException, InterruptedException, ExecutionException {
        Process process = new ProcessBuilder(comando).start();
        ExecutorService leitores = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<String> saida = leitores.submit(() ->
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<List<String>> erros = leitores.submit(() ->
                    process.errorReader(StandardCharsets.UTF_8).lines()
                            .peek(linha -> log.debug("swipl: {}", linha))
                            .toList());

            try (Writer stdin = process.outputWriter(StandardCharsets.UTF_8)) {
                stdin.write(entrada);
            } catch (IOException e) {
                log.debug("swipl fechou o stdin antes do fim do pedido", e);
            }

            if (!process.waitFor(prologProperties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TimeoutException();
            }
            return new Saida(process.exitValue(), saida.get(), erros.get());
        } finally {
            if (process.isAlive()) {
                mata(process);
            }
            leitores.close();
        }
    }

    /**
     * O texto como objeto JSON, ou null se nao for um objeto JSON valido.
     * @param texto
     * @return
     */
    private JsonNode objetoJson(String texto) {
        if (texto.isBlank()) {
            return null;
        }
        try {
            JsonNode n = json.readTree(texto);
            return n.isObject() ? n : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    /**
     * Mata os descendentes e o processo, e espera que o processo termine (nos descendentes nao espera).
     * @param process
     */
    private static void mata(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
