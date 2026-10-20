# config

Configuração Spring: propriedades tipadas e beans.

| Classe             | Prefixo        | Propriedades                                                                                                           |
|--------------------|----------------|------------------------------------------------------------------------------------------------------------------------|
| `PrologProperties` | `socrates.prolog` | `swipl` (executável), `home` (raiz com `motores/prolog/` e `base_conhecimento/`), `timeout`, `max-processos`, `espera` |

Os valores de omissão estão no `application.yaml` e na própria classe; um valor inválido (timeout zero, `max-processos` < 1, …) impede o arranque com uma mensagem clara.
