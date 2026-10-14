# engine.prolog

Adaptador do motor Prolog: lança `swipl` com `ProcessBuilder`, envia o pedido JSON por `stdin` e lê a resposta de `stdout`.

Critérios a cumprir aqui (AC1 a AC7 do plano): argumentos separados, UTF-8, `stderr` lido em paralelo, timeout que termina o processo, caminhos vindos de configuração e limite de processos em simultâneo.

O código Prolog (`wrapper.pl`, motor, base) fica em `motores/prolog/` e `base_conhecimento/`, não aqui.
