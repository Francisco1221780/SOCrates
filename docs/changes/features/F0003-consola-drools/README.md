# F0003 — Consola interativa do motor Drools

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 18/10: correr só o motor Drools como o `soc_motor.pl` com o `menu`) |
| Branch | `feature/drools-regras-a-g-n` (a mesma da F0002, por decisão do utilizador) |
| Estado | Plano aprovado em 2026-10-18 |
| Âmbito | Dois modos de correr só o Drools, como no Prolog: o atual (um pedido JSON no `stdin`, uma resposta no `stdout`, igual ao `wrapper.pl`) e um novo, interativo (`ConsolaDrools`), que arranca já com a base carregada e tem o menu do `soc_motor.pl`. O `cenarios.json` passa a existir também no módulo Drools |
| Verificação | `.\mvnw.cmd verify`; `ConsolaDroolsTest` corre a consola com entradas escritas no teste; `gera_json.pl --verificar`; uma corrida à mão no IntelliJ e pelo `exec:java` |

---

## 1. Situação

O módulo Drools só se corre de uma forma: o `MotorDrools.main` lê o `stdin` até ao fim, responde em JSON e sai. No
IntelliJ isto parece parado, porque espera por um Ctrl+D. O Prolog tem também o `menu/0` do `soc_motor.pl`, que é o
que se usa para demonstrar a base.

**Evidência**

- `motores/drools/src/main/java/socrates/motores/drools/MotorDrools.java`: o `main` faz
  `System.in.readAllBytes()`, escreve a resposta e faz `System.exit`.
- `motores/drools/pom.xml`: o `exec-maven-plugin` tem `<mainClass>` fixo no `MotorDrools` e o logger só com `error`.
  É este o caminho documentado em "Correr só o Drools".
- `motores/prolog/soc_motor.pl` `menu/0`, com 13 opções:
  - 1 carregar a base; 2 correr o motor;
  - 3 injetar um cenário; 13 injetar todos (sequência `base` + `normal`, sem respostas);
  - 4 introduzir um facto (sintaxe Prolog, ou uma lista);
  - 5 responder às perguntas (com `porque.`: a regra que gerou a pergunta, o "como" dela e as regras que usam a
    resposta);
  - 6 incidentes e tickets; 7 ações;
  - 8 registar a recuperação de uma máquina;
  - 9 como?; 10 porque não?;
  - 11 mostrar factos (todos, concluídos ou por nome); 12 opções do motor.
- O motor Drools não guarda estado entre pedidos (regra do projeto, README da raiz). O Prolog acumula os factos na
  sessão.
- `cenarios.json` (passos prontos de cada um dos 67 cenários, as sequências `base`, `atualizacoes` e `normal`, as
  dependências e as alternativas) só existe em `backend/socrates/src/main/resources/generated/`. É escrito pelo
  `motores/prolog/ferramentas/gera_json.pl`, que já escreve o `factos_iniciais.json` do Drools. Esse caminho é usado
  pelos testes Prolog, pelos dois geradores de referências, pelo `ParidadeTest` e pelos PRs #29 e #30.
- A resposta do motor já traz tudo o que o menu mostra: `perguntas` (com `texto` e `opcoes`), `incidentes`,
  `tickets` e `factos` (com `regra` e `premissas`, o que chega para o "como?").

**Inventário**

| # | Superfície |
| :--- | :--- |
| A | `cenarios.json` também em `motores/drools/src/main/resources/` (gerado pelo `gera_json.pl`, verificado pelo `--verificar`) |
| B | Sessão da consola: a lista de passos e a última resposta (cada ação junta um passo e volta a correr o pedido inteiro) |
| C | Termos em sintaxe Prolog na consola, para escrever factos (`alerta_ids(ev_1,web_srv_02,sig_x)`) e para os mostrar |
| D | `ConsolaDrools`: o menu, com as opções da §2 |
| E | Como se corre: IntelliJ (`main` da `ConsolaDrools`) e linha de comandos (`exec:java` com `--interativo`) |
| F | Testes e documentação |

**Escala:** 1 linha no `gera_json.pl` e 1 JSON gerado. 4 classes novas num pacote `consola/`, 1 argumento no `main`
do `MotorDrools`, 2 classes de teste e os READMEs.

---

## 2. Resultado pretendido

No IntelliJ, *Run* na `ConsolaDrools` (ou `.\mvnw.cmd -q -pl motores/drools compile exec:java "-Dexec.args=--interativo"`):

```
========== ANALISTA VIRTUAL DE SOC (Drools) ==========
Base v6: 92 regras, 314 factos iniciais. Passos injetados: 2 (c6, c7). 2 incidentes, 1 pergunta por responder.
 1. Ver o estado
 2. Correr o motor outra vez
 3. Injetar um cenario
 4. Introduzir um facto (telemetria)
 5. Responder as perguntas ao analista
 6. Incidentes e tickets
 7. Acoes recomendadas
 8. Registar a recuperacao de uma maquina
 9. Explicar como se concluiu um facto (como?)
11. Mostrar factos
13. Injetar todos os cenarios de uma vez
14. Carregar um pedido de um ficheiro JSON
15. Recomecar do estado inicial
 0. Sair
Opcao>
```

