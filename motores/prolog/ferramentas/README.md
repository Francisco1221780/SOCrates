# ferramentas

Scripts Prolog que geram ficheiros a partir da base de conhecimento. O que sai daqui não se edita à mão.

| Script | Gera |
| --- | --- |
| `gera_meta.pl` | Metaconhecimento (`facto_dispara_regras/2`) a partir de `soc_regras.txt`. Voltar a correr sempre que as regras mudam. |
| `gera_json.pl` | *(por criar)* `factos_iniciais.json` para `motores/drools/src/main/resources/` e `cenarios.json` para `backend/cyber/src/main/resources/generated/`. |

Um teste deve falhar se os JSON gerados estiverem desatualizados face aos `.txt`.
