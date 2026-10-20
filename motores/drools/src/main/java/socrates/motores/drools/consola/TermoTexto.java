package socrates.motores.drools.consola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Termos em sintaxe Prolog na consola, nos dois sentidos: o que se escreve ({@code alerta_ids(ev_1,web_srv_02,sig_x)},
 * {@code versao(srv_apache,'2.4.49-ubuntu1')}, listas) passa para o JSON do contrato (tabela da issue #6), e o JSON da
 * resposta passa para texto, como o legivel/2 do soc_motor.pl o escreve.
 */
final class TermoTexto {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Pattern ATOMO_SIMPLES = Pattern.compile("[a-z][A-Za-z0-9_]*");
    private static final Pattern VARIAVEL = Pattern.compile("[A-Z][A-Za-z0-9_]*");

    private TermoTexto() {
    }

    /** Um erro de sintaxe, com a mensagem que a consola mostra. */
    static final class ErroSintaxe extends Exception {
        ErroSintaxe(String mensagem) {
            super(mensagem);
        }
    }

    /**
     * Um facto ou uma lista de factos ({@code [f1, f2]}) em sintaxe Prolog, cada um como {@code {"predicado","args"}}.
     * Um ponto final no fim e opcional (como no read/1 do Prolog).
     */
    static List<ObjectNode> factos(String texto) throws ErroSintaxe {
        return factos(texto, false);
    }

    /**
     * O mesmo, e com {@code variaveis} as variaveis ({@code Ev}, {@code _}) passam a {@code {"var": "Ev"}} em vez de
     * darem erro (o facto do "porque nao?" pode ter variaveis, como no whynot/1).
     */
    static List<ObjectNode> factos(String texto, boolean variaveis) throws ErroSintaxe {
        JsonNode t = new Leitor(semPontoFinal(texto), variaveis).leTudo();
        List<ObjectNode> r = new ArrayList<>();
        if (t.isArray()) {
            if (t.isEmpty()) {
                throw new ErroSintaxe("a lista nao tem nenhum facto");
            }
            for (JsonNode f : t) {
                r.add(facto(f));
            }
        } else {
            r.add(facto(t));
        }
        return r;
    }

    private static ObjectNode facto(JsonNode termo) throws ErroSintaxe {
        ObjectNode f = JSON.objectNode();
        if (termo.isObject()) {
            f.set("predicado", termo.get("f"));
            f.set("args", termo.get("args"));
        } else if (termo.isTextual()) {
            f.set("predicado", termo);
            f.putArray("args");
        } else {
            throw new ErroSintaxe("um facto e um nome ou um termo nome(...), e nao " + texto(termo));
        }
        return f;
    }

    private static String semPontoFinal(String texto) {
        String t = texto.strip();
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    /**
     * Os factos de um ficheiro de cenario no formato dos do Prolog (base_conhecimento/cenarios/): uma linha por facto,
     * {@code :- novo_facto(F).}, {@code novo_facto(F).} ou {@code F.}; as linhas com {@code %} e as vazias ignoram-se.
     */
    static List<ObjectNode> factosDeCenario(List<String> linhas) throws ErroSintaxe {
        List<ObjectNode> r = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            String l = linhas.get(i).strip();
            if (l.isEmpty() || l.startsWith("%")) {
                continue;
            }
            if (l.startsWith(":-")) {
                l = l.substring(2).strip();
            }
            l = semPontoFinal(l);
            if (l.startsWith("novo_facto(") && l.endsWith(")")) {
                l = l.substring("novo_facto(".length(), l.length() - 1);
            }
            try {
                r.addAll(factos(l));
            } catch (ErroSintaxe e) {
                throw new ErroSintaxe("linha " + (i + 1) + ": " + e.getMessage());
            }
        }
        return r;
    }

    /** Um termo como o write/1 (o ~w do format): sem plicas. */
    static String escrito(JsonNode t) {
        return texto(t, false, ",");
    }

    /** Um termo JSON do contrato em texto Prolog (atomos com plicas quando e preciso, ex.: '2.4.49-ubuntu1'). */
    static String texto(JsonNode t) {
        return texto(t, true, ",");
    }

    /**
     * Um termo para ler na consola, sem sintaxe Prolog: {@code alerta_ids(ev_1, web_srv_02, sig_x)}, sem plicas e com
     * um espaco depois de cada virgula. E o que se escreve na opcao 4.
     */
    static String legivel(JsonNode t) {
        return texto(t, false, ", ");
    }

    private static String texto(JsonNode t, boolean plicas, String sep) {
        if (t == null || t.isNull()) {
            return "null";
        }
        if (t.isArray()) {
            List<String> partes = new ArrayList<>();
            t.forEach(a -> partes.add(texto(a, plicas, sep)));
            return "[" + String.join(sep, partes) + "]";
        }
        if (t.isObject() && t.has("var")) {
            return t.get("var").asText();
        }
        if (t.isObject() && t.has("predicado")) {
            return composto(t.get("predicado").asText(), t.get("args"), plicas, sep);
        }
        if (t.isObject()) {
            return composto(t.get("f").asText(), t.get("args"), plicas, sep);
        }
        if (t.isNumber()) {
            return t.asText();
        }
        return atomo(t.asText(), plicas);
    }

    private static String composto(String nome, JsonNode args, boolean plicas, String sep) {
        if (args == null || args.isEmpty()) {
            return atomo(nome, plicas);
        }
        List<String> partes = new ArrayList<>();
        args.forEach(a -> partes.add(texto(a, plicas, sep)));
        return atomo(nome, plicas) + "(" + String.join(sep, partes) + ")";
    }

    /** As variaveis das premissas ("A", "B") vem como texto e escrevem-se sem plicas, como no numbervars. */
    private static String atomo(String a, boolean plicas) {
        if (!plicas || ATOMO_SIMPLES.matcher(a).matches() || VARIAVEL.matcher(a).matches() || a.equals("[]")) {
            return a;
        }
        if (a.matches("[<>=\\\\+\\-*/:]+")) {
            return a;
        }
        return "'" + a.replace("'", "''") + "'";
    }

    /** Parser descendente de termos: atomo, inteiro, 'atomo entre plicas', nome(args), [lista]. */
    private static final class Leitor {
        private final String s;
        private final boolean variaveis;
        private int i;

        Leitor(String s, boolean variaveis) {
            this.s = s;
            this.variaveis = variaveis;
        }

        JsonNode leTudo() throws ErroSintaxe {
            JsonNode t = termo();
            espacos();
            if (i < s.length()) {
                throw erro("texto a mais depois do termo");
            }
            return t;
        }

        private JsonNode termo() throws ErroSintaxe {
            espacos();
            if (i >= s.length()) {
                throw erro("falta um termo");
            }
            char c = s.charAt(i);
            if (c == '[') {
                return lista();
            }
            if (c == '\'') {
                return comArgs(plicas());
            }
            if (Character.isUpperCase(c) || c == '_') {
                String v = nome();
                if (!variaveis) {
                    throw erro("um facto nao pode ter variaveis (" + v + ")");
                }
                return JSON.objectNode().put("var", v.startsWith("_") ? "_" : v);
            }
            if (c == ',' || c == ')' || c == ']' || c == '(') {
                throw erro("falta um termo");
            }
            return palavra();
        }

        private JsonNode comArgs(String nome) throws ErroSintaxe {
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                ObjectNode o = JSON.objectNode();
                o.put("f", nome);
                ArrayNode args = o.putArray("args");
                args.add(termo());
                espacos();
                while (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    args.add(termo());
                    espacos();
                }
                espera(')');
                return o;
            }
            return JSON.textNode(nome);
        }

        private JsonNode lista() throws ErroSintaxe {
            i++;
            ArrayNode a = JSON.arrayNode();
            espacos();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return a;
            }
            a.add(termo());
            espacos();
            while (i < s.length() && s.charAt(i) == ',') {
                i++;
                a.add(termo());
                espacos();
            }
            espera(']');
            return a;
        }

        private String plicas() throws ErroSintaxe {
            StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                if (i >= s.length()) {
                    throw erro("falta fechar a plica");
                }
                char c = s.charAt(i++);
                if (c == '\'') {
                    if (i < s.length() && s.charAt(i) == '\'') {
                        b.append('\'');
                        i++;
                    } else {
                        return b.toString();
                    }
                } else {
                    b.append(c);
                }
            }
        }

        /**
         * Um argumento sem plicas: tudo ate a virgula, ao parentese ou ao espaco. Um nome seguido de "(" e um termo
         * composto; so digitos e um inteiro; o resto e um atomo (ex.: 2.4.49-ubuntu1).
         */
        private JsonNode palavra() throws ErroSintaxe {
            int inicio = i;
            while (i < s.length() && !Character.isWhitespace(s.charAt(i)) && ",()[]'".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String w = s.substring(inicio, i);
            if (i < s.length() && s.charAt(i) == '(') {
                if (!ATOMO_SIMPLES.matcher(w).matches()) {
                    throw erro("o nome " + w + " nao pode levar argumentos");
                }
                return comArgs(w);
            }
            if (w.matches("-?\\d+\\.\\d+")) {
                i = inicio;
                throw erro("so se aceitam numeros inteiros");
            }
            if (w.matches("-?\\d{1,18}")) {
                long n = Long.parseLong(w);
                // int quando cabe, como o Jackson le o JSON: assim um termo escrito aqui e igual ao da resposta
                return n == (int) n ? JSON.numberNode((int) n) : JSON.numberNode(n);
            }
            return JSON.textNode(w);
        }

        private String nome() {
            int inicio = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
                i++;
            }
            return s.substring(inicio, i);
        }

        private void espera(char c) throws ErroSintaxe {
            espacos();
            if (i >= s.length() || s.charAt(i) != c) {
                throw erro("falta '" + c + "'");
            }
            i++;
        }

        private void espacos() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private ErroSintaxe erro(String m) {
            return new ErroSintaxe("erro de sintaxe na posicao " + (i + 1) + ": " + m);
        }
    }
}
