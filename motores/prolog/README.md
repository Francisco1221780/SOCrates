# motores/prolog

Lado Prolog do sistema. Corre num processo `swipl` lançado pelo backend (`engine/prolog/PrologAdapter`) e também à mão, sem Java.

Conteúdo previsto:

| Ficheiro / pasta | Papel |
| --- | --- |
| `soc_motor.pl` | Núcleo do motor (encadeamento direto, M1 a M4). Sem menu, sem `read`, sem escrita no ecrã. |
| `soc_menu.pl` | Interface manual (`menu/0`) para demonstrações e debug. Nunca é carregada pelo wrapper. |
| `wrapper.pl` | Lê o pedido JSON do `stdin`, corre os passos e escreve uma resposta JSON no `stdout` (M5 a M10, issue #9). |
| `ferramentas/` | Scripts de geração: metaconhecimento e JSON dos cenários. |
| `referencia_prolog/` | Resultados de referência do motor, um ficheiro por variante; servem de base ao Drools e ao comparador. |
| `testes/` | Testes automáticos (`plunit`) do wrapper, dos JSON gerados e das referências; correm na CI em cada PR. |
| `README_motor.md` | Documentação do motor e da tabela de alterações M1 a M10. |

Regras do wrapper:

- O `stdout` tem exatamente um documento JSON; tudo o resto vai para `stderr`.
- UTF-8 nos dois lados e o processo termina sempre com `halt`.
- A pasta da base chega por argumento, nunca por caminho fixo.
- Qualquer erro sai como JSON com `estado: "erro"` e código de saída 1; sem erro, código 0.

## Correr o wrapper

A partir da raiz do repositório (a pasta da base pode ser relativa à pasta de trabalho ou absoluta):

```bash
swipl motores/prolog/wrapper.pl base_conhecimento < pedido.json
```

O wrapper carrega o `soc_motor.pl` da sua própria pasta e o `soc_factos.txt` da pasta dada, corre o motor sobre os
dados fixos e depois cada passo do pedido, por ordem: injeta os factos com `novo_facto/1` e corre o motor até não
haver conclusões novas. O que o motor escreve ("Base carregada…") vai para o `stderr`.

O `pedido.json` tem o formato abaixo (a `versaoKB` tem de ser a da base carregada, hoje `"6"`). Os factos de cada
cenário já estão neste formato no `passo` de cada cenário do `cenarios.json` (`backend/socrates/src/main/resources/generated/`):

```json
{"versaoKB": "6", "passos": [
  {"id": "cenario_07", "factos": [{"predicado": "alerta_ids", "args": ["ev_e1", "web_srv_04", "sig_apache_path_traversal"]}]},
  {"id": "cenario_07b_sim", "factos": [{"predicado": "resposta_analista", "args": ["web_srv_04", "ev_e1", "shell_servico", "sim"]}]}
]}
```

Resposta (uma linha; aqui formatada):

| Campo | Conteúdo |
| --- | --- |
| `estado` | `aguarda_resposta` se houver perguntas por responder, senão `concluido` (ou `erro`). |
| `motor`, `versaoKB` | `"prolog"` e a versão da base, como no `MotorDrools`. |
| `perguntas` | `pergunta_analista` sem `resposta_analista` e sem `pergunta_retirada`: `facto`, `ativo`, `evento`, `pergunta`, `texto` e `opcoes` (`valor`, `texto`), tirados da base. |
| `incidentes` | `facto`, `id`, `tipo`, `ativo`, `impactados`. |
| `tickets` | `facto`, `incidente`, `ativo`, `acao`, `gravidade`, `prioridade`, `impactados` e `fechado` (o motivo do `ticket_fechado`, ou `null`). |
| `factos` | Todos os factos por número: `numero`, `predicado`, `args`, `origem` (`inicial`, `entrada` ou `regra`), `passo` (o `id` do passo, ou `null` para os dados fixos), `regra` e `premissas` (de `justifica/3`; números de factos ou termos `nao X`, com as variáveis livres como `A`, `B`, …). |
| `erro` | `null`, ou a mensagem quando `estado` é `erro`. |

Os termos seguem a tabela da issue #6: átomo → string, inteiro → número, lista → array, termo composto →
`{"f": nome, "args": [...]}`. As listas da resposta vão por número de facto, por isso o mesmo pedido dá sempre a
mesma resposta, e um prefixo dos passos dá os mesmos factos que esse ponto de um pedido mais longo.

## Testes

```bash
swipl -g run_tests -t halt motores/prolog/testes/testes.pl
```

Código de saída 0 se todos passarem. Correm igual em Windows, Mac e Linux, e na GitHub Action
`.github/workflows/prolog.yml` em cada PR e em cada push para `develop` e `main`. Lançam o wrapper como um processo
(como o `PrologAdapter`), com pedidos montados a partir do `cenarios.json`, e verificam:

| Teste | O que verifica |
| --- | --- |
| `ac12_pergunta`, `ac12_resposta` | O cenário 7 dá `aguarda_resposta` com a pergunta; com cada resposta 7b, os factos são os mesmos que o motor dá ao injetar os ficheiros de cenário |
| `ac11_prefixo` | Um prefixo dos passos dá os mesmos factos que esse ponto de um pedido mais longo |
| `ac10_determinismo` | O mesmo pedido dá a mesma resposta, byte a byte |
| `ac3_stdout_so_json` | O `stdout` tem um só documento JSON; as mensagens do motor vão para o `stderr` |
| `ac2_acentos` | Um facto com acentos vai e volta igual |
| `m9_erros` | Pedidos inválidos (versão errada, sem passos, JSON partido, stdin vazio, facto sem predicado, pasta errada) dão `estado: "erro"` e código diferente de zero |
| `ac18_json_em_dia` | `gera_json.pl --verificar`: os JSON gerados estão em dia com os `.txt` da base |
| `cenarios_coerentes` | No `cenarios.json`, as dependências e as alternativas existem, e as sequências respeitam-nas |
| `referencias_em_dia` | `gera_referencias.pl --verificar`: as referências em `referencia_prolog/` são o que o wrapper dá hoje |
| `ac13_meta_em_dia` | `gera_meta.pl --verificar`: o `facto_dispara_regras/2` do `soc_suporte.txt` é o que as regras dão hoje |

Estado: o motor (`soc_motor.pl`, ainda com o menu no mesmo ficheiro), os resultados de referência da v6, o wrapper e os
testes estão aqui.
