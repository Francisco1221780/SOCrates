# motores

Um projeto por motor de inferência. O backend (`backend/socrates`) só tem os adaptadores que os chamam.

| Pasta | Conteúdo | Como corre |
| --- | --- | --- |
| `prolog/` | Motor SWI-Prolog, wrapper, ferramentas e resultados de referência. | Processo `swipl` lançado pelo backend. |
| `drools/` | Regras DRL e `MotorDrools` (módulo Maven sem Spring). | Dentro da JVM do backend. |

Os dois falam o mesmo contrato: pedido JSON (lista de passos) e resposta JSON com factos, incidentes, tickets e perguntas.

A base de conhecimento está em `../base_conhecimento/`, não aqui.
