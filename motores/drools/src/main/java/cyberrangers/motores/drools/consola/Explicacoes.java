package cyberrangers.motores.drools.consola;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.util.Iterator;

/**
 * As tres explicacoes da consola, escritas para quem le: "como?" (a arvore de factos de uma conclusao), "porque?" (a
 * mesma conclusao com cada regra escrita por inteiro) e "porque nao?" (as regras que a podiam concluir, com cada
 * condicao marcada). Os factos escrevem-se como {@link TermoTexto#legivel}, sem sintaxe Prolog.
 *
 * <p>O "porque nao?" recebe as linhas do {@link PorqueNao}; o {@link #linhaPorqueNaoProlog} escreve essas mesmas
 * linhas como o whynot/1 do Prolog (e o que o {@code PorqueNaoTest} compara com o Prolog).
 */
final class Explicacoes {

    private static final String PASSO = "    ";

    private final PrintStream out;

    Explicacoes(PrintStream out) {
        this.out = out;
    }

    // --- como? (opcao 9)

    /** A arvore de uma conclusao (o {@code arvore} do {@code MotorDrools.como}), no estilo do explica_facto/2 do Prolog. */
    void como(JsonNode no) {
        como(no, 0);
    }

    private void como(JsonNode n, int nivel) {
        String tab = PASSO.repeat(nivel);
        int numero = n.get("numero").asInt();
        if (n.get("jaExplicado").asBoolean()) {
            out.printf("%sfacto %d: %s   (ja explicado acima)%n", tab, numero, TermoTexto.legivel(n));
            return;
        }
        if (n.get("regra").isNull()) {
            out.printf("%sfacto %d: %s   (dado)%n", tab, numero, TermoTexto.legivel(n));
            return;
        }
        out.printf("%sfacto %d: %s%n%s   concluido pela regra %d, porque:%n", tab, numero, TermoTexto.legivel(n), tab,
                n.get("regra").asInt());
        for (JsonNode p : n.get("premissas")) {
            if (p.get("tipo").asText().equals("facto")) {
                como(p.get("no"), nivel + 1);
            } else {
                out.println(tab + PASSO + premissaSemFacto(p));
            }
        }
    }

    // --- porque? (so o "porque" de uma pergunta, opcao 5)

    /** A conclusao, a regra completa que a produziu e os factos que a satisfizeram (o {@code arvore} do {@code MotorDrools.porque}). */
    void porque(JsonNode no) {
        porque(no, 0);
    }

    private void porque(JsonNode n, int nivel) {
        String tab = PASSO.repeat(nivel);
        int numero = n.get("numero").asInt();
        if (n.get("jaExplicado").asBoolean()) {
            out.printf("%sFacto %d: %s   (ja explicado acima)%n", tab, numero, TermoTexto.legivel(n));
            return;
        }
        if (n.get("regra").isNull()) {
            out.printf("%sFacto %d: %s   (dado de entrada)%n", tab, numero, TermoTexto.legivel(n));
            return;
        }
        out.printf("%sFacto %d: %s%n", tab, numero, TermoTexto.legivel(n));
        out.printf("%sConcluido pela regra %d:%n", tab, n.get("regra").asInt());
        regra(n.get("regraCompleta"), tab + "  ");
        out.printf("%sCom os factos:%n", tab);
        for (JsonNode p : n.get("premissas")) {
            if (p.get("tipo").asText().equals("facto")) {
                porque(p.get("no"), nivel + 1);
            } else {
                out.println(tab + PASSO + premissaSemFacto(p));
            }
        }
    }

    /** Uma premissa que nao e um facto: a negacao ou a condicao calculada. */
    private static String premissaSemFacto(JsonNode p) {
        if (p.get("tipo").asText().equals("nao")) {
            return "nao se verifica " + TermoTexto.legivel(p.get("termo"));
        }
        return "a condicao " + TermoTexto.legivel(p.get("termo")) + " verifica-se (calculada)";
    }

    /** O "porque" de uma pergunta (a resposta do {@code MotorDrools.porquePergunta}): a regra que a gerou e os factos que a levaram. */
    void porquePergunta(JsonNode resposta) {
        out.println();
        JsonNode gerada = resposta.get("geradaPor");
        if (gerada.isNull()) {
            out.println("Esta pergunta foi introduzida a mao.");
        } else {
            out.printf("Pergunto porque a regra %d pediu esta verificacao em %s, pelo seguinte:%n",
                    gerada.get("regra").asInt(), resposta.get("pergunta").get("ativo").asText());
            porque(gerada);
            JsonNode regras = resposta.get("regrasQueUsamAResposta");
            if (!regras.isEmpty()) {
                out.println("A resposta vai ser usada pelas regras " + regras.toString().replace(" ", "") + ".");
            }
        }
        out.println();
    }

    // --- regra escrita por inteiro

