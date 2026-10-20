# engine.drools

Adaptadores finos do módulo `motores/drools`:

- `DroolsAdapter`: implementa `MotorInferencia` e chama o `MotorDrools` com o pedido em JSON.
- `ExplicadorDrools`: implementa `Explicador` e chama a `Explicacao` do módulo, com as regras do `regras.json`. Serve também as respostas do Prolog: o `regras.json` é gerado do mesmo `soc_regras.txt`, e uma resposta de outra `versaoKB` dá erro.

A lógica, as regras DRL e os factos iniciais ficam em `motores/drools/`, não aqui. A conversão entre o Jackson 2 do módulo e o Jackson 3 do backend faz-se por texto JSON.
