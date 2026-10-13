% CENARIOS DE TESTE - indice para o motor (soc_motor.pl).
% Os factos de cada cenario estao nos ficheiros da pasta cenarios/ (ao lado do soc_factos.txt); este ficheiro so diz
% que ficheiro corresponde a cada cenario, para se poder injetar pelo nome e correr as sequencias de validacao.
% Assim os factos estao num so sitio: quem mudar um cenario muda so o ficheiro.
% Carrega-se sozinho com a base (carrega_bc) se estiver na mesma pasta que o soc_factos.txt.
%
%   cenario(Id, Descricao, Ficheiro)   Ficheiro e relativo a pasta do soc_factos.txt
%
% Injetar:  ?- injeta_cenario(c2).   ou   ?- injeta_cenarios([c1,c2,c3]).   (ou a opcao 3 do menu)
% A ordem importa: os cenarios correm-se pela ordem do cenarios/indice.txt (ex.: o 3 depende do 2, o 11 do 8).
% Nos cenarios 7b e 19b escolhe-se UMA resposta (ex.: c7b_sim). O 29c usa-se em vez do 29b.
% Os cenarios 30 a 34 (atividade normal) nao devem concluir nada e podem correr em qualquer altura (o 34 depois do 4).

% Telemetria
cenario(c1,   'Tentativa sem impacto (versao corrigida)',                         'cenarios/cenario_01.txt').
cenario(c2,   'Execucao remota confirmada; ataque dirigido de origem reincidente', 'cenarios/cenario_02.txt').
cenario(c3,   'Movimento lateral a partir de uma maquina comprometida',            'cenarios/cenario_03.txt').
cenario(c4,   'Correcao aplicada sem mudar a versao (backport)',                   'cenarios/cenario_04.txt').
cenario(c5,   'Ruido: o ativo nao tem o programa atacado',                         'cenarios/cenario_05.txt').
cenario(c6,   'Versao desconhecida',                                               'cenarios/cenario_06.txt').
cenario(c7,   'Sensor do host em baixo: pergunta ao analista',                     'cenarios/cenario_07.txt').
cenario(c8,   'Log4Shell com evidencia tardia',                                    'cenarios/cenario_08.txt').
cenario(c9,   'SQL injection confirmada: exposicao de dados',                      'cenarios/cenario_09.txt').
cenario(c10,  'Reconhecimento',                                                    'cenarios/cenario_10.txt').
cenario(c10b, 'Acesso depois do reconhecimento',                                   'cenarios/cenario_10b.txt').
cenario(c10c, 'C2 depois de um acesso suspeito',                                   'cenarios/cenario_10c.txt').
cenario(c11,  'C2 sem ligacao conhecida ao ataque',                                'cenarios/cenario_11.txt').
cenario(c11b, 'A ligacao aparece: C2 feito pelo programa atacado',                 'cenarios/cenario_11b.txt').
cenario(c12,  'Shell de outro programa: impacto ambiguo',                          'cenarios/cenario_12.txt').
cenario(c13,  'Inventario incompleto',                                             'cenarios/cenario_13.txt').
cenario(c14,  'C2 nao corroborado',                                                'cenarios/cenario_14.txt').
cenario(c15,  'Ramo corrigido a parte',                                            'cenarios/cenario_15.txt').
cenario(c16,  'Duas instalacoes do mesmo programa',                                'cenarios/cenario_16.txt').
cenario(c17,  'C2 com evidencia fraca',                                            'cenarios/cenario_17.txt').
cenario(c18,  'Movimento lateral a partir de uma maquina nao comprometida',        'cenarios/cenario_18.txt').
cenario(c19,  'Sensor em baixo numa SQL injection',                                'cenarios/cenario_19.txt').
cenario(c20,  'Evidencia sem telemetria de processo',                              'cenarios/cenario_20.txt').
cenario(c20b, 'A telemetria do processo chega depois',                             'cenarios/cenario_20b.txt').
cenario(c21,  'Catalogo sem intervalos de versoes',                                'cenarios/cenario_21.txt').
cenario(c22,  'Porto de destino conhecido',                                        'cenarios/cenario_22.txt').
cenario(c23,  'Processo nao classificado',                                         'cenarios/cenario_23.txt').
cenario(c24,  'CGI desligado: execucao remota excluida',                           'cenarios/cenario_24.txt').
cenario(c24b, 'Shell num servidor sem CGI',                                        'cenarios/cenario_24b.txt').
cenario(c25,  'Configuracao desconhecida',                                         'cenarios/cenario_25.txt').
cenario(c25b, 'A configuracao chega depois',                                       'cenarios/cenario_25b.txt').
cenario(c26,  'C2 numa maquina ja comprometida',                                   'cenarios/cenario_26.txt').
cenario(c27,  'Versao num formato desconhecido',                                   'cenarios/cenario_27.txt').
cenario(c28,  'Movimento lateral horas depois do compromisso',                     'cenarios/cenario_28.txt').
cenario(c29,  'SQL injection sem telemetria de processo',                          'cenarios/cenario_29.txt').
cenario(c29b, 'A telemetria do processo chega depois (em alternativa ao 29c)',     'cenarios/cenario_29b.txt').

