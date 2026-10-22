% Gera os resultados de referencia do motor Prolog (motores/prolog/referencia_prolog/<variante>.json) a correr o
% wrapper.pl sobre a sequencia de cenarios de cada variante. Cada ficheiro tem a variante, os cenarios pela ordem e a
% resposta do wrapper (o formato do contrato JSON), para o Drools e o comparador (#16) compararem respostas JSON.
% Uso, a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl motores/prolog/ferramentas/gera_referencias.pl                escreve os ficheiros
%   swipl motores/prolog/ferramentas/gera_referencias.pl --verificar    nao escreve nada; sai com codigo 1 se algum estiver
%                                                                       desatualizado (o motor, a base ou os cenarios mudaram)
% Os passos de cada cenario vem do cenarios.json gerado pelo gera_json.pl: corre-se esse primeiro.
%
% Variantes (as sequencias vem do soc_cenarios.pl, pelo cenarios.json; as respostas do analista sao passos com o
% facto resposta_analista, porque nao sao cenarios):
%   base                       sequencia_base (telemetria 1 a 29a e 35 a 39), sem respostas
%   base_e_normais_30_a_34     base + sequencia_normal (o mesmo que o injeta_todos do menu; os 30 a 34 nao concluem nada)
%   base_e_atualizacoes        base + sequencia_atualizacoes (06a, 13a, 21a, 27a)
%   r7_sim_19_sim              base + "sim" as perguntas de evidencia dos cenarios 7 e 19
%   r7_nao                     base + "nao" a pergunta do cenario 7
%   r7_nao_sei_19_nao          base + "nao sei" ao cenario 7 e "nao" ao 19
%   respostas_3_10b_17         base + respostas as perguntas de confirmacao dos cenarios 3 (sim), 10b (nao) e 17 (nao sei)
%   r29_em_vez_de_29a          base com a resposta "sim" a exposicao do cenario 29 no lugar do 29a
%   c35c_em_vez_de_35b         base com o 35c no lugar do 35b (e o mesmo para o 36b no lugar do 36a)
%   fecho_sim, fecho_nao       base sem o 25a e o 36a + respostas as perguntas de fecho dos tickets (grupo O)
%   ataque_intermedio_tardio   base + um alerta atrasado entre o ataque e a shell do cenario 8 (R69)
%   c2_antes_do_porto          base com um C2 de web_srv_08 entre o 37 e o 37a (R71)

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

variantes([base, base_e_normais_30_a_34, base_e_atualizacoes, r7_sim_19_sim, r7_nao, r7_nao_sei_19_nao,
	   respostas_3_10b_17, r29_em_vez_de_29a, c35c_em_vez_de_35b, c36b_em_vez_de_36a, fecho_sim, fecho_nao,
	   ataque_intermedio_tardio, c2_antes_do_porto]).

% variante(Nome, Sequencias, Itens): Sequencias e o dict "sequencias" do cenarios.json. Cada item e o id de um cenario
% ou uma resposta do analista, resposta(IdDoPasso, Ativo, Evento, Pergunta, Valor): as respostas nao sao cenarios
% (o motor faz a pergunta), e entram como um passo com o facto resposta_analista.
variante(base, S, Ids) :- Ids = S.base.
variante(base_e_normais_30_a_34, S, Ids) :- append(S.base, S.normal, Ids).
variante(base_e_atualizacoes, S, Ids) :- append(S.base, S.atualizacoes, Ids).
variante(r7_sim_19_sim, S, Ids) :-
	append(S.base, [resposta(resposta_07, web_srv_04, ev_e1, shell_servico, sim),
			resposta(resposta_19, app_srv_03, ev_w1, resposta_volumosa_servico, sim)], Ids).
variante(r7_nao, S, Ids) :-
	append(S.base, [resposta(resposta_07, web_srv_04, ev_e1, shell_servico, nao)], Ids).
variante(r7_nao_sei_19_nao, S, Ids) :-
	append(S.base, [resposta(resposta_07, web_srv_04, ev_e1, shell_servico, nao_sei),
			resposta(resposta_19, app_srv_03, ev_w1, resposta_volumosa_servico, nao)], Ids).
variante(respostas_3_10b_17, S, Ids) :-
	append(S.base, [resposta(resposta_03, db_srv_01, ev_i1, compromisso_no_host, sim),
			resposta(resposta_10b, web_srv_02, ev_j3, compromisso_no_host, nao),
			resposta(resposta_17, web_srv_05, ev_s1, compromisso_no_host, nao_sei)], Ids).
variante(r29_em_vez_de_29a, S, Ids) :-
	troca(c29a, resposta(resposta_29, app_srv_01, ev_ab1, exposicao_no_host, sim), S.base, Ids).
variante(c35c_em_vez_de_35b, S, Ids) :- troca(c35b, c35c, S.base, Ids).
variante(c36b_em_vez_de_36a, S, Ids) :- troca(c36a, c36b, S.base, Ids).
% As perguntas de fecho (grupo O). Sem o 25a e o 36a, para a configuracao e a politica ficarem por confirmar.
variante(fecho_sim, S, Ids) :-
	subtract(S.base, [c25a, c36a], B),
	append(B, [resposta(resposta_38a, web_srv_01, ev_af1, maquina_recuperada, sim),
		   resposta(resposta_13, web_srv_07, ev_m1, inventario_rede_completo, sim),
		   resposta(resposta_13, web_srv_07, ev_m1, sbom_completo, sim),
		   resposta(resposta_36, db_srv_01, ev_ad1, acesso_autorizado, sim),
		   resposta(resposta_25, web_srv_10, ev_n1, configuracao_ativa, sim),
		   resposta(resposta_06, web_srv_05, ev_f1, versao_afetada, sim),
		   resposta(resposta_21, app_srv_01, ev_x1, catalogo_afetado, sim),
		   resposta(resposta_07, web_srv_04, ev_e1, vulnerabilidade_tratada, sim),
		   resposta(resposta_03, db_srv_01, ev_i1, compromisso_no_host, sim),
		   resposta(resposta_03_fecho, db_srv_01, ev_i1, maquinas_recuperadas, sim)], Ids).
variante(fecho_nao, S, Ids) :-
	subtract(S.base, [c25a, c36a], B),
	append(B, [resposta(resposta_38a, web_srv_01, ev_af1, maquina_recuperada, nao_sei),
		   resposta(resposta_36, db_srv_01, ev_ad1, acesso_autorizado, nao),
		   resposta(resposta_25, web_srv_10, ev_n1, configuracao_ativa, nao),
		   resposta(resposta_06, web_srv_05, ev_f1, versao_afetada, nao),
		   resposta(resposta_21, app_srv_01, ev_x1, catalogo_afetado, nao),
		   resposta(resposta_07, web_srv_04, ev_e1, vulnerabilidade_tratada, nao_sei)], Ids).

% Telemetria que nao esta em nenhum cenario, para as regras que a sequencia base nao faz disparar (factos(Id, Factos)).
% R69: um alerta em app_srv_01 com a hora entre o ataque do cenario 8 (4000 s) e a shell (4600 s), que so chega no fim:
% o motivo de evidencia tardia do compromisso provavel passa a historico.
variante(ataque_intermedio_tardio, S, Ids) :-
	append(S.base, [factos(ataque_intermedio, [alerta_ids(ev_int1, app_srv_01, sig_sqli_union), origem(ev_int1, ip_203_0_113_88),
						    origem_zona(ev_int1, internet), tempo(ev_int1, 4300)])], Ids).
% R71: um C2 de web_srv_08 depois do ataque do cenario 37 (17600 s) e antes de chegar o porto de destino (37a): da um
% compromisso provavel pela exploracao possivel desse ataque, e quando o 37a a faz passar a historica, o motivo tambem.
variante(c2_antes_do_porto, S, Ids) :-
	depois_de(c37, factos(c2_antes_do_porto, [trafego_saida(ev_c2x, web_srv_08, c2_203_0_113_9), tempo(ev_c2x, 17700)]), S.base, Ids).

depois_de(Id, Item, L0, L) :-
	(   append(A, [Id|R], L0) -> append(A, [Id, Item|R], L)
	;   format(atom(M), 'a sequencia base nao tem o cenario ~w', [Id]), throw(erro_referencias(M))
	).

facto_dict(T, _{predicado: P, args: As}) :- T =.. [P|As].

troca(De, Para, L0, L) :-
	(   append(A, [De|R], L0) -> append(A, [Para|R], L)
	;   format(atom(M), 'a sequencia base nao tem o cenario ~w', [De]), throw(erro_referencias(M))
	).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% GERACAO

cenarios(Raiz, Cenarios, Seqs) :-
	directory_file_path(Raiz, 'backend/socrates/src/main/resources/generated/cenarios.json', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), json_read_dict(S, C, [value_string_as(atom)]), close(S)),
	Cenarios = C,
	Seqs = C.sequencias.

