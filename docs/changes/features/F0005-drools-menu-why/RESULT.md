# F0005 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 19/10) |
| Branch | `feature/drools-menu-why`, sobre `develop` @ `bf235f3` |
| Estado | Implementado, sem commits |
| Build | `.\mvnw.cmd -pl motores/drools verify` (JDK 25) → `BUILD SUCCESS`, 101 testes. Baseline em `develop`: 89 |
| Testes | 12 a mais: `ConsolaDroolsTest` +9 (factos sem sintaxe Prolog, prompts sem ponto, porque nao com a regra inteira, porque nao de um facto que existe, "Impede" numa condição negada, "porque?" com a regra completa, "porque?" de um dado / número inexistente / facto ausente, listas de escolhas, pausa com Enter, ordem das três explicações no menu; os testes antigos ajustados à numeração nova), `TermoTextoTest` +1, `PorqueNaoRegraTest` +2 |
| Outros gates | `gera_porque_nao.pl --verificar` → 0; `gera_json.pl --verificar` → 0 |
| Commits | Nenhum |

---

## 1. O que ficou feito

| # | Superfície | Estado |
| :--- | :--- | :--- |
| A | Factos sem sintaxe Prolog: a opção 4 pede um facto por linha (linha vazia termina), `nome(arg, arg)`, sem plicas nem ponto; as listagens, o "como?" e as explicações escrevem `nome(arg, arg)` (`TermoTexto.legivel`). Um valor como `2.4.49-ubuntu1` entra sem plicas | Feito |
| B | Prompts e mensagens sem o ponto do Prolog (opções 1, 3, 5, 11, 12). O ponto e as plicas continuam a ser aceites se forem escritos | Feito |
| C | `PorqueNao`: cada linha `regra` traz o `se` completo (cada condição com `tipo`, `verificada`, `numero`) e o `entao`; `PorqueNao.regra(id)` devolve a regra do `regras.json` com os nomes originais das variáveis | Feito |
| D | Opção 10 "porque?": o facto, a regra escrita por inteiro (`SE`/`E`/`ENTAO`) e os factos que a satisfizeram, a descer até aos dados, sem repetir um facto já explicado. O `porque` das perguntas (opção 5) passou a usar o mesmo texto | Feito |
| E | Opção 11 ("porque não?") com o formato novo: a regra inteira com `[ok]`/`[FALTA]` por condição, depois `- Falta:` / `- Impede:` e o que falta por baixo, 4 espaços por nível. O formato Prolog ficou só em `Explicacoes.linhaPorqueNaoProlog`, que o `PorqueNaoTest` continua a comparar com o `whynot/1` | Feito |
| F | `README.md` do módulo e da raiz atualizados | Feito |
| G | Menu numerado por ordem, de 0 a 14: 9 como, 10 porque, 11 porque não, 12 mostrar factos, 13 opções do motor, 14 injetar todos. Os números a partir da 10 têm mais um do que no Prolog | Feito (pedido do utilizador depois do plano) |
| H | Cada pedido mostra as escolhas: as opções 10 e 9 listam as conclusões por predicado e o nome de um predicado lista-as com o número; a 11 lista o que as regras podem concluir; a 12 lista os predicados que há | Feito (pedido do utilizador depois do plano) |
| I | Pausa depois de cada opção ("Enter para voltar ao menu"), só quando corre no teclado (`main`); os testes usam a consola sem pausa | Feito (pedido do utilizador depois do plano) |

---

## 2. Pontos que pedem decisão

| Ponto | Consequência operacional até alguém decidir |
| :--- | :--- |
| **Os números do menu já não coincidem com os do Prolog a partir da 10** (o "porque não?" é a 11, as opções do motor a 13, injetar todos a 14) | Quem conhece o menu do Prolog tem de olhar para o número antes de escolher; quem compara as duas consolas lado a lado vê números diferentes para o mesmo texto |
| **O "porque?" mostra a regra do `soc_regras.txt` (`regras.json`), não o DRL que corre** | O texto é o da regra Prolog; a paridade das duas é garantida pelo `ParidadeTest`, mas quem editar uma regra no DRL sem mudar o `soc_regras.txt` vê a explicação desatualizada até regenerar o `regras.json` |
| **Argumentos nus não podem ter espaços, vírgulas nem parênteses** | Um valor assim só entra entre plicas (continuam aceites). Os dados atuais (`factos_iniciais.json`, cenários) não têm nenhum |

---

## 3. Desvios ao plano aprovado

