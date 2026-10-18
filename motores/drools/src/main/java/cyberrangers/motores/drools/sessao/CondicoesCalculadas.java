package cyberrangers.motores.drools.sessao;

import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Termo;
import java.util.List;
import java.util.Objects;

/**
 * Condicoes calculadas do soc_suporte.txt implementadas como metodos Java estaticos.
 * As regras DRL chamam estes metodos em vez de os procurar como factos na working memory.
 * Cada metodo recebe a {@link BaseFactos} para aceder ao estado atual da sessao.
 *
 * <p>Grupos:
 * <ol>
 *   <li>Versoes e portos (necessarios para os grupos A e B das regras)</li>
 *   <li>Tempo (com a tolerancia dos relogios)</li>
 *   <li>Evidencia, estado dos incidentes, conclusoes revistas</li>
 * </ol>
 */
public final class CondicoesCalculadas {

    private CondicoesCalculadas() {
    }

    // =========================================================================
    // GRUPO 1: Versoes e portos (necessarios para os grupos A e B)
    // =========================================================================

    /**
     * versao_normal/2: normaliza um Termo v(Ma,Mi) ou v(Ma,Mi,Pa) para uma lista imutavel [Ma,Mi,Pa] de Long.
     * Retorna null se V nao for um Termo "v" com 2 ou 3 argumentos inteiros >= 0.
     * Em Prolog: versao_normal(V, v(Ma, Mi, Pa)).
     */
    public static List<Long> versaoNormal(Object v) {
        if (!(v instanceof Termo t)) return null;
        if (!"v".equals(t.f())) return null;
        List<Object> args = t.args();
        if (args.size() == 3) {
            Object ma = args.get(0), mi = args.get(1), pa = args.get(2);
            if (!campoVersaoValido(ma) || !campoVersaoValido(mi) || !campoVersaoValido(pa)) return null;
            return List.of(toLong(ma), toLong(mi), toLong(pa));
        } else if (args.size() == 2) {
            Object ma = args.get(0), mi = args.get(1);
            if (!campoVersaoValido(ma) || !campoVersaoValido(mi)) return null;
            return List.of(toLong(ma), toLong(mi), 0L);
        }
        return null;
    }

    private static boolean campoVersaoValido(Object x) {
        return (x instanceof Long l && l >= 0) || (x instanceof Integer i && i >= 0);
    }

    private static long toLong(Object x) {
        return ((Number) x).longValue();
    }

    /** versao_invalida/1: a versao nao e um Termo v valido (ex.: "2.4.49-ubuntu1"). */
    public static boolean versaoInvalida(Object v) {
        return versaoNormal(v) == null;
    }

    /** versao_valida/1: a versao e um Termo v valido. */
    public static boolean versaoValida(Object v) {
        return versaoNormal(v) != null;
    }

    /**
     * versao_cmp/3: compara duas versoes com o operador dado (como @< etc. do Prolog para termos v(...)).
     * Op suportados: {@code "<"}, {@code "=<"}, {@code ">"}, {@code ">="}, {@code "=="}.
     */
    public static boolean versaoCmp(Object a, String op, Object b) {
        List<Long> na = versaoNormal(a);
        List<Long> nb = versaoNormal(b);
        if (na == null || nb == null) return false;
        int cmp = comparaVersoes(na, nb);
        return switch (op) {
            case "<"  -> cmp < 0;
            case "=<" -> cmp <= 0;
            case ">"  -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "==" -> cmp == 0;
            default   -> false;
        };
    }

    private static int comparaVersoes(List<Long> a, List<Long> b) {
        for (int i = 0; i < 3; i++) {
            int c = Long.compare(a.get(i), b.get(i));
            if (c != 0) return c;
        }
        return 0;
    }

