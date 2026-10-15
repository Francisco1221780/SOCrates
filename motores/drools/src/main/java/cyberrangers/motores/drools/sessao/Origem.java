package cyberrangers.motores.drools.sessao;

/** De onde veio um facto: dados fixos da base, um passo do pedido, ou uma regra. */
public enum Origem {
    INICIAL,
    ENTRADA,
    REGRA;

    public String json() {
        return name().toLowerCase();
    }
}
