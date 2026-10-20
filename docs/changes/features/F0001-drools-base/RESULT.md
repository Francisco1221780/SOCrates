# F0001 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | [#4](https://github.com/Francisco1221780/SOCrates/issues/4) |
| Branch | `feature/drools-base` (base `develop` @ `92489aa`, que já traz o `factos_iniciais.json` da #10) |
| Estado | Implementado, sem commits. O motor carrega os 314 factos iniciais reais e fica pronto |
| Build | `.\mvnw.cmd verify` (JDK 25) → `BUILD SUCCESS`. Baseline: `BUILD SUCCESS`, 4 testes |
| Testes | 22 (eram 4): 20 no módulo Drools, todos novos ou reescritos (`MotorDroolsTest` 13, `TermosJsonTest` 5, `BaseFactosTest` 1, `RespostaTest` 1), a correr contra o `factos_iniciais.json` real; 2 no backend (`DroolsAdapterTest` reescrito, `SocratesApplicationTests` sem alterações) |
| Avisos | Nenhum novo. Os de antes continuam: `Unable to find pom.properties`, Mockito self-attaching, SpringDoc, e os avisos da JVM e do Maven sobre `Unsafe`/agentes |
| Commits | Nenhum: os commits ficam para o `commit-push`, pela ordem de §6 do README |

---

## 1. O que ficou feito

| # | Superfície | Estado |
| :--- | :--- | :--- |
| A | Termos: `Termo` (composto), `Variavel` (livre), `TermosJson` (JSON ↔ Java, igual ao `json_termo/2` e ao `termo_json/2`, com as mensagens do wrapper; nomes A, B, …, A1 como o `numbervars`) | Feito |
| B | `Facto` com igualdade por conteúdo, `functor`, `arg(i)`, `numero`, `origem`, `passo`, `regra`, `premissas`; `Origem` | Feito |
| C | `BaseFactos` (global `base` das regras): numeração, deduplicação, `cria(kcontext, regra, premissas, facto)`; `Premissa` (`de`, `nao`, `calculada`) | Feito |
| D | `FactosIniciais`: lê o `factos_iniciais.json` gerado pelo `gera_json.pl` (#10), `{"versaoKB", "factos"}`, e recusa outra `versaoKB`; o `MotorDrools` corre as regras sobre os dados fixos antes dos passos | Feito |
| E | `Pedido` (validação igual ao `valida_pedido/2`, `passo_<i>` por omissão) e `Resposta` (formato do `monta_resposta/2`, campo a campo) | Feito |
| F | `EstadoMotor` e `MotorDrools.estado()`: regras verificadas com `KieContainer.verify()`, factos iniciais carregados, erros com a razão | Feito |
| G | R7 em `regras/B_vetor.drl`, com o molde comentado; `00_exemplo.drl` apagado | Feito |
| H | `DroolsAdapterTest` com a R7; READMEs (raiz, `motores/drools`, `regras/`) | Feito |

O teste de paridade (`r7DaOsMesmosFactosQueOWrapperProlog`) compara o facto `incidente` e a entrada de
`incidentes` da R7 com a saída real do `wrapper.pl`: `swipl motores/prolog/wrapper.pl base_conhecimento` sobre o
cenário 6, corrido a 14/10. Corre com os mesmos 314 factos iniciais que o Prolog. Os números são os únicos campos que mudam;
a primeira premissa é verificada pelo conteúdo do facto a que aponta.

---

## 2. Pontos que pedem decisão

| Ponto | Consequência operacional até alguém decidir |
| :--- | :--- |
| **Justificação escolhida quando um facto é concluído por duas vias**: fica a primeira que o Drools disparar, e a ordem de disparo não é a do Prolog | Com a R7 sozinha não aparece diferença (as duas vias dão as mesmas premissas na forma), mas quando entrarem mais regras o comparador da AC15 (#16) pode marcar diferenças de premissas em factos que são iguais. A #16 tem de decidir se aceita qualquer justificação válida |
| **`passo` com `id` numérico**: o Drools devolve-o como string (`"1"`); o wrapper devolve o número tal como veio | Só afeta pedidos com `"id": 1` em vez de `"id": "..."`. A UI e os exemplos usam strings, por isso hoje não se vê. Se o contrato quiser números, muda-se no `Pedido` |

---

## 3. Desvios ao plano aprovado

| Desvio | Porquê |
| :--- | :--- |
| Os padrões DRL usam `functor == "vetor_aberto/4"` em vez de `predicado == "vetor_aberto"` | Com `predicado` sozinho, um facto de entrada com a aridade errada fazia o `arg(i)` lançar `IndexOutOfBoundsException` dentro da avaliação da regra. O `functor` (predicado/aridade) é como o Prolog distingue os factos e evita o problema. O molde e o README de `regras/` já usam esta forma |
| `Facto` leva `@ClassReactive` | O Drools avisava que `arg(0) == $inst` causa "class reactivity". Os factos nunca se modificam depois de inseridos, por isso a reatividade por classe não muda nada e o aviso desaparece |
| Classes organizadas em subpacotes: `modelo/` (dados imutáveis que as regras usam), `sessao/` (`BaseFactos`, `Registo`, `Origem`: a ponte com a `KieSession`) e `json/` (fronteira JSON), com `MotorDrools` e `EstadoMotor` na raiz. O número, a origem, o passo e a justificação saíram do `Facto` para um `Registo` guardado pela `BaseFactos`, e o `Facto` ficou imutável. A normalização dos termos saiu do `TermosJson` para `modelo/Termos` | Pedido do utilizador depois da implementação (os ficheiros estavam todos no mesmo pacote). As dependências vão num só sentido: `json` → `sessao` → `modelo`. Os DRL importam de `...drools.modelo` e o global é `...drools.sessao.BaseFactos`. O teste das premissas passou para `json/RespostaTest` |
| `factos_iniciais.json` é `{"versaoKB", "factos": [...]}` e não um array solto, e o leitor recusa um ficheiro de outra `versaoKB` | Foi o formato que o `gera_json.pl` (#10) produziu, já em `develop`; o plano tinha assumido um array. Verificar a versão segue a regra de não inferir com outra versão da base |
| Testes passaram a usar o `factos_iniciais.json` real; saíram as fixtures genéricas (`motores/drools/src/test/resources/factos_iniciais.json` e a cópia no backend) | Com o ficheiro real no jar do módulo, a fixture deixou de ser precisa, e a paridade com o wrapper passa a correr sobre os mesmos 314 factos. Ficam só `factos_iniciais_r7.json` e `factos_iniciais_v5.json`, para casos que o ficheiro real não cobre. A pasta vazia `backend/socrates/src/test/resources/` não se conseguiu apagar (bloqueada por outro processo); o Git ignora-a |
| `MotorDrools.main` e o `exec-maven-plugin` no `pom.xml` do módulo, para correr só o Drools pela linha de comandos (`Get-Content pedido.json \| .\mvnw.cmd -q -pl motores/drools compile exec:java`) | Pedido do utilizador: testar o motor sem o backend, que ainda não tem endpoints (#12). Mesmo uso do `wrapper.pl`: pedido no `stdin`, só o JSON no `stdout`, código 1 em erro. O `slf4j-simple` entra só como dependência do plugin, com nível `error`, porque dentro do `exec:java` o Maven junta o `stderr` ao `stdout`; o módulo continua sem logger próprio |
| Classe extra `PedidoInvalido` | Exceção com a mensagem que vai para o campo `erro`; separa os erros do pedido dos erros do motor |
| "Primeira justificação mantida" testada no `MotorDroolsTest` (`mesmoIncidentePorDuasViasFicaComAPrimeiraJustificacao`) e não no `BaseFactosTest` | O `cria` precisa de um `RuleContext`, que só existe dentro de uma regra; o teste pelo motor exercita o caminho real |
| O gate correu com `verify`, não com `clean verify` | O `clean` falhou com `Failed to delete ...\target\classes\...`, o problema conhecido do README (ficheiros abertos por outro processo). Antes do `verify`, apagou-se à mão o `00_exemplo.drl` antigo que tinha ficado em `target/classes`, para não ser carregado. A primeira tentativa de `verify` ficou parada mais de 10 minutos sem nenhum processo de testes; parou-se o Maven e a segunda correu normalmente em cerca de 1 minuto. A causa não ficou identificada |

---

## 4. Ainda não verificado

| O que | Porquê / o que é preciso |
| :--- | :--- |
| Motor "não pronto" por **erro de compilação DRL** | Não há forma limpa de pôr um DRL partido no classpath de um teste sem afetar os outros. O caminho de código existe (`KieContainer.verify()` e `catch`) mas não está exercitado. Seria preciso um teste com um `KieModule` construído à parte |
| Tempo por pedido com os 314 factos iniciais e **todas as regras** | Com só a R7, os 13 testes do `MotorDroolsTest` correm em ~1,3 s com os 314 factos. Medir de novo quando as 92 regras estiverem portadas (#13/#15) |
| Paridade com o wrapper **para lá da R7** | Fica para a #13/#16, com as outras regras e o comparador |
| `.\mvnw.cmd clean verify` numa cópia limpa | Ver §3; o `clean` falhou nesta máquina por ficheiros bloqueados em `target/` |

---

## 5. Como correr a verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify                      # os dois módulos: 19 + 2 testes
.\mvnw.cmd -pl motores/drools test     # só o módulo Drools
Get-Content pedido.json | .\mvnw.cmd -q -pl motores/drools compile exec:java   # um pedido à mão, só o Drools
```

| Teste | O que prova |
| :--- | :--- |
| `MotorDroolsTest.r7DaOsMesmosFactosQueOWrapperProlog` | A R7 dá o mesmo facto e a mesma entrada em `incidentes` que o `wrapper.pl` (fora os números) |
| `MotorDroolsTest.r7NaoDisparaSeHouverVersao`, `factoRepetidoNoutroPassoNaoSeRepete`, `mesmoIncidentePorDuasVias...` | Negação, deduplicação de entradas e de conclusões |
| `MotorDroolsTest.r7ConcluiSobreOsDadosFixosSemPasso` | As regras correm sobre os dados fixos antes dos passos (`passo: null`) |
| `MotorDroolsTest.perguntaSemRespostaFicaAAguardar`, `ticketLevaOMotivoDeFecho`, `factosIniciaisVemPrimeiro...` | Formato de `perguntas`, `tickets`, `factos` e a ordem das chaves da resposta |
| `MotorDroolsTest.pedidosInvalidosDaoAsMensagensDoWrapper`, `passoSemIdFicaPassoN` | Validação com as mensagens do wrapper |
| `MotorDroolsTest.ficaPronto...`, `semFactosIniciaisNaoFicaPronto` | AC13 do lado Drools |
| `TermosJsonTest` | Termos JSON ↔ Java; `v(2,4,49)` ≠ `"2.4.49-ubuntu1"`; `Integer` = `Long`; nomes das variáveis |
| `BaseFactosTest` | Numeração, origem, passo e factos repetidos |
| `RespostaTest` | Premissas no formato do wrapper, com os nomes das variáveis pela ordem do `numbervars` |
| `DroolsAdapterTest` | O backend chega à R7 através do adaptador |

---

## 6. Inventário de alterações

| Caminho | |
| :--- | :--- |
| `motores/drools/src/main/java/socrates/motores/drools/MotorDrools.java` | alterado |
| `motores/drools/src/main/java/socrates/motores/drools/EstadoMotor.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/modelo/Facto.java` | movido da raiz do pacote e alterado |
| `motores/drools/src/main/java/socrates/motores/drools/modelo/Termo.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/modelo/Termos.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/modelo/Variavel.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/sessao/Origem.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/modelo/Premissa.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/sessao/BaseFactos.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/sessao/Registo.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/json/TermosJson.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/json/Pedido.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/json/PedidoInvalido.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/json/Resposta.java` | novo |
| `motores/drools/src/main/java/socrates/motores/drools/json/FactosIniciais.java` | novo |
| `motores/drools/src/main/resources/regras/B_vetor.drl` | novo |
| `motores/drools/src/main/resources/regras/00_exemplo.drl` | apagado |
| `motores/drools/src/main/resources/regras/README.md` | alterado |
| `motores/drools/src/test/java/socrates/motores/drools/MotorDroolsTest.java` | alterado (reescrito) |
| `motores/drools/src/test/java/socrates/motores/drools/json/TermosJsonTest.java` | novo |
| `motores/drools/src/test/java/socrates/motores/drools/sessao/BaseFactosTest.java` | novo |
| `motores/drools/src/test/java/socrates/motores/drools/json/RespostaTest.java` | novo |
| `motores/drools/src/{main,test}/java/.gitkeep`, `src/main/resources/.gitkeep`, `src/main/resources/regras/.gitkeep` | apagados (as pastas já têm conteúdo) |
| `motores/drools/src/test/resources/factos_iniciais_r7.json` | novo (fixture: R7 sobre os dados fixos) |
| `motores/drools/src/test/resources/factos_iniciais_v5.json` | novo (fixture: versão errada) |
| `motores/drools/README.md` | alterado |
| `motores/drools/pom.xml` | alterado (`exec-maven-plugin`) |
| `backend/socrates/src/test/java/socrates/backend/engine/drools/DroolsAdapterTest.java` | alterado |
| `README.md` | alterado (estado do projeto, módulos, testing) |
| `docs/changes/features/README.md` | novo (índice) |
| `docs/changes/features/F0001-drools-base/README.md`, `RESULT.md` | novos |
