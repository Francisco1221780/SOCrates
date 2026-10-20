package socrates.motores.drools.explicacao;

/**
 * Uma variavel Prolog numa regra ou num facto a explicar. Cada instancia e uma variavel diferente (igualdade por
 * identidade): o mesmo nome em duas regras, ou dois "_", sao variaveis diferentes.
 */
final class Var {

    private final String nome;

    Var(String nome) {
        this.nome = nome;
    }

    String nome() {
        return nome;
    }

    @Override
    public String toString() {
        return nome;
    }
}
