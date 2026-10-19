package cyberrangers.motores.drools.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Termo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieSession;

/** origem_evidencia/5: um caso por resultado do classifica_origem do soc_suporte.txt. */
class CondicoesCalculadasTest {

    private final KieSession sessao = KieServices.Factory.get().getKieClasspathContainer().newKieSession("sessao");
    private final BaseFactos base = new BaseFactos(sessao);

    @BeforeEach
    void ataqueAUmaInstalacaoVulneravel() {
        f("vuln_afeta", "cve_x", "apache");
        f("componente", "srv_apache", "srv", "apache");
        f("processo_componente", "apache", "httpd");
        f("processo_componente", "openssh", "sshd");
        f("instancia_processo", "srv_apache", "httpd_1");
        f("vetor_aberto", "ev_a", "srv", "srv_apache", "cve_x");
        f("vulneravel", "srv_apache", "cve_x");
    }

    @AfterEach
    void fecha() {
        sessao.dispose();
    }

    private void f(String predicado, Object... args) {
        base.insereEntrada(Facto.de(predicado, args));
    }

    private Object origem() {
        return CondicoesCalculadas.origemEvidencia("ev_h", "ev_a", "srv", "cve_x", base);
    }

    @Test
    void instanciaDoSensorEAAtacada() {
        f("evento_instancia", "ev_h", "httpd_1");
        assertEquals("ligada", origem());
    }

    @Test
    void instanciaDoSensorEOutra() {
        f("evento_instancia", "ev_h", "httpd_2");
        assertEquals(Termo.de("de_outro_processo", Termo.de("instancia", "httpd_2")), origem());
    }

    @Test
    void vetorRevistoDeixaDeLigarAInstancia() {
        f("evento_instancia", "ev_h", "httpd_1");
        f("revisto", Termo.de("vetor_aberto", "ev_a", "srv", "srv_apache", "cve_x"), "porto_de_destino");
        assertEquals(Termo.de("de_outro_processo", Termo.de("instancia", "httpd_1")), origem());
    }

    @Test
    void semProcesso() {
        assertEquals(Termo.de("por_confirmar", "sem_processo"), origem());
    }

    @Test
    void processoDesconhecido() {
        f("evento_processo", "ev_h", "python3");
        assertEquals(Termo.de("por_confirmar", Termo.de("processo_desconhecido", "python3")), origem());
    }

    @Test
    void processoDeOutroPrograma() {
        f("evento_processo", "ev_h", "sshd");
        assertEquals(Termo.de("de_outro_processo", Termo.de("programa", "openssh")), origem());
    }

    @Test
    void unicaInstanciaComONome() {
        f("evento_processo", "ev_h", "httpd");
        assertEquals("ligada", origem());
    }

    @Test
    void variasInstanciasComONome() {
        f("componente", "srv_apache2", "srv", "apache");
        f("instancia_processo", "srv_apache2", "httpd_2");
        f("evento_processo", "ev_h", "httpd");
        assertEquals(Termo.de("por_confirmar", Termo.de("instancia_por_identificar", "httpd")), origem());
    }

    @Test
    void nenhumaInstanciaInventariadaNoAtivo() {
        f("evento_processo", "ev_h", "httpd");
        assertEquals(Termo.de("por_confirmar", Termo.de("instancia_nao_inventariada", "httpd")),
                CondicoesCalculadas.origemEvidencia("ev_h", "ev_a", "outro_srv", "cve_x", base));
    }

    @Test
    void semProgramaNoCatalogoFalha() {
        assertNull(CondicoesCalculadas.origemEvidencia("ev_h", "ev_a", "srv", "cve_y", base));
    }
}
