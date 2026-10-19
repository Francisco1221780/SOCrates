package cyberrangers.motores.drools.explicacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cyberrangers.motores.drools.explicacao.Unificacao.Comp;
import cyberrangers.motores.drools.modelo.Facto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * "Porque nao?": o whynot/1 do soc_motor.pl, ramo a ramo, sobre os factos de uma sessao. Para cada regra que podia
 * concluir o facto, percorre o "se" por ordem e junta as premissas que falham; depois explica cada uma, descendo pelas
 * que outras regras podiam concluir, ate a profundidade. Nao repete um facto ja visto.
 *
 * <p>Devolve uma lista de linhas, pela ordem em que o Prolog as escreve, cada uma com o {@code nivel}, o {@code tipo}
 * e o que a linha precisa ({@code termo}, {@code regra}, {@code numero}). A consola escreve-as com os textos do Prolog
 * ({@code ConsolaDrools}); os termos vem com as variaveis por ligar chamadas A, B, ... como no legivel/2.
 */
public final class PorqueNao {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static Regras regrasCarregadas;

    private final Regras regras;
    private final Suporte suporte;
    private final int profundidade;
    private final ArrayNode linhas = JSON.arrayNode();
    private final Set<String> jaVistos = new HashSet<>();

    private PorqueNao(List<Facto> factos, int profundidade) {
        this.regras = regras();
        this.suporte = new Suporte(factos, regras);
        this.profundidade = profundidade;
    }

    private static synchronized Regras regras() {
        if (regrasCarregadas == null) {
            regrasCarregadas = Regras.carrega();
        }
        return regrasCarregadas;
    }

    /**
     * Explica porque o facto (JSON {@code {"predicado","args"}}, os argumentos podem ter {@code {"var": "X"}}) nao se
     * concluiu, sobre os factos da sessao, por ordem do numero.
     */
    public static ArrayNode explica(JsonNode facto, List<Facto> factos, int profundidade) {
        PorqueNao p = new PorqueNao(factos, profundidade);
        p.whynot(Unificacao.deJson(facto, new HashMap<>()), new HashMap<>(), 1);
        return p.linhas;
    }

    /** Os nomes dos factos que alguma regra pode concluir (o "entao" das regras), por ordem alfabetica. */
    public static List<String> predicadosConcluidos() {
        Set<String> nomes = new java.util.TreeSet<>();
        for (Regras.Regra r : regras().todas()) {
            for (JsonNode t : r.entao()) {
                nomes.add(t.isTextual() ? t.asText() : t.path("f").asText());
            }
        }
        return new ArrayList<>(nomes);
    }

    /**
     * A regra {@code id} como esta no soc_regras.txt, com os nomes originais das variaveis: {@code {id, se, entao}},
     * cada condicao do {@code se} e um termo, {@code {"nao": termo}} ou {@code {"avalia": termo}}. Null se nao existe.
     */
    public static JsonNode regra(int id) {
        for (Regras.Regra r : regras().todas()) {
            if (r.id() == id) {
                ObjectNode o = JSON.objectNode();
                o.put("id", r.id());
                o.set("se", r.se());
                o.set("entao", r.entao());
                return o;
            }
        }
        return null;
    }

    /**
     * As regras que tem no "se" uma condicao (positiva) que unifica com o termo, por numero e sem repetir. E o
     * escreve_regras_que_usam_resposta/1 do Prolog (ex.: as regras que usam resposta_analista(_,_,shell_servico,_)).
     */
    public static List<Integer> regrasComCondicao(JsonNode termo) {
        Set<Integer> r = new java.util.TreeSet<>();
        for (Regras.Regra regra : regras().todas()) {
            Object t = Unificacao.deJson(termo, new HashMap<>());
            for (Regras.Condicao c : regra.instancia().se()) {
                if (!(c instanceof Regras.Nao) && Unificacao.unifica(t, c.termo(), new HashMap<>()) != null) {
                    r.add(regra.id());
                }
            }
        }
        return new ArrayList<>(r);
    }

    private void linha(String tipo, int nivel, Object termo, Map<Var, Object> s) {
        ObjectNode l = linhas.addObject();
        l.put("nivel", nivel);
        l.put("tipo", tipo);
        if (termo != null) {
            l.set("termo", Unificacao.json(termo, s));
        }
    }

    private void whynot(Object facto, Map<Var, Object> s, int nivel) {
        Object f = Unificacao.deref(facto, s);
        boolean nao = f instanceof Comp c && c.f().equals("nao") && c.args().size() == 1;
        if (!nao) {
            Optional<Suporte.Solucao> sol = suporte.facto(f, s).findFirst();
            if (sol.isPresent()) {
                linha("verdadeiro", nivel, f, sol.get().ligacoes());
                ((ObjectNode) linhas.get(linhas.size() - 1)).put("numero", sol.get().numero());
                return;
            }
        } else {
            linha("nao_verdadeiro", nivel, ((Comp) f).args().get(0), s);
            return;
        }
        if (regras.calculada(f)) {
            linha("calculada_falsa", nivel, f, s);
            return;
        }
        String chave = Unificacao.variante(f, s);
        if (jaVistos.contains(chave)) {
            linha("ja_explicado", nivel, f, s);
            return;
        }
        if (!regraConclui(f, s)) {
            linha("dado_de_entrada", nivel, f, s);
            return;
        }
        if (nivel > profundidade) {
            linha("profundidade", nivel, null, s);
            return;
        }
        jaVistos.add(chave);
        List<Falhadas> porRegra = encontraRegras(f, s);
        if (porRegra.isEmpty()) {
            linha("falta_correr_motor", nivel, f, s);
            return;
        }
        for (Falhadas r : porRegra) {
            linha("regra", nivel, null, s);
            ObjectNode l = (ObjectNode) linhas.get(linhas.size() - 1);
            l.put("regra", r.regra());
            juntaRegraCompleta(l, r);
            explicaPorqueNao(r, nivel + 1);
        }
    }

