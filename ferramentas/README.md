# ferramentas

## `paridade.py`: paridade Prolog × Drools pela API (#16)

Envia o mesmo pedido aos dois motores e compara as respostas pelo conteúdo, nunca pelos números dos factos nem pela ordem de disparo. Não usa o UI: é a prova independente de que os motores concordam (ou não). Só precisa de Python 3 e do backend a correr.

```
python3 ferramentas/paridade.py                  # vista global (todas as sequências do catálogo)
python3 ferramentas/paridade.py --cenario c2     # um cenário, com a cadeia "depende"
python3 ferramentas/paridade.py --todos          # cada cenário das sequências, um a um
python3 ferramentas/paridade.py --referencias    # as variantes de motores/prolog/referencia_prolog: Prolog e Drools contra a referência
python3 ferramentas/paridade.py --guardar provas/   # guarda o pedido e as duas respostas em JSON
```

- **Nível A (AC14):** os mesmos incidentes, tickets e perguntas. Incidente pela chave `id` (compara tipo, ativo, impactados); ticket pela chave incidente + ativo (compara ação, gravidade, prioridade, fechado); pergunta pela chave ativo + evento + pergunta. As chaves são as do Comparador do UI.
- **Nível B (AC15):** as mesmas conclusões e, para cada uma, a mesma regra e as mesmas premissas. Cada facto passa a `predicado(args)` e cada premissa passa ao conteúdo do facto para onde aponta.
- Mostra o SHA-256 do pedido e um SHA-256 do conteúdo de cada motor. São calculados da mesma forma que no comparador do UI: os hashes têm de coincidir.
- Código de saída: 0 iguais, 1 diferentes, 2 erro (sem ligação, HTTP ≠ 200).
