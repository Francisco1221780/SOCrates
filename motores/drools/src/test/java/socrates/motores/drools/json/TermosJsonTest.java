package socrates.motores.drools.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import socrates.motores.drools.modelo.Facto;
import socrates.motores.drools.modelo.Termo;
import socrates.motores.drools.modelo.Variavel;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermosJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void factoComTermoCompostoVaiEVoltaIgual() throws Exception {
        JsonNode entrada = json.readTree("""
                {"predicado":"incidente","args":[{"f":"inc","args":["versao_por_confirmar","ev_e9"]},
                 "versao_por_confirmar","web_srv_11",["web_srv_11"]]}""");
        Facto f = TermosJson.facto(entrada, "p1");
        assertEquals(Termo.de("inc", "versao_por_confirmar", "ev_e9"), f.arg(0));
        assertEquals(entrada.get("args"), TermosJson.json(f.getArgs()));
    }

    @Test
    void termoCompostoNaoSeConfundeComAtomo() throws Exception {
        Facto composto = TermosJson.facto(json.readTree(
                "{\"predicado\":\"versao\",\"args\":[\"i\",{\"f\":\"v\",\"args\":[2,4,49]}]}"), "p1");
        Facto atomo = TermosJson.facto(json.readTree(
                "{\"predicado\":\"versao\",\"args\":[\"i\",\"2.4.49-ubuntu1\"]}"), "p1");
        assertNotEquals(composto, atomo);
        assertEquals(Termo.de("v", 2, 4, 49), composto.arg(1));
    }

    @Test
    void inteiroDaOMesmoFactoSejaIntegerOuLong() throws Exception {
        Facto doJson = TermosJson.facto(json.readTree("{\"predicado\":\"tempo\",\"args\":[\"ev_e1\",3000]}"), "p1");
        Facto doJava = Facto.de("tempo", "ev_e1", 3000);
        assertEquals(doJson, doJava);
        assertEquals(doJson.hashCode(), doJava.hashCode());
        assertEquals(Facto.de("l", List.of(1, 2)), Facto.de("l", List.of(1L, 2L)));
    }

    @Test
    void objetoQueNaoETermoDaErro() {
        PedidoInvalido e = assertThrows(PedidoInvalido.class, () -> TermosJson.termo(
                json.readTree("{\"f\":\"v\",\"args\":[],\"x\":1}"), "cenario_01"));
        assertEquals("termo invalido no passo cenario_01: um objeto tem de ser {\"f\": nome, \"args\": [...]}",
                e.getMessage());
    }

    @Test
    void variaveisLivresTemNomesComoONumbervars() {
        TermosJson.NomesVariaveis nomes = new TermosJson.NomesVariaveis();
        Variavel a = Variavel.nova();
        Variavel b = Variavel.nova();
        assertEquals("[\"A\",\"B\",\"A\"]", TermosJson.json(List.of(a, b, a), nomes).toString());
        for (int i = 2; i < 26; i++) {
            nomes.nome(Variavel.nova());
        }
        assertEquals("A1", nomes.nome(Variavel.nova()));
    }
}
