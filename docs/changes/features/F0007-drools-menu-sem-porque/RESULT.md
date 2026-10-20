# F0007 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Branch | `feature/drools-menu-sem-porque`, sobre `develop` @ `a50d92c` |
| Estado | Implementado, sem commits |
| Testes | `ConsolaDroolsTest` (22) e `PorqueNaoTest` (10) passam. Saiu o teste `porqueMostraARegraCompletaEOsFactos` (a opção já não existe; o texto do "porque?" continua coberto por `perguntaComPorqueEResposta` e `MotorDroolsExplicacoesTest`); o teste de factos inexistentes passou a usar o "como?"; o do menu verifica que não há "(porque?)" nem opção 14 |

## O que mudou

| Ficheiro | Alteração |
| :--- | :--- |
| `consola/ConsolaDrools.java` | Menu e `executa` sem a opção 10, numeração igual à do Prolog; saiu `porque()` |
| `consola/Explicacoes.java` | Mensagens "opcao 13" passam a "opcao 12"; comentários |
| `MotorDrools.java` | Comentário de `porque` |
| `ConsolaDroolsTest.java`, `PorqueNaoTest.java` | Números das opções e testes acima |
| `motores/drools/README.md` | Tabela do menu e notas |

## Em aberto

- O "porque?" da pergunta mostra a razão da pergunta (a regra que a gerou e as regras que usam a resposta) mas não o que cada resposta permite concluir, como descreve o relatório de aquisição de conhecimento. Não foi mexido.
- A documentação da F0005 (opção 14, opção 10) e da F0006 refere a numeração antiga; ficou como registo histórico.
