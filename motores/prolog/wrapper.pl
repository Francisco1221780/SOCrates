% =====================================================================================
% WRAPPER JSON - Analista virtual de SOC (SOCrates)
% Ponte entre o Java (PrologAdapter) e o motor: le um pedido JSON do stdin, corre os passos no motor e escreve
% uma resposta JSON no stdout. O motor (soc_motor.pl) e a base nao sabem nada de JSON.
%
%   swipl wrapper.pl <pasta da base>  < pedido.json  > resposta.json
%   ex.: swipl motores/prolog/wrapper.pl base_conhecimento < pedido.json
%
% Pedido:   {"versaoKB": "6", "passos": [{"id": "cenario_07", "factos": [{"predicado": "...", "args": [...]}]}, ...]}
% Resposta: {"estado", "motor", "versaoKB", "perguntas", "incidentes", "tickets", "factos", "erro"}
%           estado: "concluido", "aguarda_resposta" (ha perguntas por responder) ou "erro"
%
% "Porque nao?" (issue #7), com o modo whynot depois da pasta:
%   swipl wrapper.pl <pasta da base> whynot  < pedido.json  > resposta.json
% Pedido:   o de cima com "facto": {"predicado", "args"} (os args podem ter variaveis {"var": "Ev"}) e,
%           opcionalmente, "profundidade" (inteiro >= 1; por omissao a opcao profundidade_whynot do motor, 4)
% Resposta: {"estado": "concluido", "motor", "versaoKB", "facto", "explicacao", "erro"}; explicacao e a lista das
%           linhas do whynot_linhas/3 do motor, {"nivel", "tipo", ...}, com os mesmos campos do Drools (PorqueNao.java)
%
% Termos Prolog em JSON (issue #6): atomo -> string, inteiro -> numero, lista -> array,
% termo composto -> {"f": nome, "args": [...]}, facto -> {"predicado": nome, "args": [...]}.
%
% Regras (motores/prolog/README.md):
%   - o stdout tem exatamente um documento JSON; mensagens do motor, avisos e diagnostico vao para o stderr;
%   - UTF-8 no stdin, no stdout, no stderr e na leitura da base (M10);
%   - a pasta da base chega por argumento (M5); o wrapper nunca le do teclado nem chama o menu do motor (menu/0);
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
	argumentos(Pasta, Modo),
	carrega_base(Pasta),
	ultimo_facto(UltimoInicial),
	json_read_dict(user_input, Pedido, [value_string_as(atom)]),
	valida_pedido(Pedido, Passos),
	valida_modo(Modo, Pedido, Explicacao),
	corre_passos(Passos),
	monta_resposta(Explicacao, UltimoInicial, Resposta).

% A pasta da base e, depois dela, o modo: nada (a inferencia) ou whynot.
argumentos(Pasta, Modo) :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	(   Argv = [Pasta|Resto]
	->  true
	;   throw(erro_wrapper('falta a pasta da base de conhecimento: swipl wrapper.pl <pasta da base> [whynot]'))
	),
	(   Resto == [] -> Modo = infere
	;   Resto == [whynot] -> Modo = whynot
	;   format(atom(M), 'modo desconhecido: ~w (so ha whynot)', [Resto]),
	    throw(erro_wrapper(M))
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

% O que o modo pede alem dos passos: nada (infere) ou porque_nao(Facto, FactoJson, Profundidade). Valida-se antes de
% correr os passos, como o resto do pedido.
valida_modo(infere, _, infere).
valida_modo(whynot, Pedido, porque_nao(Facto, FactoJson, Profundidade)) :-
	(   get_dict(facto, Pedido, FJ), FJ \== null
	->  json_facto_porque_nao(FJ, Facto, FactoJson)
	;   throw(erro_pedido('falta o facto do porque nao'))
	),
	(   ( \+ get_dict(profundidade, Pedido, _) ; get_dict(profundidade, Pedido, null) )
	->  opcao(profundidade_whynot, Profundidade)
	;   get_dict(profundidade, Pedido, Profundidade), integer(Profundidade), Profundidade >= 1
	->  true
	;   throw(erro_pedido('a profundidade tem de ser um inteiro >= 1'))
	).

% JSON -> facto do "porque nao": como o json_facto/3, mas os args podem ter variaveis {"var": nome} (o mesmo nome e a
% mesma variavel, cada "_" e uma nova, como no Drools). Da tambem o facto em JSON para a resposta, como veio.
json_facto_porque_nao(J, F, json([predicado = P, args = AsE])) :-
	(   is_dict(J), get_dict(predicado, J, P), atom(P), P \== null,
	    get_dict(args, J, AsJ), is_list(AsJ)
	->  foldl(json_termo_var, AsJ, As, AsE, [], _),
	    F =.. [P|As]
	;   throw(erro_pedido('o facto do porque nao tem de ser {"predicado", "args"}'))
	).

json_termo_var(D, V, json([var = N]), Vs0, Vs) :-
	is_dict(D), get_dict(var, D, N), atom(N), N \== null, dict_pairs(D, _, [_]), !,
	(   N == '_' -> Vs = Vs0
	;   memberchk(N-V, Vs0) -> Vs = Vs0
	;   Vs = [N-V|Vs0]
	).
json_termo_var(D, T, json([f = N, args = AsE]), Vs0, Vs) :-
	is_dict(D), get_dict(f, D, N), atom(N), get_dict(args, D, AsJ), is_list(AsJ), dict_pairs(D, _, [_, _]), !,
	foldl(json_termo_var, AsJ, As, AsE, Vs0, Vs),
	compound_name_arguments(T, N, As).
json_termo_var(D, _, _, _, _) :-
	is_dict(D), !,
	throw(erro_pedido('termo invalido no facto do porque nao: um objeto tem de ser {"f": nome, "args": [...]} ou {"var": nome}')).
json_termo_var(L, T, E, Vs0, Vs) :-
	is_list(L), !,
	foldl(json_termo_var, L, T, E, Vs0, Vs).
json_termo_var(X, X, X, Vs, Vs).


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

monta_resposta(porque_nao(Facto, FactoJson, Profundidade), _,
	       json([ estado = concluido,
		      motor = prolog,
		      versaoKB = Versao,
		      facto = FactoJson,
		      explicacao = Linhas,
		      erro = @(null) ])) :- !,
	versao_kb(Versao),
	whynot_linhas(Facto, Profundidade, Linhas0),
	maplist(linha_whynot_json, Linhas0, Linhas).
monta_resposta(infere, UltimoInicial, json([ estado = Estado,
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

% Uma linha do "porque nao?" (linha(Nivel, Tipo, Campos) do motor) -> {"nivel", "tipo", ...campos}. Cada termo tem
% as suas variaveis chamadas A, B, ... (como no legivel/2 e no Drools); um numero que nao ha ('?') sai null.
linha_whynot_json(linha(Nivel, Tipo, Campos), json([nivel = Nivel, tipo = Tipo|Pares])) :-
	maplist(campo_whynot_json, Campos, Pares).

campo_whynot_json(termo-T, termo = J) :- termo_legivel_json(T, J).
campo_whynot_json(encontrado-T, encontrado = J) :- termo_legivel_json(T, J).
campo_whynot_json(numero-N, numero = J) :- ( integer(N) -> J = N ; J = @(null) ).
campo_whynot_json(regra-Id, regra = Id).
campo_whynot_json(se-Cs, se = Js) :- maplist(condicao_whynot_json, Cs, Js).
campo_whynot_json(entao-Ts, entao = Js) :- maplist(termo_legivel_json, Ts, Js).

% condicao(Tipo, Termo, sim/nao, Numero) -> {"tipo", "termo", "verificada", "numero"?}
condicao_whynot_json(condicao(Tipo, T, Ok, N), json([tipo = Tipo, termo = J, verificada = V|Num])) :-
	termo_legivel_json(T, J),
	( Ok == sim -> V = @(true) ; V = @(false) ),
	( integer(N) -> Num = [numero = N] ; Num = [] ).

termo_legivel_json(T, J) :-
	copy_term(T, C),
	numbervars(C, 0, _),
	termo_json(C, J).

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
