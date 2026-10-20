# F0004 — Consola Drools com o menu do Prolog e o "porque não?"

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 18/10: "põe o menu igual, com o porque não") |
| Branch | `feature/drools-regras-a-g-n` (a mesma da F0002 e da F0003) |
| Estado | Plano aprovado em 2026-10-18 |
| Âmbito | O menu da `ConsolaDrools` passa a ter as mesmas 14 entradas do `menu/0` do `soc_motor.pl` (0–13), com os mesmos textos e o mesmo comportamento, incluindo a 1 (carregar a base), a 10 ("porque não?", como o `whynot/1`) e a 12 (opções do motor). O "porque não?" fica também na API do `MotorDrools`, para o backend o poder usar |
| Verificação | `.\mvnw.cmd verify`; `gera_json.pl --verificar` com o `regras.json` novo; testes do "porque não?" com o texto esperado copiado de corridas do `whynot/1` do Prolog |

---

## 1. Situação

A F0003 deixou a consola com as opções 1–9, 11 e 13 do Prolog, mais uma 14 e uma 15 próprias. Não tem a 10
("porque não?") nem a 12 (opções do motor), e a 1 mostra o estado em vez de carregar a base.

**Evidência**

- `motores/prolog/soc_motor.pl` `menu/0` e `executa/1`: 0–13.
  - A 1 pede o ficheiro da base (`carrega_bc`).
  - A 3 aceita um id de cenário, uma lista de ids ou um ficheiro de cenário (`novo_facto(F).`, `:- novo_facto(F).` ou
    o próprio facto, por linha).
  - Cada injeção corre o motor e escreve `N factos novos (n. X a Y).` ou `Nao ha conclusoes novas.`
    (`arranca_motor/0`).
  - A 12 muda `mostrar_conclusoes` (escreve cada conclusão: `Foi concluido o facto n. N -> F   (regra R)`),
    `passo_a_passo` (espera por ENTER depois de cada uma) e `profundidade_whynot` (4).
- `whynot/1` (`soc_motor.pl` l. 441–565):
  - para cada regra cujo `entao` tem `cria_facto` com um termo que unifica com o facto, percorre o `se` por ordem
    (`encontra_premissas_falsas`). Uma condição que se verifica fica com as variáveis ligadas (a primeira solução);
    uma que falha vai para a lista das falsas. Uma condição que partilha variáveis com uma que já falhou só se testa,
    sem ligar nada;
  - depois explica cada premissa falsa, descendo pelas que outras regras poderiam concluir, até
    `profundidade_whynot`;
  - não repete um facto já visto (`=@=`), e diz quando um facto é um dado de entrada, quando a condição é calculada
    e falsa, ou quando "falta correr o motor".
- O Drools não tem as regras em forma de dados: estão em DRL, compiladas. O `whynot` precisa delas como termos (o
  `se` e o `entao` de cada regra, com variáveis), e de avaliar as condições calculadas e o `avalia/2` com as variáveis
  ligadas.
