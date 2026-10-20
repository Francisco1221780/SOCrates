package cyberrangers.motores.drools.sessao;

import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Premissa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.RuleContext;

/**
 * Os factos de uma sessao, numerados por ordem de chegada, como o facto/2 do motor Prolog. E o global {@code base}
 * das regras DRL: as regras nao fazem insert, chamam {@link #cria}, que nao repete um facto que ja exista
 * (como o cria_facto/3) e guarda a regra e as premissas (como o justifica/3).
 */
public class BaseFactos {

    /**
     * Os factos que as condicoes calculadas leem, do condicao_le/2 do soc_suporte.txt: quando entra um, as regras
     * com a {@link Marca} desse functor reavaliam-se. Quem mudar o condicao_le muda esta lista.
     */
    static final List<String> LIDOS_POR_CONDICOES = List.of(
            "tempo/2", "janela/2", "intervalo_afetado/3", "destino/3", "nao_afetado/2", "revisto/2",
            "resposta_analista/4", "impacto_excluido/5", "evento_processo/2", "evento_instancia/2",
            "processo_componente/2", "instancia_processo/2", "vetor_aberto/4", "vulneravel/2");

    private final KieSession sessao;
    private final Map<Facto, Registo> registos = new HashMap<>();
    private final List<Facto> factos = new ArrayList<>();
    private final Map<String, Marca> marcas = new HashMap<>();
    private final Map<String, FactHandle> handlesMarcas = new HashMap<>();
    private String passo;

    public BaseFactos(KieSession sessao) {
        this.sessao = sessao;
        for (String functor : LIDOS_POR_CONDICOES) {
            Marca m = new Marca(functor);
            marcas.put(functor, m);
            handlesMarcas.put(functor, sessao.insert(m));
        }
    }

    /** Os factos que entram ou sao concluidos a seguir ficam com este passo (null: dados fixos). */
    public void passo(String id) {
        this.passo = id;
    }

    public void insereInicial(Facto f) {
        if (regista(f, Origem.INICIAL, null, List.of())) {
            insere(sessao, f);
        }
    }

    /** Facto de um passo do pedido; se ja existir, e ignorado (como o novo_facto/1 da base). */
    public void insereEntrada(Facto f) {
        if (regista(f, Origem.ENTRADA, null, List.of())) {
            insere(sessao, f);
        }
    }

    /**
     * Conclui um facto a partir do ENTAO de uma regra. As premissas vao uma por condicao do LHS, pela ordem da regra
     * em soc_regras.txt. Se o facto ja existir, nao se repete; fica com a justificacao que o Prolog escolheria
     * (ver {@link #comoNoProlog}).
     */
    public void cria(RuleContext contexto, int regra, List<Premissa> premissas, Facto f) {
        Registo atual = registos.get(f);
        if (atual == null) {
            regista(f, Origem.REGRA, regra, premissas);
            insere(contexto.getKieRuntime(), f);
        } else if (comoNoProlog(atual, regra, premissas)) {
            registos.put(f, new Registo(atual.numero(), atual.origem(), atual.passo(), regra, premissas));
        }
    }

    /**
     * Se a nova justificacao e a que o Prolog teria guardado em vez da atual. O Prolog percorre os factos por ordem do
     * numero e fica com a primeira instanciacao que encontra; o Drools dispara primeiro a ativacao mais recente. Para a
     * mesma regra, no mesmo passo, a primeira que o Prolog encontra e a dos factos com numeros mais baixos (ex.: a R4
     * com o servico no porto 80 e nao no 443, se o 80 tiver o numero mais baixo). So se troca se todas as premissas
     * novas ja existiam antes do facto concluido: o Prolog nunca justifica um facto com outro posterior.
     */
    private boolean comoNoProlog(Registo atual, int regra, List<Premissa> premissas) {
        if (atual.origem() != Origem.REGRA || atual.regra() != regra || !Objects.equals(atual.passo(), passo)) {
            return false;
        }
        int[] novos = numeros(premissas);
        for (int n : novos) {
            if (n >= atual.numero()) {
                return false;
            }
        }
        return Arrays.compare(novos, numeros(atual.premissas())) < 0;
    }

    /** Os numeros dos factos nas premissas, por ordem crescente. */
    private int[] numeros(List<Premissa> premissas) {
        return premissas.stream()
                .filter(p -> p instanceof Premissa.DoFacto)
                .mapToInt(p -> registos.get(((Premissa.DoFacto) p).facto()).numero())
                .sorted()
                .toArray();
    }

    private void insere(EntryPoint sessao, Facto f) {
        sessao.insert(f);
        Marca m = marcas.get(f.getFunctor());
        if (m != null) {
            m.incrementa();
            sessao.update(handlesMarcas.get(f.getFunctor()), m);
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