    /**
     * fora_dos_intervalos/2: V e uma versao valida, o catalogo tem pelo menos um intervalo_afetado da Vuln,
     * e V nao esta em nenhum deles. Sem intervalos no catalogo nao se conclui nada (R9).
     */
    public static boolean foraDosIntervalos(Object v, Object vuln, BaseFactos base) {
        if (versaoNormal(v) == null) return false;
        boolean temIntervalo = false;
        for (Facto f : base.factos()) {
            if (!"intervalo_afetado/3".equals(f.getFunctor())) continue;
            if (!Objects.equals(f.arg(0), vuln)) continue;
            temIntervalo = true;
            Object min = f.arg(1);
            Object max = f.arg(2);
            if (versaoCmp(v, ">=", min) && versaoCmp(v, "=<", max)) return false;
        }
        return temIntervalo;
    }

    /**
     * porto_compativel/3: se o alerta Ev trouxer destino(Ev, Porto, Proto), tem de ser esse;
     * se nao trouxer, qualquer porto serve.
     */
    public static boolean portoCompativel(Object ev, Object porto, Object proto, BaseFactos base) {
        for (Facto f : base.factos()) {
            if ("destino/3".equals(f.getFunctor()) && Objects.equals(f.arg(0), ev)) {
                return Objects.equals(f.arg(1), porto) && Objects.equals(f.arg(2), proto);
            }
        }
        return true;
    }

