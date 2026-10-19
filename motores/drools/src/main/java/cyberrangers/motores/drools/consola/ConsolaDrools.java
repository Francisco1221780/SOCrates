package cyberrangers.motores.drools.consola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.EstadoMotor;
import cyberrangers.motores.drools.MotorDrools;
import cyberrangers.motores.drools.explicacao.PorqueNao;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Consola interativa do motor Drools, com o menu/0 do soc_motor.pl: as mesmas opcoes (0 a 13), os mesmos textos e o
 * mesmo comportamento, incluindo o "porque nao?" (10) e as opcoes do motor (12). A diferenca e que arranca ja com a
 * base do modulo carregada (as regras, o factos_iniciais.json e o cenarios.json vem no classpath); a opcao 1 volta a
 * carrega-la, ou carrega outro ficheiro de factos iniciais.
 *
 * <p>O motor nao guarda estado entre pedidos: a consola guarda os passos e envia o pedido inteiro a cada acao. Os
 * textos escrevem os termos como o Prolog: com plicas nas explicacoes (o legivel/2), sem plicas nas listagens (o ~w).
 *
 * <p>Corre-se no IntelliJ pelo main desta classe, ou com
 * {@code .\mvnw.cmd -q -pl motores/drools compile exec:java "-Dexec.args=--interativo"}.
 */
public final class ConsolaDrools {

    private final BufferedReader in;
    private final PrintStream out;
    private final ObjectMapper json = new ObjectMapper();
    private final Cenarios cenarios;
    private MotorDrools motor;
    private SessaoConsola sessao;
    private int contador;

    // as opcoes do motor (opcao/2 do Prolog)
    private boolean mostrarConclusoes;
    private boolean passoAPasso;
    private int profundidadeWhynot = 4;

    ConsolaDrools(MotorDrools motor, BufferedReader in, PrintStream out) {
        this.motor = motor;
        this.in = in;
        this.out = out;
        this.cenarios = Cenarios.carrega(json);
    }

    public static void main(String[] args) {
        new ConsolaDrools(new MotorDrools(), new BufferedReader(new InputStreamReader(System.in)), System.out).corre();
    }

    /** O ciclo do menu, ate a opcao 0 ou ao fim da entrada. */
    void corre() {
        out.println("Analista virtual de SOC - motor Drools");
        if (!carregaBase(motor)) {
            return;
        }
        while (true) {
            menu();
            String op = le("Opcao");
            op = op == null ? null : semPontoFinal(op);
            if (op == null || op.equals("0")) {
                return;
            }
            try {
                executa(op);
            } catch (FimDaEntrada f) {
                return;
            } catch (RuntimeException ex) {
                out.println("Erro: " + ex.getMessage());
            }
        }
    }

    private void menu() {
        out.println();
        out.println("========== ANALISTA VIRTUAL DE SOC ==========");
        estado();
        out.println(" 1. Carregar a base de conhecimento");
        out.println(" 2. Correr o motor");
        out.println(" 3. Injetar um cenario");
        out.println(" 4. Introduzir um facto (telemetria)");
        out.println(" 5. Responder as perguntas ao analista");
        out.println(" 6. Incidentes e tickets");
        out.println(" 7. Acoes recomendadas");
        out.println(" 8. Registar a recuperacao de uma maquina");
        out.println(" 9. Explicar como se concluiu um facto (como?)");
        out.println("10. Explicar porque nao se concluiu um facto (porque nao?)");
        out.println("11. Mostrar factos");
        out.println("12. Opcoes do motor");
        out.println("13. Injetar todos os cenarios de uma vez");
        out.println(" 0. Sair");
    }

    private void executa(String op) {
        switch (op) {
            case "1" -> carregaBase();
            case "2" -> correMotor();
            case "3" -> injetaCenarios();
            case "4" -> introduzFactos();
            case "5" -> respondePerguntas();
            case "6" -> mostraIncidentes();
            case "7" -> mostraAcoes();
            case "8" -> registaRecuperacao();
            case "9" -> como();
            case "10" -> porqueNao();
            case "11" -> mostraFactos();
            case "12" -> opcoes();
            case "13" -> injetaTodos();
            default -> out.println("Opcao invalida.");
        }
    }

    // --- 1, 2: base e motor

