# F0002 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | [#13](https://github.com/Francisco1221780/CyberRangers/issues/13) |
| Branch | `feature/drools-regras-a-g-n`. Implementado sobre `845bcb9` (PR #31); depois do merge do #31 (18/10), a branch passou para `develop` @ `52cf9c7` e o gate voltou a correr verde |
| Estado | Implementado, sem commits. As 37 regras A–G e N estão portadas e dão os mesmos factos, regras, premissas e passos que o Prolog nas 10 variantes de cenários |
| Build | `.\mvnw.cmd verify` (JDK 25) → `BUILD SUCCESS`. Baseline em `845bcb9`: `BUILD SUCCESS`, 20 + 2 testes |
| Testes | 55 (eram 22): 53 no módulo Drools e 2 no backend (sem alterações). 33 são novos: `ParidadeTest` 21 (20 casos de paridade e 1 de consistência das referências), `CondicoesCalculadasTest` 10, 1 no `BaseFactosTest` (marcas) e 1 no `MotorDroolsTest` (grupo N). Há 3 testes alterados (§3) |
| Outros gates | `swipl motores/drools/ferramentas/gera_referencia_agn.pl --verificar` → 0; `gera_json.pl --verificar` → 0; `gera_referencias.pl --verificar` → 0; `swipl -g run_tests -t halt motores/prolog/testes/testes.pl` → 10 testes, todos a passar |
| Avisos | Nenhum tipo novo. O aviso `EvalCondition ... eval is not performance-efficient` já existia em `845bcb9` (as regras H–M do #31); nas duas corridas o Drools chega ao limite de 10 mensagens e cala as restantes. As regras A–G acrescentam `eval` do mesmo tipo (são as condições calculadas). Continuam os avisos de sempre: `Unable to find pom.properties` e os da JVM sobre `Unsafe` e acesso nativo |
| Commits | Nenhum: ficam para o `commit-push`, pela ordem de §3 (que ajusta o §6 do README) |

---

## 1. O que ficou feito

| # | Superfície | Estado |
| :--- | :--- | :--- |
| A | R1–R3 (`A_afetado.drl`) | Feito |
| B | R4–R6, R8, R9 (`B_vetor.drl`, junto da R7) | Feito |
| C | R10–R13 (`C_sem_impacto.drl`) | Feito |
| D | R14–R15 (`D_duvida.drl`) | Feito |
| E | R16–R19 (`E_impactos.drl`) | Feito |
| F | R20–R30 (`F_confirmacao.drl`) | Feito |
| G | R31–R34 (`G_pergunta.drl`) | Feito |
| N | R89–R92 (`N_catalogo.drl`) | Feito. Nenhum cenário dispara o grupo N; testado com a fixture `factos_iniciais_catalogo.json` contra a saída real do `wrapper.pl` sobre os mesmos 9 factos |
| O | `CondicoesCalculadas.origemEvidencia`, igual ao `classifica_origem`, com os 7 resultados testados | Feito |
| P | `Marca` + `BaseFactos`: reavaliação quando chega um facto lido por uma condição calculada (`condicao_le/2`) | Feito. Com as marcas desligadas (experiência de 18/10), o nível A–G+N dá 14 a 18 diferenças por variante, todas em telemetria que chega atrasada (c20b, c29b, c35c, c38b); com elas, 0 |
| Q | `motores/drools/ferramentas/gera_referencia_agn.pl` e 10 JSON em `src/test/resources/referencia_agn/` | Feito. Entre as variantes, disparam todas as regras R1–R34. O R89–R92 não dispara em nenhuma (o catálogo está completo) |
| R | `ParidadeTest` + `ComparaRespostas` | Feito. **A–G+N: 0 diferenças nas 10 variantes.** Completo: as diferenças que restam têm todas a causa de raiz numa regra H–M listada (§2) |
| S | Tabela dos factos entre metades (`regras/README.md`) e READMEs | Feito. A tabela foi tirada das regras do `soc_regras.txt` com um script, não à mão |

"Feito quando" da issue: para os cenários e as variantes de referência, com as mesmas regras ativas, o Drools dá os
mesmos factos (AC14), com a mesma regra e as mesmas premissas (AC15), no mesmo passo. É o nível A–G+N do
`ParidadeTest`, que corre em cada `mvnw verify`.

---

## 2. Pontos que pedem decisão

| Ponto | Consequência operacional até alguém decidir |
| :--- | :--- |
| **Causas H–M no nível completo (PR #31, já em `develop`)** — o que corrigir, regra a regra, está em [`PENDENTES_H_M.md`](PENDENTES_H_M.md). Listadas em `ParidadeTest.CAUSAS_H_M` / `ERROS_H_M`: **R43** não verifica a `origem_evidencia`; **R35, R74, R75** escrevem as premissas `avalia(...)` como condições calculadas; **R66** não se reavalia quando o `destino` chega depois; as **regras L (R64–R72)** põem um `Facto` dentro de `revisto/2` | Com as 92 regras, o Drools **responde `estado: "erro"`** às sequências `base_e_atualizacoes` e `c35d_em_vez_de_35c` (`termo nao suportado`). Confirma um compromisso no c26 que o Prolog não confirma, e isola a máquina, abre o ticket e fecha outros tickets por isso. Confirma o do c11 um passo mais cedo. Não fecha o incidente do c37b. A UI e o comparador vão mostrar estas diferenças até a #15 as corrigir. A `origemEvidencia` desta alteração é a que a R43 deve chamar. Quando uma causa for corrigida, sai da lista |
| **Esta branch altera o `CondicoesCalculadas` do #31** (`origemEvidencia` e a correção da `evidenciasNegadas`) | O #31 já entrou e a branch está sobre `develop`. Se alguém mudar o `CondicoesCalculadas` em `develop` antes de esta branch entrar, há conflito nesse ficheiro. As correções do `PENDENTES_H_M.md` devem começar depois do merge desta branch (precisam do `ParidadeTest` e da `origemEvidencia`) |
| **Justificação escolhida entre regras diferentes** para o mesmo facto: a `BaseFactos` só imita a escolha do Prolog dentro da mesma regra; entre regras diferentes fica a que o Drools disparar primeiro | Nas 10 variantes não aparece (as vias entre regras diferentes das A–G são mutuamente exclusivas, ex.: R7 com `nao versao` e R8 com `versao`). Pode aparecer com cenários novos, como diferença de premissas na AC15. O ponto vem da F0001 e continua para a #16 |
| **As 10 variantes estão escritas em dois geradores** (`gera_referencias.pl` e `gera_referencia_agn.pl`) | Se alguém acrescentar uma variante só a um, o `as_duas_referencias_tem_os_mesmos_cenarios` não o apanha (só compara as que estão na lista do teste), e a variante nova fica sem paridade A–G+N. Alternativa: o `gera_referencias.pl` exportar a lista |

---

## 3. Desvios ao plano aprovado

| Desvio | Porquê |
| :--- | :--- |
| **Escolha da justificação na `BaseFactos` (`comoNoProlog`)** em vez de `salience` | A única diferença A–G+N que restou depois de portar as regras foi a R4 com dois serviços do `web_srv_01` (portos 80 e 443): a mesma regra, duas vias. O Prolog fica com a dos factos de número mais baixo; o Drools disparava primeiro a mais recente. O `salience` ordena regras, não ativações da mesma regra, por isso não resolvia. A `BaseFactos` troca a justificação se for a mesma regra, no mesmo passo, se as premissas novas forem todas anteriores ao facto e se os números ordenados forem lexicograficamente menores. Não foi preciso `salience` em nenhuma regra |
| **Correção da `evidenciasNegadas` do #31** | O Prolog aceita qualquer pergunta da evidência com resposta "nao"; o Java só via a primeira. Corrigido ao rever a condição contra o `soc_suporte.txt` (a R33 usa-a), e não por uma diferença na paridade: nos cenários cada evidência tem uma só pergunta |
| **`Facto.representadoPor(Object)`** (novo método no modelo) | As regras L do #31 põem o facto revisto como `Facto` no 1.º argumento de `revisto/2`; o modelo e o JSON usam `Termo`. As negações `nao revisto(exploracao_possivel(...), _)` da R13 e da R30 aceitam as duas formas, para não dependerem dessa escolha do #31 |
| **As regras carregam-se pela ordem do nome dos DRL** (`MotorDrools.baseOrdenada`, com um `KnowledgeBuilder`), e já não com o `getKieClasspathContainer()` | Encontrado na CI do PR #33 (Linux): 18 casos do `ParidadeTest` falhavam, e cá (Windows) passavam. A ordem em que o Drools carrega as regras decide qual de duas ativações ao mesmo tempo dispara primeiro (ex.: a R5 antes da R11 para o `ev_d1`), e isso decide a justificação do `origem_hostil` e do incidente da R12. O contentor do classpath lia a pasta pela ordem do sistema de ficheiros: alfabética no Windows, arbitrária no Linux. Confirmado cá: com a ordem invertida voltam as mesmas 18 falhas; com a ordem do nome, 0 |
| **Causa de raiz no nível completo** (`ComparaRespostas.causasNaOutraMetade`): segue as premissas até à primeira diferença | O plano dizia "por regra, com a razão". Pela regra do próprio facto, a lista tinha 18 regras H–M, quase todas consequência de outra diferença (ex.: a `gravidade` de um incidente que só existe no Drools). Pela causa de raiz ficam 5 regras e um erro, cada um com a razão |
| **3 testes existentes alterados** | `MotorDroolsTest.r7NaoDisparaSeHouverVersao`: dizia "nenhum incidente", mas agora a R1/R6/R30 abrem, como devem, "exploracao_possivel"; passa a verificar que não há o incidente da R7, que é o que o teste quer provar. `factosIniciaisVemPrimeiroComOrigemInicial`: procurava a entrada pelo número 331, que muda com as conclusões sobre os dados fixos (R1–R3); passa a procurá-la pelo conteúdo. `BaseFactosTest`: `getFactCount()` conta também as 14 marcas; passa a contar só os `Facto` |
| **Ordem dos commits (§6 do README)** | A fase 3 não pode entrar com a fase 4 sozinha: o `ParidadeTest` só fica verde com A–G todos, e o `r7NaoDispara...` só falha quando entra a R30 (grupo F). Ordem que compila e passa em cada passo: (1) `Marca`/`BaseFactos` + `BaseFactosTest`; (2) `origemEvidencia` + `evidenciasNegadas` + `CondicoesCalculadasTest`; (3) gerador + `referencia_agn/`; (4) `A_afetado.drl` a `D_duvida.drl` + `Facto.representadoPor` + o teste `factosIniciaisVemPrimeiro...`; (5) `E_impactos.drl` a `G_pergunta.drl` + `comoNoProlog` + `MotorDrools` com filtro + `ParidadeTest`/`ComparaRespostas` + o teste `r7NaoDispara...`; (6) `N_catalogo.drl` + fixture + teste; (7) READMEs |
| `regras/README.md`: a tabela de ficheiros passou a ter os nomes reais das regras H–M (`I_movimento_lateral.drl`, `J_comunicacao_c2.drl`, `M_gravidade_prioridade.drl`) | O #31 usou nomes diferentes dos que o README previa |
| Exemplo "Correr só o Drools" do README do módulo passou a usar a telemetria do cenário 6 | Com a R4 portada, já não é preciso injetar o `vetor_aberto` à mão. Testado com `exec:java`: R4 e depois R7, e as regras M do #31 juntam o ticket |

---

## 4. Ainda não verificado

| O que | Porquê / o que é preciso |
| :--- | :--- |
| Paridade com cenários fora das 10 variantes (ex.: cada cenário sozinho, com os de que depende) | As referências só cobrem as variantes do `gera_referencias.pl`. Seria preciso acrescentar variantes aos dois geradores |
| Grupo N com o motor Prolog no build | O valor esperado do teste do grupo N foi copiado de uma corrida à mão do `wrapper.pl` sobre uma base temporária com os 9 factos (18/10). Não fica um gerador para o regenerar |
| `.\mvnw.cmd clean verify` | Correu `verify` sem `clean`. Esta alteração não apaga nenhum DRL (o problema do `target/classes` da F0001 não se põe), mas o `clean` não foi experimentado |
| Desempenho com o backend | O `ParidadeTest` faz 20 pedidos com 46–51 cenários cada em ~3 s (com as 92 regras e os 314 factos iniciais). Não se mediu através do backend (#12) |

---

## 5. Como correr a verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify                                                   # os dois módulos: 53 + 2 testes
.\mvnw.cmd -pl motores/drools test -Dtest=ParidadeTest              # só a paridade (21 casos)
swipl motores/drools/ferramentas/gera_referencia_agn.pl --verificar  # a referência A–G+N está em dia
```

| Teste | O que prova |
| :--- | :--- |
| `ParidadeTest` `agn <variante>` (10) | Com só A–G+N ativas, o Drools dá exatamente os mesmos factos, origem, passo, regra e premissas que o `wrapper.pl` com essas regras (o "feito quando") |
| `ParidadeTest` `completo <variante>` (10) | Com as 92 regras, as diferenças para a `referencia_prolog/` têm todas uma causa de raiz H–M conhecida; uma diferença nova, ou com causa A–G/N, faz o teste falhar |
| `ParidadeTest.as_duas_referencias_tem_os_mesmos_cenarios` | Os dois geradores correram os mesmos cenários em cada variante |
| `CondicoesCalculadasTest` | Os 7 resultados da `origem_evidencia`, e o vetor revisto a deixar de ligar a instância |
| `BaseFactosTest.factoLidoPorUmaCondicaoCalculadaMudaAMarca` | Um facto lido por uma condição calculada muda a marca (uma vez por facto novo); os outros não |
| `MotorDroolsTest.catalogoComFalhasDaAsMesmasConclusoesQueOProlog` | R89–R92 iguais à saída do Prolog sobre a mesma fixture |

---

## 6. Inventário de alterações

| Caminho | |
| :--- | :--- |
| `motores/drools/src/main/resources/regras/A_afetado.drl` | novo |
| `motores/drools/src/main/resources/regras/B_vetor.drl` | alterado (R4–R6, R8, R9; molde com as marcas) |
| `motores/drools/src/main/resources/regras/C_sem_impacto.drl` | novo |
| `motores/drools/src/main/resources/regras/D_duvida.drl` | novo |
| `motores/drools/src/main/resources/regras/E_impactos.drl` | novo |
| `motores/drools/src/main/resources/regras/F_confirmacao.drl` | novo |
| `motores/drools/src/main/resources/regras/G_pergunta.drl` | novo |
| `motores/drools/src/main/resources/regras/N_catalogo.drl` | novo |
| `motores/drools/src/main/resources/regras/README.md` | alterado |
| `motores/drools/src/main/java/cyberrangers/motores/drools/sessao/Marca.java` | novo |
| `motores/drools/src/main/java/cyberrangers/motores/drools/sessao/BaseFactos.java` | alterado (marcas, `comoNoProlog`) |
| `motores/drools/src/main/java/cyberrangers/motores/drools/sessao/CondicoesCalculadas.java` | alterado (`origemEvidencia`, `evidenciasNegadas`) |
| `motores/drools/src/main/java/cyberrangers/motores/drools/modelo/Facto.java` | alterado (`representadoPor`) |
| `motores/drools/src/main/java/cyberrangers/motores/drools/MotorDrools.java` | alterado (filtro de regras, package-private) |
| `motores/drools/src/test/java/cyberrangers/motores/drools/ParidadeTest.java` | novo |
| `motores/drools/src/test/java/cyberrangers/motores/drools/ComparaRespostas.java` | novo |
| `motores/drools/src/test/java/cyberrangers/motores/drools/sessao/CondicoesCalculadasTest.java` | novo |
| `motores/drools/src/test/java/cyberrangers/motores/drools/sessao/BaseFactosTest.java` | alterado |
| `motores/drools/src/test/java/cyberrangers/motores/drools/MotorDroolsTest.java` | alterado (grupo N; 2 testes ajustados) |
| `motores/drools/src/test/resources/factos_iniciais_catalogo.json` | novo (fixture do grupo N) |
| `motores/drools/src/test/resources/referencia_agn/*.json` | novos (10, gerados) |
| `motores/drools/ferramentas/gera_referencia_agn.pl` | novo |
| `motores/drools/ferramentas/README.md` | novo |
| `motores/drools/README.md` | alterado |
| `README.md` | alterado (estado do projeto, testing) |
| `docs/changes/features/F0002-drools-regras-a-g-n/README.md`, `RESULT.md`, `PENDENTES_H_M.md` | novos |
| `docs/changes/features/README.md` | alterado (índice) |
