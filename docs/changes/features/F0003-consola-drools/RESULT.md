# F0003 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 18/10) |
| Branch | `feature/drools-regras-a-g-n` (a mesma da F0002), sobre `develop` @ `52cf9c7` |
| Estado | Implementado, sem commits |
| Build | `.\mvnw.cmd verify` (JDK 25) → `BUILD SUCCESS`, 67 + 2 testes. Baseline (fim da F0002 sobre `52cf9c7`): `BUILD SUCCESS`, 53 + 2 |
| Testes | 14 novos: `ConsolaDroolsTest` 9, `TermoTextoTest` 5. O `ParidadeTest` (21) passou a ler o `cenarios.json` do classpath e continua verde |
| Outros gates | `gera_json.pl --verificar` → 0 (apanhou a cópia em falta antes de ser gerada); `gera_referencia_agn.pl --verificar` → 0; `gera_meta.pl --verificar` → 0; `swipl -g run_tests -t halt motores/prolog/testes/testes.pl` → 11 testes, todos a passar (o `develop` trouxe mais um) |
| À mão | `printf "3\nc6\n6\n0\n" \| .\mvnw.cmd -q -pl motores/drools compile exec:java "-Dexec.args=--interativo"`: menu, R4 e o ticket `versao_por_confirmar`, saída. O modo `stdin` → JSON (sem argumentos) responde como antes |
| Avisos | Nenhum novo. Ao correr a consola sozinha já não aparece o `SLF4J: No SLF4J providers were found`; o aviso do JDK sobre `sun.misc.Unsafe` (da `xstream`, dentro do Drools) continua, e cala-se com uma opção da JVM (§3) |
| Commits | Nenhum: ficam para o `commit-push`, pela ordem de §6 do README |

---

## 1. O que ficou feito

| # | Superfície | Estado |
| :--- | :--- | :--- |
| A | `cenarios.json` também em `motores/drools/src/main/resources/`, escrito pelo `gera_json.pl` e verificado pelo `--verificar` (que corre na CI pelo teste Prolog `ac18_json_em_dia`) | Feito |
| B | `SessaoConsola`: a lista de passos, a última resposta e os factos novos de cada corrida. Um passo que dê erro no motor sai outra vez | Feito |
| C | `TermoTexto`: sintaxe Prolog ↔ JSON do contrato (átomos, inteiros, compostos, listas, plicas; erro com a posição; variáveis recusadas) | Feito |
| D | `ConsolaDrools`: opções 1–9, 11, 13, 14, 15 e 0, com os números e os textos do `menu/0` do Prolog. `Cenarios` lê o `cenarios.json` | Feito |
| E | `ConsolaDrools.main` (IntelliJ) e `MotorDrools.main --interativo` (linha de comandos) | Feito. Pelo `exec:java` testado com a entrada por pipe; com o teclado a sério no IntelliJ ainda não (§4) |
| F | Testes, `motores/drools/README.md` ("Correr só o Drools": os dois modos e a tabela das opções), `README.md` da raiz (tabela dos dois modos nos dois motores, testing) e `motores/prolog/ferramentas/README.md` | Feito |

---

## 2. Pontos que pedem decisão

| Ponto | Consequência operacional até alguém decidir |
| :--- | :--- |
| **As regras que usam a resposta**, no `porque` da opção 5, estão escritas na consola (R32–R34 para uma pergunta de evidência, R49 para `compromisso_no_host`, R50–R51 para uma pergunta de confirmação), e não tiradas das regras como faz o Prolog | Se as regras que leem `resposta_analista` mudarem, o `porque` mostra uma lista errada até alguém atualizar o `ConsolaDrools.porquePergunta`. As conclusões não são afetadas |
| **O `gera_json.pl` é do lado Prolog** e passou a escrever um terceiro ficheiro | Quem o mantém tem de saber que o `--verificar` falha também se o `cenarios.json` do módulo Drools estiver desatualizado. Fica a nota na PR |

---

## 3. Desvios ao plano aprovado

