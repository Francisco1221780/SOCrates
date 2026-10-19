package cyberrangers.motores.drools.consola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.EstadoMotor;
import cyberrangers.motores.drools.MotorDrools;
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
 * Consola interativa do motor Drools, com o menu/0 do soc_motor.pl: as mesmas opcoes e os mesmos textos, mas numeradas
 * por ordem (0 a 14), porque o "porque?" (10) entra junto do "como?" (9) e do "porque nao?" (11), que no Prolog e a 10.
 * As tres explicacoes estao na {@link Explicacoes}. A diferenca para o Prolog e que arranca ja com a base do modulo
 * carregada (as regras, o factos_iniciais.json e o cenarios.json vem no classpath); a opcao 1 volta a carrega-la, ou
 * carrega outro ficheiro de factos iniciais.
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
    private final Explicacoes explicacoes;
    private MotorDrools motor;
    private SessaoConsola sessao;
    private int contador;

    // as opcoes do motor (opcao/2 do Prolog)
    private boolean mostrarConclusoes;
    private boolean passoAPasso;
    private int profundidadeWhynot = 4;

    /** Pausa depois de cada opcao, ate se carregar em Enter, para o menu nao tapar o que a opcao escreveu. */
    private final boolean pausa;

    ConsolaDrools(MotorDrools motor, BufferedReader in, PrintStream out) {
        this(motor, in, out, false);
    }

    ConsolaDrools(MotorDrools motor, BufferedReader in, PrintStream out, boolean pausa) {
        this.motor = motor;
        this.in = in;
        this.out = out;
        this.pausa = pausa;
        this.cenarios = Cenarios.de(lerJson(motor.cenarios()));
        this.explicacoes = new Explicacoes(out);
    }

    private JsonNode lerJson(String texto) {
        try {
            return json.readTree(texto);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        new ConsolaDrools(new MotorDrools(), new BufferedReader(new InputStreamReader(System.in)), System.out, true).corre();
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
            if (pausa && le("\nEnter para voltar ao menu") == null) {
                return;
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
        out.println("10. Explicar porque se concluiu um facto, com as regras (porque?)");
        out.println("11. Explicar porque nao se concluiu um facto (porque nao?)");
        out.println("12. Mostrar factos");
        out.println("13. Opcoes do motor");
        out.println("14. Injetar todos os cenarios de uma vez");
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
            case "10" -> porque();
            case "11" -> porqueNao();
            case "12" -> mostraFactos();
            case "13" -> opcoes();
            case "14" -> injetaTodos();
            default -> out.println("Opcao invalida.");
        }
    }

    // --- 1, 2: base e motor

    private void carregaBase() {
        String f = le("Ficheiro dos factos iniciais (ex.: factos_iniciais.json; Enter = a base do modulo)");
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
                    out.printf("O facto %s ja existe (n. %d).%n", TermoTexto.legivel(f), existente.get("numero").asInt());
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
                        TermoTexto.legivel(f), f.get("regra").asInt());
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
        out.println("  (ou o nome de um ficheiro, ex.: cenarios/cenario_30.txt, ou um pedido .json)");
        String linha = le("Cenario (ex.: c2), ou varios (ex.: c1 c2 c3)");
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
        out.println("Escreva um facto por linha, ex.: alerta_ids(ev_1, web_srv_02, sig_apache_path_traversal)");
        out.println("(uma linha vazia termina)");
        List<ObjectNode> factos = new ArrayList<>();
        while (true) {
            String linha = le("Facto");
            if (linha == null) {
                if (factos.isEmpty()) {
                    throw new FimDaEntrada();
                }
                break;
            }
            if (linha.isBlank()) {
                break;
            }
            try {
                factos.addAll(TermoTexto.factos(linha));
            } catch (TermoTexto.ErroSintaxe e) {
                out.println(e.getMessage() + " (o facto nao foi aceite)");
            }
        }
        if (!factos.isEmpty()) {
            injeta(novoId("manual"), factos);
        }
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
            opcoes.forEach(o -> out.printf("   %s - %s%n", o[0], o[1]));
            out.println("   porque - Porque e que me esta a perguntar isto?");
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

    /** O porque_pergunta/3 do Prolog: a regra que gerou a pergunta, por inteiro, e as regras que usam a resposta. */
    private void porquePergunta(JsonNode p) {
        JsonNode r = chama(motor.porquePergunta(sessao.pedido(), p.get("ativo").asText(), p.get("evento").asText(),
                p.get("pergunta").asText()));
        if (r != null) {
            explicacoes.porquePergunta(r);
        }
    }

    /** Le a resposta do motor; se for um erro escreve-o e devolve null. */
    private JsonNode chama(String resposta) {
        JsonNode r = lerJson(resposta);
        if ("erro".equals(r.path("estado").asText())) {
            out.println("O motor respondeu com um erro: " + r.path("erro").asText());
            return null;
        }
        return r;
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
                .thenComparing(t -> TermoTexto.legivel(t.get("incidente")))
                .thenComparing(t -> t.get("ativo").asText()));
        out.printf("%nTICKETS ABERTOS (%d), por prioridade:%n", abertos.size());
        for (JsonNode t : abertos) {
            JsonNode inc = t.get("incidente");
            String tipo = inc.get("args").get(0).asText();
            out.printf("  [%s] %s  ativo: %s  evento: %s  gravidade: %s%n", t.get("prioridade").asText(), tipo,
                    t.get("ativo").asText(), TermoTexto.legivel(inc.get("args").get(1)), t.get("gravidade").asText());
            out.printf("       acao: %s   ativos impactados: %s%n", TermoTexto.legivel(t.get("acao")),
                    TermoTexto.legivel(t.get("impactados")));
            for (JsonNode m : factosDe("motivo")) {
                if (m.get("args").get(0).equals(inc)) {
                    out.println("       motivo: " + TermoTexto.legivel(m.get("args").get(1)));
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
                    TermoTexto.legivel(inc.get("args").get(1)), TermoTexto.legivel(t.get("fechado")));
        }
        Set<String> comTicket = new HashSet<>();
        r.get("tickets").forEach(t -> comTicket.add(t.get("incidente").toString()));
        List<String> semTicket = new ArrayList<>();
        r.get("incidentes").forEach(i -> {
            if (!comTicket.contains(i.get("id").toString())) {
                semTicket.add(TermoTexto.legivel(i.get("id")));
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
                out.printf("   %s   (facto %d)%n", TermoTexto.legivel(acao), a.get("numero").asInt());
                String significado = significado(acao.isObject() ? acao.get("f").asText() : acao.asText());
                if (significado != null) {
                    out.println("      " + significado);
                }
            }
        });
    }

    private void mostraFactos() {
        out.println("  todos | concluidos | o nome de um predicado:");
        out.println(predicados(false));
        String q = le("Que factos");
        if (q == null) {
            throw new FimDaEntrada();
        }
        q = semPontoFinal(q.strip());
        for (JsonNode f : sessao.resposta().get("factos")) {
            boolean concluido = !f.get("regra").isNull();
            if (q.equals("concluidos") && concluido) {
                out.printf("O facto n. %d -> %s   (regra %d)%n", f.get("numero").asInt(), TermoTexto.legivel(f),
                        f.get("regra").asInt());
            } else if (q.equals("todos") || f.get("predicado").asText().equals(q)) {
                out.printf("O facto n. %d -> %s%n", f.get("numero").asInt(), TermoTexto.legivel(f));
            }
        }
    }

    private void opcoes() {
        out.println("   mostrar_conclusoes = " + (mostrarConclusoes ? "sim" : "nao"));
        out.println("   passo_a_passo = " + (passoAPasso ? "sim" : "nao"));
        out.println("   profundidade_whynot = " + profundidadeWhynot);
        String nome = le("Opcao a mudar (Enter para nao mudar nada)");
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
        String valor = le("Valor (sim / nao)");
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

    // --- 9, 14, 10: como?, porque? e porque nao?

    /**
     * Pede um facto da memoria de trabalho, pelo numero ou escrito; o nome de um predicado lista as conclusoes dele
     * (com o numero) e volta a pedir. Null se nao ha (ja escreveu porque).
     */
    private JsonNode escolheFacto(Map<Integer, JsonNode> todos) {
        out.println("Conclusoes do motor, por predicado:");
        out.println(predicados(true));
        while (true) {
            String q = le("Numero do facto, o proprio facto, ou o nome de um predicado para o listar (Enter para voltar)");
            if (q == null) {
                throw new FimDaEntrada();
            }
            q = semPontoFinal(q.strip());
            if (q.isBlank() || q.equals("0")) {
                return null;
            }
            if (q.matches("[a-z][A-Za-z0-9_]*") && listaConclusoes(q, todos)) {
                continue;
            }
            return facto(q, todos);
        }
    }

    /** Lista as conclusoes de um predicado (numero e facto); false se o motor nao concluiu nenhuma. */
    private boolean listaConclusoes(String predicado, Map<Integer, JsonNode> todos) {
        List<JsonNode> lista = todos.values().stream()
                .filter(f -> f.get("predicado").asText().equals(predicado) && !f.get("regra").isNull()
                        && f.get("args").size() > 0)
                .sorted(Comparator.comparingInt((JsonNode f) -> f.get("numero").asInt())).toList();
        if (lista.isEmpty()) {
            return false;
        }
        lista.forEach(f -> out.printf("  %d. %s%n", f.get("numero").asInt(), TermoTexto.legivel(f)));
        return true;
    }

    /** Os predicados dos factos (so os concluidos pelo motor, ou todos), um por linha, com quantos ha de cada. */
    private String predicados(boolean soConcluidos) {
        Map<String, Integer> contagem = new TreeMap<>();
        for (JsonNode f : sessao.resposta().get("factos")) {
            if (!soConcluidos || !f.get("regra").isNull()) {
                contagem.merge(f.get("predicado").asText(), 1, Integer::sum);
            }
        }
        if (contagem.isEmpty()) {
            return "  (ainda nao ha)";
        }
        List<String> linhas = new ArrayList<>();
        contagem.forEach((nome, n) -> linhas.add("  " + nome + " (" + n + ")"));
        return String.join("\n", linhas);
    }

    /** O facto da memoria de trabalho a que q se refere (numero ou facto escrito); null, com a mensagem, se nao ha. */
    private JsonNode facto(String q, Map<Integer, JsonNode> todos) {
        Integer n = inteiro(q);
        if (n != null) {
            JsonNode f = todos.get(n);
            if (f == null) {
                out.println("Essa conclusao nao foi tirada.");
            }
            return f;
        }
        List<ObjectNode> lidos;
        try {
            lidos = TermoTexto.factos(q);
        } catch (TermoTexto.ErroSintaxe e) {
            out.println(e.getMessage());
            return null;
        }
        String chave = SessaoConsola.chave(lidos.get(0));
        JsonNode f = todos.values().stream().filter(x -> SessaoConsola.chave(x).equals(chave)).findFirst().orElse(null);
        if (f == null) {
            out.println("O facto " + TermoTexto.legivel(lidos.get(0))
                    + " nao esta na memoria de trabalho. Experimente o porque nao? (opcao 11).");
        }
        return f;
    }

    private static String numeroDoFacto(JsonNode f) {
        return "{\"numero\":" + f.get("numero").asInt() + "}";
    }

    private void como() {
        Map<Integer, JsonNode> todos = porNumero();
        JsonNode f = escolheFacto(todos);
        JsonNode r = f == null ? null : chama(motor.como(sessao.pedido(), numeroDoFacto(f)));
        if (r != null) {
            explicacoes.como(r.get("arvore"));
        }
    }

    private void porque() {
        Map<Integer, JsonNode> todos = porNumero();
        JsonNode f = escolheFacto(todos);
        JsonNode r = f == null ? null : chama(motor.porque(sessao.pedido(), numeroDoFacto(f)));
        if (r != null) {
            explicacoes.porque(r.get("arvore"));
        }
    }

    private void porqueNao() {
        out.println("As regras concluem factos destes tipos:");
        chama(motor.predicadosConcluidos()).get("predicados").forEach(n -> out.println("  " + n.asText()));
        String q = le("Facto (ex.: compromisso_confirmado(web_srv_04, ev_e1); uma maiuscula, ex.: Ev, e qualquer valor)");
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
        explicacoes.porqueNao(lidos.get(0), r.get("explicacao"));
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
