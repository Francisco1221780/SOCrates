package cyberrangers.motores.drools.sessao;

import cyberrangers.motores.drools.modelo.Premissa;
import java.util.List;

/**
 * O que a sessao sabe de um facto alem do conteudo: o numero (ordem de chegada, como o facto/2 do Prolog), de onde
 * veio, em que passo apareceu e, se foi concluido, a regra e as premissas (como o justifica/3).
 */
public record Registo(int numero, Origem origem, String passo, Integer regra, List<Premissa> premissas) {

    public Registo {
        premissas = List.copyOf(premissas);
    }
}
