package cyberrangers.motores.drools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MotorDroolsTest {

    private final MotorDrools motor = new MotorDrools();

    @Test
    void regraDeExemploDispara() {
        String pedido = """
                {"versaoKB":"6","passos":[{"id":"p1","factos":[
                  {"predicado":"alerta_ids","args":["ev_e1","web_srv_04","sig_x"]}]}]}
                """;
        String resposta = motor.infere(pedido);
        assertTrue(resposta.contains("\"estado\":\"concluido\""), resposta);
        assertTrue(resposta.contains("exemplo_conclusao"), resposta);
    }

    @Test
    void versaoDesconhecidaDaErro() {
        String resposta = motor.infere("{\"versaoKB\":\"9.9\",\"passos\":[{\"factos\":[]}]}");
        assertTrue(resposta.contains("\"estado\":\"erro\""), resposta);
    }
}
