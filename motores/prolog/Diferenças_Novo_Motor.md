# Motor de inferência: diferenças para o `sp_exp2.pl`

O `soc_motor.pl` é o `sp_exp2.pl` da UC com quatro alterações ao núcleo, exigidas pela nossa base de conhecimento, e algumas camadas por cima. O núcleo continua a ser o mesmo motor de encadeamento direto: as mesmas regras `regra ID se [...] entao [...]`, os mesmos factos `facto(N, F)`, o metaconhecimento `facto_dispara_regras/2` e a negação `nao`.

Teve de mudar porque o `sp_exp2.pl` não consegue correr a nossa base: não avalia condições calculadas, como `depois(Ev2, Ev1, Janela)`, dispara cada regra uma só vez por facto e tem de ser chamado várias vezes. As quatro alterações (M1 a M4) estão na issue #3 do repositório.

## Os motores de exemplo

A UC dá dois motores de encadeamento direto, e o segundo é uma evolução do primeiro.

- **`sp_exp1.pl`** percorre todos os factos e, para cada um, todas as regras. Cada regra dispara no máximo uma vez por facto. Não tem negação, metaconhecimento nem explicações; só guarda `justifica/3` e mostra os factos.
- **`sp_exp2.pl`** acrescenta o metaconhecimento (`facto_dispara_regras/2`: que regras cada tipo de facto pode disparar), a negação `nao` e as explicações `como/1` ("como se chegou a este facto?") e `whynot/1` ("porque não se chegou?"). Foi este que serviu de base ao `soc_motor.pl`.

Os dois têm o mesmo ciclo: um `arranca_motor` que percorre os factos por retrocesso, verifica o lado SE de cada regra e aplica o ENTÃO com `cria_facto`, que escreve a conclusão e espera por uma tecla.

## Comparação ponto a ponto

A tabela compara os três motores no que cada um faz; as secções seguintes explicam as diferenças do `soc_motor.pl` com o código.

| Aspeto | `sp_exp1.pl` | `sp_exp2.pl` | `soc_motor.pl` |
| --- | --- | --- | --- |
| Sintaxe das regras e dos factos | `regra ID se [...] entao [...]`, `facto(N, F)` | Igual | Igual: a base carrega sem alterações |
| Que regras cada facto tenta | Todas | As do metaconhecimento | As do metaconhecimento; todas, se a base não o tiver |
| Negação `nao X` | Não | Sim | Sim, também sobre condições calculadas |
| Comparações `avalia(...)` | Sim | Sim | Sim, igual |
| Condições calculadas (M1) | Não | Não: são procuradas como factos e falham sempre | Sim: `call(C)` quando `condicao_calculada(C)` |
| Instanciações por regra e facto (M2) | A primeira | A primeira | Todas |
| Factos criados durante a passagem (M3) | Não são vistos: é preciso voltar a chamar o motor | Igual | Vistos na mesma passagem; repete até não haver conclusões novas |
| `cria_facto` (M4) | Escreve e espera por uma tecla | Igual | Não escreve nem espera; as duas coisas são opções |
| `como` | Não existe | Por número; repete sub-árvores | Por número ou pelo facto; árvore sem repetições |
| `whynot` | Não existe | Existe, com dois defeitos (secção Explicações) | Corrigido; trata condições calculadas; profundidade máxima |
| Factos vindos de fora | À mão, no ficheiro da base | À mão, no ficheiro da base | `introduz_facto`, `injeta_cenario`, `injeta_todos` |
| Perguntas ao analista | Não | Não | Como factos (`pergunta_analista`, `resposta_analista`), com "porquê?" no menu |
| Interface | Consultas na consola | Consultas na consola | Consultas na consola e `menu/0` |

## As alterações M1 a M4

São as quatro mudanças ao núcleo sem as quais a nossa base não corre; o resto do motor é acrescentado por cima.

### M1: condições calculadas

A nossa base tem condições que não são factos, mas cálculos sobre factos: `depois(Ev2, Ev1, 60)` (Ev2 aconteceu até 60 s depois de Ev1), `versao_cmp(V, >=, Min)`, `anterior/2`, entre outras. O `soc_suporte.txt` declara-as com `condicao_calculada/1` e define-as.

No `sp_exp2.pl`, o `verifica_condicoes` procura cada condição como facto, com `facto(N, X)`. Como nenhuma destas é um facto, a regra nunca dispara:

```prolog
verifica_condicoes([X e Y],[N|LF]):- !,
	facto(N,X),
	verifica_condicoes([Y],LF).
```

No `soc_motor.pl`, cada condição passa por `verifica_condicao/2`, que avalia as calculadas com `call/1` (também dentro de um `nao`):

```prolog
verifica_condicao(nao C, nao C) :- calculada(C), !, \+ chama_calculada(C).
verifica_condicao(C, C)         :- calculada(C), !, chama_calculada(C).
verifica_condicao(X, N)         :- facto(N, X).
```

Na justificação fica a própria condição em vez do número de um facto, e é assim que o `como` a mostra: "a condição depois(ev_a2,ev_a1,60) verifica-se (calculada)".

