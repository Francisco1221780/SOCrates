# referencia_prolog

Resultado do motor Prolog para cada variante de cenários, um ficheiro JSON por variante (`base.json`, `respostas_3_10b_17.json`, `fecho_sim.json`, …). Cada ficheiro tem `variante`, `cenarios` (os ids pela ordem; os passos de cada cenário estão no `cenarios.json`, e um id que não é de um cenário, como `resposta_07`, é um passo com uma resposta do analista, cujos factos são os de origem `entrada` desse passo) e `resposta`: a resposta do `wrapper.pl` a esse pedido, no formato do contrato (estado, perguntas, incidentes, tickets e todos os factos com origem, regra e premissas).

Servem de referência para o Drools e para o comparador. Compara-se pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

Geram-se com `swipl motores/prolog/ferramentas/gera_referencias.pl` (ver `motores/prolog/ferramentas/README.md`); não se editam à mão.

Estado: resultados do `soc_motor.pl` sobre a base v6.1 (ver "Resultados de referência" no `README_motor.md`).