As opções têm os números do `menu/0` do Prolog, para quem conhece um conhecer o outro. A 1 deixa de ser "carregar a
base", porque a base já vem carregada, e passa a mostrar o estado. A 10 e a 12 não existem (ver Decisões). Cada ação
que junta factos corre logo o motor e mostra o que mudou. A 2 só repete a última corrida.

### Decisões

| Decisão | Valor | Origem |
| :--- | :--- | :--- |
| Onde vivem os cenários | O `gera_json.pl` escreve o mesmo conteúdo também em `motores/drools/src/main/resources/cenarios.json`. A consola e o `ParidadeTest` leem-no do classpath. O ficheiro do backend fica onde está | Utilizador (opção A, 18/10) |
| Dois modos | O `main` do `MotorDrools` continua `stdin` → JSON (para pipes e para comparar com o `wrapper.pl`). Com o argumento `--interativo` passa para a `ConsolaDrools`. A `ConsolaDrools` tem o seu `main`, para se correr com um clique no IntelliJ | Utilizador |
| Estado | A consola guarda a lista de passos e envia o pedido inteiro ao `MotorDrools.infere` de cada vez, com `"versaoKB"` do motor. Usa só a API pública (JSON de entrada e de saída): o que a consola mostra é o que o backend e o comparador recebem | Regra "servidor sem estado" |
| Ids dos passos | Um cenário entra com o seu id (`c6`), como nas referências. Um facto à mão entra como `manual_<n>`, uma resposta como `resposta_<n>`, uma recuperação como `recuperacao_<n>`. Os passos de um ficheiro entram com os ids que trazem | Decisão de implementação |
| Opção 3 | Lista os cenários com o id, o grupo e a descrição, e aceita um id ou vários separados por espaços. Avisa (e não injeta) se um cenário já entrou, ou se é alternativa de um que já entrou (ex.: `c7b_nao` depois de `c7b_sim`) | `cenarios.json` (`alternativas`) |
| Opção 4 | Aceita um facto ou uma lista em sintaxe Prolog: átomos, inteiros, termos compostos, listas e átomos entre plicas (`'2.4.49-ubuntu1'`). Converte para o JSON do contrato (`v(2,4,49)` → `{"f":"v","args":[2,4,49]}`) | Igual à opção 4 do Prolog |
| Opção 5 | Lista as `perguntas` da resposta. Para a escolhida, mostra o `texto` e as `opcoes`, e aceita o valor (`sim`), o número da opção ou `porque`. O `porque` mostra a regra que gerou a pergunta, o "como" dela e as regras que usam a resposta (R32–R34 numa pergunta de evidência; R49 só para `compromisso_no_host`; R50–R51 numa pergunta de confirmação). A resposta entra como `resposta_analista(Ativo, Evento, Pergunta, Valor)` | `responde_perguntas/0` do Prolog |
| Opção 9 | Árvore pelo número do facto ou pelo facto em sintaxe Prolog. Um facto já explicado na mesma árvore aparece como "(ja explicado acima)", como o `explica_facto/2` | `como/1` do Prolog |
| Opção 13 | Recomeça do estado inicial e injeta a sequência `base` mais a `normal`, sem respostas | `injeta_todos/0` do Prolog |
| Opção 10 ("porque não?") | Não entra | Utilizador (18/10) |
| Opção 12 (opções do motor) | Não entra: as opções do Prolog (`mostrar_conclusoes`, `passo_a_passo`) não fazem sentido num motor que corre o pedido inteiro | Decisão de implementação |
| Texto da consola | Sem acentos, como o menu do Prolog. A consola do Windows e a do IntelliJ nem sempre estão em UTF-8 | `soc_motor.pl` |
| Fim de entrada | Ctrl+D / fim do ficheiro contam como `0` (sair), como o `le/2` do Prolog | `soc_motor.pl` |

### Alternativas rejeitadas

- **Guardar a `KieSession` aberta entre ações** (como o Prolog guarda os factos). Seria mais rápido, mas a consola
  mostrava um motor diferente do que o backend usa (sessão nova por pedido), e o resultado podia divergir das
  referências.
- **Escrever factos em JSON na opção 4.** É o formato do contrato, mas não se escreve à mão numa consola; a opção 14
  já aceita JSON por ficheiro.
- **Pôr a consola no backend.** O professor pode pedir para correr só o Drools; o módulo tem de se bastar.

---

## 3. Implementação

**Fase 1 — `cenarios.json` no módulo Drools (A)**
- `motores/prolog/ferramentas/gera_json.pl`: uma entrada a mais na lista `gera/2`, com o mesmo conteúdo, e os
  comentários do cabeçalho.
