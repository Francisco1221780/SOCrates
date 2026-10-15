package cyberrangers.motores.drools.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cyberrangers.motores.drools.modelo.Facto;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Os dados fixos da base (soc_factos.txt), lidos uma vez do classpath. O ficheiro e gerado por
 * motores/prolog/ferramentas/gera_json.pl e nao se edita a mao: {"versaoKB", "factos"}, com os factos
 * {"predicado","args"} pela ordem do soc_factos.txt, que da a numeracao (1..N).
 */
public final class FactosIniciais {

    public static final String RECURSO = "factos_iniciais.json";

    private final List<Facto> factos;

    private FactosIniciais(List<Facto> factos) {
        this.factos = factos;
    }

    /**
     * @param versaoKB a versao da base que o motor aceita; o ficheiro tem de ser dessa versao
     * @throws IllegalStateException se o ficheiro faltar, for invalido ou de outra versao, com a razao
     */
    public static FactosIniciais carrega(String recurso, String versaoKB, ObjectMapper json) {
        try (InputStream in = FactosIniciais.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("nao existe " + recurso + " no classpath (gera-se com gera_json.pl)");
            }
            JsonNode raiz = json.readTree(in);
            JsonNode lista = raiz == null ? null : raiz.get("factos");
            if (lista == null || !lista.isArray()) {
                throw new IllegalStateException(recurso + " tem de ser {\"versaoKB\", \"factos\": [...]}");
            }
            String versao = raiz.path("versaoKB").asText("");
            if (!versaoKB.equals(versao)) {
                throw new IllegalStateException(recurso + " e da versaoKB \"" + versao + "\", o motor e da " + versaoKB);
            }
            List<Facto> factos = new ArrayList<>(lista.size());
            for (JsonNode f : lista) {
                factos.add(TermosJson.facto(f, recurso));
            }
            return new FactosIniciais(List.copyOf(factos));
        } catch (IOException | PedidoInvalido e) {
            throw new IllegalStateException(recurso + " invalido: " + e.getMessage(), e);
        }
    }

    public int quantos() {
        return factos.size();
    }

    /** Copias novas, para a numeracao de uma sessao nao passar para a seguinte. */
    public List<Facto> copias() {
        List<Facto> r = new ArrayList<>(factos.size());
        for (Facto f : factos) {
            r.add(new Facto(f.getPredicado(), f.getArgs()));
        }
        return r;
    }
}
