package cyberrangers.motores.drools.explicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cyberrangers.motores.drools.modelo.Facto;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Os dados que a consola usa para escrever a regra inteira: a regra pelo numero e o "se" com o estado de cada condicao. */
class PorqueNaoRegraTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void regraPeloNumeroTemOsNomesOriginaisDasVariaveis() {
        JsonNode r = PorqueNao.regra(13);
        assertEquals(13, r.get("id").asInt());
        assertEquals("exploracao_possivel", r.get("se").get(0).get("f").asText());
        assertEquals("Ev", r.get("se").get(0).get("args").get(0).get("var").asText());
        assertTrue(r.get("se").get(1).has("nao"));
        assertEquals("ataque_dirigido", r.get("entao").get(0).get("f").asText());
        assertNull(PorqueNao.regra(9999));
    }

    @Test
    void linhaRegraTrazOSeCompletoComOEstadoDeCadaCondicao() throws Exception {
        // sem nenhum facto, a regra 13 falha na primeira condicao e as outras (sobre factos) tambem
        JsonNode facto = json.readTree("{\"predicado\":\"ataque_dirigido\",\"args\":[\"ev_x\",\"alvo_x\"]}");
        JsonNode linhas = PorqueNao.explica(facto, List.<Facto>of(), 4);
        JsonNode regra = null;
        for (JsonNode l : linhas) {
            if ("regra".equals(l.get("tipo").asText()) && l.get("regra").asInt() == 13) {
                regra = l;
            }
        }
        assertNotNull(regra, linhas.toString());
        assertEquals(5, regra.get("se").size());
        assertEquals("facto", regra.get("se").get(0).get("tipo").asText());
        assertEquals(false, regra.get("se").get(0).get("verificada").asBoolean());
        assertEquals("nao", regra.get("se").get(1).get("tipo").asText());
        assertEquals(true, regra.get("se").get(1).get("verificada").asBoolean());
        assertEquals(2, regra.get("entao").size());
    }
}
