package socrates.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import socrates.backend.config.PrologProperties;
import socrates.backend.domain.Metaconhecimento;
import socrates.backend.domain.Resultado;
import socrates.backend.domain.SaudeMotor;
import socrates.backend.domain.SaudeSistema;
import socrates.backend.domain.TipoResultado;
import socrates.backend.engine.drools.DroolsAdapter;
import socrates.backend.engine.drools.ExplicadorDrools;
import socrates.backend.engine.prolog.PrologAdapter;
import socrates.backend.engine.prolog.PrologInstaller;
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
     * Cria o servico com os motores dados, o validador real, o Explicador real e uma base carregada com a versao 6.
     * @param motores
     * @return
     */
    private InferenceService servico(MotorInferencia... motores) {
        BaseCarregada base = new BaseCarregada(json, "{\"versaoKB\":\"6\",\"cenarios\":[]}");
        return new InferenceService(Arrays.asList(motores), new RequestValidator(), base, new ExplicadorDrools(), json);
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
            for (String interno : List.of("Source", "REDACTED", "socrates", "java.", "JsonToken")) {
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

    // --- explicacoes --------------------------------------------------------------------------------------------

    /**
     * Uma resposta do infere com um dado de entrada (1) e uma conclusao da regra 7 (2) e uma pergunta pendente.
     */
    private static final String COM_FACTOS = """
            {"estado":"aguarda_resposta","motor":"prolog","versaoKB":"6",
             "perguntas":[{"ativo":"a1","evento":"e1","pergunta":"shell_servico","facto":1}],
             "factos":[{"numero":1,"predicado":"x","args":["a"],"regra":null,"premissas":[]},
                       {"numero":2,"predicado":"y","args":["a"],"regra":7,"premissas":[1]}],
             "erro":null}
            """;

    /**
     * O PEDIDO com os campos da explicacao dados (texto JSON sem chavetas, ex.: "\"facto\":{\"numero\":2}").
     * @param campos
     * @return
     */
    private static String comCampos(String campos) {
        String p = PEDIDO.strip();
        return p.substring(0, p.length() - 1) + (campos.isEmpty() ? "" : "," + campos) + "}";
    }

    /**
     * Verifica que cada campo facto invalido do como da PEDIDO_INVALIDO com a mensagem certa, sem chamar o motor.
     */
    @Test
    void factoInvalidoNaoChegaAoMotor() {
        String[][] casos = {
                {"", "falta o facto"},
                {"\"facto\":null", "falta o facto"},
                {"\"facto\":\"x\"", "o facto tem de ser um objeto JSON"},
                {"\"facto\":{}", "o facto tem de ter numero ou predicado e args"},
                {"\"facto\":{\"numero\":2,\"predicado\":\"y\",\"args\":[]}", "o facto tem de ter numero ou predicado e args, nao os dois"},
                {"\"facto\":{\"numero\":0}", "o numero do facto tem de ser um inteiro maior que 0"},
                {"\"facto\":{\"numero\":1.5}", "o numero do facto tem de ser um inteiro maior que 0"},
                {"\"facto\":{\"numero\":\"2\"}", "o numero do facto tem de ser um inteiro maior que 0"},
                {"\"facto\":{\"predicado\":\"y\"}", "falta a lista args do facto"},
                {"\"facto\":{\"args\":[]}", "falta o predicado do facto"}};
        for (String[] caso : casos) {
            MotorFalso prolog = motor("prolog", COM_FACTOS);
            Resultado r = servico(prolog).como("prolog", comCampos(caso[0]));
            assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado(), caso[0]);
            assertEquals(caso[1], assertErro(r, "prolog"), caso[0]);
            assertTrue(prolog.pedidos.isEmpty(), "o motor nao devia ter sido chamado: " + caso[0]);
        }
    }

    /**
     * Verifica que os erros dos passos e os da explicacao vem todos juntos no campo erro.
     */
    @Test
    void errosDoPedidoEDaExplicacaoVemJuntos() {
        Resultado r = servico(motor("prolog", COM_FACTOS)).porque("prolog", PEDIDO.replace("\"6\"", "\"5\""));
        assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado());
        assertEquals(List.of("versaoKB desconhecida: 5 (carregada: 6)", "falta o facto"),
                List.of(assertErro(r, "prolog").split("; ")));
    }

    /**
     * Verifica que o porque de uma pergunta sem ativo, evento e pergunta validos da PEDIDO_INVALIDO, sem chamar o motor.
     */
    @Test
    void perguntaInvalidaNaoChegaAoMotor() {
        MotorFalso prolog = motor("prolog", COM_FACTOS);
        Resultado r = servico(prolog).porquePergunta("prolog", comCampos("\"ativo\":\" \",\"evento\":1"));
        assertEquals(TipoResultado.PEDIDO_INVALIDO, r.tipoResultado());
        assertEquals(List.of("o campo ativo tem de ser texto nao vazio", "o campo evento tem de ser texto nao vazio",
                "falta o campo pergunta"), List.of(assertErro(r, "prolog").split("; ")));
        assertTrue(prolog.pedidos.isEmpty(), "o motor nao devia ter sido chamado");
    }

    /**
     * Verifica que o motor recebe o corpo tal como veio (com o facto) e que a explicacao e montada a partir da
     * resposta dele, com o motor da resposta.
     */
    @Test
    void comoMontaAArvoreDaRespostaDoMotor() {
        MotorFalso prolog = motor("prolog", COM_FACTOS);
        String corpo = comCampos("\"facto\":{\"numero\":2}");
        Resultado r = servico(prolog).como("prolog", corpo);
        assertEquals(TipoResultado.OK, r.tipoResultado(), r.jsonResultado());
        assertEquals(List.of(corpo), prolog.pedidos);
        JsonNode n = json.readTree(r.jsonResultado());
        assertEquals("prolog", n.path("motor").asString());
        assertEquals(7, n.path("arvore").path("regra").asInt());
        assertEquals(1, n.path("arvore").path("premissas").get(0).path("no").path("numero").asInt());
    }

    /**
     * Verifica que um erro do motor sai tal como veio, com o seu tipo, sem passar pela explicacao.
     */
    @Test
    void erroDoMotorNaoEExplicado() {
        String timeout = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":null,\"erro\":\"timeout\"}";
        Resultado r = servico(motor("prolog", timeout)).como("prolog", comCampos("\"facto\":{\"numero\":2}"));
        assertEquals(TipoResultado.TIMEOUT, r.tipoResultado());
        assertEquals(timeout, r.jsonResultado());
    }

    /**
     * Verifica que um facto que nao esta na resposta, ou uma pergunta que nao esta pendente, da NAO_ENCONTRADO.
     */
    @Test
    void factoOuPerguntaQueNaoEstaoNaRespostaDaoNaoEncontrado() {
        InferenceService s = servico(motor("prolog", COM_FACTOS));
        assertEquals(TipoResultado.NAO_ENCONTRADO,
                s.como("prolog", comCampos("\"facto\":{\"numero\":9}")).tipoResultado());
        assertEquals(TipoResultado.NAO_ENCONTRADO, s.porquePergunta("prolog",
                comCampos("\"ativo\":\"a1\",\"evento\":\"e1\",\"pergunta\":\"outra\"")).tipoResultado());
    }

    /**
     * Verifica que uma resposta de outra versaoKB que a das regras da FALHA_MOTOR: as regras nao seriam as que a
     * produziram.
     */
    @Test
    void respostaDeOutraVersaoDaFalhaMotor() {
        Resultado r = servico(motor("prolog", COM_FACTOS.replace("\"versaoKB\":\"6\"", "\"versaoKB\":\"5\"")))
                .como("prolog", comCampos("\"facto\":{\"numero\":2}"));
        assertEquals(TipoResultado.FALHA_MOTOR, r.tipoResultado());
        assertTrue(json.readTree(r.jsonResultado()).path("erro").asString().contains("versaoKB 5"), r.jsonResultado());
    }

    /**
     * Cria o servico com os dois motores reais, com o Prolog da raiz do repositorio.
     * @return
     */
    private InferenceService servicoReal() {
        PrologProperties props = new PrologProperties("swipl", Path.of("../.."), Duration.ofSeconds(10), 4,
                Duration.ofSeconds(2));
        return servico(new DroolsAdapter(), new PrologAdapter(props, json, new PrologInstaller(props)));
    }

    /**
     * Um pedido com o passo de cada cenario dado, do catalogo de cenarios, e os campos da explicacao.
     * @param campos
     * @param ids
     * @return
     */
    private String pedidoDosCenarios(String campos, String... ids) {
        JsonNode cenarios = json.readTree(new BaseCarregada(json).cenariosJson()).path("cenarios");
        StringBuilder passos = new StringBuilder();
        for (String id : ids) {
            for (JsonNode c : cenarios) {
                if (c.path("id").asString().equals(id)) {
                    passos.append(passos.isEmpty() ? "" : ",").append(json.writeValueAsString(c.path("passo")));
                }
            }
        }
        return "{\"versaoKB\":\"6\",\"passos\":[" + passos + "]," + campos + "}";
    }

    /**
     * Verifica, com os dois motores reais, que o porque de um incidente do c6 da a mesma arvore nos dois (so o motor
     * muda): as explicacoes do Prolog montam-se com as regras do modulo Drools.
     */
    @Test
    void porqueDaAMesmaArvoreNosDoisMotores() {
        String facto = "\"facto\":{\"predicado\":\"incidente\",\"args\":[{\"f\":\"inc\",\"args\":"
                + "[\"versao_por_confirmar\",\"ev_f1\"]},\"versao_por_confirmar\",\"web_srv_05\",[\"web_srv_05\"]]}";
        String corpo = pedidoDosCenarios(facto, "c6");
        InferenceService s = servicoReal();
        JsonNode drools = json.readTree(s.porque("drools", corpo).jsonResultado());
        JsonNode prolog = json.readTree(s.porque("prolog", corpo).jsonResultado());
        assertEquals("concluido", drools.path("estado").asString(), drools.toString());
        assertEquals("concluido", prolog.path("estado").asString(), prolog.toString());
        assertEquals("prolog", prolog.path("motor").asString());
        assertEquals(7, prolog.path("arvore").path("regraCompleta").path("id").asInt());
        assertEquals(drools.path("arvore"), prolog.path("arvore"));
    }

    /**
     * Verifica, com os dois motores reais, o porque da pergunta shell_servico do c7: gerada pela regra 31, e a
     * resposta e usada pelas regras 32, 33, 34, 50 e 51.
     */
    @Test
    void porquePerguntaNosDoisMotores() {
        String corpo = pedidoDosCenarios(
                "\"ativo\":\"web_srv_04\",\"evento\":\"ev_e1\",\"pergunta\":\"shell_servico\"", "c7");
        InferenceService s = servicoReal();
        for (String m : List.of("drools", "prolog")) {
            Resultado r = s.porquePergunta(m, corpo);
            assertEquals(TipoResultado.OK, r.tipoResultado(), m + ": " + r.jsonResultado());
            JsonNode n = json.readTree(r.jsonResultado());
            assertEquals(m, n.path("motor").asString());
            assertEquals(31, n.path("geradaPor").path("regraCompleta").path("id").asInt(), m);
            assertEquals(json.readTree("[32,33,34,50,51]"), n.path("regrasQueUsamAResposta"), m);
        }
    }
}
