# Testes do UI

Verificam que o UI mostra exatamente o que o motor devolve: os nós e as setas do grafo, os tickets, as perguntas, o "Como?" de cada ticket e as vistas por cenário. Nada é comparado com valores escritos à mão. A referência é sempre a resposta do motor.

## Como correr

Com o backend a correr em `localhost:8080`, que também serve o UI:

```
npm install playwright && npx playwright install chromium   # uma vez
node testes/ui/correr.js            # as 10 respostas de referência do Prolog
node testes/ui/correr.js --vivo     # inferência total pedida agora aos dois motores
```

Sai com 0 se tudo estiver certo e com 1 se houver falhas, com a lista do que falhou.

## O que se verifica (`verificacoes.js`)

| | Verificação |
| --- | --- |
| A/B | Um nó por predicado da resposta (entrada, base, deduzido) e um nó de regras por predicado deduzido, com as mesmas contagens |
| C | Cada premissa numérica dá a seta premissa → regra → conclusão, e não há setas sem facto que as suporte |
| D | Tickets do painel = tickets da resposta (ativo, prioridade, regra, fechado) |
| E | Perguntas pendentes = perguntas da resposta |
| F | A animação acende todos os nós |
| G | O "Como?" de cada ticket mostra exatamente os factos deduzidos da prova devolvida pelo motor |
| H | Vistas por cenário: cada premissa está incluída e todos os tickets aparecem em pelo menos um cenário |

Para verificar que os testes apanham erros, basta estragar o UI de propósito (por exemplo, apagar uma seta na consola do browser e voltar a correr): o teste falha nessa seta.
