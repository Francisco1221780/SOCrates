package socrates.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PedidoJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Verifica que o JSON do contrato se le para Pedido, Passo e Facto, com os args como estao (atomo, numero e
     * termo composto).
     */
    @Test
    void leOPedidoDoContrato() {
        Pedido p = json.readValue("""
                {"versaoKB":"6","passos":[{"id":"cenario_07","factos":[
                  {"predicado":"versao","args":["web_srv_04_apache",{"f":"v","args":[2,4,49]}]}]}]}
                """, Pedido.class);
        assertEquals("6", p.versaoKB());
        Passo passo = p.passos().getFirst();
        assertEquals("cenario_07", passo.id());
        Facto f = passo.factos().getFirst();
        assertEquals("versao", f.predicado());
        assertEquals(2, f.args().size());
        assertEquals("web_srv_04_apache", f.args().get(0).asString());
        assertEquals("v", f.args().get(1).path("f").asString());
    }

    /**
     * Verifica que chaves a mais (por exemplo, uma descricao enviada pela UI) sao ignoradas em vez de recusarem o
     * pedido.
     */
    @Test
    void chavesAMaisSaoIgnoradas() {
        Pedido p = json.readValue("""
                {"versaoKB":"6","extra":1,"passos":[{"id":"p1","descricao":"x","factos":[
                  {"predicado":"nota","args":[],"comentario":"y"}]}]}
                """, Pedido.class);
        assertEquals("nota", p.passos().getFirst().factos().getFirst().predicado());
    }

    /**
     * Verifica que chaves em falta ficam a null (e o RequestValidator trata delas), e que um id numerico passa a
     * texto, como no Drools.
     */
    @Test
    void chavesEmFaltaFicamNullEIdNumericoPassaATexto() {
        Pedido p = json.readValue("""
                {"passos":[{"id":1,"factos":[{"predicado":"nota"}]}]}
                """, Pedido.class);
        assertNull(p.versaoKB());
        assertEquals("1", p.passos().getFirst().id());
        assertNull(p.passos().getFirst().factos().getFirst().args());
        assertTrue(p.passos().size() == 1);
    }
}
