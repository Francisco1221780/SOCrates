#!/usr/bin/env python3
"""Paridade Prolog x Drools pela API (issue #16), sem passar pelo UI.

Envia o MESMO pedido aos dois motores e compara as respostas pelo conteúdo
(nunca pelos números dos factos nem pela ordem de disparo), nos dois níveis do plano:
  - Nível A (AC14): os mesmos incidentes, tickets e perguntas
      incidente: chave id; compara tipo, ativo, impactados
      ticket:    chave incidente + ativo; compara ação, gravidade, prioridade, fechado
      pergunta:  chave ativo + evento + pergunta
  - Nível B (AC15): as mesmas conclusões, cada uma com a mesma regra e as mesmas premissas
      cada facto vira predicado(args); cada premissa numérica vira o conteúdo do facto para onde aponta.
As chaves são as mesmas que o UI usa no Comparador.

Uso:
  python3 paridade.py                       # vista global (todas as sequências do catálogo)
  python3 paridade.py --cenario c2          # um cenário com a cadeia "depende"
  python3 paridade.py --todos               # cada cenário das sequências, um a um
  python3 paridade.py --referencias         # as variantes de motores/prolog/referencia_prolog: Prolog e Drools contra a referência
  python3 paridade.py --url http://localhost:8080 --guardar pasta/   # guarda pedido e respostas em JSON
Sai com código 0 se os dois motores derem o mesmo conteúdo, 1 se houver diferenças, 2 se houver erro.
"""
import argparse, hashlib, json, os, sys, urllib.request, urllib.error
from collections import Counter


def http(url, body=None):
    req = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'} if body else {})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except urllib.error.URLError as e:
        print(f'Sem ligação a {url}: {e.reason} (o pedido não chegou ao servidor)')
        sys.exit(2)


def termo(t):
    if t is None:
        return 'null'
    if isinstance(t, list):
        return '[' + ','.join(termo(x) for x in t) + ']'
    if isinstance(t, dict) and 'f' in t:
        return t['f'] + '(' + ','.join(termo(x) for x in t.get('args', [])) + ')'
    if isinstance(t, dict):
        return json.dumps(t, ensure_ascii=False, separators=(',', ':'))
    if isinstance(t, bool):
        return 'true' if t else 'false'
    if isinstance(t, float) and t.is_integer():
        return str(int(t))
    return str(t)


def chave(f):
    return f['predicado'] + '(' + ','.join(termo(a) for a in f['args']) + ')'


def canonica(resp):
    """{facto: (origem, regra, premissas ordenadas por conteúdo)} — independente dos números."""
    por_num = {f['numero']: f for f in resp['factos']}
    out = {}
    for f in resp['factos']:
        prem = sorted(chave(por_num[p]) if isinstance(p, int) and p in por_num else ('?' + str(p) if isinstance(p, int) else termo(p))
                      for p in f.get('premissas', []))
        out[chave(f)] = (f['origem'], f.get('regra'), tuple(prem))
    return out


def nivel_a(pl, dr):
    """AC14: {'incidentes'|'tickets'|'perguntas': (iguais, só Prolog, só Drools, dados diferentes)}"""
    def cmp(ka, kb, sig):
        A = {ka(x): x for x in pl_l}
        B = {kb(x): x for x in dr_l}
        ig = [k for k in A if k in B and sig(A[k]) == sig(B[k])]
        dif = [k for k in A if k in B and sig(A[k]) != sig(B[k])]
        return ig, [k for k in A if k not in B], [k for k in B if k not in A], dif
    out = {}
    pl_l, dr_l = pl.get('incidentes', []), dr.get('incidentes', [])
    out['incidentes'] = cmp(lambda i: termo(i.get('id')), lambda i: termo(i.get('id')),
                            lambda i: (termo(i.get('tipo')), termo(i.get('ativo')), termo(i.get('impactados'))))
    pl_l, dr_l = pl.get('tickets', []), dr.get('tickets', [])
    kt = lambda t: termo(t.get('incidente')) + '|' + termo(t.get('ativo'))
    out['tickets'] = cmp(kt, kt, lambda t: (termo(t.get('acao')), termo(t.get('gravidade')), termo(t.get('prioridade')), termo(t.get('fechado'))))
    pl_l, dr_l = pl.get('perguntas', []), dr.get('perguntas', [])
    kp = lambda q: f"{termo(q.get('ativo'))}|{termo(q.get('evento'))}|{termo(q.get('pergunta'))}"
    out['perguntas'] = cmp(kp, kp, lambda q: ())
    return out


