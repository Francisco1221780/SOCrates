# Motor de inferência: diferenças para o `sp_exp2.pl`

O `soc_motor.pl` é o `sp_exp2.pl` da UC com quatro alterações ao núcleo (M1 a M4), exigidas pela nossa base de conhecimento, mais a reavaliação por `condicao_le/2` que a base v6 pede, e algumas camadas por cima. O núcleo continua a ser o mesmo motor de encadeamento direto: as mesmas regras `regra ID se [...] entao [...]`, os mesmos factos `facto(N, F)`, o metaconhecimento `facto_dispara_regras/2` e a negação `nao`.

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
| Facto lido por uma condição calculada (v6) | Não dispara a regra | Não dispara a regra | Reavalia a regra por inteiro (`condicao_le/2`) |
| Factos criados durante a passagem (M3) | Não são vistos: é preciso voltar a chamar o motor | Igual | Vistos na mesma passagem: uma chamada chega ao ponto fixo. Nas chamadas seguintes, os factos já processados só tentam as regras que leem um tipo de facto novo |
| `cria_facto` (M4) | Escreve e espera por uma tecla | Igual | Não escreve nem espera; as duas coisas são opções |
| `como` | Não existe | Por número; repete sub-árvores | Por número ou pelo facto; árvore sem repetições |
| `whynot` | Não existe | Existe, com dois defeitos (secção Explicações) | Corrigido; trata condições calculadas; profundidade máxima; também devolve a explicação como estrutura (`whynot_linhas/3`) |
| Factos vindos de fora | À mão, no ficheiro da base | À mão, no ficheiro da base | `introduz_facto`, `injeta_cenario`, `injeta_todos` |
| Perguntas ao analista | Não | Não | Como factos (`pergunta_analista`, `resposta_analista`), com "porquê?" no menu |
| Interface | Consultas na consola | Consultas na consola | Consultas na consola e `menu/0` |

## As alterações M1 a M4

São as quatro mudanças ao núcleo sem as quais a nossa base não corre. A reavaliação por `condicao_le/2`, pedida pela v6, completa a M2; o resto do motor é acrescentado por cima.

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
verifica_condicao(nao avalia(X), nao X) :- !, \+ avalia(_, X).
verifica_condicao(avalia(X), N)        :- !, avalia(N, X).
verifica_condicao(nao C, nao C)         :- calculada(C), !, \+ chama_calculada(C).
verifica_condicao(nao X, nao X)         :- !, \+ facto(_, X).
verifica_condicao(C, C)                 :- calculada(C), !, chama_calculada(C).
verifica_condicao(X, N)                 :- facto(N, X).
```

O `chama_calculada/1` faz o `call/1`, mas conta como falsa uma condição com um argumento por instanciar, em vez de dar erro (só acontece nas explicações, quando uma premissa anterior falhou).

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
	       concluir(RHS, ID, LFactos)),
	forall(( regra ID se LHS entao RHS,
		 \+ \+ facto_lido_por_condicao_calculada(Facto, ID),
		 verifica_condicoes(LHS, LFactos) ),
	       concluir(RHS, ID, LFactos)).
```

O primeiro `forall` é a M2. O segundo é a reavaliação da secção seguinte.

### Reavaliação por `condicao_le/2` (base v6)

Uma condição calculada pode passar a ser verdadeira por causa de um facto que não é premissa da regra. Por exemplo, o `tempo/2` de um evento que chega depois do próprio evento torna verdadeira uma condição `depois/3` em que esse tempo não aparece diretamente. Com só o primeiro `forall`, esse facto não dispara a regra.

A v6 declara no `soc_suporte.txt` que factos cada condição calculada lê:

```prolog
condicao_le(depois(_, _, _), [tempo(_, _), janela(_, _)]).
condicao_le(anterior(_, _), [tempo(_, _)]).
```

Quando chega um facto que uma condição calculada positiva do SE lê (`facto_lido_por_condicao_calculada/2`), o motor avalia a regra por inteiro, sem exigir que esse facto seja uma das premissas. Os factos que cada regra lê assim calculam-se da primeira vez que a regra é tentada e ficam guardados (`lidos_pela_regra/2`), para não se percorrer o SE de cada vez. O `gera_meta.pl` junta estas regras ao `facto_dispara_regras/2`, para o metaconhecimento as deixar passar. Numa base sem `condicao_le/2` (as bases da UC, por exemplo), o segundo `forall` nunca dispara nada e o motor comporta-se como antes.

### M3: até não haver conclusões novas

O `arranca_motor` do `sp_exp2.pl` percorre os factos por retrocesso, com `facto(N, Facto)`. No SWI-Prolog, uma chamada assim só vê os factos que existiam quando começou (a vista lógica das atualizações), por isso os factos que o motor cria nessa passagem não são processados. Era preciso chamar o `arranca_motor` várias vezes, até não aparecer nada.

