% Gera os resultados de referencia do motor Prolog (motores/prolog/referencia_prolog/<variante>.json) a correr o
% wrapper.pl sobre a sequencia de cenarios de cada variante. Cada ficheiro tem a variante, os cenarios pela ordem e a
% resposta do wrapper (o formato do contrato JSON), para o Drools e o comparador (#16) compararem respostas JSON.
% Uso, a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl motores/prolog/ferramentas/gera_referencias.pl                escreve os ficheiros
%   swipl motores/prolog/ferramentas/gera_referencias.pl --verificar    nao escreve nada; sai com codigo 1 se algum estiver
%                                                                       desatualizado (o motor, a base ou os cenarios mudaram)
% Os passos de cada cenario vem do cenarios.json gerado pelo gera_json.pl: corre-se esse primeiro.
%
% Variantes (as sequencias vem do soc_cenarios.pl, pelo cenarios.json):
%   base                       sequencia_base (telemetria 1 a 29b e 35 a 39), sem respostas
%   base_e_normais_30_a_34     base + sequencia_normal (o mesmo que o injeta_todos do menu; os 30 a 34 nao concluem nada)
%   base_e_atualizacoes        base + sequencia_atualizacoes (06b, 13b, 21b, 27b)
%   r7b_sim_19b_sim            base + respostas "sim" as perguntas dos cenarios 7 e 19
%   r7b_nao                    base + resposta "nao" a pergunta do cenario 7
%   r7b_nao_sei_19b_nao        base + "nao sei" ao cenario 7 e "nao" ao 19
%   respostas_3b_10d_17b       base + respostas 3b, 10d e 17b
%   c29c_em_vez_de_29b         base com o 29c no lugar do 29b (e o mesmo para o 35d e o 36c)

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

:- initialization(main, main).

:- dynamic pasta_ferramentas/1.
:- prolog_load_context(directory, D), asserta(pasta_ferramentas(D)).

main :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	catch(corre(Argv), E, ( print_message(error, E), halt(1) )).

corre(Argv) :-
	raiz(Raiz),
	cenarios(Raiz, Cenarios, Seqs),
	variantes(Vs),
	(   memberchk('--verificar', Argv)
	->  include(desatualizada(Raiz, Cenarios, Seqs), Vs, Maus),
	    (   Maus == []
	    ->  format(user_error, 'As referencias estao atualizadas.~n', [])
	    ;   forall(member(V, Maus), format(user_error, 'Desatualizada: referencia_prolog/~w.json~n', [V])),
		format(user_error, 'Corra: swipl motores/prolog/ferramentas/gera_referencias.pl~n', []),
		halt(1)
	    )
	;   forall(member(V, Vs), escreve(Raiz, Cenarios, Seqs, V))
	).

raiz(Raiz) :-
	pasta_ferramentas(D),
	directory_file_path(D, '../../..', R0),
	absolute_file_name(R0, Raiz, [file_type(directory)]).

ficheiro(Raiz, V, F) :-
	format(atom(Rel), 'motores/prolog/referencia_prolog/~w.json', [V]),
	directory_file_path(Raiz, Rel, F).

escreve(Raiz, Cenarios, Seqs, V) :-
	texto(Raiz, Cenarios, Seqs, V, Texto),
	ficheiro(Raiz, V, F),
	setup_call_cleanup(open(F, write, S, [encoding(utf8), newline(posix)]), write(S, Texto), close(S)),
	format(user_error, 'Escrita referencia_prolog/~w.json~n', [V]).

% Compara sem contar com os fins de linha (o git pode converter LF em CRLF no Windows).
desatualizada(Raiz, Cenarios, Seqs, V) :-
	texto(Raiz, Cenarios, Seqs, V, Texto),
	ficheiro(Raiz, V, F),
	(   exists_file(F)
	->  read_file_to_string(F, Atual0, [encoding(utf8)]),
	    split_string(Atual0, "\r", "", Partes), atomic_list_concat(Partes, '', A), atom_string(A, Atual),
	    Atual \== Texto
	;   true
	).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% VARIANTES

variantes([base, base_e_normais_30_a_34, base_e_atualizacoes, r7b_sim_19b_sim, r7b_nao, r7b_nao_sei_19b_nao,
	   respostas_3b_10d_17b, c29c_em_vez_de_29b, c35d_em_vez_de_35c, c36c_em_vez_de_36b]).

% variante(Nome, Sequencias, Ids): Sequencias e o dict "sequencias" do cenarios.json.
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

% O conteudo de um ficheiro de referencia: a variante, os cenarios e a resposta do wrapper.
texto(Raiz, C, Seqs, V, Texto) :-
	variante(V, Seqs, Ids),
	maplist(passo(C), Ids, Passos),
	with_output_to(string(Pedido), json_write_dict(current_output, _{versaoKB: C.versaoKB, passos: Passos}, [width(0)])),
	corre_wrapper(Raiz, Pedido, Codigo, Out),
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

% Corre o wrapper num processo (como o PrologAdapter): pedido no stdin, resposta no stdout.
% O stderr (mensagens do motor) le-se num thread em paralelo, para o processo nunca ficar parado a escrever.
corre_wrapper(Raiz, Pedido, Codigo, Out) :-
	current_prolog_flag(executable, Swipl),
	directory_file_path(Raiz, 'motores/prolog/wrapper.pl', Wrapper),
	directory_file_path(Raiz, base_conhecimento, Base),
	process_create(Swipl, [Wrapper, Base], [stdin(pipe(In)), stdout(pipe(O)), stderr(pipe(E)), process(Pid)]),
	set_stream(In, encoding(utf8)), set_stream(O, encoding(utf8)), set_stream(E, encoding(utf8)),
	thread_create(( read_string(E, _, _) ), Leitor, []),
	write(In, Pedido), close(In),
	read_string(O, _, Out), close(O),
	thread_join(Leitor, _), close(E),
	process_wait(Pid, exit(Codigo)).

:- multifile prolog:message//1.
prolog:message(erro_referencias(M)) --> [ 'gera_referencias.pl: ~w'-[M] ].
