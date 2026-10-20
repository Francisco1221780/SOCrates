# F0005 — Consola Drools: factos legíveis, "porquê?" e "porque não?" mais claro

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 19/10: "o menu pode ficar igual ao do Prolog, mas…") |
| Branch | `feature/drools-menu-why` (base `develop`) |
| Estado | Plano à espera de aprovação |
| Âmbito | Na `ConsolaDrools`: (1) os factos deixam de se pedir e mostrar em sintaxe Prolog (sem ponto final obrigatório, sem plicas, sem listas); (2) nova opção 14 "porquê?" que mostra uma conclusão com a regra completa (SE … ENTÃO …) e os factos que a satisfizeram; (3) o "porque não?" (opção 10) passa a ter um formato claro, com a regra inteira e cada condição marcada como verificada ou em falta |
| Verificação | `.\mvnw.cmd verify` (módulo Drools); `PorqueNaoTest` (formato Prolog, inalterado) e testes novos para os dois formatos; corrida à mão no IntelliJ |

---

## 1. Situação

O menu da consola Drools foi feito para ser igual ao `menu/0` do `soc_motor.pl` (F0003, F0004). O utilizador gosta de o menu ficar igual, mas aponta três coisas.

**Evidência**

- Os factos pedem-se e mostram-se em sintaxe Prolog:
  - `ConsolaDrools.introduzFactos` pede `[alerta_ids(ev_1,web_srv_02,sig_apache_path_traversal), tempo(ev_1,900)]` e usa `TermoTexto.factos`, um leitor de termos Prolog (plicas, listas, variáveis em maiúscula).
  - `TermoTexto.escrito` e `TermoTexto.texto` escrevem `nome(a,b)` sem espaços, com plicas nas explicações (`'2.4.49-ubuntu1'`).
  - Os pedidos de entrada têm o ponto final do Prolog: `Opcao a mudar (ou nada.)`, `Valor (sim. / nao.)`, `todos. | concluidos.`, `porque.`, `base.`. O código aceita o ponto mas não o exige (`semPontoFinal`); a dúvida é só do texto.
- Não há "porquê?" por conclusão. O `porque` existe só dentro da opção 5, durante uma pergunta (`ConsolaDrools.porquePergunta`), e mostra uma regra com os factos que a levaram a perguntar, sem o texto da regra. A opção 9 ("como?") mostra a árvore de factos mas só diz `concluido pela regra N`, sem dizer o que a regra N é.
- O "porque não?" (`ConsolaDrools.linhaPorqueNao`, a partir do JSON de `PorqueNao`) escreve as linhas planas do `whynot/1`: `Porque pela regra 13:`, `A premissa X e falsa`, `Porque: o facto X nao esta na base…`, com 5 espaços por nível. Exemplo (opção 10 sobre `ataque_dirigido(ev_e1,web_srv_04)`, depois da opção 13):

  ```
  Porque pela regra 13:
       A premissa origem_hostil(ip_198_51_100_23,A) e falsa
            Porque pela regra 12:
                 A premissa origem(ev_b1,ip_198_51_100_23) e falsa
                      Porque: o facto origem(ev_b1,ip_198_51_100_23) nao esta na base e nenhuma regra o conclui (e um dado de entrada)
  ```

  Não diz o que é a regra 13 nem quais das suas condições se verificaram; só aparecem as que falharam, com uma frase diferente por tipo. É isto que o utilizador acha confuso.
- `PorqueNaoTest` compara o texto de `linhaPorqueNao` com `src/test/resources/porque_nao/prolog.txt`, gerado pelo `whynot/1` do Prolog (`ferramentas/gera_porque_nao.pl`, com `--verificar`). Esse teste tem de continuar a provar a paridade com o Prolog.
- As regras existem como dados em `src/main/resources/regras.json` (92 regras, `se`/`entao` com variáveis) e são lidas por `explicacao/Regras`. É daí que sai o texto de uma regra. O "se" de cada regra já é percorrido por `PorqueNao.premissasFalsas`.

**Inventário**

| # | Superfície |
| :--- | :--- |
| A | Factos legíveis: entrada da opção 4 (sem ponto, sem plicas, um facto por linha) e texto dos factos nas listagens, no "como?" e nas explicações (`nome(arg, arg)`) |
| B | Prompts e mensagens sem o ponto do Prolog (opções 1, 3, 5, 11, 12) |
| C | `PorqueNao` dá, em cada linha `regra`, a regra completa com o estado de cada condição; `MotorDrools`/`explicacao` expõe o texto de uma regra |
| D | Opção 14 "porquê?": a conclusão, a regra completa e os factos que a satisfizeram, a descer até aos dados; o `porque` da opção 5 passa a mostrar também a regra completa. Junto com o "como?" e o "porque não?" numa só classe de explicações |
| E | Opção 10 com o formato novo (`Explicacoes`); o formato Prolog fica só para o `PorqueNaoTest` |
| F | Testes e documentação |