    private void carregaBase() {
        String f = le("Ficheiro dos factos iniciais (ex.: 'factos_iniciais.json'; base. = a base do modulo)");
        if (f == null) {
            throw new FimDaEntrada();
        }
        f = semPlicas(f);
        if (f.isEmpty() || f.equals("base")) {
            carregaBase(new MotorDrools());
        } else {
            carregaBase(MotorDrools.comFactosIniciais(Path.of(f)));
        }
    }

    /** Recomeca do estado inicial com este motor e corre as regras sobre os dados fixos. */
    private boolean carregaBase(MotorDrools m) {
        EstadoMotor e = m.estado();
        if (!e.pronto()) {
            out.println("Nao foi possivel carregar a base: " + String.join("; ", e.erros()));
            return false;
        }
        motor = m;
        sessao = new SessaoConsola(motor, json);
        contador = 0;
        sessao.recomeca();
        out.printf("Base carregada: %d factos e %d regras.%n", e.factosIniciais(), motor.numeroRegras());
        relatorio(null, null);
        return true;
    }

    private void correMotor() {
        SessaoConsola.Mudancas m = sessao.corre();
        if (m.erro() != null) {
            out.println("O motor respondeu com um erro: " + m.erro());
            return;
        }
        long concluidos = m.factosNovos().stream().filter(f -> !f.get("regra").isNull()).count();
        out.println(concluidos == 0 ? "Nao ha conclusoes novas." : concluidos + " factos novos.");
    }

    private void estado() {
        JsonNode r = sessao.resposta();
        int concluidos = 0;
        for (JsonNode f : r.get("factos")) {
            if (!f.get("regra").isNull()) {
                concluidos++;
            }
        }
        int tickets = r.get("tickets").size();
        int abertos = 0;
        for (JsonNode t : r.get("tickets")) {
            if (t.get("fechado").isNull()) {
                abertos++;
            }
        }
        out.printf("%d factos (%d concluidos pelo motor) | %d incidentes | %d tickets: %d abertos, %d fechados | "
                + "%d perguntas por responder%n", r.get("factos").size(), concluidos, r.get("incidentes").size(), tickets,
                abertos, tickets - abertos, r.get("perguntas").size());
    }

    // --- injetar passos e escrever o que aconteceu (como o introduz_facto e o arranca_motor)

    /** Junta os passos e escreve, para cada um, o cabecalho (se houver), os factos repetidos e as conclusoes. */
    private void injeta(List<JsonNode> passos, Map<String, String> cabecalhos) {
        SessaoConsola.Mudancas m = sessao.junta(passos);
        if (m.erro() != null) {
            out.println("O motor respondeu com um erro (nada foi injetado): " + m.erro());
            return;
        }
        for (JsonNode p : passos) {
            relatorio(p, cabecalhos.get(p.get("id").asText()));
        }
    }

    private void injeta(String id, List<? extends JsonNode> factos) {
        ObjectNode passo = json.createObjectNode();
        passo.put("id", id);
        factos.forEach(passo.putArray("factos")::add);
        injeta(List.of(passo), Map.of());
    }

    /** O que um passo fez (passo null: as conclusoes sobre os dados fixos). */
    private void relatorio(JsonNode passo, String cabecalho) {
        String id = passo == null ? null : passo.get("id").asText();
        if (cabecalho != null) {
            out.println();
            out.println(cabecalho);
        }
        Map<String, JsonNode> porChave = new HashMap<>();
        sessao.resposta().get("factos").forEach(f -> porChave.put(SessaoConsola.chave(f), f));
        if (passo != null) {
            for (JsonNode f : passo.get("factos")) {
                JsonNode existente = porChave.get(SessaoConsola.chave(f));
                if (existente != null && !id.equals(existente.get("passo").asText(null))) {
                    out.printf("O facto %s ja existe (n. %d).%n", TermoTexto.escrito(f), existente.get("numero").asInt());
                }
            }
        }
        List<JsonNode> concluidos = new ArrayList<>();
        for (JsonNode f : sessao.resposta().get("factos")) {
            if (!f.get("regra").isNull() && java.util.Objects.equals(id, f.get("passo").asText(null))) {
                concluidos.add(f);
            }
        }
        for (JsonNode f : concluidos) {
            if (mostrarConclusoes) {
                out.printf("Foi concluido o facto n. %d -> %s   (regra %d)%n", f.get("numero").asInt(),
                        TermoTexto.escrito(f), f.get("regra").asInt());
                if (passoAPasso && le(null) == null) {
                    throw new FimDaEntrada();
                }
            }
        }
        if (concluidos.isEmpty()) {
            out.println("Nao ha conclusoes novas.");
        } else {
            out.printf("%d factos novos (n. %d a %d).%n", concluidos.size(), concluidos.get(0).get("numero").asInt(),
                    concluidos.get(concluidos.size() - 1).get("numero").asInt());
        }
    }

