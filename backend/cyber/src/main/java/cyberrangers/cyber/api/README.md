# api

Camada HTTP: controllers REST e DTOs do contrato JSON.

- `InferController`: `POST /api/{motor}/infer` e `POST /api/{motor}/whynot`.
- `SystemController`: `GET /api/cenarios` e `GET /api/health`. O health diz, por motor, `pronto`, `versaoKB`, `baseCarregada`, `metaconhecimento` (`em_dia`, `desatualizado`, `desconhecido` ou `nao_aplicavel` no Drools) e `erros`; um motor com outra `versaoKB` que a do catálogo não conta como pronto.
- DTOs de pedido e resposta (passos, factos, perguntas, incidentes, tickets, erro).

Não tem lógica de inferência: recebe o pedido e delega em `application`.
