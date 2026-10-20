# F0006 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 19/10) |
| Branch | `feature/drools-menu-why`, sobre `develop` @ `bf235f3` (a mesma da F0005) |
| Estado | Implementado, sem commits |
| Build | `.\mvnw.cmd -pl motores/drools verify` (JDK 25) → `BUILD SUCCESS`, 108 testes. Baseline (fim da F0005): 101 |
| Testes | 7 a mais, todos em `MotorDroolsExplicacoesTest`: como, porque, facto pelo número e dado de entrada, nó já explicado, erros, `porquePergunta` (c7, regra 31 e `[32,33,34,50,51]`), cenários e predicados. Os testes da consola e o `PorqueNaoTest` passaram sem mudar o texto esperado |
| Outros gates | `gera_porque_nao.pl --verificar` → 0 |
| Commits | Nenhum |

---

## 1. O que ficou feito

| # | Superfície | Estado |
| :--- | :--- | :--- |
| A | `MotorDrools.como(pedido, facto)` e `explicacao/Arvore` (a árvore de factos; `jaExplicado`; premissa `facto`/`nao`/`calculada`) | Feito |
| B | `MotorDrools.porque(pedido, facto)`: a mesma árvore com `regraCompleta` em cada nó concluído | Feito |
| C | `MotorDrools.porquePergunta(pedido, ativo, evento, pergunta)`: `geradaPor` e `regrasQueUsamAResposta` | Feito |
| D | `MotorDrools.cenarios()`: o `cenarios.json` do módulo | Feito |
| E | `MotorDrools.predicadosConcluidos()` | Feito |
| F | A consola chama os métodos novos (`Explicacoes` escreve a partir do JSON; `Cenarios.de`; as listas de escolhas) e o `README.md` do módulo tem a secção "API do motor" | Feito |

---

## 2. Pontos que pedem decisão

| Ponto | Consequência operacional até alguém decidir |
| :--- | :--- |
| **O backend e a interface `MotorInferencia` não chamam nada disto** (fora do âmbito, pedido do utilizador) | A API continua a só poder fazer `infere`. Os métodos existem e estão testados, mas ninguém os expõe por REST. O resumo dos endpoints está no fim da resposta da tarefa e no README do módulo |
| **O motor Prolog não tem os métodos equivalentes** | Se a interface `MotorInferencia` ganhar `como`/`porque`/`porqueNao`, o Prolog tem de responder (ou dizer que não suporta) |
| **Cada explicação volta a correr o pedido inteiro** | Com muitos cenários (opção 14 da consola) cada "como?"/"porque?" demora um pouco mais que antes (a consola reusava a resposta já em memória). Não medido com os 51 cenários |

---

## 3. Desvios ao plano aprovado

| Desvio | Porquê |
| :--- | :--- |
| `explicacao/Cenarios.java` não foi criada: o `cenarios()` devolve o JSON do recurso tal como está, e a `consola/Cenarios` ganhou `Cenarios.de(JsonNode)` | O `cenarios.json` já é o contrato; uma segunda classe só duplicava os campos |
| O plano dizia "5 métodos novos" e "`ArvoreTest`"; os testes ficaram todos em `MotorDroolsExplicacoesTest` | Chamam o `MotorDrools`, que é o que a API usa; a `Arvore` é coberta através dele |

---

## 4. Ainda não verificado

| O que | Porquê / o que é preciso |
| :--- | :--- |
| Os métodos com o backend real | O backend não os chama; só se vê quando houver endpoints |
| O tempo de uma explicação com a opção 14 (todos os cenários) | Não medido; ver §2 |

---

## 5. Como correr a verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd -pl motores/drools verify
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar
```

---

## 6. Inventário de alterações

| Caminho | Estado |
| :--- | :--- |
| `motores/drools/src/main/java/cyberrangers/motores/drools/explicacao/Arvore.java` | Novo |
| `motores/drools/src/main/java/cyberrangers/motores/drools/MotorDrools.java` | Alterado: `como`, `porque`, `porquePergunta`, `cenarios`, `predicadosConcluidos` |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/Explicacoes.java`, `ConsolaDrools.java`, `Cenarios.java` | Alterados: usam a API |
| `motores/drools/src/test/java/cyberrangers/motores/drools/MotorDroolsExplicacoesTest.java` | Novo |
| `motores/drools/README.md` | Alterado: secção "API do motor" |
| `docs/changes/features/F0006-drools-api-explicacoes/README.md`, `RESULT.md` | Novos |
| `docs/changes/features/README.md` | Alterado: linha da F0006 |
