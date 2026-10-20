package socrates.backend.engine.prolog;

import socrates.backend.config.PrologProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Instala o motor Prolog: copia os ficheiros do classpath para uma pasta temporaria, se nao houver uma pasta home configurada.
 * A pasta temporaria e apagada quando o Spring fecha a aplicacao.
 */
@Component
public class PrologInstaller implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PrologInstaller.class);
    private static final String RAIZ = "prolog-dist/";

    private final Path home;
    private final Path temporaria;
    private final String erro;

    public PrologInstaller(PrologProperties prologProperties) {
        Path h = null;
        Path t = null;
        String e = null;
        if (prologProperties.home() != null) {
            h = prologProperties.home().toAbsolutePath().normalize();
        } else {
            try {
                t = Files.createTempDirectory("socrates-prolog-");
                int n = extrai(t);
                if (!Files.isRegularFile(t.resolve("motores/prolog/wrapper.pl"))) {
                    throw new IOException("o classpath nao tem " + RAIZ + "motores/prolog/wrapper.pl");
                }
                h = t;
                log.info("Motor prolog: {} ficheiros copiados do classpath para {}", n, t);
            } catch (IOException | UncheckedIOException ex) {
                e = "nao foi possivel copiar os ficheiros do motor prolog: " + ex.getMessage();
                log.error(e, ex);
                apaga(t);
                t = null;
            }
        }
        this.home = h;
        this.temporaria = t;
        this.erro = e;
    }

    public Path home() {
        return home;
    }

    public String erro() {
        return erro;
    }

    /**
     * Extrai os ficheiros do classpath para a pasta destino, mantendo a estrutura de pastas.
     * @param destino
     * @return
     * @throws IOException
     */
    private static int extrai(Path destino) throws IOException {
        Resource[] recursos = new PathMatchingResourcePatternResolver().getResources("classpath*:" + RAIZ + "**");
        int n = 0;
        for (Resource r : recursos) {
            String url = r.getURL().toString();
            int i = url.lastIndexOf("/" + RAIZ);
            if (i < 0) {
                continue;
            }
            String relativo = URLDecoder.decode(url.substring(i + RAIZ.length() + 1).replace("+", "%2B"),
                    StandardCharsets.UTF_8);
            if (relativo.isEmpty() || relativo.endsWith("/") || !r.isReadable()) {
                continue;
            }
            Path alvo = destino.resolve(relativo).normalize();
            if (!alvo.startsWith(destino)) {
                throw new IOException("caminho fora da pasta de destino: " + relativo);
            }
            Files.createDirectories(alvo.getParent());
            try (InputStream in = r.getInputStream()) {
                Files.copy(in, alvo, StandardCopyOption.REPLACE_EXISTING);
            }
            n++;
        }
        return n;
    }

    /**
     * Apaga a pasta temporaria criada para instalar o motor Prolog, se houver.
     */
    @Override
    public void destroy() {
        apaga(temporaria);
    }

    /**
     * Apaga a pasta e todo o seu conteudo, se existir.
     * @param pasta
     */
    private static void apaga(Path pasta) {
        if (pasta == null || !Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    log.warn("nao foi possivel apagar {}", p, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("nao foi possivel apagar {}", pasta, ex);
        }
    }
}
