% =====================================================================================
% MOTOR DE INFERENCIA - Analista virtual de SOC (SOCrates)
% Parte do sp_exp2.pl da UC (encadeamento direto, metaconhecimento, negacao "nao",
% explicacoes "como" e "porque nao") e acrescenta o que a base de conhecimento v6 exige:
%   1. CONDICOES CALCULADAS: uma condicao C com condicao_calculada(C) avalia-se com call(C)
%      (depois/3, versao_cmp/3, anterior/2, ...), em vez de se procurar como facto.
%   2. TODAS AS INSTANCIACOES: cada regra dispara para todas as combinacoes de factos que
%      verificam o seu LHS e em que entra o facto que a acionou (o sp_exp2 so disparava a primeira).
%      Quando chega um facto que uma condicao calculada da regra le (condicao_le/2 da base, v6), a regra
%      avalia-se por inteiro, sem exigir que esse facto seja uma das premissas.
%   3. PONTO FIXO: o arranca_motor percorre os factos por ordem, incluindo os que vai criando,
%      e repete a passagem ate nao haver conclusoes novas (no sp_exp2 era preciso chama-lo varias vezes).
%   4. SEM PAUSA NEM ESCRITA no cria_facto (o get0 e o write do sp_exp2). As duas passam a ser opcoes,
%      desligadas por omissao: define_opcao(mostrar_conclusoes, sim) / define_opcao(passo_a_passo, sim).
%   5. EXPLICACOES adaptadas: o "como" mostra uma arvore e nao repete o que ja explicou;
%      o "porque nao" sabe tratar condicoes calculadas, evita ciclos e diz quando um facto e um dado.
%   6. INTERFACE (menu/0): carregar a base, injetar cenarios, responder as perguntas ao analista
%      (com "porque?" durante a pergunta), ver incidentes, tickets e acoes, registar recuperacoes.
% Funciona tambem com as bases da UC (ex.: veiculos2.txt): sem metaconhecimento usa todas as regras.
%
% Uso rapido:
%   ?- consult('soc_motor.pl').
%   ?- menu.                                    (interface)
% ou, a mao:
%   ?- carrega_bc('../../base_conhecimento/soc_factos.txt').
%   ?- introduz_facto(alerta_ids(ev_b1,web_srv_02,sig_apache_path_traversal)), ...
%   ?- arranca_motor.
%   ?- como(N).   ?- como(compromisso_confirmado(web_srv_01,ev_a1)).
%   ?- whynot(ataque_dirigido(ev_e1,web_srv_04)).
% =====================================================================================

:-op(220,xfx,entao).
:-op(35,xfy,se).
:-op(240,fx,regra).
:-op(500,fy,nao).
:-op(600,xfy,e).

:-dynamic justifica/3.
:-dynamic opcao/2.
:-dynamic ja_explicado/1.
:-dynamic ja_visto_whynot/1.
:-dynamic pasta_base/1.
:-dynamic ficheiro_base/1.       % o soc_factos.txt carregado (para os testes o recarregarem)          % pasta do soc_factos.txt: os ficheiros de cenario sao relativos a ela

% Opcoes do motor (mudam-se com define_opcao/2 ou no menu)
%   mostrar_conclusoes: escreve cada facto concluido
%   passo_a_passo:      espera por ENTER depois de cada conclusao (como o get0 do sp_exp2)
%   profundidade_whynot: quantos niveis o "porque nao" mostra
opcao(mostrar_conclusoes, nao).
opcao(passo_a_passo, nao).
opcao(profundidade_whynot, 4).   % ate onde o "porque nao" desce pelas premissas

define_opcao(Nome, Valor) :-
	retractall(opcao(Nome, _)),
	assertz(opcao(Nome, Valor)).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% CARREGAMENTO DA BASE DE CONHECIMENTO

carrega_bc :-
	write('NOME DA BASE DE CONHECIMENTO (terminar com .)-> '),
	read(NBC),
	carrega_bc(NBC).

% Volta a carregar do zero: apaga os factos da execucao anterior (concluidos e injetados) e as justificacoes.
carrega_bc(NBC) :-
	retractall(justifica(_,_,_)),
	retractall(ja_explicado(_)),
	( current_predicate(facto/2) -> retractall(facto(_,_)) ; true ),
	( current_predicate(ultimo_facto/1) -> retractall(ultimo_facto(_)) ; true ),
	absolute_file_name(NBC, Abs, [access(read)]),
	file_directory_name(Abs, Dir),
	retractall(pasta_base(_)),
	assertz(pasta_base(Dir)),
	retractall(ficheiro_base(_)),
	assertz(ficheiro_base(Abs)),
	consult(Abs),
	carrega_cenarios(Abs),
	findall(N, facto(N,_), L), length(L, NF),
	findall(ID, regra ID se _ entao _, LR), length(LR, NR),
	format('Base carregada: ~w factos e ~w regras.~n', [NF, NR]).

% Se houver um ficheiro de cenarios ao lado da base (soc_cenarios.pl), carrega-o tambem.
carrega_cenarios(NBC) :-
	file_directory_name(NBC, Dir),
	atomic_list_concat([Dir, '/soc_cenarios.pl'], F),
	exists_file(F), !,
	consult(F).
carrega_cenarios(_).

bc_carregada :- current_predicate(facto/2), current_predicate(regra/1).

% Escreve um termo com as variaveis como A, B, ... (em vez de _1234), para as explicacoes.
legivel(T, A) :-
	copy_term(T, C),
	numbervars(C, 0, _),
	with_output_to(atom(A), write_term(C, [numbervars(true), quoted(true)])).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ARRANQUE DO MOTOR (encadeamento direto ate ao ponto fixo)

