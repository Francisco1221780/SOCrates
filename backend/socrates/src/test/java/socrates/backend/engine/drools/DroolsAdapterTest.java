package socrates.backend.engine.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import socrates.backend.application.BaseCarregada;
import socrates.backend.domain.Metaconhecimento;
import tools.jackson.databind.json.JsonMapper;
import socrates.backend.domain.SaudeMotor;
import org.junit.jupiter.api.Test;

class DroolsAdapterTest {

    /** A versao da base do repositorio, do cenarios.json gerado (que a tira do cabecalho do soc_factos.txt). */
    private static final String VERSAO = new BaseCarregada(JsonMapper.builder().build()).versaoKB();

    private final DroolsAdapter adapter = new DroolsAdapter();

    @Test
    void chamaOMotorDrools() {
        String pedido = """
                {"versaoKB":"%s","passos":[{"id":"cenario_06","factos":[
                  {"predicado":"vetor_aberto","args":["ev_f1","web_srv_05","web_srv_05_apache","cve_2021_41773"]}]}]}
                """.formatted(VERSAO);
        String resposta = adapter.infere(pedido);
        assertEquals("drools", adapter.nome());
        // v6.1: o ticket da versao por confirmar do c6 tem a sua pergunta de fecho, por isso o pedido fica a aguardar
        assertTrue(resposta.contains("\"estado\":\"aguarda_resposta\""), resposta);
        assertTrue(resposta.contains("\"predicado\":\"incidente\""), resposta);
        assertTrue(resposta.contains("\"regra\":7"), resposta);
    }

    /**
     * Verifica que o estado do Drools e pronto (regras compiladas e factos iniciais carregados), com a versao da
     * base e sem erros.
     */
    @Test
    void estadoProntoComAVersaoDaBase() {
        SaudeMotor e = adapter.estado();
        assertEquals("drools", e.motor());
        assertTrue(e.pronto(), e.toString());
        assertEquals(VERSAO, e.versaoKB());
        assertTrue(e.baseCarregada(), e.toString());
        assertEquals(Metaconhecimento.NAO_APLICAVEL, e.metaconhecimento());
        assertTrue(e.erros().isEmpty(), e.toString());
    }

    /**
     * Verifica que o porque nao chama o MotorDrools com o facto e a profundidade: no c6 a regra 6 podia concluir
     * exploracao_possivel(ev_f1, web_srv_05, V), mas falta vulneravel.
     */
    @Test
    void porqueNaoChamaOMotorDrools() {
        String pedido = """
                {"versaoKB":"%s","passos":[{"id":"cenario_06","factos":[
                  {"predicado":"vetor_aberto","args":["ev_f1","web_srv_05","web_srv_05_apache","cve_2021_41773"]}]}]}
                """.formatted(VERSAO);
        String facto = "{\"predicado\":\"exploracao_possivel\",\"args\":[\"ev_f1\",\"web_srv_05\",{\"var\":\"V\"}]}";
        String resposta = adapter.porqueNao(pedido, facto, 4);
        assertTrue(resposta.contains("\"estado\":\"concluido\""), resposta);
        assertTrue(resposta.contains("\"tipo\":\"regra\""), resposta);
        assertTrue(resposta.contains("\"regra\":6"), resposta);
    }
}
