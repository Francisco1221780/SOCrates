% Gera o metaconhecimento (facto_dispara_regras/2) a partir das regras carregadas.
% Uso, a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl motores/prolog/ferramentas/gera_meta.pl                 escreve as linhas no stdout, para colar em soc_suporte.txt
%   swipl motores/prolog/ferramentas/gera_meta.pl --verificar     nao escreve nada; sai com codigo 1 se o metaconhecimento
%                                                                 do soc_suporte.txt nao for o que as regras dao hoje (AC13)
% ou, a mao, depois de carregar o motor e a base de regras:
%   ?- consult('soc_regras.txt').   ?- consult('soc_suporte.txt').   ?- gera_meta.
% Escreve uma linha facto_dispara_regras(Padrao, [Regras]) por tipo de facto.
% Um facto dispara uma regra em dois casos:
%   1. aparece numa condicao positiva da regra (nao conta "nao X" nem as condicoes calculadas);
%   2. e lido por uma condicao calculada da regra, segundo condicao_le/2 em soc_suporte.txt (ex.: tempo/2 para depois/3).
%      Assim, um facto que chega depois (o tempo de um evento, o porto de destino de um alerta) faz reavaliar as regras.
% O padrao so distingue o nome e o numero de argumentos do facto (ex.: alerta_ids(_,_,_)).
% O --verificar compara os termos, nao o texto: a ordem e a formatacao das linhas no soc_suporte.txt nao contam.

% Os operadores das regras, os mesmos do soc_motor.pl (para este ficheiro se ler antes ou sem o motor).
:- op(220, xfx, entao).
:- op(35, xfy, se).
:- op(240, fx, regra).
:- op(500, fy, nao).
:- op(600, xfy, e).

:- use_module(library(lists)).
:- use_module(library(apply)).
:- use_module(library(pairs)).

:- dynamic pasta_ferramentas/1.
:- prolog_load_context(directory, D), asserta(pasta_ferramentas(D)).

% So corre o main quando e o script do swipl; com consult/1 so define o gera_meta/0.
:- if(( current_prolog_flag(associated_file, Script),
	prolog_load_context(source, Este),
	same_file(Script, Este) )).
:- initialization(main, main).
:- endif.

main :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	catch(corre(Argv), E, ( print_message(error, E), halt(1) )).

corre(Argv) :-
	carrega_regras,
	(   memberchk('--verificar', Argv)
	->  verifica
	;   gera_meta
	).

raiz(Raiz) :-
	pasta_ferramentas(D),
	directory_file_path(D, '../../..', R0),
	absolute_file_name(R0, Raiz, [file_type(directory)]).

% Carrega o motor, as regras e o suporte (sem os factos nem os cenarios, que nao entram no metaconhecimento).
% As mensagens do consult vao para o stderr; um erro ao carregar para o script, para nao comparar com uma base partida.
carrega_regras :-
	raiz(Raiz),
	forall(member(Rel, ['motores/prolog/soc_motor.pl', 'base_conhecimento/soc_regras.txt',
			    'base_conhecimento/soc_suporte.txt']),
	       (   directory_file_path(Raiz, Rel, F),
		   carrega(Rel, F)
	       )),
	(   current_predicate(regra/1) -> true
	;   throw(erro_meta('o soc_regras.txt nao tem regras'))
	).

carrega(Rel, F) :-
	nb_setval(erros_ao_carregar, 0),
	setup_call_cleanup(asserta((user:message_hook(_, error, _) :- conta_erro, fail), Ref),
			   with_output_to(user_error, load_files(F, [silent(true)])),
			   erase(Ref)),
	(   nb_getval(erros_ao_carregar, 0) -> true
	;   format(atom(M), 'erros ao carregar ~w', [Rel]), throw(erro_meta(M))
	).

conta_erro :- nb_getval(erros_ao_carregar, N), N1 is N + 1, nb_setval(erros_ao_carregar, N1).

gera_meta :-
	meta(Clausulas),
	forall(member(C, Clausulas), portray_clause(C)).

