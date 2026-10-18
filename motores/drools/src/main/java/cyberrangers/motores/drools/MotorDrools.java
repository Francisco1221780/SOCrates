package cyberrangers.motores.drools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.consola.ConsolaDrools;
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
    private static final String SESSAO = "sessao";
    private static final String KBASE = "soc";

    private final ObjectMapper json = new ObjectMapper();
    private final KieContainer container;
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
        KieContainer c = null;
        try {
            c = KieServices.Factory.get().getKieClasspathContainer();
            for (Message m : c.verify().getMessages(Message.Level.ERROR)) {
                erros.add("regras: " + m.getText());
            }
        } catch (RuntimeException e) {
            erros.add("regras: " + e.getMessage());
        }
        boolean regrasOk = c != null && erros.isEmpty();
        int n = 0;
        if (regrasOk) {
            for (var pacote : c.getKieBase(KBASE).getKiePackages()) {
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
        this.container = c;
        this.iniciais = fi;
        this.estado = new EstadoMotor(erros.isEmpty(), regrasOk, fi == null ? 0 : fi.quantos(), erros);
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

    private ObjectNode corre(Pedido pedido) {
        return corre(pedido, base -> Resposta.monta(base, MOTOR, VERSAO_KB));
    }

    /** Uma sessao nova: os factos iniciais, os passos do pedido, e no fim o que se quer da base. */
    private <T> T corre(Pedido pedido, Function<BaseFactos, T> noFim) {
        KieSession sessao = container.newKieSession(SESSAO);
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