    /** Como ficou uma condicao do "se" depois de percorrido: se se verificou e, num facto, o numero dele. */
    private record Estado(Regras.Condicao condicao, boolean verificada, Integer numero) {
    }

    /**
     * Uma regra que podia concluir o facto, as premissas que falham, as ligacoes com que ficaram, o estado de todas as
     * condicoes do "se" (por ordem) e o "entao".
     */
    private record Falhadas(int regra, List<Regras.Condicao> premissas, Map<Var, Object> ligacoes,
            List<Estado> estados, List<Object> entao) {
    }

    /** Junta a uma linha "regra" o "se" completo (cada condicao com o estado) e o "entao", com as ligacoes feitas. */
    private void juntaRegraCompleta(ObjectNode linha, Falhadas f) {
        Map<Var, Object> s = f.ligacoes();
        ArrayNode se = linha.putArray("se");
        for (Estado e : f.estados()) {
            ObjectNode c = se.addObject();
            c.put("tipo", e.condicao() instanceof Regras.Nao ? "nao" : e.condicao() instanceof Regras.Avalia
                    ? "avalia" : "facto");
            c.set("termo", Unificacao.json(e.condicao().termo(), s));
            c.put("verificada", e.verificada());
            if (e.numero() != null) {
                c.put("numero", e.numero());
            }
        }
        ArrayNode entao = linha.putArray("entao");
        f.entao().forEach(t -> entao.add(Unificacao.json(t, s)));
    }

    private boolean regraConclui(Object f, Map<Var, Object> s) {
        for (Regras.Regra r : regras.todas()) {
            for (Object conclusao : r.instancia().entao()) {
                if (Unificacao.unifica(f, conclusao, s) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** encontra_regras_whynot/2: cada regra (e cada cria_facto dela) que unifica com o facto e tem premissas falsas. */
    private List<Falhadas> encontraRegras(Object f, Map<Var, Object> s) {
        List<Falhadas> r = new ArrayList<>();
        for (Regras.Regra regra : regras.todas()) {
            Regras.Instancia inst = regra.instancia();
            for (Object conclusao : inst.entao()) {
                Map<Var, Object> s2 = Unificacao.unifica(f, conclusao, s);
                if (s2 != null) {
                    Falhadas falhadas = premissasFalsas(inst.id(), inst.se(), inst.entao(), s2);
                    if (!falhadas.premissas().isEmpty()) {
                        r.add(falhadas);
                    }
                }
            }
        }
        return r;
    }

    /**
     * premissas_falsas/3: uma condicao que se verifica liga as variaveis; uma que falha vai para a lista. Uma condicao
     * que partilha variaveis com uma que ja falhou so se testa, sem ligar nada.
     */
    private Falhadas premissasFalsas(int regra, List<Regras.Condicao> se, List<Object> entao, Map<Var, Object> s) {
        List<Regras.Condicao> falsas = new ArrayList<>();
        List<Var> varsFalhadas = new ArrayList<>();
        List<Estado> estados = new ArrayList<>();
        for (Regras.Condicao c : se) {
            List<Var> vars = new ArrayList<>();
            Unificacao.variaveis(c.termo(), s, vars);
            boolean partilha = vars.stream().anyMatch(v -> varsFalhadas.stream().anyMatch(x -> x == v));
            Optional<Map<Var, Object>> r = suporte.verifica(c, s);
            Integer numero = null;
            if (r.isPresent()) {
                if (c instanceof Regras.Positiva && !regras.calculada(c.termo())) {
                    numero = suporte.facto(c.termo(), s).findFirst().map(Suporte.Solucao::numero).orElse(null);
                }
                if (!partilha) {
                    s = r.get();
                }
            } else {
                falsas.add(c);
                varsFalhadas.addAll(vars);
            }
            estados.add(new Estado(c, r.isPresent(), numero));
        }
        return new Falhadas(regra, falsas, s, estados, entao);
    }

    /** explica_porque_nao/2. */
    private void explicaPorqueNao(Falhadas f, int nivel) {
        Map<Var, Object> s = f.ligacoes();
        for (Regras.Condicao c : f.premissas()) {
            Object t = c.termo();
            if (c instanceof Regras.Nao && t instanceof Comp a && a.f().equals("avalia") && a.args().size() == 1) {
                linha("condicao_nao_falsa", nivel, a.args().get(0), s);
            } else if (c instanceof Regras.Avalia) {
                linha("condicao_falsa", nivel, t, s);
            } else if (c instanceof Regras.Nao) {
                linha("premissa_nao_falsa", nivel, t, s);
                Optional<Suporte.Solucao> sol = suporte.facto(t, s).findFirst();
                linha("facto_verdadeiro", nivel + 1, t, s);
                ObjectNode l = (ObjectNode) linhas.get(linhas.size() - 1);
                if (sol.isPresent()) {
                    l.put("numero", sol.get().numero());
                    l.set("encontrado", Unificacao.json(t, sol.get().ligacoes()));
                } else {
                    l.putNull("numero");
                }
            } else if (regras.calculada(t)) {
                linha("calculada_premissa_falsa", nivel, t, s);
            } else {
                linha("premissa_falsa", nivel, t, s);
                whynot(t, s, nivel + 1);
            }
        }
    }
}
