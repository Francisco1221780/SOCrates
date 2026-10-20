package socrates.backend.engine.drools;

import socrates.backend.application.Explicador;
import socrates.motores.drools.explicacao.Explicacao;
import org.springframework.stereotype.Component;

/**
 * As explicacoes do modulo Drools (a classe Explicacao), com as regras do regras.json. Servem tambem as respostas do
 * Prolog: o regras.json e gerado do mesmo soc_regras.txt, e uma resposta de outra versaoKB da erro.
 */
@Component
public class ExplicadorDrools implements Explicador {

    @Override
    public String como(String resposta, String facto, boolean comRegras) {
        return Explicacao.como(resposta, facto, comRegras);
    }

    @Override
    public String porquePergunta(String resposta, String ativo, String evento, String pergunta) {
        return Explicacao.porquePergunta(resposta, ativo, evento, pergunta);
    }
}
