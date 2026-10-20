package socrates.motores.drools.explicacao;

import static socrates.motores.drools.explicacao.Unificacao.deref;
import static socrates.motores.drools.explicacao.Unificacao.unifica;

import socrates.motores.drools.explicacao.Unificacao.Comp;
import socrates.motores.drools.modelo.Facto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * As condicoes das regras avaliadas como no Prolog, sobre os factos da sessao: as solucoes vem pela ordem dos factos
 * (a do facto/2), uma condicao pode ligar variaveis, e uma variavel por ligar onde o Prolog faz contas e um erro de
 * instanciacao, que conta como falso (o chama_calculada/1 do soc_motor.pl).
 *
 * <p>As condicoes calculadas estao aqui escritas outra vez, clausula a clausula, a partir do soc_suporte.txt. As do
 * {@code sessao.CondicoesCalculadas} (que as regras DRL usam) so trabalham com os argumentos ja ligados; o "porque
 * nao?" tambem as avalia com argumentos por ligar, depois de uma premissa que falhou, e ai o Prolog procura nos
 * factos (ex.: {@code anterior(EvAnterior, ev_e1)} e verdadeira se houver algum evento antes). Quem mudar uma condicao
 * no soc_suporte.txt muda-a nos dois sitios.
 */
final class Suporte {

    /** Variavel por ligar onde o Prolog precisa de um valor (instantiation_error). */
    static final class Instanciacao extends RuntimeException {
        Instanciacao() {
            super(null, null, false, false);
        }
    }

    private static final List<String> OPS = List.of("<", "=<", ">", ">=", "==");

    private final List<Facto> factos;
    private final Regras regras;

    Suporte(List<Facto> factos, Regras regras) {
        this.factos = factos;
        this.regras = regras;
    }

    // --- o motor: facto/2, verifica_condicao/2, avalia/2

    /** facto(N, X): as solucoes pela ordem do numero, cada uma com o numero (1, 2, ...). */
    Stream<Solucao> facto(Object x, Map<Var, Object> s) {
        return Stream.iterate(0, i -> i < factos.size(), i -> i + 1)
                .map(i -> {
                    Map<Var, Object> r = unifica(x, factos.get(i), s);
                    return r == null ? null : new Solucao(r, i + 1);
                })
                .filter(Objects::nonNull);
    }

    record Solucao(Map<Var, Object> ligacoes, int numero) {
    }

    /** A primeira solucao de uma condicao do "se" (o verifica_condicao/2, com o "->" do premissas_falsas). */
    Optional<Map<Var, Object>> verifica(Regras.Condicao c, Map<Var, Object> s) {
        try {
            if (c instanceof Regras.Nao n) {
                boolean verdadeiro;
                if (n.termo() instanceof Comp a && a.f().equals("avalia") && a.args().size() == 1) {
                    verdadeiro = avalia(a.args().get(0), s).findFirst().isPresent();
                } else if (regras.calculada(n.termo())) {
                    verdadeiro = calculada(n.termo(), s).findFirst().isPresent();
                } else {
                    verdadeiro = facto(n.termo(), s).findFirst().isPresent();
                }
                return verdadeiro ? Optional.empty() : Optional.of(s);
            }
            if (c instanceof Regras.Avalia a) {
                return avalia(a.termo(), s).findFirst();
            }
            if (regras.calculada(c.termo())) {
                return calculada(c.termo(), s).findFirst();
            }
            return facto(c.termo(), s).findFirst().map(Solucao::ligacoes);
        } catch (Instanciacao e) {
            return Optional.empty();
        }
    }

    /** avalia(F(E, Op, V)): um facto F(E, V1) com V1 Op V. */
    private Stream<Map<Var, Object>> avalia(Object p, Map<Var, Object> s) {
        Object t = deref(p, s);
        List<Object> a = Unificacao.args(t);
        Var v1 = new Var("_");
        Comp p1 = new Comp(Unificacao.nome(t), List.of(a.get(0), v1));
        String op = String.valueOf(deref(a.get(1), s));
        return facto(p1, s).map(Solucao::ligacoes).filter(r -> compara(deref(v1, r), op, deref(a.get(2), r)));
    }

