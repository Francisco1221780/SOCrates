# application

Casos de uso.

- `InferenceService`: valida o pedido e escolhe o motor.
- `RequestValidator`: `versaoKB`, forma dos factos, passos não vazios.
- `MotorInferencia`: interface que `engine.prolog` e `engine.drools` implementam, para os dois responderem ao mesmo pedido com o mesmo formato.

Não depende de detalhes de nenhum motor.
