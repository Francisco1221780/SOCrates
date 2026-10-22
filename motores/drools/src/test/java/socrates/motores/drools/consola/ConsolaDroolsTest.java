package socrates.motores.drools.consola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import socrates.motores.drools.MotorDrools;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A consola corrida com o que se escreveria no teclado, uma linha por resposta (com ou sem o ponto do Prolog). */
class ConsolaDroolsTest {

    private static final MotorDrools MOTOR = new MotorDrools();
    private final ObjectMapper json = new ObjectMapper();

    private static String consola(String... linhas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(saida, true, StandardCharsets.UTF_8);
        new ConsolaDrools(MOTOR, new BufferedReader(new StringReader(String.join("\n", linhas) + "\n")), out).corre();
        return saida.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
    }

    @Test
    void factosEscritosSemSintaxeProlog() {
        String s = consola("3", "c6", "4", "tempo(ev_zz, 900)", "versao(srv_x, 2.4.49-ubuntu1)", "", "11", "tempo", "11", "versao", "0");
        assertTrue(s.contains("Facto: "), s);
        assertTrue(s.contains("-> tempo(ev_zz, 900)"), s);
        assertTrue(s.contains("-> versao(srv_x, 2.4.49-ubuntu1)"), s);
        assertFalse(s.contains("'2.4.49-ubuntu1'"), s);
    }

    @Test
    void osPromptsNaoPedemPontosDoProlog() {
        String s = consola("3", "c7", "5", "1", "porque", "0", "0");
        s += consola("11", "0", "12", "", "0");
        assertFalse(s.contains("porque."), s);
        assertFalse(s.contains("todos."), s);
        assertFalse(s.contains("nada."), s);
        assertFalse(s.contains("sim. /"), s);
        assertTrue(s.contains("   porque - Porque e que me esta a perguntar isto?"), s);
        assertTrue(s.contains("todos | concluidos"), s);
    }

    @Test
    void menuIgualAoDoPrologComABaseJaCarregada() {
        String s = consola("0.");
        assertTrue(s.contains("Base carregada: 390 factos e 107 regras."), s);
        assertTrue(s.contains("========== ANALISTA VIRTUAL DE SOC =========="), s);
        assertTrue(s.matches("(?s).*\\d+ factos novos \\(n\\. 391 a \\d+\\)\\..*"), s);
        for (String opcao : new String[] {" 1. Carregar a base de conhecimento", " 2. Correr o motor",
                "10. Explicar porque nao se concluiu um facto (porque nao?)", "12. Opcoes do motor",
                "13. Injetar todos os cenarios de uma vez", " 0. Sair"}) {
            assertTrue(s.contains(opcao), opcao);
        }
        assertTrue(s.contains("Opcao: "), s);
    }

    @Test
    void cenarioComCabecalhoEConclusoesComoOArrancaMotor() {
        String s = consola("3", "c6.", "6", "0");
        assertTrue(s.contains("=== Cenario c6: versao desconhecida"), s);
        assertTrue(s.matches("(?s).*=== Cenario c6: versao desconhecida\n\\d+ factos novos \\(n\\. \\d+ a \\d+\\)\\..*"), s);
        assertTrue(s.contains("versao_por_confirmar  ativo: web_srv_05  evento: ev_f1"), s);
    }

    @Test
    void listaDeCenariosEFicheiros(@TempDir Path pasta) throws Exception {
        Path pedido = pasta.resolve("pedido.json");
        Files.writeString(pedido, "{\"versaoKB\":\"" + MotorDrools.VERSAO_KB + "\",\"passos\":[{\"id\":\"meu\",\"factos\":["
                + "{\"predicado\":\"tempo\",\"args\":[\"ev_x\",10]}]}]}");
        String s = consola("3", "[c1,c2]", "3", "'base_conhecimento/cenarios/cenario_06.txt'.", "3", pedido.toString(), "0");
        assertTrue(s.contains("=== Cenario c1: tentativa sem impacto (versao corrigida)"), s);
        assertTrue(s.contains("=== Cenario c2: "), s);
        assertTrue(s.contains("=== Ficheiro base_conhecimento/cenarios/cenario_06.txt"), s);
        assertTrue(s.contains("=== Ficheiro " + pedido), s);
    }

    @Test
    void ficheiroDeUmCenarioConhecidoContaComoOCenario() {
        String s = consola("3", "c6", "3", "'cenarios/cenario_06.txt'", "0");
        assertTrue(s.contains("O cenario c6 ja foi injetado."), s);
    }

    @Test
    void cenarioAlternativoERepetidoSaoRecusados() {
        String s = consola("3", "c35", "3", "c35a", "3", "c35b", "3", "c35c", "3", "c35", "0");
        assertTrue(s.contains("O cenario c35c e alternativa de c35b, que ja entrou: nao foi injetado."), s);
        assertTrue(s.contains("O cenario c35 ja foi injetado."), s);
    }

