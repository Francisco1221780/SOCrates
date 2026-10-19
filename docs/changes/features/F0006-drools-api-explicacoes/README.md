# F0006 — Drools: o "como?", o "porque?" e a lista de cenários na API do motor

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 19/10: "o que faz o menu tem de ser possível chamar pela API"; "implementa o que falta do meu lado do Drools") |
| Branch | `feature/drools-menu-why` (a mesma da F0005, ainda sem commits) |
| Estado | Plano à espera de aprovação |
| Âmbito | Só o módulo `motores/drools`. O `MotorDrools` ganha métodos públicos, com resposta JSON, para tudo o que o menu da consola faz e a API ainda não consegue: "como?", "porque?", o "porque?" de uma pergunta e a lista de cenários (o "porque não?" já existe). A consola passa a usar esses métodos, para a consola e a API mostrarem o mesmo. **Não se mexe no backend** (`backend/cyber`), nem na interface `MotorInferencia`, nem no Prolog; no fim há um resumo dos endpoints que o backend devia ter |
| Verificação | `.\mvnw.cmd -pl motores/drools verify`; testes novos do contrato JSON de cada método; os testes da consola (F0005) continuam a passar sem mudar o texto |

---

## 1. Situação

**Evidência**

- `MotorDrools` público: `infere(pedido)`, `porqueNao(pedido, facto, profundidade)`, `estado()`, `numeroRegras()` (`MotorDrools.java`). O backend só usa `DroolsAdapter.infere` (`backend/cyber/.../engine/drools/DroolsAdapter.java`); `backend/cyber/.../api/` só tem um README, sem endpoints.
- O "como?" e o "porque?" só existem na consola (`consola/Explicacoes.java`), construídos a partir dos factos da resposta do `infere` (`regra`, `premissas`). Quem chamar o motor por código tem de refazer a árvore.
- O texto de uma regra (`PorqueNao.regra(id)`) é público no pacote `explicacao` mas o `MotorDrools` não o expõe.
- O "porque?" de uma pergunta (opção 5 da consola) junta a regra que a gerou com as regras que usam a resposta (`PorqueNao.regrasComCondicao`); está em `ConsolaDrools.porquePergunta`.
- Os cenários (`cenarios.json`, com id, grupo, descrição, dependências, alternativas, sequências e o passo pronto a enviar) só se leem pela classe `consola/Cenarios`, de pacote privado.
- A lista de factos que as regras podem concluir (`PorqueNao.predicadosConcluidos`) só a consola a usa.

**Inventário**

| # | Superfície | Opção(ões) do menu |
| :--- | :--- | :--- |
| A | `MotorDrools.como(pedido, facto)`: a árvore de factos de uma conclusão | 9 |
| B | `MotorDrools.porque(pedido, facto)`: a mesma árvore com cada regra escrita por inteiro | 10 |
| C | `MotorDrools.porquePergunta(pedido, ativo, evento, pergunta)`: a regra que gerou a pergunta, os factos, e as regras que usam a resposta | 5 (`porque`) |
| D | `MotorDrools.cenarios()`: a lista de cenários com o passo pronto a enviar, e as sequências | 3, 14 |
| E | `MotorDrools.predicadosConcluidos()`: o que as regras podem concluir | 11 (a lista de escolhas) |
| F | A consola e os READMEs usam A–E | — |

**Escala:** 1 classe nova em `explicacao/` (a árvore), 5 métodos públicos novos no `MotorDrools`, `Explicacoes` a escrever a partir do JSON da árvore, 1 classe de teste nova e a `ConsolaDroolsTest` inalterada no texto esperado.

---

## 2. Resultado pretendido

Cada método devolve uma string JSON no mesmo envelope do `porqueNao`: `{"estado": "concluido" | "erro", "motor": "drools", "versaoKB": "6", ..., "erro": null}`. Um facto escolhe-se por `{"numero": N}` ou por `{"predicado": "...", "args": [...]}`.

```jsonc
// como(pedido, {"numero": 351})
{ "estado": "concluido", "facto": {"numero": 351, ...},
  "arvore": { "numero": 351, "predicado": "incidente", "args": [...], "regra": 7, "jaExplicado": false,
    "premissas": [
      { "tipo": "facto", "no": { "numero": 348, "regra": 4, "premissas": [...] } },
      { "tipo": "nao", "termo": {"f": "versao", "args": ["web_srv_05_apache", "A"]} },
      { "tipo": "calculada", "termo": {"f": "porto_compativel", "args": [...]} } ] } }

// porque(pedido, {"numero": 351}): igual, e cada nó com regra tem
{ ..., "regra": 7, "regraCompleta": {"id": 7, "se": [...], "entao": [...]}, ... }

// porquePergunta(pedido, "web_srv_04", "ev_e1", "shell_servico")
{ "estado": "concluido", "pergunta": {...}, "geradaPor": <árvore do "porque?" do facto da pergunta, ou null se foi à mão>,
  "regrasQueUsamAResposta": [32, 33, 34, 50, 51] }

// cenarios()
{ "cenarios": [ {"id": "c6", "grupo": "...", "descricao": "...", "depende": [], "alternativas": [], "passo": {...}} ],
  "sequencias": {"base": [...], "normal": [...]} }

// predicadosConcluidos()
{ "predicados": ["acao", "ataque_dirigido", ...] }
```

### Decisões

