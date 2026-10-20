# base_conhecimento

Fonte única da base de conhecimento do analista virtual de SOC (versão 6). É partilhada pelo motor Prolog, pelo Drools (via ficheiros gerados), pelos scripts de geração e pelos testes.

| Ficheiro / pasta | Conteúdo |
| --- | --- |
| `soc_factos.txt` | Factos fixos (314). É o ficheiro que se carrega; carrega os dois seguintes. |
| `soc_regras.txt` | As 92 regras. |
| `soc_suporte.txt` | Metaconhecimento e condições calculadas (inclui `condicao_le/2`). |
| `soc_cenarios.pl` | Liga o nome de cada cenário (`c1`, `c2`, …) ao seu ficheiro e define as sequências (`sequencia_base`, `sequencia_atualizacoes`, `sequencia_normal`). |
| `cenarios/` | Um ficheiro por cenário ou resposta do analista (67), com o `indice.txt`. |

Regras:

- Os ficheiros desta pasta têm de ficar juntos: o `soc_factos.txt` carrega os outros a partir da sua própria pasta.
- Isto é a única fonte para factos e cenários. Os JSON usados pelos motores e pelo backend (`motores/drools/src/main/resources/` e `backend/socrates/src/main/resources/generated/`) geram-se daqui e não se editam à mão.
- Mudar regras implica regenerar o metaconhecimento (`motores/prolog/ferramentas/gera_meta.pl`) e atualizar o DRL.
- O `soc_cenarios.pl` foi escrito a partir do `cenarios/indice.txt`; se o índice mudar, atualiza-se também.
- O número da versão (6) está no cabeçalho do `soc_factos.txt` e em `MotorDrools.VERSAO_KB`; mudam juntos.