    @Test
    void perguntaComPorqueEResposta() {
        String s = consola("3", "c7", "5", "1", "porque.", "sim.", "0", "0");
        assertTrue(s.contains("Pergunto porque a regra 31 pediu esta verificacao em web_srv_04"), s);
        assertTrue(s.contains("nao se verifica resposta_analista(web_srv_04, ev_e1, shell_servico, B)"), s);
        assertTrue(s.contains("A resposta vai ser usada pelas regras [32,33,34,50,51,94]."), s);
        assertTrue(s.contains("   sim - Sim, ha uma shell lancada pelo servico"), s);
        // v6.1: depois da resposta fica a pergunta de fecho da exploracao possivel do mesmo ataque
        assertTrue(s.contains("vulnerabilidade_tratada"), s);
    }

    @Test
    void factoAMaoRepetidoEOpcaoMostrarConclusoes() {
        String s = consola("12", "mostrar_conclusoes.", "sim.", "3", "c6", "4", "tempo(ev_f1,2200).", "", "0");
        assertTrue(s.contains("   mostrar_conclusoes = nao"), s);
        assertTrue(s.contains("Foi concluido o facto n. "), s);
        assertTrue(s.contains("vetor_aberto(ev_f1, web_srv_05, web_srv_05_apache, cve_2021_41773)   (regra 4)"), s);
        assertTrue(s.matches("(?s).*O facto tempo\\(ev_f1, 2200\\) ja existe \\(n\\. \\d+\\)\\.\nNao ha conclusoes novas\\..*"), s);
    }

    @Test
    void erroDeSintaxeNoFacto() {
        String s = consola("4", "tempo(ev_x,10", "", "0");
        assertTrue(s.contains("falta ')'"), s);
    }

    @Test
    void comoMostraAArvoreComoOProlog() {
        String s = consola("3", "c6", "9",
                "incidente(inc(versao_por_confirmar,ev_f1),versao_por_confirmar,web_srv_05,[web_srv_05])", "0");
        assertTrue(s.contains("   concluido pela regra 7, porque:"), s);
        assertTrue(s.contains("(dado)"), s);
        assertTrue(s.contains("nao se verifica versao(web_srv_05_apache, A)"), s);
        assertTrue(s.contains("a condicao porto_compativel(ev_f1, 80, tcp) verifica-se (calculada)"), s);
    }

    @Test
    void porqueNaoMostraARegraInteiraComCadaCondicaoMarcada() {
        String s = consola("3", "c6", "10", "exploracao_possivel(ev_f1, web_srv_05, V)", "0");
        assertTrue(s.contains("Porque nao existe exploracao_possivel(ev_f1, web_srv_05, V)?"), s);
        assertTrue(s.contains("A regra 6 podia conclui-lo, mas nao se verifica:"), s);
        assertTrue(s.contains("  SE    [ok]    vetor_aberto(ev_f1, web_srv_05, web_srv_05_apache, cve_2021_41773)   (facto n. "), s);
        assertTrue(s.contains("  E     [FALTA] vulneravel(web_srv_05_apache, cve_2021_41773)"), s);
        assertTrue(s.contains("  ENTAO exploracao_possivel(ev_f1, web_srv_05, cve_2021_41773)"), s);
        assertTrue(s.contains("    - Falta: vulneravel(web_srv_05_apache, cve_2021_41773)"), s);
        assertTrue(s.contains("[ok]    nao corrigido_backport(web_srv_05_apache, cve_2021_41773)"), s);
        assertTrue(s.contains("E um dado de entrada: nao esta na base e nenhuma regra o conclui"), s);
        assertFalse(s.contains("Porque pela regra"), s);
    }

    @Test
    void porqueNaoDeUmFactoQueExisteEDeUmaCondicaoNegada() {
        String s = consola("3", "c6", "10", "tempo(ev_f1, 2200)", "0");
        assertTrue(s.contains("Mas esse facto existe (facto n. "), s);
        s = consola("3", "c6", "10", "versao_por_confirmar_x", "0");
        assertTrue(s.contains("Porque nao existe versao_por_confirmar_x?"), s);
    }

    @Test
    void porqueNaoDizQuemImpedeUmaCondicaoNegada() {
        String s = consola("3", "c1", "10", "exploracao_incerta(Ev, Alvo, Vuln)", "0");
        assertTrue(s.matches("(?s).*- Impede: o facto versao\\(web_srv_02_apache, v\\(2, 4, 52\\)\\) existe \\(facto n\\. \\d+\\), "
                + "e a regra exige que nao exista.*"), s);
    }

    @Test
    void comoPeloNumeroDeUmDadoEDeUmFactoQueNaoExiste() {
        String s = consola("3", "c6", "9", "1", "9", "999999", "9", "tempo(ev_nao_existe, 1)", "0");
        assertTrue(s.contains("(dado)"), s);
        assertTrue(s.contains("Essa conclusao nao foi tirada."), s);
        assertTrue(s.contains("nao esta na memoria de trabalho. Experimente o porque nao? (opcao 10)."), s);
    }

