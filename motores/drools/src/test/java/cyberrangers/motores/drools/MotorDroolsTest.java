package cyberrangers.motores.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class MotorDroolsTest {

    private static final String VETOR_F1 =
            "{\"predicado\":\"vetor_aberto\",\"args\":[\"ev_f1\",\"web_srv_05\",\"web_srv_05_apache\",\"cve_2021_41773\"]}";

    private final MotorDrools motor = new MotorDrools();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode infere(MotorDrools m, String pedido) throws Exception {
        return json.readTree(m.infere(pedido));
    }

    private JsonNode infere(String pedido) throws Exception {
        return infere(motor, pedido);
    }

    private static String pedido(String... passos) {
        return "{\"versaoKB\":\"6\",\"passos\":[" + String.join(",", passos) + "]}";
    }

    private static String passo(String id, String... factos) {
        return "{\"id\":\"" + id + "\",\"factos\":[" + String.join(",", factos) + "]}";
    }

    private static List<JsonNode> factos(JsonNode resposta, String predicado) {
        List<JsonNode> r = new ArrayList<>();
        resposta.get("factos").forEach(f -> {
            if (f.get("predicado").asText().equals(predicado)) {
                r.add(f);
            }
        });
        return r;
    }

    private static JsonNode porNumero(JsonNode resposta, int numero) {
        return resposta.get("factos").get(numero - 1);
    }

    // --- estado do motor (AC13)

    @Test
    void ficaProntoComRegrasEFactosIniciais() {
        EstadoMotor e = motor.estado();
        assertTrue(e.pronto(), e.erros().toString());
        assertTrue(e.regrasCarregadas());
        assertEquals(314, e.factosIniciais());
    }

    @Test
    void factosIniciaisDeOutraVersaoNaoFicaPronto() throws Exception {
        MotorDrools v5 = new MotorDrools("factos_iniciais_v5.json");
        assertFalse(v5.estado().pronto());
        JsonNode r = infere(v5, pedido(passo("p1")));
        assertTrue(r.get("erro").asText().contains("e da versaoKB \"5.1\", o motor e da 6"), r.toString());
    }

    @Test
    void semFactosIniciaisNaoFicaPronto() throws Exception {
        MotorDrools semFactos = new MotorDrools("nao_existe.json");
        assertFalse(semFactos.estado().pronto());
        assertTrue(semFactos.estado().regrasCarregadas());
        JsonNode r = infere(semFactos, pedido(passo("p1")));
        assertEquals("erro", r.get("estado").asText());
        assertTrue(r.get("erro").asText().contains("nao existe nao_existe.json"), r.toString());
    }

    // --- R7, regra molde

    /**
     * Paridade com o wrapper.pl: os factos da R7 no cenario 6 (swipl motores/prolog/wrapper.pl base_conhecimento,
     * 14/10/2026). Os numeros mudam entre motores; o resto tem de ser igual.
     */
    @Test
    void r7DaOsMesmosFactosQueOWrapperProlog() throws Exception {
        JsonNode r = infere(pedido(passo("cenario_06", VETOR_F1)));
        JsonNode prolog = json.readTree("""
                {"numero": 351, "predicado": "incidente",
                 "args": [{"f": "inc", "args": ["versao_por_confirmar", "ev_f1"]}, "versao_por_confirmar", "web_srv_05", ["web_srv_05"]],
                 "origem": "regra", "passo": "cenario_06", "regra": 7,
                 "premissas": [348, {"f": "nao", "args": [{"f": "versao", "args": ["web_srv_05_apache", "A"]}]}]}""");
        JsonNode prologIncidentes = json.readTree("""
                [{"facto": 351, "id": {"f": "inc", "args": ["versao_por_confirmar", "ev_f1"]}, "tipo": "versao_por_confirmar",
                  "ativo": "web_srv_05", "impactados": ["web_srv_05"]}]""");

        List<JsonNode> incidentes = factos(r, "incidente");
        assertEquals(1, incidentes.size());
        ObjectNode drools = incidentes.get(0).deepCopy();
        int numero = drools.get("numero").asInt();
        int premissaVetor = drools.get("premissas").get(0).asInt();
        assertEquals("vetor_aberto", porNumero(r, premissaVetor).get("predicado").asText());
        assertEquals(json.readTree(VETOR_F1).get("args"), porNumero(r, premissaVetor).get("args"));

        drools.put("numero", 351);
        ((ArrayNode) drools.get("premissas")).set(0, 348);
        assertEquals(prolog, drools);

        ObjectNode incidente = r.get("incidentes").get(0).deepCopy();
        assertEquals(numero, incidente.get("facto").asInt());
        incidente.put("facto", 351);
        assertEquals(prologIncidentes, json.createArrayNode().add(incidente));

        assertEquals(1, factos(r, "exploracao_incerta").size());
        assertEquals(1, factos(r, "motivo").size());
        assertEquals("concluido", r.get("estado").asText());
    }

    @Test
    void r7NaoDisparaSeHouverVersao() throws Exception {
        String versao = "{\"predicado\":\"versao\",\"args\":[\"web_srv_05_apache\",{\"f\":\"v\",\"args\":[2,4,49]}]}";
        assertTrue(factos(infere(pedido(passo("p1", versao), passo("p2", VETOR_F1))), "incidente").isEmpty());
        assertTrue(factos(infere(pedido(passo("p1", versao, VETOR_F1))), "incidente").isEmpty());
    }

    @Test
    void r7ConcluiSobreOsDadosFixosSemPasso() throws Exception {
        JsonNode r = infere(new MotorDrools("factos_iniciais_r7.json"), pedido(passo("p1")));
        JsonNode incidente = factos(r, "incidente").get(0);
        assertEquals("regra", incidente.get("origem").asText());
        assertTrue(incidente.get("passo").isNull());
    }

    @Test
    void factoRepetidoNoutroPassoNaoSeRepete() throws Exception {
        JsonNode r = infere(pedido(passo("p1", VETOR_F1), passo("p2", VETOR_F1)));
        assertEquals(1, factos(r, "vetor_aberto").size());
        assertEquals("p1", factos(r, "vetor_aberto").get(0).get("passo").asText());
        assertEquals(1, factos(r, "incidente").size());
    }

    @Test
    void mesmoIncidentePorDuasViasFicaComAPrimeiraJustificacao() throws Exception {
        String outraInstalacao =
                "{\"predicado\":\"vetor_aberto\",\"args\":[\"ev_f1\",\"web_srv_05\",\"web_srv_05_outro\",\"cve_2021_41773\"]}";
        JsonNode r = infere(pedido(passo("p1", VETOR_F1, outraInstalacao)));
        List<JsonNode> incidentes = factos(r, "incidente");
        assertEquals(1, incidentes.size());
        assertEquals(2, factos(r, "motivo").size());
        JsonNode premissaVetor = porNumero(r, incidentes.get(0).get("premissas").get(0).asInt());
        assertEquals("vetor_aberto", premissaVetor.get("predicado").asText());
    }

    // --- resposta: perguntas, tickets, factos iniciais

    @Test
    void perguntaSemRespostaFicaAAguardar() throws Exception {
        String pergunta = "{\"predicado\":\"pergunta_analista\",\"args\":[\"web_srv_04\",\"ev_e1\",\"shell_servico\"]}";
        JsonNode r = infere(pedido(passo("cenario_07", pergunta)));
        assertEquals("aguarda_resposta", r.get("estado").asText());
        JsonNode p = r.get("perguntas").get(0);
        assertEquals("shell_servico", p.get("pergunta").asText());
        assertTrue(p.get("texto").asText().startsWith("O sensor deste servidor"));
        assertEquals(3, p.get("opcoes").size());
        assertEquals("{\"valor\":\"sim\",\"texto\":\"Sim, ha uma shell lancada pelo servico\"}",
                p.get("opcoes").get(0).toString());

        String resposta = "{\"predicado\":\"resposta_analista\",\"args\":[\"web_srv_04\",\"ev_e1\",\"shell_servico\",\"sim\"]}";
        JsonNode respondida = infere(pedido(passo("cenario_07", pergunta), passo("cenario_07b_sim", resposta)));
        assertEquals("concluido", respondida.get("estado").asText());
        assertEquals(0, respondida.get("perguntas").size());

        String retirada = "{\"predicado\":\"pergunta_retirada\",\"args\":[\"web_srv_04\",\"ev_e1\",\"shell_servico\"]}";
        assertEquals("concluido", infere(pedido(passo("p1", pergunta, retirada))).get("estado").asText());
    }

    @Test
    void ticketLevaOMotivoDeFecho() throws Exception {
        String inc = "{\"f\":\"inc\",\"args\":[\"versao_por_confirmar\",\"ev_f1\"]}";
        String ticket = "{\"predicado\":\"ticket\",\"args\":[" + inc
                + ",\"web_srv_05\",\"verificar_inventario\",\"media\",\"p3\",[\"web_srv_05\"]]}";
        String outro = ticket.replace("ev_f1", "ev_f2");
        String fechado = "{\"predicado\":\"ticket_fechado\",\"args\":[" + inc + ",\"versao_confirmada\"]}";
        JsonNode tickets = infere(pedido(passo("p1", ticket, outro, fechado))).get("tickets");
        assertEquals(2, tickets.size());
        assertEquals("versao_confirmada", tickets.get(0).get("fechado").asText());
        assertTrue(tickets.get(1).get("fechado").isNull());
        assertEquals("[\"facto\",\"incidente\",\"ativo\",\"acao\",\"gravidade\",\"prioridade\",\"impactados\",\"fechado\"]",
                json.valueToTree(iterableToList(tickets.get(0).fieldNames())).toString());
    }

    @Test
    void factosIniciaisVemPrimeiroComOrigemInicial() throws Exception {
        JsonNode r = infere(pedido(passo("p1", VETOR_F1)));
        JsonNode primeiro = r.get("factos").get(0);
        assertEquals(1, primeiro.get("numero").asInt());
        assertEquals("inicial", primeiro.get("origem").asText());
        assertTrue(primeiro.get("passo").isNull());
        assertTrue(primeiro.get("regra").isNull());
        assertEquals(0, primeiro.get("premissas").size());
        assertEquals("zona", primeiro.get("predicado").asText());
        JsonNode entrada = porNumero(r, 331);
        assertEquals("entrada", entrada.get("origem").asText());
        assertEquals("p1", entrada.get("passo").asText());
        assertEquals("[\"estado\",\"motor\",\"versaoKB\",\"perguntas\",\"incidentes\",\"tickets\",\"factos\",\"erro\"]",
                json.valueToTree(iterableToList(r.fieldNames())).toString());
        assertEquals("drools", r.get("motor").asText());
    }

    // --- validacao do pedido (mesmas mensagens do wrapper.pl)

    @Test
    void pedidosInvalidosDaoAsMensagensDoWrapper() throws Exception {
        assertErro("[]", "o pedido tem de ser um objeto JSON");
        assertErro("{\"versaoKB\":\"9.9\",\"passos\":[{\"factos\":[]}]}", "versaoKB desconhecida: \"9.9\" (carregada: 6)");
        assertErro(pedido(), "o pedido tem de ter pelo menos um passo");
        assertErro(pedido("1"), "o passo 1 nao e um objeto JSON");
        assertErro(pedido("{\"id\":\"c1\"}"), "o passo c1 nao tem a lista \"factos\"");
        assertErro(pedido("{\"factos\":[{\"args\":[]}]}"), "facto invalido no passo passo_1 (tem de ter \"predicado\" e \"args\")");
        assertTrue(infere("{nao e json").get("erro").asText().startsWith("pedido JSON invalido: "));
    }

    @Test
    void passoSemIdFicaPassoN() throws Exception {
        JsonNode r = infere(pedido(passo("p1"), "{\"factos\":[" + VETOR_F1 + "]}"));
        assertEquals("passo_2", factos(r, "vetor_aberto").get(0).get("passo").asText());
    }

    private void assertErro(String pedido, String mensagem) throws Exception {
        JsonNode r = infere(pedido);
        assertEquals("erro", r.get("estado").asText());
        assertEquals(mensagem, r.get("erro").asText());
    }

    private static <T> List<T> iterableToList(Iterator<T> it) {
        List<T> l = new ArrayList<>();
        it.forEachRemaining(l::add);
        return l;
    }
}
