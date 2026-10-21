# api

Camada HTTP: os controllers REST. O corpo do pedido e da resposta é o JSON do contrato, passado como texto; não há DTOs.

- `InferController`: `POST /api/{motor}/infer`.
- `ExplicacaoController`: as três explicações ("como?", "porquê?" de uma pergunta e "porque não?"), nos dois motores. O corpo é o do `/infer` (`versaoKB`, `passos`) com o que se quer explicar; o servidor corre os passos de novo e monta a explicação a partir da resposta.
  - `POST /api/{motor}/como`: com `facto` (`{"numero": N}` ou `{"predicado", "args"}`, nunca os dois); responde `{estado, motor, versaoKB, facto, arvore, erro}`. Com `?regras=true`, cada nó concluído traz também `regraCompleta` (`{id, se, entao}`, como no `GET /api/regras`). Substitui o antigo `POST /api/{motor}/porque`, que saiu: na UC, o "porquê?" é só o das perguntas.
  - `POST /api/{motor}/porque-pergunta`: com `ativo`, `evento` e `pergunta` de uma pergunta pendente; responde `{estado, motor, versaoKB, pergunta, geradaPor, regrasQueUsamAResposta, erro}`.
  - `POST /api/{motor}/whynot`: com `facto` (`{"predicado", "args"}`, sem `numero`; os `args` podem ter variáveis `{"var": "Ev"}`) e `profundidade` opcional (1 a 8, 4 por omissão); responde `{estado, motor, versaoKB, facto, explicacao, erro}`, com as linhas `{nivel, tipo, ...}` do `PorqueNao` do Drools. Ao contrário dos outros, não se monta a partir da resposta: é o motor que o faz. No Prolog é o modo `whynot` do `wrapper.pl`; os dois motores dão as mesmas linhas (só os números dos factos mudam).
- `SystemController`: `GET /api/cenarios`, `GET /api/regras` e `GET /api/health`. O `/regras` devolve o `regras.json` (gerado do `soc_regras.txt` pelo `gera_json.pl`) tal como está: `{versaoKB, regras: [{id, se, entao}]}`, as 92 regras da base. O health diz, por motor, `pronto`, `versaoKB`, `baseCarregada`, `metaconhecimento` (`em_dia`, `desatualizado`, `desconhecido` ou `nao_aplicavel` no Drools) e `erros`; um motor com outra `versaoKB` que a do catálogo não conta como pronto.
- `HttpResponseUtils`: o código HTTP de cada `TipoResultado`. As explicações usam os mesmos do `/infer` e mais o `422`, quando o facto ou a pergunta pedidos não estão na resposta do motor, e o `501`, se um motor não tiver o "porque não?" (hoje os dois têm).

Não tem lógica de inferência: recebe o pedido e delega em `application`.
