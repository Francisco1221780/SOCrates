# api

Camada HTTP: os controllers REST. O corpo do pedido e da resposta é o JSON do contrato, passado como texto; não há DTOs.

- `InferController`: `POST /api/{motor}/infer`. O `POST /api/{motor}/whynot` ainda não existe: depende do "porque não?" estruturado no Prolog.
- `ExplicacaoController`: as explicações de uma resposta, nos dois motores. O corpo é o do `/infer` (`versaoKB`, `passos`) com o que se quer explicar; o servidor corre os passos de novo e monta a explicação a partir da resposta.
  - `POST /api/{motor}/como`: com `facto` (`{"numero": N}` ou `{"predicado", "args"}`, nunca os dois); responde `{estado, motor, versaoKB, facto, arvore, erro}`.
  - `POST /api/{motor}/porque`: o mesmo, e cada nó concluído traz `regraCompleta`.
  - `POST /api/{motor}/porque-pergunta`: com `ativo`, `evento` e `pergunta` de uma pergunta pendente; responde `{estado, motor, versaoKB, pergunta, geradaPor, regrasQueUsamAResposta, erro}`.
- `SystemController`: `GET /api/cenarios` e `GET /api/health`. O health diz, por motor, `pronto`, `versaoKB`, `baseCarregada`, `metaconhecimento` (`em_dia`, `desatualizado`, `desconhecido` ou `nao_aplicavel` no Drools) e `erros`; um motor com outra `versaoKB` que a do catálogo não conta como pronto.
- `HttpResponseUtils`: o código HTTP de cada `TipoResultado`. As explicações usam os mesmos do `/infer` e mais o `422`, quando o facto ou a pergunta pedidos não estão na resposta do motor.

Não tem lógica de inferência: recebe o pedido e delega em `application`.
