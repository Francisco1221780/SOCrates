package socrates.motores.drools.sessao;

/**
 * Marca de um tipo de facto que uma condicao calculada le (condicao_le/2 do soc_suporte.txt). A {@link BaseFactos}
 * faz update a marca sempre que entra um facto desse functor, e uma regra com {@code Marca(functor == "tempo/2")}
 * volta a avaliar-se por inteiro, como o dispara_regra/3 do soc_motor.pl faz quando chega um facto lido por uma
 * condicao calculada. Ex.: o tempo de um evento que chega depois do evento torna verdadeira a condicao depois/3.
 *
 * <p>A marca nao e premissa de nenhuma regra: nao entra na justificacao.
 */
public final class Marca {

    private final String functor;
    private long versao;

    Marca(String functor) {
        this.functor = functor;
    }

    public String getFunctor() {
        return functor;
    }

    /** Quantos factos deste functor entraram na sessao. */
    public long getVersao() {
        return versao;
    }

    void incrementa() {
        versao++;
    }

    @Override
    public String toString() {
        return "Marca(" + functor + ", " + versao + ")";
    }
}