% As clausulas facto_dispara_regras(Padrao, IDs), por ordem do nome e do numero de argumentos do facto.
meta(Clausulas) :-
	findall(Nome/N-ID,
		( regra ID se LHS entao _,
		  condicoes(LHS, Cs),
		  member(C, Cs),
		  chave(C, Nome/N) ),
		Diretos),
	findall(Nome/N-ID,
		( regra ID se LHS entao _,
		  condicoes(LHS, Cs),
		  member(C, Cs),
		  lido_por_calculada(C, Nome/N) ),
		Lidos),
	append(Diretos, Lidos, Pares),
	findall(K, member(K-_, Pares), Ks0),
	sort(Ks0, Ks),
	findall(facto_dispara_regras(P, IDs),
		( member(Nome/N, Ks),
		  findall(ID, member(Nome/N-ID, Pares), IDs0),
		  sort(IDs0, IDs),
		  functor(P, Nome, N) ),
		Clausulas).

condicoes([A e B], Cs) :- !, condicoes([A], C1), condicoes([B], C2), append(C1, C2, Cs).
condicoes([C], [C]).

chave(avalia(F), Nome/2) :- !, functor(F, Nome, 3).
chave(nao _, _) :- !, fail.
chave(C, _) :- \+ \+ condicao_calculada(C), !, fail.
chave(C, Nome/N) :- functor(C, Nome, N).

lido_por_calculada(nao _, _) :- !, fail.
lido_por_calculada(C, Nome/N) :-
	\+ \+ condicao_calculada(C),
	functor(C, F, A), functor(C0, F, A),
	condicao_le(C0, Factos),
	member(Facto, Factos),
	functor(Facto, Nome, N).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% VERIFICACAO (--verificar)

% Compara, por tipo de facto, as regras que o soc_suporte.txt diz com as que as regras dao hoje.
% Um tipo de facto que aparece mais de uma vez no soc_suporte.txt tambem conta como desatualizado.
verifica :-
	meta(Esperadas),
	maplist(par_meta, Esperadas, PE),
	(   current_predicate(facto_dispara_regras/2)
	->  findall(C, ( C = facto_dispara_regras(_, _), clause(C, true) ), Atuais)
	;   Atuais = []
	),
	maplist(par_meta, Atuais, PA),
	findall(K, ( member(K-_, PE) ; member(K-_, PA) ), Ks0),
	sort(Ks0, Ks),
	include(diferente(PE, PA), Ks, Maus),
	(   Maus == []
	->  format(user_error, 'O metaconhecimento esta atualizado.~n', [])
	;   forall(member(K, Maus), explica(PE, PA, K)),
	    format(user_error, 'Corra: swipl motores/prolog/ferramentas/gera_meta.pl e cole as linhas em soc_suporte.txt~n', []),
	    halt(1)
	).

% A chave e o padrao inteiro (com as variaveis numeradas), nao so o nome e o numero de argumentos: o motor so usa a
% linha para os factos que unificam com o padrao, por isso zona(dmz, _) nao serve para zona(_, _).
par_meta(facto_dispara_regras(P, IDs), K-IDs) :- copy_term(P, K), numbervars(K, 0, _).

diferente(PE, PA, K) :-
	findall(I, member(K-I, PE), E),
	findall(I, member(K-I, PA), A),
	E \== A.

explica(PE, PA, K) :-
	findall(I, member(K-I, PE), E),
	findall(I, member(K-I, PA), A),
	(   A == [] -> E = [IE], format(user_error, 'Falta: ~w ~w~n', [K, IE])
	;   A = [_, _|_] -> format(user_error, 'Repetido: ~w ~w~n', [K, A])
	;   E == [] -> A = [IA], format(user_error, 'A mais: ~w ~w~n', [K, IA])
	;   E = [IE], A = [IA], format(user_error, 'Desatualizado: ~w tem ~w, as regras dao ~w~n', [K, IA, IE])
	).

:- multifile prolog:message//1.
prolog:message(erro_meta(M)) --> [ 'gera_meta.pl: ~w'-[M] ].