% O conteudo de um ficheiro de referencia: a variante, os cenarios e a resposta do wrapper.
texto(Raiz, C, Seqs, V, Texto) :-
	variante(V, Seqs, Ids),
	maplist(passo(C), Ids, Passos),
	maplist(nome_item, Ids, Nomes),
	with_output_to(string(Pedido), json_write_dict(current_output, _{versaoKB: C.versaoKB, passos: Passos}, [width(0)])),
	corre_wrapper(Raiz, Pedido, Codigo, Out),
	(   Codigo == 0 -> true
	;   format(atom(M), 'o wrapper falhou na variante ~w (codigo ~w): ~w', [V, Codigo, Out]), throw(erro_referencias(M))
	),
	open_string(Out, SO), json_read(SO, Resposta, [value_string_as(atom)]), close(SO),
	with_output_to(string(T0),
		       json_write(current_output, json([variante=V, cenarios=Nomes, resposta=Resposta]), [width(300), tab(1000)])),
	string_concat(T0, "\n", Texto).

% O nome de um item na lista "cenarios" do ficheiro: o id do cenario, ou o id do passo da resposta.
nome_item(resposta(Id, _, _, _, _), Id) :- !.
nome_item(factos(Id, _), Id) :- !.
nome_item(Id, Id).

passo(_, resposta(Id, Ativo, Ev, Pergunta, Valor), _{id: Id, factos: [_{predicado: resposta_analista, args: [Ativo, Ev, Pergunta, Valor]}]}) :- !.
passo(_, factos(Id, Fs), _{id: Id, factos: Ds}) :- !, maplist(facto_dict, Fs, Ds).
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
