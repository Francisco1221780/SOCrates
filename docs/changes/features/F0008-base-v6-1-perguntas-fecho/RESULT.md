# F0008 — Resultado

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Branch | `feature/base-v6-1-perguntas-fecho`, sobre `develop` @ `3d991ed` |
| Estado | Implementado (por commit) |
| Base de partida (`develop` @ `3d991ed`, worktree limpa) | Prolog 17 testes, todos a passar; Drools 113, todos a passar; backend 109, todos a passar; os três `--verificar` em dia |
| Prolog | 20 testes (3 novos), todos a passar; `gera_json`, `gera_referencias`, `gera_porque_nao` e `gera_meta` com `--verificar` em dia |
| Drools | 106 testes, todos a passar: a paridade passou a um só nível (14 variantes, com as 107 regras), em vez de dois níveis com 10 variantes cada e um teste que os comparava |
| Backend | 109 testes, todos a passar |
| Commits | nenhum (a fazer com `commit-push`) |

## 1. O que ficou fechado

- **A. Base.** A v6.1 está em `base_conhecimento/` (390 factos, 107 regras, 57 cenários), com os cabeçalhos "(SOCrates), versao 6.1". `soc_cenarios.pl` sem os cenários de resposta e com as letras novas. `README.md` da base reescrito para ser entregável (ficheiros, grupos A a O, perguntas ao analista, cenários, resultados, manutenção, o que mudou da v6). `cenarios/README.md` reposto e atualizado. O `base_conhecimento/gera_meta.pl` que a cópia tinha trazido foi retirado.
- **B. Versão sem valor escrito à mão.** `soc_motor.pl` ganhou `versao_base/1` (lê a primeira linha do `soc_factos.txt`, o mesmo critério do `gera_json.pl`); o `wrapper.pl` usa-a, e responde com `versaoKB: null` quando a base não carregou. `MotorDrools.VERSAO_KB` passou a ser lido do `factos_iniciais.json` (`FactosIniciais.versao`). Mudar a versão é mudar o cabeçalho e regenerar os JSON.
- **C. `nao_sei` pendente.** `wrapper.pl` (`perguntas_json`) e `Resposta.java` deixam pendente uma pergunta respondida só com `nao_sei`, como o `pergunta_pendente/4` do motor.
- **D. Ferramentas e referências.** As variantes do `gera_referencias.pl` metem as respostas do analista como passos com o facto `resposta_analista`, e a telemetria que não está em nenhum cenário como passos de factos; variantes novas `fecho_sim`, `fecho_nao`, `ataque_intermedio_tardio` (R69) e `c2_antes_do_porto` (R71). Com elas disparam 103 das 107 regras; as R89 a R92 (falhas no catálogo) comparam-se com o Prolog no `MotorDroolsTest`, com uma base própria. `gera_json.pl` reconhece a secção "Alternativas" do índice (grupo `alternativa`). JSON, `referencia_prolog/` e `porque_nao/prolog.txt` regenerados.
- **E. Testes Prolog.** `ac12_*`, `ac11_prefixo` e `ac10_determinismo` com a resposta como facto; os pedidos de erro levam a versão do `cenarios.json`; testes novos `nao_sei_continua_pendente`, `versao_do_cabecalho` e `versao_sem_base`.
- **F. Drools.** `regras/O_fecho_tickets.drl` com R93 a R107, com paridade igual ao Prolog nas 14 variantes. Testes do módulo com 390 factos e 107 regras, versão lida do motor, alternativas com o 35b/35c, e `[32,33,34,50,51,94]` como regras que usam a resposta de `shell_servico`.
- **G. Testes do backend.** Os que correm sobre a base real (`BaseCarregadaTest`, `DroolsAdapterTest`, `PrologAdapterTest`, `InferenceServiceTest`) usam a versão do `cenarios.json` em vez de "6"; o c6 passa a dar `aguarda_resposta` (a pergunta de fecho da versão por confirmar). Os testes com motores falsos não mudaram.
- **H. Documentação.** `README_motor.md` (resultados de referência da v6.1), `motores/prolog/README.md`, `ferramentas/README.md`, `referencia_prolog/README.md`, `regras/README.md` (linha do grupo O), README da raiz (v6.1, 390/107/57, 12 variantes).

## 2. Pontos que pedem decisão

Nenhum em aberto. Os que havia (o teste do `gera_meta.pl` no Windows, o `--verificar` que saía com 0 e o nível A-G+N redundante) foram resolvidos nesta branch (ver §3).

## 3. Desvios do plano aprovado

