# F0004 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 18/10) |
| Branch | `feature/drools-regras-a-g-n`, sobre `develop` @ `52cf9c7` |
| Estado | Implementado, sem commits |
| Build | `.\mvnw.cmd verify` (JDK 25) → `BUILD SUCCESS`, 89 + 2 testes. Baseline (fim da F0003): 67 + 2 |
| Testes | 22 a mais: `PorqueNaoTest` 10, `UnificacaoTest` 7 e `ConsolaDroolsTest` 14 (eram 9; reescrito para o menu novo) |
| Outros gates | `gera_json.pl --verificar` → 0 (com o `regras.json`); `gera_referencia_agn.pl --verificar` → 0; `gera_porque_nao.pl --verificar` → 0; `gera_meta.pl --verificar` → 0; testes Prolog: 11, todos a passar |
| Comparação com o Prolog | **"Porque não?"**: 10 factos, 126 linhas, iguais ao `whynot/1` linha a linha (sem os números dos factos e com a dica de profundidade a apontar para a opção 12). **Opção 13**: os mesmos cabeçalhos e o mesmo número de conclusões novas que o `injeta_todos` em 47 dos 52 cenários; os 5 que diferem (c11, c11b, c26, c28, c37b) são as causas H–M do `F0002/PENDENTES_H_M.md` (R43 e R66) |
| Commits | Nenhum |

---

## 1. O que ficou feito

| # | Superfície | Estado |
| :--- | :--- | :--- |
| A | `regras.json` (92 regras, 19 condições calculadas), escrito pelo `gera_json.pl`. Os operadores das regras ficam num módulo à parte (`ops_regras`), só para ler o `soc_regras.txt`; os outros JSON saem iguais | Feito |
| B | `explicacao/Unificacao`, `Var`, `Regras`, `Suporte`: termos com variáveis, unificação, e as condições avaliadas como no Prolog. As soluções vêm pela ordem dos factos, com a primeira solução de cada condição; as calculadas também funcionam com argumentos por ligar | Feito |
| C | `explicacao/PorqueNao`: o `whynot/2`, ramo a ramo (facto verdadeiro, `nao`, calculada, já visto, dado de entrada, profundidade, "falta correr o motor", premissas falsas com a regra de não ligar variáveis partilhadas com uma premissa que falhou) | Feito |
| D | `MotorDrools.porqueNao(pedido, facto, profundidade)`; `MotorDrools.comFactosIniciais(Path)` e `FactosIniciais.carrega(Path, ...)` para a opção 1; `MotorDrools.numeroRegras()` | Feito |
| E | Menu 0–13 com os textos do `menu/0`: a 1 carrega a base; a 2 corre o motor; a 3 aceita id, lista, `.txt` de cenário Prolog ou `.json`; a 10; a 12. As mensagens são as do `arranca_motor` (`N factos novos (n. X a Y).`, `Nao ha conclusoes novas.`, `O facto F ja existe (n. N).`, `Foi concluido o facto n. ...`). Os termos saem com plicas nas explicações e sem plicas nas listagens, como o Prolog | Feito |
| F | Testes, `ferramentas/gera_porque_nao.pl`, READMEs (módulo, `ferramentas/` dos dois lados, raiz) | Feito |

---

## 2. Pontos que pedem decisão

| Ponto | Consequência operacional até alguém decidir |
| :--- | :--- |
| **As condições calculadas existem agora três vezes**: no `soc_suporte.txt` (Prolog), no `sessao/CondicoesCalculadas` (as regras DRL) e no `explicacao/Suporte` (o "porque não?", com variáveis por ligar) | Quem mudar uma condição no `soc_suporte.txt` tem de a mudar nos dois sítios do Java. A paridade das regras (`ParidadeTest`) apanha o primeiro; o segundo só é apanhado pelo `PorqueNaoTest`, se um dos 10 factos passar por essa condição. Alternativa: o `CondicoesCalculadas` passar a usar o `Suporte` (com os argumentos ligados dá o mesmo), o que deixa só uma cópia em Java |
| **O menu não é igual em três pontos**: arranca já com a base carregada; a dica de profundidade aponta para a opção 12; as "perguntas por responder" são as da resposta do motor | Quem conhece o menu do Prolog não precisa de carregar a base antes. O contador de perguntas pode ser menor do que o do Prolog quando houver respostas "nao sei": o Prolog continua a contá-las, e a resposta do motor (o contrato do `wrapper.pl`) não |

---

## 3. Desvios ao plano aprovado