| Desvio | Porquê |
| :--- | :--- |
| **A apresentação das três explicações ficou numa só classe nova, `consola/Explicacoes`, e não em `ApresentaPorque`**; a lógica do "como?" e do `porquePergunta` saiu da `ConsolaDrools` | Pedido do utilizador depois do plano ("juntaria o porque ao outro módulo de explicações", "porque, como, porque não, em opções separadas"); o README foi revisto antes da aprovação |
| **`PorqueNaoTest` mudou de `ConsolaDrools.linhaPorqueNao` para `Explicacoes.linhaPorqueNaoProlog`** (só o nome da classe, o texto é o mesmo) | O método mudou com as explicações |
| **A numeração do menu passou a sequencial** (9 como, 10 porque, 11 porque não, 12–14 o que era 11–13), em vez de a 14 ficar a seguir à 13 | Pedido do utilizador ("o menu tem que estar por ordem lógica"); o plano dizia que os números 0–13 ficavam iguais ao Prolog. As mensagens que citam uma opção (`opcao 11`, `opcao 13`) e os testes foram atualizados |
| **Listas de escolhas em cada pedido e pausa com Enter**, que não estavam no plano | Pedidos do utilizador durante a implementação ("em cada opção poderia mostrar as opções que temos", "o terminal poderia ficar parado até fazeres enter") |
| **O JSON da linha `facto_verdadeiro` ganhou `encontrado`** (o facto com os valores achados) | Para o "Impede" mostrar `versao(web_srv_02_apache, v(2, 4, 52))` e não `versao(web_srv_02_apache, A)`. É um campo a mais; o formato Prolog não o usa |
| **Os testes de `PorqueNaoTest` não ganharam factos novos** | O plano previa só testes do formato novo; o formato Prolog não mudou |

---

## 4. Ainda não verificado

| O que | Porquê / o que é preciso |
| :--- | :--- |
| Ler o texto novo no IntelliJ e julgar se está claro; ver a pausa com Enter no teclado | O texto e a pausa estão testados com a entrada escrita no teste, mas só o utilizador pode confirmar que o "porque não?" deixou de ser confuso e que a pausa se sente bem |
| "Porque?" e "porque não?" para factos cuja cadeia passa pelas causas H–M | Como na F0004: a base de factos pode divergir do Prolog nesses casos (`F0002/PENDENTES_H_M.md`) |
| O "porque?" de factos com árvores muito grandes (a opção 13 com tudo injetado) | Não medido; o "já explicado acima" limita a repetição, mas a saída pode ter centenas de linhas |

---

## 5. Como correr a verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd -pl motores/drools verify
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar
swipl motores/prolog/ferramentas/gera_json.pl --verificar
```

- O `verify` corre os 101 testes: o `PorqueNaoTest` prova que o formato Prolog continua igual ao `whynot/1`; o `ConsolaDroolsTest` prova o texto novo.
- Os dois `swipl` provam que as referências e os JSON gerados não ficaram desatualizados (não se mexeu em regras, factos nem cenários).

---

## 6. Inventário de alterações

| Caminho | Estado |
| :--- | :--- |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/Explicacoes.java` | Novo: "como?", "porque?" e "porque não?" (formato novo e formato Prolog) |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/ConsolaDrools.java` | Alterado: opção 14, `escolheFacto`, entrada de factos, prompts sem ponto, chama `Explicacoes` (saíram o `explica`, `premissaComo1`, `linhaPorqueNao`) |
| `motores/drools/src/main/java/cyberrangers/motores/drools/consola/TermoTexto.java` | Alterado: `legivel`, leitura de argumentos sem plicas |
| `motores/drools/src/main/java/cyberrangers/motores/drools/explicacao/PorqueNao.java` | Alterado: `regra(id)`, `se`/`entao` completos nas linhas `regra`, `encontrado` |
| `motores/drools/src/test/java/.../consola/ConsolaDroolsTest.java` | Alterado: testes novos e textos ajustados (espaços depois das vírgulas, prompts, linha vazia a terminar a opção 4) |
| `motores/drools/src/test/java/.../consola/TermoTextoTest.java` | Alterado: leitura sem plicas, erros novos |
| `motores/drools/src/test/java/.../consola/PorqueNaoTest.java` | Alterado: usa `Explicacoes.linhaPorqueNaoProlog` |
| `motores/drools/src/test/java/.../explicacao/PorqueNaoRegraTest.java` | Novo |
| `motores/drools/README.md`, `README.md` | Alterados: o menu com a 14, a entrada de factos, o formato novo |
| `docs/changes/features/F0005-drools-menu-why/README.md`, `RESULT.md` | Novos |
| `docs/changes/features/README.md` | Alterado: linha da F0005 |
