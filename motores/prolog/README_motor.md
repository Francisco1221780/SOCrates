# Motor de inferência do analista virtual de SOC

`soc_motor.pl` é o motor de inferência em SWI-Prolog que corre a base de conhecimento v5.1 (`soc_factos.txt`, `soc_regras.txt`, `soc_suporte.txt`). Parte do `sp_exp2.pl` da UC e acrescenta o que a v5.1 exige (alterações M1 a M4 da issue #3), a injeção dos ficheiros de cenário, explicações melhoradas e um menu de texto.

> **Base v6.** A base em `base_conhecimento/` é a v6 (314 factos, 92 regras). A v6 pede que uma regra seja reavaliada
> quando chega um facto lido por uma condição calculada (`condicao_le/2`); o motor faz essa reavaliação (ver a tabela
> "O que mudou em relação ao `sp_exp2.pl`"). Os resultados de referência da v6 estão abaixo e em
> [`referencia_prolog/`](referencia_prolog/).

## Ficheiros e onde ficam

O motor fica em `motores/prolog/` e a base de conhecimento em `base_conhecimento/`, na raiz do repositório:

```
.
├── motores/
│   └── prolog/
│       ├── soc_motor.pl        ← o motor
│       ├── README_motor.md     ← este ficheiro
│       └── referencia_prolog/  ← resultados de referência do motor, um ficheiro por variante
└── base_conhecimento/
    ├── soc_factos.txt      ← os factos fixos; é o ficheiro que se carrega
    ├── soc_regras.txt      ← as regras (carregado pelo soc_factos.txt)
    ├── soc_suporte.txt     ← metaconhecimento e condições calculadas (carregado pelo soc_factos.txt)
    ├── soc_cenarios.pl     ← liga cada nome de cenário (c1, c2, …) ao seu ficheiro (carregado pelo motor)
    └── cenarios/
        ├── indice.txt      ← a ordem dos cenários e o que cada um testa (só para pessoas)
        ├── cenario_01.txt
        ├── …
        └── cenario_39.txt
```

- O motor não depende da pasta onde está: o `carrega_bc` guarda a pasta do `soc_factos.txt` e procura lá tudo o resto.
- Os ficheiros da base têm de ficar juntos em `base_conhecimento/`, porque o `soc_factos.txt` carrega o `soc_regras.txt` e o `soc_suporte.txt` a partir da sua própria pasta.
- O `soc_cenarios.pl` tem de estar ao lado do `soc_factos.txt`: o `carrega_bc` carrega-o sozinho a partir daí. Sem ele, os cenários só se injetam pelo nome do ficheiro (`injeta_ficheiro/1`).
- A pasta `cenarios/` também fica ao lado do `soc_factos.txt`. Os caminhos dos cenários são relativos a essa pasta, por isso funcionam seja qual for a pasta de trabalho do Prolog.
- O metaconhecimento do `soc_suporte.txt` (`facto_dispara_regras/2`) é gerado pelo `ferramentas/gera_meta.pl`. Não é preciso para correr o motor, só para o regenerar quando as regras mudam.

## Começar

Abre o SWI-Prolog na pasta `motores/prolog/` (por exemplo, `cd motores/prolog && swipl`). No Mac, se abrires a aplicação ou fizeres duplo clique num ficheiro, a pasta de trabalho não é essa: muda primeiro de pasta.

```prolog
?- working_directory(_, '/caminho/para/SOCrates/motores/prolog/').
?- consult('soc_motor.pl').
?- carrega_bc('../../base_conhecimento/soc_factos.txt').
Base carregada: 314 factos e 92 regras.
?- arranca_motor.
29 factos novos (n. 315 a 343).
```

O caminho dado ao `carrega_bc` é relativo à pasta de trabalho do Prolog. Também se pode dar o caminho absoluto, e então a pasta de trabalho não interessa. Escrever só `'soc_factos.txt'` a partir de `motores/prolog/` dá o erro "does not exist".

O primeiro `arranca_motor` tira as conclusões que só dependem dos dados fixos: a classe de cada ativo, e que instalações são vulneráveis ou não afetadas. Depois injetam-se os cenários.

Há duas formas de usar o motor: os comandos abaixo, ou o menu de texto (`?- menu.`), que faz o mesmo através de opções numeradas.

## Injetar cenários

Cada cenário é a telemetria que chega num momento (alertas, eventos dos hosts, logins, tráfego) ou uma resposta do analista. Injetar um cenário acrescenta os seus factos e corre o motor até não haver conclusões novas.

| Comando | O que faz |
| --- | --- |
| `injeta_cenario(c2).` | Injeta um cenário pelo nome e corre o motor. |
| `injeta_cenarios([c1,c2,c3]).` | Vários cenários, por ordem, com o motor a correr entre cada um. |
| `injeta_todos.` | Recomeça do estado inicial (recarrega a base) e injeta os cenários da sequência base (1 a 29b e 35 a 39) e os de atividade normal 30 a 34, por ordem. No fim mostra o estado. |
| `injeta_cenario('cenarios/cenario_30.txt').` ou `injeta_ficheiro(...)` | Injeta um ficheiro pelo nome. Um caminho relativo procura-se primeiro na pasta de trabalho e depois na pasta do `soc_factos.txt`. |
| `lista_cenarios.` | Mostra os nomes dos cenários e o que cada um faz. |

A ordem importa, porque os cenários contam uma história seguida. Por exemplo, o 2 só dá "ataque dirigido" porque a mesma origem já atacou no 1, e o 3 precisa do 2. A ordem é a do `cenarios/indice.txt`.

O `injeta_todos` não injeta as respostas do analista (7b, 19b, 3b, 10d, 17b, 29c), porque algumas são alternativas umas às outras. Injetam-se depois, uma de cada vez:

```prolog
?- injeta_todos.
947 factos (520 concluidos pelo motor) | 50 incidentes | 50 tickets: 36 abertos, 14 fechados | 9 perguntas por responder
?- injeta_cenario(c7b_nao).
```

Nos cenários 7b e 19b usa-se só uma das três respostas (`_sim`, `_nao` ou `_nao_sei`). O 29c, o 35d e o 36c usam-se em vez do 29b, do 35c e do 36b.

### Factos à mão

Para acrescentar telemetria que não está num cenário:

```prolog
?- introduz_facto(alerta_ids(ev_99,web_srv_02,sig_apache_path_traversal)).
?- introduz_factos([origem(ev_99,ip_203_0_113_1), origem_zona(ev_99,internet), tempo(ev_99,20000)]).
?- arranca_motor.
```

O `introduz_facto` dá ao facto o número seguinte e não o acrescenta se já existir.

## Ver os resultados

| Comando | O que mostra |
| --- | --- |
| `estado.` | Resumo: factos, quantos foram concluídos pelo motor, incidentes, tickets abertos e fechados, perguntas por responder. |
| `mostra_factos.` | Todos os factos da memória de trabalho. |
| `mostra_conclusoes.` | Só os factos concluídos pelas regras, com a regra que os concluiu. |
| `mostra_factos(incidente).` | Só os factos com esse nome (`ticket`, `acao`, `pergunta_analista`, …). |
| `mostra_incidentes.` | Tickets abertos por prioridade (p1 primeiro), com a ação pedida e o motivo, e os tickets fechados. |
| `mostra_acoes.` | Ações recomendadas por ativo (isolar, bloquear IP, revogar credenciais…), com o significado de cada uma. |
| `mostra_regra(13).` | O SE e o ENTÃO de uma regra. |
| `facto(N, F).` / `justifica(N, Regra, Premissas).` | Um facto pelo número, e a regra e as premissas que o deram. |

Os factos concluídos só existem na memória do Prolog: o motor não escreve em nenhum ficheiro, e o `soc_factos.txt` nunca é alterado. Para os guardar num ficheiro:

```prolog
?- tell('resultado.txt'), mostra_factos, told.
```

## Explicações

**Como se concluiu um facto** (`como`), pelo número ou pelo próprio facto:

```prolog
?- como(compromisso_confirmado(web_srv_01,ev_a1)).
facto 325: compromisso_confirmado(web_srv_01,ev_a1)
   concluido pela regra 25, porque:
    facto 324: impacto_confirmado(web_srv_01,ev_a1,execucao_remota)
       concluido pela regra 20, porque:
       ...
```

Mostra a árvore inteira, até aos dados. Um facto que já foi explicado na mesma árvore aparece como `(ja explicado acima)` em vez de se repetir. O `como1(F)` mostra só o primeiro nível: a regra e as premissas.

**Porque não se concluiu um facto** (`whynot`):

```prolog
?- whynot(ataque_dirigido(ev_e1,web_srv_04)).
Porque pela regra 13:
     A premissa origem_hostil(ip_198_51_100_23,A) e falsa
          ...
```

Percorre todas as regras que podiam concluir o facto e, em cada uma, as premissas que falham, descendo pelas que outras regras poderiam concluir. Para numa premissa que é um dado de entrada (nenhuma regra a conclui), numa condição calculada falsa, ou ao chegar à profundidade máxima. Para ver mais fundo: `define_opcao(profundidade_whynot, 6).`

A explicação monta-se primeiro como uma lista de linhas, `whynot_linhas(Facto, Profundidade, Linhas)`, e o `whynot/1` escreve-as. Cada linha é `linha(Nivel, Tipo, Campos)`, com os mesmos tipos do `PorqueNao` do Drools; é o que o wrapper dá em JSON no modo `whynot` (ver `README.md`).

## Perguntas ao analista

O motor nunca fica parado à espera de uma resposta: as perguntas são factos.

1. Quando precisa da confirmação de um analista (sensor do host em baixo, ou compromisso provável), uma regra conclui `pergunta_analista(Ativo, Evento, Pergunta)`. O motor continua até ao fim.
2. A resposta entra como um facto, `resposta_analista(Ativo, Evento, Pergunta, sim|nao|nao_sei)`, e corre-se o motor outra vez. As regras seguem a partir daí.
3. Se a pergunta deixar de fazer sentido (o ticket fechou por outra via), as regras concluem `pergunta_retirada(...)`.

Para ver as perguntas por responder: `perguntas_pendentes(L).` Uma resposta "não sei" pode ser substituída mais tarde, quando o analista conseguir verificar.

No menu (opção 5), cada pergunta mostra o texto e as opções de resposta da base. O analista também pode responder `porque.`, e o motor mostra a regra que gerou a pergunta, as premissas que a levaram a ser feita e as regras que vão usar a resposta.

Com o backend e a UI é o mesmo fluxo: a API lê as perguntas pendentes, mostra-as, e quando chega a resposta faz `introduz_facto(resposta_analista(...))` e corre o motor.

## Menu

`?- menu.` As respostas são termos Prolog terminados com ponto (`1.`, `c2.`, `nao.`).

| Opção | |
| --- | --- |
| 1 | Carregar a base de conhecimento |
| 2 | Correr o motor |
| 3 | Injetar um cenário (pelo nome, uma lista, ou o nome de um ficheiro) |
| 4 | Introduzir factos à mão e correr o motor |
| 5 | Responder às perguntas ao analista (com "porque?") |
| 6 | Incidentes e tickets |
| 7 | Ações recomendadas |
| 8 | Registar a recuperação de uma máquina (`recuperado(Ativo, Evento)`) |
| 9 | Explicar como se concluiu um facto |
| 10 | Explicar porque não se concluiu um facto |
| 11 | Mostrar factos (todos, só os concluídos, ou por nome) |
| 12 | Opções do motor |
| 13 | Injetar todos os cenários de uma vez |
| 0 | Sair |

## Opções

Mudam-se com `define_opcao(Nome, Valor).` ou pela opção 12 do menu.

| Opção | Por omissão | |
| --- | --- | --- |
| `mostrar_conclusoes` | `nao` | Escreve cada facto concluído, com a regra (`Foi concluido o facto n. 307 -> ... (regra 10)`). Útil para seguir o encadeamento. |
| `passo_a_passo` | `nao` | Espera por ENTER depois de cada conclusão, como o `get0` do `sp_exp2.pl`. Útil para apresentar. |
| `profundidade_whynot` | `4` | Quantos níveis o `whynot` desce. |

## Como funciona

É um motor de **encadeamento direto**: parte dos factos e aplica as regras para tirar conclusões novas, até não haver mais nenhuma.

```
arranca_motor
  └─ passagens                  percorre os factos por número, incluindo os que vai criando,
                                e repete enquanto houver conclusões novas (ponto fixo)
       └─ processa_facto(N, F)  o metaconhecimento (facto_dispara_regras/2) diz que regras o facto F pode disparar
            └─ dispara_regra    para cada regra: todas as formas de verificar o SE em que entra o facto N
                 ├─ verifica_condicoes   cada condição do SE é um facto, uma negação (nao X),
                 │                       uma comparação (avalia) ou uma condição calculada
                 └─ concluir            para cada cria_facto(F) do ENTÃO...
                      └─ cria_facto     ...acrescenta o facto com o número seguinte e guarda a justificação
                                        (justifica/3, usada pelo como); não repete factos que já existem
```

### O que mudou em relação ao `sp_exp2.pl`

| | `sp_exp2.pl` | `soc_motor.pl` |
| --- | --- | --- |
| **M1** Condições calculadas | Só factos, negações e `avalia`. | Uma condição `C` com `condicao_calculada(C)` avalia-se com `call(C)`: `depois/3`, `versao_cmp/3`, `anterior/2`, … (definidas em `soc_suporte.txt`). |
| **M2** Instanciações | Cada regra dispara só com a primeira combinação de factos. | Dispara para todas as combinações em que entra o facto que a acionou (`forall`). |
| Reavaliação por `condicao_le/2` (v6) | Uma regra só dispara a partir de um facto que seja uma das suas premissas. | Quando chega um facto que uma condição calculada da regra lê (por exemplo o `tempo` de um evento, lido por `depois/3`), a regra avalia-se por inteiro, sem exigir que esse facto seja uma premissa. Sem `condicao_le/2` na base (bases da UC), nada muda. |
| **M3** Ponto fixo | O `arranca_motor` tinha de ser chamado várias vezes. | Um `arranca_motor` chega: repete as passagens até não haver conclusões novas. |
| **M4** `cria_facto` | Escreve cada conclusão e espera por uma tecla (`get0`). | Não escreve nem espera; as duas coisas são opções, desligadas por omissão. |
| `como` | Repete as mesmas sub-árvores (as conclusões do fim da cadeia davam centenas de linhas). | Árvore sem repetições, e aceita o facto em vez do número. |
| `whynot` | Uma condição calculada dava valores inventados a uma premissa que já tinha falhado, e "não está definido na base" nunca aparecia. | Trata as condições calculadas, não repete o que já explicou, distingue dados de entrada, e tem profundidade máxima. |
| Cenários | — | `injeta_cenario`, `injeta_ficheiro`, `injeta_todos`. |
| Interface | — | `menu/0`, perguntas ao analista com "porque?", incidentes, tickets, ações. |

O motor continua a funcionar com as bases da UC (por exemplo, `veiculos2.txt`). Numa base sem metaconhecimento, cada facto tenta todas as regras.

## Resultados de referência

Com a base v6:

| Sequência | Resultado |
| --- | --- |
| Estado inicial + sequência base (cenários 1 a 29b e 35 a 39), sem respostas | 1110 factos, 61 incidentes, 61 tickets (41 abertos, 20 fechados) |
| `injeta_todos` (sequência base + 30 a 34) | 1126 factos, com as mesmas 633 conclusões: os cenários 30 a 34 não acrescentam nenhuma |
| Sequência base + respostas 3b, 10d e 17b | 1136 factos, 63 incidentes, 40 tickets abertos, 23 fechados, e o incidente "propagação" com dois ativos |

Estes valores foram confirmados com a organização atual das pastas (motor em `motores/prolog/`, base em `base_conhecimento/`). As outras variantes (respostas 7b e 19b, alternativas 29c, 35d e 36c, e as atualizações 06b, 13b, 21b e 27b) estão em [`referencia_prolog/`](referencia_prolog/), com um ficheiro JSON por variante (`base.json`, `respostas_3b_10d_17b.json`, …), gerado pelo `ferramentas/gera_referencias.pl`. Cada ficheiro tem os cenários da variante e a resposta do `wrapper.pl`, no formato do contrato JSON. Servem de referência para o Drools e para o comparador.

## Notas

- **Carregar o mesmo cenário duas vezes com `consult`** repete os factos, porque o `novo_facto/1` do `soc_suporte.txt` não verifica se o facto já existe. Pelo motor (`injeta_cenario`, `injeta_ficheiro`, `injeta_todos`) isto não acontece.
- **O `carrega_bc` recomeça do zero**: apaga os factos da execução anterior, os concluídos e os injetados.
- **As mensagens estão sem acentos**, como a base, para não haver problemas de codificação.