- `motores/drools/src/main/resources/cenarios.json` (gerado).
- `ParidadeTest`: lê o `cenarios.json` do classpath, em vez de `../../backend/...`.
- `motores/prolog/ferramentas/README.md`: a lista do que o `gera_json.pl` escreve.

**Fase 2 — Termos e sessão (B, C)**
- `consola/TermoTexto.java` (novo): converte texto Prolog em JSON e JSON em texto Prolog, com mensagens de erro de
  sintaxe.
- `consola/Cenarios.java` (novo): lê o `cenarios.json` do classpath (cenários por id, sequências, alternativas).
- `consola/SessaoConsola.java` (novo): os passos, `junta(passo)`, `corre()` e `recomeca()`, a última resposta e o
  que mudou desde a anterior (factos novos, incidentes, perguntas).
- Testes `TermoTextoTest`: ida e volta; átomo entre plicas; lista; erro de sintaxe com mensagem.

**Fase 3 — Consola (D, E)**
- `consola/ConsolaDrools.java` (novo): `main`, o ciclo do menu e as opções. Recebe um `BufferedReader` e um
  `PrintStream`, para os testes.
- `MotorDrools.main`: `--interativo` passa para a `ConsolaDrools`; sem argumentos, o modo `stdin` → JSON fica
  igual.
- Testes `ConsolaDroolsTest`, que correm a consola com entradas escritas no teste:
  - `3` `c6` → aparece o incidente `versao_por_confirmar`;
  - `3` `c7` + `5` `1` `porque` `sim` → a pergunta, o porquê com a R31, e depois da resposta o `impacto_confirmado`;
  - `4` com um facto Prolog;
  - `9` sobre um facto;
  - `13` dá o mesmo número de factos que a referência da variante `base_e_normais_30_a_34`;
  - um cenário alternativo é recusado;
  - fim de entrada sai.

**Fase 4 — Documentação (F)**
- `motores/drools/README.md`: "Correr só o Drools" com os dois modos (IntelliJ e `exec:java`).
- Secções "Getting started" / "Testing" do `README.md` da raiz.

---

## 4. Verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify
swipl motores/prolog/ferramentas/gera_json.pl --verificar
swipl -g run_tests -t halt motores/prolog/testes/testes.pl
```

- **Baseline:** o fim da F0002, com `BUILD SUCCESS` e 53 + 2 testes; os `--verificar` a 0; os 10 testes Prolog a
  passar.
- **Passa quando:** há `BUILD SUCCESS` com os testes novos; o `ParidadeTest` continua com os 21 casos verdes depois
  de passar a ler do classpath; o `gera_json.pl --verificar` sai com 0 com o ficheiro novo; o teste Prolog
  `ac18_json_em_dia` continua a passar.
- **À mão (não automatizável):** correr a `ConsolaDrools` no IntelliJ e a opção `--interativo` pelo `exec:java`, e
  confirmar que o menu aparece logo e responde ao teclado.

---

## 5. Riscos

| Risco | Como se valida que não aconteceu |
| :--- | :--- |
| **As duas cópias do `cenarios.json` divergem** | Saem do mesmo termo no `gera_json.pl`, e o `--verificar` compara as duas (corre na CI no `ac18_json_em_dia`) |
| **O `exec:java` não passa o teclado à consola**, ou o logger do Drools mistura mensagens no menu | Experimentar à mão. Se o Maven atrapalhar o `stdin`, o caminho documentado passa a ser o IntelliJ / `java -cp`, e isso fica escrito no README |
| **Reenviar o pedido inteiro a cada ação fica lento** com muitos cenários | O `ParidadeTest` corre 51 cenários em ~0,15 s por pedido. Medir a opção 13 |
| **O parser de termos aceita coisas que o Prolog não aceita**, ou o contrário (ex.: variáveis com maiúscula) | `TermoTextoTest`. Uma variável (maiúscula ou `_`) num facto dá erro, como no `introduz_facto` |
| **Mexer no `gera_json.pl`**, que é do lado Prolog | Só se acrescenta uma saída, sem mudar as que existem. Na PR fica a nota para quem o mantém |
| **A mudança do `ParidadeTest` para o classpath esconde uma cópia desatualizada** | O `gera_json.pl --verificar` apanha-a antes; e o teste continua a comparar com as referências, que vêm do ficheiro do backend |

---

## 6. Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | `build(kb): also generate cenarios.json for the Drools module` | A |
| 2 | `feat(drools): add console session and Prolog-syntax terms` | B, C |
| 3 | `feat(drools): add the interactive console with the Prolog menu options` | D, E |
| 4 | `docs(drools): document the two ways of running the Drools engine` | F |

Não há hooks de commit no repositório.

---

## 7. Fora do âmbito / a quem passa

| Fica de fora | Para |
| :--- | :--- |
| "Porque não?" (opção 10) | Para depois, se for preciso (utilizador) |
| Mudar o caminho do `cenarios.json` do backend | Não se faz (opção A) |
| UI web com as mesmas ações | #16 / UI (P6) |
