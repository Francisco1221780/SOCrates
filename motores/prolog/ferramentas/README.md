# ferramentas

Scripts Prolog que geram ficheiros a partir da base de conhecimento. O que sai daqui não se edita à mão.

| Script | Gera |
| --- | --- |
| `gera_meta.pl` | Metaconhecimento (`facto_dispara_regras/2`) a partir de `soc_regras.txt`. Voltar a correr sempre que as regras mudam. |
| `gera_json.pl` | `factos_iniciais.json` para `motores/drools/src/main/resources/` e `cenarios.json` para `backend/cyber/src/main/resources/generated/`. |
| `gera_referencias.pl` | Os resultados de referência do motor em `motores/prolog/referencia_prolog/<variante>.json`, a correr o `wrapper.pl` sobre cada variante. |

## Regenerar os JSON (`gera_json.pl`)

**Quando:** sempre que se muda o `soc_factos.txt`, um ficheiro de `cenarios/`, o `cenarios/indice.txt` ou o
`soc_cenarios.pl`. Faz-se commit dos `.txt` e dos JSON juntos.

**Como**, a partir da raiz do repositório (Windows com Git Bash ou PowerShell, Mac ou Linux):

```bash
swipl motores/prolog/ferramentas/gera_json.pl              # escreve os dois JSON
swipl motores/prolog/ferramentas/gera_json.pl --verificar  # só verifica; código 1 se algum estiver desatualizado
```

O `--verificar` é o teste do AC18: corre-se antes de abrir um PR, sozinho ou com os testes Prolog (teste
`ac18_json_em_dia` em `motores/prolog/testes/`). Não escreve nada e diz que ficheiro está
desatualizado. Os fins de linha não contam (o git pode convertê-los no Windows).

**O que vai em cada ficheiro:**

- `factos_iniciais.json`: `{"versaoKB", "factos"}`, com os factos fixos do `soc_factos.txt` por ordem de número, no
  formato do contrato (`{"predicado", "args"}`, termos compostos como `{"f", "args"}`, tabela da issue #6).
- `cenarios.json`: `{"versaoKB", "cenarios", "sequencias"}`. Cada cenário tem `id`, `ordem`, `grupo` (`telemetria`,
  `atualizacao`, `normal` ou `resposta`), `descricao`, `ficheiro`, `depende`, `alternativas` e o `passo` pronto a
  enviar (`{"id", "factos"}`). As `sequencias` (`base`, `atualizacoes`, `normal`) vêm do `soc_cenarios.pl`.

A `versaoKB` vem do cabeçalho do `soc_factos.txt`. A ordem, o grupo, as dependências e as alternativas vêm do
`cenarios/indice.txt`, tal como ele as descreve:

| Grupo | Dependências |
| --- | --- |
| Telemetria | Correm-se pela ordem da lista: cada cenário depende do anterior que não é alternativa de outro (o 35 depende do 29, não do 29b). |
| Atualizações | "carregam-se depois do cenário 39". |
| Atividade normal | Independentes, salvo "o 34 só faz sentido depois do 4". |
| Respostas | O cenário a que respondem, citado no texto; "(em alternativa ao N)" e "(só UM)" dão as alternativas. |

O id e a descrição de cada cenário vêm do `soc_cenarios.pl`, e os factos de cada passo vêm do ficheiro do cenário. Se
o índice tiver uma linha que o script não percebe, ou não bater certo com a pasta `cenarios/` ou com o
`soc_cenarios.pl`, o script para com erro em vez de gerar dados errados.

## Regenerar as referências (`gera_referencias.pl`)

**Quando:** sempre que muda o resultado do motor: o `soc_motor.pl`, as regras ou os factos da base, ou os cenários
(neste caso, primeiro o `gera_json.pl`, porque os passos vêm do `cenarios.json`). Faz-se commit das referências junto.

```bash
swipl motores/prolog/ferramentas/gera_referencias.pl              # escreve referencia_prolog/<variante>.json
swipl motores/prolog/ferramentas/gera_referencias.pl --verificar  # só verifica; código 1 se alguma estiver desatualizada
```

Corre o `wrapper.pl` sobre cada variante (cerca de 2 s cada) e guarda a resposta JSON com os cenários da variante.
As variantes estão descritas no início do script. O `--verificar` também corre nos testes Prolog (teste `referencias_em_dia`).
