% CENARIOS DE TESTE - indice para o motor (soc_motor.pl). Base de conhecimento versao 6.
% Os factos de cada cenario estao nos ficheiros da pasta cenarios/ (ao lado do soc_factos.txt); este ficheiro so diz
% que ficheiro corresponde a cada cenario, para se poder injetar pelo nome e correr as sequencias de validacao.
% Assim os factos estao num so sitio: quem mudar um cenario muda so o ficheiro.
% Carrega-se sozinho com a base (carrega_bc) se estiver na mesma pasta que o soc_factos.txt.
%
%   cenario(Id, Descricao, Ficheiro)   Ficheiro e relativo a pasta do soc_factos.txt
%
% Injetar:  ?- injeta_cenario(c2).   ou   ?- injeta_cenarios([c1,c2,c3]).   (ou a opcao 3 do menu)
% A ordem importa: os cenarios correm-se pela ordem do cenarios/indice.txt (ex.: o 3 depende do 2, o 11 do 8).
% Nos cenarios 7b e 19b escolhe-se UMA resposta (ex.: c7b_sim). O 29c, o 35d e o 36c usam-se em vez do 29b, 35c e 36b.
% Os cenarios 30 a 34 (atividade normal) nao devem concluir nada; carregam-se no fim (o 34 so depois do 4).
% Gerado a partir do cenarios/indice.txt: se o indice mudar, atualizar este ficheiro.

% Telemetria (ordem do indice)
cenario(c1, 'tentativa sem impacto (versao corrigida)', 'cenarios/cenario_01.txt').
cenario(c2, 'execucao remota confirmada; ataque dirigido de uma origem reincidente', 'cenarios/cenario_02.txt').
cenario(c3, 'movimento lateral a partir de uma maquina comprometida', 'cenarios/cenario_03.txt').
cenario(c4, 'correcao aplicada sem mudar a versao (backport)', 'cenarios/cenario_04.txt').
cenario(c5, 'ruido: o ativo nao tem o programa atacado', 'cenarios/cenario_05.txt').
cenario(c6, 'versao desconhecida', 'cenarios/cenario_06.txt').
cenario(c7, 'sensor do host em baixo: pergunta ao analista', 'cenarios/cenario_07.txt').
cenario(c8, 'Log4Shell com evidencia tardia', 'cenarios/cenario_08.txt').
cenario(c9, 'SQL injection confirmada: exposicao de dados', 'cenarios/cenario_09.txt').
cenario(c10, 'reconhecimento', 'cenarios/cenario_10.txt').
cenario(c10b, 'acesso depois do reconhecimento', 'cenarios/cenario_10b.txt').
cenario(c10c, 'C2 depois de um acesso suspeito', 'cenarios/cenario_10c.txt').
cenario(c11, 'C2 sem ligacao conhecida ao ataque', 'cenarios/cenario_11.txt').
cenario(c11b, 'a ligacao aparece: C2 feito pelo processo atacado', 'cenarios/cenario_11b.txt').
cenario(c12, 'shell de outro programa: impacto ambiguo', 'cenarios/cenario_12.txt').
cenario(c13, 'inventario incompleto', 'cenarios/cenario_13.txt').
cenario(c14, 'C2 nao corroborado', 'cenarios/cenario_14.txt').
cenario(c15, 'ramo corrigido a parte', 'cenarios/cenario_15.txt').
cenario(c16, 'duas instalacoes do mesmo programa, so a corrigida ao alcance', 'cenarios/cenario_16.txt').
cenario(c17, 'C2 com evidencia fraca', 'cenarios/cenario_17.txt').
cenario(c18, 'movimento lateral a partir de uma maquina nao comprometida', 'cenarios/cenario_18.txt').
cenario(c19, 'sensor em baixo numa SQL injection', 'cenarios/cenario_19.txt').
cenario(c20, 'evidencia sem telemetria de processo', 'cenarios/cenario_20.txt').
cenario(c20b, 'a telemetria do processo chega depois', 'cenarios/cenario_20b.txt').
cenario(c21, 'catalogo sem intervalos de versoes', 'cenarios/cenario_21.txt').
cenario(c22, 'porto de destino conhecido', 'cenarios/cenario_22.txt').
cenario(c23, 'processo nao classificado', 'cenarios/cenario_23.txt').
cenario(c24, 'CGI desligado: execucao remota excluida', 'cenarios/cenario_24.txt').
cenario(c24b, 'shell num servidor sem CGI', 'cenarios/cenario_24b.txt').
cenario(c25, 'configuracao desconhecida', 'cenarios/cenario_25.txt').
cenario(c25b, 'a configuracao chega depois', 'cenarios/cenario_25b.txt').
cenario(c26, 'C2 numa maquina ja comprometida', 'cenarios/cenario_26.txt').
cenario(c27, 'versao num formato desconhecido', 'cenarios/cenario_27.txt').
cenario(c28, 'movimento lateral horas depois do compromisso', 'cenarios/cenario_28.txt').
cenario(c29, 'SQL injection sem telemetria de processo: exposicao provavel', 'cenarios/cenario_29.txt').
cenario(c29b, 'a telemetria do processo chega depois (em alternativa ao 29c)', 'cenarios/cenario_29b.txt').
cenario(c35, 'duas instalacoes ao alcance, uma corrigida e outra vulneravel', 'cenarios/cenario_35.txt').
cenario(c35b, 'shell de um httpd sem se saber de que instancia', 'cenarios/cenario_35b.txt').
cenario(c35c, 'a instancia e a atacada (em alternativa ao 35d)', 'cenarios/cenario_35c.txt').
cenario(c36, 'acesso com a politica incompleta: politica por confirmar', 'cenarios/cenario_36.txt').
cenario(c36b, 'a politica fica completa: violacao (em alternativa ao 36c)', 'cenarios/cenario_36b.txt').
cenario(c37, 'ataque sem porto de destino', 'cenarios/cenario_37.txt').
cenario(c37b, 'o porto de destino chega depois', 'cenarios/cenario_37b.txt').
cenario(c38, 'shell sem hora', 'cenarios/cenario_38.txt').
cenario(c38b, 'a hora chega depois', 'cenarios/cenario_38b.txt').
cenario(c39, 'maquina recuperada', 'cenarios/cenario_39.txt').