| Desvio | Porquê |
| :--- | :--- |
| **As condições calculadas do "porque não?" não chamam o `CondicoesCalculadas`**: estão escritas outra vez, como relações, no `Suporte` | O plano dizia "com um argumento por ligar, é falsa". Mas no Prolog uma calculada com variáveis procura nos factos e liga-as (ex.: `anterior(EvAnterior, ev_e1)` é verdadeira se houver um evento antes). Só o `instantiation_error` conta como falso. Com a regra do plano, o "porque não?" listava como falsas condições que o Prolog dá como verdadeiras. Os 10 casos do `PorqueNaoTest` passam por estas condições (`depois`, `origem_evidencia`, `versao_cmp`, `fora_dos_intervalos`, …) |
| **Gerador `ferramentas/gera_porque_nao.pl`**, em vez de copiar o texto esperado à mão | O texto do `whynot/1` tem 126 linhas. Com o gerador (e o `--verificar`), a referência acompanha as regras e os cenários |
| **`MotorDrools.numeroRegras()` pede a base pelo nome (`soc`)** | O `kmodule.xml` declara a base `soc`, que não é a base por omissão; `getKieBase()` sem nome dava "Cannot find a default KieBase" (apanhado na primeira corrida da consola) |
| **As regras que usam a resposta**, no `porque` da opção 5, saem agora das regras (`PorqueNao.regrasComCondicao`), como no Prolog | Resolve o ponto aberto da F0003 §2 (estavam escritas na consola). Para `shell_servico` dá `[32,33,34,50,51]`, como o Prolog |
| A consola deixou de escrever "Ate a proxima." ao sair | O menu do Prolog sai sem mensagem |
| Um ficheiro relativo da opção 3 procura-se na pasta atual e nas de cima (em cada uma também dentro de `base_conhecimento/`) | O IntelliJ e o Maven correm na pasta do módulo, e o caminho do exemplo do Prolog (`'cenarios/cenario_30.txt'`) é relativo à base |
| Variável `_Nome` lida como anónima no facto da opção 10 | No Prolog, `_Nome` é uma variável com nome. O caso não aparece nos factos de exemplo; fica anotado |

---

## 4. Ainda não verificado

| O que | Porquê / o que é preciso |
| :--- | :--- |
| O menu com o teclado no IntelliJ, e o `passo_a_passo` | Testado com a entrada escrita nos testes e por pipe; falta à mão |
| `whynot(nao X)` (o ramo "Porque: o facto X e verdadeiro" pedido diretamente) | O `PorqueNao` trata-o, mas a opção 10 não aceita `nao X` (o leitor de termos não conhece o operador). No Prolog também só se chega lá escrevendo-o à mão |
| "Porque não?" para factos cuja explicação passa pelas causas H–M | Os 10 factos dão o mesmo que o Prolog, mas um facto cuja cadeia passe pela R43 ou pela R66 pode dar outra explicação, porque a base de factos é diferente. Fica resolvido com o `PENDENTES_H_M.md` |
| Desempenho com profundidades maiores | Os 10 factos com profundidade 4 correm em ~1,4 s, com o pedido inteiro (51 cenários) incluído |

---

## 5. Como correr a verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify                                                # 89 + 2 testes
swipl motores/prolog/ferramentas/gera_json.pl --verificar        # regras.json em dia
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar  # referencia do porque nao em dia
```

| Teste | O que prova |
| :--- | :--- |
| `PorqueNaoTest` (10) | O "porque não?" é igual, linha a linha, ao `whynot/1` do Prolog |
| `UnificacaoTest` | Unificação (compostos, listas, factos, `Integer`/`Long`), `_` sempre nova, variante sem confundir átomo e variável, calculadas com variáveis por ligar como no Prolog |
| `ConsolaDroolsTest` (14) | O menu 0–13, os cabeçalhos e as mensagens do `arranca_motor`, a 1 (base do módulo, outro ficheiro, ficheiro em falta), a 3 (lista, `.txt`, `.json`, cenário conhecido por ficheiro), a 5 com `porque`, a 9, a 10 com variáveis, a 12 (`mostrar_conclusoes`), a 13 igual ao pedido direto, a 8 |

---

## 6. Inventário de alterações

| Caminho | |
| :--- | :--- |
| `motores/drools/src/main/java/socrates/motores/drools/explicacao/Var.java`, `Unificacao.java`, `Regras.java`, `Suporte.java`, `PorqueNao.java` | novos |
| `motores/drools/src/main/java/socrates/motores/drools/MotorDrools.java` | alterado (`porqueNao`, `comFactosIniciais`, `numeroRegras`) |
| `motores/drools/src/main/java/socrates/motores/drools/json/FactosIniciais.java` | alterado (`carrega(Path, ...)`) |
| `motores/drools/src/main/java/socrates/motores/drools/consola/ConsolaDrools.java` | reescrito (menu 0–13) |
| `motores/drools/src/main/java/socrates/motores/drools/consola/TermoTexto.java` | alterado (variáveis, escrita sem plicas, ficheiros de cenário) |
| `motores/drools/src/main/java/socrates/motores/drools/consola/SessaoConsola.java` | alterado (`pedido()`) |
| `motores/drools/src/main/java/socrates/motores/drools/consola/Cenarios.java` | alterado (`ficheiro`) |
| `motores/drools/src/main/resources/regras.json` | novo (gerado) |
| `motores/prolog/ferramentas/gera_json.pl`, `motores/prolog/ferramentas/README.md` | alterados (`regras.json`) |
| `motores/drools/ferramentas/gera_porque_nao.pl` | novo |
| `motores/drools/ferramentas/README.md` | alterado |
| `motores/drools/src/test/resources/porque_nao/factos.txt` | novo |
| `motores/drools/src/test/resources/porque_nao/prolog.txt` | novo (gerado) |
| `motores/drools/src/test/java/socrates/motores/drools/consola/PorqueNaoTest.java` | novo |
| `motores/drools/src/test/java/socrates/motores/drools/explicacao/UnificacaoTest.java` | novo |
| `motores/drools/src/test/java/socrates/motores/drools/consola/ConsolaDroolsTest.java` | reescrito |
| `motores/drools/README.md`, `README.md` | alterados |
| `docs/changes/features/F0004-menu-prolog-porque-nao/README.md`, `RESULT.md` | novos |
| `docs/changes/features/README.md` | alterado (índice) |