arranca_motor :-
	ultimo_facto(Inicio),
	passagens,
	ultimo_facto(Fim),
	Novos is Fim - Inicio,
	Primeiro is Inicio + 1,
	( Novos =:= 0
	->  writeln('Nao ha conclusoes novas.')
	;   format('~w factos novos (n. ~w a ~w).~n', [Novos, Primeiro, Fim])
	).

% Uma passagem percorre todos os factos por ordem, incluindo os que vai criando.
% Repete-se ate uma passagem inteira nao criar nada: assim uma condicao calculada que so passa a ser
% verdadeira com um facto que nao e premissa da regra (ex.: o tempo de um evento) tambem e apanhada.
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

processa_facto(N, Facto) :-
	facto_dispara_regras1(Facto, LRegras),
	forall(member(ID, LRegras), dispara_regra(N, Facto, ID)).

% Metaconhecimento: que regras o facto pode disparar. Sem metaconhecimento na base, todas as regras.
facto_dispara_regras1(Facto, LRegras) :-
	current_predicate(facto_dispara_regras/2),
	facto_dispara_regras(Facto, LRegras), !.
facto_dispara_regras1(_, []) :-
	current_predicate(facto_dispara_regras/2), !.
facto_dispara_regras1(_, LRegras) :-
	findall(ID, regra ID se _ entao _, LRegras).

% Dispara a regra ID para TODAS as instanciacoes em que o facto N e uma das premissas.
% O forall volta atras por cada posicao do facto no LHS e por cada combinacao dos outros factos.
% Se o facto for lido por uma condicao calculada da regra (condicao_le/2 da base), a regra avalia-se tambem por inteiro,
% sem exigir que o facto seja uma das premissas: ex.: o tempo de um evento que chega depois do evento torna verdadeira
% uma condicao depois/3 de uma regra em que esse tempo nao aparece diretamente.
dispara_regra(N, Facto, ID) :-
	forall(( regra ID se LHS entao RHS,
		 facto_esta_numa_condicao(Facto, LHS),
		 verifica_condicoes(LHS, LFactos),
		 memberchk(N, LFactos) ),
	       concluir(RHS, ID, LFactos)),
	forall(( regra ID se LHS entao RHS,
		 \+ \+ facto_lido_por_condicao_calculada(Facto, LHS),
		 verifica_condicoes(LHS, LFactos) ),
	       concluir(RHS, ID, LFactos)).


% O facto aparece numa condicao positiva do LHS (instancia as variaveis da regra)

facto_esta_numa_condicao(F, [F e _]).
facto_esta_numa_condicao(F, [avalia(F1) e _]) :- F =.. [H,H1|_], F1 =.. [H,H1|_].
facto_esta_numa_condicao(F, [_ e Fs]) :- facto_esta_numa_condicao(F, [Fs]).
facto_esta_numa_condicao(F, [F]).
facto_esta_numa_condicao(F, [avalia(F1)]) :- F =.. [H,H1|_], F1 =.. [H,H1|_].


% O facto e lido por uma condicao calculada positiva do LHS: a base diz, com condicao_le(Condicao, Factos), que factos
% podem tornar a condicao verdadeira. Sem condicao_le/2 na base (ex.: bases da UC), nunca se verifica.

facto_lido_por_condicao_calculada(F, LHS) :-
	current_predicate(condicao_le/2),
	condicao_do_lhs(C, LHS),
	C \= nao(_),
	calculada(C),
	condicao_le(C, Lidos),
	member(L, Lidos),
	\+ \+ F = L, !.

condicao_do_lhs(C, [L]) :- condicao_da_conjuncao(C, L).

condicao_da_conjuncao(C, A e B) :- !, ( C = A ; condicao_da_conjuncao(C, B) ).
condicao_da_conjuncao(C, C).


% Verificar o LHS. LFactos guarda, por cada condicao, o numero do facto que a verificou
% ou, se nao foi um facto (negacao, condicao calculada), a propria condicao.

verifica_condicoes([C e Resto], [J|LJ]) :- !,
	verifica_condicao(C, J),
	verifica_condicoes([Resto], LJ).
verifica_condicoes([C], [J]) :-
	verifica_condicao(C, J).

verifica_condicao(nao avalia(X), nao X) :- !, \+ avalia(_, X).
verifica_condicao(avalia(X), N)        :- !, avalia(N, X).
verifica_condicao(nao C, nao C)         :- calculada(C), !, \+ chama_calculada(C).
verifica_condicao(nao X, nao X)         :- !, \+ facto(_, X).
verifica_condicao(C, C)                 :- calculada(C), !, chama_calculada(C).
verifica_condicao(X, N)                 :- facto(N, X).

% Avalia uma condicao calculada. Se faltar instanciar um argumento (so acontece nas explicacoes, quando
% uma premissa anterior falhou), conta como falsa em vez de dar erro.
chama_calculada(C) :-
	catch(call(C), error(instantiation_error, _), fail).

% C e uma condicao calculada (declarada na base com condicao_calculada/1)?
calculada(C) :-
	current_predicate(condicao_calculada/1),
	callable(C),
	\+ \+ condicao_calculada(C).


avalia(N, P) :-
	P =.. [Functor, Entidade, Operando, Valor],
	P1 =.. [Functor, Entidade, Valor1],
	facto(N, P1),
	compara(Valor1, Operando, Valor).

compara(V1, ==, V)  :- V1 == V.
compara(V1, \==, V) :- V1 \== V.
compara(V1, >, V)   :- V1 > V.
compara(V1, <, V)   :- V1 < V.
compara(V1, >=, V)  :- V1 >= V.
compara(V1, =<, V)  :- V1 =< V.


% Aplicar o RHS da regra que disparou

