package socrates.motores.drools.explicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import socrates.motores.drools.explicacao.Unificacao.Comp;
import socrates.motores.drools.modelo.Facto;
import socrates.motores.drools.modelo.Termo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnificacaoTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Map<Var, Object> vazio = new HashMap<>();

    @Test
    void unificaTermosCompostosListasEFactos() {
        Var ev = new Var("Ev");
        Var alvo = new Var("Alvo");
        Comp padrao = new Comp("incidente", List.of(new Comp("inc", List.of("versao_por_confirmar", ev)), "versao_por_confirmar",
                alvo, List.of(alvo)));
        Facto f = Facto.de("incidente", Termo.de("inc", "versao_por_confirmar", "ev_f1"), "versao_por_confirmar", "web_srv_05",
                List.of("web_srv_05"));
        Map<Var, Object> s = Unificacao.unifica(padrao, f, vazio);
        assertNotNull(s);
        assertEquals("ev_f1", Unificacao.deref(ev, s));
        assertEquals("web_srv_05", Unificacao.deref(alvo, s));
        Facto outro = Facto.de("incidente", Termo.de("inc", "versao_por_confirmar", "ev_f1"), "versao_por_confirmar", "web_srv_05",
                List.of("web_srv_06"));
        assertNull(Unificacao.unifica(padrao, outro, vazio));
        assertNotNull(Unificacao.unifica(3L, 3, vazio));
    }

    @Test
    void cadaSublinhadoEUmaVariavelNovaEOMesmoNomeEAMesma() throws Exception {
        Map<String, Var> nomes = new HashMap<>();
        Comp t = (Comp) Unificacao.deJson(json.readTree(
                "{\"f\":\"p\",\"args\":[{\"var\":\"_\"},{\"var\":\"_\"},{\"var\":\"X\"},{\"var\":\"X\"}]}"), nomes);
        assertNotSame(t.args().get(0), t.args().get(1));
        assertTrue(t.args().get(2) == t.args().get(3));
        assertEquals("{\"f\":\"p\",\"args\":[\"A\",\"B\",\"C\",\"C\"]}", Unificacao.json(t, vazio).toString());
    }

    @Test
    void varianteNaoConfundeAtomoComVariavel() {
        Comp comVariavel = new Comp("p", List.of(new Var("X")));
        Comp comAtomo = new Comp("p", List.of("A"));
        assertFalse(Unificacao.variante(comVariavel, vazio).equals(Unificacao.variante(comAtomo, vazio)));
        assertEquals(Unificacao.variante(comVariavel, vazio), Unificacao.variante(new Comp("p", List.of(new Var("Y"))), vazio));
    }

    // --- condicoes calculadas com variaveis por ligar, como no Prolog

    private Suporte suporte(Facto... factos) {
        return new Suporte(List.of(factos), Regras.carrega());
    }

    @Test
    void calculadaComVariavelLigaANoPrimeiroFactoQueServe() {
        Suporte s = suporte(Facto.de("tempo", "ev_0", 50), Facto.de("tempo", "ev_1", 100), Facto.de("tempo", "ev_2", 200));
        Var antes = new Var("EvAnterior");
        // anterior(EvAnterior, ev_2): o Prolog percorre os tempo/2 e liga o primeiro que e anterior
        Map<Var, Object> r = s.calculada(new Comp("anterior", List.of(antes, "ev_2")), vazio).findFirst().orElseThrow();
        assertEquals("ev_0", Unificacao.deref(antes, r));
        assertTrue(s.calculada(new Comp("anterior", List.of("ev_2", new Var("X"))), vazio).findFirst().isEmpty());
    }

    @Test
    void contaComVariavelPorLigarEFalsa() {
        Suporte s = suporte(Facto.de("tempo", "ev_1", 100), Facto.de("tempo", "ev_2", 130));
        Regras.Positiva depois = new Regras.Positiva(new Comp("depois", List.of("ev_2", "ev_1", new Var("Janela"))));
        assertTrue(s.verifica(depois, vazio).isEmpty());
        assertTrue(s.verifica(new Regras.Positiva(new Comp("depois", List.of("ev_2", "ev_1", 60L))), vazio).isPresent());
    }

    @Test
    void versaoInvalidaComVariavelEVerdadeiraComoNoProlog() {
        Suporte s = suporte();
        assertTrue(s.calculada(new Comp("versao_invalida", List.of(new Var("V"))), vazio).findFirst().isPresent());
        assertTrue(s.calculada(new Comp("versao_valida", List.of(new Var("V"))), vazio).findFirst().isEmpty());
    }

    @Test
    void origemEvidenciaLigaOUltimoArgumento() {
        Suporte s = suporte(Facto.de("vuln_afeta", "cve_x", "apache"), Facto.de("evento_processo", "ev_h", "python3"));
        Var origem = new Var("O");
        Map<Var, Object> r = s.calculada(new Comp("origem_evidencia", List.of("ev_h", "ev_a", "srv", "cve_x", origem)), vazio)
                .findFirst().orElseThrow();
        assertEquals("{\"f\":\"por_confirmar\",\"args\":[{\"f\":\"processo_desconhecido\",\"args\":[\"python3\"]}]}",
                Unificacao.json(origem, r).toString());
    }
}
