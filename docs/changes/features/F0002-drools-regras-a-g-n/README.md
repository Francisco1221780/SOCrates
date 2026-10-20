# F0002 — Drools: regras dos grupos A a G e N

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | [#13](https://github.com/Francisco1221780/SOCrates/issues/13) |
| Branch | `feature/drools-regras-a-g-n`, a partir de `feature/drools-issues-14-15` @ `845bcb9` (PR #31, ainda aberto). Faz-se rebase para `develop` quando o #31 entrar |
| Estado | Plano aprovado em 2026-10-18, com a paridade também para as 92 regras (pedido do utilizador) |
| Âmbito | As 37 regras que faltam dos grupos A a G e N (R1–R34 sem a R7, que já existe, e R89–R92) em DRL, com justificação igual à do Prolog. Inclui a `origem_evidencia` e a reavaliação das condições calculadas quando chega um facto que elas leem (`condicao_le/2`), e uma referência Prolog só com estes grupos para provar a paridade |
| Verificação | `.\mvnw.cmd verify` com JDK 25; `ParidadeTest` compara o Drools com o Prolog em 10 variantes de cenários (conteúdo, regra, premissas e passo de cada facto) a dois níveis: só as regras A–G+N (referência nova) e as 92 regras (`referencia_prolog/`) |

---

## 1. Situação

O motor Drools já tem a infraestrutura (F0001): factos genéricos, a `BaseFactos` com numeração e justificação, a
resposta no formato do `wrapper.pl` e a R7 como regra molde. O PR #31 (#14 e #15, Francisco) junta as condições
calculadas em Java e as regras H a M, mas ainda não entrou em `develop`. Dos grupos A a G e N só existe a R7.

**Evidência**

- `base_conhecimento/soc_regras.txt`: grupos A (R1–R3), B (R4–R9), C (R10–R13), D (R14–R15), E (R16–R19),
  F (R20–R30), G (R31–R34) e N (R89–R92). São 38 regras; a R7 já está em `regras/B_vetor.drl`.
- `motores/drools/src/main/resources/regras/README.md`: um ficheiro DRL por grupo (`A_afetado.drl`,
  `C_sem_impacto.drl`, `D_duvida.drl`, `E_impactos.drl`, `F_confirmacao.drl`, `G_pergunta.drl`, `N_catalogo.drl`)
  e o molde da R7: um padrão por condição, `base.cria(kcontext, n, premissas, facto)`, uma premissa por condição.
- PR #31, `sessao/CondicoesCalculadas.java`: métodos estáticos chamados com `eval(...)`, que percorrem
  `base.factos()`. Tem as condições de que A a G precisam, **menos a `origem_evidencia/5`** (R20–R23). Na R43 o
  PR põe a `origem_evidencia` nas premissas mas não a verifica.
- `soc_suporte.txt` `condicao_le/2` e `soc_motor.pl` `dispara_regra/3`: quando chega um facto que uma condição
  calculada lê (ex.: o `tempo` de um evento que chega depois do evento), o Prolog reavalia a regra inteira. Um
  `eval` no Drools só se reavalia quando muda um padrão da própria regra. Por isso, sem mais nada, um `tempo` ou um
  `destino` atrasados nunca disparam a R20 nem a R5 (cenários 37b e 38b).
- `soc_motor.pl`: a negação `nao X` avalia-se quando a regra dispara, e uma conclusão nunca se retira. As regras A
  a G com `nao` sobre factos deduzidos são R20–R23, R31 e R32 (`nao impacto_excluido`, da R18), e R13 e R30
  (`nao revisto`, do grupo L).
- Experiência feita a 18/10: com uma cópia de `base_conhecimento/` cujo `soc_regras.txt` só tem as regras 1–34 e
  89–92, o `wrapper.pl` corre a sequência `base` em ~6 s e dá 167 conclusões (31 regras diferentes disparam; as
  R32–R34 só disparam com as respostas 7b/19b, e as R89–R92 com nenhum cenário, porque o catálogo está completo).
- `motores/prolog/ferramentas/gera_referencias.pl`: as 10 variantes de cenários (`base`, `base_e_atualizacoes`,
  `r7b_sim_19b_sim`, …) e a forma de correr o `wrapper.pl` num processo.
- Baseline em `845bcb9`: `.\mvnw.cmd -pl motores/drools test` → `BUILD SUCCESS`, 20 testes.

**Inventário**

| # | Superfície |
| :--- | :--- |
| A | Regras R1–R3 (`A_afetado.drl`) |
| B | Regras R4–R6, R8, R9 (`B_vetor.drl`, junto da R7) |
| C | Regras R10–R13 (`C_sem_impacto.drl`) |
| D | Regras R14–R15 (`D_duvida.drl`) |
| E | Regras R16–R19 (`E_impactos.drl`) |
| F | Regras R20–R30 (`F_confirmacao.drl`) |
| G | Regras R31–R34 (`G_pergunta.drl`) |
| N | Regras R89–R92 (`N_catalogo.drl`) |
| O | `origem_evidencia/5` em `CondicoesCalculadas`, igual ao `classifica_origem` do `soc_suporte.txt` |
| P | Reavaliação quando chega um facto que uma condição calculada lê (`condicao_le/2`) |
| Q | Referência Prolog só com A–G+N: script gerador e 10 JSON de referência |
| R | Teste de paridade (`ParidadeAGNTest`) e testes de N |
| S | Tabela dos factos que passam entre A–G e H–M (o "combinar com quem faz H a M" da issue) e READMEs |

**Escala:** 37 regras em 8 ficheiros DRL (1 já existe). Em Java: 1 classe nova (`Marca`) e 3 alteradas
(`CondicoesCalculadas`, `BaseFactos`, `MotorDrools`). Mais 1 script Prolog, 10 JSON de referência, 2 classes de
teste e 1 fixture.

---

## 2. Resultado pretendido

Um pedido com os cenários corre no Drools e, **se só estiverem ativas as regras A–G e N**, dá os mesmos factos que o
`wrapper.pl` com essas mesmas regras. Para cada facto concluído batem certo o conteúdo, o número da regra, as
premissas (comparadas pelo conteúdo dos factos a que apontam, não pelos números) e o passo em que apareceu.

### Como se prova (o teste de paridade)

1. Um script Prolog (`motores/drools/ferramentas/gera_referencia_agn.pl`) faz uma cópia temporária de
   `base_conhecimento/` com o `soc_regras.txt` filtrado para as regras 1–34 e 89–92. Depois corre o `wrapper.pl`
   sobre as mesmas 10 variantes do `gera_referencias.pl` e guarda as respostas em
   `motores/drools/src/test/resources/referencia_agn/<variante>.json`, no formato da `referencia_prolog/`.
2. O `ParidadeTest` (JUnit, corre em cada `mvnw verify`) lê cada JSON e envia ao `MotorDrools` o mesmo pedido,
   com os passos do `cenarios.json`. Compara a resposta com a referência facto a facto e, se falhar, lista as
   diferenças. Corre a dois níveis, cada um nas 10 variantes:
   - **A–G+N**: o motor corre só com as regras A–G e N ativas (filtro pelo nome da regra), contra
     `referencia_agn/`. É o "feito quando" desta issue.
   - **Todas as regras**: o motor corre com as 92 regras (A–G e N daqui, H–M do #31), contra a
     `motores/prolog/referencia_prolog/` que já existe. Pedido do utilizador na aprovação do plano.
3. O build **não chama o `swipl`**: o JSON vai no repositório. Só se corre o script quando mudam as regras, os
   factos ou os cenários. Tem `--verificar` (código 1 se a referência estiver desatualizada), como os outros
   geradores.

Uma diferença ao nível "todas as regras" cuja causa esteja numa regra H–M (o primeiro facto diferente vem de uma
regra 35–88) não se corrige aqui. Fica numa lista explícita no teste, por regra e com a razão, e vai para a #15
pelo RESULT §2. Uma diferença causada por uma regra A–G ou N corrige-se nesta alteração.

### Decisões

| Decisão | Valor | Origem |
| :--- | :--- | :--- |
| Condições calculadas | As do `CondicoesCalculadas` do PR #31; a branch parte dele | Utilizador (18/10) |
| `origem_evidencia/5` | Novo `CondicoesCalculadas.origemEvidencia(evHost, evAtaque, ativo, vuln, base)`, que devolve o termo (`"ligada"`, `de_outro_processo(programa(P))`, `por_confirmar(sem_processo)`, …) como o `classifica_origem`. No DRL usa-se com `from` para ligar a variável (R21: `Termo(f == "de_outro_processo", $qual : args[0]) from ...`) | `soc_suporte.txt` |
| Reavaliação (`condicao_le/2`) | Um objeto `Marca(functor)` na sessão por cada functor que o `condicao_le` lista (`tempo/2`, `janela/2`, `destino/3`, `intervalo_afetado/3`, `nao_afetado/2`, `vetor_aberto/4`, `vulneravel/2`, `evento_processo/2`, …). A `BaseFactos` faz `update` à marca quando entra um facto desse functor. Uma regra com uma condição calculada tem um padrão `Marca(functor == ...)` por cada facto que a condição lê, e por isso volta a avaliar-se por inteiro quando um deles chega, como o `dispara_regra/3` | `soc_motor.pl`, `soc_suporte.txt` |
| Premissas | Uma por condição do `se`, pela ordem; as `Marca` não entram. Uma condição calculada entra com os argumentos já ligados (ex.: `origem_evidencia(ev_x, ev_a, alvo, vuln, de_outro_processo(programa(sshd)))`) | `soc_motor.pl` `verifica_condicoes/2` |
| Ordem de disparo | O Drools fica com a sua ordem (sem `salience`). Só se acrescenta `salience` a uma regra se o teste de paridade mostrar uma diferença causada pela ordem, e cada caso fica escrito no comentário da regra e no RESULT | Notas da issue |
| Diferenças que fiquem | O teste não as ignora em silêncio. Uma diferença que não se resolva fica numa lista explícita no teste, com a razão, e vai para o RESULT §2 | Regra de não enfraquecer testes |
| Correr só A–G+N | `MotorDrools(String recursoFactos, Predicate<String> regras)`, package-private: passa um `AgendaFilter` ao `fireAllRules`. O construtor público não muda | Decisão de implementação |
| Grupo N | Nenhum cenário o dispara (o catálogo está completo). Testa-se com uma fixture `factos_iniciais_catalogo.json`: um impacto sem evidência, uma evidência sem pergunta, um impacto sem classe e uma classe sem tipo provável | Experiência de 18/10 |
| Ficheiros | Um por grupo, com os nomes do `regras/README.md`; regras pela ordem do número, cada uma com o texto Prolog em comentário (molde da R7) | `regras/README.md` |

### Alternativas rejeitadas

- **Escrever cada condição calculada como padrões DRL** (`not`, `exists`, `accumulate`). O Drools ficava reativo
  sem marcas, mas cada condição passava a existir três vezes (Prolog, Java do #14 e DRL), e a semântica do
  `forall`/`once` do Prolog ficava espalhada pelas regras.
- **Pôr nas regras um padrão para cada facto lido** (ex.: `Facto(functor == "tempo/2", arg(0) == $ev)`). Funciona
  para o `depois`, mas não para as condições que podem ser verdadeiras sem o facto (o `porto_compativel` sem
  `destino`), e multiplica as ativações.
- **Emular a ordem do Prolog** (percorrer os factos por número e disparar à mão). Dava a paridade por construção,
  mas deixava de ser um motor Drools.
- **Comparar só com a `referencia_prolog/` completa** (92 regras). Com as regras H–M do #31 por rever, uma
  diferença não diria de que metade vem. Por isso há os dois níveis: o A–G+N isola esta metade e o completo mostra
  o estado das duas juntas.

---

## 3. Implementação

Por fases; cada uma compila e passa os testes sozinha.

**Fase 1 — Reavaliação e `origem_evidencia` (O, P)**
- `sessao/Marca.java` (novo): `functor` e um contador que muda a cada facto novo desse functor.
- `sessao/BaseFactos.java`: cria as marcas no construtor (o conjunto vem do `condicao_le`) e faz `update` quando
  regista um facto com esse functor.
- `sessao/CondicoesCalculadas.java`: `origemEvidencia(...)`, com `instancia_atacada` e `instancias_com_nome`
  como no `soc_suporte.txt`.
- Testes `CondicoesCalculadasTest` (novo): um caso por resultado da `origem_evidencia` (ligada, outro programa,
  outra instância, sem processo, processo desconhecido, instância por identificar, instância não inventariada).

**Fase 2 — Referência Prolog A–G+N (Q)**
- `motores/drools/ferramentas/gera_referencia_agn.pl` (novo) e `motores/drools/ferramentas/README.md` (novo).
- `motores/drools/src/test/resources/referencia_agn/*.json` (10, gerados).

**Fase 3 — Filtro de regras e teste de paridade (R)**
- `MotorDrools`: construtor package-private com o filtro de regras.
- `ParidadeTest` (novo): `@ParameterizedTest` por nível (A–G+N, todas) e por variante, 20 casos. Lê os passos do
  `backend/socrates/src/main/resources/generated/cenarios.json` (o mesmo que os geradores usam). A comparação e a
  lista de diferenças conhecidas ficam numa classe de apoio (`ComparaRespostas`), para a #16 a poder reaproveitar.
  Nesta fase os casos ficam vermelhos até às fases 4–6, por isso a fase 3 entra no mesmo commit que a fase 4.

**Fase 4 — Grupos A, B, C, D (A–D)**
- `regras/A_afetado.drl`, `regras/B_vetor.drl` (R4–R6, R8, R9 junto da R7), `regras/C_sem_impacto.drl`,
  `regras/D_duvida.drl`.

**Fase 5 — Grupos E, F, G (E–G)**
- `regras/E_impactos.drl`, `regras/F_confirmacao.drl`, `regras/G_pergunta.drl`.
- Com isto a paridade fica verde nas 10 variantes, ou com as diferenças que restem listadas e explicadas.

**Fase 6 — Grupo N (N)**
- `regras/N_catalogo.drl`, a fixture `factos_iniciais_catalogo.json` e os testes das R89–R92 no `MotorDroolsTest`.

**Fase 7 — Interface com H–M e documentação (S)**
- `regras/README.md`: o estado das regras e a tabela dos factos que passam de uma metade para a outra. A–G
  produzem e H–M consomem (`exploracao_possivel`, `incidente`, `motivo`, `suspeita`, …). H–M produzem e A–G
  consomem (`revisto` na R13 e na R30, `origem_hostil` da R35 na R13, `impacto_confirmado` da R50 na R25/R26,
  `compromisso_confirmado` da R43/R49 na R29).
- `motores/drools/README.md` e a secção "Estado do projeto" do `README.md` da raiz.

---

## 4. Verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify
swipl motores/drools/ferramentas/gera_referencia_agn.pl --verificar
```

- **Baseline (`845bcb9`, JDK 25):** `.\mvnw.cmd -pl motores/drools test` → `BUILD SUCCESS`, 20 testes. O `verify`
  completo volta a medir-se antes da fase 1.
- **Passa quando:**
  - há `BUILD SUCCESS`, os 20 testes que já existem continuam verdes e os novos também;
  - o `ParidadeTest` corre 20 casos (e não 0, o que prova que o filtro apanhou testes). O nível A–G+N fica sem
    nenhuma diferença; o nível completo fica sem diferenças fora da lista explícita de causas H–M;
  - o `--verificar` do gerador sai com código 0.
- **"Feito quando" da issue:** a AC14 (mesmos factos) e a AC15 (mesma regra e premissas) para as regras A–G e N são
  o nível A–G+N do `ParidadeTest`. O nível completo mostra o estado das duas metades juntas, para a #15/#16.

---

## 5. Riscos

| Risco | Como se valida que não aconteceu |
| :--- | :--- |
| **A ordem de disparo muda o resultado de uma negação sobre um facto deduzido**: R20–R23, R31 e R32 com `nao impacto_excluido`. O Drools cancela uma ativação se o facto negado chegar antes de ela disparar; o Prolog decide quando processa o facto | Variantes 24/24b (CGI desligado) e 25/25b (configuração que chega depois) na paridade. Se divergir, `salience` na R18 (corre antes das que a negam), registado na regra |
| **Outra justificação para o mesmo facto** (fica a primeira que disparar; ex.: `incidente` de `versao_por_confirmar` pela R7 ou pela R8) | O teste compara a regra e as premissas, por isso apanha-o. Cada caso que não se resolva com ordem fica na lista explícita e no RESULT §2 (ponto já aberto pela F0001 para a #16) |
| **As marcas reavaliam demais**: uma regra volta a avaliar-se a cada `tempo` novo, e o tempo por pedido sobe | Medir o tempo do `ParidadeAGNTest` por variante e comparar com o ~1,3 s da F0001. Se for excessivo, restringir as marcas ao evento (`Marca` por functor e primeiro argumento) |
| **As marcas mudam o resultado das regras H–M do #31** (que usam `eval` sem marcas) | Não muda: as marcas só entram nos padrões das regras A–G. O `MotorDroolsTest` do #31 (20 testes) continua verde |
| **O `soc_regras.txt` filtrado à mão no gerador perde ou parte uma regra** | O gerador conta as regras do ficheiro filtrado e falha se não forem 38. A referência regista quantas regras disparam |
| **A base do #31 muda** (revisão, rebase) e a branch tem de a seguir | As alterações ao `CondicoesCalculadas` ficam num commit à parte (fase 1), para o rebase ser simples. Na PR fica a nota de que depende do #31 |
| **`cenarios.json` / referências desatualizados** em relação aos `.txt` | `gera_json.pl --verificar` e o `--verificar` do novo gerador, ambos corridos antes de fechar |
| **`passo` diferente** (o facto aparece noutro passo no Drools) | O teste compara o `passo` de cada facto |
| **O nível completo esconde um erro A–G atrás de uma "causa H–M"** (a lista explícita cresce sem critério) | Uma diferença só entra na lista se o primeiro facto diferente, seguido pelas premissas, vier de uma regra 35–88. O nível A–G+N continua sem lista nenhuma |

---

## 6. Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | `feat(drools): re-evaluate calculated conditions when a fact they read arrives` | P |
| 2 | `feat(drools): add origem_evidencia as a calculated condition` | O |
| 3 | `test(drools): add the Prolog A-G+N reference and its generator` | Q |
| 4 | `feat(drools): port rules of groups A to D` + `ParidadeTest` | A–D, R |
| 5 | `feat(drools): port rules of groups E to G` | E–G |
| 6 | `feat(drools): port catalogue validation rules (group N)` | N |
| 7 | `docs(drools): document rules A-G and N and the facts shared with H-M` | S |

Não há hooks de commit no repositório (não existem `.pre-commit-config.yaml`, `.husky/` nem `core.hooksPath`).

---

## 7. Fora do âmbito / a quem passa

| Fica de fora | Para |
| :--- | :--- |
| Rever as regras H–M e o resto do `CondicoesCalculadas` do PR #31 (incluindo a `origem_evidencia` que a R43 não verifica, e as marcas nas regras H–M) | #15 / revisão do #31 (Francisco). Fica a nota na PR |
| Corrigir as diferenças do nível completo cuja causa é uma regra H–M | #15 (lista no teste e no RESULT §2) |
| Comparador com UI e relatório por cenário, e o critério para justificações diferentes | #16 (pode reaproveitar o `ComparaRespostas`) |
| Mudar o `gera_referencias.pl` do Prolog para aceitar um subconjunto de regras | Não se faz: o novo gerador fica no módulo Drools e não toca no lado Prolog |
| `salience` ou outra afinação nas regras H–M | #15 |
