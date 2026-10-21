# F0008 — Base v6.1 (perguntas de fecho dos tickets) nos dois motores

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 21/10) |
| Branch | `feature/base-v6-1-perguntas-fecho`, sobre `develop` @ `3d991ed` |
| Estado | Plano (por aprovar) |
| Âmbito | Base v6.1 em `base_conhecimento/`, wrapper e motor Prolog, ferramentas e referências, motor Drools, testes dos dois motores e do backend. Sem UI e sem código do backend |
| Verificação | `swipl -g run_tests -t halt motores/prolog/testes/testes.pl`, `./mvnw -B -pl motores/drools verify`, `./mvnw -B -pl backend/socrates -am verify`, e o `--verificar` do `gera_json.pl`, do `gera_referencias.pl` e do `gera_meta.pl` |

## Problema

A base v6 só faz perguntas ao analista em três tipos de incidente (inspeção manual, compromisso provável, exposição provável). Os outros tickets só fecham com um facto enviado à mão (`recuperado`, `versao`, `cobertura`...) ou por arrastamento, e a UI só sabe responder a perguntas: na sequência base ficam 41 tickets abertos e só 9 perguntas. A v6.1 (feita na base de conhecimento do curso, verificada no `soc_motor.pl` e num verificador independente) dá a cada ticket aberto uma pergunta de fecho: um grupo novo O com as regras R93 a R107, 76 factos novos (390 no total) e sem os ficheiros de cenário que só tinham respostas do analista (3b, 7b, 10d, 17b, 19b, 29c), porque a pergunta já aparece e responde-se pela interface.

Os ficheiros da v6.1 já estão copiados em `base_conhecimento/` na cópia de trabalho (por fazer commit). Falta tudo o que depende deles.

### Evidência

- `base_conhecimento/soc_regras.txt`, `soc_factos.txt`, `soc_suporte.txt` e `cenarios/` (por commit) são iguais aos da v6.1; o cabeçalho diz "versao 6.1".
- `motores/prolog/wrapper.pl:47` tem `versao_kb('6')` escrito à mão; `motores/drools/.../MotorDrools.java:53` tem `VERSAO_KB = "6"`. O `gera_json.pl` (linhas 131-140) já lê a versão do cabeçalho do `soc_factos.txt` e põe-na nos JSON gerados; o backend (`BaseCarregada`) já a lê do `cenarios.json`.
- `base_conhecimento/soc_cenarios.pl` ainda lista `c3b`, `c7b_*`, `c10d`, `c17b`, `c19b_*` e `c29c`, cujos ficheiros saíram.
- Usam esses cenários: `motores/prolog/testes/testes.pl` (`ac12_resposta`, `ac11_prefixo`, `ac10_determinismo`), `ferramentas/gera_referencias.pl` e `motores/drools/ferramentas/gera_referencia_agn.pl` (cinco variantes cada), `ParidadeTest`, `ConsolaDroolsTest`, `MotorDroolsTest` e os testes do backend com `versaoKB` "6".
- `wrapper.pl` (`perguntas_json`) e `Resposta.java` escondem uma pergunta com qualquer resposta, incluindo `nao_sei`; o `pergunta_pendente/4` do `soc_motor.pl` deixa-a pendente. Na v6.1 o "ainda não" das perguntas de fecho é o `nao_sei`, por isso pela API o ticket ficaria sem forma de fechar.
- A cópia trouxe para `base_conhecimento/` um `README.md` que não é o do repositório (é o rascunho do curso, com `../v6/` e "este README não é entregue"), apagou `cenarios/README.md` e `cenarios/.gitkeep`, e juntou um `gera_meta.pl` que não é igual ao de `motores/prolog/ferramentas/`.

### Inventário

