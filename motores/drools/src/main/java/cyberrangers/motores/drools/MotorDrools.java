package cyberrangers.motores.drools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.json.FactosIniciais;
import cyberrangers.motores.drools.json.Pedido;
import cyberrangers.motores.drools.json.PedidoInvalido;
import cyberrangers.motores.drools.json.Resposta;
import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.sessao.BaseFactos;
import java.util.ArrayList;
import java.util.List;
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

    private final ObjectMapper json = new ObjectMapper();
    private final KieContainer container;
    private final FactosIniciais iniciais;
    private final EstadoMotor estado;

    public MotorDrools() {
        this(FactosIniciais.RECURSO);
    }

    /** @param recursoFactos o ficheiro de factos iniciais no classpath (outro nome so nos testes) */
    MotorDrools(String recursoFactos) {
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
        FactosIniciais fi = null;
        try {
            fi = FactosIniciais.carrega(recursoFactos, VERSAO_KB, json);
        } catch (IllegalStateException e) {
            erros.add("factos iniciais: " + e.getMessage());
        }
        this.container = c;
        this.iniciais = fi;
        this.estado = new EstadoMotor(erros.isEmpty(), regrasOk, fi == null ? 0 : fi.quantos(), erros);
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

    private ObjectNode corre(Pedido pedido) {
        KieSession sessao = container.newKieSession(SESSAO);
        try {
            BaseFactos base = new BaseFactos(sessao);
            sessao.setGlobal("base", base);
            for (Facto f : iniciais.copias()) {
                base.insereInicial(f);
            }
            sessao.fireAllRules();
            for (Pedido.Passo passo : pedido.passos()) {
                base.passo(passo.id());
                for (Facto f : passo.factos()) {
                    base.insereEntrada(f);
                }
                sessao.fireAllRules();
            }
            return Resposta.monta(base, MOTOR, VERSAO_KB);
        } finally {
            sessao.dispose();
        }
    }

    private String erro(String mensagem) {
        return Resposta.erro(MOTOR, VERSAO_KB, mensagem).toString();
    }
}