concluir([cria_facto(F)|Y], ID, LFactos) :- !,
	cria_facto(F, ID, LFactos),
	concluir(Y, ID, LFactos).
concluir([], _, _) :- !.
concluir([A|_], ID, _) :-
	format('ATENCAO: a regra ~w tem uma acao desconhecida no ENTAO: ~w~n', [ID, A]),
	fail.

cria_facto(F, _, _) :-
	facto(_, F), !.
cria_facto(F, ID, LFactos) :-
	retract(ultimo_facto(N1)),
	N is N1 + 1,
	assertz(ultimo_facto(N)),
	assertz(justifica(N, ID, LFactos)),
	assertz(facto(N, F)),
	anuncia(N, F, ID).

anuncia(N, F, ID) :-
	( opcao(mostrar_conclusoes, sim)
	-> format('Foi concluido o facto n. ~w -> ~w   (regra ~w)~n', [N, F, ID])
	;  true ),
	( opcao(passo_a_passo, sim) -> get_char(_) ; true ).


% Acrescentar um facto vindo de fora (telemetria, resposta do analista, recuperacao).
% Igual ao novo_facto/1 da base, mas nao repete um facto que ja exista.
introduz_facto(F) :-
	facto(N, F), !,
	format('O facto ~w ja existe (n. ~w).~n', [F, N]).
introduz_facto(F) :-
	retract(ultimo_facto(N0)),
	N is N0 + 1,
	assertz(ultimo_facto(N)),
	assertz(facto(N, F)).

introduz_factos(L) :- forall(member(F, L), introduz_facto(F)).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% CENARIOS (se a base tiver cenario(Id, Descricao, Factos), ver soc_cenarios.pl)

% Injeta o bloco de factos de um cenario e corre o motor, como pede o soc_factos.txt.
% Injeta um cenario pelo nome (ex.: c2) e corre o motor. No soc_cenarios.pl, cenario(Id, Descricao, X) diz onde estao
% os factos: X e o ficheiro do cenario (relativo a pasta do soc_factos.txt) ou, em alternativa, a lista dos factos.
% Tambem aceita diretamente o nome de um ficheiro (ex.: injeta_cenario('cenarios/cenario_30.txt')).
injeta_cenario(Id) :-
	current_predicate(cenario/3),
	cenario(Id, Descr, X), !,
	format('~n=== Cenario ~w: ~w~n', [Id, Descr]),
	(   is_list(X)
	->  introduz_factos(X), arranca_motor
	;   injeta_ficheiro_silencioso(X)
	).
injeta_cenario(F) :-
	atom(F), resolve_ficheiro(F, _), !,
	injeta_ficheiro(F).
injeta_cenario(_) :-
	\+ current_predicate(cenario/3), !,
	writeln('Nao ha cenarios carregados: o soc_cenarios.pl nao estava na pasta do soc_factos.txt.'),
	writeln('Carregue-o com consult(\'caminho/para/soc_cenarios.pl\') ou ponha-o ao lado do soc_factos.txt e volte a correr carrega_bc.').
injeta_cenario(Id) :-
	format('Nao conheco o cenario ~w (lista_cenarios. mostra os que ha).~n', [Id]).

injeta_cenarios(L) :- forall(member(Id, L), injeta_cenario(Id)).

% Injeta um ficheiro de cenario e corre o motor. O ficheiro pode ter, por qualquer ordem:
%   novo_facto(F).   ou   :- novo_facto(F).   (como os blocos do soc_factos.txt, ja sem o '% ')
%   F.               (o proprio facto, ex.: alerta_ids(ev_1,web_srv_02,sig_apache_path_traversal).)
%   linhas de comentario, que se ignoram.
% Os factos entram com introduz_facto/1 (numero seguinte, sem repetir), e no fim corre o motor.
% Um ficheiro relativo procura-se primeiro na pasta atual e depois na pasta do soc_factos.txt.
injeta_ficheiro(Ficheiro) :-
	format('~n=== Ficheiro ~w~n', [Ficheiro]),
	injeta_ficheiro_silencioso(Ficheiro).

injeta_ficheiro_silencioso(Ficheiro) :-
	(   resolve_ficheiro(Ficheiro, Caminho)
	->  setup_call_cleanup(open(Caminho, read, S),
			       le_termos_cenario(S),
			       close(S)),
	    arranca_motor
	;   format('Nao encontro o ficheiro ~w (nem na pasta atual nem na pasta da base).~n', [Ficheiro])
	).

resolve_ficheiro(F, F) :- exists_file(F), !.
resolve_ficheiro(F, C) :-
	is_relative_path(F),
	pasta_base(Dir),
	atomic_list_concat([Dir, '/', F], C),
	exists_file(C).

is_relative_path(F) :- \+ is_absolute_file_name(F).

injeta_ficheiros(L) :- forall(member(F, L), injeta_ficheiro(F)).

le_termos_cenario(S) :-
	read_term(S, T, []),
	(   T == end_of_file
	->  true
	;   termo_cenario(T),
	    le_termos_cenario(S)
	).

termo_cenario((:- novo_facto(F))) :- !, introduz_facto(F).
termo_cenario((:- G)) :- !, ( catch(G, E, (print_message(error, E), fail)) -> true ; true ).
termo_cenario(novo_facto(F)) :- !, introduz_facto(F).
termo_cenario(F) :- introduz_facto(F).