- `CondicoesCalculadas` (#14 e F0002) já tem as 19 condições calculadas do `soc_suporte.txt`, sobre uma `BaseFactos`.
- `gera_json.pl` já lê os ficheiros da base como termos (`read_term`), sem os carregar. O `gera_meta.pl` declara os
  operadores das regras (`regra`, `se`, `entao`, `e`, `nao`).

**Inventário**

| # | Superfície |
| :--- | :--- |
| A | `regras.json` no módulo Drools: as 92 regras como dados (gerado pelo `gera_json.pl` a partir do `soc_regras.txt`, com o `--verificar`) |
| B | Unificação de termos com variáveis em Java, e avaliação de uma condição (facto, `nao`, `avalia`, calculada) sobre a `BaseFactos` |
| C | `PorqueNao`: o `whynot/1` do Prolog, a devolver a explicação como uma árvore JSON |
| D | `MotorDrools.porqueNao(pedido, facto)`: corre o pedido e explica o facto na mesma sessão |
| E | Menu 0–13 igual ao do Prolog: textos, a 1 (carregar a base), a 3 com ficheiros, a 10, a 12 e as mensagens do `arranca_motor` |
| F | Testes e documentação |

**Escala:** 1 saída nova no `gera_json.pl` e 1 JSON gerado. Em Java: um pacote `explicacao/` com 4 classes, o
`MotorDrools` (1 método público, 1 construtor a partir de um ficheiro de factos iniciais) e a `ConsolaDrools`. Mais 3
classes de teste e os READMEs.

---

## 2. Resultado pretendido

```
========== ANALISTA VIRTUAL DE SOC ==========
1126 factos (633 concluidos pelo motor) | 61 incidentes | 61 tickets: 41 abertos, 20 fechados | 9 perguntas por responder
 1. Carregar a base de conhecimento
 2. Correr o motor
 ...
10. Explicar porque nao se concluiu um facto (porque nao?)
11. Mostrar factos
12. Opcoes do motor
13. Injetar todos os cenarios de uma vez
 0. Sair
Opcao: 10.
Facto (ex.: compromisso_confirmado(web_srv_04,ev_e1)): ataque_dirigido(ev_e1,web_srv_04).
Porque pela regra 13:
     A premissa exploracao_possivel(ev_e1,web_srv_04,_) e falsa
          Porque pela regra 6:
               A premissa vulneravel(web_srv_04_apache,cve_2021_41773) e falsa
          ...
```

(O exemplo do "porque não?" acima é só ilustrativo da forma; o texto real é o que o `whynot/1` der.) Os textos são
os do Prolog, letra a letra, para quem conhece um conhecer o outro. A comparação é pelo texto: os
testes do "porque não?" usam a saída do `whynot/1`.

### Decisões

| Decisão | Valor | Origem |
| :--- | :--- | :--- |
| Âmbito do menu | As 14 entradas do Prolog (0–13), com os mesmos números, textos e comportamento. Saem a 14 e a 15 da F0003: o ficheiro JSON passa para a 3 (que, como no Prolog, aceita um ficheiro) e "recomeçar" é a 1 com Enter | Utilizador (18/10) |
| Arranque | A consola arranca já com a base do módulo carregada, como até aqui (o estado aparece logo). A diferença para o Prolog fica escrita no README: no Prolog é preciso a opção 1 | Pedido anterior do utilizador ("ficar à espera das questões") |
| Opção 1 | Pede `Ficheiro dos factos iniciais (Enter = a base do modulo)`. Com Enter, recomeça com o `factos_iniciais.json` do módulo; com um caminho, carrega esse ficheiro (o mesmo formato). As regras são sempre as do módulo (DRL compilado) | O Drools não carrega regras de um ficheiro de texto |
| Opção 2 | Volta a correr o pedido e escreve como o `arranca_motor`: `N factos novos (n. X a Y).` ou `Nao ha conclusoes novas.` | `soc_motor.pl` |
| Opção 3 | Um id, uma lista (`[c1,c2]` ou `c1 c2`) ou um ficheiro: `.txt` no formato dos cenários Prolog (`:- novo_facto(F).`, `novo_facto(F).` ou `F.`, por linha; `%` é comentário), ou `.json` com um pedido. Escreve `=== Cenario c6: <descricao>` antes de cada um, como o Prolog. Mantém a recusa de repetidos e alternativos da F0003 | `injeta_cenario/1`, `injeta_ficheiro/1` |
| Opção 12 | `mostrar_conclusoes` (por omissão `nao`): escreve `Foi concluido o facto n. N -> F   (regra R)` para cada conclusão nova. `passo_a_passo` (por omissão `nao`): espera por ENTER depois de cada uma. `profundidade_whynot` (por omissão 4) | `opcao/2` do Prolog |
| Regras como dados | O `gera_json.pl` escreve `motores/drools/src/main/resources/regras.json`: `{"versaoKB", "regras": [{"id", "se": [condição...], "entao": [termo...]}], "calculadas": ["depois/3", ...]}`. Uma condição é um termo, `{"nao": termo}` ou `{"avalia": termo}`. Uma variável é `{"var": "Ev"}`; cada `_` é `{"var": "_"}`, uma variável nova de cada vez. As variáveis vêm do `read_term(..., [variable_names(...)])` | Formato novo; os termos seguem a tabela da #6 |
| Onde vive o "porque não?" | No módulo, pacote `explicacao/`, chamado por `MotorDrools.porqueNao(String pedido, String facto)`. O método corre o pedido e explica o facto sobre a mesma `BaseFactos`, para as condições calculadas usarem o `CondicoesCalculadas`. Devolve JSON (uma árvore de nós com o tipo e os termos), e a consola escreve-a com os textos do Prolog. O backend pode expô-lo mais tarde | Decisão de implementação |
| Unificação | Termos com variáveis em Java, sobre o modelo (`String`, `Long`, `Termo`, listas). Uma condição de facto procura o primeiro facto, por número, que unifica (a primeira solução do `facto(N, X)`). Um `nao` é verdadeiro se nenhum unifica. O `avalia(F(E,Op,V))` procura `F(E,V1)` e compara. Uma calculada liga os argumentos e chama o `CondicoesCalculadas`. Com um argumento por ligar, é falsa (como o `chama_calculada`), menos o último da `origem_evidencia`, que se calcula e liga | `verifica_condicao/2` do Prolog |
| Facto da opção 10 | Pode ter variáveis (`ataque_dirigido(Ev,web_srv_04)`), como no Prolog. O `TermoTexto` passa a aceitar variáveis só nesta opção | `whynot/1` |

### Alternativas rejeitadas

- **Tirar as regras do DRL** (ler a `KieBase`). As condições DRL não têm a forma das do Prolog (as marcas, os `eval`,
  o `from`), e o "porque não?" tem de raciocinar sobre o `se` da base, não sobre a tradução.
- **Pôr o "porque não?" só na consola**, com uma `BaseFactos` montada à parte a partir da resposta. Seria preciso
  copiar a base para outra sessão, e o backend não o podia usar.
- **Correr o `whynot/1` do Prolog a partir do Drools.** O módulo Drools tem de se bastar a si próprio.

---

## 3. Implementação

**Fase 1 — `regras.json` (A)**
- `gera_json.pl`:
  - os operadores das regras;
  - ler o `soc_regras.txt` com `variable_names`;
  - converter as regras para JSON;
  - a lista das condições calculadas (do `soc_suporte.txt`);
  - uma saída nova, também verificada pelo `--verificar`.
- `motores/drools/src/main/resources/regras.json` (gerado).
- `motores/prolog/ferramentas/README.md`.

**Fase 2 — Unificação e condições (B)**
- `explicacao/Var.java`, `explicacao/Unificacao.java`, `explicacao/Regras.java` (novos): ler o `regras.json`,
  unificar e avaliar uma condição sobre a `BaseFactos`.
- Testes `UnificacaoTest`: unificar com termos compostos e listas; `_` sempre nova; variante (`=@=`); uma calculada
  com argumento por ligar é falsa; a `origem_evidencia` liga o último argumento.

**Fase 3 — `PorqueNao` e API (C, D)**
- `explicacao/PorqueNao.java` (novo): o `whynot/2`, ramo a ramo, com a profundidade e o "já visto".
- `MotorDrools.porqueNao(String pedido, String facto, int profundidade)` e o construtor a partir de um ficheiro de
  factos iniciais (para a opção 1). Também `FactosIniciais.carrega(Path, ...)`.
- Testes `PorqueNaoTest`: 5 factos sobre a sequência `base`, com o texto esperado copiado do `whynot/1` (corrida de
  18/10), escolhidos para cobrir os ramos:
  - um facto que é verdadeiro ("nao e falso");
  - um dado de entrada;
  - uma calculada falsa;
  - uma premissa `nao` falsa;
  - a recursão com "ja explicado acima" e o corte pela profundidade.

  Escolhem-se factos cuja explicação não passa pelas causas H–M do `PENDENTES_H_M.md`. Se algum passar, fica
  anotado.

**Fase 4 — Menu (E)**
- `ConsolaDrools`: as 14 entradas, os textos do Prolog, a 1, a 3 com ficheiros, a 10, a 12 e as mensagens do
  `arranca_motor`.
- `TermoTexto`: variáveis permitidas quando se pede (opção 10); leitura de um ficheiro de cenário Prolog.
- `ConsolaDroolsTest` atualizado (a 14 e a 15 saem; testes para a 1, a 3 com um `.txt` e um `.json`, a 10 e a 12).

**Fase 5 — Documentação (F)**
- `motores/drools/README.md` (tabela das opções), `README.md` da raiz (a menção a "porque não?").

---

## 4. Verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify
swipl motores/prolog/ferramentas/gera_json.pl --verificar
swipl -g run_tests -t halt motores/prolog/testes/testes.pl
```

- **Baseline:** o fim da F0003: `BUILD SUCCESS` com 67 + 2 testes; os `--verificar` a 0; 11 testes Prolog.
- **Passa quando:**
  - há `BUILD SUCCESS` e os testes novos estão verdes;
  - o `regras.json` tem 92 regras;
  - os textos do "porque não?" são iguais aos do Prolog nos 5 casos;
  - o `ac18_json_em_dia` do Prolog continua a passar.
- **À mão:** o menu no IntelliJ, com a 10 e a 12.

---

## 5. Riscos

| Risco | Como se valida que não aconteceu |
| :--- | :--- |
| **A ordem das soluções muda a explicação.** O Prolog liga a primeira solução de cada condição, e uma ordem diferente dá outras premissas falsas | A procura vai por número de facto, como o `facto(N, X)`, e os testes comparam o texto com o do Prolog |
| **As causas H–M (R43, R66, …) mudam a base** e o "porque não?" difere do Prolog por isso, não por um erro desta alteração | Escolher os factos dos testes fora dessas cadeias. Se um teste falhar por elas, fica escrito no RESULT, ligado ao `PENDENTES_H_M.md` |
| **Uma calculada no Java comporta-se diferente do Prolog com argumentos meio ligados** | No Prolog uma calculada com variáveis por ligar dá `instantiation_error` e conta como falsa; faz-se igual, e há um teste para isso |
| **O `regras.json` deixa de bater com as regras** | Gerado e verificado pelo `--verificar`, que corre na CI (`ac18_json_em_dia`) |
| **Mexer outra vez no `gera_json.pl`** (lado Prolog) | Só se acrescenta uma saída; as outras ficam iguais (o `--verificar` compara-as) |
| **O "porque não?" é lento** (percorre a base por cada condição, com recursão) | Profundidade 4 por omissão, como no Prolog; medir no teste |

---

## 6. Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | `build(kb): generate the rules as data (regras.json) for the Drools module` | A |
| 2 | `feat(drools): add term unification and condition checks over the session facts` | B |
| 3 | `feat(drools): add why-not explanations (whynot) to the Drools engine` | C, D |
| 4 | `feat(drools): make the console menu match the Prolog menu` | E |
| 5 | `docs(drools): document the Prolog-like menu and why-not` | F |

---

## 7. Fora do âmbito / a quem passa

| Fica de fora | Para |
| :--- | :--- |
| Expor o "porque não?" no backend (`/api/...`) | #12 / UI (P6), se quiserem |
| Carregar regras de um ficheiro na opção 1 | Não se faz: as regras do Drools são o DRL do módulo |
| Corrigir as diferenças que vêm das regras H–M | `PENDENTES_H_M.md` (#15) |
