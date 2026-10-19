% Gera a referencia Prolog SO COM AS REGRAS DOS GRUPOS A a G e N (R1-R34 e R89-R92) para o teste de paridade do Drools
% (motores/drools/src/test/resources/referencia_agn/<variante>.json). E o "feito quando" da issue #13: com as mesmas
% regras ativas, o Drools tem de dar os mesmos factos, com a mesma regra e as mesmas premissas, que o Prolog.
%
% Faz uma copia temporaria de base_conhecimento/ com o soc_regras.txt filtrado para essas regras, corre o wrapper.pl
% sobre as mesmas variantes do motores/prolog/ferramentas/gera_referencias.pl e escreve os ficheiros no mesmo formato
% da referencia_prolog/ (variante, cenarios, resposta).
% Uso, a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl motores/drools/ferramentas/gera_referencia_agn.pl                escreve os ficheiros
%   swipl motores/drools/ferramentas/gera_referencia_agn.pl --verificar    nao escreve nada; sai com codigo 1 se algum
%                                                                          estiver desatualizado
% Os passos de cada cenario vem do cenarios.json gerado pelo gera_json.pl: corre-se esse primeiro.

% A biblioteca JSON passou de library(http/json) para library(json) no SWI-Prolog 10; aceitam-se as duas.
:- if(exists_source(library(json))).
:- use_module(library(json)).
:- else.
:- use_module(library(http/json)).
:- endif.
:- use_module(library(process)).
:- use_module(library(readutil)).
:- use_module(library(lists)).
:- use_module(library(apply)).
:- use_module(library(filesex)).

:- initialization(main, main).

:- dynamic pasta_ferramentas/1.
:- prolog_load_context(directory, D), asserta(pasta_ferramentas(D)).

% As regras que ficam ativas, e quantas sao (o gerador falha se o filtro nao der este numero).
regra_ativa(N) :- between(1, 34, N) ; between(89, 92, N).
regras_ativas(38).

main :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	catch(corre(Argv), E, ( print_message(error, E), halt(1) )).

corre(Argv) :-
	raiz(Raiz),
	cenarios(Raiz, Cenarios, Seqs),
	variantes(Vs),
	setup_call_cleanup(base_filtrada(Raiz, Base),
			   gera(Argv, Raiz, Base, Cenarios, Seqs, Vs),
			   delete_directory_and_contents(Base)).

gera(Argv, Raiz, Base, Cenarios, Seqs, Vs) :-
	(   memberchk('--verificar', Argv)
	->  include(desatualizada(Raiz, Base, Cenarios, Seqs), Vs, Maus),
	    (   Maus == []
	    ->  format(user_error, 'As referencias A-G+N estao atualizadas.~n', [])
	    ;   forall(member(V, Maus), format(user_error, 'Desatualizada: referencia_agn/~w.json~n', [V])),
		format(user_error, 'Corra: swipl motores/drools/ferramentas/gera_referencia_agn.pl~n', []),
		halt(1)
	    )
	;   forall(member(V, Vs), escreve(Raiz, Base, Cenarios, Seqs, V))
	).

raiz(Raiz) :-
	pasta_ferramentas(D),
	directory_file_path(D, '../../..', R0),
	absolute_file_name(R0, Raiz, [file_type(directory)]).

ficheiro(Raiz, V, F) :-
	format(atom(Rel), 'motores/drools/src/test/resources/referencia_agn/~w.json', [V]),
	directory_file_path(Raiz, Rel, F).

escreve(Raiz, Base, Cenarios, Seqs, V) :-
	texto(Base, Cenarios, Seqs, V, Texto),
	ficheiro(Raiz, V, F),
	file_directory_name(F, Pasta),
	make_directory_path(Pasta),
	setup_call_cleanup(open(F, write, S, [encoding(utf8), newline(posix)]), write(S, Texto), close(S)),
	format(user_error, 'Escrita referencia_agn/~w.json~n', [V]).

% Compara sem contar com os fins de linha (o git pode converter LF em CRLF no Windows).
desatualizada(Raiz, Base, Cenarios, Seqs, V) :-
	texto(Base, Cenarios, Seqs, V, Texto),
	ficheiro(Raiz, V, F),
	(   exists_file(F)
	->  read_file_to_string(F, Atual0, [encoding(utf8)]),
	    split_string(Atual0, "\r", "", Partes), atomic_list_concat(Partes, '', A), atom_string(A, Atual),
	    Atual \== Texto
	;   true
	).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% BASE FILTRADA

% Copia o soc_factos.txt e o soc_suporte.txt para uma pasta temporaria, com o soc_regras.txt so com as regras ativas.
% O metaconhecimento (facto_dispara_regras/2) fica como esta: uma regra que la apareca e nao exista nao dispara.
base_filtrada(Raiz, Base) :-
	tmp_file(kb_agn, Base),
	make_directory(Base),
	directory_file_path(Raiz, base_conhecimento, Origem),
	forall(member(F, ['soc_factos.txt', 'soc_suporte.txt']),
	       ( directory_file_path(Origem, F, De), directory_file_path(Base, F, Para), copy_file(De, Para) )),
	directory_file_path(Origem, 'soc_regras.txt', RegrasDe),
	read_file_to_string(RegrasDe, Texto, [encoding(utf8)]),
	split_string(Texto, "\n", "\r", Linhas),
	filtra(Linhas, fora, Ficam, 0, NRegras),
	regras_ativas(Esperadas),
	(   NRegras =:= Esperadas
	->  true
	;   format(atom(M), 'o filtro do soc_regras.txt deu ~w regras, e deviam ser ~w', [NRegras, Esperadas]),
	    throw(erro_referencias(M))
	),
	atomic_list_concat(Ficam, '\n', Filtrado),
	directory_file_path(Base, 'soc_regras.txt', RegrasPara),
	setup_call_cleanup(open(RegrasPara, write, S, [encoding(utf8)]), write(S, Filtrado), close(S)).

