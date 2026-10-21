package socrates.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import socrates.backend.application.BaseCarregada;
import socrates.backend.application.InferenceService;
import socrates.backend.domain.Metaconhecimento;
import socrates.backend.domain.SaudeMotor;
import socrates.backend.domain.SaudeSistema;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testes da camada HTTP do GET /api/cenarios e do GET /api/health; o InferenceService e a BaseCarregada sao mocks.
 */
@WebMvcTest(SystemController.class)
class SystemControllerTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InferenceService inferenceService;

    @MockitoBean
    private BaseCarregada baseCarregada;

    /**
     * Faz um GET e devolve o resultado.
     * @param caminho
     * @return
     * @throws Exception
     */
    private MvcResult le(String caminho) throws Exception {
        return mvc.perform(get(caminho)).andReturn();
    }

    /**
     * Verifica que o GET /api/cenarios devolve o catalogo tal como a BaseCarregada o tem (sem conversao), em JSON.
     * @throws Exception
     */
    @Test
    void cenariosVemTalComoNaBase() throws Exception {
        String catalogo = "{ \"versaoKB\" : \"6\", \"cenarios\" : [ {\"id\":\"c1\",\"descricao\":\"ação\"} ] }";
        when(baseCarregada.cenariosJson()).thenReturn(catalogo);
        MvcResult r = le("/api/cenarios");
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(catalogo, r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(MediaType.parseMediaType(r.getResponse().getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /**
     * Verifica a forma da resposta do GET /api/health: pronto, versaoKB e a lista dos motores com os seus campos.
     * @throws Exception
     */
    @Test
    void healthTemAFormaCombinada() throws Exception {
        when(inferenceService.saude()).thenReturn(new SaudeSistema(true, "6", List.of(
                new SaudeMotor("drools", true, "6", true, Metaconhecimento.NAO_APLICAVEL, List.of()),
                new SaudeMotor("prolog", true, "6", true, Metaconhecimento.EM_DIA, List.of()))));
        MvcResult r = le("/api/health");
        assertEquals(200, r.getResponse().getStatus());
        JsonNode n = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(n.path("pronto").asBoolean());
        assertEquals("6", n.path("versaoKB").asString());
        assertEquals(2, n.path("motores").size());
        JsonNode drools = n.path("motores").get(0);
        assertEquals("drools", drools.path("motor").asString());
        assertTrue(drools.path("pronto").asBoolean());
        assertEquals("6", drools.path("versaoKB").asString());
        assertTrue(drools.path("baseCarregada").asBoolean());
        assertEquals("nao_aplicavel", drools.path("metaconhecimento").asString());
        assertTrue(drools.path("erros").isArray());
        assertEquals("em_dia", n.path("motores").get(1).path("metaconhecimento").asString(), n.toString());
    }

    /**
     * Verifica que o health responde 200 tambem quando o sistema nao esta pronto: o estado vai no corpo, com os erros.
     * @throws Exception
     */
    @Test
    void healthNaoProntoTambemE200ComOsErros() throws Exception {
        when(inferenceService.saude()).thenReturn(new SaudeSistema(false, "6", List.of(
                new SaudeMotor("prolog", false, null, false, Metaconhecimento.DESCONHECIDO, List.of("swipl nao encontrado: x")))));
        MvcResult r = le("/api/health");
        assertEquals(200, r.getResponse().getStatus());
        JsonNode n = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(n.path("pronto").asBoolean());
        JsonNode prolog = n.path("motores").get(0);
        assertEquals("swipl nao encontrado: x", prolog.path("erros").get(0).asString());
        assertTrue(prolog.path("versaoKB").isNull(), n.toString());
        assertFalse(prolog.path("baseCarregada").asBoolean());
        assertEquals("desconhecido", prolog.path("metaconhecimento").asString());
    }

    /**
     * Verifica que o GET /api/regras devolve as regras tal como a BaseCarregada as tem (sem conversao), em JSON.
     * @throws Exception
     */
    @Test
    void regrasVemTalComoNaBase() throws Exception {
        String regras = "{ \"versaoKB\" : \"6\", \"regras\" : [ {\"id\":7,\"se\":[],\"entao\":[]} ] }";
        when(baseCarregada.regrasJson()).thenReturn(regras);
        MvcResult r = le("/api/regras");
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(regras, r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(MediaType.parseMediaType(r.getResponse().getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /**
     * Verifica que os endpoints do sistema so aceitam GET (405 para POST).
     * @throws Exception
     */
    @Test
    void soAceitamGet() throws Exception {
        for (String caminho : List.of("/api/cenarios", "/api/regras", "/api/health")) {
            mvc.perform(post(caminho)).andExpect(status().isMethodNotAllowed());
        }
    }
}
