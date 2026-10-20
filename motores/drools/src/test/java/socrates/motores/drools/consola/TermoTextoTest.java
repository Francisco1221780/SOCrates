package socrates.motores.drools.consola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermoTextoTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void factoComTermoCompostoPassaAoJsonDoContrato() throws Exception {
        List<ObjectNode> f = TermoTexto.factos("versao(srv_apache, v(2,4,49)).");
        assertEquals(json.readTree("{\"predicado\":\"versao\",\"args\":[\"srv_apache\",{\"f\":\"v\",\"args\":[2,4,49]}]}"),
                f.get(0));
        assertEquals("versao(srv_apache,v(2,4,49))", TermoTexto.texto(f.get(0)));
    }

    @Test
    void atomoEntrePlicasNaoSeConfundeComUmaVersao() throws Exception {
        ObjectNode f = TermoTexto.factos("versao(srv_apache,'2.4.49-ubuntu1')").get(0);
        assertEquals("2.4.49-ubuntu1", f.get("args").get(1).asText());
        assertEquals("versao(srv_apache,'2.4.49-ubuntu1')", TermoTexto.texto(f));
        assertEquals("'it''s'", TermoTexto.texto(TermoTexto.factos("x('it''s')").get(0).get("args").get(0)));
    }

    @Test
    void listaDeFactosEListasNosArgumentos() throws Exception {
        List<ObjectNode> f = TermoTexto.factos("[alerta_ids(ev_1,web_srv_02,sig_x), tempo(ev_1,900), "
                + "incidente(inc(t,ev_1),t,web_srv_02,[web_srv_02])]");
        assertEquals(3, f.size());
        assertEquals(900, f.get(1).get("args").get(1).asInt());
        assertEquals("incidente(inc(t,ev_1),t,web_srv_02,[web_srv_02])", TermoTexto.texto(f.get(2)));
    }

    @Test
    void argumentosSemPlicasNemPonto() throws Exception {
        ObjectNode f = TermoTexto.factos("versao(srv_apache, 2.4.49-ubuntu1)").get(0);
        assertEquals("2.4.49-ubuntu1", f.get("args").get(1).asText());
        assertEquals("versao(srv_apache, 2.4.49-ubuntu1)", TermoTexto.legivel(f));
        ObjectNode t = TermoTexto.factos("tempo(ev_1,   900)").get(0);
        assertEquals(900, t.get("args").get(1).asInt());
        assertEquals(TermoTexto.factos("tempo(ev_1, 900)"), TermoTexto.factos("tempo(ev_1,900)."));
        assertEquals("incidente(inc(t, ev_1), t, web_srv_02, [web_srv_02])", TermoTexto.legivel(
                TermoTexto.factos("incidente(inc(t,ev_1),t,web_srv_02,[web_srv_02])").get(0)));
        assertEquals("nome", TermoTexto.legivel(TermoTexto.factos("nome").get(0)));
    }

    @Test
    void errosDeSintaxeTemMensagem() {
        assertTrue(assertThrows(TermoTexto.ErroSintaxe.class, () -> TermoTexto.factos("tempo(ev_1,)"))
                .getMessage().contains("falta um termo"));
        assertTrue(assertThrows(TermoTexto.ErroSintaxe.class, () -> TermoTexto.factos("2.4(x)"))
                .getMessage().contains("nao pode levar argumentos"));
        assertTrue(assertThrows(TermoTexto.ErroSintaxe.class, () -> TermoTexto.factos("tempo(ev_1,900"))
                .getMessage().contains("falta ')'"));
        assertTrue(assertThrows(TermoTexto.ErroSintaxe.class, () -> TermoTexto.factos("versao(Inst,v(2,4,49))"))
                .getMessage().contains("variaveis"));
        assertTrue(assertThrows(TermoTexto.ErroSintaxe.class, () -> TermoTexto.factos("tempo(ev_1,9.5)"))
                .getMessage().contains("inteiros"));
        assertTrue(assertThrows(TermoTexto.ErroSintaxe.class, () -> TermoTexto.factos("tempo(ev_1,900) x"))
                .getMessage().contains("texto a mais"));
    }

    @Test
    void variaveisDasPremissasEOperadoresEscrevemSeSemPlicas() throws Exception {
        assertEquals("versao_cmp(v(2,4,49),>=,v(2,4,49))", TermoTexto.texto(json.readTree(
                "{\"f\":\"versao_cmp\",\"args\":[{\"f\":\"v\",\"args\":[2,4,49]},\">=\",{\"f\":\"v\",\"args\":[2,4,49]}]}")));
        assertEquals("versao(srv,A)", TermoTexto.texto(json.readTree("{\"f\":\"versao\",\"args\":[\"srv\",\"A\"]}")));
    }
}