**Escala:** 1 classe nova na consola, `Explicacoes` (porquê, como e porque não), ~3 métodos novos em `explicacao`, alterações em `ConsolaDrools` (873 linhas) e `TermoTexto` (309 linhas), 2 classes de teste alteradas (`ConsolaDroolsTest`, `TermoTextoTest`) e 2 novas, mais os READMEs.

---

## 2. Resultado pretendido

O menu mantém as entradas 0–13 do Prolog, com os mesmos números, e ganha a 14. As três explicações ficam juntas no menu e no código — porquê, como, porque não — e todas pedem o facto da mesma maneira (o número ou o próprio facto):

```
 9. Explicar como se concluiu um facto (como?)
14. Explicar porque se concluiu um facto, com as regras (porque?)
10. Explicar porque nao se concluiu um facto (porque nao?)
11. Mostrar factos
12. Opcoes do motor
13. Injetar todos os cenarios de uma vez
 0. Sair
```

(O 14 aparece entre a 9 e a 10 para as explicações ficarem seguidas; o número é o único que não existe no Prolog.)

**Factos (opção 4).** Um facto por linha, linha vazia para terminar; sem ponto, sem plicas, sem listas:

```
Facto (ex.: alerta_ids(ev_1, web_srv_02, sig_apache_path_traversal)), linha vazia para acabar:
> alerta_ids(ev_1, web_srv_02, sig_apache_path_traversal)
> tempo(ev_1, 900)
>
```

Listagens e explicações escrevem os factos da mesma forma: `alerta_ids(ev_1, web_srv_02, sig_x)`, `versao(srv_apache, 2.4.49-ubuntu1)`, sem plicas nem ponto final.

**Porquê? (opção 14).** Pelo número do facto (ou o próprio facto), mostra a conclusão, a regra que a produziu escrita por inteiro e os factos que a satisfizeram, e desce pelos factos que também foram concluídos (sem repetir um já explicado). Tudo: factos e regras.

```
Facto 612: ataque_dirigido(ev_e1, web_srv_04)
  Concluido pela regra 13:
    SE    exploracao_possivel(Ev, Ativo, _)
    E     origem_hostil(Ip, _)
    ENTAO ataque_dirigido(Ev, Ativo)
  Com os factos:
    [facto 598] exploracao_possivel(ev_e1, web_srv_04, cve_2021_41773)
        Concluido pela regra 6:
          SE    ...
    [facto 40]  origem_hostil(ip_198_51_100_23, ev_e1)   (dado)
```

(O texto de cima é a forma, não a saída final.) A regra mostra-se com os nomes das variáveis do `soc_regras.txt`.

**Porque não? (opção 10).** A mesma ideia, ao contrário: para cada regra que podia concluir o facto, a regra inteira com cada condição marcada, e a seguir porque falha a que falha.

```
Porque nao existe ataque_dirigido(ev_e1, web_srv_04)?

Podia ser concluido pela regra 13, mas falta uma condicao:
  SE    [ok]    exploracao_possivel(ev_e1, web_srv_04, _)   (facto 598)
        [FALTA] origem_hostil(ip_198_51_100_23, _)
  ENTAO ataque_dirigido(ev_e1, web_srv_04)

  Falta: origem_hostil(ip_198_51_100_23, _)
  Podia ser concluida:
    pela regra 12: ...
```

Marcas em ASCII (a consola do Windows e a do IntelliJ nem sempre estão em UTF-8). Os níveis têm 4 espaços e uma etiqueta por tipo de motivo: "Falta", "Nao e um facto da base e nenhuma regra o conclui (dado de entrada)", "Ja explicado acima", "(profundidade maxima; opcao 12)", "A condicao calculada ... e falsa".

### Decisões