% Atualizacoes do inventario e do catalogo (depois do cenario 39)
cenario(c6b, 'versao de web_srv_05 (corrigida): fecha a duvida do cenario 6 e o provavel do cenario 17', 'cenarios/cenario_06b.txt').
cenario(c13b, 'inventario de web_srv_07: fecha os dois incidentes de inventario do cenario 13', 'cenarios/cenario_13b.txt').
cenario(c21b, 'intervalos da falha de upload: fecha o catalogo incompleto do cenario 21', 'cenarios/cenario_21b.txt').
cenario(c27b, 'versao valida de web_srv_11: fecha a duvida do cenario 27', 'cenarios/cenario_27b.txt').

% Atividade normal (controlos negativos: o sistema nao deve concluir nada)
cenario(c30, 'Normal: shell de um administrador numa maquina que nao foi atacada', 'cenarios/cenario_30.txt').
cenario(c31, 'Normal: trafego de saida para um endereco que nao e um C2 conhecido', 'cenarios/cenario_31.txt').
cenario(c32, 'Normal: sondagem abaixo dos limiares do reconhecimento', 'cenarios/cenario_32.txt').
cenario(c33, 'Normal: login que a politica permite', 'cenarios/cenario_33.txt').
cenario(c34, 'Normal: shell num servidor que so teve uma tentativa sem impacto', 'cenarios/cenario_34.txt').

% Respostas do analista e alternativas (depois do cenario a que respondem)
cenario(c7b_sim, 'Analista: resposta "sim" a pergunta do cenario 7', 'cenarios/cenario_07b_sim.txt').
cenario(c7b_nao, 'Analista: resposta "nao" a pergunta do cenario 7', 'cenarios/cenario_07b_nao.txt').
cenario(c7b_nao_sei, 'Analista: resposta "nao sei" a pergunta do cenario 7', 'cenarios/cenario_07b_nao_sei.txt').
cenario(c19b_sim, 'Analista: resposta "sim" a pergunta do cenario 19', 'cenarios/cenario_19b_sim.txt').
cenario(c19b_nao, 'Analista: resposta "nao" a pergunta do cenario 19', 'cenarios/cenario_19b_nao.txt').
cenario(c19b_nao_sei, 'Analista: resposta "nao sei" a pergunta do cenario 19', 'cenarios/cenario_19b_nao_sei.txt').
cenario(c3b, 'o analista confirma o compromisso do cenario 3: propagacao', 'cenarios/cenario_03b.txt').
cenario(c10d, 'o analista descarta o compromisso provavel do cenario 10c', 'cenarios/cenario_10d.txt').
cenario(c17b, 'o analista ainda nao conseguiu verificar o cenario 17', 'cenarios/cenario_17b.txt').
cenario(c29c, 'o analista confirma a exposicao do cenario 29 (em alternativa ao 29b)', 'cenarios/cenario_29c.txt').
cenario(c35d, 'a shell do 35b veio da outra instancia (em alternativa ao 35c): impacto ambiguo', 'cenarios/cenario_35d.txt').
cenario(c36c, 'a politica autoriza o acesso do cenario 36 (em alternativa ao 36b)', 'cenarios/cenario_36c.txt').

% Sequencias (ordem do cenarios/indice.txt). Alternativas: entra a primeira (29b, 35c, 36b); 29c, 35d e 36c ficam de fora.
sequencia_base([c1,c2,c3,c4,c5,c6,c7,c8,c9,c10,c10b,c10c,c11,c11b,c12,c13,c14,c15,c16,c17,c18,c19,c20,c20b,c21,c22,c23,c24,c24b,c25,c25b,c26,c27,c28,c29,c29b,c35,c35b,c35c,c36,c36b,c37,c37b,c38,c38b,c39]).
sequencia_atualizacoes([c6b,c13b,c21b,c27b]).
sequencia_normal([c30,c31,c32,c33,c34]).
