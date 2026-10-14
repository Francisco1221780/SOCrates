# api

Camada HTTP: controllers REST e DTOs do contrato JSON.

- `InferController`: `POST /api/{motor}/infer` e `POST /api/{motor}/whynot`.
- `CatalogController`: `GET /api/cenarios` e `GET /api/health`.
- DTOs de pedido e resposta (passos, factos, perguntas, incidentes, tickets, erro).

Não tem lógica de inferência: recebe o pedido e delega em `application`.