    /**
     * sem_servico_acessivel/4: nenhum servico do programa no ativo escuta num porto e protocolo
     * abertos para a zona (e, se o alerta Ev trouxer o porto de destino, nesse porto).
     */
    public static boolean semServicoAcessivel(Object ev, Object ativo, Object nome, Object zona, BaseFactos base) {
        for (Facto srv : base.factos()) {
            if (!"servico/5".equals(srv.getFunctor())) continue;
            if (!Objects.equals(srv.arg(1), ativo)) continue;
            if (!Objects.equals(srv.arg(2), nome)) continue;
            Object porto = srv.arg(3);
            Object proto = srv.arg(4);
            if (!portoCompativel(ev, porto, proto, base)) continue;
            for (Facto exp : base.factos()) {
                if ("exposto/4".equals(exp.getFunctor())
                        && Objects.equals(exp.arg(0), ativo)
                        && Objects.equals(exp.arg(1), porto)
                        && Objects.equals(exp.arg(2), proto)
                        && Objects.equals(exp.arg(3), zona)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * todas_ao_alcance_nao_afetadas/3: todas as instalacoes do programa afetado que o ataque Ev alcanca
     * (servico num porto aberto para a zona de origem e, se o alerta o disser, no porto de destino)
     * estao marcadas como nao_afetado.
     */
    public static boolean todasAoAlcanceNaoAfetadas(Object ev, Object ativo, Object vuln, BaseFactos base) {
        Object zona = null;
        for (Facto f : base.factos()) {
            if ("origem_zona/2".equals(f.getFunctor()) && Objects.equals(f.arg(0), ev)) {
                zona = f.arg(1);
                break;
            }
        }
        if (zona == null) return false;

        Object nome = null;
        for (Facto f : base.factos()) {
            if ("vuln_afeta/2".equals(f.getFunctor()) && Objects.equals(f.arg(0), vuln)) {
                nome = f.arg(1);
                break;
            }
        }
        if (nome == null) return false;

        final Object zonaFinal = zona, nomeFinal = nome;

        for (Facto srv : base.factos()) {
            if (!"servico/5".equals(srv.getFunctor())) continue;
            if (!Objects.equals(srv.arg(1), ativo)) continue;
            if (!Objects.equals(srv.arg(2), nomeFinal)) continue;
            Object porto = srv.arg(3);
            Object proto = srv.arg(4);
            Object inst = srv.arg(0);
            if (!portoCompativel(ev, porto, proto, base)) continue;
            boolean exposto = false;
            for (Facto exp : base.factos()) {
                if ("exposto/4".equals(exp.getFunctor())
                        && Objects.equals(exp.arg(0), ativo)
                        && Objects.equals(exp.arg(1), porto)
                        && Objects.equals(exp.arg(2), proto)
                        && Objects.equals(exp.arg(3), zonaFinal)) {
                    exposto = true;
                    break;
                }
            }
            if (!exposto) continue;
            boolean naoAfetado = false;
            for (Facto f : base.factos()) {
                if ("nao_afetado/2".equals(f.getFunctor())
                        && Objects.equals(f.arg(0), inst)
                        && Objects.equals(f.arg(1), vuln)) {
                    naoAfetado = true;
                    break;
                }
            }
            if (!naoAfetado) return false;
        }
        return true;
    }

    /**
     * fora_do_destino/2: o alerta Ev ja diz o porto de destino, e a instalacao nao escuta nesse porto.
     */
    public static boolean foraDoDestino(Object ev, Object inst, BaseFactos base) {
        Object porto = null, proto = null;
        for (Facto f : base.factos()) {
            if ("destino/3".equals(f.getFunctor()) && Objects.equals(f.arg(0), ev)) {
                porto = f.arg(1);
                proto = f.arg(2);
                break;
            }
        }
        if (porto == null) return false;
        final Object p = porto, pr = proto;
        for (Facto f : base.factos()) {
            if ("servico/5".equals(f.getFunctor())
                    && Objects.equals(f.arg(0), inst)
                    && Objects.equals(f.arg(3), p)
                    && Objects.equals(f.arg(4), pr)) {
                return false;
            }
        }
        return true;
    }

    /**
     * sem_vetor_exploravel/3: nenhum vetor em vigor (nao revisto) do ataque Ev chega a uma instalacao vulneravel.
     */
    public static boolean semVetorExploravel(Object ev, Object ativo, Object vuln, BaseFactos base) {
        for (Facto va : base.factos()) {
            if (!"vetor_aberto/4".equals(va.getFunctor())) continue;
            if (!Objects.equals(va.arg(0), ev)) continue;
            if (!Objects.equals(va.arg(1), ativo)) continue;
            if (!Objects.equals(va.arg(3), vuln)) continue;
            Object inst = va.arg(2);
            boolean revisto = false;
            for (Facto r : base.factos()) {
                if ("revisto/2".equals(r.getFunctor()) && Objects.equals(r.arg(0), va)) {
                    revisto = true;
                    break;
                }
            }
            if (revisto) continue;
            for (Facto f : base.factos()) {
                if ("vulneravel/2".equals(f.getFunctor())
                        && Objects.equals(f.arg(0), inst)
                        && Objects.equals(f.arg(1), vuln)) {
                    return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    // GRUPO 2: Tempo (com a tolerancia dos relogios)
    // =========================================================================

    /**
     * tolerancia_relogios: le janela(tolerancia_relogios, T) ou retorna 0.
     * Os relogios do IDS, dos sensores e dos registos de login nunca estao exatamente certos.
     */
    public static long toleranciaRelogios(BaseFactos base) {
        for (Facto f : base.factos()) {
            if ("janela/2".equals(f.getFunctor()) && "tolerancia_relogios".equals(f.arg(0))) {
                Object v = f.arg(1);
                if (v instanceof Number n) return n.longValue();
            }
        }
        return 0L;
    }

    /** Obtem o tempo de um evento (tempo/2), ou null se nao houver. */
    private static Long tempo(Object ev, BaseFactos base) {
        for (Facto f : base.factos()) {
            if ("tempo/2".equals(f.getFunctor()) && Objects.equals(f.arg(0), ev)) {
                Object t = f.arg(1);
                if (t instanceof Number n) return n.longValue();
            }
        }
        return null;
    }

    /**
     * depois/3: Ev2 aconteceu depois de Ev1 e dentro da janela (com a tolerancia dos relogios nos dois extremos).
     * T2 >= T1 - Tol e T2 - T1 <= Janela + Tol.
     */
    public static boolean depois(Object ev2, Object ev1, Object janela, BaseFactos base) {
        Long t2 = tempo(ev2, base);
        Long t1 = tempo(ev1, base);
        if (t1 == null || t2 == null) return false;
        long tol = toleranciaRelogios(base);
        long jan = janela instanceof Number n ? n.longValue() : 0L;
        return t2 >= t1 - tol && (t2 - t1) <= jan + tol;
    }

    /** anterior/2: Ev1 aconteceu estritamente antes de Ev2 (por isso sao eventos diferentes). */
    public static boolean anterior(Object ev1, Object ev2, BaseFactos base) {
        Long t1 = tempo(ev1, base);
        Long t2 = tempo(ev2, base);
        return t1 != null && t2 != null && t1 < t2;
    }

    /**
     * tardia/4: Ev2 aconteceu depois de Ev1, ja fora da janela curta mas dentro da longa.
     * D > JanelaCurta + Tol e D <= JanelaLonga + Tol.
     */
    public static boolean tardia(Object ev2, Object ev1, Object janelaCurta, Object janelaLonga, BaseFactos base) {
        Long t2 = tempo(ev2, base);
        Long t1 = tempo(ev1, base);
        if (t1 == null || t2 == null) return false;
        long tol = toleranciaRelogios(base);
        long jc = janelaCurta instanceof Number n ? n.longValue() : 0L;
        long jl = janelaLonga instanceof Number n ? n.longValue() : 0L;
        long d = t2 - t1;
        return d > jc + tol && d <= jl + tol;
    }

    /**
     * entre/3: Outro e um evento diferente de Ev1 que aconteceu depois de Ev1 e ate Ev2.
     * Outro != Ev1, T > T1, T <= T2.
     */
    public static boolean entre(Object outro, Object ev1, Object ev2, BaseFactos base) {
        if (Objects.equals(outro, ev1)) return false;
        Long t1 = tempo(ev1, base);
        Long t2 = tempo(ev2, base);
        Long t = tempo(outro, base);
        return t1 != null && t2 != null && t != null && t > t1 && t <= t2;
    }

    /**
     * sem_ataque_intermedio/3: entre o ataque e a evidencia nao houve outro alerta_ids contra o mesmo ativo.
     * Se houve, a evidencia e mais provavelmente desse outro ataque.
     */
    public static boolean semAtaqueIntermedio(Object evAtaque, Object evHost, Object ativo, BaseFactos base) {
        if (tempo(evAtaque, base) == null || tempo(evHost, base) == null) return false;
        for (Facto f : base.factos()) {
            if (!"alerta_ids/3".equals(f.getFunctor())) continue;
            if (!Objects.equals(f.arg(1), ativo)) continue;
            Object outro = f.arg(0);
            if (entre(outro, evAtaque, evHost, base)) return false;
        }
        return true;
    }

    // =========================================================================
    // GRUPO 3: Evidencia, estado dos incidentes, conclusoes revistas
    // =========================================================================

    /**
     * sem_condicao_em_falta/3: nenhuma das condicoes que o impacto exige esta registada como "nao"
     * na configuracao da instalacao. E o contrario do que a R18 procura.
     */
    public static boolean semCondicaoEmFalta(Object inst, Object vuln, Object impacto, BaseFactos base) {
        for (Facto f : base.factos()) {
            if (!"requer_condicao/3".equals(f.getFunctor())) continue;
            if (!Objects.equals(f.arg(0), vuln)) continue;
            if (!Objects.equals(f.arg(1), impacto)) continue;
            Object cond = f.arg(2);
            for (Facto cfg : base.factos()) {
                if ("configuracao/3".equals(cfg.getFunctor())
                        && Objects.equals(cfg.arg(0), inst)
                        && Objects.equals(cfg.arg(1), cond)
                        && "nao".equals(cfg.arg(2))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * evidencias_negadas/3: o ataque Ev tem pelo menos um impacto possivel no ativo (nao excluido),
     * cada impacto da vulnerabilidade tem pelo menos uma evidencia esperada, cada evidencia dos impactos
     * possiveis tem uma pergunta, e o analista respondeu "nao" a todas essas perguntas.
     */
    public static boolean evidenciasNegadas(Object ativo, Object ev, Object vuln, BaseFactos base) {
        // Verificar que ha pelo menos um impacto_possivel nao excluido
        boolean temPossivel = false;
        for (Facto f : base.factos()) {
            if (!"impacto_possivel/4".equals(f.getFunctor())) continue;
            if (!Objects.equals(f.arg(0), ev)) continue;
            if (!Objects.equals(f.arg(1), ativo)) continue;
            if (!Objects.equals(f.arg(2), vuln)) continue;
            Object imp = f.arg(3);
            if (!impactoExcluido(ev, ativo, vuln, imp, base)) {
                temPossivel = true;
                break;
            }
        }
        if (!temPossivel) return false;

        // Todos os vuln_impacto(Vuln, Imp) tem evidencia_impacto(Imp, _, _)
        for (Facto vi : base.factos()) {
            if (!"vuln_impacto/2".equals(vi.getFunctor())) continue;
            if (!Objects.equals(vi.arg(0), vuln)) continue;
            Object imp = vi.arg(1);
            boolean temEvidencia = false;
            for (Facto ei : base.factos()) {
                if ("evidencia_impacto/3".equals(ei.getFunctor()) && Objects.equals(ei.arg(0), imp)) {
                    temEvidencia = true;
                    break;
                }
            }
            if (!temEvidencia) return false;
        }

        // Para cada impacto_possivel nao excluido, a pergunta foi respondida com "nao"
        for (Facto ip : base.factos()) {
            if (!"impacto_possivel/4".equals(ip.getFunctor())) continue;
            if (!Objects.equals(ip.arg(0), ev)) continue;
            if (!Objects.equals(ip.arg(1), ativo)) continue;
            if (!Objects.equals(ip.arg(2), vuln)) continue;
            Object imp = ip.arg(3);
            if (impactoExcluido(ev, ativo, vuln, imp, base)) continue;
            for (Facto ei : base.factos()) {
                if (!"evidencia_impacto/3".equals(ei.getFunctor())) continue;
                if (!Objects.equals(ei.arg(0), imp)) continue;
                Object tipo = ei.arg(1);
                Object pergunta = null;
                for (Facto pe : base.factos()) {
                    if ("pergunta_evidencia/2".equals(pe.getFunctor()) && Objects.equals(pe.arg(0), tipo)) {
                        pergunta = pe.arg(1);
                        break;
                    }
                }
                if (pergunta == null) return false;
                boolean respondeuNao = false;
                for (Facto ra : base.factos()) {
                    if ("resposta_analista/4".equals(ra.getFunctor())
                            && Objects.equals(ra.arg(0), ativo)
                            && Objects.equals(ra.arg(1), ev)
                            && Objects.equals(ra.arg(2), pergunta)
                            && "nao".equals(ra.arg(3))) {
                        respondeuNao = true;
                        break;
                    }
                }
                if (!respondeuNao) return false;
            }
        }
        return true;
    }

    private static boolean impactoExcluido(Object ev, Object ativo, Object vuln, Object imp, BaseFactos base) {
        for (Facto ie : base.factos()) {
            if ("impacto_excluido/5".equals(ie.getFunctor())
                    && Objects.equals(ie.arg(0), ev)
                    && Objects.equals(ie.arg(1), ativo)
                    && Objects.equals(ie.arg(2), vuln)
                    && Objects.equals(ie.arg(3), imp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * em_aberto/1: o incidente nao foi superado nem resolvido.
     */
    public static boolean emAberto(Object inc, BaseFactos base) {
        for (Facto f : base.factos()) {
            if ("superado/2".equals(f.getFunctor()) && Objects.equals(f.arg(0), inc)) return false;
            if ("resolvido/2".equals(f.getFunctor()) && Objects.equals(f.arg(0), inc)) return false;
        }
        return true;
    }

    /**
     * todos_motivos_revistos/1: o incidente tem pelo menos um motivo, e todos os seus motivos
     * passaram a historicos (revisto(motivo(Inc, M), _)).
     */
    public static boolean todosMotivosRevistos(Object inc, BaseFactos base) {
        boolean temMotivo = false;
        for (Facto f : base.factos()) {
            if (!"motivo/2".equals(f.getFunctor())) continue;
            if (!Objects.equals(f.arg(0), inc)) continue;
            temMotivo = true;
            Object m = f.arg(1);
            Facto motivoFacto = Facto.de("motivo", inc, m);
            boolean revisto = false;
            for (Facto r : base.factos()) {
                if ("revisto/2".equals(r.getFunctor()) && Objects.equals(r.arg(0), motivoFacto)) {
                    revisto = true;
                    break;
                }
            }
            if (!revisto) return false;
        }
        return temMotivo;
    }
}
