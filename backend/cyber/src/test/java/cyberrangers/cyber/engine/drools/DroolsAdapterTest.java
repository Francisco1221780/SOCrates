package cyberrangers.cyber.engine.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cyberrangers.cyber.domain.Metaconhecimento;
import cyberrangers.cyber.domain.SaudeMotor;
import org.junit.jupiter.api.Test;

class DroolsAdapterTest {

    private final DroolsAdapter adapter = new DroolsAdapter();

    @Test
    void chamaOMotorDrools() {
        String pedido = """
                {"versaoKB":"6","passos":[{"id":"cenario_06","factos":[
                  {"predicado":"vetor_aberto","args":["ev_f1","web_srv_05","web_srv_05_apache","cve_2021_41773"]}]}]}
                """;
        String resposta = adapter.infere(pedido);
        assertEquals("drools", adapter.nome());
        assertTrue(resposta.contains("\"estado\":\"concluido\""), resposta);
        assertTrue(resposta.contains("\"predicado\":\"incidente\""), resposta);
        assertTrue(resposta.contains("\"regra\":7"), resposta);
    }

    /**
     * Verifica que o estado do Drools e pronto (regras compiladas e factos iniciais carregados), com a versao 6 da
     * base e sem erros.
     */
    @Test
    void estadoProntoComAVersaoDaBase() {
        SaudeMotor e = adapter.estado();
        assertEquals("drools", e.motor());
        assertTrue(e.pronto(), e.toString());
        assertEquals("6", e.versaoKB());
        assertTrue(e.baseCarregada(), e.toString());
        assertEquals(Metaconhecimento.NAO_APLICAVEL, e.metaconhecimento());
        assertTrue(e.erros().isEmpty(), e.toString());
    }
}
