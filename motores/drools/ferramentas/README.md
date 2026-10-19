# ferramentas

Scripts do lado Drools. O que sai daqui não se edita à mão.

| Script | Gera |
| --- | --- |
| `gera_referencia_agn.pl` | A referência Prolog só com as regras dos grupos A a G e N (R1–R34 e R89–R92) em `motores/drools/src/test/resources/referencia_agn/<variante>.json`, para o nível A–G+N do `ParidadeTest`. |
| `gera_porque_nao.pl` | A saída do `whynot/1` do Prolog para os factos de `src/test/resources/porque_nao/factos.txt`, depois das sequências `base` e `normal`, em `src/test/resources/porque_nao/prolog.txt`, para o `PorqueNaoTest`. |

## Regenerar a referência A–G+N (`gera_referencia_agn.pl`)

**Quando:** sempre que muda o resultado do motor Prolog para estas regras: o `soc_motor.pl`, o `wrapper.pl`, as regras A–G e N, o `soc_suporte.txt`, os factos da base ou os cenários (neste caso, primeiro o `gera_json.pl`, porque os passos vêm do `cenarios.json`). Faz-se commit da referência junto.

```bash
swipl motores/drools/ferramentas/gera_referencia_agn.pl              # escreve referencia_agn/<variante>.json
swipl motores/drools/ferramentas/gera_referencia_agn.pl --verificar  # só verifica; código 1 se alguma estiver desatualizada
```

**Como funciona:** copia o `soc_factos.txt` e o `soc_suporte.txt` de `base_conhecimento/` para uma pasta temporária, com um `soc_regras.txt` que só tem as regras 1–34 e 89–92 (falha se o filtro não der 38 regras). Corre o `motores/prolog/wrapper.pl` sobre essa pasta, para cada uma das 10 variantes do `motores/prolog/ferramentas/gera_referencias.pl`, e escreve o mesmo formato da `referencia_prolog/` (`variante`, `cenarios`, `resposta`). Demora cerca de 25 s. A pasta temporária apaga-se no fim.

As variantes estão repetidas no script; o `ParidadeTest` verifica que os cenários de cada variante são os mesmos nas duas referências.

## Regenerar a referência do "porque não?" (`gera_porque_nao.pl`)

**Quando:** sempre que mudam as regras, o `soc_motor.pl`, os factos da base ou os cenários, ou quando se acrescenta um facto a `src/test/resources/porque_nao/factos.txt` (um por linha, em sintaxe Prolog; pode ter variáveis).

```bash
swipl motores/drools/ferramentas/gera_porque_nao.pl              # escreve porque_nao/prolog.txt
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar  # código 1 se estiver desatualizada
```

Carrega o `soc_motor.pl` e a base, injeta as sequências `base` e `normal` (como a opção 13 do menu) e escreve, para cada facto, `#### <facto>` e o que o `whynot/1` escreve. O `PorqueNaoTest` compara linha a linha, sem os números dos factos (mudam entre motores).

