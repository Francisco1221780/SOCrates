# application

Casos de uso.

- `InferenceService`: valida o pedido, escolhe o motor e classifica a resposta. As explicações (`como`, com ou sem as regras, e `porquePergunta`) passam pelo mesmo caminho do `infere` e, se o motor responder sem erro, montam-se a partir da resposta com o `Explicador`.
- `RequestValidator`: `versaoKB`, forma dos factos, passos não vazios, e os campos das explicações (`facto`; `ativo`, `evento`, `pergunta`; no "porque não?", os termos do facto e a `profundidade`).
- `MotorInferencia`: interface que `engine.prolog` e `engine.drools` implementam, para os dois responderem ao mesmo pedido com o mesmo formato. Tem também o `porqueNao`, que só o motor pode fazer (testa as regras contra os factos da sessão); os dois motores o fazem, e um motor que não o fizesse responderia "nao suportado" (`501`).
- `Explicador`: interface que `engine.drools` implementa. Monta as explicações a partir de uma resposta de qualquer dos dois motores, porque as respostas têm o mesmo formato.

Não depende de detalhes de nenhum motor.
