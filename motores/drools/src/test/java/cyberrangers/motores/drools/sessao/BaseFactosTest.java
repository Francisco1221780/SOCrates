package cyberrangers.motores.drools.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cyberrangers.motores.drools.modelo.Facto;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieSession;

class BaseFactosTest {

    private final KieSession sessao = KieServices.Factory.get().getKieClasspathContainer().newKieSession("sessao");
    private final BaseFactos base = new BaseFactos(sessao);

    @AfterEach
    void fecha() {
        sessao.dispose();
    }

    @Test
    void numeraPorOrdemENaoRepeteFactos() {
        base.insereInicial(Facto.de("zona", "web_srv_05", "dmz"));
        base.passo("cenario_06");
        base.insereEntrada(Facto.de("tempo", "ev_f1", 2200));
        base.insereEntrada(Facto.de("tempo", "ev_f1", 2200L));
        base.insereEntrada(Facto.de("zona", "web_srv_05", "dmz"));

        List<Facto> factos = base.factos();
        assertEquals(2, factos.size());
        Registo inicial = base.registo(factos.get(0));
        assertEquals(1, inicial.numero());
        assertEquals(Origem.INICIAL, inicial.origem());
        assertNull(inicial.passo());
        Registo entrada = base.registo(factos.get(1));
        assertEquals(2, entrada.numero());
        assertEquals(Origem.ENTRADA, entrada.origem());
        assertEquals("cenario_06", entrada.passo());
        assertNull(entrada.regra());
        assertEquals(2, sessao.getObjects(o -> o instanceof Facto).size());
    }

    @Test
    void factoLidoPorUmaCondicaoCalculadaMudaAMarca() {
        base.insereEntrada(Facto.de("tempo", "ev_f1", 2200));
        base.insereEntrada(Facto.de("tempo", "ev_f1", 2200));
        base.insereEntrada(Facto.de("zona", "web_srv_05", "dmz"));

        long tempos = sessao.getObjects(o -> o instanceof Marca m && m.getFunctor().equals("tempo/2")).stream()
                .mapToLong(o -> ((Marca) o).getVersao()).sum();
        assertEquals(1, tempos);
        assertEquals(BaseFactos.LIDOS_POR_CONDICOES.size(), sessao.getObjects(o -> o instanceof Marca).size());
    }
}
