% Gera a referencia Prolog do "porque nao?" para o teste do Drools (PorqueNaoTest): o whynot/1 do soc_motor.pl, depois
% de carregar a base e injetar as sequencias base e normal (o injeta_todos do menu), para cada facto de
% motores/drools/src/test/resources/porque_nao/factos.txt (um por linha, em sintaxe Prolog, pode ter variaveis).
% Escreve motores/drools/src/test/resources/porque_nao/prolog.txt: "#### <facto>" e o que o whynot/1 escreve.
% Uso, a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl motores/drools/ferramentas/gera_porque_nao.pl                escreve o ficheiro
%   swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar    nao escreve nada; codigo 1 se estiver desatualizado

:- use_module(library(readutil)).
:- use_module(library(lists)).

:- initialization(main, main).

:- dynamic pasta_ferramentas/1.
:- prolog_load_context(directory, D), asserta(pasta_ferramentas(D)).

main :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	catch(corre(Argv), E, ( print_message(error, E), halt(1) )).

corre(Argv) :-
	pasta_ferramentas(D),
	directory_file_path(D, '../../..', R0),
	absolute_file_name(R0, Raiz, [file_type(directory)]),
	directory_file_path(Raiz, 'motores/drools/src/test/resources/porque_nao', Pasta),
	directory_file_path(Pasta, 'factos.txt', FFactos),
	directory_file_path(Pasta, 'prolog.txt', FSaida),
	directory_file_path(Raiz, 'motores/prolog/soc_motor.pl', Motor),
	directory_file_path(Raiz, 'base_conhecimento/soc_factos.txt', Base),
	consult(Motor),
	with_output_to(string(_), ( carrega_bc(Base), arranca_motor,
				    sequencia_base(S), sequencia_normal(N), append(S, N, Todos),
				    injeta_cenarios(Todos) )),
	read_file_to_string(FFactos, Str, [encoding(utf8)]),
	split_string(Str, "\n", "\r ", Linhas),
	with_output_to(string(Texto),
		       forall(( member(L, Linhas), L \== "" ),
			      ( term_string(Q, L), format("#### ~w~n", [L]), whynot(Q) ))),
	(   memberchk('--verificar', Argv)
	->  (   exists_file(FSaida),
		read_file_to_string(FSaida, Atual0, [encoding(utf8)]),
		split_string(Atual0, "\r", "", Ps), atomic_list_concat(Ps, '', A), atom_string(A, Texto)
	    ->  format(user_error, 'A referencia do porque nao esta atualizada.~n', [])
	    ;   format(user_error, 'Desatualizada: porque_nao/prolog.txt. Corra: swipl motores/drools/ferramentas/gera_porque_nao.pl~n', []),
		halt(1)
	    )
	;   setup_call_cleanup(open(FSaida, write, O, [encoding(utf8), newline(posix)]), write(O, Texto), close(O)),
	    format(user_error, 'Escrito porque_nao/prolog.txt~n', [])
	).
