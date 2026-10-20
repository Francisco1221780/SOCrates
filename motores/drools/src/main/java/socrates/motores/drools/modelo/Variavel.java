package socrates.motores.drools.modelo;

/**
 * Uma variavel livre numa premissa negada, ex.: o {@code _} de {@code nao versao(Inst,_)}.
 * Compara-se por identidade; na resposta ganha um nome (A, B, ...) como no numbervars do wrapper Prolog.
 */
public final class Variavel {

    private Variavel() {
    }

    public static Variavel nova() {
        return new Variavel();
    }

    @Override
    public String toString() {
        return "_";
    }
}
