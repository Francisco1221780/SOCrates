% =====================================================================================
% MENU E EXPLICACOES - Analista virtual de SOC (CyberRangers)
% Interface manual do motor (soc_motor.pl), para demonstracoes e debug. O wrapper.pl nunca carrega este ficheiro.
%   - EXPLICACOES adaptadas: o "como" mostra uma arvore e nao repete o que ja explicou;
%     o "porque nao" sabe tratar condicoes calculadas, evita ciclos e diz quando um facto e um dado.
%   - INTERFACE (menu/0): carregar a base, injetar cenarios, responder as perguntas ao analista
%     (com "porque?" durante a pergunta), ver incidentes, tickets e acoes, registar recuperacoes.
%
% Uso rapido:
%   ?- consult('soc_menu.pl').
%   ?- menu.                                    (interface)
% ou, a mao (os comandos do soc_motor.pl e mais estes):
%   ?- carrega_bc('../../base_conhecimento/soc_factos.txt'), arranca_motor.
%   ?- injeta_cenario(c2).   ?- estado.
%   ?- como(N).   ?- como(compromisso_confirmado(web_srv_01,ev_a1)).
%   ?- whynot(ataque_dirigido(ev_e1,web_srv_04)).
% =====================================================================================

:- encoding(utf8).
:- ensure_loaded(soc_motor).

% Pausa do passo_a_passo (define_opcao(passo_a_passo, sim)): espera por ENTER depois de cada conclusao.
pausa :- get_char(_).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% CARREGAMENTO E CENARIOS (com escrita no ecra)

carrega_bc :-
	write('NOME DA BASE DE CONHECIMENTO (terminar com .)-> '),
	read(NBC),
	carrega_bc(NBC).

% Injeta de uma so vez todos os cenarios, pela ordem do indice: os de ataque (1 a 29b) e os de atividade normal
% (30 a 34), com o motor a correr entre cada um. Recomeca do estado inicial (recarrega a base) e mostra o estado no fim.
% As respostas do analista (7b, 19b, 3b, 10d, 17b, 29c) nao entram: injetam-se depois, uma a uma, porque algumas
% sao alternativas umas as outras.
injeta_todos :-
	ficheiro_base(B), !,
	carrega_bc(B),
	arranca_motor,
	sequencia_base(S), sequencia_normal(N), append(S, N, Todos),
	injeta_cenarios(Todos),
	nl, estado.
injeta_todos :-
	writeln('Primeiro carregue a base com carrega_bc(\'.../soc_factos.txt\').').

lista_cenarios :-
	forall(cenario(Id, Descr, _), format('  ~w~t~16|~w~n', [Id, Descr])).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% VISUALIZACAO DA BASE DE FACTOS

mostra_factos :-
	forall(facto(N, F), format('O facto n. ~w -> ~w~n', [N, F])).

% So os factos com um dado nome (ex.: mostra_factos(incidente)).
mostra_factos(Nome) :-
	forall(( facto(N, F), functor(F, Nome, _) ),
	       format('O facto n. ~w -> ~w~n', [N, F])).

% So os factos concluidos pelas regras.
mostra_conclusoes :-
	forall(( facto(N, F), justifica(N, ID, _) ),
	       format('O facto n. ~w -> ~w   (regra ~w)~n', [N, F, ID])).

mostra_regra(ID) :-
	regra ID se LHS entao RHS, !,
	format('regra ~w~n  SE~n', [ID]),
	escreve_lhs(LHS),
	writeln('  ENTAO'),
	forall(member(A, RHS), format('     ~w~n', [A])).
mostra_regra(ID) :-
	format('Nao ha regra ~w.~n', [ID]).

