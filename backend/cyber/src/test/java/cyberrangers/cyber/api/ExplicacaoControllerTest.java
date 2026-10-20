package cyberrangers.cyber.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import cyberrangers.cyber.application.InferenceService;
import cyberrangers.cyber.domain.Resultado;
import cyberrangers.cyber.domain.TipoResultado;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Testes da camada HTTP das explicacoes: cada caminho chama o metodo certo do InferenceService (um mock), com o motor
 * e o corpo tal como vieram, e o resultado da o codigo HTTP do HttpResponseUtils.
 */
@WebMvcTest(ExplicacaoController.class)
class ExplicacaoControllerTest {

    private static final String CORPO = "{\"versaoKB\":\"6\",\"passos\":[],\"facto\":{\"numero\":2}}";
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
     * Verifica que cada caminho chama o seu metodo do servico com o motor e o corpo tal como vieram, e devolve o
     * JSON do resultado.
     * @throws Exception
     */
    @Test
    void cadaCaminhoChamaOSeuMetodo() throws Exception {
        Resultado ok = new Resultado(TipoResultado.OK, RESPOSTA);
        when(inferenceService.como(anyString(), anyString())).thenReturn(ok);
        when(inferenceService.porque(anyString(), anyString())).thenReturn(ok);
        when(inferenceService.porquePergunta(anyString(), anyString())).thenReturn(ok);
        when(inferenceService.porqueNao(anyString(), anyString())).thenReturn(ok);
        for (String caminho : new String[] {"como", "porque", "porque-pergunta", "whynot"}) {
            MvcResult r = envia("/api/prolog/" + caminho, CORPO, MediaType.APPLICATION_JSON);
            assertEquals(200, r.getResponse().getStatus(), caminho);
            assertEquals(RESPOSTA, r.getResponse().getContentAsString(StandardCharsets.UTF_8), caminho);
        }
        verify(inferenceService).como("prolog", CORPO);
        verify(inferenceService).porque("prolog", CORPO);
        verify(inferenceService).porquePergunta("prolog", CORPO);
        verify(inferenceService).porqueNao("prolog", CORPO);
    }

    /**
     * Verifica que um motor sem porque nao (NAO_SUPORTADO) da 501 com o erro do contrato no corpo.
     * @throws Exception
     */
    @Test
    void naoSuportadoDa501() throws Exception {
        String erro = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":null,\"erro\":\"nao suportado\"}";
        when(inferenceService.porqueNao(anyString(), anyString())).thenReturn(new Resultado(TipoResultado.NAO_SUPORTADO, erro));
        MvcResult r = envia("/api/prolog/whynot", CORPO, MediaType.APPLICATION_JSON);
        assertEquals(501, r.getResponse().getStatus());
        assertEquals(erro, r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * Verifica que um facto que nao esta na resposta (NAO_ENCONTRADO) da 422 com o erro do contrato no corpo.
     * @throws Exception
     */
    @Test
    void naoEncontradoDa422() throws Exception {
        String erro = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":\"6\",\"erro\":\"esse facto nao esta\"}";
        when(inferenceService.como(anyString(), anyString())).thenReturn(new Resultado(TipoResultado.NAO_ENCONTRADO, erro));
        MvcResult r = envia("/api/prolog/como", CORPO, MediaType.APPLICATION_JSON);
        assertEquals(422, r.getResponse().getStatus());
        assertEquals(erro, r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * Verifica que um pedido sem corpo chega ao servico como texto vazio.
     * @throws Exception
     */
    @Test
    void pedidoSemCorpoChegaAoServicoComoTextoVazio() throws Exception {
        when(inferenceService.porque("drools", "")).thenReturn(new Resultado(TipoResultado.PEDIDO_INVALIDO, RESPOSTA));
        MvcResult r = envia("/api/drools/porque", null, MediaType.APPLICATION_JSON);
        assertEquals(400, r.getResponse().getStatus());
        verify(inferenceService).porque("drools", "");
    }

    /**
     * Verifica que um pedido que nao e application/json da 415, sem chegar ao servico.
     * @throws Exception
     */
    @Test
    void contentTypeQueNaoEJsonDa415() throws Exception {
        for (String caminho : new String[] {"como", "porque", "porque-pergunta", "whynot"}) {
            MvcResult r = envia("/api/prolog/" + caminho, CORPO, MediaType.TEXT_PLAIN);
            assertEquals(415, r.getResponse().getStatus(), caminho);
        }
        verifyNoInteractions(inferenceService);
    }
}