| Desvio | Porquê |
| :--- | :--- |
| `MotorDrools` (pacote raiz) passa a importar a `ConsolaDrools` (pacote `consola`), que importa o `MotorDrools`: há um ciclo entre os dois pacotes | É o `--interativo` do plano aprovado. A alternativa sem ciclo era tornar configurável o `<mainClass>` do `exec-maven-plugin` (`-Dexec.mainClass=...ConsolaDrools`), mas o comando ficava mais comprido. Fica à consideração |
| `TermoTexto` cria `int` quando o número cabe, e não `long` | O Jackson lê os números da resposta como `int`, e um `LongNode` não é igual a um `IntNode`. Um termo escrito na consola tem de ser igual ao da resposta (o "como?" pelo facto procura-o assim) |
| A opção 3 com vários cenários envia-os todos num só pedido, em vez de correr o motor depois de cada um | O resultado é o mesmo (cada passo corre até ao ponto fixo antes do seguinte, dentro do pedido) e evita 51 pedidos na opção 13 |
| Erro encontrado pelo `ConsolaDroolsTest` e corrigido: um facto com erro de sintaxe gastava um número de passo (`manual_2` em vez de `manual_1`) | O contador avançava antes de o texto ser validado |
| Teste da recuperação com `app_srv_01`/`ev_k1` em vez de `web_srv_01`/`ev_a1` | O `web_srv_01`/`ev_a1` já vem recuperado no cenário 39. O `ev_o1` do `web_srv_01` também não serve, porque é um compromisso que só existe por causa do problema da R43 (`F0002/PENDENTES_H_M.md` §2) |
| **Logger do módulo** (pedido do utilizador depois da implementação, ao correr a consola no IntelliJ): o `slf4j-simple` passou de `scope test` a dependência `optional`, com o nível em `error` no novo `src/main/resources/simplelogger.properties`. O Surefire põe o nível em `info` nos testes, e o `exec-maven-plugin` deixou de ter a sua cópia do `slf4j-simple` e o `systemProperty` | No IntelliJ a consola começava com `SLF4J: No SLF4J providers were found` (o `slf4j-simple` só existia nos testes e no `exec:java`). Com `optional`, entra quando o módulo corre sozinho e não passa para o backend (`dependency:tree` do backend: só o `slf4j-api` e o logger do Spring). Os logs dos testes ficam iguais: os mesmos avisos do Drools, do mesmo tipo |
| **Aviso `sun.misc.Unsafe` do JDK 25** fica como está; o README do módulo diz como o calar no IntelliJ (`--sun-misc-unsafe-memory-access=allow` em *VM options*) | Vem da `xstream`, por dentro do Drools, e não do nosso código. Pôr a opção no projeto obrigava cada forma de correr (IntelliJ, `exec:java`, testes, backend) a levá-la; confirmado à mão que com ela a consola arranca sem avisos |
| A lista de factos novos mostra só os concluídos (com o número total) | Mostrar também as entradas do cenário repetia o que se acabou de escrever. Com a opção 11 vê-se tudo |

---

## 4. Ainda não verificado

| O que | Porquê / o que é preciso |
| :--- | :--- |
| A consola com o teclado no IntelliJ e no PowerShell (`exec:java`) | Só a corri com a entrada por pipe e nos testes. Falta abrir a `ConsolaDrools` no IntelliJ (*Run*) e escrever as opções à mão, e fazer o mesmo pelo `exec:java` num terminal |
| Consola com caracteres não ASCII (ex.: um facto com acentos) | A consola usa a codificação por omissão do sistema. Os factos da base não têm acentos |

---

## 5. Como correr a verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify                                                          # 67 + 2 testes
swipl motores/prolog/ferramentas/gera_json.pl --verificar                  # as duas cópias do cenarios.json em dia
.\mvnw.cmd -q -pl motores/drools compile exec:java "-Dexec.args=--interativo"   # a consola, à mão
```

| Teste | O que prova |
| :--- | :--- |
| `ConsolaDroolsTest.cenarioMostraAsConclusoesEOsTickets` | Opções 3 e 6 |
| `ConsolaDroolsTest.perguntaComPorqueEResposta` | Opção 5: a lista, o `porque` (R31, premissas, regras que usam a resposta) e a resposta, que dá o `impacto_confirmado` da R32 |
| `ConsolaDroolsTest.cenarioAlternativoERepetidoSaoRecusados` | Alternativas (`c7b_nao` depois de `c7b_sim`) e repetidos |
| `ConsolaDroolsTest.factoEscritoEmSintaxeProlog` | Opções 4 e 11, e o erro de sintaxe |
| `ConsolaDroolsTest.comoMostraAArvoreComoOProlog` | Opção 9: regra, factos, negação com variável e condição calculada |
| `ConsolaDroolsTest.injetarTodosEOMesmoQueOPedidoDireto` | Opção 13 = o pedido com as sequências `base` + `normal` enviado direto ao motor |
| `ConsolaDroolsTest.recuperacaoDeUmaMaquina` | Opção 8 e a R63 |
| `ConsolaDroolsTest.pedidoDeUmFicheiro` | Opção 14, incluindo a recusa de outra `versaoKB` |
| `ConsolaDroolsTest.arrancaComABaseCarregadaESaiNoFimDaEntrada` | A base vem carregada; o fim da entrada sai |
| `TermoTextoTest` | Sintaxe Prolog ↔ JSON, plicas, listas, erros, variáveis das premissas |

---

## 6. Inventário de alterações

| Caminho | |
| :--- | :--- |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/ConsolaDrools.java` | novo |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/SessaoConsola.java` | novo |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/TermoTexto.java` | novo |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/Cenarios.java` | novo |
| `motores/drools/src/main/java/cyberrangers/motores/drools/MotorDrools.java` | alterado (`--interativo`) |
| `motores/drools/pom.xml` | alterado (`slf4j-simple` `optional`, nível dos testes no Surefire, `exec-maven-plugin` sem o logger próprio) |
| `motores/drools/src/main/resources/simplelogger.properties` | novo (nível `error`) |
| `motores/drools/src/main/resources/cenarios.json` | novo (gerado) |
| `motores/prolog/ferramentas/gera_json.pl` | alterado (terceira saída) |
| `motores/prolog/ferramentas/README.md` | alterado |
| `motores/drools/src/test/java/cyberrangers/motores/drools/consola/ConsolaDroolsTest.java` | novo |
| `motores/drools/src/test/java/cyberrangers/motores/drools/consola/TermoTextoTest.java` | novo |
| `motores/drools/src/test/java/cyberrangers/motores/drools/ParidadeTest.java` | alterado (lê o `cenarios.json` do classpath) |
| `motores/drools/README.md` | alterado |
| `README.md` | alterado |
| `docs/changes/features/F0003-consola-drools/README.md`, `RESULT.md` | novos |
| `docs/changes/features/README.md` | alterado (índice) |
