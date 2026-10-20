# F0007 — Consola Drools sem a opção "porque?" de factos

| Campo | Valor |
| :--- | :--- |
| Tipo | Feature |
| Issue | — (pedido do utilizador, 20/10) |
| Branch | `feature/drools-menu-sem-porque`, sobre `develop` @ `a50d92c` |

## Problema

A F0005 acrescentou à consola Drools a opção 10 "Explicar porque se concluiu um facto, com as regras (porque?)", que aceita qualquer facto. Na matéria (ENGCIA) o "porquê?" explica porque o sistema faz uma pergunta ao utilizador; explicar uma conclusão é o "como?". O menu do Prolog não tem essa opção: o "porque?" só existe durante as perguntas ao analista (opção 5). A opção 10 era, na prática, um "como?" com as regras escritas, e deslocava a numeração do Drools em relação à do Prolog.

## Plano

1. Tirar a opção 10 da `ConsolaDrools` e renumerar para ficar igual ao Prolog (0 a 13): porque não 10, mostrar factos 11, opções do motor 12, injetar todos 13.
2. Manter o "porque?" da opção 5 (`porquePergunta`), `MotorDrools.porque` e `Explicacoes.porque`, que a pergunta e a API usam.
3. Atualizar as mensagens que citam uma opção (`opcao 10` para o porque não, `opcao 12` para a profundidade), os testes e o README do módulo.

## Fora do âmbito

- Os endpoints `how`, `why` e `question-why` da API (F0006): não mudam.
- Mostrar, no "porque?" da pergunta, o que cada resposta permite concluir: ver `RESULT.md`.
