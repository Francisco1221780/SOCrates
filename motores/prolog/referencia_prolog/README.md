# referencia_prolog

Resultado do motor Prolog para cada variante de cenários, um ficheiro por variante (`base.txt`, `respostas_3b_10d_17b.txt`, …). Cada linha tem o número do facto, o facto e a regra que o concluiu (ou `dado`), separados por tabulações.

Servem de referência para o Drools e para o comparador. Compara-se pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

## Variantes (base v6)

Cada variante parte do estado inicial (`carrega_bc` e `arranca_motor`) e injeta os cenários por ordem, com o motor a
correr entre cada um (`injeta_cenarios/1`). As sequências `sequencia_base`, `sequencia_atualizacoes` e
`sequencia_normal` estão no `base_conhecimento/soc_cenarios.pl`.

| Ficheiro | Cenários | Resultado |
| --- | --- | --- |
| `base.txt` | `sequencia_base` (1 a 29b e 35 a 39) | 1110 factos, 61 incidentes, 61 tickets (41 abertos, 20 fechados), 9 perguntas |
| `base_e_atualizacoes.txt` | base + `sequencia_atualizacoes` (06b, 13b, 21b, 27b) | 1170 factos, 65 incidentes, 65 tickets (39 abertos, 26 fechados), 8 perguntas |
| `base_atualizacoes_e_normais.txt` | base + atualizações + `sequencia_normal` (30 a 34) | 1186 factos, com as mesmas 688 conclusões: os cenários 30 a 34 não acrescentam nenhuma (AC9) |
| `r7b_sim_19b_sim.txt` | base + 7b_sim + 19b_sim | 1149 factos, 64 incidentes, 64 tickets (40 abertos, 24 fechados), 8 perguntas |
| `r7b_nao.txt` | base + 7b_nao | 1116 factos, 61 incidentes, 61 tickets (40 abertos, 21 fechados), 8 perguntas |
| `r7b_nao_sei_19b_nao.txt` | base + 7b_nao_sei + 19b_nao | 1126 factos, 62 incidentes, 62 tickets (41 abertos, 21 fechados), 8 perguntas |
| `respostas_3b_10d_17b.txt` | base + 3b + 10d + 17b | 1136 factos, 63 incidentes, 63 tickets (40 abertos, 23 fechados), 7 perguntas |
| `c29c_em_vez_de_29b.txt` | base com o 29c no lugar do 29b | 1110 factos, 61 incidentes, 61 tickets (41 abertos, 20 fechados), 9 perguntas |
| `c35d_em_vez_de_35c.txt` | base com o 35d no lugar do 35c | 1110 factos, 61 incidentes, 61 tickets (41 abertos, 20 fechados), 9 perguntas |
| `c36c_em_vez_de_36b.txt` | base com o 36c no lugar do 36b | 1102 factos, 60 incidentes, 60 tickets (40 abertos, 20 fechados), 9 perguntas |

Para regenerar uma variante, com o `soc_menu.pl` carregado e a pasta de trabalho em `motores/prolog/`:

```prolog
?- carrega_bc('../../base_conhecimento/soc_factos.txt'), arranca_motor,
   sequencia_base(B), append(B, [c7b_sim, c19b_sim], L), injeta_cenarios(L), estado.
```

Os resultados foram confirmados com uma versão do motor que segue à letra os três requisitos do `soc_suporte.txt`
(uma só passagem por `arranca_motor`, e reavaliação da regra inteira quando chega um facto lido por uma condição
calculada, `condicao_le/2`): em todas as variantes dá os mesmos factos, cada um concluído pela mesma regra. Só os
números dos factos mudam, por causa da ordem de disparo.

Estado: gerados com o `soc_motor.pl` sobre a base v6. Falta compará-los com os resultados que o README da base
registar (AC8, issues #1 e #3).
