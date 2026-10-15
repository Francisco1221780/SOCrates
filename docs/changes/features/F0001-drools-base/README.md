# F0001 — Drools: base (modelo de factos, justificações, DroolsAdapter)

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | [#4](https://github.com/Francisco1221780/CyberRangers/issues/4) |
| Branch | `feature/drools-base` (a partir de `develop`, atualizada para `635785e`) |
| Estado | Plano revisto para seguir o `wrapper.pl`, aprovado em 2026-10-14; a #6 (contrato) está fechada |
| Âmbito | Infraestrutura do motor Drools sobre a qual se portam as 92 regras: termos, factos numerados, factos iniciais, justificação, resposta **no formato exato do `motores/prolog/wrapper.pl`**, estado "pronto" e a R7 como regra molde |
| Verificação | `.\mvnw.cmd verify` com JDK 25; testes novos no módulo Drools; `DroolsAdapterTest` atualizado |

> **Revisão de 14/10.** A primeira versão deste plano (aprovada) definia um formato próprio de resposta. Entretanto
> o PR #24 trouxe o `wrapper.pl` para `develop`, que já fixa o formato do lado Prolog. O comparador (AC14/AC15) só
> funciona se os dois motores responderem igual, por isso o plano foi revisto para seguir o wrapper à letra.

---

## 1. Situação

O esqueleto do lado Drools já existe em `develop`: o pedido JSON entra, cada passo é inserido numa `KieSession`
nova, corre `fireAllRules()` e a resposta devolve todos os `Facto` da sessão. Falta tudo o que torna a resposta
igual à do Prolog.

**Evidência**

- `motores/drools/src/main/java/cyberrangers/motores/drools/Facto.java`: `Facto(predicado, List<Object> args)`,
  sem `equals`/`hashCode`, sem número e sem justificação.
- `motores/drools/src/main/java/cyberrangers/motores/drools/MotorDrools.java`: os `args` passam por
  `json.treeToValue(a, Object.class)`, por isso um termo composto `{"f":"v","args":[2,4,49]}` fica um
  `LinkedHashMap`. A resposta só tem `estado`, `motor`, `versaoKB`, `factos` e `erro`, e o `estado` é sempre
  `concluido`. As regras não correm sobre os dados fixos antes dos passos.
- `motores/prolog/wrapper.pl` (develop, PR #24) é a **referência do formato**:
  - `responde/1` carrega a base e corre o motor sobre os dados fixos (`carrega_base/1` → `arranca_motor`).
    Só depois valida o pedido inteiro e corre os passos.
  - `facto_json/4`: `{numero, predicado, args, origem, passo, regra, premissas}`. `origem` é `inicial` (n.º ≤
    último inicial), `entrada` ou `regra`. `passo` é o id do passo em que o facto apareceu, ou `null` nos factos
    iniciais e nas conclusões sobre os dados fixos.
  - Premissas (`justifica/3` passado por `termo_json/2`):
    - um facto → o seu número (inteiro);
    - `nao X` → `{"f":"nao","args":[X]}`;
    - uma condição calculada → o próprio termo (`{"f":"versao_cmp","args":[...]}`);
    - variáveis livres → `"A"`, `"B"`, … (`numbervars` sobre a lista toda).
  - `perguntas_json/1`: `{facto, ativo, evento, pergunta, texto, opcoes:[{valor, texto}]}`, para
    `pergunta_analista` sem `resposta_analista` e sem `pergunta_retirada`. `texto` vem de `texto_pergunta/2`
    (ou `null`) e as opções de `opcao_resposta/3`.
  - `incidentes_json/1`: `{facto, id, tipo, ativo, impactados}`.
  - `tickets_json/1`: `{facto, incidente, ativo, acao, gravidade, prioridade, impactados, fechado}`; `fechado` é
    o motivo de `ticket_fechado/2` ou `null`.
  - `estado`: `aguarda_resposta` se houver perguntas, senão `concluido`. As listas vão por ordem do número.
  - `valida_pedido/2` e `json_facto/3` validam o pedido inteiro antes do 1.º passo, com mensagens fixas. O id de
    um passo sem `id` é `passo_<i>`, a contar de 1. `resposta_erro/2` dá `{estado:"erro", motor, versaoKB, erro}`.
- `base_conhecimento/soc_suporte.txt` `novo_facto/1` e `soc_motor.pl` `cria_facto/3`: nenhum dos dois cria um
  facto que já exista. O `justifica(N, ID, LFactos)` guarda uma premissa por condição do LHS, pela ordem.
- #6 (comentário de 14/10): tabela de termos Prolog ↔ JSON; `v(2,4,49)` e `'2.4.49-ubuntu1'` têm de continuar
  distinguíveis.
- `base_conhecimento/soc_regras.txt` R7: `vetor_aberto(Ev,Alvo,Inst,Vuln) e nao versao(Inst,_)` → três
  `cria_facto` (`exploracao_incerta`, `motivo`, `incidente` com o termo `inc(versao_por_confirmar,Ev)`).
- `factos_iniciais.json` ainda não existe; é gerado pela #10, que está por fazer.

**Inventário**

| # | Superfície |
| :--- | :--- |
| A | Termos: átomo, inteiro, lista, termo composto e variável livre, com conversão JSON nos dois sentidos igual à do wrapper |
| B | `Facto` com igualdade por conteúdo, `numero`, `origem`, `passo`, `regra` e `premissas` |
| C | Base de factos da sessão: numeração, deduplicação e criação de factos com justificação, chamada a partir das regras |
| D | Carregamento de `factos_iniciais.json` e corrida das regras sobre os dados fixos antes dos passos |
| E | Validação do pedido e resposta no formato do wrapper: `estado`, `perguntas`, `incidentes`, `tickets`, `factos`, `erro` |
| F | Estado "pronto": regras compiladas e factos iniciais carregados, com a razão quando não está |
| G | R7 portada como regra molde, com teste; sai a `00_exemplo.drl` |
| H | Atualização do `DroolsAdapterTest` e dos READMEs |

**Escala:** 1 módulo (`motores/drools`), mais 1 teste e 1 fixture no backend. São cerca de 8 classes novas ou
alteradas, 1 ficheiro DRL e 2 fixtures JSON de teste. Fica preparada a base para as 92 regras (portadas na #13 e
na #15) e os 314 factos iniciais.

---

## 2. Resultado pretendido

Um pedido entra no `MotorDrools.infere(String)`, e o fluxo é o mesmo do `wrapper.pl`:

1. O pedido inteiro é validado e convertido antes de qualquer inferência.
2. Abre-se uma sessão nova e inserem-se os factos iniciais (`origem: inicial`, `passo: null`).
3. Corre-se `fireAllRules()` sobre os dados fixos. As conclusões ficam com `origem: regra` e `passo: null`.
4. Para cada passo, inserem-se os factos (`origem: entrada`; um facto repetido é ignorado, como no `novo_facto/1`)
   e corre-se `fireAllRules()`. O que aparecer leva `passo: <id>`.
5. A resposta é montada a partir da base de factos, por ordem de `numero`, e a sessão faz `dispose()`.

As regras não fazem `insert` diretamente. Chamam a base de factos da sessão (global DRL `base`) com o número da
regra, as premissas pela ordem do LHS e o facto a criar. A base ignora o facto se ele já existir; se for novo,
dá-lhe o número seguinte e o passo corrente, guarda a justificação e insere-o.

Facto concluído pela R7 na resposta, igual ao que o wrapper dá:

```json
{"numero": 320, "predicado": "incidente",
 "args": [{"f": "inc", "args": ["versao_por_confirmar", "ev_e1"]}, "versao_por_confirmar", "web_srv_04", ["web_srv_04"]],
 "origem": "regra", "passo": "cenario_07", "regra": 7,
 "premissas": [318, {"f": "nao", "args": [{"f": "versao", "args": ["web_srv_04_apache", "A"]}]}]}
```

Resposta:

```json
{"estado": "concluido | aguarda_resposta", "motor": "drools", "versaoKB": "6",
 "perguntas":  [{"facto": 401, "ativo": "...", "evento": "...", "pergunta": "...", "texto": "... | null",
                 "opcoes": [{"valor": "sim", "texto": "..."}]}],
 "incidentes": [{"facto": 320, "id": {"f": "inc", "args": [...]}, "tipo": "...", "ativo": "...", "impactados": [...]}],
 "tickets":    [{"facto": 450, "incidente": {...}, "ativo": "...", "acao": "...", "gravidade": "...",
                 "prioridade": "...", "impactados": [...], "fechado": null}],
 "factos": [...], "erro": null}
```

Em erro: `{"estado": "erro", "motor": "drools", "versaoKB": "6", "erro": "<mensagem>"}`.

### Decisões

| Decisão | Valor | Origem |
| :--- | :--- | :--- |
| Formato da resposta | **O do `wrapper.pl`, campo a campo.** O único campo que muda é `motor: "drools"` | Revisão de 14/10 (pedido do utilizador) |
| Modelo de factos | Genérico: `Facto(predicado, args)`, com `equals`/`hashCode` sobre `predicado` + `args` | #6 |
| Termos em Java | Átomo → `String`; inteiro → `Long` (sempre, para o `equals` ser estável); lista → `List<Object>` imutável; termo composto → `record Termo(String f, List<Object> args)`; variável livre → `Variavel` (só nas premissas negadas) | Tabela da #6; decisão de implementação |
| JSON → termo | Igual ao `json_termo/2`: um objeto só é válido com exatamente `f` e `args`; `true`/`false`/`null` passam a átomo (string), como o `value_string_as(atom)` do wrapper | `wrapper.pl` |
| Acesso aos argumentos no DRL | `Facto.arg(int i)`, ex.: `Facto(predicado == "vetor_aberto", $ev : arg(0), $inst : arg(2))` | Decisão de implementação |
| Premissas no JSON | Facto → `numero`; negação → `{"f":"nao","args":[<termo>]}`; condição calculada → `<termo>`. As variáveis livres têm o nome `A`, `B`, … pela ordem em que aparecem na lista inteira; a mesma `Variavel` tem sempre o mesmo nome | `facto_json/4` (`numbervars`) |
| Facto dentro de uma premissa | Como termo composto (`{"f": predicado, "args": [...]}`), não como `{"predicado", ...}` | `termo_json/2` |
| Facto repetido | Não se cria (nem entrada, nem conclusão); fica a primeira justificação | `novo_facto/1`, `cria_facto/3` |
| `origem` / `passo` | `inicial`, `entrada` ou `regra`. `passo` é o id do passo em que o facto apareceu, ou `null` nos iniciais e nas conclusões sobre os dados fixos. `regra`/`premissas` são `null`/`[]` fora de `origem: regra` | `facto_json/4` |
| Id do passo | `id` do pedido, ou `passo_<i>` (a contar de 1) se faltar ou for `null` | `converte_passos/3` |
| Arranque | Corre `fireAllRules()` sobre os factos iniciais antes do 1.º passo | `carrega_base/1` |
| Validação | Igual ao `valida_pedido/2`, com as mesmas mensagens; o pedido inteiro é validado antes de inferir | `wrapper.pl` |
| `perguntas` / `incidentes` / `tickets` | Objetos com os campos do wrapper (ver resposta acima), com os mesmos filtros, por ordem do número | `perguntas_json/1`, `incidentes_json/1`, `tickets_json/1` |
| Factos iniciais | Em `factos_iniciais.json`, na raiz do classpath (`motores/drools/src/main/resources/`). Formato assumido: array de `{"predicado","args"}` pela ordem do `soc_factos.txt`, numerados 1..N por essa ordem | README do módulo; **a combinar com a #10** |
| Estado "pronto" | `MotorDrools.estado()` → `EstadoMotor(pronto, regrasCarregadas, factosIniciais, erros)`. Erros de compilação DRL (`KieContainer.verify()`) ou um `factos_iniciais.json` em falta ou inválido deixam o motor não pronto, e `infere` devolve então `estado: "erro"` com a razão | #4, AC13 |
| Regra molde | R7, em `regras/B_vetor.drl` (um ficheiro por grupo), com nome da regra `"R7"` | Pergunta ao utilizador |

### Alternativas rejeitadas

- **O formato próprio da primeira versão deste plano** (`n`, `{"nao": ...}`, listas de factos filtrados). Era
  razoável, mas obrigava a mudar o wrapper que já está em `develop`, ou deixava o comparador com dois formatos
  para traduzir.
- **Uma classe por predicado.** Os padrões DRL ficavam mais legíveis, mas seriam dezenas de classes a manter em
  paralelo com a base, mais um conversor JSON por classe. Foi rejeitado na #6.
- **Termo composto como `Map` (o que o Jackson dá hoje).** Não se distingue com segurança de um objeto JSON
  qualquer, e no DRL obriga a `get("f")`. Um `record` é explícito e imutável.
- **Justificação com `insertLogical` / TMS do Drools.** O TMS retrai conclusões quando a premissa desaparece, mas
  a base nunca apaga conclusões (passam a históricas por `revisto`/`superado`/`resolvido`).
- **Deduplicar com o equality behavior do `KieBase`.** Evita duplicados, mas não dá número nem justificação, e
  descarta a conclusão em silêncio.

---

## 3. Implementação

Por fases; cada uma compila e passa os testes sozinha.

**Fase 1 — Termos e factos (A, B)**
- `Termo.java` (novo): `record Termo(String f, List<Object> args)`.
- `Variavel.java` (novo): marca de variável livre, comparada por identidade.
- `Facto.java`: argumentos imutáveis, `arg(int)`, `equals`/`hashCode` por conteúdo; campos `numero`, `origem`,
  `passo`, `regra`, `premissas` (fora do `equals`).
- `Origem.java` (novo): enum `INICIAL`, `ENTRADA`, `REGRA`, serializado em minúsculas.
- `TermosJson.java` (novo): JSON ↔ termo como o `json_termo/2` e o `termo_json/2`, com as mensagens de erro do
  wrapper.
- Testes `TermosJsonTest`:
  - ida e volta;
  - `v(2,4,49)` ≠ `"2.4.49-ubuntu1"`;
  - `3` como `Integer` e como `Long` dá o mesmo facto;
  - objeto com chaves a mais → erro.

**Fase 2 — Base de factos e justificação (C)**
- `Premissa.java` (novo): `sealed interface` com `DoFacto(Facto)`, `Negacao(Termo)` e `Calculada(Termo)`. Tem
  fábricas para o DRL: `Premissa.de($f)`, `Premissa.nao("versao", $inst, Variavel.nova())` e
  `Premissa.calculada(...)`.
- `BaseFactos.java` (novo): índice por conteúdo, contador e passo corrente. Métodos `insereInicial`,
  `insereEntrada`, `cria(RuleContext, int regra, List<Premissa>, Facto)` e `factos()` ordenados por número.
- Serialização das premissas com nomes `A`, `B`, … atribuídos pela ordem de aparecimento na lista, como o
  `numbervars`.
- Testes `BaseFactosTest`:
  - numeração;
  - facto repetido ignorado;
  - primeira justificação mantida;
  - duas variáveis livres → `A` e `B`.

**Fase 3 — Factos iniciais, arranque e estado "pronto" (D, F)**
- `FactosIniciais.java` (novo): lê o `factos_iniciais.json` do classpath uma vez.
- `EstadoMotor.java` (novo) e `MotorDrools.estado()`. O construtor do `MotorDrools` cria o `KieContainer` e
  verifica as regras, e guarda os erros em vez de os lançar.
- `MotorDrools.corre`: insere os iniciais e corre `fireAllRules()` antes dos passos.
- `motores/drools/src/test/resources/factos_iniciais.json` (novo): fixture pequena, só para testes até a #10
  gerar o ficheiro real. Inclui `texto_pergunta` e `opcao_resposta` para uma pergunta.
- Testes:
  - motor pronto com a fixture;
  - ficheiro em falta → não pronto, e `infere` devolve `erro`;
  - conclusão sobre os dados fixos fica com `passo: null`.

**Fase 4 — Validação e resposta (E)**
- `Pedido.java` (novo): valida e converte o pedido inteiro como o `valida_pedido/2`, com o id `passo_<i>` por
  omissão.
- `Resposta.java` (novo): monta `perguntas`, `incidentes`, `tickets` e `factos` com os campos e os filtros do
  wrapper, e decide o `estado`.
- Testes:
  - `pergunta_analista` sem resposta → `aguarda_resposta`, com `texto` e `opcoes`;
  - com `resposta_analista` ou `pergunta_retirada` → `concluido`;
  - `ticket` + `ticket_fechado` → `fechado` preenchido;
  - cada mensagem de erro do `valida_pedido/2`.

**Fase 5 — Regra molde R7 (G)**
- `regras/B_vetor.drl` (novo): R7 com `not Facto(predicado == "versao", arg(0) == $inst)` e três
  `base.cria(kcontext, 7, [Premissa.de($v), Premissa.nao("versao", $inst, Variavel.nova())], ...)`. Leva um
  comentário com o texto da regra de `soc_regras.txt`, para servir de molde.
- `regras/00_exemplo.drl`: apagado.
- Testes (`MotorDroolsTest`):
  - `vetor_aberto` sem `versao` → os três factos com `regra: 7`, `passo` certo e as premissas
    `[<numero>, {"f":"nao","args":[{"f":"versao","args":[<inst>,"A"]}]}]`, mais o incidente em `incidentes`;
  - com `versao(Inst, ...)` num passo anterior → não dispara;
  - o mesmo `vetor_aberto` em dois passos → um só facto.
- Teste de paridade pontual: o facto `incidente` da R7 serializado pelo Drools é igual, campo a campo, ao que o
  `wrapper.pl` dá para o mesmo facto (valor esperado copiado à mão de uma corrida do wrapper; sem chamar o `swipl`
  no build).

**Fase 6 — Backend e documentação (H)**
- `backend/cyber/src/test/.../DroolsAdapterTest.java`: deixa de procurar `exemplo_conclusao` e passa a verificar
  um facto da R7. Junta `backend/cyber/src/test/resources/factos_iniciais.json` (cópia da fixture), porque o
  backend não vê os recursos de teste do módulo.
- `motores/drools/README.md`, `motores/drools/src/main/resources/regras/README.md` e as secções "Estado do
  projeto" e "Testing" do `README.md` da raiz: o molde de uma regra (como se escrevem as premissas) e a nota de
  que o formato da resposta é o do `wrapper.pl`.

---

## 4. Verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'   # o pom do backend pede release 25
.\mvnw.cmd verify
```

- **Baseline (14/10, `develop` @ 681b21d, JDK 25):** `BUILD SUCCESS` com 4 testes:
  - 2 em `MotorDroolsTest`;
  - 1 em `CyberApplicationTests`;
  - 1 em `DroolsAdapterTest`.

  Avisos que já existiam: `Unable to find pom.properties` (Drools a correr a partir de `target/classes`) e o
  aviso do Mockito sobre self-attaching. Com o JDK 23 (o `JAVA_HOME` por omissão nesta máquina), o backend falha
  com `release version 25 not supported`; não é regressão desta alteração. Volta a medir-se sobre `635785e`
  antes de implementar; o PR #24 só trouxe ficheiros Prolog e READMEs.
- **Passa quando:** há `BUILD SUCCESS`, os testes atuais (ou os seus substitutos) e os novos das fases 1 a 5
  estão a verde, e não aparecem avisos novos.
- **Critérios da issue:**
  - "Um pedido com um cenário simples devolve uma resposta válida pelo contrato": teste da R7 com a resposta
    completa, mais o teste de paridade pontual com o wrapper;
  - "AC13 (lado Drools)": testes de `estado()`. O endpoint `/api/health` é da #12.

---

## 5. Riscos

| Risco | Como se valida que não aconteceu |
| :--- | :--- |
| **O wrapper muda de formato** depois desta alteração (a #6 ainda está aberta) e os dois motores divergem | O formato fica num só sítio no Drools (`Resposta`, `TermosJson`). O teste de paridade pontual da fase 5 deixa de passar se o formato acordado mudar e ninguém atualizar o valor esperado. Na PR, deixar a nota a ligar à #6 e à #9 |
| **A ordem de disparo do Drools escolhe outra justificação** para o mesmo facto (fica a primeira) ou outra numeração | A comparação faz-se pelo conteúdo, nunca pelos números (README da raiz). A escolha da justificação fica para a #13/#16, e há um teste com o mesmo facto concluído por duas vias, para fixar o comportamento |
| **Nomes das variáveis livres diferentes** dos do `numbervars` (ex.: `A` na posição errada) | Teste com duas variáveis numa só lista de premissas, e o teste de paridade da R7 |
| **`Integer` vs `Long`** nos argumentos faz com que dois factos iguais não sejam iguais (`3` ≠ `3L`) | `TermosJsonTest` e `BaseFactosTest` com o mesmo facto vindo do JSON e criado no DRL |
| **Negação no DRL ≠ `nao` do Prolog** quando o facto negado chega depois: o Drools cancela a ativação ainda por disparar; o Prolog não volta atrás numa conclusão já feita | Teste da R7 com `versao` num passo anterior e no mesmo passo. Dentro de um passo, o Drools só cancela se o facto chegar antes de a regra disparar; o caso fica documentado para a #13 |
| **Formato do `factos_iniciais.json` diferente** do que a #10 vai gerar | O formato fica escrito no README do módulo e combinado com o P2; o leitor falha com uma mensagem clara (motor "não pronto"), não em silêncio |
| **Custo de inserir os 314 factos e correr as regras sobre eles em cada pedido** | Medir com a fixture; reavaliar com o ficheiro real (fora do âmbito) |
| **`DroolsAdapterTest` / `CyberApplicationTests` partem** por o motor ficar "não pronto" sem `factos_iniciais.json` no classpath do backend | Fixture em `backend/cyber/src/test/resources/`; `.\mvnw.cmd verify` completo |

---

## 6. Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | `feat(drools): add Termo and content-equal Facto with wrapper-compatible JSON terms` | A, B |
| 2 | `feat(drools): add session fact base with numbering and justifications` | C |
| 3 | `feat(drools): load initial facts, run rules on them and report readiness` | D, F |
| 4 | `feat(drools): validate requests and build the wrapper response format` | E |
| 5 | `feat(drools): port R7 as template rule and drop the example rule` | G |
| 6 | `test(backend): update DroolsAdapterTest for the R7 template` | H |
| 7 | `docs(drools): document fact model, premises and response format` | H |

Não há hooks de commit no repositório (não existem `.pre-commit-config.yaml`, `.husky/` nem `core.hooksPath`).

---

## 7. Fora do âmbito / a quem passa

| Fica de fora | Para |
| :--- | :--- |
| Gerar o `factos_iniciais.json` real a partir de `soc_factos.txt` | #10 (P2) |
| As restantes regras dos grupos A a G e N | #13 |
| Condições calculadas em Java (`versao_cmp`, `porto_compativel`, …) e a reavaliação por `condicao_le/2` | #14 |
| Regras H a M (incluindo as que criam `ticket`, `ticket_fechado` e `pergunta_retirada`) | #15 |
| `GET /api/health`, endpoints e DTOs no backend | #12 |
| Escrever o contrato em `docs/` e fechar a #6 (o formato do `wrapper.pl` passa a ser o contrato) | #6 (todos) |
| Comparador Prolog/Drools e critério de igualdade das justificações | #16 |
