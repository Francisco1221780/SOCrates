% Testes automaticos do lado Prolog: o wrapper.pl (issue #9), os JSON gerados pelo gera_json.pl (issue #10), as
% referencias do motor geradas pelo gera_referencias.pl e o metaconhecimento gerado pelo gera_meta.pl.
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
	corre_wrapper_args(Texto, [Pasta], Codigo, Out, Err).

% O mesmo com os argumentos do wrapper dados por inteiro (a pasta e o modo).
corre_wrapper_args(Texto, Args, Codigo, Out, Err) :-
	current_prolog_flag(executable, Swipl),
	caminho('motores/prolog/wrapper.pl', Wrapper),
	pasta_testes(Cwd),
	process_create(Swipl, [Wrapper|Args],
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

% Pedido com os passos dos itens Ids, tirados do cenarios.json. Um item e o id de um cenario ou uma resposta do
% analista, resposta(IdDoPasso, Ativo, Evento, Pergunta, Valor): as respostas nao sao cenarios (o motor faz a pergunta).
pedido(Ids, Texto) :-
	caminho('backend/socrates/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C), close(S)),
	maplist(passo(C), Ids, Passos),
	with_output_to(string(Texto), json_write_dict(current_output, _{versaoKB: C.versaoKB, passos: Passos})).

passo(_, resposta(Id, A, E, P, V), _{id: Id, factos: [_{predicado: resposta_analista, args: [A, E, P, V]}]}) :- !.
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

% Factos do motor depois de carregar a base e injetar os itens Ids, pela ordem: os ficheiros de cenario, e as
% respostas do analista com introduz_facto/1 (como a interface as envia).
factos_motor(Ids, Factos) :-
	base(Pasta), directory_file_path(Pasta, 'soc_factos.txt', F),
	with_output_to(string(_), ( carrega_bc(F), arranca_motor, forall(member(I, Ids), injeta_item(I)) )),
	ultimo_facto(U),
	findall(T, ( between(1, U, N), facto(N, T) ), Factos).

injeta_item(resposta(_, A, E, P, V)) :- !, introduz_facto(resposta_analista(A, E, P, V)), arranca_motor.
injeta_item(Id) :- injeta_cenario(Id).

% A versao da base, tal como esta nos JSON gerados (que o gera_json.pl tira do cabecalho do soc_factos.txt).
versao_base_json(V) :-
	caminho('backend/socrates/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C), close(S)),
	atom_string(V, C.versaoKB).

% Respostas a pergunta do cenario 7 (sensor em baixo em web_srv_04).
resposta_7(V, resposta(resposta_07, web_srv_04, ev_e1, shell_servico, V)).

% O texto tem exatamente um documento JSON (e espacos a volta).
um_so_json(Texto) :-
	open_string(Texto, S),
	json_read_dict(S, _),
	read_string(S, _, Resto),
	normalize_space(atom(''), Resto).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ISSUE #9: WRAPPER

:- begin_tests(wrapper).

% AC12: o cenario 7 pergunta ao analista (a evidencia, com o sensor em baixo, e o fecho do ticket da exploracao
% possivel); com a resposta, da o mesmo que injetar o cenario e a resposta no motor.
test(ac12_pergunta) :-
	resposta([c7], R, Codigo),
	Codigo == 0,
	R.estado == aguarda_resposta,
	findall(Pg-N, ( member(P, R.perguntas), Pg = P.pergunta, length(P.opcoes, N) ), Ps),
	msort(Ps, [shell_servico-3, vulnerabilidade_tratada-2]).

test(ac12_resposta, [forall(member(V, [sim, nao, nao_sei]))]) :-
	resposta_7(V, Resp),
	resposta([c7, Resp], R, Codigo),
	Codigo == 0,
	R.estado == aguarda_resposta,
	factos_resposta(R, FW),
	factos_motor([c7, Resp], FM),
	FW == FM.

% "Nao sei" nao fecha a pergunta: continua pendente para se responder mais tarde; "sim" ou "nao" fecham-na.
test(nao_sei_continua_pendente, [forall(member(V-Pendente, [nao_sei-true, sim-false, nao-false]))]) :-
	resposta_7(V, Resp),
	resposta([c7, Resp], R, 0),
	(   ( member(P, R.perguntas), P.pergunta == shell_servico ) -> E = true ; E = false ),
	E == Pendente.

% AC11: um prefixo dos passos da os mesmos factos que esse ponto de um pedido mais longo.
test(ac11_prefixo) :-
	resposta([c7], R1, 0),
	resposta_7(sim, Resp),
	resposta([c7, Resp], R2, 0),
	length(R1.factos, N),
	length(Prefixo, N), append(Prefixo, _, R2.factos),
	Prefixo == R1.factos.

% AC10: o mesmo pedido da a mesma resposta (byte a byte).
test(ac10_determinismo) :-
	resposta_7(sim, Resp),
	pedido([c7, Resp], Texto), base(Pasta),
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
	versao_base_json(Vs),
	format(string(Texto), "{\"versaoKB\":\"~w\",\"passos\":[{\"id\":\"p1\",\"factos\":[{\"predicado\":\"nota\",\"args\":[\"ação\",{\"f\":\"é\",\"args\":[\"Ç\"]}]}]}]}", [Vs]),
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

% Os pedidos levam a versao da base (do cenarios.json), para cada um falhar so pela razao indicada.
erro_pedido("{\"versaoKB\":\"5.1\",\"passos\":[{\"factos\":[]}]}", P) :- base(P).   % versao errada
erro_pedido(T, P) :- base(P), com_versao("{\"versaoKB\":\"~w\",\"passos\":[]}", T).                 % sem passos
erro_pedido(T, P) :- base(P), com_versao("{\"versaoKB\":\"~w\",\"passos\":[", T).                   % JSON partido
erro_pedido("", P) :- base(P).                                                     % stdin vazio
erro_pedido(T, P) :- base(P), com_versao("{\"versaoKB\":\"~w\",\"passos\":[{\"factos\":[{\"args\":[]}]}]}", T).  % facto sem predicado
erro_pedido(T, 'pasta_que_nao_existe') :- com_versao("{\"versaoKB\":\"~w\",\"passos\":[{\"factos\":[]}]}", T).     % pasta errada

com_versao(Modelo, Texto) :- versao_base_json(V), format(string(Texto), Modelo, [V]).

% A versao da resposta e a do cabecalho do soc_factos.txt (a mesma dos JSON gerados), sem estar escrita no wrapper.
test(versao_do_cabecalho) :-
	resposta([c7], R, 0),
	versao_base_json(V),
	R.versaoKB == V,
	base(Pasta), directory_file_path(Pasta, 'soc_factos.txt', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), read_line_to_string(S, L), close(S)),
	atom_string(V, Vs), string_concat(_, Vs, L).

% Sem base carregada (pasta errada) nao ha versao: o erro leva versaoKB null.
test(versao_sem_base) :-
	com_versao("{\"versaoKB\":\"~w\",\"passos\":[{\"factos\":[]}]}", Texto),
	corre_wrapper(Texto, 'pasta_que_nao_existe', Codigo, Out, _),
	Codigo \== 0,
	atom_json_dict(Out, R, [value_string_as(atom)]),
	R.versaoKB == null.

:- end_tests(wrapper).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ISSUE #7: "PORQUE NAO?" ESTRUTURADO (whynot_linhas/3 do motor e o modo whynot do wrapper)
% A sessao e a do PorqueNaoTest do Drools: a base com as sequencias base e normal.

% Pedido do modo whynot: os passos das sequencias base e normal, o facto (dict JSON) e os Extra (ex.: profundidade).
pedido_whynot(Facto, Extra, Texto) :-
	caminho('backend/socrates/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C), close(S)),
	append(C.sequencias.base, C.sequencias.normal, Ids),
	maplist(passo(C), Ids, Passos),
	dict_pairs(P, _, [versaoKB-C.versaoKB, passos-Passos, facto-Facto|Extra]),
	with_output_to(string(Texto), json_write_dict(current_output, P)).

resposta_whynot(Facto, Extra, R, Codigo) :-
	pedido_whynot(Facto, Extra, Texto),
	base(Pasta),
	corre_wrapper_args(Texto, [Pasta, whynot], Codigo, Out, _),
	atom_json_dict(Out, R, [value_string_as(atom)]).

% Os factos do PorqueNaoTest (porque_nao/factos.txt), em sintaxe Prolog.
facto_porque_nao(T) :-
	caminho('motores/drools/src/test/resources/porque_nao/factos.txt', F),
	read_file_to_string(F, Str, [encoding(utf8)]),
	split_string(Str, "\n", "\r ", Ls),
	member(L, Ls), L \== "",
	term_string(T, L).

% Termo Prolog -> dict JSON do pedido, com as variaveis como {"var": "V1"}, {"var": "V2"}, ...
facto_dict(T, _{predicado: P, args: Js}) :-
	copy_term(T, C),
	term_variables(C, Vs),
	foldl([V, I0, I]>>( format(atom(N), 'V~w', [I0]), V = '$v'(N), I is I0 + 1 ), Vs, 1, _),
	C =.. [P|As],
	maplist(termo_dict, As, Js).

termo_dict('$v'(N), _{var: N}) :- !.
termo_dict(L, J) :- is_list(L), !, maplist(termo_dict, L, J).
termo_dict(T, _{f: N, args: J}) :- compound(T), !, compound_name_arguments(T, N, As), maplist(termo_dict, As, J).
termo_dict(X, X).

% As linhas do motor (nesta sessao) como Nivel-Tipo-Regra, para comparar com as do wrapper.
linhas_motor(T, Max, L) :-
	base(Pasta), directory_file_path(Pasta, 'soc_factos.txt', F),
	with_output_to(string(_), ( carrega_bc(F), arranca_motor,
				    sequencia_base(S), sequencia_normal(N), append(S, N, Ids), injeta_cenarios(Ids) )),
	whynot_linhas(T, Max, Linhas),
	findall(Nv-Tp-Rg, ( member(linha(Nv, Tp, Cs), Linhas), ( memberchk(regra-Rg, Cs) -> true ; Rg = null ) ), L).

linhas_resposta(R, L) :-
	findall(Nv-Tp-Rg, ( member(X, R.explicacao), Nv = X.nivel, Tp = X.tipo,
			    ( get_dict(regra, X, Rg) -> true ; Rg = null ) ), L).

:- begin_tests(porque_nao).

% O whynot/1 escreve o mesmo que antes de passar a montar as linhas: a referencia do PorqueNaoTest esta em dia.
test(texto_igual_a_referencia) :-
	current_prolog_flag(executable, Swipl),
	caminho('motores/drools/ferramentas/gera_porque_nao.pl', G),
	process_create(Swipl, [G, '--verificar'], [stdout(null), stderr(pipe(E)), process(Pid)]),
	read_string(E, _, Msg), close(E),
	process_wait(Pid, exit(Codigo)),
	(   Codigo == 0 -> true
	;   format(user_error, "~s", [Msg]), fail
	).

% O wrapper da as linhas do motor (nivel, tipo e regra), para cada facto do PorqueNaoTest.
test(wrapper_igual_ao_motor, [forall(facto_porque_nao(T))]) :-
	facto_dict(T, D),
	resposta_whynot(D, [], R, Codigo),
	Codigo == 0,
	R.estado == concluido,
	R.motor == prolog,
	linhas_resposta(R, LW),
	linhas_motor(T, 4, LM),
	LW == LM.

% As variaveis do pedido: o facto volta como veio, e cada linha regra traz o se (com verificada) e o entao.
test(variaveis_e_regra_completa) :-
	resposta_whynot(_{predicado: compromisso_confirmado, args: [web_srv_05, _{var: 'Ev'}]}, [], R, 0),
	R.facto.args = [web_srv_05, V], V.var == 'Ev',
	member(L, R.explicacao), L.tipo == regra, !,
	integer(L.regra),
	L.se = [_|_],
	forall(member(C, L.se), ( memberchk(C.tipo, [facto, nao, avalia]), memberchk(C.verificada, [true, false]) )),
	L.entao = [_|_].

% A profundidade do pedido: a por omissao e 4; com 1 para logo no segundo nivel.
test(profundidade) :-
	D = _{predicado: ataque_dirigido, args: [ev_e1, web_srv_04]},
	resposta_whynot(D, [], R0, 0),
	resposta_whynot(D, [profundidade-4], R4, 0),
	R0.explicacao =@= R4.explicacao,
	resposta_whynot(D, [profundidade-1], R1, 0),
	once(( member(L, R1.explicacao), L.tipo == profundidade )),
	length(R1.explicacao, N1), length(R4.explicacao, N4), N1 < N4.

% M9 no modo whynot: sem facto, facto mal formado ou profundidade invalida dao JSON de erro e codigo diferente de 0.
test(m9_erros_whynot, [forall(erro_whynot(Extra))]) :-
	pedido_whynot(_{predicado: ataque_dirigido, args: [ev_e1, web_srv_04]}, [], Texto0),
	atom_json_dict(Texto0, P0, []),
	del_dict(facto, P0, _, P1),
	dict_pairs(P1, T, Pares), append(Pares, Extra, Pares1), dict_pairs(P, T, Pares1),
	with_output_to(string(Texto), json_write_dict(current_output, P)),
	base(Pasta),
	corre_wrapper_args(Texto, [Pasta, whynot], Codigo, Out, _),
	Codigo \== 0,
	um_so_json(Out),
	atom_json_dict(Out, R, [value_string_as(atom)]),
	R.estado == erro,
	atom(R.erro).

erro_whynot([]).                                                          % sem facto
erro_whynot([facto-_{args: [ev_e1]}]).                                    % facto sem predicado
erro_whynot([facto-_{predicado: ataque_dirigido, args: [_{x: 1}]}]).      % termo invalido
erro_whynot([facto-_{predicado: ataque_dirigido, args: []}, profundidade-0]).
erro_whynot([facto-_{predicado: ataque_dirigido, args: []}, profundidade-"4"]).

% Um modo que nao existe tambem e um erro em JSON.
test(modo_desconhecido) :-
	pedido([c7], Texto), base(Pasta),
	corre_wrapper_args(Texto, [Pasta, outro], Codigo, Out, _),
	Codigo \== 0,
	atom_json_dict(Out, R, [value_string_as(atom)]),
	R.estado == erro.

:- end_tests(porque_nao).


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
	caminho('backend/socrates/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C, [value_string_as(atom)]), close(S)),
	findall(Id, ( member(X, C.cenarios), Id = X.id ), Ids),
	forall(( member(X, C.cenarios), ( member(D, X.depende) ; member(D, X.alternativas) ) ), memberchk(D, Ids)),
	B = C.sequencias.base,
	append(B, C.sequencias.atualizacoes, BA),
	append(B, C.sequencias.normal, BN),
	forall(member(Seq, [B, BA, BN, C.sequencias.completa]), sequencia_valida(C, Seq)),
	append([B, C.sequencias.atualizacoes, C.sequencias.normal], C.sequencias.completa).

% As sequencias como se usam: a base, a base com as atualizacoes, a base com a atividade normal, e a completa
% (injeta_todos e a vista global da UI), que tem de ser a base, as atualizacoes e a normal, por esta ordem.
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


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% METACONHECIMENTO (facto_dispara_regras/2 no soc_suporte.txt, gerado pelo gera_meta.pl)

:- begin_tests(gera_meta).

% AC13: o metaconhecimento do soc_suporte.txt e o que as regras dao hoje. O codigo 0 sozinho nao chega (um ficheiro que
% nao seja o gera_meta.pl, sem main, tambem sai com 0): o script tem de confirmar que esta em dia.
test(ac13_meta_em_dia) :-
	current_prolog_flag(executable, Swipl),
	caminho('motores/prolog/ferramentas/gera_meta.pl', G),
	process_create(Swipl, [G, '--verificar'], [stdin(null), stdout(null), stderr(pipe(E)), process(Pid)]),
	read_string(E, _, Msg), close(E),
	process_wait(Pid, exit(Codigo)),
	(   Codigo == 0, sub_string(Msg, _, _, _, "O metaconhecimento esta atualizado.") -> true
	;   format(user_error, "~s", [Msg]), fail
	).

:- end_tests(gera_meta).
