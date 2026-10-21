# Lê do stdin a resposta JSON de um motor e mostra o estado, os tickets e as perguntas.
import json, sys
txt = sys.stdin.read()
try:
    r = json.loads(txt)
except Exception:
    print('A saída não é JSON. Primeiras linhas:'); print(txt[:800]); sys.exit(1)
print('estado:', r.get('estado'))
print('tickets:', len(r.get('tickets', [])))
print('perguntas:', len(r.get('perguntas', [])))
for q in r.get('perguntas', []):
    print('  -', q['ativo'], q['evento'], q['pergunta'])
