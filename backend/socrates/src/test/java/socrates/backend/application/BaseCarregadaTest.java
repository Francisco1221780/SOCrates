package socrates.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class BaseCarregadaTest {

    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Verifica que a versao vem do cenarios.json real (a mesma do factos_iniciais.json do Drools: as duas vem do
     * cabecalho do soc_factos.txt) e que o catalogo tem os cenarios e as sequencias.
     */
    @Test
    void leAVersaoDoFicheiroReal() {
        BaseCarregada base = new BaseCarregada(json);
        assertEquals(socrates.motores.drools.MotorDrools.VERSAO_KB, base.versaoKB());
        assertFalse(base.versaoKB().isBlank());
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
}
