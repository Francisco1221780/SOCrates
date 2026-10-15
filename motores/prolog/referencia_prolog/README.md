# referencia_prolog

Resultado do motor Prolog para cada variante de cenários, um ficheiro JSON por variante (`base.json`, `respostas_3b_10d_17b.json`, …). Cada ficheiro tem `variante`, `cenarios` (os ids pela ordem; os passos de cada um estão no `cenarios.json`) e `resposta`: a resposta do `wrapper.pl` a esse pedido, no formato do contrato (estado, perguntas, incidentes, tickets e todos os factos com origem, regra e premissas).

Servem de referência para o Drools e para o comparador. Compara-se pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

Geram-se com `swipl motores/prolog/ferramentas/gera_referencias.pl` (ver `motores/prolog/ferramentas/README.md`); não se editam à mão.

Estado: resultados do `soc_motor.pl` sobre a base v6 (ver "Resultados de referência" no `README_motor.md`).