% Injeta de uma so vez todos os cenarios, pela ordem do indice: os de ataque (1 a 29b) e os de atividade normal
% (30 a 34), com o motor a correr entre cada um. Recomeca do estado inicial (recarrega a base) e mostra o estado no fim.
% As respostas do analista (7b, 19b, 3b, 10d, 17b, 29c) nao entram: injetam-se depois, uma a uma, porque algumas
% sao alternativas umas as outras.
injeta_todos :-
	ficheiro_base(B), !,
	carrega_bc(B),
	arranca_motor,
	sequencia_base(S), sequencia_normal(N), append(S, N, Todos),
	injeta_cenarios(Todos),
	nl, estado.
injeta_todos :-
	writeln('Primeiro carregue a base com carrega_bc(\'.../soc_factos.txt\').').

lista_cenarios :-
	forall(cenario(Id, Descr, _), format('  ~w~t~16|~w~n', [Id, Descr])).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% VISUALIZACAO DA BASE DE FACTOS

mostra_factos :-
	forall(facto(N, F), format('O facto n. ~w -> ~w~n', [N, F])).

% So os factos com um dado nome (ex.: mostra_factos(incidente)).
mostra_factos(Nome) :-
	forall(( facto(N, F), functor(F, Nome, _) ),
	       format('O facto n. ~w -> ~w~n', [N, F])).

% So os factos concluidos pelas regras.
mostra_conclusoes :-
	forall(( facto(N, F), justifica(N, ID, _) ),
	       format('O facto n. ~w -> ~w   (regra ~w)~n', [N, F, ID])).

mostra_regra(ID) :-
	regra ID se LHS entao RHS, !,
	format('regra ~w~n  SE~n', [ID]),
	escreve_lhs(LHS),
	writeln('  ENTAO'),
	forall(member(A, RHS), format('     ~w~n', [A])).
mostra_regra(ID) :-
	format('Nao ha regra ~w.~n', [ID]).