- **A.** Base: `soc_cenarios.pl` sem os cenários de resposta; `README.md` da base entregável; `cenarios/README.md` reposto e atualizado; `base_conhecimento/gera_meta.pl` retirado (o do repositório está em `motores/prolog/ferramentas/`).
- **B.** Versão da base lida do cabeçalho do `soc_factos.txt`, sem valor escrito à mão: no Prolog (motor e wrapper) e no Drools.
- **C.** `nao_sei` deixa a pergunta pendente no wrapper e no Drools, como no `soc_motor.pl`.
- **D.** Ferramentas Prolog: variantes com respostas injetadas como factos; variantes novas para as perguntas de fecho; JSON e referências regenerados.
- **E.** Testes Prolog (`testes.pl`).
- **F.** Drools: R93 a R107 num ficheiro DRL novo; testes do módulo e referências do nível A-G+N.
- **G.** Testes do backend: `versaoKB` "6.1" e respostas como factos no passo.
- **H.** Documentação: READMEs dos motores e das ferramentas, resultados de referência, este registo e o índice.

### Escala

15 regras novas em DRL; cerca de 10 ficheiros de código ou ferramentas; cerca de 12 ficheiros de teste; 12 JSON de referência regenerados (mais 2 novos); 6 READMEs.

## Decisões

1. **A versão vem do cabeçalho do `soc_factos.txt`, como já faz o `gera_json.pl`.** No Prolog, o `soc_motor.pl` ganha `versao_base/1`, que lê a primeira linha do `soc_factos.txt` carregado pelo `carrega_bc` (o mesmo critério do `gera_json.pl`: a palavra a seguir a "versao "); o wrapper usa-a em vez de `versao_kb('6')`. No Drools, `VERSAO_KB` deixa de ser uma constante escrita à mão e passa a ser lido do `factos_iniciais.json` do classpath (que o `gera_json.pl` gera do mesmo cabeçalho). Mudar a versão passa a ser só mudar o cabeçalho e regenerar.
2. **`nao_sei` não fecha a pergunta**, nos dois motores: uma pergunta continua pendente se não estiver retirada e não tiver uma resposta diferente de `nao_sei` (o critério do `pergunta_pendente/4`). Uma segunda resposta entra como outro passo.
3. **As respostas do analista nas variantes e nos testes entram como passos com o facto `resposta_analista`**, com o mesmo `id` que o ficheiro tinha (`cenario_07b_sim`...), para as referências continuarem comparáveis. As variantes mantêm os nomes (`r7b_nao`...).
4. **Duas variantes novas de referência**: `fecho_sim` (sequência base e "sim" às perguntas de fecho usadas na verificação da v6.1: `maquina_recuperada`, inventários, `acesso_autorizado`, `configuracao_ativa`, `versao_afetada`, `catalogo_afetado`, `vulnerabilidade_tratada`, `maquinas_recuperadas` depois do "sim" a `compromisso_no_host` do cenário 3) e `fecho_nao` ("não" e "não sei" às mesmas, onde existem).
5. **As regras Drools ficam em `O_fecho_tickets.drl`**, pela convenção de um ficheiro por grupo.
6. **README da base entregável**: descreve a base como ela é (ficheiros, como se carrega, grupos de regras A a O, cenários, perguntas ao analista), sem o histórico de rascunho; o que mudou da v6 para a v6.1 fica numa secção curta. É o `README.md` de `base_conhecimento/`.

### Alternativas rejeitadas

- **Um facto `versao_base('6.1')` em `soc_suporte.txt`.** Seria uma segunda fonte da versão, ao lado do cabeçalho que o `gera_json.pl` já usa.
- **O Drools ler o `soc_factos.txt`.** O módulo Drools não lê os `.txt` da base, só os JSON gerados; ler o JSON mantém isso.
- **Manter os ficheiros de resposta só para os testes.** Saíram da base por serem redundantes com as perguntas; voltar a tê-los noutro sítio era a mesma redundância.

## Plano