| Decisão | Valor | Origem |
| :--- | :--- | :--- |
| O que é o "why" | Opção nova 14 ("porquê?"), com a regra completa e os factos. O `porque` da opção 5 mantém-se (porque é que o motor pergunta isto) e passa a mostrar também a regra completa | Utilizador (19/10) |
| Facto sem sintaxe Prolog | `nome(arg, arg)`, com ou sem o ponto final, sem plicas, e um facto por linha na opção 4. Os argumentos são texto até à vírgula ou ao `)`; um argumento só com dígitos é número; um argumento composto (`v(2,4,49)`) continua a ler-se. Escrito da mesma forma | Utilizador (19/10) |
| Plicas e listas | Continuam a ser aceites na entrada (`'2.4.49-ubuntu1'`, `[f1, f2]`), para os cenários `.txt` do Prolog e para quem copia da consola Prolog; só deixam de ser pedidas e de aparecer | Decisão de implementação |
| Numeração | O "porquê?" é a 14, para os números 0–13 ficarem iguais aos do Prolog; no menu aparece entre a 9 e a 10 | Utilizador (19/10: "porque, como, porque não") |
| Onde vivem as explicações | As três (porquê, como, porque não) numa classe nova `consola/Explicacoes`, retiradas da `ConsolaDrools` (o `como`/`explica`, o `porquePergunta` e o `porqueNao`/`linhaPorqueNao` mudam para lá). A `ConsolaDrools` só chama. A lógica de dados fica em `explicacao/` (`PorqueNao`) | Utilizador (19/10: "juntaria o porquê ao outro módulo de explicações") |
| Formato do "porque não?" | Só muda a apresentação (nova classe na consola). O JSON de `PorqueNao` ganha campos (`se` e `entao` nas linhas `regra`) sem tirar nenhum, por isso o `linhaPorqueNao` (formato Prolog) e o `PorqueNaoTest` ficam como estão | Decisão de implementação |
| Texto da regra | Vem do `regras.json` (`se`/`entao`), com as variáveis pelos nomes originais. É a regra tal como está no `soc_regras.txt`, não a tradução para DRL | Igual ao "porque não?" da F0004 |
| Ponto final do Prolog | Deixa de aparecer nos prompts e nas mensagens; continua a ser aceite se o utilizador o escrever | Utilizador (19/10) |

### Alternativas rejeitadas

- **Perguntar o predicado e cada argumento, um por linha** (para a opção 4). Evita sintaxe, mas é lento para quem copia o facto de uma explicação, e os cenários já são factos escritos de uma vez.
- **Escrever factos como `Facto("alerta_ids", "ev_1", …)`** (estilo DRL). Troca uma sintaxe por outra, e os factos do motor não são objetos com campos, são `predicado + args`.
- **Fundir o "como?" (9) e o "porquê?"**. Obrigava a mudar o texto de uma opção que existe no Prolog e que o utilizador quer igual.
- **Mudar o formato Prolog do `whynot/1`**. O Prolog é a referência; a paridade com ele fica garantida pelo teste existente.

---

## 3. Implementação

**Fase 1 — Factos legíveis (A, B)**
- `consola/TermoTexto.java`: `legivel(JsonNode)` (`nome(a, b)`, sem plicas, vírgula e espaço) e um modo de leitura sem plicas (argumentos nus até `,` ou `)`, só dígitos é inteiro). Variáveis só no "porque não?", como hoje.
- `consola/ConsolaDrools.java`: usar `legivel` nas listagens (`mostraFactos`, `relatorio`, `mostraIncidentes`, `mostraAcoes`, `explica`, `porquePergunta`); `introduzFactos` passa a um facto por linha; tirar o ponto dos prompts e mensagens (`carregaBase`, `injetaCenarios`, `mostraFactos`, `opcoes`, `pergunta`, `como`, `porqueNao`).
- Testes `TermoTextoTest` (entrada sem plicas, argumento composto, com e sem ponto, um valor com ponto e hífen como `2.4.49-ubuntu1`, `legivel` ida e volta) e `ConsolaDroolsTest` (4 sem lista, os prompts sem ponto).

**Fase 2 — Dados para a regra completa (C)**
- `explicacao/Regras.java` e `explicacao/PorqueNao.java`: nas linhas `regra`, juntar `se` (cada condição com o termo e `estado` `ok`/`falta`) e `entao`, com as ligações no fim de `premissasFalsas`; uma função pública `PorqueNao.regra(int)` que devolve o `se`/`entao` de uma regra com os nomes das variáveis (para a opção 14 e para o `porque` da opção 5).
- Testes: `PorqueNaoTest` mantém-se (formato Prolog); teste novo `PorqueNaoRegraTest` para o `se` com estados.

