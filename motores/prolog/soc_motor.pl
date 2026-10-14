% =====================================================================================
% MOTOR DE INFERENCIA - Analista virtual de SOC (CyberRangers)
% Parte do sp_exp2.pl da UC (encadeamento direto, metaconhecimento, negacao "nao",
% explicacoes "como" e "porque nao") e acrescenta o que a base de conhecimento v5.1 exige:
%   1. CONDICOES CALCULADAS: uma condicao C com condicao_calculada(C) avalia-se com call(C)
%      (depois/3, versao_cmp/3, anterior/2, ...), em vez de se procurar como facto.
%   2. TODAS AS INSTANCIACOES: cada regra dispara para todas as combinacoes de factos que
%      verificam o seu LHS e em que entra o facto que a acionou (o sp_exp2 so disparava a primeira).
%   3. PONTO FIXO: o arranca_motor percorre os factos por ordem, incluindo os que vai criando,
%      e repete a passagem ate nao haver conclusoes novas (no sp_exp2 era preciso chama-lo varias vezes).
%   4. SEM PAUSA NEM ESCRITA no cria_facto (o get0 e o write do sp_exp2). As duas passam a ser opcoes,
%      desligadas por omissao: define_opcao(mostrar_conclusoes, sim) / define_opcao(passo_a_passo, sim).
% Este ficheiro e so o nucleo: nao tem menu, nao le do teclado e nao escreve no stdout. O que diz ao utilizador
% passa por print_message/2 (vai para o stderr), para o wrapper.pl poder responder em JSON no stdout.
% As explicacoes ("como" e "porque nao"), a visualizacao e o menu estao no soc_menu.pl, que carrega este ficheiro.
% Funciona tambem com as bases da UC (ex.: veiculos2.txt): sem metaconhecimento usa todas as regras.
%
% Uso rapido (sem menu):
%   ?- consult('soc_motor.pl').
%   ?- carrega_bc('../../base_conhecimento/soc_factos.txt').
%   ?- introduz_facto(alerta_ids(ev_b1,web_srv_02,sig_apache_path_traversal)), ...
%   ?- arranca_motor.
% Com menu e explicacoes: ?- consult('soc_menu.pl').
% =====================================================================================

:- encoding(utf8).

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
%   passo_a_passo:      espera por ENTER depois de cada conclusao (como o get0 do sp_exp2; so com o soc_menu.pl)
%   profundidade_whynot: quantos niveis o "porque nao" mostra
opcao(mostrar_conclusoes, nao).
opcao(passo_a_passo, nao).
opcao(profundidade_whynot, 4).   % ate onde o "porque nao" desce pelas premissas

define_opcao(Nome, Valor) :-
	retractall(opcao(Nome, _)),
	assertz(opcao(Nome, Valor)).

% Mensagens para o utilizador. Vao para o stderr com print_message/2 (as informativas nao aparecem com swipl -q).
:- multifile prolog:message//1.
prolog:message(soc_motor(Formato, Args)) --> [Formato-Args].

informa(Formato, Args) :- print_message(informational, soc_motor(Formato, Args)).
avisa(Formato, Args)   :- print_message(warning, soc_motor(Formato, Args)).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% CARREGAMENTO DA BASE DE CONHECIMENTO

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
	informa('Base carregada: ~w factos e ~w regras.', [NF, NR]).

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
	->  informa('Nao ha conclusoes novas.', [])
	;   informa('~w factos novos (n. ~w a ~w).', [Novos, Primeiro, Fim])
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
dispara_regra(N, Facto, ID) :-
	forall(( regra ID se LHS entao RHS,
		 facto_esta_numa_condicao(Facto, LHS),
		 verifica_condicoes(LHS, LFactos),
		 memberchk(N, LFactos) ),
	       concluir(RHS, ID, LFactos)).


% O facto aparece numa condicao positiva do LHS (instancia as variaveis da regra)

facto_esta_numa_condicao(F, [F e _]).
facto_esta_numa_condicao(F, [avalia(F1) e _]) :- F =.. [H,H1|_], F1 =.. [H,H1|_].
facto_esta_numa_condicao(F, [_ e Fs]) :- facto_esta_numa_condicao(F, [Fs]).
facto_esta_numa_condicao(F, [F]).
facto_esta_numa_condicao(F, [avalia(F1)]) :- F =.. [H,H1|_], F1 =.. [H,H1|_].


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
	avisa('A regra ~w tem uma acao desconhecida no ENTAO: ~w', [ID, A]),
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

% A pausa do passo_a_passo le do teclado, por isso so existe com o soc_menu.pl (pausa/0).
anuncia(N, F, ID) :-
	( opcao(mostrar_conclusoes, sim)
	-> informa('Foi concluido o facto n. ~w -> ~w   (regra ~w)', [N, F, ID])
	;  true ),
	( opcao(passo_a_passo, sim), current_predicate(pausa/0) -> pausa ; true ).


% Acrescentar um facto vindo de fora (telemetria, resposta do analista, recuperacao).
% Igual ao novo_facto/1 da base, mas nao repete um facto que ja exista.
introduz_facto(F) :-
	facto(N, F), !,
	informa('O facto ~w ja existe (n. ~w).', [F, N]).
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
	informa('=== Cenario ~w: ~w', [Id, Descr]),
	(   is_list(X)
	->  introduz_factos(X), arranca_motor
	;   injeta_ficheiro_silencioso(X)
	).
injeta_cenario(F) :-
	atom(F), resolve_ficheiro(F, _), !,
	injeta_ficheiro(F).
injeta_cenario(_) :-
	\+ current_predicate(cenario/3), !,
	avisa('Nao ha cenarios carregados: o soc_cenarios.pl nao estava na pasta do soc_factos.txt.~n~w',
	      ['Carregue-o com consult(\'caminho/para/soc_cenarios.pl\') ou ponha-o ao lado do soc_factos.txt e volte a correr carrega_bc.']).
injeta_cenario(Id) :-
	avisa('Nao conheco o cenario ~w (lista_cenarios. mostra os que ha).', [Id]).

injeta_cenarios(L) :- forall(member(Id, L), injeta_cenario(Id)).

% Injeta um ficheiro de cenario e corre o motor. O ficheiro pode ter, por qualquer ordem:
%   novo_facto(F).   ou   :- novo_facto(F).   (como os blocos do soc_factos.txt, ja sem o '% ')
%   F.               (o proprio facto, ex.: alerta_ids(ev_1,web_srv_02,sig_apache_path_traversal).)
%   linhas de comentario, que se ignoram.
% Os factos entram com introduz_facto/1 (numero seguinte, sem repetir), e no fim corre o motor.
% Um ficheiro relativo procura-se primeiro na pasta atual e depois na pasta do soc_factos.txt.
injeta_ficheiro(Ficheiro) :-
	informa('=== Ficheiro ~w', [Ficheiro]),
	injeta_ficheiro_silencioso(Ficheiro).

injeta_ficheiro_silencioso(Ficheiro) :-
	(   resolve_ficheiro(Ficheiro, Caminho)
	->  setup_call_cleanup(open(Caminho, read, S),
			       le_termos_cenario(S),
			       close(S)),
	    arranca_motor
	;   avisa('Nao encontro o ficheiro ~w (nem na pasta atual nem na pasta da base).', [Ficheiro])
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

