// Testes do UI contra as respostas do motor (issues #5, #16, #17).
// Abre o UI servido pelo backend, injeta cada resposta e verifica, facto a facto, o que o UI mostra.
//
//   npm install playwright && npx playwright install chromium     (uma vez)
//   node testes/ui/correr.js                     # as 10 respostas de referência do Prolog (motores/prolog/referencia_prolog)
//   node testes/ui/correr.js --vivo              # pede a inferência total aos dois motores e verifica o UI com essas respostas
//   node testes/ui/correr.js --url http://localhost:8080
//
// Verificações (verificacoes.js):
//   A/B nós: um por predicado da resposta (entrada, base, deduzido) e um nó de regras por predicado deduzido; contagens iguais
//   C   setas: cada premissa numérica dá a seta premissa → regra → conclusão; nenhuma seta sem facto que a suporte
//   D   tickets do painel = tickets da resposta (ativo, prioridade, regra, fechado)
//   E   perguntas pendentes = perguntas da resposta
//   F   a animação acende todos os nós
//   G   o "Como?" de cada ticket mostra exatamente os factos deduzidos da prova devolvida pelo motor
//   H   vistas por cenário: cada premissa está incluída e todos os tickets aparecem em pelo menos um cenário
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const args = process.argv.slice(2);
const URL = (args.includes('--url') ? args[args.indexOf('--url') + 1] : 'http://localhost:8080').replace(/\/$/, '');
const VIVO = args.includes('--vivo');
const RAIZ = path.resolve(__dirname, '..', '..');

(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.goto(URL + '/'); await p.waitForTimeout(2500);
  await p.addScriptTag({ path: path.join(__dirname, 'verificacoes.js') });
  const casos = [];
  if (VIVO) {
    casos.push(...await p.evaluate(async () => {
      const cat = await fetch('/api/cenarios').then(r => r.json());
      const ids = []; Object.values(cat.sequencias).flat().forEach(i => { if (!ids.includes(i)) ids.push(i); });
      const by = Object.fromEntries(cat.cenarios.map(c => [c.id, c]));
      const passos = ids.map(i => by[i].passo);
      const out = [];
      for (const m of ['prolog', 'drools']) {
        const r = await fetch(`/api/${m}/infer`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ versaoKB: cat.versaoKB, passos }) }).then(r => r.json());
        out.push({ nome: `vivo · ${m}`, motor: m, resposta: r, passos });
      }
      return out;
    }));
  } else {
    const cat = JSON.parse(fs.readFileSync(path.join(RAIZ, 'backend/socrates/src/main/resources/generated/cenarios.json')));
    const by = Object.fromEntries(cat.cenarios.map(c => [c.id, c]));
    const pasta = path.join(RAIZ, 'motores/prolog/referencia_prolog');
    for (const f of fs.readdirSync(pasta).filter(x => x.endsWith('.json')).sort()) {
      const ref = JSON.parse(fs.readFileSync(path.join(pasta, f)));
      casos.push({ nome: f, motor: 'prolog', resposta: ref.resposta, passos: ref.cenarios.map(i => by[i].passo) });
    }
  }
  let ok = 0;
  for (const c of casos) {
    const r = await p.evaluate(async ({ res, passos, motor }) => {
      res._passos = passos; res._at = Date.now(); res.motor = motor;
      S.engine = motor; S.scen = 'all'; S.v64Answers = {};
      S.engineResults = { prolog: {}, drools: {} }; S.engineResults[motor].all = res;
      await applyRealEngineResultToGraph(res, motor, false);
      return { a: await window.__uiCheck(res), v: window.__viewCheck(res) };
    }, { res: c.resposta, passos: c.passos, motor: c.motor });
    const bom = r.a.ok && r.v.ok; if (bom) ok++;
    console.log(`${bom ? 'OK   ' : 'FALHA'} ${c.nome.padEnd(32)} nós=${r.a.st.nos} setas=${r.a.st.setas} tickets=${r.a.st.tickets} perguntas=${r.a.st.perguntas} explicações=${r.a.st.explicacoes} vistas=${r.v.vistas}`);
    [...r.a.falhas, ...r.v.falhas].slice(0, 12).forEach(x => console.log('      - ' + x));
  }
  console.log(`\n${ok}/${casos.length} sem falhas · erros JS na página: ${errs.length}`);
  errs.slice(0, 5).forEach(e => console.log('  JS:', e));
  await b.close();
  process.exit(ok === casos.length && !errs.length ? 0 : 1);
})();