escreve_lhs([C e R]) :- !, format('     ~w~n', [C]), escreve_lhs([R]).
escreve_lhs([C])     :- format('     ~w~n', [C]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% EXPLICACOES DO TIPO "COMO?"
% como(N) ou como(Facto). Mostra a arvore de regras e factos que levou a conclusao.
% Um facto que ja foi explicado na mesma arvore nao volta a ser explicado: aparece "(ja explicado acima)".
% (no sp_exp2 a mesma sub-arvore repetia-se, e as conclusoes do fim da cadeia davam centenas de linhas)

como(F) :-
	\+ integer(F), !,
	(   facto(N, F)
	->  como(N)
	;   format('O facto ~w nao esta na memoria de trabalho. Experimente whynot(~w).~n', [F, F])
	).
como(N) :-
	ultimo_facto(Last), N > Last, !,
	writeln('Essa conclusao nao foi tirada.').
como(N) :-
	retractall(ja_explicado(_)),
	explica_facto(N, 0),
	retractall(ja_explicado(_)).

explica_facto(N, Nivel) :-
	ja_explicado(N), !,
	facto(N, F),
	legivel(F, FL),
	tab_nivel(Nivel), format('facto ~w: ~w   (ja explicado acima)~n', [N, FL]).
explica_facto(N, Nivel) :-
	justifica(N, ID, LFactos), !,
	assertz(ja_explicado(N)),
	facto(N, F), legivel(F, FL),
	tab_nivel(Nivel), format('facto ~w: ~w~n', [N, FL]),
	tab_nivel(Nivel), format('   concluido pela regra ~w, porque:~n', [ID]),
	N1 is Nivel + 1,
	forall(member(P, LFactos), explica_premissa(P, N1)).
explica_facto(N, Nivel) :-
	facto(N, F), legivel(F, FL),
	assertz(ja_explicado(N)),
	tab_nivel(Nivel), format('facto ~w: ~w   (dado)~n', [N, FL]).

explica_premissa(N, Nivel) :- integer(N), !, explica_facto(N, Nivel).
explica_premissa(nao X, Nivel) :- !,
	legivel(X, XL),
	tab_nivel(Nivel), format('nao se verifica ~w~n', [XL]).
explica_premissa(C, Nivel) :-
	legivel(C, CL),
	tab_nivel(Nivel), format('a condicao ~w verifica-se (calculada)~n', [CL]).

tab_nivel(Nivel) :- Esp is Nivel * 4, tab(Esp).

% como1(N): so o primeiro nivel (a regra e as premissas), sem descer na arvore.
como1(F) :- \+ integer(F), !, ( facto(N, F) -> como1(N) ; writeln('Esse facto nao esta na memoria de trabalho.') ).
como1(N) :-
	facto(N, F),
	(   justifica(N, ID, LFactos)
	->  format('facto ~w: ~w~n   concluido pela regra ~w, porque:~n', [N, F, ID]),
	    forall(member(P, LFactos), escreve_premissa(P))
	;   format('facto ~w: ~w   (dado)~n', [N, F])
	).

escreve_premissa(N) :- integer(N), !, facto(N, F), format('    facto ~w: ~w~n', [N, F]).
escreve_premissa(nao X) :- !, legivel(X, XL), format('    nao se verifica ~w~n', [XL]).
escreve_premissa(C) :- legivel(C, CL), format('    a condicao ~w verifica-se (calculada)~n', [CL]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% EXPLICACOES DO TIPO "PORQUE NAO?"
% Exemplo: ?- whynot(ataque_dirigido(ev_e1,web_srv_04)).
% Considera todas as regras que podiam concluir o facto e, em cada uma, as premissas que falham.
% Desce pelas premissas que outras regras poderiam concluir, ate opcao(profundidade_whynot, N).
% Diferencas para o sp_exp2: trata as condicoes calculadas (nao tem regras: diz so que sao falsas),
% nao repete um facto ja explicado, diz quando um facto e um dado de entrada, e uma premissa que falhou
% nao fica com valores inventados por uma condicao que vem a seguir (ver encontra_premissas_falsas).

whynot(Facto) :-
	retractall(ja_visto_whynot(_)),
	whynot(Facto, 1),
	retractall(ja_visto_whynot(_)).

whynot(Facto, _) :-
	\+ Facto = (nao _),
	facto(N, Facto), !,
	legivel(Facto, FL),
	format('O facto ~w nao e falso! (facto n. ~w)~n', [FL, N]).
whynot(nao Facto, Nivel) :- !,
	legivel(Facto, FL),
	formata(Nivel), format('Porque: o facto ~w e verdadeiro~n', [FL]).
whynot(Facto, Nivel) :-
	calculada(Facto), !,
	legivel(Facto, FL),
	formata(Nivel), format('Porque: a condicao calculada ~w e falsa~n', [FL]).
whynot(Facto, Nivel) :-
	ja_visto_whynot(F0), F0 =@= Facto, !,
	legivel(Facto, FL),
	formata(Nivel), format('(~w: ja explicado acima)~n', [FL]).
whynot(Facto, Nivel) :-
	\+ regra_conclui(Facto), !,
	legivel(Facto, FL),
	formata(Nivel), format('Porque: o facto ~w nao esta na base e nenhuma regra o conclui (e um dado de entrada)~n', [FL]).
whynot(_, Nivel) :-
	opcao(profundidade_whynot, Max), Nivel > Max, !,
	formata(Nivel), writeln('(... para ver mais fundo: define_opcao(profundidade_whynot, N))').
whynot(Facto, Nivel) :-
	copy_term(Facto, Copia),
	assertz(ja_visto_whynot(Copia)),
	encontra_regras_whynot(Facto, LLPF),
	LLPF \== [], !,
	whynot1(LLPF, Nivel).
whynot(Facto, Nivel) :-
	legivel(Facto, FL),
	formata(Nivel), format('As premissas de uma regra que conclui ~w verificam-se: falta correr o motor (arranca_motor).~n', [FL]).

regra_conclui(Facto) :-
	regra _ se _ entao RHS,
	member(cria_facto(Facto), RHS), !.

% As explicacoes consideram todas as regras que poderiam dar origem a conclusao relativa ao facto
encontra_regras_whynot(Facto, LLPF) :-
	findall((ID, LPF),
		( regra ID se LHS entao RHS,
		  member(cria_facto(Facto), RHS),
		  encontra_premissas_falsas(LHS, LPF),
		  LPF \== [] ),
		LLPF).

whynot1([], _).
whynot1([(ID, LPF)|LLPF], Nivel) :-
	formata(Nivel), format('Porque pela regra ~w:~n', [ID]),
	Nivel1 is Nivel + 1,
	explica_porque_nao(LPF, Nivel1),
	whynot1(LLPF, Nivel).

% Percorre o LHS por ordem. Uma condicao que se verifica fica com as variaveis instanciadas (para as seguintes);
% uma que falha vai para a lista das premissas falsas. Uma condicao que partilha variaveis com uma premissa
% que ja falhou so se testa (com \+ \+), sem instanciar nada: senao uma condicao calculada como
% anterior(EvAnterior, Ev) dava a premissa falhada um valor qualquer e a explicacao ficava errada.
encontra_premissas_falsas(LHS, LPF) :-
	lista_condicoes(LHS, Cs),
	premissas_falsas(Cs, [], LPF).

lista_condicoes([C e Resto], [C|Cs]) :- !, lista_condicoes([Resto], Cs).
lista_condicoes([C], [C]).

premissas_falsas([], _, []).
premissas_falsas([C|Cs], Falhadas, LPF) :-
	(   partilha_variaveis(C, Falhadas)
	->  ( \+ \+ verifica_condicao(C, _) -> Ok = sim ; Ok = nao )
	;   ( verifica_condicao(C, _) -> Ok = sim ; Ok = nao )
	),
	(   Ok == sim
	->  LPF = LPF1, Falhadas1 = Falhadas
	;   LPF = [C|LPF1], Falhadas1 = [C|Falhadas]
	),
	premissas_falsas(Cs, Falhadas1, LPF1).

partilha_variaveis(C, Termos) :-
	term_variables(C, V1),
	term_variables(Termos, V2),
	member(X, V1), member(Y, V2), X == Y, !.

explica_porque_nao([], _).
explica_porque_nao([nao avalia(X)|LPF], Nivel) :- !,
	legivel(X, XL),
	formata(Nivel), format('A condicao nao ~w e falsa~n', [XL]),
	explica_porque_nao(LPF, Nivel).
explica_porque_nao([avalia(X)|LPF], Nivel) :- !,
	legivel(X, XL),
	formata(Nivel), format('A condicao ~w e falsa~n', [XL]),
	explica_porque_nao(LPF, Nivel).
explica_porque_nao([nao X|LPF], Nivel) :- !,
	legivel(X, XL),
	formata(Nivel), format('A premissa nao ~w e falsa~n', [XL]),
	Nivel1 is Nivel + 1,
	( facto(N, X) -> true ; N = '?' ),
	formata(Nivel1), format('Porque: o facto ~w e verdadeiro (facto n. ~w)~n', [XL, N]),
	explica_porque_nao(LPF, Nivel).
explica_porque_nao([C|LPF], Nivel) :-
	calculada(C), !,
	legivel(C, CL),
	formata(Nivel), format('A condicao calculada ~w e falsa~n', [CL]),
	explica_porque_nao(LPF, Nivel).
explica_porque_nao([P|LPF], Nivel) :-
	legivel(P, PL),
	formata(Nivel), format('A premissa ~w e falsa~n', [PL]),
	Nivel1 is Nivel + 1,
	whynot(P, Nivel1),
	explica_porque_nao(LPF, Nivel).

formata(Nivel) :- Esp is (Nivel - 1) * 5, tab(Esp).


estado :-
	findall(N, facto(N,_), LF), length(LF, NF),
	findall(N, justifica(N,_,_), LC), length(LC, NC),
	findall(I, facto(_, incidente(I,_,_,_)), LI), length(LI, NI),
	findall(I, facto(_, ticket(I,_,_,_,_,_)), LT), length(LT, NT),
	findall(I, ticket_aberto(I), LA), length(LA, NA),
	perguntas_pendentes(LP), length(LP, NP),
	NFech is NT - NA,
	format('~w factos (~w concluidos pelo motor) | ~w incidentes | ~w tickets: ~w abertos, ~w fechados | ~w perguntas por responder~n',
	       [NF, NC, NI, NT, NA, NFech, NP]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% INTERFACE (menu de texto). As respostas escrevem-se como termos Prolog, terminadas com ponto.

menu :-
	repeat,
	nl, writeln('========== ANALISTA VIRTUAL DE SOC =========='),
	( bc_carregada -> estado ; writeln('(nenhuma base carregada)') ),
	writeln(' 1. Carregar a base de conhecimento'),
	writeln(' 2. Correr o motor'),
	writeln(' 3. Injetar um cenario'),
	writeln(' 4. Introduzir um facto (telemetria)'),
	writeln(' 5. Responder as perguntas ao analista'),
	writeln(' 6. Incidentes e tickets'),
	writeln(' 7. Acoes recomendadas'),
	writeln(' 8. Registar a recuperacao de uma maquina'),
	writeln(' 9. Explicar como se concluiu um facto (como?)'),
	writeln('10. Explicar porque nao se concluiu um facto (porque nao?)'),
	writeln('11. Mostrar factos'),
	writeln('12. Opcoes do motor'),
	writeln('13. Injetar todos os cenarios de uma vez'),
	writeln(' 0. Sair'),
	le('Opcao', Op),
	( catch(executa(Op), E, print_message(error, E)) -> true ; true ),
	Op == 0, !.

executa(0) :- !.
executa(1) :- !,
	le('Ficheiro da base (ex.: \'../../base_conhecimento/soc_factos.txt\')', F),
	carrega_bc(F).
executa(Op) :-
	\+ bc_carregada, Op \== 12, !,
	writeln('Primeiro carregue a base de conhecimento (opcao 1).').
executa(2) :- !, arranca_motor.
executa(3) :- !,
	(   current_predicate(cenario/3)
	->  lista_cenarios,
	    writeln('  (ou o nome de um ficheiro, ex.: \'cenarios/cenario_30.txt\')'),
	    le('Cenario (ex.: c2), ou uma lista (ex.: [c1,c2,c3])', C),
	    ( is_list(C) -> injeta_cenarios(C) ; injeta_cenario(C) )
	;   writeln('Nao ha cenarios carregados (falta o soc_cenarios.pl ao lado da base).')
	).
executa(4) :- !,
	writeln('Escreva um facto ou uma lista de factos, ex.: [alerta_ids(ev_1,web_srv_02,sig_apache_path_traversal), tempo(ev_1,900)]'),
	le('Facto(s)', F),
	( is_list(F) -> introduz_factos(F) ; introduz_facto(F) ),
	arranca_motor.
executa(5) :- !, responde_perguntas.
executa(6) :- !, mostra_incidentes.
executa(7) :- !, mostra_acoes.
executa(8) :- !,
	writeln('Regista que uma maquina comprometida foi recuperada: um login a partir dela deixa de contar como vindo de uma maquina comprometida.'),
	findall(A-E, ( facto(_, compromisso_confirmado(A, E)), \+ facto(_, recuperado(A, E)) ), L),
	( L == [] -> writeln('Nao ha compromissos confirmados por recuperar.')
	; writeln('Compromissos confirmados por recuperar:'), forall(member(A-E, L), format('   ~w (evento ~w)~n', [A, E])) ),
	le('Ativo', A), le('Evento do compromisso', E),
	introduz_facto(recuperado(A, E)),
	arranca_motor.
executa(9) :- !,
	le('Numero do facto ou o proprio facto', F),
	como(F).
executa(10) :- !,
	le('Facto (ex.: compromisso_confirmado(web_srv_04,ev_e1))', F),
	whynot(F).
executa(11) :- !,
	writeln('  todos. | concluidos. | o nome de um facto, ex.: incidente.'),
	le('Que factos', Q),
	(   Q == todos -> mostra_factos
	;   Q == concluidos -> mostra_conclusoes
	;   mostra_factos(Q)
	).
executa(12) :- !,
	forall(opcao(N, V), format('   ~w = ~w~n', [N, V])),
	le('Opcao a mudar (ou nada.)', N),
	(   N == nada -> true
	;   opcao(N, _) -> le('Valor (sim. / nao.)', V), define_opcao(N, V)
	;   writeln('Opcao desconhecida.')
	).
executa(13) :- !, injeta_todos.
executa(_) :-
	writeln('Opcao invalida.').

% Le um termo, com uma mensagem. Volta a pedir se houver um erro de sintaxe; fim de ficheiro conta como 0.
le(Msg, X) :-
	format('~w: ', [Msg]), flush_output,
	catch(read(X0), _, (writeln('Erro de sintaxe: escreva um termo Prolog terminado com ponto.'), fail)), !,
	( X0 == end_of_file -> X = 0 ; X = X0 ).
le(Msg, X) :- le(Msg, X).


% --- Perguntas ao analista ---------------------------------------------------------------

responde_perguntas :-
	perguntas_pendentes(L),
	(   L == []
	->  writeln('Nao ha perguntas por responder.')
	;   writeln('Perguntas por responder:'),
	    escreve_perguntas(L, 1),
	    length(L, Max),
	    le('Numero da pergunta (0 para voltar)', I),
	    (   integer(I), I >= 1, I =< Max
	    ->  nth1(I, L, N-p(A, E, P)),
		pergunta(N, A, E, P),
		responde_perguntas
	    ;   true
	    )
	).

escreve_perguntas([], _).
escreve_perguntas([_-p(A,E,P)|R], I) :-
	(   facto(_, resposta_analista(A,E,P,nao_sei)) -> Nota = '  (respondida "nao sei")' ; Nota = '' ),
	format('  ~w. ~w sobre o evento ~w: ~w~w~n', [I, A, E, P, Nota]),
	I1 is I + 1,
	escreve_perguntas(R, I1).

% Faz a pergunta N. O analista pode responder, ou perguntar "porque?" (estilo MYCIN):
% o sistema mostra a regra que gerou a pergunta e as premissas que a levaram a ser feita.
pergunta(N, Ativo, Ev, P) :-
	nl,
	format('Ativo: ~w   Evento: ~w~n', [Ativo, Ev]),
	( facto(_, texto_pergunta(P, Texto)) -> writeln(Texto) ; format('~w?~n', [P]) ),
	findall(R-T, facto(_, opcao_resposta(P, R, T)), Opcoes0),
	( Opcoes0 == [] -> Opcoes = [sim-'Sim', nao-'Nao', nao_sei-'Nao sei'] ; Opcoes = Opcoes0 ),
	forall(member(R-T, Opcoes), format('   ~w. ~w~n', [R, T])),
	writeln('   porque. Porque e que me esta a perguntar isto?'),
	le('Resposta', Resp),
	(   Resp == porque
	->  porque_pergunta(N, Ativo, P),
	    pergunta(N, Ativo, Ev, P)
	;   memberchk(Resp-_, Opcoes)
	->  introduz_facto(resposta_analista(Ativo, Ev, P, Resp)),
	    arranca_motor
	;   writeln('Resposta invalida.'),
	    pergunta(N, Ativo, Ev, P)
	).

porque_pergunta(N, Ativo, P) :-
	nl,
	(   justifica(N, ID, _)
	->  format('Pergunto porque a regra ~w pediu esta verificacao em ~w, pelo seguinte:~n', [ID, Ativo]),
	    como1(N),
	    escreve_regras_que_usam_resposta(P)
	;   writeln('Esta pergunta foi introduzida a mao.')
	),
	nl.

% As regras que usam a resposta a esta pergunta (as que tem resposta_analista(_,_,P,_) no SE).
escreve_regras_que_usam_resposta(P) :-
	findall(ID, ( regra ID se LHS entao _, facto_esta_numa_condicao(resposta_analista(_,_,P,_), LHS) ), IDs0),
	sort(IDs0, IDs),
	( IDs == [] -> true ; format('A resposta vai ser usada pelas regras ~w.~n', [IDs]) ).


% --- Incidentes, tickets e acoes ---------------------------------------------------------

mostra_incidentes :-
	tickets_abertos(LA),
	length(LA, NA),
	format('~nTICKETS ABERTOS (~w), por prioridade:~n', [NA]),
	forall(member(P-t(Inc, Ativo, Acao, G, Imp), LA),
	       ( Inc = inc(Tipo, Ev),
		 format('  [~w] ~w  ativo: ~w  evento: ~w  gravidade: ~w~n', [P, Tipo, Ativo, Ev, G]),
		 format('       acao: ~w   ativos impactados: ~w~n', [Acao, Imp]),
		 forall(facto(_, motivo(Inc, M)), format('       motivo: ~w~n', [M])),
		 ( facto(_, significado(Tipo, S)) -> format('       (~w)~n', [S]) ; true ) )),
	tickets_fechados(LF),
	length(LF, NF),
	format('~nTICKETS FECHADOS (~w):~n', [NF]),
	forall(member(t(inc(Tipo, Ev), Ativo, Motivo), LF),
	       format('  ~w  ativo: ~w  evento: ~w  -> ~w~n', [Tipo, Ativo, Ev, Motivo])),
	findall(Inc, ( facto(_, incidente(Inc,_,_,_)), \+ facto(_, ticket(Inc,_,_,_,_,_)) ), SemTicket),
	(   SemTicket == [] -> true
	;   format('~nINCIDENTES SEM TICKET: ~w~n', [SemTicket])
	).

mostra_acoes :-
	findall(Ativo, facto(_, acao(Ativo, _)), L0),
	sort(L0, Ativos),
	(   Ativos == []
	->  writeln('Nao ha acoes recomendadas.')
	;   forall(member(A, Ativos),
		   ( format('~n~w:~n', [A]),
		     forall(facto(N, acao(A, Ac)),
			    ( format('   ~w   (facto ~w)~n', [Ac, N]),
			      functor(Ac, Nome, _),
			      ( facto(_, significado(Nome, S)) -> format('      ~w~n', [S]) ; true ) )) ))
	).
