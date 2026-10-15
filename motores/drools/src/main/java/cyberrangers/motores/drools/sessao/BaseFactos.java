package cyberrangers.motores.drools.sessao;

import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Premissa;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.RuleContext;

/**
 * Os factos de uma sessao, numerados por ordem de chegada, como o facto/2 do motor Prolog. E o global {@code base}
 * das regras DRL: as regras nao fazem insert, chamam {@link #cria}, que nao repete um facto que ja exista
 * (como o cria_facto/3) e guarda a regra e as premissas (como o justifica/3).
 */
public class BaseFactos {

    private final KieSession sessao;
    private final Map<Facto, Registo> registos = new HashMap<>();
    private final List<Facto> factos = new ArrayList<>();
    private String passo;

    public BaseFactos(KieSession sessao) {
        this.sessao = sessao;
    }

    /** Os factos que entram ou sao concluidos a seguir ficam com este passo (null: dados fixos). */
    public void passo(String id) {
        this.passo = id;
    }

    public void insereInicial(Facto f) {
        if (regista(f, Origem.INICIAL, null, List.of())) {
            sessao.insert(f);
        }
    }

    /** Facto de um passo do pedido; se ja existir, e ignorado (como o novo_facto/1 da base). */
    public void insereEntrada(Facto f) {
        if (regista(f, Origem.ENTRADA, null, List.of())) {
            sessao.insert(f);
        }
    }

    /**
     * Conclui um facto a partir do ENTAO de uma regra. As premissas vao uma por condicao do LHS, pela ordem da regra
     * em soc_regras.txt. Se o facto ja existir, fica como estava (com a primeira justificacao).
     */
    public void cria(RuleContext contexto, int regra, List<Premissa> premissas, Facto f) {
        if (regista(f, Origem.REGRA, regra, premissas)) {
            contexto.getKieRuntime().insert(f);
        }
    }

    private boolean regista(Facto f, Origem origem, Integer regra, List<Premissa> premissas) {
        if (registos.containsKey(f)) {
            return false;
        }
        String p = origem == Origem.INICIAL ? null : passo;
        registos.put(f, new Registo(factos.size() + 1, origem, p, regra, premissas));
        factos.add(f);
        return true;
    }

    public boolean existe(Facto f) {
        return registos.containsKey(f);
    }

    /** O numero, a origem, o passo e a justificacao do facto, ou null se nao estiver na base. */
    public Registo registo(Facto f) {
        return registos.get(f);
    }

    /** Todos os factos, por ordem do numero. */
    public List<Facto> factos() {
        return Collections.unmodifiableList(factos);
    }
}
