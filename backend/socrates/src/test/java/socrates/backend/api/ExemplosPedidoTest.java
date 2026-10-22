package socrates.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import socrates.backend.application.BaseCarregada;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os exemplos de pedido e de resposta do Swagger UI sao JSON valido e da versao da base carregada: quando a versao
 * da base mudar, este teste falha ate os exemplos serem atualizados.
 */
class ExemplosPedidoTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void exemplosSaoJsonDaVersaoDaBase() {
        String versao = new BaseCarregada(json).versaoKB();
        for (String exemplo : List.of(ExemplosPedido.INFER, ExemplosPedido.INFER_COM_RESPOSTA, ExemplosPedido.COMO,
                ExemplosPedido.PORQUE_PERGUNTA, ExemplosPedido.WHYNOT)) {
            JsonNode n = json.readTree(exemplo);
            assertEquals(versao, n.path("versaoKB").asString(), exemplo);
            assertTrue(n.path("passos").isArray() && !n.path("passos").isEmpty(), exemplo);
        }
    }

    @Test
    void exemplosDeRespostaSaoJsonDaVersaoDaBase() {
        String versao = new BaseCarregada(json).versaoKB();
        for (String exemplo : List.of(ExemplosResposta.INFER_200, ExemplosResposta.COMO_200,
                ExemplosResposta.PORQUE_PERGUNTA_200, ExemplosResposta.WHYNOT_200, ExemplosResposta.ERRO_400,
                ExemplosResposta.ERRO_404, ExemplosResposta.ERRO_422, ExemplosResposta.ERRO_500,
                ExemplosResposta.ERRO_503, ExemplosResposta.ERRO_504)) {
            JsonNode n = json.readTree(exemplo);
            assertTrue(n.has("estado") && n.has("motor") && n.has("versaoKB"), exemplo);
            if (!n.path("versaoKB").isNull()) {
                assertEquals(versao, n.path("versaoKB").asString(), exemplo);
            }
        }
        assertEquals(415, json.readTree(ExemplosResposta.ERRO_415).path("status").asInt());
    }
}