% Uma regra vai da linha "regra N" ate a linha que acaba em "]." (o fim do entao). O resto (comentarios) fica.
filtra([], _, [], N, N).
filtra([L|Ls], Estado, Ficam, N0, N) :-
	(   Estado = fora, numero_regra(L, Id)
	->  ( regra_ativa(Id) -> Fica = sim, N1 is N0 + 1 ; Fica = nao, N1 = N0 ),
	    ( fim_regra(L) -> E1 = fora ; E1 = dentro(Fica) )
	;   Estado = dentro(Fica)
	->  N1 = N0,
	    ( fim_regra(L) -> E1 = fora ; E1 = Estado )
	;   Fica = sim, N1 = N0, E1 = fora
	),
	( Fica == sim -> Ficam = [L|Fs] ; Ficam = Fs ),
	filtra(Ls, E1, Fs, N1, N).

numero_regra(L, Id) :-
	split_string(L, " \t", " \t", ["regra", S|_]),
	number_string(Id, S).

fim_regra(L) :-
	split_string(L, "", " \t", [T]),
	string_concat(_, "].", T).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% VARIANTES (as mesmas do motores/prolog/ferramentas/gera_referencias.pl; o ParidadeTest verifica que os cenarios de
% cada variante sao os mesmos nas duas referencias)

variantes([base, base_e_normais_30_a_34, base_e_atualizacoes, r7b_sim_19b_sim, r7b_nao, r7b_nao_sei_19b_nao,
	   respostas_3b_10d_17b, c29c_em_vez_de_29b, c35d_em_vez_de_35c, c36c_em_vez_de_36b]).

variante(base, S, Ids) :- Ids = S.base.
variante(base_e_normais_30_a_34, S, Ids) :- append(S.base, S.normal, Ids).
variante(base_e_atualizacoes, S, Ids) :- append(S.base, S.atualizacoes, Ids).
variante(r7b_sim_19b_sim, S, Ids) :- append(S.base, [c7b_sim, c19b_sim], Ids).
variante(r7b_nao, S, Ids) :- append(S.base, [c7b_nao], Ids).
variante(r7b_nao_sei_19b_nao, S, Ids) :- append(S.base, [c7b_nao_sei, c19b_nao], Ids).
variante(respostas_3b_10d_17b, S, Ids) :- append(S.base, [c3b, c10d, c17b], Ids).
variante(c29c_em_vez_de_29b, S, Ids) :- troca(c29b, c29c, S.base, Ids).
variante(c35d_em_vez_de_35c, S, Ids) :- troca(c35c, c35d, S.base, Ids).
variante(c36c_em_vez_de_36b, S, Ids) :- troca(c36b, c36c, S.base, Ids).

troca(De, Para, L0, L) :-
	(   append(A, [De|R], L0) -> append(A, [Para|R], L)
	;   format(atom(M), 'a sequencia base nao tem o cenario ~w', [De]), throw(erro_referencias(M))
	).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% GERACAO

cenarios(Raiz, Cenarios, Seqs) :-
	directory_file_path(Raiz, 'backend/cyber/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C, [value_string_as(atom)]), close(S)),
	Cenarios = C,
	Seqs = C.sequencias.

texto(Base, C, Seqs, V, Texto) :-
	variante(V, Seqs, Ids),
	maplist(passo(C), Ids, Passos),
	with_output_to(string(Pedido), json_write_dict(current_output, _{versaoKB: C.versaoKB, passos: Passos}, [width(0)])),
	corre_wrapper(Base, Pedido, Codigo, Out),
	(   Codigo == 0 -> true
	;   format(atom(M), 'o wrapper falhou na variante ~w (codigo ~w): ~w', [V, Codigo, Out]), throw(erro_referencias(M))
	),
	open_string(Out, SO), json_read(SO, Resposta, [value_string_as(atom)]), close(SO),
	with_output_to(string(T0),
		       json_write(current_output, json([variante=V, cenarios=Ids, resposta=Resposta]), [width(300), tab(1000)])),
	string_concat(T0, "\n", Texto).

passo(C, Id, Passo) :-
	(   member(X, C.cenarios), X.id == Id -> Passo = X.passo
	;   format(atom(M), 'o cenarios.json nao tem o cenario ~w', [Id]), throw(erro_referencias(M))
	).

% Corre o wrapper num processo, com a base filtrada: pedido no stdin, resposta no stdout.
% O stderr (mensagens do motor) le-se num thread em paralelo, para o processo nunca ficar parado a escrever.
corre_wrapper(Base, Pedido, Codigo, Out) :-
	raiz(Raiz),
	current_prolog_flag(executable, Swipl),
	directory_file_path(Raiz, 'motores/prolog/wrapper.pl', Wrapper),
	process_create(Swipl, [Wrapper, Base], [stdin(pipe(In)), stdout(pipe(O)), stderr(pipe(E)), process(Pid)]),
	set_stream(In, encoding(utf8)), set_stream(O, encoding(utf8)), set_stream(E, encoding(utf8)),
	thread_create(( read_string(E, _, _) ), Leitor, []),
	write(In, Pedido), close(In),
	read_string(O, _, Out), close(O),
	thread_join(Leitor, _), close(E),
	process_wait(Pid, exit(Codigo)).

:- multifile prolog:message//1.
prolog:message(erro_referencias(M)) --> [ 'gera_referencia_agn.pl: ~w'-[M] ].
