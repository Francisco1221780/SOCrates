# motores/drools

Lado Drools do sistema. Módulo Maven próprio, sem Spring: o backend depende dele e chama-o dentro da mesma JVM, em paralelo com o motor Prolog.

| Pasta | Conteúdo |
| --- | --- |
| `src/main/java/.../drools/` | `MotorDrools` (recebe o pedido JSON, corre os passos numa `KieSession` nova e devolve a resposta JSON no formato do `wrapper.pl`) e `EstadoMotor`. É a única API que o backend usa. |
| `src/main/java/.../drools/modelo/` | Dados imutáveis que as regras DRL usam: `Facto`, `Termo`, `Variavel`, `Premissa`. Não conhece o Drools nem JSON. |
| `src/main/java/.../drools/sessao/` | A ponte com a `KieSession`: `BaseFactos` (o global `base` das regras: numeração, deduplicação, justificação), `Registo` (número, origem, passo, regra e premissas de cada facto), `Origem`, `CondicoesCalculadas` (as condições calculadas do `soc_suporte.txt`, issue #14) e `Marca` (reavaliação quando chega um facto que uma condição calculada lê). |
| `src/main/java/.../drools/json/` | A fronteira JSON, igual ao `wrapper.pl`: `Pedido` (validação), `Resposta`, `TermosJson`, `FactosIniciais`, `PedidoInvalido`. |
| `src/main/java/.../drools/explicacao/` | O "porque não?" (`PorqueNao`, o `whynot/1` do Prolog): as regras do `regras.json` (`Regras`), unificação de termos com variáveis (`Unificacao`, `Var`) e as condições avaliadas como no Prolog, também com variáveis por ligar (`Suporte`). |
| `src/main/java/.../drools/consola/` | A consola interativa: `ConsolaDrools` (o menu), `SessaoConsola` (os passos e a última resposta), `TermoTexto` (termos em sintaxe Prolog) e `Cenarios` (o `cenarios.json`). Só usa a API pública do `MotorDrools`. |
| `src/main/resources/regras/` | Regras DRL, um ficheiro por grupo da base (92, portadas por grupos; nomes e molde no README da pasta). |
| `src/main/resources/` | `cenarios.json` (igual ao do backend), `regras.json` (as regras como dados, para o "porque não?") e `factos_iniciais.json` (`{"versaoKB", "factos"}`), gerados a partir de `base_conhecimento/` por `motores/prolog/ferramentas/gera_json.pl` (issue #10); não se editam à mão. Se a `versaoKB` não for a do motor, o motor fica "não pronto". |
| `src/test/java/` | Testes do motor e `ParidadeTest`, que compara o Drools com o Prolog em 10 variantes de cenários (ver "Paridade com o Prolog"). |
| `src/test/resources/` | Ficheiros de factos iniciais alternativos para casos de teste (R7 sobre os dados fixos, versão errada, catálogo com falhas para o grupo N) `referencia_agn/` e `porque_nao/` (geradas, não se editam à mão). Os outros testes usam o `factos_iniciais.json` real. |
| `ferramentas/` | `gera_referencia_agn.pl` e `gera_porque_nao.pl`: geram as referências Prolog dos testes (ver o README da pasta). |

## Como corre um pedido

Igual ao `motores/prolog/wrapper.pl`:

1. O pedido inteiro é validado antes de inferir (mesmas mensagens de erro do wrapper).
2. Sessão nova; entram os factos iniciais (`origem: inicial`) e as regras correm sobre eles.
3. Para cada passo: `insert` dos factos (`origem: entrada`) e `fireAllRules()`.
4. A resposta é montada e a sessão faz `dispose()`.

Um facto igual a outro que já existe nunca entra duas vezes, nem como entrada nem como conclusão (como o `novo_facto/1` e o `cria_facto/3` do Prolog).

## Correr só o Drools

Há duas formas, como no Prolog (o `menu/0` do `soc_motor.pl` e o `wrapper.pl`). O módulo basta-se a si próprio: as regras, o `factos_iniciais.json` e o `cenarios.json` vêm no classpath.

### Consola interativa

No IntelliJ: *Run* no `main` de `socrates.motores.drools.consola.ConsolaDrools`. Pela linha de comandos, a partir da raiz:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd -q -pl motores/drools compile exec:java "-Dexec.args=--interativo"
```

Arranca com a base carregada e espera pelas opções.

Mensagens que podem aparecer antes do menu, e o que fazer com elas:
- **`SLF4J: No SLF4J providers were found`** não deve aparecer. O módulo traz o `slf4j-simple` como dependência `optional` (não passa para o backend), com o nível em `error` no `src/main/resources/simplelogger.properties`. Se aparecer, o IntelliJ está a correr com um classpath antigo: *Reload* do projeto Maven.
- **`WARNING: sun.misc.Unsafe::objectFieldOffset ...`** é o JDK 25 a avisar que a `xstream`, que o Drools usa por dentro, chama um método interno que vai ser removido. Não afeta o motor. Para não o ver, na *Run Configuration* da `ConsolaDrools`, em *VM options*: `--sun-misc-unsafe-memory-access=allow` (opção da JVM; fica na configuração de cada um, não no projeto).

O menu é o do `soc_motor.pl` (`menu/0`): as mesmas opções e a mesma numeração, de 0 a 13. Depois de cada opção a consola pára até carregares em Enter (para o menu não tapar o que a opção escreveu); cada pedido mostra as escolhas que há (cenários, perguntas, predicados com conclusões, o que as regras podem concluir). Não se escreve sintaxe Prolog: os factos escrevem-se `nome(arg, arg)`, sem plicas, e as respostas não levam ponto final (`1`, `c6`, `sim`). O ponto final e as plicas do Prolog continuam a ser aceites, e as listas e os cenários `.txt` do Prolog também.

| Opção | O que faz |
| --- | --- |
| 1 | Carregar a base de conhecimento: `base` (ou Enter) recomeça com a base do módulo; um caminho carrega outro ficheiro de factos iniciais, no formato do `factos_iniciais.json`. As regras são sempre as do módulo |
| 2 | Correr o motor (o Drools corre sempre até ao ponto fixo, por isso dá `Nao ha conclusoes novas.`) |
| 3 | Injetar um cenário: um id (`c6`), vários (`c1 c2`) ou um ficheiro: `.txt` no formato dos cenários Prolog (`cenarios/cenario_30.txt`), ou `.json` com um pedido. Recusa um repetido ou alternativo de um que já entrou, e avisa se falta uma dependência |
| 4 | Introduzir factos, um por linha e uma linha vazia para acabar: `alerta_ids(ev_1, web_srv_02, sig_apache_path_traversal)`. Um valor como `2.4.49-ubuntu1` escreve-se sem plicas |
| 5 | Responder às perguntas ao analista (o valor, `sim`, ou o número da opção). `porque` mostra a regra que fez a pergunta (escrita por inteiro), os factos que a levaram e as regras que vão usar a resposta |
| 6 / 7 | Tickets abertos (por prioridade) e fechados, incidentes sem ticket / ações recomendadas por ativo |
| 8 | Registar a recuperação de uma máquina comprometida (`recuperado(Ativo, Evento)`) |
| 9 | "Como?": a árvore de factos de uma conclusão, com o número da regra de cada um (pelo número ou pelo facto) |
| 10 | "Porque não?": porque é que um facto não se concluiu. Para cada regra que o podia concluir, a regra inteira com cada condição marcada (`[ok]` ou `[FALTA]`) e, por baixo, o que falta e porquê, até aos dados de entrada. O facto pode ter variáveis, ex.: `compromisso_confirmado(web_srv_05, Ev)` |
| 11 | Mostrar factos: `todos`, `concluidos` ou os de um predicado (a consola lista os predicados que há) |
| 12 | Opções do motor: `mostrar_conclusoes` (escreve cada conclusão nova), `passo_a_passo` (espera por Enter depois de cada uma), `profundidade_whynot` (4) |
| 13 | Injetar todos os cenários de uma vez: volta a carregar a base e injeta as sequências `base` e `normal` (sem respostas), como o `injeta_todos` |
| 0 | Sair (também com o fim da entrada, Ctrl+D) |

O menu tem as mesmas opções e a mesma numeração (0 a 13) do Prolog. O "porque?" só existe durante as perguntas ao analista (opção 5), onde explica porque se faz a pergunta; não há opção para o pedir sobre um facto qualquer (isso é o "como?"). Diferenças para o menu do Prolog: arranca já com a base carregada (no Prolog é preciso a opção 1, e depois a 2); a dica de profundidade do "porque não?" aponta para a opção 12, em vez do `define_opcao/2`; e as perguntas por responder são as da resposta do motor (sem as já respondidas com "nao sei").

As explicações (o "como?" na 9, o "porque não?" na 10 e o "porque?" de uma pergunta na 5) estão na classe `consola/Explicacoes` e mostram os factos como `nome(arg, arg)`. O "porque não?" usa as regras como dados (`src/main/resources/regras.json`, gerado pelo `gera_json.pl`) e está também na API: `MotorDrools.porqueNao(pedido, facto, profundidade)`. O texto do "porque não?" é próprio da consola Drools, mas o `PorqueNaoTest` compara as mesmas linhas, escritas no formato do `whynot/1`, com o do Prolog (referência gerada por `ferramentas/gera_porque_nao.pl`).

O motor não guarda estado entre pedidos: a consola guarda a lista de passos e envia o pedido inteiro ao `MotorDrools.infere` a cada ação. O que mostra é o que o backend e o comparador recebem.

### Um pedido JSON

Como o `wrapper.pl`: o pedido entra pelo `stdin` e a resposta sai no `stdout` (só o JSON; código de saída 1 em erro). A partir da raiz, no PowerShell, com o JDK 25:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'

# pedido de exemplo: a telemetria do cenario 6 (versao desconhecida: R4 e depois R7)
@'
{"versaoKB": "6", "passos": [{"id": "c6", "factos": [
  {"predicado": "alerta_ids", "args": ["ev_f1", "web_srv_05", "sig_apache_path_traversal"]},
  {"predicado": "origem", "args": ["ev_f1", "ip_198_51_100_22"]},
  {"predicado": "origem_zona", "args": ["ev_f1", "internet"]},
  {"predicado": "tempo", "args": ["ev_f1", 2200]}
]}]}
'@ | Set-Content pedido.json

Get-Content pedido.json | .\mvnw.cmd -q -pl motores/drools compile exec:java > resposta.json

# o mesmo pedido no Prolog, para comparar
Get-Content pedido.json | swipl motores/prolog/wrapper.pl base_conhecimento > resposta_prolog.json
```

Os passos prontos a enviar de cada cenário estão no `backend/socrates/src/main/resources/generated/cenarios.json`.

## API do motor (o que o menu faz, em código)

Tudo o que o menu da consola faz está também no `MotorDrools`, com respostas JSON no mesmo envelope (`estado`, `motor`, `versaoKB`, `erro`). A consola chama estes métodos, por isso mostra o que um cliente da API recebe. O motor não guarda estado: cada método recebe o pedido (`{"versaoKB", "passos"}`) e volta a corrê-lo; um cliente que peça várias explicações paga uma corrida por pedido.

| Método | O que devolve | Opção do menu |
| --- | --- | --- |
| `infere(pedido)` | A resposta: `factos` (com `regra` e `premissas`), `perguntas`, `incidentes`, `tickets` | 2, 3, 4, 5, 6, 7, 8, 12, 14 (injetar é juntar `passos`; o resto lê-se da resposta) |
| `como(pedido, facto)` | `arvore`: o facto, a regra que o concluiu e as premissas (facto, `nao` ou condição calculada), até aos dados | 9 |
| `porque(pedido, facto)` | O mesmo, e cada nó concluído traz `regraCompleta` (`id`, `se`, `entao`, com os nomes das variáveis) | 10 |
| `porquePergunta(pedido, ativo, evento, pergunta)` | `geradaPor` (o "porque" do facto que gerou a pergunta, ou `null` se foi à mão), `regrasQueUsamAResposta` | 5 (`porque`) |
| `porqueNao(pedido, facto, profundidade)` | `explicacao`: linhas planas com `nivel` e `tipo`; as linhas `regra` trazem o `se` completo (cada condição com `verificada`) e o `entao` | 11 |
| `predicadosConcluidos()` | `predicados`: o que as regras podem concluir | 11 (a lista de escolhas) |
| `cenarios()` | O `cenarios.json`: cada cenário com `passo` pronto a enviar, e as `sequencias` | 3, 14 |

Um facto escolhe-se por `{"numero": N}` (o `numero` da resposta do `infere`) ou por `{"predicado", "args"}`; o `porqueNao` aceita variáveis (`{"var": "Ev"}`). Um facto que não existe, um pedido inválido ou um motor não pronto dão `estado: "erro"` com a mensagem. Um nó de `como`/`porque` que já apareceu antes na mesma árvore tem `jaExplicado: true` e não repete as premissas.

O `como`, o `porque` e o `porquePergunta` correm o pedido e passam a resposta à `explicacao.Explicacao`, que monta a explicação só a partir da resposta (texto JSON à entrada e à saída). O backend usa a `Explicacao` diretamente para explicar as respostas dos dois motores (a do `wrapper.pl` tem o mesmo formato, ver `ParidadeTest`). A explicação leva o `motor` e a `versaoKB` da resposta, e uma resposta de outra `versaoKB` que a do `regras.json` dá erro.

## Modelo de factos

- `Facto(predicado, args)` genérico (decisão da #6). Dois factos são iguais se tiverem o mesmo predicado e os mesmos argumentos.
- Termos: átomo → `String`; inteiro → `Long`; lista → `List`; termo composto → `Termo(f, args)`. `v(2,4,49)` e `"2.4.49-ubuntu1"` nunca se confundem.
- Nas regras usa-se `functor` (predicado e aridade, ex.: `"versao/2"`) e `arg(i)`.

## Resposta

Campo a campo a do `wrapper.pl`, com `"motor": "drools"`: `estado`, `motor`, `versaoKB`, `perguntas`, `incidentes`, `tickets`, `factos`, `erro`.

Cada facto tem `numero`, `predicado`, `args`, `origem` (`inicial`, `entrada`, `regra`), `passo`, `regra` e `premissas`.

As premissas vão uma por condição da regra, pela ordem:
- um facto entra pelo seu número;
- uma negação entra como `{"f":"nao","args":[termo]}`;
- uma condição calculada entra como o próprio termo;
- as variáveis livres chamam-se `"A"`, `"B"`, …

Os números mudam entre motores; compara-se pelo conteúdo.

## Paridade com o Prolog

O `ParidadeTest` (corre em cada `mvnw verify`) envia ao Drools os passos de cada variante de cenários e compara a resposta, facto a facto, com a do `wrapper.pl`: conteúdo, origem, passo, regra e premissas. Uma premissa que aponta para um facto compara-se pelo conteúdo desse facto. Corre a dois níveis, nas 10 variantes do `gera_referencias.pl`:

| Nível | Regras ativas no Drools | Referência | Diferenças aceites |
| --- | --- | --- | --- |
| A–G+N | R1–R34 e R89–R92 (filtro pelo nome da regra) | `src/test/resources/referencia_agn/`, o `wrapper.pl` com um `soc_regras.txt` só com essas regras | Nenhuma |
| Completo | As 92 | `motores/prolog/referencia_prolog/` | Só as que têm causa de raiz numa regra H–M listada em `ParidadeTest.CAUSAS_H_M` / `ERROS_H_M`, com a razão |

Quando mudam as regras, os factos ou os cenários, regenera-se a referência A–G+N (precisa do `swipl`; o build não o chama):

```bash
swipl motores/drools/ferramentas/gera_referencia_agn.pl              # escreve os 10 JSON
swipl motores/drools/ferramentas/gera_referencia_agn.pl --verificar  # código 1 se algum estiver desatualizado
```

Três diferenças entre os motores que o código trata para a resposta ser igual:

- **Ordem das regras.** Quando duas regras ficam ativas ao mesmo tempo, o Drools dispara primeiro a que foi carregada primeiro, e isso decide a justificação de um facto concluído por duas vias. O `MotorDrools` carrega os DRL sempre pela ordem do nome (`A_afetado.drl`, `B_vetor.drl`, …), em qualquer sistema; é com essa ordem que o `ParidadeTest` passa. Um DRL novo entra pela ordem do nome.

- **Reavaliação das condições calculadas.** Quando chega um facto que uma condição calculada lê (`condicao_le/2`, ex.: o `tempo` de um evento que chega depois do evento), o Prolog reavalia a regra. No Drools, a `BaseFactos` faz `update` à `Marca` desse functor e as regras com `Marca(functor == ...)` antes da condição voltam a avaliar-se.
- **Justificação de um facto concluído por duas vias pela mesma regra.** O Prolog fica com a primeira que encontra, percorrendo os factos por número; o Drools dispara primeiro a ativação mais recente. A `BaseFactos` fica com a via de factos com números mais baixos, se for a mesma regra, no mesmo passo, e com premissas anteriores ao facto.

## Estado do motor

`MotorDrools.estado()` diz se as regras compilaram e se os factos iniciais carregaram, e porquê quando não (AC13). Um motor que não está pronto responde `estado: "erro"` a qualquer pedido.

Regras do módulo:

- Não depende de Spring nem de classes do backend. A fronteira é JSON de entrada e JSON de saída.
- Compara-se com o Prolog pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

Estado: infraestrutura (issue #4), regras A a G e N (issue #13, iguais ao Prolog nas 10 variantes) e regras H a M com as condições calculadas (PR #31, issues #14 e #15; diferenças listadas no `ParidadeTest`).
