// Verificações do UI contra uma resposta do motor (corre na página). Devolve {ok, falhas[], stats}
window.__uiCheck = async function(res) {
  const falhas = [], st = {};
  const F = res.factos, byNum = new Map(F.map(f => [f.numero, f]));
  const nid = f => f.origem === 'inicial' ? 'kb' : (f.origem === 'entrada' ? 'in_' : 'd_') + f.predicado;
  const fail = (m) => { if (falhas.length < 40) falhas.push(m); };
  // A/B. nós: um por predicado (entrada / deduzido) + um nó R por predicado deduzido + KB
  const esperado = new Map();
  F.forEach(f => { const k = nid(f); esperado.set(k, (esperado.get(k) || 0) + 1); });
  const regrasDe = new Map();
  F.filter(f => f.origem === 'regra').forEach(f => { const s = regrasDe.get(f.predicado) || new Set(); s.add(f.regra); regrasDe.set(f.predicado, s); });
  esperado.forEach((n, k) => {
    const nd = byId[k];
    if (!nd) return fail(`nó em falta: ${k}`);
    if (nd.count !== n) fail(`contagem errada em ${k}: UI ${nd.count} vs motor ${n}`);
  });
  regrasDe.forEach((rs, p) => {
    const nd = byId['r_' + p];
    if (!nd) return fail(`nó de regras em falta: r_${p}`);
    const ui = new Set(nd.rules);
    if (ui.size !== rs.size || [...rs].some(r => !ui.has(r))) fail(`regras de ${p}: UI ${[...ui]} vs motor ${[...rs]}`);
  });
  const extra = NODES.filter(n => !esperado.has(n.id) && !(n.id.startsWith('r_') && regrasDe.has(n.id.slice(2))));
  extra.forEach(n => fail(`nó a mais (não vem do motor): ${n.id}`));
  st.nos = NODES.length;
  // C. setas: cada premissa numérica -> R(conclusão) -> conclusão; e nenhuma seta sem facto que a suporte
  const precisa = new Set();
  F.filter(f => f.origem === 'regra').forEach(f => {
    precisa.add(`r_${f.predicado}->${nid(f)}`);
    (f.premissas || []).forEach(p => { if (typeof p === 'number' && byNum.has(p) && nid(byNum.get(p)) !== nid(f)) precisa.add(`${nid(byNum.get(p))}->r_${f.predicado}`); });
  });
  const temos = new Set(EDGES.map(([a, b]) => a + '->' + b));
  precisa.forEach(k => { if (!temos.has(k)) fail(`seta em falta: ${k}`); });
  temos.forEach(k => { if (!precisa.has(k)) fail(`seta sem suporte no motor: ${k}`); });
  st.setas = EDGES.length;
  // D. tickets do painel = tickets da resposta
  if (TICKETS.length !== res.tickets.length) fail(`tickets: UI ${TICKETS.length} vs motor ${res.tickets.length}`);
  res.tickets.forEach((t, i) => {
    const u = TICKETS.find(x => x.facto === t.facto);
    if (!u) return fail(`ticket em falta (facto ${t.facto})`);
    if (u.alvo !== t.ativo) fail(`ticket ${t.facto}: ativo ${u.alvo} vs ${t.ativo}`);
    if (u.prio !== String(t.prioridade).toUpperCase()) fail(`ticket ${t.facto}: prioridade ${u.prio} vs ${t.prioridade}`);
    if (u.regra !== 'R' + byNum.get(t.facto).regra) fail(`ticket ${t.facto}: regra ${u.regra} vs R${byNum.get(t.facto).regra}`);
    if (!!u.fechado !== !!t.fechado) fail(`ticket ${t.facto}: fechado ${u.fechado} vs ${t.fechado}`);
  });
  st.tickets = TICKETS.length;
  // E. perguntas pendentes = perguntas da resposta
  const pend = getPendingEngineQuestions().length + Object.keys(S.v64Answers || {}).length;
  if (pend !== res.perguntas.length) fail(`perguntas: UI ${pend} vs motor ${res.perguntas.length}`);
  st.perguntas = res.perguntas.length;
  // F. a animação chega a todos os nós
  const snaps = S.timelineSnapshots;
  const last = snaps[snaps.length - 1];
  const nao = NODES.filter(n => !last.lit.has(n.id)).map(n => n.id);
  if (nao.length) fail(`nós que a animação não acende: ${nao.join(', ')}`);
  st.etapas = snaps.length;
  // G. explicação "como?" de cada ticket = prova devolvida pelo motor (factos e regras)
  let expl = 0;
  for (const t of res.tickets) {
    const prova = new Set(), seen = new Set();
    const walk = n => { if (seen.has(n)) return; seen.add(n); const f = byNum.get(n); if (!f || f.origem !== 'regra') return; prova.add(n); (f.premissas || []).forEach(p => typeof p === 'number' && walk(p)); };
    walk(t.facto);
    S.v61Exp = t.facto; S.inspTab = 'como'; S.v71ComoModo = 'arvore'; S.sel = null; S.selTicket = null; inspect();
    const ui = new Set([...document.querySelectorAll('#insp .v71-step[data-facto]')].map(d => Number(d.dataset.facto)));
    if (ui.size !== prova.size || [...prova].some(n => !ui.has(n))) fail(`explicação do ticket ${t.facto}: UI mostra ${ui.size} passos, motor tem ${prova.size}`);
    for (const n of prova) {
      const d = document.querySelector(`#insp .v71-step[data-facto="${n}"] > summary`);
      if (d && !d.textContent.includes('R' + byNum.get(n).regra)) { fail(`explicação: passo ${n} não mostra R${byNum.get(n).regra}`); break; }
    }
    // a mesma prova no modo Leitura: as mesmas deduções, por ordem, e as folhas separadas por origem
    const folhas = { E: 0, B: 0 }, vistos = new Set();
    const walk2 = n => { if (vistos.has(n)) return; vistos.add(n); const f = byNum.get(n); if (!f) return; if (f.origem !== 'regra') { folhas[f.origem === 'inicial' ? 'B' : 'E']++; return; } (f.premissas || []).forEach(p => typeof p === 'number' && walk2(p)); };
    walk2(t.facto);
    S.v71ComoModo = 'leitura'; inspect();
    const passos = [...document.querySelectorAll('#insp .v78-step[data-facto]')].map(d => Number(d.dataset.facto));
    if (passos.length !== prova.size || passos.some(n => !prova.has(n))) fail(`leitura do ticket ${t.facto}: ${passos.length} deduções no UI, motor tem ${prova.size}`);
    passos.forEach((n, i) => (byNum.get(n).premissas || []).forEach(p => { if (typeof p === 'number' && prova.has(p) && passos.indexOf(p) > i) fail(`leitura do ticket ${t.facto}: #${n} aparece antes da sua premissa #${p}`); }));
    if (passos[passos.length - 1] !== t.facto) fail(`leitura do ticket ${t.facto}: a última dedução não é o ticket`);
    const nE = document.querySelectorAll('#insp .v78-line.kE, #insp .v78-line.kA').length, nB = document.querySelectorAll('#insp .v78-line.kB').length;
    if (nE !== folhas.E || nB !== folhas.B) fail(`leitura do ticket ${t.facto}: entradas ${nE}/${folhas.E}, base ${nB}/${folhas.B}`);
    // o grafo da explicação (janela inteira) tem as mesmas deduções e a mesma evidência, ligadas como o motor diz
    if (typeof v79GAbrir === 'function') {
      v79GAbrir(t.facto);
      const nos = [...document.querySelectorAll('#v79G .v79n')];
      const dedG = nos.filter(d => !/\bk[EAB]\b/.test(d.className)).map(d => Number(d.dataset.n));
      if (dedG.length !== prova.size || dedG.some(n => !prova.has(n))) fail(`grafo do ticket ${t.facto}: ${dedG.length} deduções, motor tem ${prova.size}`);
      if (nos.length - dedG.length !== folhas.E) fail(`grafo do ticket ${t.facto}: ${nos.length - dedG.length} entradas, motor tem ${folhas.E}`);
      let arestas = 0;
      prova.forEach(n => (byNum.get(n).premissas || []).forEach(p => { if (typeof p === 'number' && byNum.get(p) && byNum.get(p).origem !== 'inicial') arestas++; }));
      const setas = document.querySelectorAll('#v79G .v79g-ed path').length;
      if (setas !== arestas) fail(`grafo do ticket ${t.facto}: ${setas} setas, o motor tem ${arestas} ligações premissa→conclusão`);
      document.getElementById('v79G')?.remove();
    }
    S.v71ComoModo = 'arvore';
    expl++;
  }
  st.explicacoes = expl;
  // contexto de cada pergunta ao analista: vem da prova da pergunta e mostra o ativo e a evidência
  (res.perguntas || []).forEach(q => {
    const h = typeof v80Ctx === 'function' ? v80Ctx(res, q) : typeof v79Contexto === 'function' ? v79Contexto(res, q) : 'x';
    if (!h || !h.includes(q.ativo)) fail(`contexto da pergunta ${q.facto}: vazio ou sem o ativo ${q.ativo}`);
  });
  return { ok: falhas.length === 0, falhas, st };
};

// H. vistas filtradas por cenário: fechadas (cada premissa incluída) e todos os tickets aparecem em alguma vista
window.__viewCheck = function(res) {
  const falhas = [];
  const seq = new Set(Object.values(window._LAST_SEQUENCIAS || {}).flat());
  const cover = new Set();
  let vistas = 0;
  [...seq].forEach(k => {
    const v = v66FilterResult(res, k);
    if (!v) return;
    vistas++;
    const nums = new Set(v.factos.map(f => f.numero));
    v.factos.forEach(f => (f.premissas || []).forEach(p => { if (typeof p === 'number' && !nums.has(p)) falhas.push(`${k}: facto ${f.numero} sem a premissa ${p}`); }));
    v.tickets.forEach(t => cover.add(t.facto));
  });
  const semVista = res.tickets.filter(t => !cover.has(t.facto)).map(t => t.facto);
  if (semVista.length) falhas.push(`tickets que não aparecem em nenhum cenário: ${semVista.join(',')}`);
  return { ok: falhas.length === 0, falhas: falhas.slice(0, 20), vistas };
};
