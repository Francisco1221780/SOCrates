% Testes automaticos do lado Prolog: o wrapper.pl (issue #9), os JSON gerados pelo gera_json.pl (issue #10) e as
% referencias do motor geradas pelo gera_referencias.pl.
% Correm-se a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl -g run_tests -t halt motores/prolog/testes/testes.pl
% O codigo de saida e 0 se todos passarem e diferente de zero se algum falhar (e o que a CI usa).
%
% O wrapper corre num processo a parte, como o PrologAdapter o vai lancar: o pedido entra pelo stdin e a resposta sai
% pelo stdout. Os pedidos montam-se com os passos do cenarios.json, a fonte unica dos passos de cada cenario.
% Para comparar com o motor (AC12), o motor corre tambem dentro deste processo, sobre a mesma base.

:- encoding(utf8).

:- use_module(library(plunit)).
:- use_module(library(process)).
:- use_module(library(readutil)).
:- use_module(library(lists)).
:- use_module(library(apply)).
% A biblioteca JSON passou de library(http/json) para library(json) no SWI-Prolog 10; aceitam-se as duas.
:- if(exists_source(library(json))).
:- use_module(library(json)).
:- else.
:- use_module(library(http/json)).
:- endif.

:- dynamic pasta_testes/1.
:- prolog_load_context(directory, D), asserta(pasta_testes(D)).

raiz(Raiz) :-
	pasta_testes(D),
	directory_file_path(D, '../../..', R0),
	absolute_file_name(R0, Raiz, [file_type(directory)]).

caminho(Rel, Abs) :- raiz(R), directory_file_path(R, Rel, Abs).

% O motor, carregado aqui so para o AC12 (comparar com o resultado de injetar os ficheiros de cenario).
:- caminho('motores/prolog/soc_motor.pl', M), consult(M).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% AUXILIARES

% Corre o wrapper com o pedido Texto e a pasta da base Pasta; devolve o codigo de saida, o stdout e o stderr.
% A pasta de trabalho do processo e a pasta dos testes, nao a raiz do repositorio (M5). O stderr le-se num thread em
% paralelo com o stdout (como o PrologAdapter deve fazer), para o processo nunca ficar parado a escrever.
corre_wrapper(Texto, Pasta, Codigo, Out, Err) :-
	current_prolog_flag(executable, Swipl),
	caminho('motores/prolog/wrapper.pl', Wrapper),
	pasta_testes(Cwd),
	process_create(Swipl, [Wrapper, Pasta],
		       [ stdin(pipe(In)), stdout(pipe(O)), stderr(pipe(E)), cwd(Cwd), process(Pid) ]),
	set_stream(In, encoding(utf8)), set_stream(O, encoding(utf8)), set_stream(E, encoding(utf8)),
	thread_self(Eu),
	thread_create(( read_string(E, _, S), thread_send_message(Eu, stderr_wrapper(S)) ), Leitor, []),
	write(In, Texto), close(In),
	read_string(O, _, Out), close(O),
	thread_join(Leitor, _),
	thread_get_message(stderr_wrapper(Err)),
	close(E),
	process_wait(Pid, exit(Codigo)).

base(Pasta) :- caminho(base_conhecimento, Pasta).

% Pedido com os passos dos cenarios Ids, tirados do cenarios.json.
pedido(Ids, Texto) :-
	caminho('backend/cyber/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C), close(S)),
	maplist(passo(C), Ids, Passos),
	with_output_to(string(Texto), json_write_dict(current_output, _{versaoKB: C.versaoKB, passos: Passos})).

passo(C, Id, Passo) :-
	atom_string(Id, IdS),
	member(X, C.cenarios), X.id == IdS, !,
	Passo = X.passo.

% Resposta do wrapper (com os cenarios Ids) lida como dict, com as strings como atomos.
resposta(Ids, R, Codigo) :-
	pedido(Ids, Texto),
	base(Pasta),
	corre_wrapper(Texto, Pasta, Codigo, Out, _),
	atom_json_dict(Out, R, [value_string_as(atom)]).

% Factos da resposta como termos Prolog (tabela da issue #6 ao contrario).
factos_resposta(R, Factos) :-
	get_dict(factos, R, Fs),
	maplist(json_facto, Fs, Factos).

json_facto(F, T) :-
	get_dict(predicado, F, P), get_dict(args, F, Args),
	maplist(json_termo, Args, As),
	T =.. [P|As].

json_termo(D, T) :- is_dict(D), !, maplist(json_termo, D.args, As), compound_name_arguments(T, D.f, As).
json_termo(L, T) :- is_list(L), !, maplist(json_termo, L, T).
json_termo(X, X).

% Factos do motor depois de carregar a base e injetar os ficheiros de cenario Ids, pela ordem.
factos_motor(Ids, Factos) :-
	base(Pasta), directory_file_path(Pasta, 'soc_factos.txt', F),
	with_output_to(string(_), ( carrega_bc(F), arranca_motor, injeta_cenarios(Ids) )),
	ultimo_facto(U),
	findall(T, ( between(1, U, N), facto(N, T) ), Factos).

% O texto tem exatamente um documento JSON (e espacos a volta).
um_so_json(Texto) :-
	open_string(Texto, S),
	json_read_dict(S, _),
	read_string(S, _, Resto),
	normalize_space(atom(''), Resto).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ISSUE #9: WRAPPER

:- begin_tests(wrapper).

% AC12: o cenario 7 pergunta ao analista; com a resposta, da o mesmo que injetar os ficheiros de cenario no motor.
test(ac12_pergunta) :-
	resposta([c7], R, Codigo),
	Codigo == 0,
	R.estado == aguarda_resposta,
	R.perguntas = [P],
	P.pergunta == shell_servico,
	length(P.opcoes, 3).

test(ac12_resposta, [forall(member(Resp, [c7b_sim, c7b_nao, c7b_nao_sei]))]) :-
	resposta([c7, Resp], R, Codigo),
	Codigo == 0,
	R.estado == concluido,
	factos_resposta(R, FW),
	factos_motor([c7, Resp], FM),
	FW == FM.

% AC11: um prefixo dos passos da os mesmos factos que esse ponto de um pedido mais longo.
test(ac11_prefixo) :-
	resposta([c7], R1, 0),
	resposta([c7, c7b_sim], R2, 0),
	length(R1.factos, N),
	length(Prefixo, N), append(Prefixo, _, R2.factos),
	Prefixo == R1.factos.

% AC10: o mesmo pedido da a mesma resposta (byte a byte).
test(ac10_determinismo) :-
	pedido([c7, c7b_sim], Texto), base(Pasta),
	corre_wrapper(Texto, Pasta, 0, Out1, _),
	corre_wrapper(Texto, Pasta, 0, Out2, _),
	Out1 == Out2.

% AC3: o stdout tem exatamente um documento JSON; as mensagens do motor vao para o stderr.
test(ac3_stdout_so_json) :-
	pedido([c7], Texto), base(Pasta),
	corre_wrapper(Texto, Pasta, 0, Out, Err),
	um_so_json(Out),
	once(sub_string(Err, _, _, _, "Base carregada")).

% AC2: um facto com acentos vai e volta igual.
test(ac2_acentos) :-
	Texto = "{\"versaoKB\":\"6\",\"passos\":[{\"id\":\"p1\",\"factos\":[{\"predicado\":\"nota\",\"args\":[\"ação\",{\"f\":\"é\",\"args\":[\"Ç\"]}]}]}]}",
	base(Pasta),
	corre_wrapper(Texto, Pasta, 0, Out, _),
	atom_json_dict(Out, R, [value_string_as(atom)]),
	factos_resposta(R, Factos),
	memberchk(nota('ação', 'é'('Ç')), Factos).

% M9: qualquer erro sai como JSON de erro e o processo termina com codigo diferente de zero.
test(m9_erros, [forall(erro_pedido(Texto, Pasta))]) :-
	corre_wrapper(Texto, Pasta, Codigo, Out, _),
	Codigo \== 0,
	um_so_json(Out),
	atom_json_dict(Out, R, [value_string_as(atom)]),
	R.estado == erro,
	atom(R.erro).

erro_pedido("{\"versaoKB\":\"5.1\",\"passos\":[{\"factos\":[]}]}", P) :- base(P).   % versao errada
erro_pedido("{\"versaoKB\":\"6\",\"passos\":[]}", P) :- base(P).                 % sem passos
erro_pedido("{\"versaoKB\":\"6\",\"passos\":[", P) :- base(P).                   % JSON partido
erro_pedido("", P) :- base(P).                                                     % stdin vazio
erro_pedido("{\"versaoKB\":\"6\",\"passos\":[{\"factos\":[{\"args\":[]}]}]}", P) :- base(P).  % facto sem predicado
erro_pedido("{\"versaoKB\":\"6\",\"passos\":[{\"factos\":[]}]}", 'pasta_que_nao_existe').     % pasta errada

:- end_tests(wrapper).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ISSUE #10: JSON GERADOS

:- begin_tests(gera_json).

% AC18: os JSON gerados estao em dia com os .txt da base.
test(ac18_json_em_dia) :-
	current_prolog_flag(executable, Swipl),
	caminho('motores/prolog/ferramentas/gera_json.pl', G),
	process_create(Swipl, [G, '--verificar'], [stdout(null), stderr(pipe(E)), process(Pid)]),
	read_string(E, _, Msg), close(E),
	process_wait(Pid, exit(Codigo)),
	(   Codigo == 0 -> true
	;   format(user_error, "~s", [Msg]), fail
	).

% cenarios.json: as dependencias e as alternativas citam cenarios que existem, e as sequencias respeitam-nas
% (nenhum cenario aparece antes das suas dependencias nem junto com uma alternativa).
test(cenarios_coerentes) :-
	caminho('backend/cyber/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C, [value_string_as(atom)]), close(S)),
	findall(Id, ( member(X, C.cenarios), Id = X.id ), Ids),
	forall(( member(X, C.cenarios), ( member(D, X.depende) ; member(D, X.alternativas) ) ), memberchk(D, Ids)),
	B = C.sequencias.base,
	append(B, C.sequencias.atualizacoes, BA),
	append(B, C.sequencias.normal, BN),
	forall(member(Seq, [B, BA, BN]), sequencia_valida(C, Seq)).

% As sequencias como se usam: a base, a base com as atualizacoes, a base com a atividade normal (injeta_todos).
sequencia_valida(C, Seq) :-
	forall(nth1(I, Seq, Id),
	       (   member(X, C.cenarios), X.id == Id, !,
		   forall(member(D, X.depende), ( nth1(J, Seq, D), J < I )),
		   forall(member(A, X.alternativas), \+ memberchk(A, Seq))
	       )).

:- end_tests(gera_json).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% REFERENCIAS DO MOTOR (referencia_prolog/*.json, geradas pelo gera_referencias.pl)

:- begin_tests(referencias).

% As referencias estao em dia com o motor, a base e os cenarios (correr o wrapper da o que esta guardado).
test(referencias_em_dia) :-
	current_prolog_flag(executable, Swipl),
	caminho('motores/prolog/ferramentas/gera_referencias.pl', G),
	process_create(Swipl, [G, '--verificar'], [stdout(null), stderr(pipe(E)), process(Pid)]),
	read_string(E, _, Msg), close(E),
	process_wait(Pid, exit(Codigo)),
	(   Codigo == 0 -> true
	;   format(user_error, "~s", [Msg]), fail
	).

:- end_tests(referencias).