- **Letras dos cenários a começar em "a"** (pedido do utilizador depois da aprovação): 06b→06a, 10b/10c→10a/10b, 11b→11a, 13b→13a, 20b→20a, 21b→21a, 24b→24a, 25b→25a, 27b→27a, 29b→29a, 35b/35c/35d→35a/35b/35c, 36b/36c→36a/36b, 37b→37a, 38b→38a, nos ficheiros, no índice, nos comentários e no `soc_cenarios.pl`. Os ids e os nomes das variantes seguem as letras novas (`c35c_em_vez_de_35b`, `c36b_em_vez_de_36a`), e as variantes de resposta perderam o "b" (`r7_nao`, `respostas_3_10b_17`, `r29_em_vez_de_29a`). Os passos das respostas têm ids `resposta_07`, `resposta_19`… em vez de `cenario_07b_sim`.
- **Grupo `alternativa` no `cenarios.json`.** As alternativas 35c e 36b eram do grupo `resposta`; passaram a `alternativa` (a UI não usa o grupo; a consola Drools só o mostra).
- **`ParidadeTest` refaz os passos das respostas a partir da referência** (factos de origem `entrada` desse passo), porque as respostas já não estão no `cenarios.json`. O formato das referências não mudou.
- **Filtro A-G+N do `ParidadeTest` corrigido:** era `n <= 34 || n >= 89`, que com a v6.1 apanhava R93 a R107; passou a `n <= 34 || (89 <= n <= 92)`.
- **Testes do backend:** só mudaram os que correm sobre a base real. Os que usam motores falsos continuam com a versão "6", que é só um valor de teste.
- **`motores/prolog/ferramentas/gera_meta.pl` reposto.** Na cópia de trabalho estava marcado como apagado e substituído pelo `gera_meta.pl` da base de conhecimento do curso (outro programa, que dá erros de sintaxe sem o motor carregado). Reposto do `HEAD`.
- **`soc_regras.txt` e `soc_factos.txt`:** o utilizador mudou os cabeçalhos para "(SOCrates), versao 6" durante a implementação; acertou-se para "(SOCrates), versao 6.1" (decisão do utilizador), e o mesmo no `indice.txt`.
- **Nível A-G+N da paridade retirado** (pedido do utilizador depois da aprovação): saíram o `motores/drools/ferramentas/gera_referencia_agn.pl`, a pasta `src/test/resources/referencia_agn/`, o nível "agn" do `ParidadeTest` e o teste que comparava as duas referências, e o filtro de regras do `MotorDrools` (o construtor com `Predicate`, que só esse nível usava). Ficou um nível, com as 107 regras.
- **Variantes para as regras que não disparavam** (pedido do utilizador: testar todas as regras): `ataque_intermedio_tardio` (R69) e `c2_antes_do_porto` (R71), com passos de factos que não estão em nenhum cenário.
- **`.gitkeep` retirados** (pedido do utilizador): os seis que estavam em pastas com outros ficheiros (`base_conhecimento/`, `docs/`, `motores/`, `motores/prolog/`, `backend/.../generated/` e `backend/.../static/`), além do de `base_conhecimento/cenarios/`.
- **`gera_meta.pl --verificar` passa a falhar quando o script está partido** (pedido do utilizador): o script tem `:- set_prolog_flag(on_error, status).`, e um erro de sintaxe nele dá agora código 1. Um ficheiro trocado por outro programa sem `main` continua a sair com 0, por isso o teste `ac13_meta_em_dia` passou a exigir também a linha "O metaconhecimento esta atualizado.". A mesma exigência no health do backend deixou de ser precisa: o PR #44 da `develop` tirou a verificação do metaconhecimento do health.
- **Merges da `develop`:** a branch juntou o #41 (UI), o #43 (`/como?regras=true`) e o #44 (health sem metaconhecimento); os conflitos foram nos testes do backend, resolvidos com as chamadas novas da `develop` e a versão lida da base. Saiu a pasta `provas/` (pedidos da v6), a pedido do utilizador, e o timeout do Prolog no `application.yaml` passou para 30 s (a sequência base demora ~9 s num portátil).
- **`PrologAdapterTest`:** na cópia de trabalho, duas asserções tinham passado a `contains("ferramentas/gera_meta.pl")`, que falhava no Windows (o caminho vem com `\`). A pedido do utilizador, as duas normalizam agora as barras antes de comparar.
- **"Injetar todos" igual nos três lados** (pedido do utilizador): a UI corria todas as sequências do catálogo (com as atualizações do inventário e do catálogo, 39 perguntas) e as consolas só a base e a atividade normal (41). O `soc_cenarios.pl` tem agora `sequencia_completa/1` (base, atualizações e atividade normal), que o `gera_json.pl` põe no `cenarios.json` como `completa`; o `injeta_todos` do Prolog, a opção 13 da consola Drools e a vista global da UI usam-na.
- **Swagger com JSON em vez de "string"** (pedido do utilizador): os `POST` recebem e devolvem o JSON como texto, por isso o OpenAPI só mostrava "string". `api/ExemplosPedido` e `api/ExemplosResposta` têm exemplos de pedido (o cenário 7, com e sem resposta) e de resposta (200 e erros 400, 404, 415, 422, 500, 503, 504), tirados de respostas reais do backend e encurtados; o `ExemplosPedidoTest` falha se a versão da base mudar sem os atualizar. O texto do topo do Swagger dizia "(v6)": passou a juntar a versão da base em uso (`config/OpenApiConfig`, a partir da `BaseCarregada`).
- **`target/` desatualizado:** o `backend/socrates/target/classes/prolog-dist` tinha a base v6 de um build anterior (o Maven não a recopiava) e o `clean` falhou por ficheiros presos no `motores/drools/target`; apagou-se só a `prolog-dist` gerada antes do `verify`.

## 4. Por verificar

- O CI no GitHub (Linux) não foi corrido; os três comandos correram nesta máquina (Windows).
- A UI não foi testada com a base v6.1 (fora do âmbito): o modal do cenário 7 escrito à mão e a lista de 41 perguntas.
- O `ConsolaDroolsTest` não testa o "ainda não" (`nao_sei`) na consola Drools; está testado no wrapper (`nao_sei_continua_pendente`) e coberto pela paridade (`r7_nao_sei_19_nao`, `fecho_nao`).
- As R89 a R92 não disparam em nenhuma variante de referência (precisam de um catálogo com falhas); a paridade delas está só no `MotorDroolsTest.catalogoComFalhasDaAsMesmasConclusoesQueOProlog`.

## 5. Como correr a verificação

```bash
swipl -g run_tests -t halt motores/prolog/testes/testes.pl     # wrapper, JSON, referências e metaconhecimento (20 testes)
./mvnw -B -pl motores/drools verify                            # Drools, incluindo a paridade com o Prolog (106 testes)
./mvnw -B -pl backend/socrates -am verify                      # backend (109 testes)
swipl motores/prolog/ferramentas/gera_json.pl --verificar       # JSON gerados em dia com a base
swipl motores/prolog/ferramentas/gera_referencias.pl --verificar
swipl motores/prolog/ferramentas/gera_meta.pl --verificar
```

## 6. Inventário de alterações

- `base_conhecimento/soc_factos.txt`, `soc_regras.txt`, `soc_suporte.txt` — alterados (v6.1)
- `base_conhecimento/cenarios/` — 22 ficheiros de resposta apagados, 18 renomeados (letras a começar em "a"), restantes com comentários atualizados; `indice.txt` e `README.md` alterados; `.gitkeep` apagado
- `base_conhecimento/soc_cenarios.pl`, `base_conhecimento/README.md` — alterados
- `motores/prolog/soc_motor.pl` — alterado (`versao_base/1`, comentários)
- `motores/prolog/wrapper.pl` — alterado (versão do cabeçalho, `nao_sei`)
- `motores/prolog/ferramentas/gera_json.pl`, `gera_referencias.pl` — alterados
- `motores/prolog/testes/testes.pl` — alterado
- `motores/prolog/referencia_prolog/*.json` — regenerados; 5 apagados, 9 novos
- `motores/prolog/README.md`, `README_motor.md`, `ferramentas/README.md`, `referencia_prolog/README.md` — alterados
- `motores/drools/src/main/resources/regras/O_fecho_tickets.drl` — novo
- `motores/drools/src/main/resources/regras/README.md` — alterado
- `motores/drools/src/main/java/.../MotorDrools.java`, `json/FactosIniciais.java`, `json/Resposta.java` — alterados
- `motores/drools/src/main/resources/cenarios.json`, `factos_iniciais.json`, `regras.json` — regenerados
- `motores/drools/ferramentas/gera_referencia_agn.pl` — apagado
- `motores/drools/src/test/resources/referencia_agn/` — apagada
- `motores/drools/README.md`, `motores/drools/ferramentas/README.md` — alterados (sem o nível A-G+N)
- `.gitkeep` de `base_conhecimento/`, `base_conhecimento/cenarios/`, `docs/`, `motores/`, `motores/prolog/`, `backend/socrates/src/main/resources/generated/` e `static/` — apagados
- `motores/drools/src/test/resources/porque_nao/prolog.txt` — regenerado
- `motores/drools/src/test/resources/factos_iniciais_catalogo.json`, `factos_iniciais_r7.json` — alterados (versão)
- `motores/drools/src/test/java/.../MotorDroolsTest.java`, `ParidadeTest.java`, `MotorDroolsExplicacoesTest.java`, `consola/ConsolaDroolsTest.java`, `consola/PorqueNaoTest.java`, `explicacao/ExplicacaoTest.java` — alterados
- `backend/socrates/src/main/resources/generated/cenarios.json` — regenerado
- `backend/socrates/src/test/java/.../BaseCarregadaTest.java`, `InferenceServiceTest.java`, `DroolsAdapterTest.java`, `PrologAdapterTest.java` — alterados
- `README.md` (raiz) — alterado
- `docs/changes/features/F0008-base-v6-1-perguntas-fecho/` — novo (README e RESULT); `docs/changes/features/README.md` — alterado
