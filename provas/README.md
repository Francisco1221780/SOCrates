# Provas: perguntas e tickets sem o UI

Pedidos construídos a partir de `backend/socrates/src/main/resources/generated/cenarios.json` (cada cenário com as suas dependências, campo `depende`).

| Pedido | Cenários | Perguntas esperadas | Tickets |
|---|---|---|---|
| `pedido_c3.json` | c1, c2, c3 | 1: db_srv_01 / ev_i1 / compromisso_no_host (R48) | 5 |
| `pedido_c7.json` | c1 a c7 | 2: a anterior + web_srv_04 / ev_e1 / shell_servico (R31) | 10 |
| `pedido_base_e_atualizacoes.json` | os 50 da referência | 8 (as de `motores/prolog/referencia_prolog/base_e_atualizacoes.json`) | 65 |

Resultado obtido a 21/10/2026 com os dois motores, pela API, iguais nos dois.

## Prolog, sem backend (na raiz do repositório)
Windows: `Get-Content provas\pedido_c3.json | swipl motores/prolog/wrapper.pl base_conhecimento`
macOS/Linux: `swipl motores/prolog/wrapper.pl base_conhecimento < provas/pedido_c3.json`

## Drools, sem backend
Windows: `Get-Content provas\pedido_c3.json | .\mvnw.cmd -q -pl motores/drools compile exec:java`
macOS/Linux: `./mvnw -q -pl motores/drools compile exec:java < provas/pedido_c3.json`

Procurar na resposta `"perguntas"` e `"tickets"`.

## Pela API, os dois motores de uma vez
`python3 ferramentas/paridade.py --cenario c3` (ou sem argumentos para a vista global).

Atenção: correr um cenário sem as dependências (por exemplo só c3, sem c1 e c2) dá menos conclusões: a pergunta de db_srv_01 precisa do ataque de c2 e do login de c3.