### M2: todas as instanciações

No `sp_exp2.pl`, o corte depois de `concluir` faz cada regra disparar só com a primeira combinação de factos que verifica o SE:

```prolog
dispara_regras(N, Facto, [ID|LRegras]):-
	regra ID se LHS entao RHS,
	facto_esta_numa_condicao(Facto,LHS),
	verifica_condicoes(LHS, LFactos),
	member(N,LFactos),
	concluir(RHS,ID,LFactos),
	!,
	dispara_regras(N, Facto, LRegras).
```

Na nossa base isso perde conclusões: o mesmo alerta pode chegar a duas instalações, e um mesmo programa pode ter dois intervalos de versões afetados. O `soc_motor.pl` usa `forall`, que aplica o ENTÃO para cada combinação em que entra o facto que acionou a regra:

```prolog
dispara_regra(N, Facto, ID) :-
	forall(( regra ID se LHS entao RHS,
		 facto_esta_numa_condicao(Facto, LHS),
		 verifica_condicoes(LHS, LFactos),
		 memberchk(N, LFactos) ),
	       concluir(RHS, ID, LFactos)).
```

### M3: até não haver conclusões novas

O `arranca_motor` do `sp_exp2.pl` percorre os factos por retrocesso, com `facto(N, Facto)`. No SWI-Prolog, uma chamada assim só vê os factos que existiam quando começou (a vista lógica das atualizações), por isso os factos que o motor cria nessa passagem não são processados. Era preciso chamar o `arranca_motor` várias vezes, até não aparecer nada.

O `soc_motor.pl` percorre os factos pelo número, de 1 até ao último, e volta a ler o último número a cada passo. Assim, um facto criado a meio da passagem é processado nessa mesma passagem. No fim, se a passagem criou alguma coisa, repete:

```prolog
passagens :-
	ultimo_facto(Antes),
	processa_factos_desde(1),
	ultimo_facto(Depois),
	( Depois > Antes -> passagens ; true ).

processa_factos_desde(N) :-
	ultimo_facto(U), N > U, !.
processa_factos_desde(N) :-
	( facto(N, Facto) -> processa_facto(N, Facto) ; true ),
	N1 is N + 1,
	processa_factos_desde(N1).
```

A repetição é precisa por causa das condições calculadas: uma pode passar a ser verdadeira por causa de um facto que não é premissa da regra (por exemplo, o `tempo/2` de um evento), e esse facto não volta a disparar a regra.

### M4: sem pausa nem escrita no `cria_facto`

No `sp_exp2.pl`, cada conclusão escreve uma linha e espera por uma tecla (`get0`). Para correr dezenas de cenários, ou para ser chamado por um backend, isso não serve. No `soc_motor.pl` o `cria_facto` faz o mesmo de resto (não repete factos, dá o número seguinte e guarda a justificação), e a escrita e a pausa passam para `anuncia/3`, controladas por opções desligadas por omissão:

```prolog
?- define_opcao(mostrar_conclusoes, sim).   % escreve cada conclusão, com a regra
?- define_opcao(passo_a_passo, sim).        % espera por ENTER, como o get0
```

## Explicações

O `como` e o `whynot` do `sp_exp2.pl` mantêm a ideia e o formato, mas foram reescritos: com a nossa base, o `como` dava respostas enormes e o `whynot` dava respostas erradas.

### `como`: como se chegou a um facto

- **Repetições.** No `sp_exp2.pl`, o `explica/1` desce por todas as premissas, e uma premissa usada por várias regras é explicada outra vez em cada uma. Nas conclusões do fim da cadeia da nossa base (tickets fechados, por exemplo), isso dá centenas de linhas. O `soc_motor.pl` guarda o que já explicou na mesma árvore (`ja_explicado/1`) e escreve "(ja explicado acima)" em vez de repetir.
- **Pelo facto, não só pelo número.** `como(compromisso_confirmado(web_srv_01,ev_a1))` procura o número sozinho.
- **Condições calculadas e negações** aparecem na árvore pelo que são: "a condição depois(...) verifica-se (calculada)" e "não se verifica corrigido_backport(...)".
- **`como1/1`**, novo, mostra só o primeiro nível: a regra e as premissas. É o que o menu usa na resposta a "porquê?".

### `whynot`: porque não se chegou a um facto

O `whynot` do `sp_exp2.pl` tem dois defeitos que a nossa base torna visíveis:

1. **A mensagem "não está definido na base de conhecimento" nunca aparece.** A cláusula que a escreve vem depois de outra que nunca falha (o `findall` de `encontra_regras_whynot` dá uma lista vazia, e o `whynot1([], _)` tem sucesso). Uma premissa que é um dado de entrada em falta fica sem explicação nenhuma.
2. **Valores inventados nas premissas.** O `encontra_premissas_falsas` percorre o SE e deixa as condições seguintes instanciarem as variáveis de uma premissa que já falhou. Com condições calculadas como `anterior(EvAnterior, Ev)`, a premissa falhada aparece com um evento qualquer que não tem nada a ver com o caso.