def resumo_hash(canon):
    txt = json.dumps(sorted([k, list(v)] for k, v in canon.items()), ensure_ascii=False, separators=(',', ':'))
    return hashlib.sha256(txt.encode()).hexdigest()


def passos_de(cat, cenario):
    por_id = {c['id']: c for c in cat['cenarios']}
    if cenario == 'all':
        ids = []
        for seq in cat['sequencias'].values():
            for i in seq:
                if i not in ids:
                    ids.append(i)
        return [por_id[i]['passo'] for i in ids]
    out, visto = [], set()
    def visita(i):
        if i in visto:
            return
        visto.add(i)
        c = por_id[i]
        for d in c.get('depende', []):
            visita(d)
        out.append(c['passo'])
    visita(cenario)
    return out


def compara(url, cat, cenario, guardar=None, detalhe=True):
    corpo = json.dumps({'versaoKB': cat['versaoKB'], 'passos': passos_de(cat, cenario)}, ensure_ascii=False, separators=(',', ':')).encode()  # os mesmos bytes que o UI envia
    h_pedido = hashlib.sha256(corpo).hexdigest()
    resp = {}
    for m in ('prolog', 'drools'):
        st, raw = http(f'{url}/api/{m}/infer', corpo)
        if st != 200:
            print(f'[{cenario}] ERRO {m}: HTTP {st} {raw[:200]!r}')
            return 2
        resp[m] = json.loads(raw)
        if guardar:
            os.makedirs(guardar, exist_ok=True)
            open(os.path.join(guardar, f'{cenario}_{m}.json'), 'wb').write(raw)
    if guardar:
        open(os.path.join(guardar, f'{cenario}_pedido.json'), 'wb').write(corpo)
    A, B = canonica(resp['prolog']), canonica(resp['drools'])
    hA, hB = resumo_hash(A), resumo_hash(B)
    so_pl = [k for k in A if k not in B]
    so_dr = [k for k in B if k not in A]
    just = [k for k in A if k in B and A[k] != B[k]]
    na = nivel_a(resp['prolog'], resp['drools'])
    igual_a = all(not (s or d or x) for _, s, d, x in na.values())
    igual_b = not so_pl and not so_dr and not just
    igual = igual_a and igual_b
    print(f'[{cenario}] pedido sha256={h_pedido[:16]}…  ({len(passos_de(cat, cenario))} passos, o mesmo corpo para os dois motores)')
    for m, C, h in (('prolog', A, hA), ('drools', B, hB)):
        r = resp[m]
        n_reg = sum(1 for v in C.values() if v[0] == 'regra')
        print(f'  {m:7s} estado={r["estado"]:17s} factos={len(C):5d} deduzidos={n_reg:4d} incidentes={len(r.get("incidentes", [])):3d} tickets={len(r["tickets"]):3d} perguntas={len(r["perguntas"]):2d}  conteúdo sha256={h[:16]}…')
    print(f'  Nível A (AC14) {"✓" if igual_a else "✕"}  ' + '   '.join(
        f'{nome}: {len(ig)} iguais' + (f', {len(s)} só Prolog' if s else '') + (f', {len(d)} só Drools' if d else '') + (f', {len(x)} com dados diferentes' if x else '')
        for nome, (ig, s, d, x) in na.items()))
    print(f'  Nível B (AC15) {"✓" if igual_b else "✕"}  conclusões só no Prolog: {len(so_pl)}   só no Drools: {len(so_dr)}   mesma conclusão, justificação diferente: {len(just)}')
    print('  RESULTADO:', 'IGUAIS (o mesmo conteúdo nos dois níveis)' if igual else 'DIFERENTES' + ('' if igual_a else ' no nível A') + ('' if igual_b else (' e' if not igual_a else '') + ' no nível B'))
    if detalhe and not igual_a:
        for nome, (ig, s, d, x) in na.items():
            for k in s[:8]:
                print(f'  {nome} só no Prolog: {k}')
            for k in d[:8]:
                print(f'  {nome} só no Drools: {k}')
            for k in x[:8]:
                print(f'  {nome} com dados diferentes: {k}')
    if detalhe and not igual_b:
        c = Counter('R' + str(A[k][1]) for k in so_pl if A[k][0] == 'regra')
        if c:
            print('  só no Prolog, por regra:', ', '.join(f'{r}×{n}' for r, n in sorted(c.items(), key=lambda x: int(x[0][1:]))))
        for k in so_dr[:10]:
            print('  só no Drools:', k, '← R' + str(B[k][1]))
        for k in just[:10]:
            print(f'  justificação diferente: {k}\n      prolog R{A[k][1]}: {"; ".join(A[k][2])}\n      drools R{B[k][1]}: {"; ".join(B[k][2])}')
    return 0 if igual else 1


