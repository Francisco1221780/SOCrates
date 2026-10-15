# regras

Regras DRL (as 92 regras da base v6, portadas por grupos), um ficheiro por grupo de `soc_regras.txt`. Escritas à mão: é o comparador que garante que dizem o mesmo que as regras Prolog.

## Um ficheiro por grupo

O nome é a letra do grupo mais uma palavra do título, para os ficheiros ficarem pela ordem da base. Dentro do ficheiro, as regras ficam pela ordem do número.

| Ficheiro | Grupo em `soc_regras.txt` | Regras | Issue |
| --- | --- | --- | --- |
| `A_afetado.drl` | A. O ativo está afetado por uma vulnerabilidade? | R1–R3 | #13 |
| `B_vetor.drl` | B. O atacante consegue chegar à vulnerabilidade? | R4–R9 | #13 (R7 já portada, #4) |
| `C_sem_impacto.drl` | C. Tentativa sem impacto | R10–R13 | #13 |
| `D_duvida.drl` | D. Na dúvida, não se conclui "sem impacto" | R14–R15 | #13 |
| `E_impactos.drl` | E. Que impactos pode o ataque ter nesta instalação? | R16–R19 | #13 |
| `F_confirmacao.drl` | F. Confirmação: o ataque resultou? | R20–R30 | #13 |
| `G_pergunta.drl` | G. Sensor do host em baixo: pergunta ao analista | R31–R34 | #13 |
| `H_reconhecimento.drl` | H. Reconhecimento e acesso a seguir | R35–R37 | #15 |
| `I_lateral.drl` | I. Políticas de acesso e movimento lateral | R38–R41 | #15 |
| `J_c2.drl` | J. Comunicação com comando e controlo | R42–R47 | #15 |
| `K_confirmacao_host.drl` | K. Confirmação no host de um compromisso ou exposição provável | R48–R52 | #15 |
| `L_ciclo_vida.drl` | L. Ciclo de vida: incidentes que fecham e conclusões históricas | R53–R73 | #15 |
| `M_tickets.drl` | M. Gravidade, prioridade e tickets | R74–R88 | #15 |
| `N_catalogo.drl` | N. Validação do catálogo | R89–R92 | #13 |

## Como se porta uma regra

Cada regra regista a justificação ao concluir, para dar o "Como?" no mesmo formato do Prolog. O molde está em `B_vetor.drl` (R7):

```
rule "R7"
when
    $v : Facto(functor == "vetor_aberto/4", $ev : arg(0), $alvo : arg(1), $inst : arg(2), $vuln : arg(3))
    not Facto(functor == "versao/2", arg(0) == $inst)
then
    List<Premissa> p = List.of(Premissa.de($v), Premissa.nao("versao", $inst, Variavel.nova()));
    base.cria(kcontext, 7, p, Facto.de("exploracao_incerta", $ev, $alvo, $vuln));
    ...
end
```

- Um padrão por condição, pela ordem do `se` da regra Prolog. `nao X` passa a `not Facto(...)`.
- O ENTAO não faz `insert`: chama `base.cria(kcontext, <número da regra>, premissas, facto)` por cada `cria_facto`.
- Premissas, uma por condição e pela mesma ordem:
  - `Premissa.de($f)` para um facto;
  - `Premissa.nao(...)` para uma negação, com `Variavel.nova()` no lugar de cada `_`;
  - `Premissa.calculada(...)` para uma condição calculada.
- O nome da regra é `"R<n>"` e leva por cima, em comentário, o texto da regra de `soc_regras.txt`.
- Cada ficheiro importa as classes de `cyberrangers.motores.drools.modelo` e declara `global cyberrangers.motores.drools.sessao.BaseFactos base;`.

Estado: R7 portada (issue #4). Os grupos A a G e N ficam para a #13; H a M para a #15.
