# engine.prolog

Adaptador do motor Prolog: lança `swipl` com `ProcessBuilder`, envia o pedido JSON por `stdin` e lê a resposta de `stdout`.

| Classe | Papel |
| --- | --- |
| `PrologAdapter` | Implementa `MotorInferencia`: um processo `swipl wrapper.pl <base>` por pedido, com argumentos separados (AC1), UTF-8 (AC2), `stdout` e `stderr` lidos em paralelo (AC4), timeout que termina o processo (AC5) e limite de processos em simultâneo (AC7). O JSON do wrapper vai tal como veio; as falhas do processo dão o JSON de erro do contrato. |
| `PrologInstaller` | Dá a pasta com `motores/prolog/wrapper.pl` e `base_conhecimento/` (AC6): `cyber.prolog.home` se estiver definido, senão uma cópia temporária do que o Maven pôs no jar em `prolog-dist/`. |

A configuração (`cyber.prolog.*`) está em `config/PrologProperties`. O código Prolog (`wrapper.pl`, motor, base) fica em `motores/prolog/` e `base_conhecimento/`, não aqui.
