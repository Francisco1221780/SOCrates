# Impressão digital da base de conhecimento e das regras dos dois motores.
# Corre na raiz do repositório: python3 provas/impressao_base.py   (Windows: python provas\impressao_base.py)
# Se os dois colegas obtiverem os mesmos valores, têm a mesma base e as mesmas regras.
import hashlib, glob, json, re, subprocess
def h(p):
    return hashlib.sha256(open(p, 'rb').read().replace(b'\r\n', b'\n')).hexdigest()[:16]
try:
    print('commit:', subprocess.run(['git', 'log', '--oneline', '-1'], capture_output=True, text=True).stdout.strip())
except Exception:
    pass
for f in ['base_conhecimento/soc_regras.txt', 'base_conhecimento/soc_factos.txt', 'base_conhecimento/soc_suporte.txt',
          'backend/socrates/src/main/resources/generated/cenarios.json']:
    print(h(f), f)
regras = re.findall(r'^regra (\d+)', open('base_conhecimento/soc_regras.txt', encoding='utf-8').read(), re.M)
print('regras na base (soc_regras.txt):', len(regras))
drl = set()
for f in sorted(glob.glob('motores/drools/src/main/resources/regras/*.drl')):
    drl |= set(re.findall(r'^rule "(R\d+)', open(f, encoding='utf-8').read(), re.M))
print('regras no Drools (DRL):', len(drl))
for v in ['base', 'base_e_atualizacoes']:
    r = json.load(open(f'motores/prolog/referencia_prolog/{v}.json', encoding='utf-8'))['resposta']
    ded = [f for f in r['factos'] if f['origem'] == 'regra']
    print(f'referência {v}: {len(set(f["regra"] for f in ded))} regras disparadas, {len(ded)} deduções, {len(r["tickets"])} tickets, {len(r["perguntas"])} perguntas')