    private static boolean compara(Object v1, String op, Object v) {
        return switch (op) {
            case "==" -> Objects.equals(v1, v);
            case "\\==" -> !Objects.equals(v1, v);
            case ">" -> num(v1) > num(v);
            case "<" -> num(v1) < num(v);
            case ">=" -> num(v1) >= num(v);
            case "=<" -> num(v1) <= num(v);
            default -> false;
        };
    }

    private static long num(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        throw new Instanciacao();
    }

    private Stream<Map<Var, Object>> f(Map<Var, Object> s, String nome, Object... args) {
        return facto(new Comp(nome, Arrays.asList(args)), s).map(Solucao::ligacoes);
    }

    private boolean existe(Map<Var, Object> s, String nome, Object... args) {
        return f(s, nome, args).findFirst().isPresent();
    }

    private static Var v() {
        return new Var("_");
    }

    private static Stream<Map<Var, Object>> so(Map<Var, Object> s) {
        return Stream.of(s);
    }

    // --- condicoes calculadas (soc_suporte.txt)

    /** As solucoes de uma condicao calculada, pela ordem em que o Prolog as encontra. */
    Stream<Map<Var, Object>> calculada(Object c, Map<Var, Object> s) {
        Object t = deref(c, s);
        List<Object> a = Unificacao.args(t);
        return switch (Unificacao.nome(t) + "/" + a.size()) {
            case "depois/3" -> depois(a.get(0), a.get(1), a.get(2), s);
            case "anterior/2" -> anterior(a.get(0), a.get(1), s);
            case "tardia/4" -> tardia(a.get(0), a.get(1), a.get(2), a.get(3), s);
            case "entre/3" -> entre(a.get(0), a.get(1), a.get(2), s);
            case "sem_ataque_intermedio/3" -> semAtaqueIntermedio(a.get(0), a.get(1), a.get(2), s);
            case "versao_cmp/3" -> versaoCmp(a.get(0), a.get(1), a.get(2), s);
            case "versao_invalida/1" -> versaoNormal(a.get(0), s) == null ? so(s) : Stream.empty();
            case "versao_valida/1" -> versaoNormal(a.get(0), s) != null ? so(s) : Stream.empty();
            case "fora_dos_intervalos/2" -> foraDosIntervalos(a.get(0), a.get(1), s);
            case "porto_compativel/3" -> portoCompativel(a.get(0), a.get(1), a.get(2), s);
            case "sem_servico_acessivel/4" -> semServicoAcessivel(a.get(0), a.get(1), a.get(2), a.get(3), s)
                    ? so(s) : Stream.empty();
            case "todas_ao_alcance_nao_afetadas/3" -> todasAoAlcanceNaoAfetadas(a.get(0), a.get(1), a.get(2), s);
            case "fora_do_destino/2" -> foraDoDestino(a.get(0), a.get(1), s);
            case "sem_vetor_exploravel/3" -> semVetorExploravel(a.get(0), a.get(1), a.get(2), s) ? so(s) : Stream.empty();
            case "sem_condicao_em_falta/3" -> semCondicaoEmFalta(a.get(0), a.get(1), a.get(2), s) ? so(s) : Stream.empty();
            case "evidencias_negadas/3" -> evidenciasNegadas(a.get(0), a.get(1), a.get(2), s);
            case "origem_evidencia/5" -> origemEvidencia(a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), s);
            case "em_aberto/1" -> !existe(s, "superado", a.get(0), v()) && !existe(s, "resolvido", a.get(0), v())
                    ? so(s) : Stream.empty();
            case "todos_motivos_revistos/1" -> todosMotivosRevistos(a.get(0), s);
            default -> throw new IllegalStateException("condicao calculada desconhecida: " + t);
        };
    }

    private long tolerancia(Map<Var, Object> s) {
        Var t = v();
        return f(s, "janela", "tolerancia_relogios", t).findFirst().map(r -> num(deref(t, r))).orElse(0L);
    }

    private Stream<Map<Var, Object>> tempos(Object e2, Object e1, Map<Var, Object> s, Var t2, Var t1) {
        return f(s, "tempo", e2, t2).flatMap(r -> f(r, "tempo", e1, t1));
    }

    private Stream<Map<Var, Object>> depois(Object e2, Object e1, Object janela, Map<Var, Object> s) {
        Var t2 = v();
        Var t1 = v();
        return tempos(e2, e1, s, t2, t1).filter(r -> {
            long tol = tolerancia(r);
            long a = num(deref(t2, r));
            long b = num(deref(t1, r));
            return a >= b - tol && a - b <= num(deref(janela, r)) + tol;
        });
    }

    private Stream<Map<Var, Object>> anterior(Object e1, Object e2, Map<Var, Object> s) {
        Var t1 = v();
        Var t2 = v();
        return f(s, "tempo", e1, t1).flatMap(r -> f(r, "tempo", e2, t2))
                .filter(r -> num(deref(t1, r)) < num(deref(t2, r)));
    }

    private Stream<Map<Var, Object>> tardia(Object e2, Object e1, Object curta, Object longa, Map<Var, Object> s) {
        Var t2 = v();
        Var t1 = v();
        return tempos(e2, e1, s, t2, t1).filter(r -> {
            long tol = tolerancia(r);
            long d = num(deref(t2, r)) - num(deref(t1, r));
            return d > num(deref(curta, r)) + tol && d <= num(deref(longa, r)) + tol;
        });
    }

    private Stream<Map<Var, Object>> entre(Object outro, Object e1, Object e2, Map<Var, Object> s) {
        Object o = deref(outro, s);
        Object a = deref(e1, s);
        if (o == a || (!(o instanceof Var) && o.equals(a))) {
            return Stream.empty();
        }
        Var t1 = v();
        Var t2 = v();
        Var t = v();
        return f(s, "tempo", e1, t1).flatMap(r -> f(r, "tempo", e2, t2)).flatMap(r -> f(r, "tempo", outro, t))
                .filter(r -> num(deref(t, r)) > num(deref(t1, r)) && num(deref(t, r)) <= num(deref(t2, r)));
    }

    private Stream<Map<Var, Object>> semAtaqueIntermedio(Object evAtaque, Object evHost, Object ativo, Map<Var, Object> s) {
        Var outro = v();
        return f(s, "tempo", evAtaque, v()).flatMap(r -> f(r, "tempo", evHost, v()))
                .filter(r -> f(r, "alerta_ids", outro, ativo, v())
                        .noneMatch(r2 -> entre(outro, evAtaque, evHost, r2).findFirst().isPresent()));
    }

    /** versao_normal/2: [Ma, Mi, Pa], ou null. */
    private static long[] versaoNormal(Object v, Map<Var, Object> s) {
        Object t = deref(v, s);
        if (t instanceof Var || !"v".equals(Unificacao.nome(t))) {
            return null;
        }
        List<Object> a = Unificacao.args(t);
        if (a.size() != 3 && a.size() != 2) {
            return null;
        }
        long[] r = new long[3];
        for (int i = 0; i < a.size(); i++) {
            Object x = deref(a.get(i), s);
            if (!(x instanceof Number n) || n.longValue() < 0) {
                return null;
            }
            r[i] = n.longValue();
        }
        return r;
    }

    private Stream<Map<Var, Object>> versaoCmp(Object a, Object op, Object b, Map<Var, Object> s) {
        long[] na = versaoNormal(a, s);
        long[] nb = versaoNormal(b, s);
        if (na == null || nb == null) {
            return Stream.empty();
        }
        int c = Arrays.compare(na, nb);
        return OPS.stream().map(o -> unifica(op, o, s)).filter(Objects::nonNull).filter(r -> switch ((String) deref(op, r)) {
            case "<" -> c < 0;
            case "=<" -> c <= 0;
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            default -> c == 0;
        });
    }

    private boolean naIntervalo(Object v, Object vuln, Map<Var, Object> s) {
        Var min = v();
        Var max = v();
        return f(s, "intervalo_afetado", vuln, min, max).anyMatch(r ->
                versaoCmp(v, ">=", deref(min, r), r).findFirst().isPresent()
                        && versaoCmp(v, "=<", deref(max, r), r).findFirst().isPresent());
    }

    private Stream<Map<Var, Object>> foraDosIntervalos(Object v, Object vuln, Map<Var, Object> s) {
        if (versaoNormal(v, s) == null) {
            return Stream.empty();
        }
        return f(s, "intervalo_afetado", vuln, v(), v()).limit(1).filter(r -> !naIntervalo(v, vuln, r));
    }

    private Stream<Map<Var, Object>> portoCompativel(Object ev, Object porto, Object proto, Map<Var, Object> s) {
        Var p0 = v();
        Var pr0 = v();
        Optional<Map<Var, Object>> d = f(s, "destino", ev, p0, pr0).findFirst();
        if (d.isEmpty()) {
            return so(s);
        }
        Map<Var, Object> r = d.get();
        return identicos(porto, p0, r) && identicos(proto, pr0, r) ? so(r) : Stream.empty();
    }

    /** O == do Prolog: identicos sem ligar nada (duas variaveis so se forem a mesma). */
    private static boolean identicos(Object a, Object b, Map<Var, Object> s) {
        Object x = deref(a, s);
        Object y = deref(b, s);
        if (x instanceof Var || y instanceof Var) {
            return x == y;
        }
        return Unificacao.variante(x, s).equals(Unificacao.variante(y, s));
    }

    private Stream<Map<Var, Object>> servicosAoAlcance(Object ev, Object ativo, Object nome, Object zona, Object inst,
            Map<Var, Object> s) {
        Var porto = v();
        Var proto = v();
        return f(s, "servico", inst, ativo, nome, porto, proto)
                .flatMap(r -> portoCompativel(ev, porto, proto, r))
                .flatMap(r -> f(r, "exposto", ativo, porto, proto, zona));
    }

    private boolean semServicoAcessivel(Object ev, Object ativo, Object nome, Object zona, Map<Var, Object> s) {
        return servicosAoAlcance(ev, ativo, nome, zona, v(), s).findFirst().isEmpty();
    }

    private Stream<Map<Var, Object>> todasAoAlcanceNaoAfetadas(Object ev, Object ativo, Object vuln, Map<Var, Object> s) {
        Var zona = v();
        Var nome = v();
        return f(s, "origem_zona", ev, zona).flatMap(r -> f(r, "vuln_afeta", vuln, nome)).filter(r -> {
            Var inst = v();
            return servicosAoAlcance(ev, ativo, nome, zona, inst, r).allMatch(r2 -> existe(r2, "nao_afetado", inst, vuln));
        });
    }

    private Stream<Map<Var, Object>> foraDoDestino(Object ev, Object inst, Map<Var, Object> s) {
        Var porto = v();
        Var proto = v();
        return f(s, "destino", ev, porto, proto).filter(r -> !existe(r, "servico", inst, v(), v(), porto, proto));
    }

    private boolean revisto(Object termo, Map<Var, Object> s) {
        return existe(s, "revisto", termo, v());
    }

    private boolean semVetorExploravel(Object ev, Object ativo, Object vuln, Map<Var, Object> s) {
        Var inst = v();
        return f(s, "vetor_aberto", ev, ativo, inst, vuln)
                .noneMatch(r -> existe(r, "vulneravel", inst, vuln)
                        && !revisto(new Comp("vetor_aberto", List.of(ev, ativo, inst, vuln)), r));
    }

    private boolean semCondicaoEmFalta(Object inst, Object vuln, Object impacto, Map<Var, Object> s) {
        Var cond = v();
        return f(s, "requer_condicao", vuln, impacto, cond).noneMatch(r -> existe(r, "configuracao", inst, cond, "nao"));
    }

    private Stream<Map<Var, Object>> evidenciasNegadas(Object ativo, Object ev, Object vuln, Map<Var, Object> s) {
        Var i0 = v();
        return f(s, "impacto_possivel", ev, ativo, vuln, i0)
                .filter(r -> !existe(r, "impacto_excluido", ev, ativo, vuln, i0, v()))
                .limit(1)
                .filter(r -> {
                    Var imp = v();
                    if (!f(r, "vuln_impacto", vuln, imp).allMatch(r2 -> existe(r2, "evidencia_impacto", imp, v(), v()))) {
                        return false;
                    }
                    Var imp2 = v();
                    Var tipo = v();
                    return f(r, "impacto_possivel", ev, ativo, vuln, imp2)
                            .filter(r2 -> !existe(r2, "impacto_excluido", ev, ativo, vuln, imp2, v()))
                            .flatMap(r2 -> f(r2, "evidencia_impacto", imp2, tipo, v()))
                            .allMatch(r2 -> {
                                Var p = v();
                                return f(r2, "pergunta_evidencia", tipo, p)
                                        .anyMatch(r3 -> existe(r3, "resposta_analista", ativo, ev, p, "nao"));
                            });
                });
    }

    private Stream<Map<Var, Object>> origemEvidencia(Object evHost, Object evAtaque, Object ativo, Object vuln,
            Object origem, Map<Var, Object> s) {
        Var prog = v();
        return f(s, "vuln_afeta", vuln, prog).limit(1).flatMap(r -> {
            Var o = v();
            return classificaOrigem(evHost, evAtaque, ativo, vuln, prog, o, r).limit(1)
                    .map(r2 -> unifica(origem, o, r2)).filter(Objects::nonNull);
        });
    }

    private Stream<Map<Var, Object>> classificaOrigem(Object evHost, Object evAtaque, Object ativo, Object vuln,
            Object prog, Var o, Map<Var, Object> s) {
        Var i = v();
        Optional<Map<Var, Object>> inst = f(s, "evento_instancia", evHost, i).findFirst();
        if (inst.isPresent()) {
            return instanciaLigada(evAtaque, ativo, vuln, i, o, inst.get());
        }
        Var nome = v();
        Optional<Map<Var, Object>> proc = f(s, "evento_processo", evHost, nome).findFirst();
        if (proc.isEmpty()) {
            return Stream.ofNullable(unifica(o, new Comp("por_confirmar", List.of("sem_processo")), s));
        }
        Map<Var, Object> r = proc.get();
        if (!existe(r, "processo_componente", v(), nome)) {
            return Stream.ofNullable(unifica(o, comp("por_confirmar", comp("processo_desconhecido", deref(nome, r))), r));
        }
        if (!existe(r, "processo_componente", prog, nome)) {
            Var outro = v();
            Map<Var, Object> r2 = f(r, "processo_componente", outro, nome).findFirst().orElseThrow();
            return Stream.ofNullable(unifica(o, comp("de_outro_processo", comp("programa", deref(outro, r2))), r2));
        }
        List<Object> is = instanciasComNome(ativo, nome, r);
        if (is.size() == 1) {
            return Stream.ofNullable(unifica(i, is.get(0), r)).flatMap(r2 -> instanciaLigada(evAtaque, ativo, vuln, i, o, r2));
        }
        String razao = is.isEmpty() ? "instancia_nao_inventariada" : "instancia_por_identificar";
        return Stream.ofNullable(unifica(o, comp("por_confirmar", comp(razao, deref(nome, r))), r));
    }

    private Stream<Map<Var, Object>> instanciaLigada(Object evAtaque, Object ativo, Object vuln, Object i, Var o,
            Map<Var, Object> s) {
        Var inst = v();
        Optional<Map<Var, Object>> atacada = f(s, "vetor_aberto", evAtaque, ativo, inst, vuln)
                .filter(r -> !revisto(new Comp("vetor_aberto", List.of(evAtaque, ativo, inst, vuln)), r))
                .flatMap(r -> f(r, "vulneravel", inst, vuln))
                .flatMap(r -> f(r, "instancia_processo", inst, i))
                .findFirst();
        return atacada.isPresent()
                ? Stream.ofNullable(unifica(o, "ligada", atacada.get()))
                : Stream.ofNullable(unifica(o, comp("de_outro_processo", comp("instancia", deref(i, s))), s));
    }

    /** findall + sort: as instancias de processo do ativo cujo programa corre com esse nome, sem repetidos. */
    private List<Object> instanciasComNome(Object ativo, Object nome, Map<Var, Object> s) {
        Var inst = v();
        Var i = v();
        Var p = v();
        TreeSet<String> vistos = new TreeSet<>();
        List<Object> r = new ArrayList<>();
        f(s, "instancia_processo", inst, i).flatMap(r1 -> f(r1, "componente", inst, ativo, p))
                .flatMap(r1 -> f(r1, "processo_componente", p, nome))
                .forEach(r1 -> {
                    Object x = Unificacao.resolve(i, r1);
                    if (vistos.add(Unificacao.variante(x, r1))) {
                        r.add(x);
                    }
                });
        return r;
    }

    private Stream<Map<Var, Object>> todosMotivosRevistos(Object inc, Map<Var, Object> s) {
        return f(s, "motivo", inc, v()).limit(1).filter(r -> {
            Var m = v();
            return f(r, "motivo", inc, m).allMatch(r2 ->
                    revisto(new Comp("motivo", List.of(Unificacao.resolve(inc, r2), Unificacao.resolve(m, r2))), r2));
        });
    }

    private static Comp comp(String f, Object... args) {
        return new Comp(f, Arrays.asList(args));
    }
}