def referencias(url, cat, pasta):
    import glob
    por_id = {c['id']: c for c in cat['cenarios']}
    ficheiros = sorted(glob.glob(os.path.join(pasta, '*.json')))
    if not ficheiros:
        print('Sem ficheiros de referência em', pasta); return 2
    print(f'{"variante":32s} {"Prolog = referência":22s} {"Drools = referência":22s}')
    tudo_ok = True
    for f in ficheiros:
        ref = json.load(open(f))
        corpo = json.dumps({'versaoKB': cat['versaoKB'], 'passos': [por_id[i]['passo'] for i in ref['cenarios']]}, ensure_ascii=False, separators=(',', ':')).encode()
        R = canonica(ref['resposta'])
        linha = []
        for m in ('prolog', 'drools'):
            st, raw = http(f'{url}/api/{m}/infer', corpo)
            if st != 200:
                linha.append(f'erro HTTP {st}'); tudo_ok = False; continue
            C = canonica(json.loads(raw))
            so_ref = sum(1 for k in R if k not in C); so_m = sum(1 for k in C if k not in R); just = sum(1 for k in R if k in C and R[k] != C[k])
            ok = not (so_ref or so_m or just)
            tudo_ok &= ok
            linha.append('✓ igual' if ok else f'✕ falta {so_ref} · a mais {so_m} · justif. {just}')
        print(f'{os.path.basename(f):32s} {linha[0]:22s} {linha[1]:22s}')
    return 0 if tudo_ok else 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--url', default='http://localhost:8080')
    ap.add_argument('--cenario', default='all')
    ap.add_argument('--todos', action='store_true')
    ap.add_argument('--guardar')
    ap.add_argument('--referencias', nargs='?', const=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'motores', 'prolog', 'referencia_prolog'),
                    help='pasta com as respostas de referência do Prolog (por omissão motores/prolog/referencia_prolog)')
    a = ap.parse_args()
    st, raw = http(a.url + '/api/cenarios')
    if st != 200:
        print('Sem catálogo:', st); sys.exit(2)
    cat = json.loads(raw)
    if a.referencias:
        sys.exit(referencias(a.url, cat, a.referencias))
    if a.todos:
        ids = []
        for seq in cat['sequencias'].values():
            for i in seq:
                if i not in ids:
                    ids.append(i)
        res = {i: compara(a.url, cat, i, a.guardar, detalhe=False) for i in ids}
        print(f'\n{sum(1 for v in res.values() if v == 0)}/{len(res)} cenários iguais nos dois motores')
        print('diferentes:', ' '.join(i for i, v in res.items() if v == 1) or '—')
        sys.exit(0 if all(v == 0 for v in res.values()) else 1)
    sys.exit(compara(a.url, cat, a.cenario, a.guardar))


if __name__ == '__main__':
    main()
