package cyberrangers.cyber.engine.prolog;

import cyberrangers.cyber.application.MotorInferencia;
import cyberrangers.cyber.config.PrologProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class PrologAdapter implements MotorInferencia {
    private static final Logger log = LoggerFactory.getLogger(PrologAdapter.class);
    private final PrologProperties prologProperties;
    private final JsonMapper json;
    private final Semaphore lugares;

    public PrologAdapter(PrologProperties prologProperties, JsonMapper json) {
        this.prologProperties = prologProperties;
        this.json = json;
        this.lugares = new Semaphore(prologProperties.maxProcessos(), true);
    }

    @Override
    public String nome() {
        return "prolog";
    }

    /**
     * Executa o wrapper.pl com o pedido JSON se houver um lugar livre, devolvendo a resposta JSON ou uma mensagem de erro.
     * Se os cyber.prolog.max-processos lugares estiverem ocupados, espera no maximo cyber.prolog.espera e depois
     * devolve o erro "ocupado".
     * @param pedidoJson
     * @return
     */
    @Override
    public String infere(String pedidoJson) {
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
            return corre(pedidoJson);
        } finally {
                lugares.release();
        }
    }

    private String erro(String mensagem) {
        return json.writeValueAsString(json.createObjectNode().put("estado", "erro").put("motor", nome()).putNull("versaoKB").put("erro", mensagem));
    }

    /**
     * Executa o wrapper.pl com o pedido JSON, devolvendo a resposta JSON ou uma mensagem de erro.
     * @param pedidoJson
     * @return
     */
    private String corre(String pedidoJson) {
        if (prologProperties.home() == null) {
            return erro("cyber.prolog.home nao configurado");
        }
        Path raiz = prologProperties.home().toAbsolutePath().normalize();
        Path wrapper = raiz.resolve("motores/prolog/wrapper.pl");
        Path base = raiz.resolve("base_conhecimento");
        if (!Files.isRegularFile(wrapper) || !Files.isDirectory(base)) {
            return erro("cyber.prolog.home nao contem wrapper.pl ou base_conhecimento: " + raiz);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(prologProperties.swipl(), wrapper.toString(), base.toString());
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            return erro("swipl nao encontrado ou falhou ao iniciar: " + e.getMessage());
        }
        try (ExecutorService leitores = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> saida = leitores.submit(() ->
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<List<String>> erros = leitores.submit(() ->
                    process.errorReader(StandardCharsets.UTF_8).lines()
                            .peek(linha -> log.debug("swipl: {}", linha))
                            .toList());

            try (Writer stdin = process.outputWriter(StandardCharsets.UTF_8)) {
                stdin.write(pedidoJson);
            } catch (IOException e) {
                log.debug("swipl fechou o stdin antes do fim do pedido", e);
            }

            if (!process.waitFor(prologProperties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                mata(process);
                return erro("timeout");
            }

            String stdout = saida.get();
            List<String> stderr = erros.get();
            if (eObjetoJson(stdout)) {
                return stdout.strip();
            }
            String ultima = stderr.isEmpty() ? "stderr vazio" : stderr.getLast();
            return erro("o motor prolog terminou sem resposta JSON (codigo " + process.exitValue() + "): " + ultima);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return erro("interrompido");
        } catch (ExecutionException e) {
            return erro("erro do adaptador ao ler a resposta: " + e.getCause());
        } finally {
            if (process.isAlive()) {
                mata(process);
            }
        }
    }

    /**
     * Verifica se o texto é um objeto JSON válido.
     * @param texto
     * @return
     */
    private boolean eObjetoJson(String texto) {
        if (texto.isBlank()) {
            return false;
        }
        try {
            return json.readTree(texto).isObject();
        } catch (JacksonException e) {
            return false;
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
