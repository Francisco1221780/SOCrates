% =====================================================================================
% WRAPPER JSON - Analista virtual de SOC (CyberRangers)
% Ponte entre o Java (PrologAdapter) e o motor: le um pedido JSON do stdin, corre os passos no motor e escreve
% uma resposta JSON no stdout. O motor (soc_motor.pl) e a base nao sabem nada de JSON.
%
%   swipl wrapper.pl <pasta da base>  < pedido.json  > resposta.json
%   ex.: swipl motores/prolog/wrapper.pl base_conhecimento < motores/prolog/exemplos/pedido_cenario_07.json
%
% Pedido:   {"versaoKB": "6", "passos": [{"id": "cenario_07", "factos": [{"predicado": "...", "args": [...]}]}, ...]}
% Resposta: {"estado", "motor", "versaoKB", "perguntas", "incidentes", "tickets", "factos", "erro"}
%           estado: "concluido", "aguarda_resposta" (ha perguntas por responder) ou "erro"
%
% Termos Prolog em JSON (issue #6): atomo -> string, inteiro -> numero, lista -> array,
% termo composto -> {"f": nome, "args": [...]}, facto -> {"predicado": nome, "args": [...]}.
%
% Regras (motores/prolog/README.md):
%   - o stdout tem exatamente um documento JSON; mensagens do motor, avisos e diagnostico vao para o stderr;
%   - UTF-8 no stdin, no stdout, no stderr e na leitura da base (M10);
%   - a pasta da base chega por argumento (M5); o wrapper nunca le do teclado nem carrega o menu;
%   - qualquer erro sai como JSON com estado "erro" e codigo de saida 1 (M9); sem erro, codigo 0.
% =====================================================================================

:- encoding(utf8).
% A biblioteca JSON passou de library(http/json) para library(json) no SWI-Prolog 10; aceitam-se as duas.
:- if(exists_source(library(json))).
:- use_module(library(json)).
:- else.
:- use_module(library(http/json)).
:- endif.
:- use_module(library(lists)).
:- use_module(library(apply)).

:- ensure_loaded(soc_motor).

:- initialization(main, main).

:- dynamic passo_intervalo/3.        % passo_intervalo(Id, UltimoAntes, UltimoDepois)

% Versao da base que este wrapper aceita. Muda junto com o cabecalho do soc_factos.txt e MotorDrools.VERSAO_KB.
versao_kb('6').


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ARRANQUE

main :-
	prepara_streams,
	(   catch(responde(Resposta), Erro, true)
	->  (   var(Erro)
	    ->  Codigo = 0
	    ;   resposta_erro(Erro, Resposta), Codigo = 1
	    )
	;   resposta_erro(falhou, Resposta), Codigo = 1
	),
	json_write(user_output, Resposta, [width(0)]),
	nl(user_output),
	flush_output(user_output),
	halt(Codigo).

% UTF-8 em todo o lado. Tudo o que o motor e o consult escrevem vai para o stderr; o JSON escreve-se em user_output.
prepara_streams :-
	set_prolog_flag(encoding, utf8),
	set_stream(user_input, encoding(utf8)),
	set_stream(user_output, encoding(utf8)),
	set_stream(user_error, encoding(utf8)),
	set_output(user_error).

responde(Resposta) :-
	pasta_base_argumento(Pasta),
	carrega_base(Pasta),
	ultimo_facto(UltimoInicial),
	json_read_dict(user_input, Pedido, [value_string_as(atom)]),
	valida_pedido(Pedido, Passos),
	corre_passos(Passos),
	monta_resposta(UltimoInicial, Resposta).

pasta_base_argumento(Pasta) :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	(   Argv = [Pasta|_]
	->  true
	;   throw(erro_wrapper('falta a pasta da base de conhecimento: swipl wrapper.pl <pasta da base>'))
	).

% Carrega o soc_factos.txt da pasta dada (que carrega as regras e o suporte) e corre o motor sobre os dados fixos.
carrega_base(Pasta) :-
	directory_file_path(Pasta, 'soc_factos.txt', Ficheiro),
	(   exists_file(Ficheiro)
	->  true
	;   format(atom(M), 'nao existe ~w (a pasta da base e o argumento do wrapper)', [Ficheiro]),
	    throw(erro_wrapper(M))
	),
	carrega_bc(Ficheiro),
	arranca_motor.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% PEDIDO

% Valida o pedido e converte-o para uma lista Id-Factos. Tudo e convertido antes de correr o primeiro passo,
% para um pedido mal formado nunca dar uma inferencia a meio.
valida_pedido(Pedido, Passos) :-
	(   is_dict(Pedido)
	->  true
	;   throw(erro_pedido('o pedido tem de ser um objeto JSON'))
	),
	versao_kb(Versao),
	(   get_dict(versaoKB, Pedido, V0), atom_string(V0, S), atom_string(Versao, S)
	->  true
	;   ( get_dict(versaoKB, Pedido, V1) -> true ; V1 = '' ),
	    format(atom(M), 'versaoKB desconhecida: "~w" (carregada: ~w)', [V1, Versao]),
	    throw(erro_pedido(M))
	),
	(   get_dict(passos, Pedido, PassosJson), is_list(PassosJson), PassosJson \== []
	->  true
	;   throw(erro_pedido('o pedido tem de ter pelo menos um passo'))
	),
	converte_passos(PassosJson, 1, Passos).

converte_passos([], _, []).
converte_passos([PJ|PJs], I, [Id-Factos|Ps]) :-
	(   is_dict(PJ)
	->  true
	;   format(atom(M), 'o passo ~w nao e um objeto JSON', [I]),
	    throw(erro_pedido(M))
	),
	( get_dict(id, PJ, Id0), Id0 \== null -> Id = Id0 ; format(atom(Id), 'passo_~w', [I]) ),
	(   get_dict(factos, PJ, FJs), is_list(FJs)
	->  true
	;   format(atom(M2), 'o passo ~w nao tem a lista "factos"', [Id]),
	    throw(erro_pedido(M2))
	),
	maplist(json_facto(Id), FJs, Factos),
	I1 is I + 1,
	converte_passos(PJs, I1, Ps).

% JSON -> facto: {"predicado": nome, "args": [...]}
json_facto(Passo, J, F) :-
	(   is_dict(J), get_dict(predicado, J, P), atom(P), P \== null,
	    get_dict(args, J, AsJ), is_list(AsJ)
	->  maplist(json_termo(Passo), AsJ, As),
	    F =.. [P|As]
	;   format(atom(M), 'facto invalido no passo ~w (tem de ter "predicado" e "args")', [Passo]),
	    throw(erro_pedido(M))
	).

% JSON -> termo: objeto {"f", "args"} -> termo composto; array -> lista; string -> atomo; numero -> numero.
json_termo(Passo, D, T) :-
	is_dict(D), !,
	(   get_dict(f, D, N), atom(N), get_dict(args, D, AsJ), is_list(AsJ), dict_pairs(D, _, [_, _])
	->  maplist(json_termo(Passo), AsJ, As),
	    compound_name_arguments(T, N, As)
	;   format(atom(M), 'termo invalido no passo ~w: um objeto tem de ser {"f": nome, "args": [...]}', [Passo]),
	    throw(erro_pedido(M))
	).
json_termo(Passo, L, T) :-
	is_list(L), !,
	maplist(json_termo(Passo), L, T).
json_termo(_, X, X).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% PASSOS

% Cada passo, por ordem: injeta os factos com novo_facto/1 (da base) e corre o motor ate ao ponto fixo.
% Os factos tem de entrar por passos: se o alerta e a resposta do analista entrarem juntos, a pergunta nunca e feita.
corre_passos([]).
corre_passos([Id-Factos|Ps]) :-
	ultimo_facto(Antes),
	forall(member(F, Factos), novo_facto(F)),
	arranca_motor,
	ultimo_facto(Depois),
	assertz(passo_intervalo(Id, Antes, Depois)),
	corre_passos(Ps).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% RESPOSTA (as listas vao por ordem do numero do facto, para o mesmo pedido dar sempre a mesma resposta)

monta_resposta(UltimoInicial, json([ estado = Estado,
				     motor = prolog,
				     versaoKB = Versao,
				     perguntas = Perguntas,
				     incidentes = Incidentes,
				     tickets = Tickets,
				     factos = Factos,
				     erro = @(null) ])) :-
	versao_kb(Versao),
	perguntas_json(Perguntas),
	( Perguntas == [] -> Estado = concluido ; Estado = aguarda_resposta ),
	incidentes_json(Incidentes),
	tickets_json(Tickets),
	factos_json(UltimoInicial, Factos).

% Perguntas ao analista sem resposta e sem pergunta_retirada, com o texto e as opcoes que estao na base.
perguntas_json(L) :-
	findall(json([ facto = N, ativo = A, evento = Ev, pergunta = P, texto = Texto, opcoes = Opcoes ]),
		( facto(N, pergunta_analista(A, Ev, P)),
		  \+ facto(_, resposta_analista(A, Ev, P, _)),
		  \+ facto(_, pergunta_retirada(A, Ev, P)),
		  ( facto(_, texto_pergunta(P, Texto)) -> true ; Texto = @(null) ),
		  findall(json([valor = V, texto = TV]), facto(_, opcao_resposta(P, V, TV)), Opcoes) ),
		L).

incidentes_json(L) :-
	findall(json([ facto = N, id = IdJ, tipo = TipoJ, ativo = AtivoJ, impactados = ImpJ ]),
		( facto(N, incidente(Id, Tipo, Ativo, Imp)),
		  maplist(termo_json, [Id, Tipo, Ativo, Imp], [IdJ, TipoJ, AtivoJ, ImpJ]) ),
		L).

% ticket(Incidente, Ativo, AcaoPedida, Gravidade, Prioridade, AtivosImpactados); fechado: o motivo, ou null.
tickets_json(L) :-
	findall(json([ facto = N, incidente = IncJ, ativo = AtivoJ, acao = AcaoJ, gravidade = GJ,
		       prioridade = PJ, impactados = ImpJ, fechado = Fechado ]),
		( facto(N, ticket(Inc, Ativo, Acao, G, P, Imp)),
		  maplist(termo_json, [Inc, Ativo, Acao, G, P, Imp], [IncJ, AtivoJ, AcaoJ, GJ, PJ, ImpJ]),
		  (   facto(_, ticket_fechado(Inc, Motivo))
		  ->  termo_json(Motivo, Fechado)
		  ;   Fechado = @(null)
		  ) ),
		L).

% Todos os factos, com a origem: inicial (dados fixos), entrada (injetado num passo) ou regra (concluido pelo motor,
% com a regra e as premissas de justifica/3). Os factos concluidos sobre os dados fixos nao tem passo.
factos_json(UltimoInicial, L) :-
	ultimo_facto(U),
	findall(J, ( between(1, U, N), facto(N, F), facto_json(UltimoInicial, N, F, J) ), L).

facto_json(UltimoInicial, N, F, json([ numero = N, predicado = P, args = ArgsJ, origem = Origem,
				       passo = Passo, regra = Regra, premissas = PremJ ])) :-
	F =.. [P|Args],
	maplist(termo_json, Args, ArgsJ),
	(   justifica(N, R, Premissas0)
	->  Origem = regra, Regra = R,
	    % as premissas negadas (nao X) podem ter variaveis livres: nomeiam-se A, B, ... para a resposta ser estavel
	    copy_term(Premissas0, Premissas),
	    numbervars(Premissas, 0, _),
	    maplist(termo_json, Premissas, PremJ)
	;   N =< UltimoInicial
	->  Origem = inicial, Regra = @(null), PremJ = []
	;   Origem = entrada, Regra = @(null), PremJ = []
	),
	(   N > UltimoInicial, passo_intervalo(Id, Antes, Depois), N > Antes, N =< Depois
	->  Passo = Id
	;   Passo = @(null)
	).

% Termo Prolog -> JSON (issue #6). Os atomos saem como strings (tambem true, false e null).
termo_json('$VAR'(I), S) :-
	integer(I), !,
	format(atom(S), '~W', ['$VAR'(I), [numbervars(true)]]).
termo_json(L, J) :-
	is_list(L), !,
	maplist(termo_json, L, J).
termo_json(T, json([f = N, args = J])) :-
	compound(T), !,
	compound_name_arguments(T, N, As),
	maplist(termo_json, As, J).
termo_json(X, X) :-
	( atom(X) ; number(X) ), !.
termo_json(X, S) :-
	format(atom(S), '~q', [X]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ERROS

resposta_erro(Erro, json([ estado = erro, motor = prolog, versaoKB = Versao, erro = Mensagem ])) :-
	versao_kb(Versao),
	texto_erro(Erro, Mensagem),
	format(user_error, 'wrapper.pl: ~w~n', [Mensagem]).

texto_erro(erro_pedido(M), M) :- !.
texto_erro(erro_wrapper(M), M) :- !.
texto_erro(falhou, 'o motor falhou sem dar erro') :- !.
texto_erro(error(syntax_error(S), _), M) :- !,
	format(atom(M), 'pedido JSON invalido: ~w', [S]).
texto_erro(E, M) :-
	catch(( message_to_codes(E, Codes), atom_codes(M, Codes) ), _, fail), !.
texto_erro(E, M) :-
	format(atom(M), '~q', [E]).

message_to_codes(E, Codes) :-
	translate_message(E, Linhas, []),
	with_output_to(codes(Codes), print_message_lines(current_output, '', Linhas)).
