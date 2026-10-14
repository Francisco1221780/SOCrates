package cyberrangers.cyber.engine.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroolsAdapterTest {

    private final DroolsAdapter adapter = new DroolsAdapter();

    @Test
    void chamaOMotorDrools() {
        String pedido = """
                {"versaoKB":"6","passos":[{"id":"p1","factos":[
                  {"predicado":"alerta_ids","args":["ev_e1","web_srv_04","sig_x"]}]}]}
                """;
        assertEquals("drools", adapter.nome());
        assertTrue(adapter.infere(pedido).contains("exemplo_conclusao"));
    }
}
