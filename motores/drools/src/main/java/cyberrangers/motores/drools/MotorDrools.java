package cyberrangers.motores.drools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.consola.ConsolaDrools;
import cyberrangers.motores.drools.explicacao.Explicacao;
import cyberrangers.motores.drools.explicacao.PorqueNao;
import cyberrangers.motores.drools.json.FactosIniciais;
import cyberrangers.motores.drools.json.Pedido;
import cyberrangers.motores.drools.json.PedidoInvalido;
import cyberrangers.motores.drools.json.Resposta;
import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.sessao.BaseFactos;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

/**
 * Motor Drools: recebe o pedido JSON (lista de passos), corre-o numa KieSession nova e devolve a resposta JSON,
 * no mesmo formato do wrapper.pl. Como no wrapper, as regras correm primeiro sobre os dados fixos e depois, passo a
 * passo, sobre os factos de cada passo.
 */
public class MotorDrools {

    public static final String VERSAO_KB = "6";

    private static final String MOTOR = "drools";
    private static final String KBASE = "soc";
    private static final String PASTA_REGRAS = "regras";

    private final ObjectMapper json = new ObjectMapper();
    private final KieBase base;
    private final FactosIniciais iniciais;
    private final EstadoMotor estado;
    private final Predicate<String> regrasAtivas;
    private final int numeroRegras;

    public MotorDrools() {
        this(FactosIniciais.RECURSO);
    }

