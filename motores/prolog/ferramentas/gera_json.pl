% Gera os JSON que os outros componentes usam a partir da base de conhecimento (a unica fonte dos factos e dos cenarios):
%   motores/drools/src/main/resources/factos_iniciais.json        os factos fixos do soc_factos.txt (para o Drools)
%   backend/socrates/src/main/resources/generated/cenarios.json      os cenarios, um passo por ficheiro, com a ordem e as
%                                                                 dependencias do cenarios/indice.txt (para a UI)
%   motores/drools/src/main/resources/cenarios.json               o mesmo ficheiro, para o modulo Drools se bastar a si
%                                                                 proprio (a consola interativa corre os cenarios)
%   motores/drools/src/main/resources/regras.json                 as regras do soc_regras.txt como dados, com as
%                                                                 variaveis, e as condicoes calculadas (para o
%                                                                 "porque nao?" do Drools)
% Uso, a partir de qualquer pasta (os caminhos sao relativos a este ficheiro):
%   swipl motores/prolog/ferramentas/gera_json.pl                 escreve os ficheiros
%   swipl motores/prolog/ferramentas/gera_json.pl --verificar     nao escreve nada; sai com codigo 1 se algum estiver
%                                                                 desatualizado em relacao aos .txt (AC18)
% Le os ficheiros da base como termos (sem os carregar), por isso nao depende do motor.
%
% De onde vem cada coisa:
%   versaoKB      cabecalho do soc_factos.txt ("... versao 6"), como diz o base_conhecimento/README.md
%   factos        facto(N, F) do soc_factos.txt, por ordem de numero
%   cenarios      ordem, grupo e dependencias: cenarios/indice.txt; id e descricao: soc_cenarios.pl (cenario/3);
%                 factos de cada passo: o ficheiro do cenario (linhas novo_facto(F)), pela ordem do ficheiro
%   sequencias    sequencia_base/1, sequencia_atualizacoes/1, sequencia_normal/1 e sequencia_completa/1 do soc_cenarios.pl
%                 (a completa e uma regra: corre-se sobre as outras; sem ela, e a base, as atualizacoes e a normal)
%
% Dependencias, como o indice as descreve:
%   telemetria    correm-se pela ordem da lista: cada cenario depende do anterior que nao e alternativa de outro
%   atualizacoes  "carregam-se depois do cenario N" (cabecalho da seccao)
%   normal        independentes ("ou sozinhos so com o estado inicial"), salvo "O X so faz sentido depois do Y"
%   alternativas  seccao "Alternativas": dependem do cenario citado no texto ("do cenario N", "do Na"), como as antigas
%                 respostas do analista (seccao "Respostas", que a base v6.1 ja nao tem)
%   alternativas  "(em alternativa ao N)" e as linhas com varios ficheiros e "(so UM)"
% Se o indice tiver uma linha que nao se percebe, ou faltar um ficheiro, para com erro em vez de gerar dados errados.

% A biblioteca JSON passou de library(http/json) para library(json) no SWI-Prolog 10; aceitam-se as duas.
:- if(exists_source(library(json))).
:- use_module(library(json)).
:- else.
:- use_module(library(http/json)).
:- endif.
:- use_module(library(lists)).
:- use_module(library(apply)).

:- initialization(main, main).

:- prolog_load_context(directory, D), asserta(pasta_ferramentas(D)).
:- dynamic pasta_ferramentas/1.

main :-
	current_prolog_flag(argv, Argv0),
	( Argv0 = ['--'|Argv] -> true ; Argv = Argv0 ),
	catch(corre(Argv), E, ( print_message(error, E), halt(1) )).

corre(Argv) :-
	raiz(Raiz),
	gera(Raiz, Ficheiros),
	(   memberchk('--verificar', Argv)
	->  verifica(Raiz, Ficheiros)
	;   forall(member(F-Texto, Ficheiros), escreve(Raiz, F, Texto))
	).

raiz(Raiz) :-
	pasta_ferramentas(D),
	directory_file_path(D, '../../..', R0),
	absolute_file_name(R0, Raiz, [file_type(directory)]).

