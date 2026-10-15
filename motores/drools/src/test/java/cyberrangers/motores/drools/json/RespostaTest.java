package cyberrangers.motores.drools.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cyberrangers.motores.drools.modelo.Facto;
import cyberrangers.motores.drools.modelo.Premissa;
import cyberrangers.motores.drools.modelo.Variavel;
import cyberrangers.motores.drools.sessao.BaseFactos;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieSession;

class RespostaTest {

    private final KieSession sessao = KieServices.Factory.get().getKieClasspathContainer().newKieSession("sessao");
    private final BaseFactos base = new BaseFactos(sessao);

    @AfterEach
    void fecha() {
        sessao.dispose();
    }

    /** Como o facto_json/4 do wrapper: numero do facto, {"f":"nao",...}, termo calculado; variaveis A, B pela ordem. */
    @Test
    void premissasSaemComoNoWrapper() {
        Facto vetor = Facto.de("vetor_aberto", "ev_f1", "web_srv_05", "web_srv_05_apache", "cve_2021_41773");
        base.insereEntrada(vetor);
        Variavel x = Variavel.nova();

        assertEquals("[1,"
                        + "{\"f\":\"nao\",\"args\":[{\"f\":\"versao\",\"args\":[\"web_srv_05_apache\",\"A\"]}]},"
                        + "{\"f\":\"nao\",\"args\":[{\"f\":\"par\",\"args\":[\"B\",\"A\"]}]},"
                        + "{\"f\":\"porto_compativel\",\"args\":[\"ev_f1\",80,\"tcp\"]}]",
                Resposta.premissas(base, List.of(
                        Premissa.de(vetor),
                        Premissa.nao("versao", "web_srv_05_apache", x),
                        Premissa.nao("par", Variavel.nova(), x),
                        Premissa.calculada("porto_compativel", "ev_f1", 80, "tcp"))).toString());
    }
}