**Fase 3 — Porquê? (D)**
- `consola/Explicacoes.java` (novo): junta as três explicações. Muda para aqui, sem alterar o comportamento, o `como`/`explica` da opção 9 e o `porquePergunta`; acrescenta a opção 14 (a regra `SE`/`E`/`ENTAO` e a árvore de factos), também usada no `porque` da opção 5.
- `ConsolaDrools`: a entrada 14 no menu e no `executa`.
- Testes `ConsolaDroolsTest`: a 14 sobre um facto concluído (mostra a regra e os factos); a 14 sobre um dado (diz que é um dado); a 14 sobre um facto que não está na memória.

**Fase 4 — Porque não? (E)**
- `consola/Explicacoes.java`: `porqueNao(linhas)` com o formato novo; `ConsolaDrools.porqueNao` passa a usá-lo; `linhaPorqueNao` fica (só para o `PorqueNaoTest`).
- Testes: o exemplo do `ataque_dirigido(ev_e1, web_srv_04)` com o texto esperado; um facto verdadeiro; um dado de entrada; a profundidade; a regra com condição `nao`; a calculada falsa.

**Fase 5 — Documentação (F)**
- `motores/drools/README.md` e `README.md` da raiz: o menu com a 14, a entrada de factos sem sintaxe Prolog e o formato do "porque não?".

---

## 4. Verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd verify
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar
```

- **Baseline:** o fim da F0004 (`BUILD SUCCESS`, 89 + 2 testes), com os 5 cenários da opção 13 que diferem do Prolog por causa das causas H–M (`F0002/PENDENTES_H_M.md`), a que se somam as correções da #15 já em `develop`. Corre-se primeiro a suite em `develop` para fixar o número real.
- **Passa quando:** `BUILD SUCCESS` com os testes novos; o `PorqueNaoTest` continua igual (126 linhas iguais ao `whynot/1`); `gera_porque_nao.pl --verificar` sai com 0; nenhum prompt da consola tem um ponto final de Prolog (um teste varre a saída de uma sessão completa).
- **À mão:** correr a `ConsolaDrools` no IntelliJ, injetar `c6`, `c7`, usar a 14 e a 10, e confirmar que o texto é claro (é uma questão de leitura, não de teste).

---

## 5. Riscos

| Risco | Como se valida que não aconteceu |
| :--- | :--- |
| **O leitor sem plicas aceita coisas a mais** (um erro de escrita vira um átomo) | `TermoTextoTest` com casos de erro: parêntese por fechar, argumento vazio, variável em maiúscula fora do "porque não?"; mensagem de erro com a posição |
| **Mudar a forma como os factos se escrevem quebra os testes que comparam texto** (`ConsolaDroolsTest`) | Corrida completa do módulo; os testes alterados ficam listados no `RESULT.md` §6 |
| **Um argumento com vírgula ou parêntese dentro de texto** (ex.: um valor com `(`) já não se distingue de um argumento composto | O `facto` do contrato só leva átomos e números; verificar no `factos_iniciais.json` e nos cenários que nenhum argumento tem `,` ou `()`; a plica continua aceite como saída de emergência |
| **A regra mostrada difere do DRL que corre** | O texto vem do `soc_regras.txt` (regras.json), como no "porque não?" da F0004; a paridade das regras (`ParidadeTest`) já garante que o DRL dá o mesmo resultado. Fica escrito no README |
| **Dois formatos de "porque não?" a manter** (Prolog para o teste, novo para o utilizador) | O JSON de `PorqueNao` é único; os dois formatos só o apresentam. O `PorqueNaoTest` prova o primeiro, o novo tem os seus testes |
| **A 14 desce por árvores muito grandes** | Um facto concluído por regras em cadeia pode ter dezenas de factos: respeitar o "já explicado acima" (como o "como?") e a profundidade da opção 12 |

---

## 6. Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | `feat(drools): read and print facts without Prolog syntax in the console` | A, B |
| 2 | `feat(drools): expose the full rule and its conditions in the why-not data` | C |
| 3 | `feat(drools): add the why option with the full rule and facts` | D |
| 4 | `feat(drools): present the why-not as the whole rule with marked conditions` | E |
| 5 | `docs(drools): document the new menu option and fact entry` | F |

Verificar antes de planear mais: `ls .pre-commit-config.yaml .husky/ .githooks/` (a F0003 diz que não há hooks de commit).

---

## 7. Fora do âmbito / a quem passa

| Fica de fora | Para |
| :--- | :--- |
| Mudar o menu do Prolog (a entrada de factos, o formato do `whynot/1`) | Quem mantém o Prolog; o utilizador só pediu a consola Drools |
| Expor o "porquê?" e o "porque não?" novos no backend/UI | #16 / UI |
| O desvio do "porque não?" para factos que passam pelas causas H–M | `F0002/PENDENTES_H_M.md` |