% Os ficheiros: caminho relativo a raiz do repositorio - conteudo. O cenarios.json vai igual para o backend e para o
% modulo Drools (o --verificar compara os dois).
gera(Raiz, ['motores/drools/src/main/resources/factos_iniciais.json'-Factos,
	    'backend/socrates/src/main/resources/generated/cenarios.json'-Cenarios,
	    'motores/drools/src/main/resources/cenarios.json'-Cenarios,
	    'motores/drools/src/main/resources/regras.json'-Regras]) :-
	directory_file_path(Raiz, base_conhecimento, Base),
	versao_kb(Base, Versao),
	factos_iniciais(Base, LF),
	json_texto(json([versaoKB=Versao, factos=LF]), Factos),
	cenarios(Base, LC, Seqs),
	json_texto(json([versaoKB=Versao, cenarios=LC, sequencias=Seqs]), Cenarios),
	regras(Base, LR, LCalc),
	json_texto(json([versaoKB=Versao, regras=LR, calculadas=LCalc]), Regras).

json_texto(Json, Texto) :-
	with_output_to(string(T0), json_write(current_output, Json, [width(120), tab(1000)])),
	string_concat(T0, "\n", Texto).

escreve(Raiz, F, Texto) :-
	directory_file_path(Raiz, F, Caminho),
	setup_call_cleanup(open(Caminho, write, S, [encoding(utf8), newline(posix)]),
			   write(S, Texto),
			   close(S)),
	format(user_error, 'Escrito ~w~n', [F]).

% Compara sem contar com os fins de linha (o git pode converter LF em CRLF no Windows).
verifica(Raiz, Ficheiros) :-
	include(desatualizado(Raiz), Ficheiros, Maus),
	(   Maus == []
	->  format(user_error, 'Os JSON estao atualizados.~n', [])
	;   forall(member(F-_, Maus), format(user_error, 'Desatualizado: ~w~n', [F])),
	    format(user_error, 'Corra: swipl motores/prolog/ferramentas/gera_json.pl~n', []),
	    halt(1)
	).

desatualizado(Raiz, F-Texto) :-
	directory_file_path(Raiz, F, Caminho),
	(   exists_file(Caminho)
	->  read_file_to_string(Caminho, Atual0, [encoding(utf8)]),
	    sem_cr(Atual0, Atual),
	    Atual \== Texto
	;   true
	).

sem_cr(S0, S) :- split_string(S0, "\r", "", Partes), atomic_list_concat(Partes, '', A), atom_string(A, S).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% LEITURA DA BASE

le_termos(Ficheiro, Termos) :-
	setup_call_cleanup(open(Ficheiro, read, S, [encoding(utf8)]),
			   le_termos_stream(S, Termos),
			   close(S)).

le_termos_stream(S, Termos) :-
	read_term(S, T, []),
	(   T == end_of_file
	->  Termos = []
	;   Termos = [T|R],
	    le_termos_stream(S, R)
	).

le_linhas(Ficheiro, Linhas) :-
	read_file_to_string(Ficheiro, S, [encoding(utf8)]),
	split_string(S, "\n", "\r", Linhas).

% A versao esta no cabecalho do soc_factos.txt: "% FACTOS - ... versao 6".
versao_kb(Base, Versao) :-
	directory_file_path(Base, 'soc_factos.txt', F),
	le_linhas(F, [Linha|_]),
	(   sub_string(Linha, _, _, Depois, "versao "),
	    sub_string(Linha, _, Depois, 0, V0),
	    split_string(V0, " ", " ", [V|_]), V \== ""
	->  atom_string(Versao, V)
	;   throw(erro_base('nao encontro a versao no cabecalho do soc_factos.txt'))
	).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% FACTOS INICIAIS

factos_iniciais(Base, L) :-
	directory_file_path(Base, 'soc_factos.txt', F),
	le_termos(F, Termos),
	findall(N-Facto, member(facto(N, Facto), Termos), Pares),
	keysort(Pares, Ordenados),
	pairs_values(Ordenados, Factos),
	maplist(facto_json, Factos, L).