    private String novoId(String prefixo) {
        return prefixo + "_" + (++contador);
    }

    // --- 3, 13: cenarios

    private void injetaCenarios() {
        Set<String> injetados = new HashSet<>(sessao.idsDosPassos());
        for (Cenarios.Cenario c : cenarios.todos()) {
            out.printf("  %-14s%s%s%n", c.id(), c.descricao(), injetados.contains(c.id()) ? "   (ja injetado)" : "");
        }
        out.println("  (ou o nome de um ficheiro, ex.: 'cenarios/cenario_30.txt', ou um pedido .json)");
        String linha = le("Cenario (ex.: c2), ou uma lista (ex.: [c1,c2,c3])");
        if (linha == null) {
            throw new FimDaEntrada();
        }
        linha = semPontoFinal(linha.strip());
        if (linha.isEmpty()) {
            return;
        }
        if (pareceFicheiro(linha)) {
            injetaFicheiro(semPlicas(linha), injetados);
            return;
        }
        String ids = linha.startsWith("[") && linha.endsWith("]") ? linha.substring(1, linha.length() - 1) : linha;
        List<JsonNode> passos = new ArrayList<>();
        Map<String, String> cabecalhos = new LinkedHashMap<>();
        Set<String> nesta = new HashSet<>();
        for (String id : ids.replace(",", " ").trim().split("\\s+")) {
            Cenarios.Cenario c = aceitaCenario(id, injetados, nesta);
            if (c != null) {
                nesta.add(id);
                passos.add(c.passo());
                cabecalhos.put(id, "=== Cenario " + id + ": " + c.descricao());
            }
        }
        if (!passos.isEmpty()) {
            injeta(passos, cabecalhos);
        }
    }

    /** O cenario, se pode entrar; senao escreve porque e devolve null. */
    private Cenarios.Cenario aceitaCenario(String id, Set<String> injetados, Set<String> nesta) {
        Cenarios.Cenario c = cenarios.cenario(id);
        if (c == null) {
            out.println("Nao conheco o cenario " + id + " (a lista acima mostra os que ha).");
            return null;
        }
        if (injetados.contains(id) || nesta.contains(id)) {
            out.println("O cenario " + id + " ja foi injetado.");
            return null;
        }
        for (String alt : c.alternativas()) {
            if (injetados.contains(alt) || nesta.contains(alt)) {
                out.println("O cenario " + id + " e alternativa de " + alt + ", que ja entrou: nao foi injetado.");
                return null;
            }
        }
        for (String dep : c.depende()) {
            if (!injetados.contains(dep) && !nesta.contains(dep)) {
                out.println("Aviso: " + id + " depende de " + dep + ", que ainda nao entrou.");
            }
        }
        return c;
    }

    private static boolean pareceFicheiro(String s) {
        return s.startsWith("'") || s.contains("/") || s.contains("\\") || s.endsWith(".txt") || s.endsWith(".json");
    }

    /**
     * Um ficheiro de cenario (.txt, no formato dos do Prolog) ou um pedido (.json). Um caminho relativo procura-se na
     * pasta atual e nas de cima (o IntelliJ e o Maven correm na pasta do modulo) e, em cada uma, tambem dentro de
     * base_conhecimento/ (como o Prolog procura na pasta do soc_factos.txt).
     */
    private void injetaFicheiro(String nome, Set<String> injetados) {
        Path p = procura(nome);
        if (p == null) {
            out.println("Nao encontro o ficheiro " + nome + ".");
            return;
        }
        try {
            if (nome.endsWith(".json")) {
                JsonNode pedido = json.readTree(Files.readString(p));
                if (pedido.has("versaoKB") && !MotorDrools.VERSAO_KB.equals(pedido.get("versaoKB").asText())) {
                    out.printf("O pedido e da versaoKB \"%s\" e o motor e da %s: nao foi injetado.%n",
                            pedido.get("versaoKB").asText(), MotorDrools.VERSAO_KB);
                    return;
                }
                if (!pedido.path("passos").isArray() || pedido.get("passos").isEmpty()) {
                    out.println("O ficheiro nao tem uma lista \"passos\" com pelo menos um passo.");
                    return;
                }
                List<JsonNode> passos = new ArrayList<>();
                pedido.get("passos").forEach(passos::add);
                injeta(passos, Map.of(passos.get(0).path("id").asText(), "=== Ficheiro " + nome));
                return;
            }
            List<ObjectNode> factos = TermoTexto.factosDeCenario(Files.readAllLines(p));
            String id = null;
            for (Cenarios.Cenario c : cenarios.todos()) {
                if (p.normalize().endsWith(Path.of(c.ficheiro()))) {
                    id = c.id();
                }
            }
            if (id != null && aceitaCenario(id, injetados, Set.of()) == null) {
                return;
            }
            String passo = id != null ? id : novoId("ficheiro");
            ObjectNode j = json.createObjectNode();
            j.put("id", passo);
            factos.forEach(j.putArray("factos")::add);
            injeta(List.of(j), Map.of(passo, "=== Ficheiro " + nome));
        } catch (TermoTexto.ErroSintaxe e) {
            out.println(nome + ": " + e.getMessage());
        } catch (IOException e) {
            out.println("Nao foi possivel ler o ficheiro: " + e.getMessage());
        }
    }

