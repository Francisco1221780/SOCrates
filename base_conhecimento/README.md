# base_conhecimento

Fonte única da base de conhecimento do analista virtual de SOC (versão 6.1). É partilhada pelo motor Prolog, pelo Drools (através dos ficheiros gerados), pelos scripts de geração e pelos testes.

## Ficheiros

| Ficheiro / pasta | Conteúdo |
| --- | --- |
| `soc_factos.txt` | O estado inicial: os 390 factos fixos da organização (ativos, software, serviços, configuração, vulnerabilidades, rede, políticas, janelas de tempo, tipos de incidente, prioridades e perguntas ao analista). É o ficheiro que se carrega; carrega os dois seguintes. A primeira linha tem a versão da base. |
| `soc_regras.txt` | As 107 regras (R1 a R107), em 15 grupos (A a O), cada uma com uma explicação em linguagem simples. |
| `soc_suporte.txt` | O que o motor precisa e não é conhecimento do domínio: o metaconhecimento (`facto_dispara_regras/2`), as condições calculadas, o que cada uma lê (`condicao_le/2`) e a ajuda `novo_facto/1`. |
| `soc_cenarios.pl` | Liga o nome de cada cenário (`c1`, `c2`, …) ao seu ficheiro e define as sequências (`sequencia_base`, `sequencia_atualizacoes`, `sequencia_normal`). |
| `cenarios/` | Um ficheiro por cenário (57), com o `indice.txt` (a ordem e o que cada um testa). |

## Como se usa

Carrega-se o `soc_factos.txt` e corre-se o motor até não haver conclusões novas; depois injeta-se cada cenário pela ordem do `cenarios/indice.txt`, e o motor corre outra vez. Com o motor Prolog: `carrega_bc('base_conhecimento/soc_factos.txt')`, `arranca_motor` e `injeta_cenario(c2)` (ver `motores/prolog/README_motor.md`).

## O que a base faz

| Grupo | Regras | O quê |
| --- | --- | --- |
| A a D | R1 a R15 | Se a instalação está afetada pela vulnerabilidade, se o ataque lá chega, tentativas sem impacto e, na dúvida, não concluir "sem impacto" |
| E e F | R16 a R30 | Que impactos o ataque pode ter, e se resultou (evidência ligada ao ataque, tardia, ambígua ou por confirmar) |
| G | R31 a R34 | Sensor do host em baixo: pergunta ao analista pela evidência |
| H a J | R35 a R47 | Reconhecimento, políticas de acesso e movimento lateral, comunicação com C2 |
| K | R48 a R52 | Confirmação no host de um compromisso ou de uma exposição provável |
| L | R53 a R73 | Ciclo de vida: incidentes superados ou resolvidos, conclusões que passam a históricas |
| M | R74 a R88 | Gravidade, prioridade, tickets, escalada e fecho dos tickets |
| N | R89 a R92 | Falhas no catálogo de vulnerabilidades |
| O | R93 a R107 | Perguntas de fecho: cada ticket aberto tem uma pergunta ao analista que o pode fechar |

## Perguntas ao analista

O motor nunca pára à espera de uma resposta: as perguntas são factos (`pergunta_analista(Ativo, Evento, Pergunta)`), e a resposta entra como outro facto (`resposta_analista(Ativo, Evento, Pergunta, sim|nao|nao_sei)`). O texto de cada pergunta e as respostas possíveis estão nos factos (`texto_pergunta`, `opcao_resposta`). Por isso as respostas não são cenários: a interface mostra as perguntas e envia as respostas.

Cada ticket aberto tem uma pergunta, de uma de quatro famílias:

| Família | Tipos de incidente | O que a resposta regista |
| --- | --- | --- |
| Confirmação | inspeção manual, compromisso provável, exposição provável | se a evidência ou o compromisso se confirma no host (grupos G e K) |
| Recuperação | compromisso, propagação | que a máquina foi recuperada (`recuperado`) |
| Dados | inventários, política por confirmar, configuração por confirmar, versão por confirmar, catálogo incompleto | o dado que faltava (`cobertura`, `permitido`, `configuracao`) ou se a instalação está afetada (`vulneravel`, `nao_afetado`) |
| Ação | os restantes (exploração possível, movimento lateral, C2…) | que a ação pedida pelo ticket já foi feita |

"Não sei" (nas perguntas de recuperação, de inventário e de ação, "ainda não") não muda nada: o ticket continua aberto e a pergunta pode ser respondida mais tarde. Quando um ticket fecha por outra via, a sua pergunta é retirada (`pergunta_retirada`).

## Cenários

Os cenários são a telemetria que chega ao sistema (alertas, eventos dos hosts, logins, tráfego) e as atualizações do inventário e do catálogo. Os cenários com letra (`06a`, `10a`, `10b`, `35a`…) continuam o cenário do mesmo número: telemetria que chega depois (o porto de destino, a hora, o processo) ou uma atualização. O 35c e o 36b são alternativas ao 35b e ao 36a. O `cenarios/indice.txt` tem a lista e a ordem.

## Resultados de referência

Com o estado inicial e a sequência base (`sequencia_base`, sem respostas): 61 incidentes e 61 tickets, 41 abertos, cada um com a sua pergunta (41 perguntas por responder). Os resultados de cada variante estão em `motores/prolog/referencia_prolog/`.

## Regras de manutenção

- Os ficheiros desta pasta têm de ficar juntos: o `soc_factos.txt` carrega os outros a partir da sua própria pasta.
- Esta pasta é a única fonte para factos, regras e cenários. Os JSON usados pelos motores e pelo backend (`motores/drools/src/main/resources/` e `backend/socrates/src/main/resources/generated/`) geram-se daqui (`motores/prolog/ferramentas/gera_json.pl`) e não se editam à mão.
- Mudar regras implica regenerar o metaconhecimento (`motores/prolog/ferramentas/gera_meta.pl`) e atualizar o DRL (`motores/drools/src/main/resources/regras/`).
- O `soc_cenarios.pl` foi escrito a partir do `cenarios/indice.txt`; se o índice mudar, atualiza-se também.
- A versão da base está só no cabeçalho do `soc_factos.txt` ("... versao 6.1"). O motor Prolog, o `wrapper.pl` e os JSON gerados (e daí o Drools e o backend) leem-na de lá.

## Da v6 para a v6.1

- Grupo O (R93 a R107) e 76 factos novos: as perguntas de fecho dos tickets, o seu texto e as opções de resposta.
- Saíram os ficheiros de cenário que só tinham respostas do analista (antigos 3b, 7b, 10d, 17b, 19b e 29c): as respostas entram pela interface.
- Os cenários com letra passaram a começar em "a" (o antigo 6b é o 06a, o 10b e o 10c são o 10a e o 10b, e assim por diante).
- As R1 a R92 e a telemetria dos cenários não mudaram: a sequência base dá os mesmos incidentes e tickets que na v6.
