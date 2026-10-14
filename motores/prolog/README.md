# motores/prolog

Lado Prolog do sistema. Corre num processo `swipl` lançado pelo backend (`engine/prolog/PrologAdapter`) e também à mão, sem Java.

Conteúdo previsto:

| Ficheiro / pasta | Papel |
| --- | --- |
| `soc_motor.pl` | Núcleo do motor (encadeamento direto, M1 a M4). Sem menu, sem `read`, sem escrita no ecrã. |
| `soc_menu.pl` | Interface manual (`menu/0`) para demonstrações e debug. Nunca é carregada pelo wrapper. |
| `wrapper.pl` | Lê o pedido JSON do `stdin`, corre os passos e escreve uma resposta JSON no `stdout` (M5 a M9). |
| `ferramentas/` | Scripts de geração: metaconhecimento e JSON dos cenários. |
| `referencia_prolog/` | Resultados de referência do motor, um ficheiro por variante; servem de base ao Drools e ao comparador. |
| `README_motor.md` | Documentação do motor e da tabela de alterações M1 a M10. |

Regras do wrapper:

- O `stdout` tem exatamente um documento JSON; tudo o resto vai para `stderr`.
- UTF-8 nos dois lados e o processo termina sempre com `halt`.
- A pasta da base chega por argumento, nunca por caminho fixo.

Estado: o motor (`soc_motor.pl`, ainda com o menu no mesmo ficheiro) e os resultados de referência da v6 estão aqui;
o wrapper ainda não existe.