O `soc_motor.pl` percorre os factos pelo número, de 1 até ao último, e volta a ler o último número a cada passo. Assim, um facto criado a meio da passagem é processado nessa mesma passagem, e uma passagem chega ao ponto fixo:

```prolog
passagens :-
	( processado_ate(W) -> true ; W = 0 ),
	retractall(functor_novo(_, _)),
	forall(( facto(N, F), N > W ), regista_functor_novo(F)),
	processa_factos_desde(1, W),
	ultimo_facto(U),
	retractall(processado_ate(_)),
	assertz(processado_ate(U)).

processa_facto(N, Facto, W) :-
	facto_dispara_regras1(Facto, LRegras),
	(   N > W
	->  forall(member(ID, LRegras), dispara_regra(N, Facto, ID))
	;   forall(( member(ID, LRegras), regra_le_algo_novo(ID) ), dispara_regra(N, Facto, ID))
	).
```

Uma condição calculada pode passar a ser verdadeira por causa de um facto que não é premissa da regra (por exemplo, o `tempo/2` de um evento). Esse caso é apanhado pela reavaliação por `condicao_le/2`, quando o facto chega, tal como no Drools. Até à versão anterior do motor, cada passagem era repetida até não criar nada, como garantia extra; essa passagem de confirmação nunca criava nenhum facto e foi retirada.

**Corridas seguintes.** O motor corre outra vez depois de cada cenário ou resposta do analista, e cada corrida percorre de novo todos os factos desde o número 1. Antes, cada facto tentava de novo todas as regras do seu metaconhecimento: na sequência completa, cada facto era processado cerca de 74 vezes, quase sempre só para dar conclusões repetidas. Agora, um facto que já tinha sido processado numa corrida anterior (número até `processado_ate/1`) só tenta as regras que leem algum tipo de facto novo desde essa corrida (`functor_novo/2`): numa condição positiva, numa comparação, ou através do `condicao_le/2` de uma condição calculada. Uma regra sem nenhum desses tipos não pode ter instanciações novas, porque os factos só se acrescentam: uma negação só pode passar de verdadeira a falsa, e uma condição calculada só passa a verdadeira com um facto do `condicao_le/2`. Uma regra com uma condição calculada negada (`nao C`) tenta-se sempre.

Os factos continuam a ser percorridos pela mesma ordem, por isso as conclusões aparecem pela mesma ordem: a numeração, as regras e as premissas de cada facto ficam iguais. Isto importa porque algumas regras da base dependem da ordem (por exemplo, a R93 só pergunta se o incidente ainda está em aberto), e porque o Drools é comparado com o Prolog facto a facto. O motor também guarda, por nome e aridade, se uma condição é calculada (`calculada/1`), em vez de o verificar de cada vez. Os três registos (`calculada_cache/3`, `lidos_cache/2` e `regra_le_cache/2`) e o `processado_ate/1` limpam-se no `carrega_bc`.

Na sequência completa (base 6.1, 55 passos, 1319 factos), o motor passou de cerca de 4,8 s para 1,75 s de CPU, e a sequência base de 3,9 s para 1,1 s.

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

- uma condição que partilha variáveis com uma premissa falhada só é testada sobre uma cópia, sem instanciar nada (e numa premissa `nao X` falsa, o facto que existe também se procura numa cópia, como no Drools);
- um facto que nenhuma regra conclui é apresentado como "dado de entrada";
- uma condição calculada falsa é apresentada como tal, sem procurar regras para ela;
- um facto já explicado não se repete, e a explicação para numa profundidade máxima (`profundidade_whynot`, 4 por omissão);
- as variáveis aparecem como `A`, `B`, … em vez de `_1234`;
- se as premissas de uma regra que conclui o facto se verificam, diz que falta correr o motor (`arranca_motor`).

