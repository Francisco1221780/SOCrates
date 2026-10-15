package cyberrangers.motores.drools.json;

/** Pedido mal formado; a mensagem vai tal e qual para o campo "erro" da resposta. */
public class PedidoInvalido extends RuntimeException {

    public PedidoInvalido(String mensagem) {
        super(mensagem);
    }
}