No `soc_motor.pl`:

- uma condição que partilha variáveis com uma premissa falhada só é testada (com `\+ \+`), sem instanciar nada;
- um facto que nenhuma regra conclui é apresentado como "dado de entrada";
- uma condição calculada falsa é apresentada como tal, sem procurar regras para ela;
- um facto já explicado não se repete, e a explicação para numa profundidade máxima (`profundidade_whynot`, 4 por omissão);
- as variáveis aparecem como `A`, `B`, … em vez de `_1234`.

Exemplo, depois dos cenários 1, 2, 3 e 7:

```text
?- whynot(ataque_dirigido(ev_e1,web_srv_04)).
Porque pela regra 13:
     A premissa origem_hostil(ip_198_51_100_23,A) e falsa
          Porque pela regra 12:
               A premissa origem(ev_b1,ip_198_51_100_23) e falsa
                    Porque: o facto origem(ev_b1,ip_198_51_100_23) nao esta na base e nenhuma regra o conclui (e um dado de entrada)
          ...
```

## O que foi acrescentado

Nada disto existe nos motores de exemplo, e nada disto muda o raciocínio: são formas de pôr factos no motor e de ver o que ele concluiu.

| O quê | Predicados | Para quê |
| --- | --- | --- |
| Factos vindos de fora | `introduz_facto/1`, `introduz_factos/1` | Telemetria, respostas do analista e recuperações entram durante a execução, com o número seguinte e sem repetir factos. Nos exemplos, os factos só se escrevem no ficheiro da base. |
| Cenários | `injeta_cenario/1`, `injeta_ficheiro/1`, `injeta_todos/0` | Injeta um ficheiro da pasta `cenarios/` (pelo nome, como `c2`, ou pelo ficheiro) e corre o motor; ou todos, do 1 ao 34, pela ordem do índice. |
| Carregar a base | `carrega_bc/1` | Recomeça do zero (apaga os factos e as justificações da execução anterior) e guarda a pasta da base, para os cenários se encontrarem seja qual for a pasta de trabalho. |
| Perguntas ao analista | `perguntas_pendentes/1`, `pergunta_pendente/4` | As perguntas são factos concluídos pelas regras (`pergunta_analista`); a resposta entra como facto (`resposta_analista`) e o motor corre outra vez. O motor nunca fica parado à espera. |
| Tickets e ações | `tickets_abertos/1`, `tickets_fechados/1`, `mostra_incidentes/0`, `mostra_acoes/0`, `estado/0` | Tickets abertos por prioridade, fechados com o motivo, ações por ativo, e um resumo do estado. |
| Opções | `define_opcao/2` | `mostrar_conclusoes`, `passo_a_passo` e `profundidade_whynot`. |
| Menu | `menu/0` | Carregar, correr, injetar cenários, responder às perguntas (com "porquê?", que mostra a regra que gerou a pergunta), ver tickets, ações e explicações. |

O menu e as consultas para a interface ainda estão no `soc_motor.pl`. O `README.md` de `motores/prolog/` prevê passá-los para um `soc_menu.pl` à parte, para o wrapper do backend carregar só o núcleo.

## O que se manteve do `sp_exp2.pl`

Tudo o que define a linguagem da base ficou igual, por isso as regras e os factos da nossa base carregam sem alterações, e as bases da UC (por exemplo, `veiculos2.txt`) correm no `soc_motor.pl` com o mesmo resultado.

- Os operadores `regra`, `se`, `entao`, `e` e `nao`, com as mesmas prioridades.
- A forma das regras (`regra ID se [C1 e C2 e ...] entao [cria_facto(F), ...]`) e dos factos (`facto(N, F)`, `ultimo_facto/1`).
- O metaconhecimento `facto_dispara_regras/2` e o `facto_esta_numa_condicao/2`, que só dispara uma regra se o facto estiver numa condição positiva do SE.
- As comparações `avalia/2` e `compara/3`.
- O `concluir/3` e o `cria_facto/3`: não repetir factos, numerar pelo `ultimo_facto` e guardar `justifica(N, Regra, Premissas)`.
- A negação por falha: `nao X` é verdadeiro quando `X` não está na memória de trabalho.
- Os factos só existem em memória: o motor não escreve nos ficheiros da base.

## Como se confirma que as alterações não mudam o raciocínio

As alterações mudam como o motor percorre as regras, não o que as regras concluem. Durante o desenvolvimento, isso foi confirmado de duas formas:

- **Com a base de exemplo da UC.** Com o `veiculos2.txt`, o `soc_motor.pl` conclui os mesmos factos que o `sp_exp2.pl`.
- **Com um verificador independente.** Um motor mínimo, sem metaconhecimento, que dispara cada regra sempre que o SE se verifica, até não haver conclusões novas. Com a nossa base e os cenários, os factos finais dos dois motores são exatamente os mesmos.

Os resultados de cada variante estão em `referencia_prolog/`, como referência para o Drools e o comparador.
