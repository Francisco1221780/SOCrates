# ferramentas

Scripts Prolog que geram ficheiros a partir da base de conhecimento. O que sai daqui não se edita à mão.

| Script | Gera |
| --- | --- |
| `gera_meta.pl` | Metaconhecimento (`facto_dispara_regras/2`) a partir de `soc_regras.txt` e do `condicao_le/2` do `soc_suporte.txt`. Voltar a correr sempre que as regras mudam. |
| `gera_json.pl` | `factos_iniciais.json` para `motores/drools/src/main/resources/` e `cenarios.json` para `backend/cyber/src/main/resources/generated/` e, igual, para `motores/drools/src/main/resources/` (o módulo Drools corre os cenários sem o backend); `regras.json` (as regras do `soc_regras.txt` como dados, para o "porque não?" do Drools) para `motores/drools/src/main/resources/`. |
| `gera_referencias.pl` | Os resultados de referência do motor em `motores/prolog/referencia_prolog/<variante>.json`, a correr o `wrapper.pl` sobre cada variante. |

## Regenerar o metaconhecimento (`gera_meta.pl`)

**Quando:** sempre que se muda uma regra do `soc_regras.txt`, ou o `condicao_calculada/1` ou o `condicao_le/2` do
`soc_suporte.txt`. Faz-se commit das regras e do metaconhecimento juntos.

```bash
swipl motores/prolog/ferramentas/gera_meta.pl              # escreve as linhas facto_dispara_regras/2 no stdout
swipl motores/prolog/ferramentas/gera_meta.pl --verificar  # só verifica; código 1 se estiver desatualizado
```

As linhas que o script escreve substituem as `facto_dispara_regras/2` do `soc_suporte.txt`. Também se pode correr à
mão, com o motor e a base carregados: `?- consult('ferramentas/gera_meta.pl'), gera_meta.`

O `--verificar` é o teste do AC13: carrega o motor, as regras e o suporte, calcula o metaconhecimento e compara-o com o
do `soc_suporte.txt`. Compara os termos, não o texto (a ordem e a formatação das linhas não contam). Não escreve nada
no `stdout`; no `stderr` diz o que está mal, por tipo de facto: `Falta`, `A mais`, `Repetido` ou `Desatualizado`
(com as regras que estão e as que as regras dão). Se a base não carregar (ex.: um erro de sintaxe nas regras), também
sai com código 1. Corre na CI em cada PR (teste `ac13_meta_em_dia`) e é o que o health do backend pode usar para
dizer se o metaconhecimento está em dia.

## Regenerar os JSON (`gera_json.pl`)

**Quando:** sempre que se muda o `soc_factos.txt`, um ficheiro de `cenarios/`, o `cenarios/indice.txt` ou o
`soc_cenarios.pl`. Faz-se commit dos `.txt` e dos JSON juntos.

**Como**, a partir da raiz do repositório (Windows com Git Bash ou PowerShell, Mac ou Linux):

```bash
swipl motores/prolog/ferramentas/gera_json.pl              # escreve os JSON (os dois cenarios.json iguais)
swipl motores/prolog/ferramentas/gera_json.pl --verificar  # só verifica; código 1 se algum estiver desatualizado
```

O `--verificar` é o teste do AC18: corre na CI em cada PR (teste `ac18_json_em_dia` em `motores/prolog/testes/`) e
pode correr-se à mão antes de abrir um PR. Não escreve nada e diz que ficheiro está
desatualizado. Os fins de linha não contam (o git pode convertê-los no Windows).

**O que vai em cada ficheiro:**

- `factos_iniciais.json`: `{"versaoKB", "factos"}`, com os factos fixos do `soc_factos.txt` por ordem de número, no
  formato do contrato (`{"predicado", "args"}`, termos compostos como `{"f", "args"}`, tabela da issue #6).
- `regras.json`: `{"versaoKB", "regras", "calculadas"}`. Cada regra é `{"id", "se": [condição...], "entao": [termo...]}`; uma condição é um termo, `{"nao": termo}` ou `{"avalia": termo}`, e uma variável é `{"var": "Nome"}` (cada `_` é `{"var": "_"}`). `calculadas` são as condições do `condicao_calculada/1` do `soc_suporte.txt` (`"depois/3"`, …). Os operadores das regras só se usam ao ler o `soc_regras.txt`.
- `cenarios.json` (as duas cópias são iguais; o `--verificar` vê as duas): `{"versaoKB", "cenarios", "sequencias"}`. Cada cenário tem `id`, `ordem`, `grupo` (`telemetria`,
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
As variantes estão descritas no início do script. O `--verificar` também corre na CI (teste `referencias_em_dia`).
