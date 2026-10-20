# application

Casos de uso.

- `InferenceService`: valida o pedido, escolhe o motor e classifica a resposta. As explicações (`como`, `porque`, `porquePergunta`) passam pelo mesmo caminho do `infere` e, se o motor responder sem erro, montam-se a partir da resposta com o `Explicador`.
- `RequestValidator`: `versaoKB`, forma dos factos, passos não vazios, e os campos das explicações (`facto`; `ativo`, `evento`, `pergunta`).
- `MotorInferencia`: interface que `engine.prolog` e `engine.drools` implementam, para os dois responderem ao mesmo pedido com o mesmo formato.
- `Explicador`: interface que `engine.drools` implementa. Monta as explicações a partir de uma resposta de qualquer dos dois motores, porque as respostas têm o mesmo formato.

Não depende de detalhes de nenhum motor.
