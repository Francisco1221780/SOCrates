# regras

Regras DRL (as 92 regras da base v6, portadas por grupos), um ficheiro por grupo de `soc_regras.txt`. Escritas à mão: é o `ParidadeTest` que garante que dizem o mesmo que as regras Prolog (ver o README do módulo).

## Um ficheiro por grupo

O nome é a letra do grupo mais uma palavra do título, para os ficheiros ficarem pela ordem da base. Dentro do ficheiro, as regras ficam pela ordem do número.

| Ficheiro | Grupo em `soc_regras.txt` | Regras | Issue |
| --- | --- | --- | --- |
| `A_afetado.drl` | A. O ativo está afetado por uma vulnerabilidade? | R1–R3 | #13 |
| `B_vetor.drl` | B. O atacante consegue chegar à vulnerabilidade? | R4–R9 | #13 (R7 da #4) |
| `C_sem_impacto.drl` | C. Tentativa sem impacto | R10–R13 | #13 |
| `D_duvida.drl` | D. Na dúvida, não se conclui "sem impacto" | R14–R15 | #13 |
| `E_impactos.drl` | E. Que impactos pode o ataque ter nesta instalação? | R16–R19 | #13 |
| `F_confirmacao.drl` | F. Confirmação: o ataque resultou? | R20–R30 | #13 |
| `G_pergunta.drl` | G. Sensor do host em baixo: pergunta ao analista | R31–R34 | #13 |
| `H_reconhecimento.drl` | H. Reconhecimento e acesso a seguir | R35–R37 | #15 |
| `I_movimento_lateral.drl` | I. Políticas de acesso e movimento lateral | R38–R41 | #15 |
| `J_comunicacao_c2.drl` | J. Comunicação com comando e controlo | R42–R47 | #15 |
| `K_confirmacao_host.drl` | K. Confirmação no host de um compromisso ou exposição provável | R48–R52 | #15 |
| `L_ciclo_vida.drl` | L. Ciclo de vida: incidentes que fecham e conclusões históricas | R53–R73 | #15 |
| `M_gravidade_prioridade.drl` | M. Gravidade, prioridade e tickets | R74–R88 | #15 |
| `N_catalogo.drl` | N. Validação do catálogo | R89–R92 | #13 |

## Como se porta uma regra

Cada regra regista a justificação ao concluir, para dar o "Como?" no mesmo formato do Prolog. O molde está no início de `B_vetor.drl`:

```
rule "R20"
when
    $ip : Facto(functor == "impacto_possivel/4", $ev : arg(0), $alvo : arg(1), $vuln : arg(2), $impacto : arg(3))
    not Facto(functor == "impacto_excluido/5", arg(0) == $ev, arg(1) == $alvo, arg(2) == $vuln, arg(3) == $impacto)
    ...
    Marca(functor == "tempo/2")
    Marca(functor == "janela/2")
    eval(CondicoesCalculadas.depois($evHost, $ev, $janela, base))
    ...
then
    List<Premissa> p = List.of(Premissa.de($ip), Premissa.nao("impacto_excluido", $ev, $alvo, $vuln, $impacto, Variavel.nova()), ...);
    base.cria(kcontext, 20, p, Facto.de("impacto_confirmado", $alvo, $ev, $impacto));
end
```

- Um padrão por condição, pela ordem do `se` da regra Prolog. `nao X` passa a `not Facto(...)`; um `_` não entra no padrão.
- Uma condição calculada passa a `eval(CondicoesCalculadas.<nome>(..., base))`, ou a `from` quando liga uma variável (a `origem_evidencia` das R20–R23).
- **Antes de uma condição calculada, um `Marca(functor == ...)` por cada facto que o `condicao_le/2` do `soc_suporte.txt` diz que ela lê.** Assim a regra volta a avaliar-se quando um desses factos chega depois (ex.: o `tempo` de um evento atrasado), como no Prolog. Uma condição sem `condicao_le` (ex.: `porto_compativel`) não leva marca.
- Uma negação sobre um facto que vem dentro de outro (`nao revisto(exploracao_possivel(...), _)`) usa `$e.representadoPor(arg(0))`, que aceita o facto como `Termo` ou como `Facto`.
- O ENTAO não faz `insert`: chama `base.cria(kcontext, <número da regra>, premissas, facto)` por cada `cria_facto`.
- Premissas, uma por condição e pela mesma ordem (as `Marca` não contam):
  - `Premissa.de($f)` para um facto;
  - `Premissa.nao(...)` para uma negação, com `Variavel.nova()` no lugar de cada `_`;
  - `Premissa.calculada(...)` para uma condição calculada, com os argumentos já ligados (ex.: o resultado da `origem_evidencia`).
- O nome da regra é `"R<n>"` e leva por cima, em comentário, o texto da regra de `soc_regras.txt`.
- Cada ficheiro importa as classes de `socrates.motores.drools.modelo` (e de `...sessao` se usar condições calculadas) e declara `global socrates.motores.drools.sessao.BaseFactos base;`.

## Factos que passam de uma metade para a outra

As regras A–G e N (#13) e H–M (#15) estão em ficheiros separados, mas leem os factos umas das outras. Tirado das regras de `soc_regras.txt`:

| Facto | Concluído por | Usado por |
| --- | --- | --- |
| `vetor_aberto` | R4 | R66 (e a `origem_evidencia` da R43) |
| `exploracao_possivel` | R6 | R44, R64, R67 |
| `exploracao_incerta` | R7, R8, R9, R14, R15 | R45, R64, R65 |
| `tentativa_sem_impacto` | R5, R10, R11 | R65 |
| `impacto_possivel` / `impacto_excluido` | R16, R17, R19 / R18 | R43 |
| `impacto_confirmado` | R20, R32 | R55 |
| `impacto_provavel` | R22, R23, R24 | R49, R50 |
| `compromisso_confirmado` | R25 | R41, R52, R63 |
| `compromisso_provavel` | R27 | R47, R49 |
| `pergunta_analista` | R31 | R87, R88 |
| `sem_evidencia_observada` | R33 | R56 |
| `suspeita` | R34 | R36, R37 |
| `incidente` | R7–R9, R12, R14, R15, R19, R21, R26, R28–R31 | R48, R51, R53–R56, R60–R62, R68, R76–R80, R83, R84 |
| `motivo` | R7–R9, R19, R21–R24, R26, R33 | R52, R54, R57–R59, R69–R72 |

No outro sentido (H–M → A–G):

| Facto | Concluído por | Usado por |
| --- | --- | --- |
| `revisto` | R64–R67, R69–R72 | R13, R30 (`nao revisto(...)`) |
| `origem_hostil` | R35 | R13 |
| `impacto_confirmado` | R50 | R25, R26 |
| `compromisso_confirmado` | R43, R49 | R29 |

Estado: todas as regras portadas. A–G e N dão os mesmos factos, regras e premissas que o Prolog nas 10 variantes (nível A–G+N do `ParidadeTest`). Com as 92 juntas, as diferenças que restam têm causa nas regras H–M e estão listadas, com a razão, em `ParidadeTest.CAUSAS_H_M` e `ERROS_H_M`.
