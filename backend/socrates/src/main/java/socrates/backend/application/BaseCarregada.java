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
 * partir do soc_factos.txt, por isso a versao vem da fonte unica da base e nao de um valor escrito a mao. Tem tambem
 * as regras (o regras.json do modulo Drools, gerado do soc_regras.txt), que o GET /api/regras devolve.
 */
@Component
public class BaseCarregada {

    private static final String RECURSO = "/generated/cenarios.json";
    private static final String REGRAS = "/regras.json";

    private final String cenariosJson;
    private final String versaoKB;
    private final String regrasJson;

    /**
     * Le o cenarios.json e o regras.json do classpath, uma vez, no arranque.
     * @param jsonMapper
     */
    @Autowired
    public BaseCarregada(JsonMapper jsonMapper) {
        this(jsonMapper, le(RECURSO));
    }

    /**
     * Usa o catalogo dado em vez do ficheiro (para os testes), com as regras do classpath.
     * @param jsonMapper
     * @param cenariosJson
     */
    BaseCarregada(JsonMapper jsonMapper, String cenariosJson) {
        this(jsonMapper, cenariosJson, le(REGRAS));
    }

    /**
     * Usa o conteudo dado em vez dos ficheiros (para os testes). Sem versaoKB a aplicacao nao arranca: nenhum pedido
     * podia ser validado, para nenhum dos motores.
     * @param jsonMapper
     * @param cenariosJson
     * @param regrasJson
     */
    BaseCarregada(JsonMapper jsonMapper, String cenariosJson, String regrasJson) {
        String versao = jsonMapper.readTree(cenariosJson).path("versaoKB").asString();
        if (versao.isBlank()) {
            throw new IllegalStateException("o catalogo de cenarios nao tem versaoKB");
        }
        this.cenariosJson = cenariosJson;
        this.versaoKB = versao;
        this.regrasJson = regrasJson;
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
     * O regras.json tal como esta no ficheiro: {versaoKB, regras: [{id, se, entao}]}, cada regra no mesmo formato da
     * regraCompleta do /como?regras=true.
     * @return
     */
    public String regrasJson() {
        return regrasJson;
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
