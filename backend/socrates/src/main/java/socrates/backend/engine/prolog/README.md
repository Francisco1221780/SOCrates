# engine.prolog

Adaptador do motor Prolog: lança `swipl` com `ProcessBuilder`, envia o pedido JSON por `stdin` e lê a resposta de `stdout`.

| Classe | Papel |
| --- | --- |
| `PrologAdapter` | Implementa `MotorInferencia`: um processo `swipl wrapper.pl <base>` por pedido, com argumentos separados (AC1), UTF-8 (AC2), `stdout` e `stderr` lidos em paralelo (AC4), timeout que termina o processo (AC5) e limite de processos em simultâneo (AC7). O JSON do wrapper vai tal como veio; as falhas do processo dão o JSON de erro do contrato. |
| `PrologAdapter.estado()` | O estado para o `/api/health` (AC13), verificado uma vez (no arranque) e guardado: (1) manda ao `wrapper.pl` um pedido com `versaoKB` vazia; a recusa traz a versão que o wrapper tem carregada e prova que a base carregou, e uma linha `ERROR` do consult no `stderr` conta como base com erros; (2) corre `ferramentas/gera_meta.pl --verificar` e diz se o metaconhecimento está `em_dia`, `desatualizado` (com as diferenças) ou `desconhecido`. Só fica pronto com a base carregada, sem erros e o metaconhecimento em dia. Para ver uma alteração à pasta de `socrates.prolog.home`, reinicia-se a aplicação. |
| `PrologAdapter.porqueNao()` | Ainda não suportado: responde "nao suportado" (o `/whynot` dá `501`) sem lançar o `swipl`. Fica à espera de o `whynot/1` do `soc_motor.pl` devolver as linhas estruturadas e de o `wrapper.pl` ter um modo "porque não". |
| `PrologInstaller` | Dá a pasta com `motores/prolog/wrapper.pl`, `motores/prolog/ferramentas/gera_meta.pl` e `base_conhecimento/` (AC6): `socrates.prolog.home` se estiver definido, senão uma cópia temporária do que o Maven pôs no jar em `prolog-dist/`. |

A configuração (`socrates.prolog.*`) está em `config/PrologProperties`. O código Prolog (`wrapper.pl`, motor, base) fica em `motores/prolog/` e `base_conhecimento/`, não aqui.