% Facto e termos em JSON (tabela da issue #6, a mesma do wrapper.pl).
facto_json(F, json([predicado=P, args=J])) :-
	F =.. [P|As],
	maplist(termo_json, As, J).

termo_json(L, J) :- is_list(L), !, maplist(termo_json, L, J).
termo_json(T, json([f=N, args=J])) :- compound(T), !, compound_name_arguments(T, N, As), maplist(termo_json, As, J).
termo_json(X, X).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% REGRAS (para o "porque nao?" do Drools)
% Cada regra: {"id", "se": [condicao...], "entao": [termo...]}. Uma condicao e um termo, {"nao": termo} ou
% {"avalia": termo}. Uma variavel e {"var": "Nome"}; cada "_" e {"var": "_"} (uma variavel nova de cada vez).
% Os operadores das regras (os do soc_motor.pl) ficam num modulo a parte e so se usam ao ler o soc_regras.txt, para
% os atomos nao e e dos factos se lerem como antes.

:- op(220, xfx, ops_regras:entao).
:- op(35, xfy, ops_regras:se).
:- op(240, fx, ops_regras:regra).
:- op(500, fy, ops_regras:nao).
:- op(600, xfy, ops_regras:e).

regras(Base, Regras, Calculadas) :-
	directory_file_path(Base, 'soc_regras.txt', F),
	setup_call_cleanup(open(F, read, S, [encoding(utf8)]), le_regras(S, Regras), close(S)),
	(   Regras == [] -> throw(erro_base('o soc_regras.txt nao tem regras')) ; true ),
	directory_file_path(Base, 'soc_suporte.txt', FS),
	le_termos(FS, TS),
	findall(NA, ( member(condicao_calculada(C), TS), functor(C, Nome, Ar), format(atom(NA), '~w/~w', [Nome, Ar]) ),
		Calculadas).

le_regras(S, Regras) :-
	read_term(S, T, [module(ops_regras), variable_names(Vs)]),
	(   T == end_of_file
	->  Regras = []
	;   T = regra(entao(se(Id, [LHS]), RHS))
	->  maplist(nome_variavel, Vs),
	    conjuncao(LHS, Cs),
	    maplist(condicao_json, Cs, JCs),
	    maplist(conclusao_json, RHS, JRs),
	    Regras = [json([id=Id, se=JCs, entao=JRs])|R],
	    le_regras(S, R)
	;   le_regras(S, Regras)
	).

% Liga cada variavel com nome a '$var'(Nome); as que ficam por ligar sao os "_".
nome_variavel(Nome=V) :- V = '$var'(Nome).

conjuncao(e(A, B), [A|Cs]) :- !, conjuncao(B, Cs).
conjuncao(C, [C]).

condicao_json(nao(X), json([nao=J])) :- !, termo_var_json(X, J).
condicao_json(avalia(X), json([avalia=J])) :- !, termo_var_json(X, J).
condicao_json(X, J) :- termo_var_json(X, J).

conclusao_json(cria_facto(F), J) :- !, termo_var_json(F, J).
conclusao_json(A, _) :-
	format(atom(M), 'acao desconhecida no entao de uma regra: ~w', [A]), throw(erro_base(M)).

termo_var_json(V, json([var='_'])) :- var(V), !.
termo_var_json('$var'(Nome), json([var=Nome])) :- !.
termo_var_json(L, J) :- is_list(L), !, maplist(termo_var_json, L, J).
termo_var_json(T, json([f=N, args=J])) :- compound(T), !, compound_name_arguments(T, N, As), maplist(termo_var_json, As, J).
termo_var_json(X, X).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% CENARIOS

cenarios(Base, Cenarios, json([base=SB, atualizacoes=SA, normal=SN, completa=SC])) :-
	directory_file_path(Base, 'soc_cenarios.pl', FC),
	le_termos(FC, TC),
	findall(c(Id, Desc, Fich), member(cenario(Id, Desc, Fich), TC), Catalogo),
	( member(sequencia_base(SB), TC) -> true ; SB = [] ),
	( member(sequencia_atualizacoes(SA), TC) -> true ; SA = [] ),
	( member(sequencia_normal(SN), TC) -> true ; SN = [] ),
	sequencia_completa(TC, SB, SA, SN, SC),
	directory_file_path(Base, 'cenarios/indice.txt', FI),
	le_linhas(FI, Linhas),
	entradas_indice(Linhas, Entradas),
	verifica_indice(Base, Entradas, Catalogo),
	dependencias(Entradas, Catalogo, ComDeps),
	findall(J, ( nth1(Ordem, ComDeps, E), cenario_json(Base, Catalogo, Ordem, E, J) ), Cenarios).

% A sequencia completa: a regra sequencia_completa/1 do soc_cenarios.pl, corrida sobre as sequencias lidas; sem a regra,
% a base, as atualizacoes e a normal, por esta ordem.
sequencia_completa(TC, SB, SA, SN, SC) :-
	(   member((sequencia_completa(L) :- Corpo), TC)
	->  copy_term((L :- Corpo), (SC :- C)),
	    setup_call_cleanup(
		( assertz(gj_seq:sequencia_base(SB)), assertz(gj_seq:sequencia_atualizacoes(SA)), assertz(gj_seq:sequencia_normal(SN)) ),
		( gj_seq:C -> true ; throw(erro_base('a regra sequencia_completa/1 do soc_cenarios.pl falhou')) ),
		( retractall(gj_seq:sequencia_base(_)), retractall(gj_seq:sequencia_atualizacoes(_)), retractall(gj_seq:sequencia_normal(_)) ))
	;   append([SB, SA, SN], SC)
	).

% Le o indice: as seccoes (cabecalho e texto) e as entradas e(Grupo, Ficheiros, Texto, TextoDaSeccao).
% Um cabecalho de seccao comeca com 3 espacos depois do "%"; uma entrada comeca com 5 espacos e "cenario_".
entradas_indice(Linhas, Entradas) :-
	entradas_indice(Linhas, nenhum, "", Entradas).

entradas_indice([], _, _, []).
entradas_indice([L|Ls], G0, T0, Es) :-
	(   sub_string(L, 0, _, _, "%     cenario_")
	->  sub_string(L, 6, _, 0, Resto),
	    entrada(Resto, Fichs, Texto),
	    (   G0 == nenhum
	    ->  format(atom(M), 'indice.txt: entrada fora de uma seccao: ~w', [L]), throw(erro_base(M))
	    ;   true
	    ),
	    Es = [e(G0, Fichs, Texto, T0)|Es1],
	    entradas_indice(Ls, G0, T0, Es1)
	;   sub_string(L, 0, _, _, "%   "), \+ sub_string(L, 0, _, _, "%    "),
	    sub_string(L, 4, _, 0, Cab), grupo_seccao(Cab, G)
	->  entradas_indice(Ls, G, Cab, Es)
	;   G0 \== nenhum, sub_string(L, 0, _, _, "%   "), \+ sub_string(L, 0, _, _, "%     ")
	->  sub_string(L, 4, _, 0, Mais), string_concat(T0, " ", T1), string_concat(T1, Mais, T2),
	    entradas_indice(Ls, G0, T2, Es)
	;   entradas_indice(Ls, G0, T0, Es)
	).

grupo_seccao(Cab, telemetria)  :- sub_string(Cab, 0, _, _, "Telemetria").
grupo_seccao(Cab, atualizacao) :- sub_string(Cab, 0, _, _, "Atualizacoes").
grupo_seccao(Cab, normal)      :- sub_string(Cab, 0, _, _, "Atividade normal").
grupo_seccao(Cab, resposta)    :- sub_string(Cab, 0, _, _, "Respostas").
grupo_seccao(Cab, alternativa) :- sub_string(Cab, 0, _, _, "Alternativas").

% "cenario_07b_sim.txt, cenario_07b_nao.txt   texto" -> ficheiros e texto.
entrada(Resto, Fichs, Texto) :-
	split_string(Resto, " ", "", Palavras0),
	exclude(==(""), Palavras0, Palavras),
	ficheiros(Palavras, Fichs, PalavrasTexto),
	Fichs \== [],
	atomic_list_concat(PalavrasTexto, ' ', TextoA),
	atom_string(TextoA, Texto).

ficheiros([P|Ps], [F|Fs], R) :-
	split_string(P, ",", "", [P1|_]),
	sub_string(P1, 0, _, _, "cenario_"), sub_string(P1, _, _, 0, ".txt"), !,
	atom_string(F, P1),
	ficheiros(Ps, Fs, R).
ficheiros(R, [], R).

% Cada ficheiro de cenario aparece uma vez no indice e tem um cenario/3 no soc_cenarios.pl.
verifica_indice(Base, Entradas, Catalogo) :-
	findall(F, ( member(e(_, Fs, _, _), Entradas), member(F, Fs) ), NoIndice),
	msort(NoIndice, Ordenados), sort(NoIndice, Unicos),
	(   Ordenados == Unicos -> true
	;   throw(erro_base('indice.txt: ha ficheiros repetidos'))
	),
	directory_file_path(Base, cenarios, Pasta),
	directory_files(Pasta, Todos),
	include([X]>>( sub_atom(X, 0, _, _, cenario_), sub_atom(X, _, _, 0, '.txt') ), Todos, NaPasta0),
	sort(NaPasta0, NaPasta),
	(   NaPasta == Unicos -> true
	;   ord_subtract(NaPasta, Unicos, SoPasta), ord_subtract(Unicos, NaPasta, SoIndice),
	    format(atom(M), 'indice.txt e cenarios/ nao batem certo: so na pasta ~w, so no indice ~w', [SoPasta, SoIndice]),
	    throw(erro_base(M))
	),
	forall(member(F, Unicos),
	       (   id_ficheiro(Catalogo, F, _) -> true
	       ;   format(atom(M2), 'soc_cenarios.pl nao tem cenario/3 para ~w', [F]), throw(erro_base(M2))
	       )).

id_ficheiro(Catalogo, F, Id) :-
	atom_concat('cenarios/', F, Rel),
	memberchk(c(Id, _, Rel), Catalogo).

% Numero de um cenario no texto do indice ("7", "10c", "35b") -> id, pelo ficheiro (cenario_07.txt, cenario_10c.txt).
id_numero(Catalogo, Num, Id) :-
	atom_codes(Num, Cs),
	append(Ds, Ls, Cs), Ds \== [], maplist([C]>>code_type(C, digit), Ds), \+ ( Ls = [C0|_], code_type(C0, digit) ), !,
	number_codes(N, Ds), atom_codes(Letras, Ls),
	format(atom(F), 'cenario_~|~`0t~d~2+~w.txt', [N, Letras]),
	(   id_ficheiro(Catalogo, F, Id) -> true
	;   format(atom(M), 'indice.txt cita o cenario ~w, mas nao existe ~w', [Num, F]), throw(erro_base(M))
	).

% Junta a cada entrada (uma por ficheiro) as dependencias e as alternativas.
dependencias(Entradas, Catalogo, ComDeps) :-
	findall(d(G, F, Texto, Cab, Fs), ( member(e(G, Fs, Texto, Cab), Entradas), member(F, Fs) ), Ds),
	deps(Ds, Ds, Catalogo, none, ComDeps).

% Todas: a lista completa, para as alternativas citadas no texto de outras entradas.
deps([], _, _, _, []).
deps([d(G, F, Texto, Cab, Fs)|Ds], Todas, Cat, Anterior, [x(G, F, Depende, Alternativas)|Xs]) :-
	id_ficheiro(Cat, F, Id),
	alternativas(Cat, F, Texto, Fs, Alt0),
	findall(A, ( member(d(_, F2, T2, _, _), Todas), alternativa_ao(T2, Num), id_numero(Cat, Num, IdC), IdC == Id, id_ficheiro(Cat, F2, A) ), Alt1),
	append(Alt0, Alt1, Alt2), sort(Alt2, Alternativas),
	depende(G, Cat, Texto, Cab, Id, Anterior, Depende),
	(   G == telemetria, Alternativas == [] -> Anterior1 = Id ; Anterior1 = Anterior ),
	deps(Ds, Todas, Cat, Anterior1, Xs).

% "(em alternativa ao 29c)" e, numa linha com varios ficheiros e "(so UM)", os outros ficheiros dessa linha.
alternativas(Cat, F, Texto, Fs, Alt) :-
	findall(A, ( alternativa_ao(Texto, Num), id_numero(Cat, Num, A) ), A1),
	(   sub_string(Texto, _, _, _, "(so UM)")
	->  findall(A, ( member(F2, Fs), F2 \== F, id_ficheiro(Cat, F2, A) ), A2)
	;   A2 = []
	),
	append(A1, A2, Alt).

alternativa_ao(Texto, Num) :-
	sub_string(Texto, _, _, Depois, "em alternativa ao "),
	sub_string(Texto, _, Depois, 0, Resto),
	palavra(Resto, Num).

palavra(S, P) :-
	string_codes(S, Cs),
	append(Ps, R, Cs), \+ ( Ps = [] ), ( R = [] ; R = [C|_], \+ code_type(C, alnum) ), !,
	atom_codes(P, Ps).

depende(telemetria, _, _, _, _, none, []) :- !.
depende(telemetria, _, _, _, _, Anterior, [Anterior]) :- !.
depende(atualizacao, Cat, _, Cab, _, _, [Id]) :- !,
	(   cenario_citado(Cab, Num) -> id_numero(Cat, Num, Id)
	;   throw(erro_base('indice.txt: a seccao das atualizacoes nao diz depois de que cenario se carregam'))
	).
depende(normal, Cat, _, Cab, Id, _, Deps) :- !,
	findall(D, ( so_faz_sentido_depois(Cab, X, Y), id_numero(Cat, X, IdX), IdX == Id, id_numero(Cat, Y, D) ), Deps).
depende(alternativa, Cat, Texto, Cab, Id0, Ant, Deps) :- !,
	depende(resposta, Cat, Texto, Cab, Id0, Ant, Deps).
depende(resposta, Cat, Texto, _, _, _, [Id]) :-
	(   antes_de_alternativa(Texto, T1), cenario_citado(T1, Num) -> id_numero(Cat, Num, Id)
	;   format(atom(M), 'indice.txt: nao percebo a que cenario responde "~w"', [Texto]), throw(erro_base(M))
	).

antes_de_alternativa(Texto, T) :-
	(   sub_string(Texto, Antes, _, _, "(em alternativa") -> sub_string(Texto, 0, Antes, _, T) ; T = Texto ).

% O primeiro "cenario N" do texto ou, se nao houver, o primeiro "do N" (ex.: "a shell do 35b").
cenario_citado(Texto, Num) :-
	(   sub_string(Texto, _, _, Depois, "cenario "), sub_string(Texto, _, Depois, 0, R), palavra(R, Num), comeca_por_digito(Num)
	->  true
	;   sub_string(Texto, _, _, Depois, "do "), sub_string(Texto, _, Depois, 0, R), palavra(R, Num), comeca_por_digito(Num)
	->  true
	).

comeca_por_digito(A) :- sub_atom(A, 0, 1, _, C), char_type(C, digit(_)).

% "O 34 so faz sentido depois do 4."
so_faz_sentido_depois(Texto, X, Y) :-
	sub_string(Texto, Antes, _, _, " so faz sentido depois do "),
	sub_string(Texto, 0, Antes, _, T0),
	split_string(T0, " ", "", Ps), last(Ps, XS), atom_string(X, XS),
	sub_string(Texto, _, _, Depois, " so faz sentido depois do "),
	sub_string(Texto, _, Depois, 0, R), palavra(R, Y).

cenario_json(Base, Cat, Ordem, x(G, F, Depende, Alternativas),
	     json([id=Id, ordem=Ordem, grupo=G, descricao=Desc, ficheiro=Rel, depende=Depende, alternativas=Alternativas,
		   passo=json([id=Id, factos=Factos])])) :-
	atom_concat('cenarios/', F, Rel),
	memberchk(c(Id, Desc, Rel), Cat),
	directory_file_path(Base, Rel, Caminho),
	le_termos(Caminho, Termos),
	findall(J, ( member(T, Termos), facto_do_cenario(T, Fa), facto_json(Fa, J) ), Factos).

facto_do_cenario((:- novo_facto(F)), F) :- !.
facto_do_cenario(novo_facto(F), F) :- !.
facto_do_cenario((:- _), _) :- !, fail.
facto_do_cenario(F, F).

:- multifile prolog:message//1.
prolog:message(erro_base(M)) --> [ 'gera_json.pl: ~w'-[M] ].