    /**
     * Linha de comandos, como o wrapper.pl: le o pedido do stdin e escreve so a resposta JSON no stdout;
     * codigo de saida 1 se a resposta for um erro. Com {@code --interativo}, abre a consola com o menu do
     * soc_motor.pl ({@link ConsolaDrools}). Ver "Correr so o Drools" no README do modulo.
     */
    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("--interativo")) {
            ConsolaDrools.main(args);
            return;
        }
        String pedido = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        MotorDrools motor = new MotorDrools();
        String resposta = motor.infere(pedido);
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        out.println(resposta);
        boolean erro = "erro".equals(motor.json.readTree(resposta).path("estado").asText());
        System.exit(erro ? 1 : 0);
    }

    /** @param recursoFactos o ficheiro de factos iniciais no classpath (outro nome so nos testes) */
    MotorDrools(String recursoFactos) {
        this(recursoFactos, regra -> true);
    }

    /**
     * So para os testes: so disparam as regras cujo nome ({@code "R7"}, ...) o filtro aceita. O teste de paridade usa-o
     * para correr so os grupos A a G e N contra a referencia Prolog com essas regras.
     */
    MotorDrools(String recursoFactos, Predicate<String> regrasAtivas) {
        this(() -> FactosIniciais.carrega(recursoFactos, VERSAO_KB, new ObjectMapper()), regrasAtivas);
    }

    /**
     * As regras do modulo com outro ficheiro de factos iniciais (o mesmo formato do factos_iniciais.json). E a opcao 1
     * da consola ("Carregar a base de conhecimento").
     */
    public static MotorDrools comFactosIniciais(Path ficheiro) {
        return new MotorDrools(() -> FactosIniciais.carrega(ficheiro, VERSAO_KB, new ObjectMapper()), regra -> true);
    }

    private MotorDrools(Supplier<FactosIniciais> factosIniciais, Predicate<String> regrasAtivas) {
        this.regrasAtivas = regrasAtivas;
        List<String> erros = new ArrayList<>();
        KieBase c = null;
        try {
            c = baseOrdenada(erros);
        } catch (RuntimeException e) {
            erros.add("regras: " + e.getMessage());
        }
        boolean regrasOk = c != null && erros.isEmpty();
        int n = 0;
        if (regrasOk) {
            for (var pacote : c.getKiePackages()) {
                n += pacote.getRules().size();
            }
        }
        this.numeroRegras = n;
        FactosIniciais fi = null;
        try {
            fi = factosIniciais.get();
        } catch (IllegalStateException e) {
            erros.add("factos iniciais: " + e.getMessage());
        }
        this.base = c;
        this.iniciais = fi;
        this.estado = new EstadoMotor(erros.isEmpty(), regrasOk, fi == null ? 0 : fi.quantos(), erros);
    }

    /**
     * As regras compiladas com os DRL pela ordem do nome (A_afetado.drl, B_vetor.drl, ...), juntas uma a uma num
     * KnowledgeBuilder. A ordem em que as regras entram decide qual de duas ativacoes ao mesmo tempo dispara primeiro
     * (ex.: a R5 antes da R11 para o mesmo alerta), e isso decide a justificacao de um facto concluido por duas vias.
     * Com esta ordem o Drools da o mesmo que o Prolog (ParidadeTest). O getKieClasspathContainer() le a pasta pela
     * ordem do sistema de ficheiros (alfabetica no Windows, arbitraria no Linux), e o KieFileSystem pela ordem de um
     * hash: os dois davam outras premissas na CI. Se a pasta nao se conseguir listar, usa o contentor do classpath.
     */
    private static KieBase baseOrdenada(List<String> erros) {
        List<String> drl = ficheirosDrl();
        if (drl.isEmpty()) {
            KieContainer c = KieServices.Factory.get().getKieClasspathContainer();
            for (Message m : c.verify().getMessages(Message.Level.ERROR)) {
                erros.add("regras: " + m.getText());
            }
            return erros.isEmpty() ? c.getKieBase(KBASE) : null;
        }
        KnowledgeBuilder kb = KnowledgeBuilderFactory.newKnowledgeBuilder();
        for (String f : drl) {
            kb.add(ResourceFactory.newByteArrayResource(recurso(f)).setSourcePath(f), ResourceType.DRL);
            if (kb.hasErrors()) {
                kb.getErrors().forEach(e -> erros.add("regras: " + f + ": " + e.getMessage()));
                return null;
            }
        }
        InternalKnowledgeBase base = KnowledgeBaseFactory.newKnowledgeBase();
        base.addPackages(kb.getKnowledgePackages());
        return base;
    }

    /** Os .drl de regras/ no classpath (pasta ou jar), por ordem do nome; vazio se nao se conseguirem listar. */
    private static List<String> ficheirosDrl() {
        List<String> r = new ArrayList<>();
        try {
            URL url = MotorDrools.class.getClassLoader().getResource(PASTA_REGRAS);
            if (url == null) {
                return r;
            }
            if (url.getProtocol().equals("file")) {
                try (var lista = Files.list(Path.of(url.toURI()))) {
                    lista.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".drl"))
                            .forEach(n -> r.add(PASTA_REGRAS + "/" + n));
                }
            } else if (url.openConnection() instanceof JarURLConnection jar) {
                // sem cache: o JarFile e so desta leitura, e fecha-lo nao fecha o do class loader
                jar.setUseCaches(false);
                try (JarFile j = jar.getJarFile()) {
                    j.stream().map(JarEntry::getName)
                            .filter(n -> n.startsWith(PASTA_REGRAS + "/") && n.endsWith(".drl") && n.indexOf('/', PASTA_REGRAS.length() + 1) < 0)
                            .forEach(r::add);
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            return new ArrayList<>();
        }
        r.sort(null);
        return r;
    }

    private static byte[] recurso(String nome) {
        try (InputStream in = MotorDrools.class.getClassLoader().getResourceAsStream(nome)) {
            if (in == null) {
                throw new IllegalStateException("nao existe " + nome + " no classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(nome + ": " + e.getMessage(), e);
        }
    }

    /** Quantas regras DRL compilaram (92 com a base v6 inteira). */
    public int numeroRegras() {
        return numeroRegras;
    }

    /** Se as regras e os factos iniciais carregaram (AC13). */
    public EstadoMotor estado() {
        return estado;
    }

    public String infere(String pedidoJson) {
        try {
            if (!estado.pronto()) {
                return erro("motor drools nao esta pronto: " + String.join("; ", estado.erros()));
            }
            Pedido pedido = Pedido.de(json.readTree(pedidoJson), VERSAO_KB);
            return json.writeValueAsString(corre(pedido));
        } catch (PedidoInvalido e) {
            return erro(e.getMessage());
        } catch (JsonProcessingException e) {
            return erro("pedido JSON invalido: " + e.getOriginalMessage());
        } catch (RuntimeException e) {
            return erro("erro do motor: " + e.getMessage());
        }
    }

    /**
     * "Porque nao?" (o whynot/1 do soc_motor.pl): corre o pedido e explica porque o facto nao se concluiu, na mesma
     * sessao. O facto e {@code {"predicado","args"}}, e os argumentos podem ter variaveis ({@code {"var": "Ev"}}). A
     * resposta e {@code {estado, motor, versaoKB, facto, explicacao: [linhas], erro}}; as linhas estao descritas em
     * {@link PorqueNao}. A profundidade e a do profundidade_whynot do Prolog (4 por omissao).
     */
    public String porqueNao(String pedidoJson, String factoJson, int profundidade) {
        try {
            if (!estado.pronto()) {
                return erro("motor drools nao esta pronto: " + String.join("; ", estado.erros()));
            }
            Pedido pedido = Pedido.de(json.readTree(pedidoJson), VERSAO_KB);
            JsonNode facto = json.readTree(factoJson);
            if (facto == null || !facto.path("predicado").isTextual() || !facto.path("args").isArray()) {
                return erro("o facto tem de ser {\"predicado\", \"args\"}");
            }
            ObjectNode r = corre(pedido, base -> {
                ObjectNode o = json.createObjectNode();
                o.put("estado", "concluido");
                o.put("motor", MOTOR);
                o.put("versaoKB", VERSAO_KB);
                o.set("facto", facto);
                o.set("explicacao", PorqueNao.explica(facto, base.factos(), profundidade));
                o.putNull("erro");
                return o;
            });
            return json.writeValueAsString(r);
        } catch (PedidoInvalido e) {
            return erro(e.getMessage());
        } catch (JsonProcessingException e) {
            return erro("pedido JSON invalido: " + e.getOriginalMessage());
        } catch (RuntimeException e) {
            return erro("erro do motor: " + e.getMessage());
        }
    }

    /**
     * "Como?" (opcao 9 da consola): corre o pedido e devolve a arvore de factos de uma conclusao. O facto escolhe-se
     * por {@code {"numero": N}} ou por {@code {"predicado", "args"}} (o primeiro que existir). A resposta e
     * {@code {estado, motor, versaoKB, facto, arvore, erro}}; a arvore esta descrita em {@link Explicacao#como}.
     */
    public String como(String pedidoJson, String factoJson) {
        return arvore(pedidoJson, factoJson, false);
    }

    /** "Porque?" (de uma pergunta, opcao 5, e a API): o mesmo que {@link #como}, e cada no concluido traz a regra completa ({@code regraCompleta}). */
    public String porque(String pedidoJson, String factoJson) {
        return arvore(pedidoJson, factoJson, true);
    }

    private String arvore(String pedidoJson, String factoJson, boolean comRegras) {
        return Explicacao.como(infere(pedidoJson), factoJson, comRegras);
    }

    /**
     * O "porque?" de uma pergunta ao analista (a opcao 5, {@code porque}): a regra que a gerou, escrita por inteiro,
     * com os factos que a levaram ({@code geradaPor}, null se a pergunta foi introduzida a mao), e as regras que
     * usam a resposta. A pergunta e a da resposta do {@link #infere}, por ativo, evento e pergunta.
     */
    public String porquePergunta(String pedidoJson, String ativo, String evento, String pergunta) {
        return Explicacao.porquePergunta(infere(pedidoJson), ativo, evento, pergunta);
    }

    /**
     * Os cenarios da base (id, grupo, descricao, dependencias, alternativas e o passo pronto a enviar) e as sequencias
     * (base, atualizacoes, normal): o {@code cenarios.json} do modulo, tal como esta.
     */
    public String cenarios() {
        try (InputStream in = MotorDrools.class.getResourceAsStream("/cenarios.json")) {
            if (in == null) {
                return erro("nao existe cenarios.json no classpath (gera_json.pl)");
            }
            return json.readTree(in).toString();
        } catch (IOException e) {
            return erro("cenarios.json: " + e.getMessage());
        }
    }

    /** Os nomes dos factos que alguma regra pode concluir: o que faz sentido pedir ao "porque nao?". */
    public String predicadosConcluidos() {
        ObjectNode o = envelope();
        o.set("predicados", json.valueToTree(PorqueNao.predicadosConcluidos()));
        o.putNull("erro");
        return o.toString();
    }

    private ObjectNode envelope() {
        ObjectNode o = json.createObjectNode();
        o.put("estado", "concluido");
        o.put("motor", MOTOR);
        o.put("versaoKB", VERSAO_KB);
        return o;
    }

    private ObjectNode corre(Pedido pedido) {
        return corre(pedido, base -> Resposta.monta(base, MOTOR, VERSAO_KB));
    }

    /** Uma sessao nova: os factos iniciais, os passos do pedido, e no fim o que se quer da base. */
    private <T> T corre(Pedido pedido, Function<BaseFactos, T> noFim) {
        KieSession sessao = base.newKieSession();
        try {
            BaseFactos base = new BaseFactos(sessao);
            sessao.setGlobal("base", base);
            for (Facto f : iniciais.copias()) {
                base.insereInicial(f);
            }
            dispara(sessao);
            for (Pedido.Passo passo : pedido.passos()) {
                base.passo(passo.id());
                for (Facto f : passo.factos()) {
                    base.insereEntrada(f);
                }
                dispara(sessao);
            }
            return noFim.apply(base);
        } finally {
            sessao.dispose();
        }
    }

    private void dispara(KieSession sessao) {
        sessao.fireAllRules(m -> regrasAtivas.test(m.getRule().getName()));
    }

    private String erro(String mensagem) {
        return Resposta.erro(MOTOR, VERSAO_KB, mensagem).toString();
    }
}
