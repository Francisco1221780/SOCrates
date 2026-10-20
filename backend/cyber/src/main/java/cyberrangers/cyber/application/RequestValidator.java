package cyberrangers.cyber.application;

import cyberrangers.cyber.domain.Facto;
import cyberrangers.cyber.domain.Passo;
import cyberrangers.cyber.domain.Pedido;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida a forma de um pedido antes de chegar a um motor (AC17): versaoKB igual a carregada, passos nao vazios e
 * factos com predicado e args. Os termos dentro de args sao validados pelos motores, com as mesmas mensagens nos dois.
 * Valida tambem os campos que as explicacoes juntam ao pedido (o facto, ou a pergunta).
 */
@Component
public class RequestValidator {

    /** A profundidade maxima do "porque nao?": a explicacao e recursiva e cresce depressa com os niveis. */
    public static final int PROFUNDIDADE_MAXIMA = 8;

    /**
     * Devolve todos os erros do pedido de uma vez, com a posicao (a contar de 1) do passo e do facto; vazia se o
     * pedido for valido.
     * @param pedido
     * @param versaoCarregada
     * @return
     */
    public List<String> validaPedido(Pedido pedido, String versaoCarregada) {
        if (pedido == null) {
            return List.of("o pedido esta vazio");
        }
        List<String> erros = new ArrayList<>();
        if (pedido.versaoKB() == null || pedido.versaoKB().isBlank()) {
            erros.add("falta a versaoKB");
        } else if (!pedido.versaoKB().equals(versaoCarregada)) {
            erros.add("versaoKB desconhecida: " + pedido.versaoKB() + " (carregada: " + versaoCarregada + ")");
        }
        List<Passo> passos = pedido.passos();
        if (passos == null || passos.isEmpty()) {
            erros.add("o pedido nao tem passos");
            return erros;
        }
        for (int i = 0; i < passos.size(); i++) {
            validaPasso(passos.get(i), "passo " + (i + 1), erros);
        }
        return erros;
    }

    /**
     * Devolve os erros do campo facto do "como?" e do "porque?"; vazia se for valido. O facto escolhe-se por numero
     * ou por predicado e args, nunca pelos dois: um pedido assim e quase sempre um erro de quem o fez.
     * @param corpo
     * @return
     */
    public List<String> validaFacto(JsonNode corpo) {
        JsonNode facto = corpo.get("facto");
        if (facto == null || facto.isNull()) {
            return List.of("falta o facto");
        }
        if (!facto.isObject()) {
            return List.of("o facto tem de ser um objeto JSON");
        }
        boolean porNumero = facto.has("numero");
        boolean porTermo = facto.has("predicado") || facto.has("args");
        if (porNumero == porTermo) {
            return List.of(porNumero ? "o facto tem de ter numero ou predicado e args, nao os dois"
                    : "o facto tem de ter numero ou predicado e args");
        }
        if (porNumero) {
            JsonNode numero = facto.get("numero");
            boolean valido = numero.isIntegralNumber() && numero.canConvertToInt() && numero.asInt() > 0;
            return valido ? List.of() : List.of("o numero do facto tem de ser um inteiro maior que 0");
        }
        List<String> erros = new ArrayList<>();
        JsonNode predicado = facto.get("predicado");
        if (predicado == null || !predicado.isString() || predicado.asString().isBlank()) {
            erros.add("falta o predicado do facto");
        }
        JsonNode args = facto.get("args");
        if (args == null || !args.isArray()) {
            erros.add("falta a lista args do facto");
        }
        return erros;
    }

