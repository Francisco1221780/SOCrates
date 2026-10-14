# referencia_prolog

Resultado do motor Prolog para cada variante de cenários, um ficheiro por variante (`base.txt`, `respostas_3b_10d_17b.txt`, …). Cada linha tem o número do facto, o facto e a regra que o concluiu (ou `dado`), separados por tabulações.

Servem de referência para o Drools e para o comparador. Compara-se pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

Estado: por gerar para a base v6, depois de o motor ter a reavaliação por `condicao_le/2`.
