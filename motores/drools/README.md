# motores/drools

Lado Drools do sistema. Módulo Maven próprio, sem Spring: o backend depende dele e chama-o dentro da mesma JVM, em paralelo com o motor Prolog.

| Pasta | Conteúdo |
| --- | --- |
| `src/main/java/.../drools/` | `MotorDrools` (recebe o pedido JSON, corre os passos numa `KieSession` nova e devolve a resposta JSON no formato do `wrapper.pl`) e `EstadoMotor`. É a única API que o backend usa. |
| `src/main/java/.../drools/modelo/` | Dados imutáveis que as regras DRL usam: `Facto`, `Termo`, `Variavel`, `Premissa`. Não conhece o Drools nem JSON. |
| `src/main/java/.../drools/sessao/` | A ponte com a `KieSession`: `BaseFactos` (o global `base` das regras: numeração, deduplicação, justificação), `Registo` (número, origem, passo, regra e premissas de cada facto) e `Origem`. |
| `src/main/java/.../drools/json/` | A fronteira JSON, igual ao `wrapper.pl`: `Pedido` (validação), `Resposta`, `TermosJson`, `FactosIniciais`, `PedidoInvalido`. |
| `src/main/resources/regras/` | Regras DRL, um ficheiro por grupo da base (92, portadas por grupos; nomes e molde no README da pasta). |
| `src/main/resources/` | `factos_iniciais.json` (`{"versaoKB", "factos"}`), gerado a partir de `base_conhecimento/` por `motores/prolog/ferramentas/gera_json.pl` (issue #10); não se edita à mão. Se a `versaoKB` não for a do motor, o motor fica "não pronto". |
| `src/test/java/` | Testes do motor; mais tarde, cada cenário comparado com `motores/prolog/referencia_prolog/`. |
| `src/test/resources/` | Ficheiros de factos iniciais alternativos para casos de teste (R7 sobre os dados fixos, versão errada). Os outros testes usam o `factos_iniciais.json` real. |

## Como corre um pedido

Igual ao `motores/prolog/wrapper.pl`:

1. O pedido inteiro é validado antes de inferir (mesmas mensagens de erro do wrapper).
2. Sessão nova; entram os factos iniciais (`origem: inicial`) e as regras correm sobre eles.
3. Para cada passo: `insert` dos factos (`origem: entrada`) e `fireAllRules()`.
4. A resposta é montada e a sessão faz `dispose()`.

Um facto igual a outro que já existe nunca entra duas vezes, nem como entrada nem como conclusão (como o `novo_facto/1` e o `cria_facto/3` do Prolog).

## Correr só o Drools

Sem o backend, como o `wrapper.pl`: o pedido entra pelo `stdin` e a resposta sai no `stdout` (só o JSON; código de saída 1 em erro). A partir da raiz, no PowerShell, com o JDK 25:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'

# pedido de exemplo: a R7 com um vetor aberto para uma instalacao sem versao (cenario 6)
@'
{"versaoKB": "6", "passos": [{"id": "cenario_06", "factos": [
  {"predicado": "vetor_aberto", "args": ["ev_f1", "web_srv_05", "web_srv_05_apache", "cve_2021_41773"]}
]}]}
'@ | Set-Content pedido.json

Get-Content pedido.json | .\mvnw.cmd -q -pl motores/drools compile exec:java > resposta.json

# o mesmo pedido no Prolog, para comparar
Get-Content pedido.json | swipl motores/prolog/wrapper.pl base_conhecimento > resposta_prolog.json
```

Enquanto só houver a R7, a telemetria de um cenário (`alerta_ids`, `origem`, …) não chega para o Drools concluir nada: o `vetor_aberto` vem da R4, que ainda não está portada. Por isso o exemplo injeta o `vetor_aberto` diretamente.

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

## Estado do motor

`MotorDrools.estado()` diz se as regras compilaram e se os factos iniciais carregaram, e porquê quando não (AC13). Um motor que não está pronto responde `estado: "erro"` a qualquer pedido.

Regras do módulo:

- Não depende de Spring nem de classes do backend. A fronteira é JSON de entrada e JSON de saída.
- Compara-se com o Prolog pelo conteúdo dos factos, nunca pelos números nem pela ordem de disparo.

Estado: a infraestrutura está feita (issue #4); a única regra portada é a R7, em `regras/B_vetor.drl`, que serve de molde.