    private static Path procura(String nome) {
        Path p = Path.of(nome);
        if (p.isAbsolute()) {
            return Files.exists(p) ? p : null;
        }
        for (Path pasta = Path.of("").toAbsolutePath(); pasta != null; pasta = pasta.getParent()) {
            for (Path c : List.of(pasta.resolve(nome), pasta.resolve("base_conhecimento").resolve(nome))) {
                if (Files.exists(c)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Como o injeta_todos do Prolog: volta a carregar a base e injeta as sequencias base e normal, sem respostas. */
    private void injetaTodos() {
        if (!carregaBase(motor)) {
            return;
        }
        List<JsonNode> passos = new ArrayList<>();
        Map<String, String> cabecalhos = new LinkedHashMap<>();
        for (String seq : List.of("base", "normal")) {
            for (String id : cenarios.sequencia(seq)) {
                Cenarios.Cenario c = cenarios.cenario(id);
                passos.add(c.passo());
                cabecalhos.put(id, "=== Cenario " + id + ": " + c.descricao());
            }
        }
        injeta(passos, cabecalhos);
        out.println();
        estado();
    }

    // --- 4, 5, 8: factos, respostas, recuperacao

    private void introduzFactos() {
        out.println("Escreva um facto ou uma lista de factos, ex.: "
                + "[alerta_ids(ev_1,web_srv_02,sig_apache_path_traversal), tempo(ev_1,900)]");
        String linha = le("Facto(s)");
        if (linha == null) {
            throw new FimDaEntrada();
        }
        if (linha.isBlank()) {
            return;
        }
        List<ObjectNode> factos;
        try {
            factos = TermoTexto.factos(linha);
        } catch (TermoTexto.ErroSintaxe e) {
            out.println(e.getMessage());
            return;
        }
        injeta(novoId("manual"), factos);
    }

    private void respondePerguntas() {
        while (true) {
            JsonNode perguntas = sessao.resposta().get("perguntas");
            if (perguntas.isEmpty()) {
                out.println("Nao ha perguntas por responder.");
                return;
            }
            out.println("Perguntas por responder:");
            for (int i = 0; i < perguntas.size(); i++) {
                JsonNode p = perguntas.get(i);
                out.printf("  %d. %s sobre o evento %s: %s%n", i + 1, p.get("ativo").asText(), p.get("evento").asText(),
                        p.get("pergunta").asText());
            }
            Integer i = inteiro(le("Numero da pergunta (0 para voltar)"));
            if (i == null || i < 1 || i > perguntas.size()) {
                return;
            }
            pergunta(perguntas.get(i - 1));
        }
    }

    private void pergunta(JsonNode p) {
        String ativo = p.get("ativo").asText();
        String evento = p.get("evento").asText();
        String pergunta = p.get("pergunta").asText();
        List<String[]> opcoes = new ArrayList<>();
        p.get("opcoes").forEach(o -> opcoes.add(new String[] {o.get("valor").asText(), o.get("texto").asText()}));
        if (opcoes.isEmpty()) {
            opcoes.add(new String[] {"sim", "Sim"});
            opcoes.add(new String[] {"nao", "Nao"});
            opcoes.add(new String[] {"nao_sei", "Nao sei"});
        }
        while (true) {
            out.println();
            out.printf("Ativo: %s   Evento: %s%n", ativo, evento);
            out.println(p.get("texto").isNull() ? pergunta + "?" : p.get("texto").asText());
            opcoes.forEach(o -> out.printf("   %s. %s%n", o[0], o[1]));
            out.println("   porque. Porque e que me esta a perguntar isto?");
            String r = le("Resposta");
            if (r == null) {
                throw new FimDaEntrada();
            }
            r = semPontoFinal(r.strip());
            if (r.equals("porque")) {
                porquePergunta(p);
                continue;
            }
            String valor = null;
            Integer n = inteiro(r);
            for (int i = 0; i < opcoes.size(); i++) {
                if ((n != null && n == i + 1) || opcoes.get(i)[0].equals(r)) {
                    valor = opcoes.get(i)[0];
                }
            }
            if (valor == null) {
                out.println("Resposta invalida.");
                continue;
            }
            ObjectNode f = json.createObjectNode();
            f.put("predicado", "resposta_analista");
            f.putArray("args").add(ativo).add(evento).add(pergunta).add(valor);
            injeta(novoId("resposta"), List.of(f));
            return;
        }
    }

    /** Como o porque_pergunta/3 do Prolog: a regra que gerou a pergunta, porque, e as regras que usam a resposta. */
    private void porquePergunta(JsonNode p) {
        out.println();
        Map<Integer, JsonNode> todos = porNumero();
        JsonNode f = todos.get(p.get("facto").asInt());
        if (f == null || f.get("regra").isNull()) {
            out.println("Esta pergunta foi introduzida a mao.");
        } else {
            out.printf("Pergunto porque a regra %d pediu esta verificacao em %s, pelo seguinte:%n", f.get("regra").asInt(),
                    p.get("ativo").asText());
            out.printf("facto %d: %s%n   concluido pela regra %d, porque:%n", f.get("numero").asInt(),
                    TermoTexto.escrito(f), f.get("regra").asInt());
            for (JsonNode premissa : f.get("premissas")) {
                out.println("    " + premissaComo1(premissa, todos));
            }
            ObjectNode termo = json.createObjectNode();
            termo.put("f", "resposta_analista");
            termo.putArray("args").add(json.createObjectNode().put("var", "_"))
                    .add(json.createObjectNode().put("var", "_")).add(p.get("pergunta").asText())
                    .add(json.createObjectNode().put("var", "_"));
            List<Integer> regras = PorqueNao.regrasComCondicao(termo);
            if (!regras.isEmpty()) {
                out.println("A resposta vai ser usada pelas regras " + regras.toString().replace(" ", "") + ".");
            }
        }
        out.println();
    }

    private void registaRecuperacao() {
        out.println("Regista que uma maquina comprometida foi recuperada: um login a partir dela deixa de contar como "
                + "vindo de uma maquina comprometida.");
        Set<String> recuperados = new HashSet<>();
        factosDe("recuperado").forEach(f -> recuperados.add(f.get("args").toString()));
        List<JsonNode> porRecuperar = factosDe("compromisso_confirmado").stream()
                .filter(f -> !recuperados.contains(f.get("args").toString())).toList();
        if (porRecuperar.isEmpty()) {
            out.println("Nao ha compromissos confirmados por recuperar.");
        } else {
            out.println("Compromissos confirmados por recuperar:");
            porRecuperar.forEach(f -> out.printf("   %s (evento %s)%n", f.get("args").get(0).asText(), f.get("args").get(1).asText()));
        }
        String ativo = le("Ativo");
        if (ativo == null) {
            throw new FimDaEntrada();
        }
        String evento = le("Evento do compromisso");
        if (evento == null) {
            throw new FimDaEntrada();
        }
        ativo = semPlicas(ativo);
        evento = semPlicas(evento);
        if (ativo.isEmpty() || evento.isEmpty()) {
            return;
        }
        ObjectNode f = json.createObjectNode();
        f.put("predicado", "recuperado");
        f.putArray("args").add(ativo).add(evento);
        injeta(novoId("recuperacao"), List.of(f));
    }

    // --- 6, 7, 11, 12: listagens e opcoes

    private void mostraIncidentes() {
        JsonNode r = sessao.resposta();
        List<JsonNode> abertos = new ArrayList<>();
        List<JsonNode> fechados = new ArrayList<>();
        r.get("tickets").forEach(t -> (t.get("fechado").isNull() ? abertos : fechados).add(t));
        // msort de P-t(Inc, Ativo, ...): pela prioridade, depois pelo incidente
        abertos.sort(Comparator.comparing((JsonNode t) -> t.get("prioridade").asText())
                .thenComparing(t -> TermoTexto.escrito(t.get("incidente")))
                .thenComparing(t -> t.get("ativo").asText()));
        out.printf("%nTICKETS ABERTOS (%d), por prioridade:%n", abertos.size());
        for (JsonNode t : abertos) {
            JsonNode inc = t.get("incidente");
            String tipo = inc.get("args").get(0).asText();
            out.printf("  [%s] %s  ativo: %s  evento: %s  gravidade: %s%n", t.get("prioridade").asText(), tipo,
                    t.get("ativo").asText(), TermoTexto.escrito(inc.get("args").get(1)), t.get("gravidade").asText());
            out.printf("       acao: %s   ativos impactados: %s%n", TermoTexto.escrito(t.get("acao")),
                    TermoTexto.escrito(t.get("impactados")));
            for (JsonNode m : factosDe("motivo")) {
                if (m.get("args").get(0).equals(inc)) {
                    out.println("       motivo: " + TermoTexto.escrito(m.get("args").get(1)));
                }
            }
            String significado = significado(tipo);
            if (significado != null) {
                out.println("       (" + significado + ")");
            }
        }
        out.printf("%nTICKETS FECHADOS (%d):%n", fechados.size());
        for (JsonNode t : fechados) {
            JsonNode inc = t.get("incidente");
            out.printf("  %s  ativo: %s  evento: %s  -> %s%n", inc.get("args").get(0).asText(), t.get("ativo").asText(),
                    TermoTexto.escrito(inc.get("args").get(1)), TermoTexto.escrito(t.get("fechado")));
        }
        Set<String> comTicket = new HashSet<>();
        r.get("tickets").forEach(t -> comTicket.add(t.get("incidente").toString()));
        List<String> semTicket = new ArrayList<>();
        r.get("incidentes").forEach(i -> {
            if (!comTicket.contains(i.get("id").toString())) {
                semTicket.add(TermoTexto.escrito(i.get("id")));
            }
        });
        if (!semTicket.isEmpty()) {
            out.printf("%nINCIDENTES SEM TICKET: [%s]%n", String.join(",", semTicket));
        }
    }

    private void mostraAcoes() {
        Map<String, List<JsonNode>> porAtivo = new TreeMap<>();
        for (JsonNode a : factosDe("acao")) {
            porAtivo.computeIfAbsent(a.get("args").get(0).asText(), k -> new ArrayList<>()).add(a);
        }
        if (porAtivo.isEmpty()) {
            out.println("Nao ha acoes recomendadas.");
            return;
        }
        porAtivo.forEach((ativo, acoes) -> {
            out.printf("%n%s:%n", ativo);
            for (JsonNode a : acoes) {
                JsonNode acao = a.get("args").get(1);
                out.printf("   %s   (facto %d)%n", TermoTexto.escrito(acao), a.get("numero").asInt());
                String significado = significado(acao.isObject() ? acao.get("f").asText() : acao.asText());
                if (significado != null) {
                    out.println("      " + significado);
                }
            }
        });
    }

    private void mostraFactos() {
        out.println("  todos. | concluidos. | o nome de um facto, ex.: incidente.");
        String q = le("Que factos");
        if (q == null) {
            throw new FimDaEntrada();
        }
        q = semPontoFinal(q.strip());
        for (JsonNode f : sessao.resposta().get("factos")) {
            boolean concluido = !f.get("regra").isNull();
            if (q.equals("concluidos") && concluido) {
                out.printf("O facto n. %d -> %s   (regra %d)%n", f.get("numero").asInt(), TermoTexto.escrito(f),
                        f.get("regra").asInt());
            } else if (q.equals("todos") || f.get("predicado").asText().equals(q)) {
                out.printf("O facto n. %d -> %s%n", f.get("numero").asInt(), TermoTexto.escrito(f));
            }
        }
    }

    private void opcoes() {
        out.println("   mostrar_conclusoes = " + (mostrarConclusoes ? "sim" : "nao"));
        out.println("   passo_a_passo = " + (passoAPasso ? "sim" : "nao"));
        out.println("   profundidade_whynot = " + profundidadeWhynot);
        String nome = le("Opcao a mudar (ou nada.)");
        if (nome == null) {
            throw new FimDaEntrada();
        }
        nome = semPontoFinal(nome.strip());
        if (nome.equals("nada") || nome.isEmpty()) {
            return;
        }
        if (!List.of("mostrar_conclusoes", "passo_a_passo", "profundidade_whynot").contains(nome)) {
            out.println("Opcao desconhecida.");
            return;
        }
        String valor = le("Valor (sim. / nao.)");
        if (valor == null) {
            throw new FimDaEntrada();
        }
        valor = semPontoFinal(valor.strip());
        switch (nome) {
            case "mostrar_conclusoes" -> mostrarConclusoes = valor.equals("sim");
            case "passo_a_passo" -> passoAPasso = valor.equals("sim");
            default -> {
                Integer n = inteiro(valor);
                if (n == null || n < 1) {
                    out.println("A profundidade e um numero inteiro, a partir de 1.");
                } else {
                    profundidadeWhynot = n;
                }
            }
        }
    }

    // --- 9, 10: como? e porque nao?

    private void como() {
        String q = le("Numero do facto ou o proprio facto");
        if (q == null) {
            throw new FimDaEntrada();
        }
        if (q.isBlank()) {
            return;
        }
        Map<Integer, JsonNode> todos = porNumero();
        JsonNode f;
        Integer n = inteiro(q);
        if (n != null) {
            f = todos.get(n);
            if (f == null) {
                out.println("Essa conclusao nao foi tirada.");
                return;
            }
        } else {
            List<ObjectNode> lidos;
            try {
                lidos = TermoTexto.factos(q);
            } catch (TermoTexto.ErroSintaxe e) {
                out.println(e.getMessage());
                return;
            }
            String chave = SessaoConsola.chave(lidos.get(0));
            f = todos.values().stream().filter(x -> SessaoConsola.chave(x).equals(chave)).findFirst().orElse(null);
            if (f == null) {
                String t = TermoTexto.escrito(lidos.get(0));
                out.println("O facto " + t + " nao esta na memoria de trabalho. Experimente o porque nao? (opcao 10).");
                return;
            }
        }
        explica(f, 0, todos, new HashSet<>());
    }

    /** Como o explica_facto/2 do Prolog: a arvore, sem repetir um facto ja explicado. */
    private void explica(JsonNode f, int nivel, Map<Integer, JsonNode> todos, Set<Integer> explicados) {
        String tab = " ".repeat(nivel * 4);
        int n = f.get("numero").asInt();
        if (!explicados.add(n)) {
            out.printf("%sfacto %d: %s   (ja explicado acima)%n", tab, n, TermoTexto.texto(f));
            return;
        }
        if (f.get("regra").isNull()) {
            out.printf("%sfacto %d: %s   (dado)%n", tab, n, TermoTexto.texto(f));
            return;
        }
        out.printf("%sfacto %d: %s%n%s   concluido pela regra %d, porque:%n", tab, n, TermoTexto.texto(f), tab,
                f.get("regra").asInt());
        for (JsonNode p : f.get("premissas")) {
            if (p.isInt()) {
                explica(todos.get(p.asInt()), nivel + 1, todos, explicados);
            } else if (p.isObject() && "nao".equals(p.path("f").asText()) && p.get("args").size() == 1) {
                out.println(" ".repeat((nivel + 1) * 4) + "nao se verifica " + TermoTexto.texto(p.get("args").get(0)));
            } else {
                out.println(" ".repeat((nivel + 1) * 4) + "a condicao " + TermoTexto.texto(p) + " verifica-se (calculada)");
            }
        }
    }

    /** Uma premissa no como1/1 do Prolog: o facto com ~w, a negacao e a calculada com o legivel. */
    private static String premissaComo1(JsonNode p, Map<Integer, JsonNode> todos) {
        if (p.isInt()) {
            return "facto " + p.asInt() + ": " + TermoTexto.escrito(todos.get(p.asInt()));
        }
        if (p.isObject() && "nao".equals(p.path("f").asText()) && p.get("args").size() == 1) {
            return "nao se verifica " + TermoTexto.texto(p.get("args").get(0));
        }
        return "a condicao " + TermoTexto.texto(p) + " verifica-se (calculada)";
    }

    private void porqueNao() {
        String q = le("Facto (ex.: compromisso_confirmado(web_srv_04,ev_e1))");
        if (q == null) {
            throw new FimDaEntrada();
        }
        if (q.isBlank()) {
            return;
        }
        List<ObjectNode> lidos;
        try {
            lidos = TermoTexto.factos(q, true);
        } catch (TermoTexto.ErroSintaxe e) {
            out.println(e.getMessage());
            return;
        }
        JsonNode r;
        try {
            r = json.readTree(motor.porqueNao(sessao.pedido(), lidos.get(0).toString(), profundidadeWhynot));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if ("erro".equals(r.path("estado").asText())) {
            out.println("O motor respondeu com um erro: " + r.path("erro").asText());
            return;
        }
        r.get("explicacao").forEach(l -> out.println(linhaPorqueNao(l)));
    }

    /** O texto de uma linha do "porque nao?", como o whynot/1 do Prolog a escreve. */
    static String linhaPorqueNao(JsonNode l) {
        int nivel = l.get("nivel").asInt();
        String tab = " ".repeat(Math.max(0, (nivel - 1) * 5));
        String t = l.has("termo") ? TermoTexto.texto(l.get("termo")) : "";
        return switch (l.get("tipo").asText()) {
            case "verdadeiro" -> "O facto " + t + " nao e falso! (facto n. " + l.get("numero").asInt() + ")";
            case "nao_verdadeiro" -> tab + "Porque: o facto " + t + " e verdadeiro";
            case "calculada_falsa" -> tab + "Porque: a condicao calculada " + t + " e falsa";
            case "ja_explicado" -> tab + "(" + t + ": ja explicado acima)";
            case "dado_de_entrada" -> tab + "Porque: o facto " + t
                    + " nao esta na base e nenhuma regra o conclui (e um dado de entrada)";
            case "profundidade" -> tab + "(... para ver mais fundo: opcao 12, profundidade_whynot)";
            case "falta_correr_motor" -> tab + "As premissas de uma regra que conclui " + t
                    + " verificam-se: falta correr o motor (opcao 2).";
            case "regra" -> tab + "Porque pela regra " + l.get("regra").asInt() + ":";
            case "condicao_nao_falsa" -> tab + "A condicao nao " + t + " e falsa";
            case "condicao_falsa" -> tab + "A condicao " + t + " e falsa";
            case "premissa_nao_falsa" -> tab + "A premissa nao " + t + " e falsa";
            case "facto_verdadeiro" -> tab + "Porque: o facto " + t + " e verdadeiro (facto n. "
                    + (l.get("numero").isNull() ? "?" : l.get("numero").asInt()) + ")";
            case "calculada_premissa_falsa" -> tab + "A condicao calculada " + t + " e falsa";
            case "premissa_falsa" -> tab + "A premissa " + t + " e falsa";
            default -> tab + l;
        };
    }

    // --- apoio

    private Map<Integer, JsonNode> porNumero() {
        Map<Integer, JsonNode> r = new HashMap<>();
        sessao.resposta().get("factos").forEach(f -> r.put(f.get("numero").asInt(), f));
        return r;
    }

    private List<JsonNode> factosDe(String predicado) {
        List<JsonNode> r = new ArrayList<>();
        sessao.resposta().get("factos").forEach(f -> {
            if (f.get("predicado").asText().equals(predicado)) {
                r.add(f);
            }
        });
        return r;
    }

    /** O texto de significado(Nome, Texto) dos factos iniciais, ou null. */
    private String significado(String nome) {
        for (JsonNode s : factosDe("significado")) {
            if (s.get("args").get(0).asText().equals(nome)) {
                return s.get("args").get(1).asText();
            }
        }
        return null;
    }

    /**
     * Le uma linha, depois de escrever "mensagem: " (o le/2 do Prolog; com mensagem null nao escreve nada). O ponto
     * final do Prolog e opcional. Fim da entrada (Ctrl+D) devolve null.
     */
    private String le(String mensagem) {
        if (mensagem != null) {
            out.print(mensagem + ": ");
            out.flush();
        }
        try {
            String linha = in.readLine();
            return linha == null ? null : linha.strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String semPontoFinal(String s) {
        return s.endsWith(".") ? s.substring(0, s.length() - 1).strip() : s;
    }

    private static String semPlicas(String s) {
        s = semPontoFinal(s.strip());
        if (s.length() >= 2 && (s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\""))) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Integer inteiro(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(semPontoFinal(s.strip()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Fim da entrada a meio de uma opcao: sai da consola. */
    private static final class FimDaEntrada extends RuntimeException {
    }
}
