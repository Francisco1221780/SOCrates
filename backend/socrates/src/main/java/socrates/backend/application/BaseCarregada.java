package socrates.backend.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A base de conhecimento em uso, tal como o gera_json.pl a descreve no cenarios.json: a versao (que o
 * RequestValidator compara com a do pedido) e os cenarios (que o GET /api/cenarios devolve). O ficheiro e gerado a
 * partir do soc_factos.txt, por isso a versao vem da fonte unica da base e nao de um valor escrito a mao.
 */
@Component
public class BaseCarregada {

    private static final String RECURSO = "/generated/cenarios.json";

    private final String cenariosJson;
    private final String versaoKB;

    /**
     * Le o cenarios.json do classpath, uma vez, no arranque.
     * @param jsonMapper
     */
    @Autowired
    public BaseCarregada(JsonMapper jsonMapper) {
        this(jsonMapper, le(RECURSO));
    }

    /**
     * Usa o conteudo dado em vez do ficheiro (para os testes). Sem versaoKB a aplicacao nao arranca: nenhum pedido
     * podia ser validado, para nenhum dos motores.
     * @param jsonMapper
     * @param cenariosJson
     */
    BaseCarregada(JsonMapper jsonMapper, String cenariosJson) {
        String versao = jsonMapper.readTree(cenariosJson).path("versaoKB").asString();
        if (versao.isBlank()) {
            throw new IllegalStateException("o catalogo de cenarios nao tem versaoKB");
        }
        this.cenariosJson = cenariosJson;
        this.versaoKB = versao;
    }

    /**
     * A versao da base carregada, por exemplo "6".
     * @return
     */
    public String versaoKB() {
        return versaoKB;
    }

    /**
     * O cenarios.json tal como esta no ficheiro.
     * @return
     */
    public String cenariosJson() {
        return cenariosJson;
    }

    /**
     * Le um recurso de texto do classpath (pasta em target/classes ou BOOT-INF/classes no jar).
     * @param recurso
     * @return
     */
    private static String le(String recurso) {
        try (InputStream in = BaseCarregada.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("falta " + recurso + " no classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