escreve_lhs([C e R]) :- !, format('     ~w~n', [C]), escreve_lhs([R]).
escreve_lhs([C])     :- format('     ~w~n', [C]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% EXPLICACOES DO TIPO "COMO?"
% como(N) ou como(Facto). Mostra a arvore de regras e factos que levou a conclusao.
% Um facto que ja foi explicado na mesma arvore nao volta a ser explicado: aparece "(ja explicado acima)".
% (no sp_exp2 a mesma sub-arvore repetia-se, e as conclusoes do fim da cadeia davam centenas de linhas)

como(F) :-
	\+ integer(F), !,
	(   facto(N, F)
	->  como(N)
	;   format('O facto ~w nao esta na memoria de trabalho. Experimente whynot(~w).~n', [F, F])
	).
como(N) :-
	ultimo_facto(Last), N > Last, !,
	writeln('Essa conclusao nao foi tirada.').
como(N) :-
	retractall(ja_explicado(_)),
	explica_facto(N, 0),
	retractall(ja_explicado(_)).

explica_facto(N, Nivel) :-
	ja_explicado(N), !,
	facto(N, F),
	legivel(F, FL),
	tab_nivel(Nivel), format('facto ~w: ~w   (ja explicado acima)~n', [N, FL]).
explica_facto(N, Nivel) :-
	justifica(N, ID, LFactos), !,
	assertz(ja_explicado(N)),
	facto(N, F), legivel(F, FL),
	tab_nivel(Nivel), format('facto ~w: ~w~n', [N, FL]),
	tab_nivel(Nivel), format('   concluido pela regra ~w, porque:~n', [ID]),
	N1 is Nivel + 1,
	forall(member(P, LFactos), explica_premissa(P, N1)).
explica_facto(N, Nivel) :-
	facto(N, F), legivel(F, FL),
	assertz(ja_explicado(N)),
	tab_nivel(Nivel), format('facto ~w: ~w   (dado)~n', [N, FL]).

explica_premissa(N, Nivel) :- integer(N), !, explica_facto(N, Nivel).
explica_premissa(nao X, Nivel) :- !,
	legivel(X, XL),
	tab_nivel(Nivel), format('nao se verifica ~w~n', [XL]).
explica_premissa(C, Nivel) :-
	legivel(C, CL),
	tab_nivel(Nivel), format('a condicao ~w verifica-se (calculada)~n', [CL]).

tab_nivel(Nivel) :- Esp is Nivel * 4, tab(Esp).

% como1(N): so o primeiro nivel (a regra e as premissas), sem descer na arvore.
como1(F) :- \+ integer(F), !, ( facto(N, F) -> como1(N) ; writeln('Esse facto nao esta na memoria de trabalho.') ).
como1(N) :-
	facto(N, F),
	(   justifica(N, ID, LFactos)
	->  format('facto ~w: ~w~n   concluido pela regra ~w, porque:~n', [N, F, ID]),
	    forall(member(P, LFactos), escreve_premissa(P))
	;   format('facto ~w: ~w   (dado)~n', [N, F])
	).

escreve_premissa(N) :- integer(N), !, facto(N, F), format('    facto ~w: ~w~n', [N, F]).
escreve_premissa(nao X) :- !, legivel(X, XL), format('    nao se verifica ~w~n', [XL]).
escreve_premissa(C) :- legivel(C, CL), format('    a condicao ~w verifica-se (calculada)~n', [CL]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% EXPLICACOES DO TIPO "PORQUE NAO?"
% Exemplo: ?- whynot(ataque_dirigido(ev_e1,web_srv_04)).
% Considera todas as regras que podiam concluir o facto e, em cada uma, as premissas que falham.
% Desce pelas premissas que outras regras poderiam concluir, ate opcao(profundidade_whynot, N).
% Diferencas para o sp_exp2: trata as condicoes calculadas (nao tem regras: diz so que sao falsas),
% nao repete um facto ja explicado, diz quando um facto e um dado de entrada, e uma premissa que falhou
% nao fica com valores inventados por uma condicao que vem a seguir (ver encontra_premissas_falsas).
% A explicacao monta-se primeiro como uma lista de linhas (whynot_linhas/3, issue #7), que o wrapper.pl da em JSON,
% e o whynot/1 escreve essas linhas.

whynot(Facto) :-
	opcao(profundidade_whynot, Max),
	whynot_linhas(Facto, Max, Linhas),
	forall(member(L, Linhas), escreve_linha_whynot(L)).

% whynot_linhas(+Facto, +Profundidade, -Linhas): as linhas do "porque nao?", pela ordem em que se escrevem.
% Cada linha e linha(Nivel, Tipo, Campos), em que Campos e uma lista Chave-Valor com o que a linha precisa:
%   termo-T, numero-N (o numero do facto, ou '?'), encontrado-T (o facto que verificou uma negacao),
%   regra-ID, se-Condicoes (o LHS, cada uma condicao(Tipo, Termo, sim/nao, Numero ou nenhum)) e entao-Factos.
% Os tipos sao os do Drools (PorqueNao.java), para os dois motores darem as mesmas linhas.
% Os termos sao copias tiradas no momento da linha: uma ligacao feita depois nao muda uma linha ja feita.
whynot_linhas(Facto, Max, Linhas) :-
	retractall(ja_visto_whynot(_)),
	phrase(whynot(Facto, 1, Max), Linhas),
	retractall(ja_visto_whynot(_)).

whynot(Facto, Nivel, _) -->
	{ \+ Facto = (nao _), facto(N, Facto) }, !,
	linha_whynot(Nivel, verdadeiro, [termo-Facto, numero-N]).
whynot(nao Facto, Nivel, _) --> !,
	linha_whynot(Nivel, nao_verdadeiro, [termo-Facto]).
whynot(Facto, Nivel, _) -->
	{ calculada(Facto) }, !,
	linha_whynot(Nivel, calculada_falsa, [termo-Facto]).
whynot(Facto, Nivel, _) -->
	{ ja_visto_whynot(F0), F0 =@= Facto }, !,
	linha_whynot(Nivel, ja_explicado, [termo-Facto]).
whynot(Facto, Nivel, _) -->
	{ \+ regra_conclui(Facto) }, !,
	linha_whynot(Nivel, dado_de_entrada, [termo-Facto]).
whynot(_, Nivel, Max) -->
	{ Nivel > Max }, !,
	linha_whynot(Nivel, profundidade, []).
whynot(Facto, Nivel, Max) -->
	{ copy_term(Facto, Copia),
	  assertz(ja_visto_whynot(Copia)),
	  encontra_regras_whynot(Facto, LR),
	  LR \== [] }, !,
	whynot1(LR, Nivel, Max).
whynot(Facto, Nivel, _) -->
	linha_whynot(Nivel, falta_correr_motor, [termo-Facto]).

linha_whynot(Nivel, Tipo, Campos0) -->
	{ copy_term(Campos0, Campos) },
	[linha(Nivel, Tipo, Campos)].

regra_conclui(Facto) :-
	regra _ se _ entao RHS,
	member(cria_facto(Facto), RHS), !.

% As explicacoes consideram todas as regras que poderiam dar origem a conclusao relativa ao facto.
% Cada uma vem com as premissas falsas, o LHS com o estado de cada condicao e os factos que a regra conclui.
encontra_regras_whynot(Facto, LR) :-
	findall(r(ID, LPF, Se, Entao),
		( regra ID se LHS entao RHS,
		  member(cria_facto(Facto), RHS),
		  encontra_premissas_falsas(LHS, LPF, Se),
		  LPF \== [],
		  conclusoes(RHS, Entao) ),
		LR).

conclusoes([], []).
conclusoes([cria_facto(F)|As], [F|Fs]) :- !, conclusoes(As, Fs).
conclusoes([_|As], Fs) :- conclusoes(As, Fs).

whynot1([], _, _) --> [].
whynot1([r(ID, LPF, Se, Entao)|LR], Nivel, Max) -->
	linha_whynot(Nivel, regra, [regra-ID, se-Se, entao-Entao]),
	{ Nivel1 is Nivel + 1 },
	explica_porque_nao(LPF, Nivel1, Max),
	whynot1(LR, Nivel, Max).

% Percorre o LHS por ordem. Uma condicao que se verifica fica com as variaveis instanciadas (para as seguintes);
% uma que falha vai para a lista das premissas falsas. Uma condicao que partilha variaveis com uma premissa
% que ja falhou so se testa (sobre uma copia), sem instanciar nada: senao uma condicao calculada como
% anterior(EvAnterior, Ev) dava a premissa falhada um valor qualquer e a explicacao ficava errada.
% Se tem o estado de cada condicao, pela ordem do LHS (ver estado_condicao).
encontra_premissas_falsas(LHS, LPF, Se) :-
	lista_condicoes(LHS, Cs),
	premissas_falsas(Cs, [], LPF, Se).

lista_condicoes([C e Resto], [C|Cs]) :- !, lista_condicoes([Resto], Cs).
lista_condicoes([C], [C]).

premissas_falsas([], _, [], []).
premissas_falsas([C|Cs], Falhadas, LPF, [E|Es]) :-
	(   partilha_variaveis(C, Falhadas)
	->  ( copy_term(C, C1), verifica_condicao(C1, J) -> Ok = sim ; Ok = nao )
	;   ( verifica_condicao(C, J) -> Ok = sim ; Ok = nao )
	),
	(   Ok == sim
	->  LPF = LPF1, Falhadas1 = Falhadas
	;   LPF = [C|LPF1], Falhadas1 = [C|Falhadas]
	),
	estado_condicao(C, Ok, J, E),
	premissas_falsas(Cs, Falhadas1, LPF1, Es).

% condicao(Tipo, Termo, Verificada, Numero): Tipo e nao (Termo e o que esta negado), avalia (Termo e o de avalia/1)
% ou facto (tambem as condicoes calculadas); Numero e o do facto que verificou a condicao, so num facto, ou nenhum.
estado_condicao(nao T, Ok, _, condicao(nao, T, Ok, nenhum)) :- !.
estado_condicao(avalia(T), Ok, _, condicao(avalia, T, Ok, nenhum)) :- !.
estado_condicao(C, Ok, J, condicao(facto, C, Ok, N)) :-
	( Ok == sim, integer(J) -> N = J ; N = nenhum ).

partilha_variaveis(C, Termos) :-
	term_variables(C, V1),
	term_variables(Termos, V2),
	member(X, V1), member(Y, V2), X == Y, !.

explica_porque_nao([], _, _) --> [].
explica_porque_nao([nao avalia(X)|LPF], Nivel, Max) --> !,
	linha_whynot(Nivel, condicao_nao_falsa, [termo-X]),
	explica_porque_nao(LPF, Nivel, Max).
explica_porque_nao([avalia(X)|LPF], Nivel, Max) --> !,
	linha_whynot(Nivel, condicao_falsa, [termo-X]),
	explica_porque_nao(LPF, Nivel, Max).
explica_porque_nao([nao X|LPF], Nivel, Max) --> !,
	linha_whynot(Nivel, premissa_nao_falsa, [termo-X]),
	% o facto procura-se sobre uma copia: as premissas seguintes nao ficam com os valores dele (como no Drools)
	{ Nivel1 is Nivel + 1,
	  copy_term(X, X1),
	  ( facto(N, X1) -> Campos = [termo-X, numero-N, encontrado-X1] ; Campos = [termo-X, numero-'?'] ) },
	linha_whynot(Nivel1, facto_verdadeiro, Campos),
	explica_porque_nao(LPF, Nivel, Max).
explica_porque_nao([C|LPF], Nivel, Max) -->
	{ calculada(C) }, !,
	linha_whynot(Nivel, calculada_premissa_falsa, [termo-C]),
	explica_porque_nao(LPF, Nivel, Max).
explica_porque_nao([P|LPF], Nivel, Max) -->
	linha_whynot(Nivel, premissa_falsa, [termo-P]),
	{ Nivel1 is Nivel + 1 },
	whynot(P, Nivel1, Max),
	explica_porque_nao(LPF, Nivel, Max).

% Escreve uma linha do "porque nao?". So a primeira (o facto existe) nao leva a margem do nivel.
escreve_linha_whynot(linha(_, verdadeiro, [termo-F, numero-N])) :- !,
	legivel(F, FL),
	format('O facto ~w nao e falso! (facto n. ~w)~n', [FL, N]).
escreve_linha_whynot(linha(Nivel, Tipo, Campos)) :-
	( memberchk(termo-T, Campos) -> legivel(T, TL) ; TL = '' ),
	formata(Nivel),
	texto_linha_whynot(Tipo, TL, Campos).

texto_linha_whynot(nao_verdadeiro, TL, _) :-
	format('Porque: o facto ~w e verdadeiro~n', [TL]).
texto_linha_whynot(calculada_falsa, TL, _) :-
	format('Porque: a condicao calculada ~w e falsa~n', [TL]).
texto_linha_whynot(ja_explicado, TL, _) :-
	format('(~w: ja explicado acima)~n', [TL]).
texto_linha_whynot(dado_de_entrada, TL, _) :-
	format('Porque: o facto ~w nao esta na base e nenhuma regra o conclui (e um dado de entrada)~n', [TL]).
texto_linha_whynot(profundidade, _, _) :-
	writeln('(... para ver mais fundo: define_opcao(profundidade_whynot, N))').
texto_linha_whynot(falta_correr_motor, TL, _) :-
	format('As premissas de uma regra que conclui ~w verificam-se: falta correr o motor (arranca_motor).~n', [TL]).
texto_linha_whynot(regra, _, Campos) :-
	memberchk(regra-ID, Campos),
	format('Porque pela regra ~w:~n', [ID]).
texto_linha_whynot(condicao_nao_falsa, TL, _) :-
	format('A condicao nao ~w e falsa~n', [TL]).
texto_linha_whynot(condicao_falsa, TL, _) :-
	format('A condicao ~w e falsa~n', [TL]).
texto_linha_whynot(premissa_nao_falsa, TL, _) :-
	format('A premissa nao ~w e falsa~n', [TL]).
texto_linha_whynot(facto_verdadeiro, TL, Campos) :-
	memberchk(numero-N, Campos),
	format('Porque: o facto ~w e verdadeiro (facto n. ~w)~n', [TL, N]).
texto_linha_whynot(calculada_premissa_falsa, TL, _) :-
	format('A condicao calculada ~w e falsa~n', [TL]).
texto_linha_whynot(premissa_falsa, TL, _) :-
	format('A premissa ~w e falsa~n', [TL]).

formata(Nivel) :- Esp is (Nivel - 1) * 5, tab(Esp).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% CONSULTAS PARA A INTERFACE

% Perguntas ao analista que estao por responder: nao retiradas, e sem resposta (ou so com "nao sei",
% que se pode substituir quando o analista conseguir verificar).
pergunta_pendente(N, Ativo, Ev, P) :-
	facto(N, pergunta_analista(Ativo, Ev, P)),
	\+ facto(_, pergunta_retirada(Ativo, Ev, P)),
	\+ ( facto(_, resposta_analista(Ativo, Ev, P, R)), R \== nao_sei ).

perguntas_pendentes(L) :-
	findall(N-p(A,E,P), pergunta_pendente(N, A, E, P), L).

ticket_aberto(Inc) :-
	facto(_, ticket(Inc,_,_,_,_,_)),
	\+ facto(_, ticket_fechado(Inc,_)).

% Tickets abertos por prioridade (p1 primeiro).
tickets_abertos(L) :-
	findall(P-t(Inc,Ativo,Acao,G,Imp),
		( facto(_, ticket(Inc,Ativo,Acao,G,P,Imp)), \+ facto(_, ticket_fechado(Inc,_)) ),
		L0),
	msort(L0, L).

tickets_fechados(L) :-
	findall(t(Inc,Ativo,Motivo),
		( facto(_, ticket(Inc,Ativo,_,_,_,_)), facto(_, ticket_fechado(Inc,Motivo)) ),
		L).

estado :-
	findall(N, facto(N,_), LF), length(LF, NF),
	findall(N, justifica(N,_,_), LC), length(LC, NC),
	findall(I, facto(_, incidente(I,_,_,_)), LI), length(LI, NI),
	findall(I, facto(_, ticket(I,_,_,_,_,_)), LT), length(LT, NT),
	findall(I, ticket_aberto(I), LA), length(LA, NA),
	perguntas_pendentes(LP), length(LP, NP),
	NFech is NT - NA,
	format('~w factos (~w concluidos pelo motor) | ~w incidentes | ~w tickets: ~w abertos, ~w fechados | ~w perguntas por responder~n',
	       [NF, NC, NI, NT, NA, NFech, NP]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% INTERFACE (menu de texto). As respostas escrevem-se como termos Prolog, terminadas com ponto.

menu :-
	repeat,
	nl, writeln('========== ANALISTA VIRTUAL DE SOC =========='),
	( bc_carregada -> estado ; writeln('(nenhuma base carregada)') ),
	writeln(' 1. Carregar a base de conhecimento'),
	writeln(' 2. Correr o motor'),
	writeln(' 3. Injetar um cenario'),
	writeln(' 4. Introduzir um facto (telemetria)'),
	writeln(' 5. Responder as perguntas ao analista'),
	writeln(' 6. Incidentes e tickets'),
	writeln(' 7. Acoes recomendadas'),
	writeln(' 8. Registar a recuperacao de uma maquina'),
	writeln(' 9. Explicar como se concluiu um facto (como?)'),
	writeln('10. Explicar porque nao se concluiu um facto (porque nao?)'),
	writeln('11. Mostrar factos'),
	writeln('12. Opcoes do motor'),
	writeln('13. Injetar todos os cenarios de uma vez'),
	writeln(' 0. Sair'),
	le('Opcao', Op),
	( catch(executa(Op), E, print_message(error, E)) -> true ; true ),
	Op == 0, !.

executa(0) :- !.
executa(1) :- !,
	le('Ficheiro da base (ex.: \'../../base_conhecimento/soc_factos.txt\')', F),
	carrega_bc(F).
executa(Op) :-
	\+ bc_carregada, Op \== 12, !,
	writeln('Primeiro carregue a base de conhecimento (opcao 1).').
executa(2) :- !, arranca_motor.
executa(3) :- !,
	(   current_predicate(cenario/3)
	->  lista_cenarios,
	    writeln('  (ou o nome de um ficheiro, ex.: \'cenarios/cenario_30.txt\')'),
	    le('Cenario (ex.: c2), ou uma lista (ex.: [c1,c2,c3])', C),
	    ( is_list(C) -> injeta_cenarios(C) ; injeta_cenario(C) )
	;   writeln('Nao ha cenarios carregados (falta o soc_cenarios.pl ao lado da base).')
	).
executa(4) :- !,
	writeln('Escreva um facto ou uma lista de factos, ex.: [alerta_ids(ev_1,web_srv_02,sig_apache_path_traversal), tempo(ev_1,900)]'),
	le('Facto(s)', F),
	( is_list(F) -> introduz_factos(F) ; introduz_facto(F) ),
	arranca_motor.
executa(5) :- !, responde_perguntas.
executa(6) :- !, mostra_incidentes.
executa(7) :- !, mostra_acoes.
executa(8) :- !,
	writeln('Regista que uma maquina comprometida foi recuperada: um login a partir dela deixa de contar como vindo de uma maquina comprometida.'),
	findall(A-E, ( facto(_, compromisso_confirmado(A, E)), \+ facto(_, recuperado(A, E)) ), L),
	( L == [] -> writeln('Nao ha compromissos confirmados por recuperar.')
	; writeln('Compromissos confirmados por recuperar:'), forall(member(A-E, L), format('   ~w (evento ~w)~n', [A, E])) ),
	le('Ativo', A), le('Evento do compromisso', E),
	introduz_facto(recuperado(A, E)),
	arranca_motor.
executa(9) :- !,
	le('Numero do facto ou o proprio facto', F),
	como(F).
executa(10) :- !,
	le('Facto (ex.: compromisso_confirmado(web_srv_04,ev_e1))', F),
	whynot(F).
executa(11) :- !,
	writeln('  todos. | concluidos. | o nome de um facto, ex.: incidente.'),
	le('Que factos', Q),
	(   Q == todos -> mostra_factos
	;   Q == concluidos -> mostra_conclusoes
	;   mostra_factos(Q)
	).
executa(12) :- !,
	forall(opcao(N, V), format('   ~w = ~w~n', [N, V])),
	le('Opcao a mudar (ou nada.)', N),
	(   N == nada -> true
	;   opcao(N, _) -> le('Valor (sim. / nao.)', V), define_opcao(N, V)
	;   writeln('Opcao desconhecida.')
	).
executa(13) :- !, injeta_todos.
executa(_) :-
	writeln('Opcao invalida.').

% Le um termo, com uma mensagem. Volta a pedir se houver um erro de sintaxe; fim de ficheiro conta como 0.
le(Msg, X) :-
	format('~w: ', [Msg]), flush_output,
	catch(read(X0), _, (writeln('Erro de sintaxe: escreva um termo Prolog terminado com ponto.'), fail)), !,
	( X0 == end_of_file -> X = 0 ; X = X0 ).
le(Msg, X) :- le(Msg, X).


% --- Perguntas ao analista ---------------------------------------------------------------

responde_perguntas :-
	perguntas_pendentes(L),
	(   L == []
	->  writeln('Nao ha perguntas por responder.')
	;   writeln('Perguntas por responder:'),
	    escreve_perguntas(L, 1),
	    length(L, Max),
	    le('Numero da pergunta (0 para voltar)', I),
	    (   integer(I), I >= 1, I =< Max
	    ->  nth1(I, L, N-p(A, E, P)),
		pergunta(N, A, E, P),
		responde_perguntas
	    ;   true
	    )
	).

escreve_perguntas([], _).
escreve_perguntas([_-p(A,E,P)|R], I) :-
	(   facto(_, resposta_analista(A,E,P,nao_sei)) -> Nota = '  (respondida "nao sei")' ; Nota = '' ),
	format('  ~w. ~w sobre o evento ~w: ~w~w~n', [I, A, E, P, Nota]),
	I1 is I + 1,
	escreve_perguntas(R, I1).

% Faz a pergunta N. O analista pode responder, ou perguntar "porque?" (estilo MYCIN):
% o sistema mostra a regra que gerou a pergunta e as premissas que a levaram a ser feita.
pergunta(N, Ativo, Ev, P) :-
	nl,
	format('Ativo: ~w   Evento: ~w~n', [Ativo, Ev]),
	( facto(_, texto_pergunta(P, Texto)) -> writeln(Texto) ; format('~w?~n', [P]) ),
	findall(R-T, facto(_, opcao_resposta(P, R, T)), Opcoes0),
	( Opcoes0 == [] -> Opcoes = [sim-'Sim', nao-'Nao', nao_sei-'Nao sei'] ; Opcoes = Opcoes0 ),
	forall(member(R-T, Opcoes), format('   ~w. ~w~n', [R, T])),
	writeln('   porque. Porque e que me esta a perguntar isto?'),
	le('Resposta', Resp),
	(   Resp == porque
	->  porque_pergunta(N, Ativo, P),
	    pergunta(N, Ativo, Ev, P)
	;   memberchk(Resp-_, Opcoes)
	->  introduz_facto(resposta_analista(Ativo, Ev, P, Resp)),
	    arranca_motor
	;   writeln('Resposta invalida.'),
	    pergunta(N, Ativo, Ev, P)
	).

porque_pergunta(N, Ativo, P) :-
	nl,
	(   justifica(N, ID, _)
	->  format('Pergunto porque a regra ~w pediu esta verificacao em ~w, pelo seguinte:~n', [ID, Ativo]),
	    como1(N),
	    escreve_regras_que_usam_resposta(P)
	;   writeln('Esta pergunta foi introduzida a mao.')
	),
	nl.

% As regras que usam a resposta a esta pergunta (as que tem resposta_analista(_,_,P,_) no SE).
escreve_regras_que_usam_resposta(P) :-
	findall(ID, ( regra ID se LHS entao _, facto_esta_numa_condicao(resposta_analista(_,_,P,_), LHS) ), IDs0),
	sort(IDs0, IDs),
	( IDs == [] -> true ; format('A resposta vai ser usada pelas regras ~w.~n', [IDs]) ).


% --- Incidentes, tickets e acoes ---------------------------------------------------------

mostra_incidentes :-
	tickets_abertos(LA),
	length(LA, NA),
	format('~nTICKETS ABERTOS (~w), por prioridade:~n', [NA]),
	forall(member(P-t(Inc, Ativo, Acao, G, Imp), LA),
	       ( Inc = inc(Tipo, Ev),
		 format('  [~w] ~w  ativo: ~w  evento: ~w  gravidade: ~w~n', [P, Tipo, Ativo, Ev, G]),
		 format('       acao: ~w   ativos impactados: ~w~n', [Acao, Imp]),
		 forall(facto(_, motivo(Inc, M)), format('       motivo: ~w~n', [M])),
		 ( facto(_, significado(Tipo, S)) -> format('       (~w)~n', [S]) ; true ) )),
	tickets_fechados(LF),
	length(LF, NF),
	format('~nTICKETS FECHADOS (~w):~n', [NF]),
	forall(member(t(inc(Tipo, Ev), Ativo, Motivo), LF),
	       format('  ~w  ativo: ~w  evento: ~w  -> ~w~n', [Tipo, Ativo, Ev, Motivo])),
	findall(Inc, ( facto(_, incidente(Inc,_,_,_)), \+ facto(_, ticket(Inc,_,_,_,_,_)) ), SemTicket),
	(   SemTicket == [] -> true
	;   format('~nINCIDENTES SEM TICKET: ~w~n', [SemTicket])
	).

mostra_acoes :-
	findall(Ativo, facto(_, acao(Ativo, _)), L0),
	sort(L0, Ativos),
	(   Ativos == []
	->  writeln('Nao ha acoes recomendadas.')
	;   forall(member(A, Ativos),
		   ( format('~n~w:~n', [A]),
		     forall(facto(N, acao(A, Ac)),
			    ( format('   ~w   (facto ~w)~n', [Ac, N]),
			      functor(Ac, Nome, _),
			      ( facto(_, significado(Nome, S)) -> format('      ~w~n', [S]) ; true ) )) ))
	).