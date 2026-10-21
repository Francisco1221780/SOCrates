package socrates.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BaseCarregadaTest {

    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Verifica que a versao vem do cenarios.json real e que o catalogo tem os cenarios e as sequencias.
     */
    @Test
    void leAVersaoDoFicheiroReal() {
        BaseCarregada base = new BaseCarregada(json);
        assertEquals("6", base.versaoKB());
        assertTrue(base.cenariosJson().contains("\"cenarios\""));
        assertTrue(base.cenariosJson().contains("\"sequencias\""));
    }

    /**
     * Verifica que o catalogo e devolvido tal como esta no ficheiro, sem ser convertido de ida e volta.
     * @throws Exception
     */
    @Test
    void cenariosVemTalComoNoFicheiro() throws Exception {
        String ficheiro;
        try (InputStream in = getClass().getResourceAsStream("/generated/cenarios.json")) {
            ficheiro = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(ficheiro, new BaseCarregada(json).cenariosJson());
    }

    /**
     * Verifica que um catalogo sem versaoKB impede o arranque.
     */
    @Test
    void semVersaoNaoArranca() {
        for (String semVersao : new String[] {"{\"cenarios\":[]}", "{\"versaoKB\":\"\"}", "{\"versaoKB\":null}"}) {
            assertThrows(IllegalStateException.class, () -> new BaseCarregada(json, semVersao), semVersao);
        }
    }

    /**
     * Verifica que um catalogo que nao e JSON impede o arranque.
     */
    @Test
    void jsonInvalidoNaoArranca() {
        assertThrows(JacksonException.class, () -> new BaseCarregada(json, "{nao e json"));
    }

    /**
     * Verifica que as regras sao o regras.json do classpath tal como esta, com as 92 regras da base, e que sao da
     * mesma versaoKB que o catalogo de cenarios (os dois vem do gera_json.pl).
     * @throws Exception
     */
    @Test
    void regrasVemDoRegrasJsonComAMesmaVersao() throws Exception {
        String ficheiro;
        try (InputStream in = getClass().getResourceAsStream("/regras.json")) {
            ficheiro = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        BaseCarregada base = new BaseCarregada(json);
        assertEquals(ficheiro, base.regrasJson());
        JsonNode regras = json.readTree(base.regrasJson());
        assertEquals(base.versaoKB(), regras.path("versaoKB").asString());
        assertEquals(92, regras.path("regras").size());
        JsonNode primeira = regras.path("regras").get(0);
        assertTrue(primeira.has("id") && primeira.path("se").isArray() && primeira.path("entao").isArray(),
                primeira.toString());
    }
}
