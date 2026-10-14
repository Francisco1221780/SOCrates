# base_conhecimento

Fonte única da base de conhecimento do analista virtual de SOC (versão 5.1). É partilhada pelo motor Prolog, pelo Drools (via ficheiros gerados), pelos scripts de geração e pelos testes.

| Ficheiro / pasta | Conteúdo |
| --- | --- |
| `soc_factos.txt` | Factos fixos (272). É o ficheiro que se carrega; carrega os dois seguintes. |
| `soc_regras.txt` | As 75 regras. |
| `soc_suporte.txt` | Metaconhecimento e condições calculadas. |
| `soc_cenarios.pl` | Liga o nome de cada cenário (`c1`, `c2`, …) ao seu ficheiro. |
| `cenarios/` | Um ficheiro por cenário ou resposta do analista. |

Regras:

- Os ficheiros desta pasta têm de ficar juntos: o `soc_factos.txt` carrega os outros a partir da sua própria pasta.
- Isto é a única fonte para factos e cenários. Os JSON usados pelos motores e pelo backend (`motores/drools/src/main/resources/` e `backend/cyber/src/main/resources/generated/`) geram-se daqui e não se editam à mão.
- Mudar regras implica regenerar o metaconhecimento (`motores/prolog/ferramentas/gera_meta.pl`) e atualizar o DRL.

Estado: pasta criada; o conteúdo vem da branch `feature/motor-prolog`.