1. **Base (A).** `soc_cenarios.pl`: tirar as entradas e as menções dos cenários de resposta, versão 6.1 no cabeçalho, alternativas só 35d e 36c. Repor `cenarios/README.md` (e `.gitkeep`) com o texto atualizado. Retirar `base_conhecimento/gera_meta.pl`. Reescrever `base_conhecimento/README.md` (decisão 6).
2. **Versão (B).** `soc_motor.pl`: `versao_base/1`. `wrapper.pl`: usar `versao_base/1` (e a mensagem de erro de versão). `MotorDrools.java`: `VERSAO_KB` lido do `factos_iniciais.json`; comentário das 92 regras.
3. **`nao_sei` (C).** `wrapper.pl` (`perguntas_json`) e `json/Resposta.java`.
4. **Ferramentas e referências (D).** `gera_referencias.pl` e `gera_referencia_agn.pl`: variantes com passos de resposta; `fecho_sim` e `fecho_nao`. Verificar que o `gera_json.pl` lê bem o `indice.txt` novo (as alternativas passaram a uma secção "Alternativas"). Regenerar `cenarios.json` (backend e Drools), `factos_iniciais.json`, `regras.json`, `referencia_prolog/` e `referencia_agn/`. `soc_motor.pl`: comentário do `injeta_todos`.
5. **Testes Prolog (E).** `testes.pl`: `ac12_resposta`, `ac11_prefixo` e `ac10_determinismo` com a resposta como facto; teste novo do `nao_sei` pendente; teste novo da versão lida do cabeçalho.
6. **Drools (F).** `regras/O_fecho_tickets.drl` (R93 a R107), linha na tabela do `regras/README.md`. Testes: `MotorDroolsTest` (390 factos, resposta como facto, `nao_sei`), `ConsolaDroolsTest` ("390 factos e 107 regras"; alternativas com 35c/35d em vez de 7b), `ParidadeTest` (variantes novas).
7. **Testes do backend (G).** `versaoKB` "6.1" e respostas como factos em `InferenceServiceTest`, `RequestValidatorTest`, `PedidoJsonTest`, `SystemControllerTest`, `BaseCarregadaTest`, `DroolsAdapterTest` e `PrologAdapterTest`.
8. **Documentação (H).** `README_motor.md` (resultados de referência v6.1), `referencia_prolog/README.md`, `ferramentas/README.md`, README do módulo Drools, `RESULT.md` e o índice das features.

## Verificação

- Base de partida: correr os três comandos do CI e os três `--verificar` em `develop` @ `3d991ed`, numa worktree temporária, antes de mudar código, para registar o que já falha ou avisa.
- Passa se: os três comandos do CI acabam sem falhas, com o número de testes plausível (17 testes Prolog mais os novos); os três `--verificar` dizem "em dia"; a paridade Drools-Prolog dá nível A e B iguais em todas as variantes, incluindo `fecho_sim` e `fecho_nao`; a sequência base dá 61 incidentes, 41 tickets abertos e 41 perguntas nos dois motores.

## Riscos

- **O `gera_json.pl` não reconhecer o `indice.txt` novo** (as alternativas mudaram de secção) e o `cenarios.json` perder o 35d e o 36c ou as marcas de alternativa. Valida-se pelo conteúdo do `cenarios.json` regenerado e pelo teste da consola das alternativas.
- **As regras DRL dizerem algo diferente das Prolog** (15 regras escritas à mão). Valida-se pelo `ParidadeTest` nas variantes novas, que exercitam R93 a R107.
- **A versão lida do cabeçalho falhar num arranque sem base** (resposta de erro do wrapper antes de carregar a base). Valida-se com um teste do erro de pedido inválido, que tem de continuar a responder com uma versão (ou com a indicação de que não há base).
- **Mudar o `nao_sei` muda respostas que os testes do backend esperam** (perguntas que deixavam de aparecer). Valida-se pelos testes do backend e pelo `ac12`.
- **A cópia de trabalho de `develop` tem alterações que não são minhas** (`base_conhecimento/` e `provas/`). Passam para a branch nova sem serem tocadas, a não ser os ficheiros da fase 1; `provas/` fica como está.

## Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | Base v6.1 (os ficheiros já copiados) e fase 1 | A |
| 2 | Versão lida do cabeçalho (Prolog e Drools) | B |
| 3 | `nao_sei` pendente nos dois motores | C |
| 4 | Ferramentas e JSON gerados | D |
| 5 | Testes Prolog | E |
| 6 | Regras DRL R93 a R107 e testes do Drools | F |
| 7 | Testes do backend | G |
| 8 | Documentação e registo | H |

## Fora do âmbito

- A UI (`static/index.html`) e os seus testes (`testes/ui/`): o modal do cenário 7 escrito à mão, a lista de 41 perguntas e o texto das perguntas de configuração e política. Para quem mantém a UI.
- Código do backend: só os testes mudam.
- Commit, push e PR: `commit-push` e `create-pr`, depois.
