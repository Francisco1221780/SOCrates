# api

Camada HTTP: os controllers REST. O corpo do pedido e da resposta é o JSON do contrato, passado como texto; não há DTOs.

- `InferController`: `POST /api/{motor}/infer`.
- `ExplicacaoController`: as explicações de uma resposta, nos dois motores. O corpo é o do `/infer` (`versaoKB`, `passos`) com o que se quer explicar; o servidor corre os passos de novo e monta a explicação a partir da resposta.
  - `POST /api/{motor}/como`: com `facto` (`{"numero": N}` ou `{"predicado", "args"}`, nunca os dois); responde `{estado, motor, versaoKB, facto, arvore, erro}`.
  - `POST /api/{motor}/porque`: o mesmo, e cada nó concluído traz `regraCompleta`.
  - `POST /api/{motor}/porque-pergunta`: com `ativo`, `evento` e `pergunta` de uma pergunta pendente; responde `{estado, motor, versaoKB, pergunta, geradaPor, regrasQueUsamAResposta, erro}`.
  - `POST /api/{motor}/whynot`: com `facto` (`{"predicado", "args"}`, sem `numero`; os `args` podem ter variáveis `{"var": "Ev"}`) e `profundidade` opcional (1 a 8, 4 por omissão); responde `{estado, motor, versaoKB, facto, explicacao, erro}`, com as linhas `{nivel, tipo, ...}` do `PorqueNao` do Drools. Ao contrário dos outros, não se monta a partir da resposta: é o motor que o faz. Por agora só o Drools; o Prolog responde `501` até o `soc_motor.pl` e o `wrapper.pl` terem o "porque não?" estruturado.
- `SystemController`: `GET /api/cenarios` e `GET /api/health`. O health diz, por motor, `pronto`, `versaoKB`, `baseCarregada`, `metaconhecimento` (`em_dia`, `desatualizado`, `desconhecido` ou `nao_aplicavel` no Drools) e `erros`; um motor com outra `versaoKB` que a do catálogo não conta como pronto.
- `HttpResponseUtils`: o código HTTP de cada `TipoResultado`. As explicações usam os mesmos do `/infer` e mais o `422`, quando o facto ou a pergunta pedidos não estão na resposta do motor, e o `501`, quando o motor ainda não tem o "porque não?".

Não tem lógica de inferência: recebe o pedido e delega em `application`.
