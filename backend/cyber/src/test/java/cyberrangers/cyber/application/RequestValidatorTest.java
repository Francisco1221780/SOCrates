package cyberrangers.cyber.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cyberrangers.cyber.domain.Facto;
import cyberrangers.cyber.domain.Passo;
import cyberrangers.cyber.domain.Pedido;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class RequestValidatorTest {

    private static final String VERSAO = "6";

    private final RequestValidator validador = new RequestValidator();

    /**
     * Cria um facto valido com um argumento atomo.
     * @param predicado
     * @return
     */
    private static Facto facto(String predicado) {
        return new Facto(predicado, List.of(JsonNodeFactory.instance.stringNode("web_srv_04")));
    }

    /**
     * Cria um pedido valido com um passo e um facto.
     * @return
     */
    private static Pedido valido() {
        return new Pedido(VERSAO, List.of(new Passo("cenario_07", List.of(facto("alerta_ids")))));
    }

    /**
     * Junta os erros numa so string, para as mensagens de falha dos testes.
     * @param erros
     * @return
     */
    private static String texto(List<String> erros) {
        return String.join(" | ", erros);
    }

    /**
     * Verifica que um pedido valido nao tem erros.
     */
    @Test
    void pedidoValidoNaoTemErros() {
        assertEquals(List.of(), validador.validaPedido(valido(), VERSAO));
    }

    /**
     * Verifica que um passo sem id e valido (os motores usam passo_<i>) e que args vazio tambem e valido.
     */
    @Test
    void passoSemIdEArgsVaziosSaoValidos() {
        Pedido p = new Pedido(VERSAO, List.of(new Passo(null, List.of(new Facto("arranque", List.of())))));
        assertEquals(List.of(), validador.validaPedido(p, VERSAO));
    }

    /**
     * Verifica que um pedido sem versaoKB da um erro que fala da versaoKB, sem rebentar.
     */
    @Test
    void semVersaoDaErro() {
        Pedido p = new Pedido(null, valido().passos());
        List<String> erros = validador.validaPedido(p, VERSAO);
        assertEquals(1, erros.size(), texto(erros));
        assertTrue(erros.getFirst().contains("versaoKB"), texto(erros));
    }

    /**
     * Verifica que uma versaoKB diferente da carregada da erro com as duas versoes na mensagem (AC17).
     */
    @Test
    void versaoDiferenteDaErroComAsDuasVersoes() {
        Pedido p = new Pedido("5", valido().passos());
        List<String> erros = validador.validaPedido(p, VERSAO);
        assertEquals(1, erros.size(), texto(erros));
        assertTrue(erros.getFirst().contains("versaoKB"), texto(erros));
        assertTrue(erros.getFirst().contains("5"), texto(erros));
        assertTrue(erros.getFirst().contains("6"), texto(erros));
    }

    /**
     * Verifica que um pedido sem passos (null ou lista vazia) da erro.
     */
    @Test
    void semPassosDaErro() {
        for (List<Passo> passos : java.util.Arrays.asList(null, List.<Passo>of())) {
            List<String> erros = validador.validaPedido(new Pedido(VERSAO, passos), VERSAO);
            assertEquals(1, erros.size(), texto(erros));
            assertTrue(erros.getFirst().contains("passos"), texto(erros));
        }
    }

    /**
     * Verifica que um passo sem a lista de factos da erro com a posicao do passo, a contar de 1.
     */
    @Test
    void passoSemFactosDaErroComAPosicao() {
        Pedido p = new Pedido(VERSAO, List.of(valido().passos().getFirst(), new Passo("p2", null)));
        List<String> erros = validador.validaPedido(p, VERSAO);
        assertEquals(1, erros.size(), texto(erros));
        assertTrue(erros.getFirst().contains("passo 2"), texto(erros));
        assertTrue(erros.getFirst().contains("factos"), texto(erros));
    }

    /**
     * Verifica que um facto sem predicado (null ou em branco) da erro com a posicao do passo e do facto, a contar de 1.
     */
    @Test
    void factoSemPredicadoDaErroComAPosicao() {
        for (String predicado : java.util.Arrays.asList(null, " ")) {
            Passo passo = new Passo("p2", List.of(facto("ok"), new Facto(predicado, List.of())));
            Pedido p = new Pedido(VERSAO, List.of(valido().passos().getFirst(), passo));
            List<String> erros = validador.validaPedido(p, VERSAO);
            assertEquals(1, erros.size(), texto(erros));
            assertTrue(erros.getFirst().contains("passo 2, facto 2"), texto(erros));
            assertTrue(erros.getFirst().contains("predicado"), texto(erros));
        }
    }

    /**
     * Verifica que um facto sem args da erro com a posicao.
     */
    @Test
    void factoSemArgsDaErroComAPosicao() {
        Pedido p = new Pedido(VERSAO, List.of(new Passo("p1", List.of(new Facto("alerta_ids", null)))));
        List<String> erros = validador.validaPedido(p, VERSAO);
        assertEquals(1, erros.size(), texto(erros));
        assertTrue(erros.getFirst().contains("passo 1, facto 1"), texto(erros));
        assertTrue(erros.getFirst().contains("args"), texto(erros));
    }

    /**
     * Verifica que um passo ou um facto a null (por exemplo, [null] no JSON) da erro, sem rebentar.
     */
    @Test
    void elementosNullDaoErro() {
        List<Passo> passos = new ArrayList<>();
        passos.add(null);
        List<Facto> factos = new ArrayList<>();
        factos.add(null);
        passos.add(new Passo("p2", factos));
        List<String> erros = validador.validaPedido(new Pedido(VERSAO, passos), VERSAO);
        assertEquals(2, erros.size(), texto(erros));
        assertTrue(erros.get(0).contains("passo 1"), texto(erros));
        assertTrue(erros.get(1).contains("passo 2, facto 1"), texto(erros));
    }

    /**
     * Verifica que todos os erros vem de uma vez, e nao so o primeiro (AC17: quem escreve o pedido ve tudo o que
     * tem de corrigir).
     */
    @Test
    void variosErrosVemTodos() {
        Passo passo = new Passo("p1", List.of(new Facto(null, List.of()), new Facto("x", null)));
        List<String> erros = validador.validaPedido(new Pedido("5", List.of(passo)), VERSAO);
        assertEquals(3, erros.size(), texto(erros));
    }
}
