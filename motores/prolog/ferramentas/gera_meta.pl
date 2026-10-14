% Gera o metaconhecimento (facto_dispara_regras/2) a partir das regras carregadas.
% Uso, depois de carregar o motor e a base de regras:
%   ?- consult('soc_regras.txt').   ?- consult('soc_suporte.txt').   ?- gera_meta.
% Escreve uma linha facto_dispara_regras(Padrao, [Regras]) por tipo de facto, para colar em soc_suporte.txt.
% Um facto dispara uma regra em dois casos:
%   1. aparece numa condicao positiva da regra (nao conta "nao X" nem as condicoes calculadas);
%   2. e lido por uma condicao calculada da regra, segundo condicao_le/2 em soc_suporte.txt (ex.: tempo/2 para depois/3).
%      Assim, um facto que chega depois (o tempo de um evento, o porto de destino de um alerta) faz reavaliar as regras.
% O padrao so distingue o nome e o numero de argumentos do facto (ex.: alerta_ids(_,_,_)).

gera_meta :-
	findall(Nome/N-ID,
		( regra ID se LHS entao _,
		  condicoes(LHS, Cs),
		  member(C, Cs),
		  chave(C, Nome/N) ),
		Diretos),
	findall(Nome/N-ID,
		( regra ID se LHS entao _,
		  condicoes(LHS, Cs),
		  member(C, Cs),
		  lido_por_calculada(C, Nome/N) ),
		Lidos),
	append(Diretos, Lidos, Pares),
	findall(K, member(K-_, Pares), Ks0),
	sort(Ks0, Ks),
	forall(member(Nome/N, Ks),
		( findall(ID, member(Nome/N-ID, Pares), IDs0),
		  sort(IDs0, IDs),
		  functor(P, Nome, N),
		  portray_clause(facto_dispara_regras(P, IDs)) )).

condicoes([A e B], Cs) :- !, condicoes([A], C1), condicoes([B], C2), append(C1, C2, Cs).
condicoes([C], [C]).

chave(avalia(F), Nome/2) :- !, functor(F, Nome, 3).
chave(nao _, _) :- !, fail.
chave(C, _) :- \+ \+ condicao_calculada(C), !, fail.
chave(C, Nome/N) :- functor(C, Nome, N).

lido_por_calculada(nao _, _) :- !, fail.
lido_por_calculada(C, Nome/N) :-
	\+ \+ condicao_calculada(C),
	functor(C, F, A), functor(C0, F, A),
	condicao_le(C0, Factos),
	member(Facto, Factos),
	functor(Facto, Nome, N).
