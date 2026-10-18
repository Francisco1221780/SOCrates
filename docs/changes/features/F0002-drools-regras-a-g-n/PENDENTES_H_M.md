# F0002 — O que falta corrigir nas regras H a M (#15)

Para quem mantém as regras H–M e as condições calculadas (#14, #15; entraram em `develop` pelo PR #31). Encontrado
pelo nível completo do `ParidadeTest` (F0002), que compara o Drools com as 92 regras contra a
`motores/prolog/referencia_prolog/` nas 10 variantes de cenários.

**Estado a 18/10:** as regras A–G e N dão exatamente o mesmo que o Prolog (nível A–G+N, 0 diferenças). Com as 92
juntas, todas as diferenças que restam têm a causa de raiz numa das regras abaixo. Estão listadas em
`ParidadeTest.CAUSAS_H_M` e `ERROS_H_M` (`motores/drools/src/test/java/cyberrangers/motores/drools/ParidadeTest.java`),
e por isso o build passa. **Quando corrigires uma causa, tira a linha respetiva dessas listas.** O teste não avisa
quando uma linha da lista já não tem diferença nenhuma; o objetivo é as duas listas ficarem vazias.

Como ver as diferenças de uma causa: tira a linha da lista e corre
`.\mvnw.cmd -pl motores/drools test -Dtest=ParidadeTest`. O relatório
(`motores/drools/target/surefire-reports/cyberrangers.motores.drools.ParidadeTest.txt`) lista cada facto diferente,
com a regra e as premissas dos dois lados.

---

## 1. Regras L põem um `Facto` dentro de `revisto/2` — o motor responde "erro" (prioridade alta)

**Onde:** `regras/L_ciclo_vida.drl`, nas linhas que fazem `Facto.de("revisto", $x, ...)`, com `$x` a ser o próprio
facto: R64 (l. 178), R65 (189), R66 (203), R67 (223), R69 (256), R70 (271), R71 (286), R72 (306).

**O que acontece:** o `TermosJson` não sabe escrever um `Facto` dentro de outro facto. Com as sequências
`base_e_atualizacoes` e `c35d_em_vez_de_35c`, o Drools responde
`{"estado":"erro", "erro":"erro do motor: termo nao suportado: exploracao_incerta(...)"}`. Isto chega à UI e ao
backend: estas sequências não dão resposta nenhuma.

**Correção:** o argumento tem de ser um `Termo` (o modelo da F0001: um facto dentro de outro é um termo composto):

```java
base.cria(kcontext, 64, p, Facto.de("revisto", new Termo($ei.getPredicado(), $ei.getArgs()), ...));
```

Depois disto, as comparações que procuram o facto revisto têm de aceitar o `Termo`:
- nas regras L (`arg(0) == $ep`, etc.), usar `$ep.representadoPor(arg(0))`, que aceita as duas formas (é o que as
  R13 e R30 já fazem);
- em `CondicoesCalculadas.semVetorExploravel` (l. 250, `Objects.equals(r.arg(0), va)`), também (ou o `revisto(...)`
  privado da `origemEvidencia`, que já aceita as duas).

**Na lista do teste:** `ERROS_H_M` (`"termo nao suportado: "`).

## 2. R43 não verifica a `origem_evidencia`

**Onde:** `regras/J_comunicacao_c2.drl`, R43 (l. 44). A condição `origem_evidencia(Ev,EvAtaque,Host,Vuln,ligada)`
está nas premissas mas não no LHS.

**O que acontece:** a R43 confirma compromissos que o Prolog não confirma. No c26 confirma o compromisso do
`web_srv_01`, isola a máquina, abre o ticket, e com isso supera e fecha outros tickets. No c11 confirma o do
`app_srv_01` um passo mais cedo do que devia (c11 em vez de c11b). É a causa de raiz da maior parte das diferenças:
cerca de 30 factos diferentes por variante.

**Correção:** como a R20 em `regras/F_confirmacao.drl`. Antes do `from`, um `Marca(...)` por cada facto que a
`origem_evidencia` lê (ver o comentário do início do `F_confirmacao.drl`):

```
    Marca(functor == "evento_processo/2")
    ... (as 6 marcas)
    $oe : Object() from CondicoesCalculadas.origemEvidencia($ev, $evAtaque, $host, $vuln, base)
    eval("ligada".equals($oe))
```

**Na lista do teste:** `CAUSAS_H_M` 43.

## 3. R35, R74 e R75 escrevem as premissas `avalia(...)` noutro formato

**Onde:** `regras/H_reconhecimento.drl`, R35 (l. 35–38); `regras/M_gravidade_prioridade.drl`, R74 (l. 36) e R75
(l. 46).

**O que acontece:** o `avalia/2` do `soc_motor.pl` guarda como premissa **o número do facto lido** (ex.: o facto
`portos_sondados(ev_j1,450)`). O Drools escreve `{"f":"portos_sondados","args":["ev_j1",">=",100]}`. A conclusão é
a mesma, mas a justificação não (AC15). Afeta `suspeita` e `origem_hostil` (R35) e os 16 `classe_ativo` (R74/R75).

**Correção:** ligar o facto no padrão e usar `Premissa.de($f)`:

```
    $ps : Facto(functor == "portos_sondados/2", arg(0) == $ev, $n : arg(1))
    eval(((Number) $n).longValue() >= 100)
    ...
    List<Premissa> p = List.of(Premissa.de($r), Premissa.de($ps), ...);
```

**Na lista do teste:** `CAUSAS_H_M` 35, 74, 75.

## 4. Regras com `eval` não se reavaliam quando chega um facto que a condição lê

**Onde:** R66 (`foraDoDestino`, `L_ciclo_vida.drl` l. 197) é a que aparece nas referências. Pela mesma razão, também
R37, R41, R43–R46 (`depois`), R67 (`semVetorExploravel`), R69 (`entre`) e R73 (`todosMotivosRevistos`).

**O que acontece:** quando o `destino` do alerta chega depois do alerta (c37b), o Prolog reavalia a R66 (o
`condicao_le/2` do `soc_suporte.txt`); o Drools não, porque um `eval` só se reavalia quando muda um padrão da própria
regra. O vetor não passa a histórico, e o incidente "exploracao_possivel" do `ev_ae1` não fecha. As outras ainda não
aparecem nos cenários, mas falham da mesma maneira se o facto lido chegar atrasado.

**Correção:** antes de cada `eval`, um `Marca(functor == "...")` por cada facto que o `condicao_le/2` lista para essa
condição (`depois`: `tempo/2` e `janela/2`; `fora_do_destino`: `destino/3`; `sem_vetor_exploravel` e
`todos_motivos_revistos`: `revisto/2`; `entre`: `tempo/2`). A `BaseFactos` já atualiza as marcas; é só pô-las nas
regras. O molde e a explicação estão no início de `regras/B_vetor.drl` e no `regras/README.md`.

**Na lista do teste:** `CAUSAS_H_M` 66.

---

## Ordem sugerida

1. **§1** primeiro: é a única que faz o motor responder "erro".
2. **§2**: depois dela, a maior parte das diferenças em cadeia (tickets, `superado`, `pergunta_retirada`) deve
   desaparecer sozinha.
3. **§4** e **§3**.

A cada passo, tirar a linha da lista e correr o `ParidadeTest`. Se aparecer uma diferença nova com causa A–G ou N,
o teste diz qual é, e é para corrigir no lado A–G (F0002).