% Atividade normal (controlos negativos: o sistema nao deve concluir nada)
cenario(c30,  'Normal: shell de um administrador numa maquina que nao foi atacada', 'cenarios/cenario_30.txt').
cenario(c31,  'Normal: trafego para um endereco que nao e um C2 conhecido',          'cenarios/cenario_31.txt').
cenario(c32,  'Normal: sondagem abaixo dos limiares do reconhecimento',              'cenarios/cenario_32.txt').
cenario(c33,  'Normal: login que a politica permite',                                'cenarios/cenario_33.txt').
cenario(c34,  'Normal: shell num servidor que so teve uma tentativa sem impacto (depois do 4)', 'cenarios/cenario_34.txt').

% Respostas do analista (depois do cenario a que respondem)
cenario(c7b_sim,      'Analista: ha uma shell em web_srv_04 (cenario 7)',              'cenarios/cenario_07b_sim.txt').
cenario(c7b_nao,      'Analista: nao ha shell em web_srv_04 (cenario 7)',              'cenarios/cenario_07b_nao.txt').
cenario(c7b_nao_sei,  'Analista: nao sabe se ha shell em web_srv_04 (cenario 7)',      'cenarios/cenario_07b_nao_sei.txt').
cenario(c19b_sim,     'Analista: ha resposta volumosa em app_srv_03 (cenario 19)',     'cenarios/cenario_19b_sim.txt').
cenario(c19b_nao,     'Analista: nao ha resposta volumosa em app_srv_03 (cenario 19)', 'cenarios/cenario_19b_nao.txt').
cenario(c19b_nao_sei, 'Analista: nao sabe se ha resposta volumosa (cenario 19)',       'cenarios/cenario_19b_nao_sei.txt').
cenario(c3b,  'Analista confirma no host o compromisso da BD: propagacao',             'cenarios/cenario_03b.txt').
cenario(c10d, 'Analista descarta o compromisso provavel de web_srv_02',                'cenarios/cenario_10d.txt').
cenario(c17b, 'Analista ainda nao conseguiu verificar web_srv_05',                     'cenarios/cenario_17b.txt').
cenario(c29c, 'Analista confirma o cenario 29 (em alternativa ao 29b)',                'cenarios/cenario_29c.txt').

% Sequencias usadas por injeta_todos (ordem do cenarios/indice.txt)
% Cenarios 1 a 29b, sem respostas: 931 factos, 50 incidentes, 50 tickets (14 fechados, 36 abertos) - AC8
sequencia_base([c1,c2,c3,c4,c5,c6,c7,c8,c9,c10,c10b,c10c,c11,c11b,c12,c13,c14,c15,c16,c17,c18,c19,
		c20,c20b,c21,c22,c23,c24,c24b,c25,c25b,c26,c27,c28,c29,c29b]).
% Atividade normal: nao acrescentam conclusoes - AC9
sequencia_normal([c30,c31,c32,c33,c34]).