    /**
     * Devolve os erros dos campos do "porque nao?"; vazia se forem validos. O facto e {predicado, args}, sem numero
     * (um facto que nao se concluiu nao tem numero), e os args podem ter variaveis ({"var": "Ev"}). A profundidade,
     * se vier, e um inteiro de 1 a PROFUNDIDADE_MAXIMA.
     * @param corpo
     * @return
     */
    public List<String> validaPorqueNao(JsonNode corpo) {
        List<String> erros = new ArrayList<>();
        JsonNode facto = corpo.get("facto");
        if (facto == null || facto.isNull()) {
            erros.add("falta o facto");
        } else if (!facto.isObject()) {
            erros.add("o facto tem de ser um objeto JSON");
        } else if (facto.has("numero")) {
            erros.add("o facto do porque nao escolhe-se por predicado e args (um facto que nao existe nao tem numero)");
        } else {
            JsonNode predicado = facto.get("predicado");
            if (predicado == null || !predicado.isString() || predicado.asString().isBlank()) {
                erros.add("falta o predicado do facto");
            }
            JsonNode args = facto.get("args");
            if (args == null || !args.isArray()) {
                erros.add("falta a lista args do facto");
            } else {
                for (JsonNode a : args) {
                    if (!termoValido(a)) {
                        erros.add("termo invalido no facto: " + a);
                        break;
                    }
                }
            }
        }
        JsonNode profundidade = corpo.get("profundidade");
        if (profundidade != null && !profundidade.isNull() && !(profundidade.isIntegralNumber()
                && profundidade.canConvertToInt() && profundidade.asInt() >= 1
                && profundidade.asInt() <= PROFUNDIDADE_MAXIMA)) {
            erros.add("a profundidade tem de ser um inteiro de 1 a " + PROFUNDIDADE_MAXIMA);
        }
        return erros;
    }

    /**
     * Se o termo e um atomo, um numero, uma lista de termos, uma variavel {"var": nome} ou um termo composto
     * {"f": nome, "args": [termos]}.
     * @param t
     * @return
     */
    private static boolean termoValido(JsonNode t) {
        if (t.isString() || t.isNumber()) {
            return true;
        }
        if (t.isArray()) {
            for (JsonNode x : t) {
                if (!termoValido(x)) {
                    return false;
                }
            }
            return true;
        }
        if (!t.isObject()) {
            return false;
        }
        if (t.size() == 1 && t.has("var")) {
            return t.get("var").isString() && !t.get("var").asString().isBlank();
        }
        JsonNode f = t.get("f");
        JsonNode args = t.get("args");
        return t.size() == 2 && f != null && f.isString() && !f.asString().isBlank() && args != null
                && args.isArray() && termoValido(args);
    }

    /**
     * Devolve os erros dos campos ativo, evento e pergunta do "porque?" de uma pergunta; vazia se forem validos.
     * @param corpo
     * @return
     */
    public List<String> validaPergunta(JsonNode corpo) {
        List<String> erros = new ArrayList<>();
        for (String campo : List.of("ativo", "evento", "pergunta")) {
            JsonNode valor = corpo.get(campo);
            if (valor == null || valor.isNull()) {
                erros.add("falta o campo " + campo);
            } else if (!valor.isString() || valor.asString().isBlank()) {
                erros.add("o campo " + campo + " tem de ser texto nao vazio");
            }
        }
        return erros;
    }

    /**
     * Acrescenta aos erros o que falta no passo e nos seus factos.
     * @param passo
     * @param onde
     * @param erros
     */
    private static void validaPasso(Passo passo, String onde, List<String> erros) {
        if (passo == null) {
            erros.add(onde + ": o passo esta vazio");
            return;
        }
        List<Facto> factos = passo.factos();
        if (factos == null) {
            erros.add(onde + ": falta a lista de factos");
            return;
        }
        for (int j = 0; j < factos.size(); j++) {
            Facto facto = factos.get(j);
            String aqui = onde + ", facto " + (j + 1);
            if (facto == null) {
                erros.add(aqui + ": o facto esta vazio");
                continue;
            }
            if (facto.predicado() == null || facto.predicado().isBlank()) {
                erros.add(aqui + ": falta o predicado");
            }
            if (facto.args() == null) {
                erros.add(aqui + ": falta args");
            }
        }
    }
}