    @Test
    void osPedidosMostramAsEscolhasPossiveis() {
        String s = consola("3", "c6", "9", "incidente", "0", "10", "", "11", "0");
        // 9: os predicados concluidos, e o nome de um predicado lista as conclusoes dele com o numero
        assertTrue(s.contains("Conclusoes do motor, por predicado:"), s);
        assertTrue(s.contains("incidente (1)"), s);
        assertTrue(s.matches("(?s).*  \\d+\\. incidente\\(inc\\(versao_por_confirmar, ev_f1\\).*"), s);
        // 10: o que as regras podem concluir
        assertTrue(s.contains("As regras concluem factos destes tipos:"), s);
        assertTrue(s.contains("ataque_dirigido"), s);
        // 11: os predicados que existem
        assertTrue(s.contains("todos | concluidos | o nome de um predicado:"), s);
        assertTrue(s.contains("alerta_ids (1)"), s);
    }

    @Test
    void comPausaOMenuSoVoltaDepoisDoEnter() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(saida, true, StandardCharsets.UTF_8);
        // "6" escreve os tickets; so depois do Enter (a linha vazia) o menu aparece outra vez; "0" sai sem pausa
        new ConsolaDrools(MOTOR, new BufferedReader(new StringReader("6\n\n0\n")), out, true).corre();
        String s = saida.toString(StandardCharsets.UTF_8);
        int tickets = s.indexOf("TICKETS ABERTOS");
        int pausa = s.indexOf("Enter para voltar ao menu");
        int segundoMenu = s.indexOf("ANALISTA VIRTUAL DE SOC", s.indexOf("ANALISTA VIRTUAL DE SOC") + 1);
        assertTrue(tickets > 0 && tickets < pausa && pausa < segundoMenu, s);
        assertEquals(1, s.split("Enter para voltar ao menu", -1).length - 1, s);
    }

    @Test
    void menuTemOComoEOPorqueNaoSeguidosESemPorqueDeFactos() {
        String s = consola("0");
        int como = s.indexOf(" 9. Explicar como se concluiu");
        int porqueNao = s.indexOf("10. Explicar porque nao se concluiu");
        assertTrue(como > 0 && como < porqueNao, s);
        // o "porque?" so existe durante as perguntas (opcao 5), como no Prolog
        assertFalse(s.contains("(porque?)"), s);
        assertFalse(s.contains("14."), s);
    }

    /** A opcao 13 da a mesma resposta que o pedido com a sequencia completa enviado direto ao motor. */
    @Test
    void injetarTodosEOMesmoQueOPedidoDireto() throws Exception {
        String s = consola("13", "0");
        assertTrue(s.contains("=== Cenario c39: maquina recuperada"), s);
        assertTrue(s.contains("=== Cenario c34: "), s);
        assertTrue(s.contains("=== Cenario c6a: "), s);
        JsonNode c = json.readTree(ConsolaDroolsTest.class.getResourceAsStream("/cenarios.json"));
        ObjectNode pedido = json.createObjectNode().put("versaoKB", MotorDrools.VERSAO_KB);
        var passos = pedido.putArray("passos");
        c.get("sequencias").get("completa").forEach(id -> c.get("cenarios").forEach(x -> {
            if (x.get("id").equals(id)) {
                passos.add(x.get("passo"));
            }
        }));
        int factos = json.readTree(MOTOR.infere(pedido.toString())).get("factos").size();
        assertTrue(s.contains("\n" + factos + " factos ("), s);
    }

    @Test
    void recuperacaoDeUmaMaquina() {
        // web_srv_01/ev_a1 ja vem recuperado no cenario 39; app_srv_01/ev_k1 (c11a) nao
        String s = consola("13", "8", "app_srv_01.", "ev_k1.", "0");
        assertTrue(s.contains("   app_srv_01 (evento ev_k1)"), s);
        Matcher m = Pattern.compile("Evento do compromisso: (\\d+) factos novos").matcher(s);
        assertTrue(m.find(), s);
    }

    @Test
    void carregarABaseDoModuloOuOutroFicheiro() {
        String s = consola("1", "base.", "1", "'src/test/resources/factos_iniciais_r7.json'.", "1", "nao_existe.json", "0");
        assertEquals(3, s.split("Base carregada: 390 factos e 107 regras\\.", -1).length, s);
        assertTrue(s.contains("Base carregada: 2 factos e 107 regras."), s);
        assertTrue(s.contains("Nao foi possivel carregar a base: factos iniciais: nao existe nao_existe.json"), s);
    }

    @Test
    void opcaoInvalidaEFimDaEntradaSaiSemErro() {
        String s = consola("99");
        assertTrue(s.contains("Opcao invalida."), s);
        assertFalse(s.contains("Erro:"), s);
    }
}
