package socrates.motores.drools.explicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * O {@link Explicacao} sobre respostas escritas a mao: o motor e a versaoKB vem da resposta, um erro passa tal como
 * veio, e uma resposta de outra versao ou que nao e do infere da erro.
 */
class ExplicacaoTest {

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Uma resposta do infere com um dado de entrada (1) e uma conclusao da regra 7 (2), na versaoKB dada.
     * @param motor
     * @param versao
     * @return
     */
    private static String resposta(String motor, String versao) {
        return "{\"estado\":\"aguarda_resposta\",\"motor\":\"" + motor + "\",\"versaoKB\":\"" + versao + "\","
                + "\"perguntas\":[{\"ativo\":\"a1\",\"evento\":\"e1\",\"pergunta\":\"shell_servico\",\"facto\":1}],"
                + "\"factos\":["
                + "{\"numero\":1,\"predicado\":\"x\",\"args\":[\"a\"],\"regra\":null,\"premissas\":[]},"
                + "{\"numero\":2,\"predicado\":\"y\",\"args\":[\"a\"],\"regra\":7,\"premissas\":[1]}],"
                + "\"erro\":null}";
    }

    /**
     * Verifica que a explicacao tem o motor e a versaoKB da resposta explicada, e nao os do Drools.
     * @throws Exception
     */
    @Test
    void oMotorEAVersaoVemDaResposta() throws Exception {
        JsonNode r = json.readTree(Explicacao.como(resposta("prolog", Explicacao.versaoRegras()), "{\"numero\":2}", false));
        assertEquals("concluido", r.get("estado").asText(), r.toString());
        assertEquals("prolog", r.get("motor").asText());
        assertEquals(Explicacao.versaoRegras(), r.get("versaoKB").asText());
        assertEquals(1, r.get("arvore").get("premissas").get(0).get("no").get("numero").asInt());
    }

    /**
     * Verifica que o porque? de uma pergunta introduzida a mao (o facto e um dado de entrada) tem geradaPor null.
     * @throws Exception
     */
    @Test
    void perguntaIntroduzidaAMaoNaoTemGeradaPor() throws Exception {
        JsonNode r = json.readTree(Explicacao.porquePergunta(resposta("prolog", Explicacao.versaoRegras()), "a1", "e1",
                "shell_servico"));
        assertEquals("concluido", r.get("estado").asText(), r.toString());
        assertTrue(r.get("geradaPor").isNull());
        assertEquals(json.readTree("[32,33,34,50,51]"), r.get("regrasQueUsamAResposta"));
    }

    /**
     * Verifica que uma resposta de erro do motor sai tal como veio.
     * @throws Exception
     */
    @Test
    void umErroDoMotorSaiTalComoVeio() throws Exception {
        String erro = "{\"estado\":\"erro\",\"motor\":\"prolog\",\"versaoKB\":null,\"erro\":\"timeout\"}";
        assertEquals(json.readTree(erro), json.readTree(Explicacao.como(erro, "{\"numero\":1}", false)));
        assertEquals(json.readTree(erro), json.readTree(Explicacao.porquePergunta(erro, "a", "e", "p")));
    }

    /**
     * Verifica que uma resposta de outra versaoKB que a das regras da erro, com as duas versoes na mensagem.
     * @throws Exception
     */
    @Test
    void outraVersaoDaErro() throws Exception {
        JsonNode r = json.readTree(Explicacao.como(resposta("prolog", "5"), "{\"numero\":2}", true));
        assertEquals("erro", r.get("estado").asText());
        assertEquals("prolog", r.get("motor").asText());
        assertEquals("a resposta e da versaoKB 5 e as regras das explicacoes da " + Explicacao.versaoRegras(),
                r.get("erro").asText());
    }

    /**
     * Verifica que o que nao e uma resposta do infere (nao e JSON, nao e um objeto, nao tem factos) da erro.
     * @throws Exception
     */
    @Test
    void oQueNaoEUmaRespostaDoInfereDaErro() throws Exception {
        for (String r : new String[] {"isto nao e json", "[1]", "{\"estado\":\"concluido\",\"versaoKB\":\"6\"}"}) {
            assertEquals("erro", json.readTree(Explicacao.como(r, "{\"numero\":1}", false)).get("estado").asText(), r);
        }
    }

    /**
     * Verifica que um facto ou uma pergunta que nao estao na resposta dao os erros que a API classifica como 422.
     * @throws Exception
     */
    @Test
    void factoEPerguntaInexistentes() throws Exception {
        String resposta = resposta("drools", Explicacao.versaoRegras());
        assertTrue(json.readTree(Explicacao.como(resposta, "{\"numero\":9}", false)).get("erro").asText()
                .startsWith("esse facto nao esta"));
        assertTrue(json.readTree(Explicacao.porquePergunta(resposta, "a1", "e1", "outra")).get("erro").asText()
                .startsWith("essa pergunta nao esta"));
    }
}
