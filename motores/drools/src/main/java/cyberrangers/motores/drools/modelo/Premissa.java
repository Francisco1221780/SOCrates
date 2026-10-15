package cyberrangers.motores.drools.modelo;

import java.util.Arrays;

/**
 * Uma premissa da justificacao de um facto concluido: uma por condicao do LHS, pela ordem da regra Prolog
 * (como o justifica/3 do soc_motor.pl). Na resposta: o numero do facto, {@code {"f":"nao","args":[termo]}} ou o termo
 * da condicao calculada.
 */
public sealed interface Premissa permits Premissa.DoFacto, Premissa.Negacao, Premissa.Calculada {

    /** Condicao verificada por um facto. */
    record DoFacto(Facto facto) implements Premissa {
    }

    /** Condicao {@code nao X}: X nao esta registado (ou a condicao calculada X e falsa). */
    record Negacao(Termo termo) implements Premissa {
    }

    /** Condicao calculada verdadeira (ex.: {@code porto_compativel(ev_f1,80,tcp)}). */
    record Calculada(Termo termo) implements Premissa {
    }

    static Premissa de(Facto facto) {
        return new DoFacto(facto);
    }

    /** {@code nao predicado(args...)}; um argumento livre e {@code Variavel.nova()}. */
    static Premissa nao(String predicado, Object... args) {
        return new Negacao(new Termo(predicado, Arrays.asList(args)));
    }

    static Premissa calculada(String nome, Object... args) {
        return new Calculada(new Termo(nome, Arrays.asList(args)));
    }
}
