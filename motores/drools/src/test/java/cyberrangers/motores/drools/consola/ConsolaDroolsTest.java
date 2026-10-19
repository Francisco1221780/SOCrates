package cyberrangers.motores.drools.consola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.MotorDrools;
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
    void menuIgualAoDoPrologComABaseJaCarregada() {
        String s = consola("0.");
        assertTrue(s.contains("Base carregada: 314 factos e 92 regras."), s);
        assertTrue(s.contains("========== ANALISTA VIRTUAL DE SOC =========="), s);
        assertTrue(s.matches("(?s).*\\d+ factos novos \\(n\\. 315 a \\d+\\)\\..*"), s);
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
        Files.writeString(pedido, "{\"versaoKB\":\"6\",\"passos\":[{\"id\":\"meu\",\"factos\":["
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
        String s = consola("3", "c7", "3", "c7b_sim", "3", "c7b_nao", "3", "c7", "0");
        assertTrue(s.contains("O cenario c7b_nao e alternativa de c7b_sim, que ja entrou: nao foi injetado."), s);
        assertTrue(s.contains("O cenario c7 ja foi injetado."), s);
    }

    @Test
    void perguntaComPorqueEResposta() {
        String s = consola("3", "c7", "5", "1", "porque.", "sim.", "0", "0");
        assertTrue(s.contains("Pergunto porque a regra 31 pediu esta verificacao em web_srv_04"), s);
        assertTrue(s.contains("nao se verifica resposta_analista(web_srv_04,ev_e1,shell_servico,B)"), s);
        assertTrue(s.contains("A resposta vai ser usada pelas regras [32,33,34,50,51]."), s);
        assertTrue(s.contains("   sim. Sim, ha uma shell lancada pelo servico"), s);
        assertTrue(s.contains("Nao ha perguntas por responder."), s);
    }

    @Test
    void factoAMaoRepetidoEOpcaoMostrarConclusoes() {
        String s = consola("12", "mostrar_conclusoes.", "sim.", "3", "c6", "4", "tempo(ev_f1,2200).", "0");
        assertTrue(s.contains("   mostrar_conclusoes = nao"), s);
        assertTrue(s.contains("Foi concluido o facto n. "), s);
        assertTrue(s.contains("vetor_aberto(ev_f1,web_srv_05,web_srv_05_apache,cve_2021_41773)   (regra 4)"), s);
        assertTrue(s.matches("(?s).*O facto tempo\\(ev_f1,2200\\) ja existe \\(n\\. \\d+\\)\\.\nNao ha conclusoes novas\\..*"), s);
    }

    @Test
    void erroDeSintaxeNoFacto() {
        String s = consola("4", "tempo(ev_x,10", "0");
        assertTrue(s.contains("falta ')'"), s);
    }

    @Test
    void comoMostraAArvoreComoOProlog() {
        String s = consola("3", "c6", "9",
                "incidente(inc(versao_por_confirmar,ev_f1),versao_por_confirmar,web_srv_05,[web_srv_05])", "0");
        assertTrue(s.contains("   concluido pela regra 7, porque:"), s);
        assertTrue(s.contains("(dado)"), s);
        assertTrue(s.contains("nao se verifica versao(web_srv_05_apache,A)"), s);
        assertTrue(s.contains("a condicao porto_compativel(ev_f1,80,tcp) verifica-se (calculada)"), s);
    }

    @Test
    void porqueNaoComVariaveis() {
        String s = consola("3", "c6", "10", "exploracao_possivel(ev_f1,web_srv_05,V).", "0");
        assertTrue(s.contains("Porque pela regra 6:"), s);
        assertTrue(s.contains("     A premissa vulneravel(web_srv_05_apache,cve_2021_41773) e falsa"), s);
        assertTrue(s.contains("e nenhuma regra o conclui (e um dado de entrada)"), s);
    }

    /** A opcao 13 da a mesma resposta que o pedido com as sequencias base e normal enviado direto ao motor. */
    @Test
    void injetarTodosEOMesmoQueOPedidoDireto() throws Exception {
        String s = consola("13", "0");
        assertTrue(s.contains("=== Cenario c39: maquina recuperada"), s);
        assertTrue(s.contains("=== Cenario c34: "), s);
        JsonNode c = json.readTree(ConsolaDroolsTest.class.getResourceAsStream("/cenarios.json"));
        ObjectNode pedido = json.createObjectNode().put("versaoKB", "6");
        var passos = pedido.putArray("passos");
        for (String seq : new String[] {"base", "normal"}) {
            c.get("sequencias").get(seq).forEach(id -> c.get("cenarios").forEach(x -> {
                if (x.get("id").equals(id)) {
                    passos.add(x.get("passo"));
                }
            }));
        }
        int factos = json.readTree(MOTOR.infere(pedido.toString())).get("factos").size();
        assertTrue(s.contains("\n" + factos + " factos ("), s);
    }

    @Test
    void recuperacaoDeUmaMaquina() {
        // web_srv_01/ev_a1 ja vem recuperado no cenario 39; app_srv_01/ev_k1 (c11b) nao
        String s = consola("13", "8", "app_srv_01.", "ev_k1.", "0");
        assertTrue(s.contains("   app_srv_01 (evento ev_k1)"), s);
        Matcher m = Pattern.compile("Evento do compromisso: (\\d+) factos novos").matcher(s);
        assertTrue(m.find(), s);
    }

    @Test
    void carregarABaseDoModuloOuOutroFicheiro() {
        String s = consola("1", "base.", "1", "'src/test/resources/factos_iniciais_r7.json'.", "1", "nao_existe.json", "0");
        assertEquals(3, s.split("Base carregada: 314 factos e 92 regras\\.", -1).length, s);
        assertTrue(s.contains("Base carregada: 2 factos e 92 regras."), s);
        assertTrue(s.contains("Nao foi possivel carregar a base: factos iniciais: nao existe nao_existe.json"), s);
    }

    @Test
    void opcaoInvalidaEFimDaEntradaSaiSemErro() {
        String s = consola("99");
        assertTrue(s.contains("Opcao invalida."), s);
        assertFalse(s.contains("Erro:"), s);
    }
}
