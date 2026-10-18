# Features

Registo das features: cada uma tem o plano aprovado (`README.md`) e o resultado (`RESULT.md`) na sua pasta.
Numeração sequencial, nunca reutilizada.

| Id | Título | Estado | Data | Branch | Pontos em aberto |
| :--- | :--- | :--- | :--- | :--- | :--- |
| [F0001](F0001-drools-base/README.md) ([resultado](F0001-drools-base/RESULT.md)) | Drools: base (modelo de factos, justificações, DroolsAdapter) — #4 | Implementado | 2026-10-15 | `feature/drools-base` | Escolha da justificação entre vias para a #16; `passo` com `id` numérico |
| [F0002](F0002-drools-regras-a-g-n/README.md) ([resultado](F0002-drools-regras-a-g-n/RESULT.md)) | Drools: regras dos grupos A a G e N — #13 | Implementado | 2026-10-18 | `feature/drools-regras-a-g-n` | Causas H–M no nível completo para a #15 ([pendentes](F0002-drools-regras-a-g-n/PENDENTES_H_M.md): regras L em erro, R43, R35/R74/R75, R66); justificação entre regras diferentes para a #16 |
| [F0003](F0003-consola-drools/README.md) ([resultado](F0003-consola-drools/RESULT.md)) | Consola interativa do motor Drools (menu do Prolog) e `cenarios.json` no módulo | Implementado | 2026-10-18 | `feature/drools-regras-a-g-n` | Experimentar com o teclado no IntelliJ; ciclo `MotorDrools` ↔ `consola` (as regras do `porque` passaram a vir das regras na F0004) |
| [F0004](F0004-menu-prolog-porque-nao/README.md) ([resultado](F0004-menu-prolog-porque-nao/RESULT.md)) | Consola Drools com o menu do Prolog (0–13) e o "porque não?" | Implementado | 2026-10-18 | `feature/drools-regras-a-g-n` | Condições calculadas em três sítios (Prolog, DRL, "porque não?"); arranque com a base carregada |
