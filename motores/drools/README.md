# motores/drools

Lado Drools do sistema. Módulo Maven próprio, sem Spring: o backend depende dele e chama-o dentro da mesma JVM, em paralelo com o motor Prolog.

| Pasta | Conteúdo |
| --- | --- |
| `src/main/java/` | `MotorDrools`: recebe o pedido JSON, corre os passos numa `KieSession` nova e devolve a resposta JSON (mesmo contrato do `wrapper.pl`). |
| `src/main/resources/regras/` | Regras DRL (75, portadas por grupos A a N). |
| `src/main/resources/` | `factos_iniciais.json`, gerado a partir de `base_conhecimento/`; não se edita à mão. |
| `src/test/java/` | Corre cada cenário e compara com `motores/prolog/referencia_prolog/`. |

Regras do módulo:

- Não depende de Spring nem de classes do backend. A fronteira é JSON de entrada e JSON de saída.
- Uma `KieSession` nova por pedido: `insert` dos factos do passo, `fireAllRules()`, passo seguinte, `dispose()` no fim.
- Compara-se com o Prolog pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

Estado: módulo operacional com uma regra de exemplo (`regras/00_exemplo.drl`) e testes a passar; as 75 regras reais por portar.