    /** SE / E ... ENTAO, uma condicao por linha, com os nomes das variaveis da regra. */
    private void regra(JsonNode regra, String tab) {
        boolean primeira = true;
        for (JsonNode c : regra.get("se")) {
            out.println(tab + (primeira ? "SE    " : "E     ") + condicao(c));
            primeira = false;
        }
        primeira = true;
        for (JsonNode t : regra.get("entao")) {
            out.println(tab + (primeira ? "ENTAO " : "      ") + TermoTexto.legivel(t));
            primeira = false;
        }
    }

    private static String condicao(JsonNode c) {
        if (c.has("nao")) {
            return "nao " + TermoTexto.legivel(c.get("nao"));
        }
        if (c.has("avalia")) {
            return "calcular " + TermoTexto.legivel(c.get("avalia"));
        }
        return TermoTexto.legivel(c);
    }

    // --- porque nao? (opcao 10)

    /**
     * As linhas do {@link PorqueNao}, no formato novo: para cada regra que podia concluir o facto, a regra inteira com
     * cada condicao marcada ([ok] ou [FALTA]), e a seguir, mais fundo, o que falta e porque.
     */
    void porqueNao(JsonNode facto, JsonNode linhas) {
        out.println("Porque nao existe " + TermoTexto.legivel(facto) + "?");
        Iterator<JsonNode> it = linhas.iterator();
        while (it.hasNext()) {
            JsonNode l = it.next();
            String tab = PASSO.repeat(Math.max(0, l.get("nivel").asInt() - 1));
            String t = l.has("termo") ? TermoTexto.legivel(l.get("termo")) : "";
            switch (l.get("tipo").asText()) {
                case "verdadeiro" -> out.println(tab + "Mas esse facto existe (facto n. " + l.get("numero").asInt()
                        + "): " + t + ". Nao ha nada a explicar.");
                case "regra" -> {
                    out.println();
                    out.println(tab + "A regra " + l.get("regra").asInt() + " podia conclui-lo, mas nao se verifica:");
                    regraComEstado(l, tab + "  ");
                }
                case "premissa_falsa" -> out.println(tab + "- Falta: " + t);
                case "calculada_premissa_falsa" -> out.println(tab + "- Falta: a condicao calculada " + t
                        + " e falsa");
                case "condicao_falsa" -> out.println(tab + "- Falta: a condicao " + t + " nao se verifica");
                case "condicao_nao_falsa" -> out.println(tab + "- Impede: a condicao " + t
                        + " verifica-se, e a regra exige que nao");
                case "premissa_nao_falsa" -> {
                    JsonNode seguinte = it.hasNext() ? it.next() : null;
                    String numero = seguinte != null && seguinte.has("numero") && !seguinte.get("numero").isNull()
                            ? " (facto n. " + seguinte.get("numero").asInt() + ")" : "";
                    String existente = seguinte != null && seguinte.has("encontrado")
                            ? TermoTexto.legivel(seguinte.get("encontrado")) : t;
                    out.println(tab + "- Impede: o facto " + existente + " existe" + numero
                            + ", e a regra exige que nao exista");
                }
                case "facto_verdadeiro" -> out.println(tab + "O facto " + t + " existe");
                case "dado_de_entrada" -> out.println(tab + "E um dado de entrada: nao esta na base e nenhuma regra o"
                        + " conclui, por isso tem de ser fornecido");
                case "ja_explicado" -> out.println(tab + "(" + t + ": ja explicado acima)");
                case "profundidade" -> out.println(tab + "(ha mais causas: aumente a profundidade na opcao 12)");
                case "falta_correr_motor" -> out.println(tab + "As condicoes de uma regra que conclui " + t
                        + " verificam-se: falta correr o motor (opcao 2)");
                case "calculada_falsa" -> out.println(tab + "A condicao calculada " + t + " e falsa");
                case "nao_verdadeiro" -> out.println(tab + "O facto " + t + " e verdadeiro");
                default -> out.println(tab + l);
            }
        }
    }

    /** A regra de uma linha "regra" com o estado de cada condicao e o facto que a satisfez. */
    private void regraComEstado(JsonNode l, String tab) {
        boolean primeira = true;
        for (JsonNode c : l.get("se")) {
            String marca = c.get("verificada").asBoolean() ? "[ok]    " : "[FALTA] ";
            String termo = TermoTexto.legivel(c.get("termo"));
            String texto = switch (c.get("tipo").asText()) {
                case "nao" -> "nao " + termo;
                case "avalia" -> "calcular " + termo;
                default -> termo;
            };
            String numero = c.has("numero") ? "   (facto n. " + c.get("numero").asInt() + ")" : "";
            out.println(tab + (primeira ? "SE    " : "E     ") + marca + texto + numero);
            primeira = false;
        }
        primeira = true;
        for (JsonNode t : l.get("entao")) {
            out.println(tab + (primeira ? "ENTAO " : "      ") + TermoTexto.legivel(t));
            primeira = false;
        }
    }

    // --- porque nao? no formato do whynot/1 do Prolog (so para comparar com ele)

    /** O texto de uma linha do "porque nao?", como o whynot/1 do Prolog a escreve. */
    static String linhaPorqueNaoProlog(JsonNode l) {
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
}
