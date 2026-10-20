package cyberrangers.cyber.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cyberrangers.cyber.application.InferenceService;
import cyberrangers.cyber.domain.Resultado;
import cyberrangers.cyber.domain.TipoResultado;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Testes da camada HTTP do POST /api/{motor}/infer: so o controller e a conversao de mensagens do Spring; o
 * InferenceService e um mock, por isso nao ha motores nem validacao aqui.
 */
@WebMvcTest(InferController.class)
class InferControllerTest {

    private static final String PEDIDO = "{\"versaoKB\":\"6\",\"passos\":[{\"id\":\"p1\",\"factos\":[]}]}";
    private static final String RESPOSTA = "{\"estado\":\"concluido\",\"motor\":\"prolog\",\"versaoKB\":\"6\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InferenceService inferenceService;

    /**
     * Faz o POST com o corpo e o content type dados.
     * @param caminho
     * @param corpo
     * @param tipo
     * @return
     * @throws Exception
     */
    private MvcResult envia(String caminho, String corpo, MediaType tipo) throws Exception {
        MockHttpServletRequestBuilder pedido = post(caminho).contentType(tipo);
        if (corpo != null) {
            pedido.content(corpo.getBytes(StandardCharsets.UTF_8));
        }
        return mvc.perform(pedido).andReturn();
    }

    /**
     * Verifica que cada TipoResultado da o codigo HTTP certo e que o corpo e o JSON do resultado, sem alteracoes.
     * @throws Exception
     */
    @Test
    void cadaTipoDaOCodigoHttpCerto() throws Exception {
        Map<TipoResultado, Integer> esperado = Map.of(
                TipoResultado.OK, 200,
                TipoResultado.MOTOR_INEXISTENTE, 404,
                TipoResultado.PEDIDO_INVALIDO, 400,
                TipoResultado.NAO_ENCONTRADO, 422,
                TipoResultado.OCUPADO, 503,
                TipoResultado.TIMEOUT, 504,
                TipoResultado.FALHA_MOTOR, 500);
        assertEquals(TipoResultado.values().length, esperado.size(), "ha um TipoResultado sem codigo esperado");
        for (Map.Entry<TipoResultado, Integer> e : esperado.entrySet()) {
            String json = "{\"tipo\":\"" + e.getKey() + "\"}";
            when(inferenceService.infere(anyString(), anyString())).thenReturn(new Resultado(e.getKey(), json));
            MvcResult r = envia("/api/prolog/infer", PEDIDO, MediaType.APPLICATION_JSON);
            assertEquals(e.getValue(), r.getResponse().getStatus(), e.getKey().toString());
            assertEquals(json, r.getResponse().getContentAsString(StandardCharsets.UTF_8), e.getKey().toString());
            assertTrue(MediaType.parseMediaType(r.getResponse().getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON),
                    r.getResponse().getContentType());
        }
    }

    /**
     * Verifica que o servico recebe o nome do motor do caminho e o corpo exatamente como foi enviado.
     * @throws Exception
     */
    @Test
    void oServicoRecebeOMotorEOCorpoTalComoVieram() throws Exception {
        String corpo = "{ \"passos\" : [], \"versaoKB\" : \"6\", \"n\": 2.50 }";
        when(inferenceService.infere(anyString(), anyString())).thenReturn(new Resultado(TipoResultado.OK, RESPOSTA));
        envia("/api/drools/infer", corpo, MediaType.APPLICATION_JSON);
        verify(inferenceService).infere("drools", corpo);
    }

    /**
     * Verifica que um pedido sem corpo chega ao servico como texto vazio, e que o 400 vem no formato do contrato
     * (o do servico) e nao no formato de erro do Spring.
     * @throws Exception
     */
    @Test
    void pedidoSemCorpoChegaAoServicoComoTextoVazio() throws Exception {
        String erro = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":\"6\",\"erro\":\"pedido JSON invalido\"}";
        when(inferenceService.infere("prolog", "")).thenReturn(new Resultado(TipoResultado.PEDIDO_INVALIDO, erro));
        MvcResult r = envia("/api/prolog/infer", null, MediaType.APPLICATION_JSON);
        assertEquals(400, r.getResponse().getStatus());
        assertEquals(erro, r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        verify(inferenceService).infere("prolog", "");
    }

    /**
     * Verifica que um pedido que nao e application/json da 415, sem chegar ao servico.
     * @throws Exception
     */
    @Test
    void contentTypeQueNaoEJsonDa415() throws Exception {
        MvcResult r = envia("/api/prolog/infer", PEDIDO, MediaType.TEXT_PLAIN);
        assertEquals(415, r.getResponse().getStatus());
        verifyNoInteractions(inferenceService);
    }

    /**
     * Verifica que os acentos vao e voltam certos em UTF-8, no pedido e na resposta (AC2 pela API).
     * @throws Exception
     */
    @Test
    void acentosVaoEVoltamEmUtf8() throws Exception {
        String corpo = "{\"versaoKB\":\"6\",\"passos\":[{\"factos\":[{\"predicado\":\"nota\",\"args\":[\"ação €\"]}]}]}";
        String resposta = "{\"estado\":\"concluido\",\"factos\":[{\"args\":[\"ação €\"]}]}";
        when(inferenceService.infere("prolog", corpo)).thenReturn(new Resultado(TipoResultado.OK, resposta));
        MvcResult r = envia("/api/prolog/infer", corpo, MediaType.APPLICATION_JSON);
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(resposta, new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    /**
     * Verifica que o GET no mesmo caminho nao existe (405): inferir so por POST.
     * @throws Exception
     */
    @Test
    void getNaoEPermitido() throws Exception {
        mvc.perform(get("/api/prolog/infer"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(inferenceService);
    }
}
