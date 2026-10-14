package cyberrangers.cyber.application;

/** Um motor de inferência: recebe o pedido JSON e devolve a resposta JSON no formato comum. */
public interface MotorInferencia {

    String nome();

    String infere(String pedidoJson);
}
