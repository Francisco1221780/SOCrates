# ferramentas

Scripts do lado Drools. O que sai daqui não se edita à mão.

| Script | Gera |
| --- | --- |
| `gera_porque_nao.pl` | A saída do `whynot/1` do Prolog para os factos de `src/test/resources/porque_nao/factos.txt`, depois das sequências `base` e `normal`, em `src/test/resources/porque_nao/prolog.txt`, para o `PorqueNaoTest`. |

## Regenerar a referência do "porque não?" (`gera_porque_nao.pl`)

**Quando:** sempre que mudam as regras, o `soc_motor.pl`, os factos da base ou os cenários, ou quando se acrescenta um facto a `src/test/resources/porque_nao/factos.txt` (um por linha, em sintaxe Prolog; pode ter variáveis).

```bash
swipl motores/drools/ferramentas/gera_porque_nao.pl              # escreve porque_nao/prolog.txt
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar  # código 1 se estiver desatualizada
```

Carrega o `soc_motor.pl` e a base, injeta as sequências `base` e `normal` e escreve, para cada facto, `#### <facto>` e o que o `whynot/1` escreve. O `PorqueNaoTest` compara linha a linha, sem os números dos factos (mudam entre motores).

A referência da paridade (`ParidadeTest`) é a do motor Prolog com todas as regras, em `motores/prolog/referencia_prolog/`, gerada pelo `motores/prolog/ferramentas/gera_referencias.pl`. O nível só com os grupos A a G e N (`gera_referencia_agn.pl` e `src/test/resources/referencia_agn/`) saiu na F0008.