| Decisão | Valor | Origem |
| :--- | :--- | :--- |
| Onde | Só no módulo Drools (`MotorDrools` + `explicacao/`). Backend e interface `MotorInferencia` ficam por fazer, e o resumo no fim diz o que devia existir | Utilizador (19/10) |
| De onde vem a árvore | Do `infere` (os factos da resposta, com `regra` e `premissas`), para a API e a consola mostrarem o que o cliente vê | Decisão de implementação |
| "como?" e "porque?" | A mesma árvore; o "porque?" junta a `regraCompleta` a cada nó. Um só construtor, com um interruptor | Decisão de implementação |
| Nó já explicado | `"jaExplicado": true` e sem premissas, como o "ja explicado acima" da consola | `como/1` do Prolog |
| Erros | `estado: "erro"` com a mensagem (facto inexistente, pedido inválido, motor não pronto), como o `porqueNao` | `porqueNao` |
| Consola | `Explicacoes` passa a escrever a partir destes JSON (o texto escrito não muda) | Decisão de implementação |

### Alternativas rejeitadas

- **Expor só o `infere` e deixar o cliente montar a árvore.** O cliente (UI) teria de copiar a lógica da consola, e a regra completa nem estaria na resposta.
- **Pôr as explicações dentro da resposta do `infere`.** Engorda cada resposta (centenas de factos) com árvores que quase ninguém pede.
- **Mexer já no backend.** O utilizador pediu só o lado do Drools.

---

## 3. Implementação

**Fase 1 — A árvore (A, B)**
- `explicacao/Arvore.java` (novo, público): constrói o nó de um facto a partir da lista de factos da resposta; `comRegras` liga ou desliga a `regraCompleta` (via `PorqueNao.regra`).
- `MotorDrools.como` e `MotorDrools.porque` (corrida do `infere` + `Arvore`, erros no envelope).
- Testes `ArvoreTest`: um facto concluído (regra, premissas de facto, `nao`, calculada), um dado, "já explicado", número inexistente, facto escrito que não está na base, motor não pronto.

**Fase 2 — Pergunta, cenários, predicados (C, D, E)**
- `MotorDrools.porquePergunta`: acha a pergunta na resposta, pega no `facto` dela e usa `Arvore` com regras, e junta `PorqueNao.regrasComCondicao`.
- `explicacao/Cenarios.java`: a classe sai de `consola/` (continua a servir a consola) e `MotorDrools.cenarios()` devolve o JSON; `MotorDrools.predicadosConcluidos()`.
- Testes: 67 cenários, o passo do `c6`, as sequências `base` e `normal`; a pergunta `shell_servico` do `c7` devolve `[32,33,34,50,51]`.

**Fase 3 — Consola e documentação (F)**
- `consola/Explicacoes` e `ConsolaDrools` passam a chamar `motor.como`/`porque`/`porquePergunta`/`cenarios`/`predicadosConcluidos` e a escrever a partir do JSON.
- `motores/drools/README.md`: uma secção "API do motor" com os métodos e o contrato; `README.md` da raiz se citar a API.

---

## 4. Verificação

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd -pl motores/drools verify
swipl motores/drools/ferramentas/gera_porque_nao.pl --verificar
```

- **Baseline:** o fim da F0005 (101 testes, `BUILD SUCCESS`).
- **Passa quando:** `BUILD SUCCESS` com os testes novos; a `ConsolaDroolsTest` e o `PorqueNaoTest` passam sem mudar o texto esperado (a consola escreve o mesmo a partir do JSON); o `ParidadeTest` continua a 21.

---

## 5. Riscos

| Risco | Como se valida que não aconteceu |
| :--- | :--- |
| **A consola muda de texto ao passar a escrever do JSON** | Os testes da F0005 (`ConsolaDroolsTest`, 22 testes) ficam como estão; se algum falhar, o desvio vê-se e corrige-se |
| **Cada chamada volta a correr o pedido inteiro** (o `como`, o `porque` e o `porqueNao` correm uma sessão cada) | Medir: o `infere` de 51 cenários demora ~0,15 s; fica dito no README que um cliente que pede várias explicações paga uma corrida por pedido |
| **O contrato JSON fica instável** (campos como `jaExplicado`, `regraCompleta`) | Os testes fixam os nomes dos campos; o contrato escreve-se no README antes de o backend o usar |
| **Facto "escrito" ambíguo** (um predicado com a mesma assinatura em várias origens) | O número é a forma sem ambiguidade; o facto escrito escolhe o primeiro, como a consola |

---

## 6. Ordem dos commits

| # | Commit | Inventário |
| :--- | :--- | :--- |
| 1 | `feat(drools): add the how and why trees to the engine API` | A, B |
| 2 | `feat(drools): add the question why, the scenarios and the concludable facts to the engine API` | C, D, E |
| 3 | `refactor(drools): write the console explanations from the engine API` | F |
| 4 | `docs(drools): document the engine API` | F |

---

## 7. Fora do âmbito / a quem passa

| Fica de fora | Para |
| :--- | :--- |
| Endpoints REST, `MotorInferencia` com os métodos novos, `DroolsAdapter` | Backend (resumo no fim desta tarefa) |
| O motor Prolog responder aos mesmos métodos | Quem mantém o Prolog / decisão da interface comum |
| Paginação ou limites para árvores muito grandes | Se a UI precisar |
