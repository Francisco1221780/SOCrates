package cyberrangers.cyber.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cyberrangers.cyber.config.PrologProperties;
import cyberrangers.cyber.domain.Metaconhecimento;
import cyberrangers.cyber.domain.Resultado;
import cyberrangers.cyber.domain.SaudeMotor;
import cyberrangers.cyber.domain.SaudeSistema;
import cyberrangers.cyber.domain.TipoResultado;
import cyberrangers.cyber.engine.drools.DroolsAdapter;
import cyberrangers.cyber.engine.prolog.PrologAdapter;
import cyberrangers.cyber.engine.prolog.PrologInstaller;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InferenceServiceTest {

    private static final String PEDIDO = """
            {"versaoKB":"6","passos":[{"id":"cenario_07","factos":[
              {"predicado":"alerta_ids","args":["ev_e1","web_srv_04","sig_apache_path_traversal"]}]}]}
            """;
    private static final String CONCLUIDO = "{\"estado\":\"concluido\",\"motor\":\"prolog\",\"versaoKB\":\"6\"}";

    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Motor falso: devolve sempre a resposta dada e guarda os pedidos que recebeu.
     */
    private static final class MotorFalso implements MotorInferencia {
        private final String nome;
        private final boolean pronto;
        private final String resposta;
        private final String versaoKB;
        private final List<String> pedidos = new ArrayList<>();

        MotorFalso(String nome, boolean pronto, String resposta) {
            this(nome, pronto, resposta, "6");
        }

        MotorFalso(String nome, boolean pronto, String resposta, String versaoKB) {
            this.nome = nome;
            this.pronto = pronto;
            this.resposta = resposta;
            this.versaoKB = versaoKB;
        }

        @Override
        public String nome() {
            return nome;
        }

        @Override
        public String infere(String pedidoJson) {
            pedidos.add(pedidoJson);
            return resposta;
        }

        @Override
        public SaudeMotor estado() {
            return new SaudeMotor(nome, pronto, versaoKB, pronto, Metaconhecimento.EM_DIA, List.of());
        }
    }

    /**
     * Cria um motor falso pronto, com a resposta dada.
     * @param nome
     * @param resposta
     * @return
     */
    private static MotorFalso motor(String nome, String resposta) {
        return new MotorFalso(nome, true, resposta);
    }

    /**
     * Cria o servico com os motores dados, o validador real e uma base carregada com a versao 6.
     * @param motores
     * @return
     */
    private InferenceService servico(MotorInferencia... motores) {
        BaseCarregada base = new BaseCarregada(json, "{\"versaoKB\":\"6\",\"cenarios\":[]}");
        return new InferenceService(Arrays.asList(motores), new RequestValidator(), base, json);
    }

    /**
     * Verifica que o resultado e um erro do contrato com o motor pedido e a versao carregada, e devolve a mensagem.
     * @param r
     * @param motor
     * @return
     */
    private String assertErro(Resultado r, String motor) {
        JsonNode n = json.readTree(r.jsonResultado());
        assertEquals("erro", n.path("estado").asString(), r.jsonResultado());
        assertEquals(motor, n.path("motor").asString(), r.jsonResultado());
        assertEquals("6", n.path("versaoKB").asString(), r.jsonResultado());
        return n.path("erro").asString();
    }

    // --- escolha do motor e estados -----------------------------------------------------------------------------

    /**
     * Verifica que getMotor devolve o motor com o nome pedido.
     */
    @Test
    void encontraOMotorPeloNome() {
        MotorFalso prolog = motor("prolog", CONCLUIDO);
        assertSame(prolog, servico(motor("drools", CONCLUIDO), prolog).getMotor("prolog").orElseThrow());
    }

    /**
     * Verifica que um nome que nenhum motor tem da Optional vazio.
     */
    @Test
    void motorDesconhecidoDaVazio() {
        assertTrue(servico(motor("drools", CONCLUIDO)).getMotor("xyz").isEmpty());
    }

    /**
     * Verifica que os estados vem ordenados pelo nome do motor, seja qual for a ordem em que o Spring entrega os
     * motores, e que cada estado e o do seu motor.
     */
    @Test
    void estadosVemOrdenadosPeloNome() {
        List<SaudeMotor> estados = servico(new MotorFalso("prolog", false, CONCLUIDO), motor("drools", CONCLUIDO))
                .estados();
        assertEquals(List.of("drools", "prolog"), estados.stream().map(SaudeMotor::motor).toList());
        assertTrue(estados.get(0).pronto());
        assertFalse(estados.get(1).pronto());
    }

    /**
     * Verifica que dois motores com o mesmo nome impedem o servico de ser criado, em vez de um substituir o outro.
     */
    @Test
    void nomesRepetidosFalhamNoArranque() {
        assertThrows(IllegalStateException.class, () -> servico(motor("drools", CONCLUIDO), motor("drools", CONCLUIDO)));
    }

    /**
     * Verifica que sem motores o servico fica vazio, sem erro.
     */
    @Test
    void semMotoresNaoHaEstados() {
        InferenceService s = servico();
        assertTrue(s.estados().isEmpty());
        assertTrue(s.getMotor("drools").isEmpty());
    }

    // --- portoes do infere --------------------------------------------------------------------------------------

    /**
     * Verifica que um motor desconhecido da MOTOR_INEXISTENTE, com os motores disponiveis por ordem na mensagem.
     */
    @Test
    void inferirNumMotorDesconhecidoDaMotorInexistente() {
        Resultado r = servico(motor("prolog", CONCLUIDO), motor("drools", CONCLUIDO)).infere("xyz", PEDIDO);
        assertEquals(TipoResultado.MOTOR_INEXISTENTE, r.tipoResultado());
        String erro = assertErro(r, "xyz");
        assertTrue(erro.contains("drools, prolog"), erro);
    }

    /**
     * Verifica que um corpo que nao e JSON (ou vazio) da PEDIDO_INVALIDO e nao chega ao motor.
     */
    @Test
    void corpoQueNaoEJsonDaPedidoInvalido() {
        for (String corpo : new String[] {"{nao e json", ""}) {
            MotorFalso prolog = motor("prolog", CONCLUIDO);
            Resultado r = servico(prolog).infere("prolog", corpo);
            assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado(), corpo);
            assertTrue(assertErro(r, "prolog").startsWith("pedido JSON invalido"), r.jsonResultado());
            assertTrue(prolog.pedidos.isEmpty(), "o motor nao devia ter sido chamado");
        }
    }

    /**
     * Verifica que uma versaoKB diferente da carregada da PEDIDO_INVALIDO e nunca chega ao motor (nunca se infere
     * com outra versao da base).
     */
    @Test
    void versaoErradaNuncaChegaAoMotor() {
        MotorFalso prolog = motor("prolog", CONCLUIDO);
        Resultado r = servico(prolog).infere("prolog", PEDIDO.replace("\"6\"", "\"5\""));
        assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado());
        assertTrue(assertErro(r, "prolog").contains("versaoKB desconhecida: 5 (carregada: 6)"), r.jsonResultado());
        assertTrue(prolog.pedidos.isEmpty(), "o motor nao devia ter sido chamado");
    }

    /**
     * Verifica que varios erros de validacao vem todos no campo erro, separados por "; ".
     */
    @Test
    void variosErrosVemJuntosNoCampoErro() {
        Resultado r = servico(motor("prolog", CONCLUIDO)).infere("prolog",
                "{\"versaoKB\":\"5\",\"passos\":[{\"factos\":[{\"args\":[]}]}]}");
        assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado());
        String erro = assertErro(r, "prolog");
        assertEquals(2, erro.split("; ").length, erro);
    }

    /**
     * Verifica que um pedido valido chega ao motor exatamente como veio (sem ser convertido) e que a resposta do
     * motor sai tal como ele a deu.
     */
    @Test
    void pedidoValidoChegaAoMotorTalComoVeio() {
        MotorFalso prolog = motor("prolog", CONCLUIDO);
        String corpo = "{ \"passos\" : [{\"factos\":[{\"args\":[1, 2.50],\"predicado\":\"x\"}]}], \"versaoKB\":\"6\" }";
        Resultado r = servico(prolog).infere("prolog", corpo);
        assertEquals(TipoResultado.OK, r.tipoResultado());
        assertEquals(List.of(corpo), prolog.pedidos);
        assertEquals(CONCLUIDO, r.jsonResultado());
    }

    // --- classificacao da resposta do motor ---------------------------------------------------------------------

    /**
     * Verifica que aguarda_resposta e uma resposta normal (OK): o motor esta a espera do analista.
     */
    @Test
    void aguardaRespostaEOk() {
        String aguarda = "{\"estado\":\"aguarda_resposta\",\"perguntas\":[{\"facto\":401}]}";
        assertEquals(TipoResultado.OK, servico(motor("prolog", aguarda)).infere("prolog", PEDIDO).tipoResultado());
    }

    /**
     * Verifica que o erro "timeout" do motor da TIMEOUT e que a resposta do motor e mantida.
     */
    @Test
    void timeoutDoMotorDaTimeout() {
        String timeout = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":null,\"erro\":\"timeout\"}";
        Resultado r = servico(motor("prolog", timeout)).infere("prolog", PEDIDO);
        assertEquals(TipoResultado.TIMEOUT, r.tipoResultado());
        assertEquals(timeout, r.jsonResultado());
    }

    /**
     * Verifica que o erro "ocupado: ..." do motor da OCUPADO.
     */
    @Test
    void ocupadoDoMotorDaOcupado() {
        String ocupado = "{\"estado\":\"erro\",\"erro\":\"ocupado: 4 pedidos prolog em curso\"}";
        assertEquals(TipoResultado.OCUPADO, servico(motor("prolog", ocupado)).infere("prolog", PEDIDO).tipoResultado());
    }

    /**
     * Verifica que qualquer outro erro do motor da FALHA_MOTOR.
     */
    @Test
    void outroErroDoMotorDaFalhaMotor() {
        String falha = "{\"estado\":\"erro\",\"erro\":\"swipl nao encontrado: x\"}";
        assertEquals(TipoResultado.FALHA_MOTOR, servico(motor("prolog", falha)).infere("prolog", PEDIDO).tipoResultado());
    }

    /**
     * Verifica que uma resposta do motor que nao e JSON da FALHA_MOTOR com um erro do contrato, sem rebentar.
     */
    @Test
    void respostaDoMotorQueNaoEJsonDaFalhaMotor() {
        Resultado r = servico(motor("prolog", "isto nao e json")).infere("prolog", PEDIDO);
        assertEquals(TipoResultado.FALHA_MOTOR, r.tipoResultado());
        assertTrue(assertErro(r, "prolog").contains("resposta"), r.jsonResultado());
    }

    /**
     * Verifica que as mensagens com que os motores recusam um pedido mal formado dao PEDIDO_INVALIDO (400), e nao
     * FALHA_MOTOR (500). As mensagens sao as do wrapper.pl e do Drools, que sao iguais.
     */
    @Test
    void errosDoPedidoVindosDoMotorDaoPedidoInvalido() {
        for (String mensagem : List.of(
                "termo invalido no passo p1: um objeto tem de ser {\\\"f\\\": nome, \\\"args\\\": [...]}",
                "facto invalido no passo p1 (tem de ter \\\"predicado\\\" e \\\"args\\\")",
                "o passo p1 nao tem a lista \\\"factos\\\"",
                "o passo 2 nao e um objeto JSON",
                "o pedido tem de ter pelo menos um passo",
                "o pedido tem de ser um objeto JSON",
                "versaoKB desconhecida: \\\"5\\\" (carregada: 6)",
                "pedido JSON invalido: x")) {
            String resposta = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":\"6\",\"erro\":\"" + mensagem + "\"}";
            Resultado r = servico(motor("prolog", resposta)).infere("prolog", PEDIDO);
            assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado(), mensagem);
            assertEquals(resposta, r.jsonResultado());
        }
    }

    /**
     * Verifica, com os dois motores reais, que um termo invalido dentro de args (que so os motores validam) da
     * PEDIDO_INVALIDO. Falha se um dos motores mudar a mensagem sem se atualizar a lista do InferenceService.
     */
    @Test
    void termoInvalidoNosMotoresReaisDaPedidoInvalido() {
        PrologProperties props = new PrologProperties("swipl", Path.of("../.."), Duration.ofSeconds(10), 4,
                Duration.ofSeconds(2));
        InferenceService s = servico(new DroolsAdapter(),
                new PrologAdapter(props, json, new PrologInstaller(props)));
        String termoInvalido = """
                {"versaoKB":"6","passos":[{"id":"p1","factos":[{"predicado":"nota","args":[{"f":"x"}]}]}]}
                """;
        for (String m : List.of("drools", "prolog")) {
            Resultado r = s.infere(m, termoInvalido);
            assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado(), m + ": " + r.jsonResultado());
            assertTrue(json.readTree(r.jsonResultado()).path("erro").asString().startsWith("termo invalido"),
                    r.jsonResultado());
        }
    }

    /**
     * Verifica que os erros de leitura do pedido dizem o que esta mal sem detalhes internos: sintaxe com linha e
     * coluna, um JSON que nao e objeto, um campo com o tipo errado (com o caminho) e o corpo vazio.
     */
    @Test
    void errosDeLeituraSemDetalhesInternos() {
        InferenceService s = servico(motor("prolog", CONCLUIDO));
        String[][] casos = {
                {"{\"versaoKB\": \"6\", \"passos\": [", "pedido JSON invalido na linha 1, coluna"},
                {"[1, 2]", "o pedido tem de ser um objeto JSON"},
                {"\"texto\"", "o pedido tem de ser um objeto JSON"},
                {"{\"versaoKB\":\"6\",\"passos\":\"x\"}", "o campo passos tem o tipo errado"},
                {"{\"versaoKB\":\"6\",\"passos\":[{\"factos\":[{\"predicado\":\"x\",\"args\":\"y\"}]}]}",
                        "o campo passos[0].factos[0].args tem o tipo errado"},
                {"", "pedido JSON invalido: o corpo esta vazio"},
                {"   ", "pedido JSON invalido: o corpo esta vazio"}};
        for (String[] caso : casos) {
            Resultado r = s.infere("prolog", caso[0]);
            String erro = assertErro(r, "prolog");
            assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado(), caso[0]);
            assertTrue(erro.startsWith(caso[1]), caso[0] + " -> " + erro);
            for (String interno : List.of("Source", "REDACTED", "cyberrangers", "java.", "JsonToken")) {
                assertFalse(erro.contains(interno), caso[0] + " -> " + erro);
            }
        }
    }

    // --- saude do sistema ---------------------------------------------------------------------------------------

    /**
     * Verifica que com todos os motores prontos o sistema esta pronto, com a versao carregada e os motores por ordem.
     */
    @Test
    void saudeProntaComTodosOsMotoresProntos() {
        SaudeSistema s = servico(motor("prolog", CONCLUIDO), motor("drools", CONCLUIDO)).saude();
        assertTrue(s.pronto());
        assertEquals("6", s.versaoKB());
        assertEquals(List.of("drools", "prolog"), s.motores().stream().map(SaudeMotor::motor).toList());
    }

    /**
     * Verifica que basta um motor nao pronto para o sistema nao estar pronto, e que o estado desse motor vem na lista.
     */
    @Test
    void saudeNaoProntaComUmMotorEmBaixo() {
        SaudeSistema s = servico(motor("drools", CONCLUIDO), new MotorFalso("prolog", false, CONCLUIDO)).saude();
        assertFalse(s.pronto());
        assertFalse(s.motores().get(1).pronto());
    }

    /**
     * Verifica que sem motores o sistema nao esta pronto (o allMatch de uma lista vazia daria true).
     */
    @Test
    void saudeSemMotoresNaoEstaPronta() {
        SaudeSistema s = servico().saude();
        assertFalse(s.pronto());
        assertTrue(s.motores().isEmpty());
    }

    /**
     * Verifica que um motor pronto com outra versao da base que a do catalogo passa a nao pronto, com o erro a dizer
     * as duas versoes, e que o sistema tambem nao fica pronto.
     */
    @Test
    void motorComOutraVersaoNaoEstaPronto() {
        SaudeSistema s = servico(motor("drools", CONCLUIDO), new MotorFalso("prolog", true, CONCLUIDO, "5")).saude();
        assertFalse(s.pronto());
        SaudeMotor prolog = s.motores().get(1);
        assertFalse(prolog.pronto());
        assertEquals("5", prolog.versaoKB());
        assertEquals(List.of("o motor tem a versaoKB 5 e o catalogo de cenarios a 6"), prolog.erros());
        assertTrue(s.motores().get(0).pronto());
    }

    /**
     * Verifica que um motor ja nao pronto com outra versao (ou sem versao) fica como esta, sem um erro a mais: a
     * versao nao e a causa.
     */
    @Test
    void motorNaoProntoNaoGanhaErroDeVersao() {
        SaudeSistema s = servico(new MotorFalso("prolog", false, CONCLUIDO, null)).saude();
        assertTrue(s.motores().getFirst().erros().isEmpty(), s.toString());
    }
}
