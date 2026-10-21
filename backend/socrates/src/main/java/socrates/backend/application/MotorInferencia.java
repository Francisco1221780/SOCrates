package socrates.backend.application;

import socrates.backend.domain.SaudeMotor;

/** Um motor de inferência: recebe o pedido JSON e devolve a resposta JSON no formato comum. */
public interface MotorInferencia {

    String nome();

    String infere(String pedidoJson);

    SaudeMotor estado();

    String porqueNao(String pedidoJson, String factoJson, int profundidade);
}