A explicação também existe como estrutura: `whynot_linhas(Facto, Profundidade, Linhas)` devolve as linhas pela ordem em que o `whynot/1` as escreve, cada uma `linha(Nivel, Tipo, Campos)`, com os mesmos tipos que o `porqueNao` do Drools (`regra`, `premissa_falsa`, `dado_de_entrada`, `calculada_falsa`, …). O `whynot/1` limita-se a escrever essas linhas, e o `wrapper.pl` passa-as a JSON (com `swipl wrapper.pl <pasta da base> whynot`), por isso o texto e a estrutura dizem sempre o mesmo.

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
| Cenários | `injeta_cenario/1`, `injeta_cenarios/1`, `injeta_ficheiro/1`, `injeta_todos/0`, `lista_cenarios/0` | Injeta um cenário pelo nome (como `c2`, definido com `cenario/3` no `soc_cenarios.pl`) ou pelo ficheiro, e corre o motor. O `injeta_todos` recarrega a base e injeta a sequência base e os cenários de atividade normal, pela ordem do índice, com o motor a correr entre cada um; as respostas do analista ficam de fora, porque algumas são alternativas umas às outras. |
| Carregar a base | `carrega_bc/1` | Recomeça do zero (apaga os factos e as justificações da execução anterior), guarda a pasta e o ficheiro da base, para os cenários se encontrarem seja qual for a pasta de trabalho, e carrega o `soc_cenarios.pl` se estiver ao lado. |
| Perguntas ao analista | `perguntas_pendentes/1`, `pergunta_pendente/4` | As perguntas são factos concluídos pelas regras (`pergunta_analista`); a resposta entra como facto (`resposta_analista`) e o motor corre outra vez. O motor nunca fica parado à espera. |
| Tickets e ações | `tickets_abertos/1`, `tickets_fechados/1`, `mostra_incidentes/0`, `mostra_acoes/0`, `estado/0` | Tickets abertos por prioridade, fechados com o motivo, ações por ativo, e um resumo do estado. |
| Ver factos e regras | `mostra_factos/0,1`, `mostra_conclusoes/0`, `mostra_regra/1` | Todos os factos, só os de um nome, só os concluídos (com a regra), ou uma regra escrita por extenso. |
| Explicação estruturada | `whynot_linhas/3` | O `whynot` como lista de linhas, para o `wrapper.pl` passar a JSON. |
| Opções | `define_opcao/2` | `mostrar_conclusoes`, `passo_a_passo` e `profundidade_whynot`. |
| Menu | `menu/0` | Carregar, correr, injetar um cenário ou todos, introduzir factos, responder às perguntas (com "porquê?", que mostra a regra que gerou a pergunta), registar a recuperação de uma máquina (`recuperado/2`), ver tickets, ações, factos e explicações, e mudar as opções. |

O menu e as consultas para a interface ainda estão no `soc_motor.pl`. O `wrapper.pl`, que o backend lança, já existe e carrega o `soc_motor.pl` inteiro; o `README.md` de `motores/prolog/` prevê passar o menu para um `soc_menu.pl` à parte, para o wrapper carregar só o núcleo.

## O que se manteve do `sp_exp2.pl`

Tudo o que define a linguagem da base ficou igual, por isso as regras e os factos da nossa base carregam sem alterações, e as bases da UC (por exemplo, `veiculos2.txt`) correm no `soc_motor.pl` com o mesmo resultado.

- Os operadores `regra`, `se`, `entao`, `e` e `nao`, com as mesmas prioridades.
- A forma das regras (`regra ID se [C1 e C2 e ...] entao [cria_facto(F), ...]`) e dos factos (`facto(N, F)`, `ultimo_facto/1`).
- O metaconhecimento `facto_dispara_regras/2` e o `facto_esta_numa_condicao/2`, que só dispara uma regra se o facto estiver numa condição positiva do SE.
- As comparações `avalia/2` e `compara/3`.
- O `concluir/3` e o `cria_facto/3`: não repetir factos, numerar pelo `ultimo_facto` e guardar `justifica(N, Regra, Premissas)`. A única diferença no `concluir` é que uma ação desconhecida no ENTÃO dá um aviso, em vez de falhar em silêncio.
- A negação por falha: `nao X` é verdadeiro quando `X` não está na memória de trabalho.
- Os factos só existem em memória: o motor não escreve nos ficheiros da base.

## Como se confirma que as alterações não mudam o raciocínio

As alterações mudam como o motor percorre as regras, não o que as regras concluem. Durante o desenvolvimento, isso foi confirmado de duas formas:

- **Com a base de exemplo da UC.** Com o `veiculos2.txt`, o `soc_motor.pl` conclui os mesmos factos que o `sp_exp2.pl`.
- **Com um verificador independente.** Um motor mínimo, sem metaconhecimento, que dispara cada regra sempre que o SE se verifica, até não haver conclusões novas. Com a base v6, os factos finais dos dois motores eram exatamente os mesmos. Com a v6.1, o verificador faz a mais 7 perguntas de fecho (R93) que são logo retiradas (R103): como dispara todas as regras ao mesmo tempo, pergunta antes de saber que o incidente foi superado. Os tickets, os incidentes e as perguntas por responder são os mesmos.
- **A otimização das corridas seguintes** (secção M3) foi comparada com o motor anterior nas 14 variantes do `gera_referencias.pl` e na sequência completa: a lista de factos, com o número, a regra e as premissas de cada um, é igual, e as explicações também.

Os resultados de cada variante estão em `referencia_prolog/`, como referência para o Drools e o comparador. O teste `referencias_em_dia` (`testes/testes.pl`) confirma que continuam a ser o que o motor dá hoje.
