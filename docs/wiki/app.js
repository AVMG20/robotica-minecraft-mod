/* Robotica wiki: a hash-routed single page app over wiki/data.json (built by scripts/wiki/build_wiki.py). No build step. */
'use strict';

let D = null;                       // data.json
const $ = (s, el = document) => el.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const fmt = n => typeof n === 'number' ? n.toLocaleString('en-US') : n;
const AGE_COLORS = ['#c2652a', '#8a8f94', '#33b8cc', '#d0542c', '#8e5bd6'];

/* Short taglines of the landing page hotbar (hand-written marketing lines). */
const HOME_TAGS = {
  copper_gear: 'Where it all starts', mainspring: 'Wind it by hand. Power for your first robot.',
  clockwork_mechanism: 'The heart of every early bot', tinkers_hammer: 'Slow 3x3 mining on day one',
  felling_axe: 'Whole trees in one chop', gearblade: 'Fast copper sword', copper_cell: 'Your first battery',
  bore_drill: 'Iron-pick speed, 3x3 mode, runs on FE', chainsaw: 'Trees, leaves and all', mining_drone: 'Digs tunnels for you',
  sentry_drone: 'Shoots monsters, not friends', courier_drone: 'Chest to chest, with filters', basic_circuit: 'Wired age',
  servo_drill: '5x5 and vein mining', rivet_gun: 'Four shots a second, no ammo', redstone_cell: 'A bigger battery',
  essence_vial: 'Bottle a monster, print its loot', advanced_circuit: 'Servo age', magma_drill: '3x3x3, auto-smelt',
  arc_blade: 'Lightning jumps to three monsters', magma_core: 'Deep age', null_drill: 'Up to 12x12x12',
  null_lance: 'Charge. Fire. Everything in a line.', ender_cell: 'The biggest cell', antigrav_core: 'Antigrav age',
  flight_module: 'Yes, you can fly', exo_helmet_mk1: 'Night vision, rebreather', exo_chestplate_mk1: 'Double jump, shield, flight',
  exo_leggings_mk1: 'Run faster', exo_boots_mk1: 'No fall damage, item magnet', upgrade_speed: 'Stack them. Go faster.',
  upgrade_range: 'Bigger work area', upgrade_fortune: 'More ore per ore', upgrade_void: 'Bye, cobblestone', upgrade_growth: 'Crops grow faster',
  codex: 'Your guide. Every player gets one.', recall_remote: 'Hold to go home', tesla_linker: 'Click a coil, then a machine',
  signal_flare: 'Wakes the Scrap Colossus', storage_expansion_mk1: 'More room in your terminal', mining_drone_mk2: 'Diamond-level digging',
  sentry_drone_mk2: 'Hits harder, flies faster', courier_drone_mk2: 'Three stacks per trip', magnet_module: 'Items fly to you',
  night_vision_module: 'See in the dark', jet_assist_module: 'Double jump, soft landings', grinder_mk1: 'Two dusts per ore',
  reactor_controller: 'Split the atom', item_pipe: 'Move items, no lag', thorium_ingot: 'Glows a little'
};
const HOME_AGES = [
  ['copper_gear', 'mainspring', 'clockwork_mechanism', 'tinkers_hammer', 'felling_axe', 'gearblade'],
  ['copper_cell', 'tesla_linker', 'bore_drill', 'chainsaw', 'mining_drone', 'grinder_mk1', 'item_pipe', 'signal_flare'],
  ['advanced_circuit', 'servo_drill', 'rivet_gun', 'redstone_cell', 'essence_vial', 'reactor_controller', 'upgrade_fortune'],
  ['magma_core', 'magma_drill', 'arc_blade', 'upgrade_void'],
  ['antigrav_core', 'null_drill', 'null_lance', 'ender_cell', 'flight_module']
];

/* ------------------------------------------------------------ items, names, icons */

function isTag(id) { return id.startsWith('#'); }
function item(id) { return D.items[id]; }
function nameOf(id) {
  if (D.items[id]) return D.items[id].n;
  if (isTag(id)) {
    const p = id.slice(1).split(':').pop().split('/');
    return 'any ' + (p.length > 1 ? p.slice(1).join(' ') + ' ' + p[0].replace(/s$/, '') : p[0]).replace(/_/g, ' ');
  }
  return D.vanilla[id] || id.split(':').pop().replace(/_/g, ' ').replace(/^./, c => c.toUpperCase());
}
const VANILLA_COLORS = {
  water: '#3f76e4', ice: '#91b7fd', packed_ice: '#7ea5f0', blue_ice: '#6aa5f5', air: '#cfe6f8', iron_ingot: '#c8c8c8',
  copper_ingot: '#c2652a', gold_ingot: '#e8c13a', diamond: '#4ee0d5', flint: '#4a4a4a', nether_star: '#f4f1c8', dragon_egg: '#1b0f24',
  crafting_table: '#9b6b3c', furnace: '#7a7a7a', blast_furnace: '#5e5e64', smithing_table: '#3c3c48', stonecutter: '#8f8f8f',
  stone: '#7d7d7d', deepslate: '#4d4d52', netherrack: '#7a2e2e', end_stone: '#dedca0', obsidian: '#1d1430', glass: '#c9e7ef'
};
function hue(s) { let h = 0; for (const c of s) h = (h * 31 + c.charCodeAt(0)) % 360; return `hsl(${h} 35% 42%)`; }
function glyph(id, cls = '') {
  const tag = isTag(id), key = id.replace(/^#/, '').split(':').pop();
  const color = VANILLA_COLORS[key] || hue(key);
  const words = nameOf(id).replace(/^any /, '').split(/\s+/);
  const ab = (words.length > 1 ? words[0][0] + words[1][0] : words[0].slice(0, 2));
  return `<span class="glyph ${tag ? 'tag' : ''} ${cls}" style="background:${color}" role="img" aria-label="${esc(nameOf(id))}">${esc(ab)}</span>`;
}
function icon(id, cls = '') {
  if (D.items[id]) return `<img class="px ${cls}" src="wiki/icons/${id}.png" alt="${esc(nameOf(id))}" loading="lazy">`;
  return glyph(id, cls);
}
function href(id) { return isTag(id) ? '' : `#/item/${id}`; }
/* Inline link with a small icon: [[id]] or [[id|label]]. */
function ilink(id, label) {
  const h = href(id), txt = esc(label || nameOf(id));
  return h ? `<a class="ilink" href="${h}" data-item="${esc(id)}">${icon(id)}<span>${txt}</span></a>`
           : `<span class="ilink" data-item="${esc(id)}">${icon(id)}<span>${txt}</span></span>`;
}
function slot(id, count) {
  const h = href(id), c = count > 1 ? `<span class="cnt">${count}</span>` : '';
  return h ? `<a class="slot" href="${h}" data-item="${esc(id)}">${icon(id)}${c}</a>`
           : `<span class="slot" data-item="${esc(id)}" tabindex="0">${icon(id)}${c}</span>`;
}
function hotbar(ids) { return `<div class="hotbar">${ids.map(i => slot(i)).join('')}</div>`; }
function ageChip(a) { return a == null ? '' : `<a class="chip age" href="#/age/${a}" style="background:${AGE_COLORS[a]}">Age ${a} · ${esc(D.ages[a].name)}</a>`; }
function moduleName(m) { return (D.modules.find(x => x.id === m) || {name: m}).name; }

/* ------------------------------------------------------------ markdown subset */

function inline(s) {
  let out = esc(s);
  out = out.replace(/\[\[([^\]|]+)(?:\|([^\]]*))?\]\]/g, (_, id, label) => ilink(id.trim(), label));
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>');
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, t, u) => `<a href="${u}"${/^https?:/.test(u) ? ' rel="noopener"' : ''}>${t}</a>`);
  return out;
}

function directive(name, arg) {
  switch (name) {
    case 'items': return hotbar(arg.split(/\s+/).filter(Boolean));
    case 'multiblock': return D.multiblocks[arg] ? viewerHtml(arg) : '';
    case 'upgrades': return upgradesFor(arg, true);
    case 'cards': return cardMatrix();
    case 'mkrule': return mkRuleTable();
    case 'guide': { const g = D.guides.find(x => x.slug === arg); return g ? `<div class="grid-cards">${guideCard(g)}</div>` : ''; }
    case 'image': { const [src, alt] = arg.split('|'); return `<img class="frame" style="max-width:640px;margin:12px 0" src="${esc(src.trim())}" alt="${esc((alt || '').trim())}" loading="lazy">`; }
    case 'orechart': return oreChart();
    case 'ores': return Object.keys(D.world.ores).sort((a, b) => ['Overworld', 'Nether', 'The End'].indexOf(D.world.ores[a].veins[0].dimension[0]) - ['Overworld', 'Nether', 'The End'].indexOf(D.world.ores[b].veins[0].dimension[0])).map(k => `<h3>${ilink(k)}</h3>` + orePanel(k)).join('');
    case 'structures': return structuresHtml();
    default: return '';
  }
}

function md(src) {
  const lines = src.replace(/\r/g, '').split('\n');
  let html = '', i = 0;
  const para = [];
  const flush = () => { if (para.length) { html += `<p>${inline(para.join(' '))}</p>`; para.length = 0; } };
  while (i < lines.length) {
    const line = lines[i];
    let m;
    if (!line.trim()) { flush(); i++; continue; }
    if ((m = line.match(/^\{\{(\w+)\s*([^}]*)\}\}\s*$/))) { flush(); html += directive(m[1], m[2].trim()); i++; continue; }
    if ((m = line.match(/^(#{1,3})\s+(.*)$/))) {
      flush();
      const lvl = Math.min(3, m[1].length + 1), id = m[2].toLowerCase().replace(/[^a-z0-9]+/g, '-');
      html += `<h${lvl} id="h-${id}">${inline(m[2])}</h${lvl}>`; i++; continue;
    }
    if (line.startsWith('>')) {
      flush();
      const buf = [];
      while (i < lines.length && lines[i].startsWith('>')) buf.push(lines[i++].replace(/^>\s?/, ''));
      html += `<div class="tip-box">${inline(buf.join(' '))}</div>`; continue;
    }
    if (line.startsWith('|')) {
      flush();
      const rows = [];
      while (i < lines.length && lines[i].startsWith('|')) rows.push(lines[i++]);
      const cells = r => r.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim());
      const head = cells(rows[0]), body = rows.slice(rows[1] && /^\|[\s:|-]+\|?$/.test(rows[1]) ? 2 : 1);
      html += `<div class="tbl"><table><thead><tr>${head.map(h => `<th>${inline(h)}</th>`).join('')}</tr></thead><tbody>` +
        body.map(r => `<tr>${cells(r).map(c => `<td>${inline(c)}</td>`).join('')}</tr>`).join('') + '</tbody></table></div>';
      continue;
    }
    if ((m = line.match(/^(\s*)([-*]|\d+\.)\s+/))) {
      flush();
      const ordered = /\d/.test(m[2]), buf = [];
      while (i < lines.length && /^\s*([-*]|\d+\.)\s+/.test(lines[i])) buf.push(lines[i++].replace(/^\s*([-*]|\d+\.)\s+/, ''));
      html += `<${ordered ? 'ol' : 'ul'}>${buf.map(b => `<li>${inline(b)}</li>`).join('')}</${ordered ? 'ol' : 'ul'}>`;
      continue;
    }
    para.push(line.trim()); i++;
  }
  flush();
  return html;
}
function plain(src) {
  return src.replace(/\{\{[^}]*\}\}/g, ' ').replace(/\[\[([^\]|]+)(?:\|([^\]]*))?\]\]/g, (_, id, l) => l || (D.items[id] ? D.items[id].n : nameOf(id)))
    .replace(/[#*`>|]/g, ' ').replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/\s+/g, ' ').trim();
}

/* ------------------------------------------------------------ upgrade cards */

const KIND_ORDER = ['speed', 'efficiency', 'range', 'fortune', 'silk', 'growth', 'void', 'height', 'carry'];
function rowOf(id) { return D.upgrades.machines.find(m => m.items.includes(id) || (m.tierItems || []).includes(id)); }
function capAt(v, t) { return Array.isArray(v) ? v[t] : v; }
function capText(v) {
  if (!Array.isArray(v)) return String(v);
  return v.every(x => x === v[0]) ? String(v[0]) : v.map(x => x || '–').join(' / ');
}
function kindsOf(m) { return KIND_ORDER.filter(k => k in m.caps); }
function cardItem(k) { return D.upgrades.cards[k].item; }

/* "Upgrades that fit" for a machine item; the column of this item's tier is marked. */
function upgradesFor(id, withTitle) {
  const m = rowOf(id);
  if (!m) return '';
  const tiers = m.tiers && m.tiers.length > 1 ? m.tiers : null;
  const here = m.items.indexOf(id);
  const slots = Array.isArray(m.slots) ? m.slots : null;
  let h = withTitle ? `<p><strong>${esc(m.name)}</strong>: cards that fit${slots ? '' : `, ${m.slots} card slot${m.slots > 1 ? 's' : ''}`}.</p>` : '';
  h += '<div class="tbl"><table><thead><tr><th>Card</th>';
  h += tiers ? tiers.map((t, i) => `<th class="num"${i === here && m.items.length > 1 ? ' style="background:var(--mark)"' : ''}>${esc(t)}</th>`).join('') : '<th class="num">Max</th>';
  h += '<th>What it does here</th></tr></thead><tbody>';
  if (slots) h += `<tr><td>Card slots</td>${slots.map(s => `<td class="num">${s}</td>`).join('')}<td></td></tr>`;
  for (const k of kindsOf(m)) {
    const v = m.caps[k];
    h += `<tr><td>${ilink(cardItem(k))}</td>`;
    h += tiers ? tiers.map((_, i) => `<td class="num">${capAt(v, i) || '–'}</td>`).join('') : `<td class="num">${capText(v)}</td>`;
    h += `<td>${inline(m.effects[k] || '')}</td></tr>`;
  }
  h += '</tbody></table></div>';
  if (m.tierItems) h += `<p>Mk2-Mk4 via ${m.tierItems.filter(Boolean).map(t => ilink(t)).join(', ')} on the placed block.</p>`;
  return h;
}
/* The Mk rule: card slots and caps per Mk, from the Java code. */
function mkRuleTable() {
  const r = D.upgrades.mkRule;
  if (!r) return '';
  let h = '<div class="tbl"><table><thead><tr><th>Card</th>' + r.tiers.map(t => `<th class="num">${esc(t)}</th>`).join('') + '</tr></thead><tbody>';
  h += `<tr><td>Card slots</td>${r.slots.map(s => `<td class="num">${s}</td>`).join('')}</tr>`;
  for (const k of KIND_ORDER.filter(k => k in r.caps)) h += `<tr><td>${ilink(cardItem(k))}</td>${r.caps[k].map(c => `<td class="num">${c}</td>`).join('')}</tr>`;
  return h + '</tbody></table></div>';
}
/* Every machine that takes a card kind. */
function machinesForCard(kind) {
  const rows = D.upgrades.machines.filter(m => kind in m.caps);
  if (!rows.length) return '';
  return '<div class="tbl"><table><thead><tr><th>Machine</th><th class="num">Max cards</th><th>What it does there</th></tr></thead><tbody>' +
    rows.map(m => `<tr><td>${ilink(m.items[0], m.name)}</td><td class="num">${capText(m.caps[kind])}${m.tiers && Array.isArray(m.caps[kind]) ? `<br><small>${esc(m.tiers.join(' / '))}</small>` : ''}</td><td>${inline(m.effects[kind] || '')}</td></tr>`).join('') +
    '</tbody></table></div>';
}
function cardMatrix() {
  const kinds = KIND_ORDER.filter(k => D.upgrades.machines.some(m => k in m.caps));
  let h = '<div class="tbl"><table><thead><tr><th>Machine</th>' +
    kinds.map(k => `<th class="num"><a href="#/item/${cardItem(k)}" title="${esc(D.upgrades.cards[k].name)}" data-item="${cardItem(k)}">${icon(cardItem(k))}</a><span class="sr">${esc(k)}</span></th>`).join('') +
    '</tr></thead><tbody>';
  for (const m of D.upgrades.machines) {
    h += `<tr><td>${ilink(m.items[0], m.name)}</td>` + kinds.map(k => k in m.caps
      ? `<td class="num" title="${esc(m.effects[k] || '')}">${capText(m.caps[k])}</td>` : '<td class="num" style="color:var(--muted)">·</td>').join('') + '</tr>';
  }
  return h + '</tbody></table></div><p style="color:var(--muted);font-size:14px">Numbers are the most cards that count; "2 / 4 / 6 / 8" is per Mk. Hover a number for what the card does there.</p>';
}

/* ------------------------------------------------------------ multiblock viewer */

const S = 2, OX = 4, OY = 48, SPRITE = 64;          // must match ISO_* in build_wiki.py
const proj = (x, y, z) => [(x + z) * 0.866 * S, (x - z) * 0.5 * S - y * S];
const imgCache = {};
function sprite(id) {
  if (!imgCache[id]) { imgCache[id] = new Image(); imgCache[id].src = `wiki/icons/${id}.png`; }
  return imgCache[id];
}
function hull(pts) {
  pts = pts.slice().sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  const cross = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], up = [];
  for (const p of pts) { while (lo.length >= 2 && cross(lo[lo.length - 2], lo[lo.length - 1], p) <= 0) lo.pop(); lo.push(p); }
  for (const p of pts.slice().reverse()) { while (up.length >= 2 && cross(up[up.length - 2], up[up.length - 1], p) <= 0) up.pop(); up.push(p); }
  return lo.slice(0, -1).concat(up.slice(0, -1));
}
function shade(hex, f) {
  const n = parseInt(hex.slice(1), 16), c = [n >> 16, (n >> 8) & 255, n & 255].map(v => Math.round(v * f));
  return `rgb(${c.join(',')})`;
}
function blockAt(mb, x, y, z) { return mb.legend[mb.layers[y][z][x]] || 'air'; }
const isAir = b => !b || b === 'air' || b === 'minecraft:air';

function drawStructure(canvas, mb, upto) {
  const [W, H, Dd] = mb.size, cells = [];
  for (let y = 0; y <= upto; y++) for (let z = 0; z < Dd; z++) for (let x = 0; x < W; x++) {
    const b = blockAt(mb, x, y, z);
    if (!isAir(b)) cells.push([x, y, z, b]);
  }
  let minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
  for (let y = 0; y < H; y++) for (const [x, z] of [[0, 0], [W, 0], [0, Dd], [W, Dd]]) {
    for (const yy of [y * 16, y * 16 + 16]) {
      const [sx, sy] = proj(x * 16, yy, z * 16);
      minX = Math.min(minX, sx); maxX = Math.max(maxX, sx); minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
    }
  }
  const pad = 8;
  canvas.width = Math.ceil(maxX - minX + 2 * pad); canvas.height = Math.ceil(maxY - minY + 2 * pad);
  const g = canvas.getContext('2d');
  g.imageSmoothingEnabled = false;
  g.clearRect(0, 0, canvas.width, canvas.height);
  const off = (x, y, z) => { const [sx, sy] = proj(x, y, z); return [sx - minX + pad, sy - minY + pad]; };
  cells.sort((a, b) => (0.6 * a[0] + 0.75 * a[1] - 0.6 * a[2]) - (0.6 * b[0] + 0.75 * b[1] - 0.6 * b[2]));
  const corners = (x, y, z) => { const r = []; for (const dx of [0, 16]) for (const dy of [0, 16]) for (const dz of [0, 16]) r.push(off(x + dx, y + dy, z + dz)); return r; };
  const poly = (pts, fill) => { g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1])); g.closePath(); g.fillStyle = fill; g.fill(); };
  for (const [x, y, z, b] of cells) {           // dark silhouettes first: rounding gaps read as outlines
    const X = x * 16, Y = y * 16, Z = z * 16;
    poly(hull(corners(X, Y, Z)), 'rgba(20,24,26,.9)');
  }
  for (const [x, y, z, b] of cells) {
    const X = x * 16, Y = y * 16, Z = z * 16;
    if (D.items[b]) {
      const [px, py] = off(X, Y, Z);
      const im = sprite(b);
      if (im.complete && im.naturalWidth) g.drawImage(im, Math.round(px - OX), Math.round(py - OY), SPRITE, SPRITE);
      else im.addEventListener('load', () => drawStructure(canvas, mb, upto), {once: true});
    } else {
      const key = b.split(':').pop(), col = VANILLA_COLORS[key] || '#888888', water = key === 'water';
      g.globalAlpha = water ? 0.75 : 1;
      const p = (dx, dy, dz) => off(X + dx, Y + dy, Z + dz);
      poly([p(0, 16, 0), p(16, 16, 0), p(16, 16, 16), p(0, 16, 16)], shade(col, 1));       // top
      poly([p(0, 0, 0), p(16, 0, 0), p(16, 16, 0), p(0, 16, 0)], shade(col, 0.8));        // north
      poly([p(16, 0, 0), p(16, 0, 16), p(16, 16, 16), p(16, 16, 0)], shade(col, 0.62));   // east
      g.globalAlpha = 1;
    }
  }
}

function layerGrid(mb, y) {
  const [W] = mb.size, rows = mb.layers[y];
  return `<div class="layer" style="grid-template-columns:repeat(${W},auto)" role="grid" aria-label="Layer ${y + 1}">` +
    rows.map(r => [...r].map(ch => {
      const b = mb.legend[ch] || 'air';
      return isAir(b) ? '<span class="slot air" title="Air"></span>' : slot(b);
    }).join('')).join('') + '</div>';
}

function viewerHtml(id, big) {
  const mb = D.multiblocks[id];
  const [W, H, Dd] = mb.size;
  const parts = Object.entries(mb.parts).map(([b, n]) => `<span>${ilink(b)} × ${n}</span>`).join('');
  return `<section class="mb" data-mb="${id}" aria-label="${esc(mb.title)}">
    <div class="mb-top"><h3>${big ? esc(mb.title) : `<a href="#/multiblock/${id}">${esc(mb.title)}</a>`}</h3><span class="chip">${W} × ${H} × ${Dd}</span></div>
    <p>${inline(mb.about || '')}</p>
    <div class="mb-body">
      <div><canvas width="10" height="10" role="img" aria-label="3D view of the ${esc(mb.title)}"></canvas>
        <p class="compass">Seen from the north-east, above. The controller faces you.</p></div>
      <div>
        <div class="mb-ctl"><button type="button" data-d="-1" aria-label="Layer down">▼</button>
          <input type="range" min="1" max="${H}" value="${H}" aria-label="Layer">
          <button type="button" data-d="1" aria-label="Layer up">▲</button></div>
        <p class="mb-lbl" aria-live="polite"></p>
        <div class="mb-grid"></div>
        <p class="compass">North is up. The 3D view shows the layers up to this one.</p>
      </div>
    </div>
    <h4 style="margin:12px 0 0;font-family:var(--f-px)">Parts</h4><div class="mb-parts">${parts}</div>
    ${mb.notes && mb.notes.length ? `<ul class="mb-notes">${mb.notes.map(n => `<li>${inline(n)}</li>`).join('')}</ul>` : ''}
  </section>`;
}
function mountViewers(root) {
  root.querySelectorAll('.mb[data-mb]').forEach(el => {
    const mb = D.multiblocks[el.dataset.mb], H = mb.size[1];
    const range = $('input[type=range]', el), canvas = $('canvas', el);
    const set = v => {
      v = Math.max(1, Math.min(H, v)); range.value = v;
      $('.mb-lbl', el).textContent = `Layer ${v} of ${H} (bottom to top)`;
      $('.mb-grid', el).innerHTML = layerGrid(mb, v - 1);
      drawStructure(canvas, mb, v - 1);
    };
    range.addEventListener('input', () => set(+range.value));
    el.querySelectorAll('button[data-d]').forEach(b => b.addEventListener('click', () => set(+range.value + +b.dataset.d)));
    set(H);
  });
}

/* ------------------------------------------------------------ world: ores and structures */

function rarity(o) {
  const n = o.veins.reduce((s, v) => s + v.perChunk * v.size, 0);
  return n >= 60 ? 'common' : n >= 30 ? 'uncommon' : 'rare';
}
function heightText(h) { return h.best != null ? `Y ${h.min} to ${h.max}, most at Y ${h.best}` : `Y ${h.min} to ${h.max}, even spread`; }
function orePanel(key) {
  const o = D.world.ores[key];
  if (!o) return '';
  const cell = (k, v) => `<div><b>${k}</b>${v}</div>`;
  let h = '';
  o.veins.forEach((v, i) => {
    h += `<div class="oregrid">` +
      cell('Dimension', esc(v.dimension.join(', '))) + cell('Biomes', esc(v.biomes.join(', '))) +
      cell('Height', esc(heightText(v.height))) + cell('Veins per chunk', v.perChunk) + cell('Vein size', `up to ${v.size}`) +
      cell('In', esc(v.hosts.join(', '))) + (i === 0 ? cell('Rarity', rarity(o)) : '') + '</div>';
    if (o.veins.length > 1 && i === 0) h += '<p>Extra veins:</p>';
  });
  const d = o.drop || {};
  h += `<div class="oregrid">` + cell('Pickaxe', `${esc(o.tool)} or better`) +
    cell('Drops', d.item ? ilink(d.item) + (d.fortune ? ' (Fortune adds more)' : '') : 'itself') +
    cell('Silk Touch', d.silk ? 'drops the ore block' : '–') + (o.xp ? cell('Experience', `${o.xp[0]}-${o.xp[1]}`) : '') + '</div>';
  if (o.blocks.length > 1) h += `<p>Variants: ${o.blocks.map(b => ilink(b)).join(' ')}</p>`;
  return h;
}
function oreChart() {
  const DIM = ['Overworld', 'Nether', 'The End'];
  const ores = Object.entries(D.world.ores).sort((a, b) => DIM.indexOf(a[1].veins[0].dimension[0]) - DIM.indexOf(b[1].veins[0].dimension[0])), lo = -64, hi = 128, W = 640, Hh = 380, top = 20, bot = 50, left = 56;
  const y = v => top + (hi - v) / (hi - lo) * (Hh - top - bot);
  const colW = (W - left - 20) / ores.length;
  let s = `<svg class="orechart" viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Height chart of the Robotica ores">`;
  for (let v = lo; v <= hi; v += 32) s += `<line class="ax" x1="${left}" x2="${W - 10}" y1="${y(v)}" y2="${y(v)}"/><text x="${left - 8}" y="${y(v) + 4}" text-anchor="end">Y ${v}</text>`;
  ores.forEach(([key, o], i) => {
    const cx = left + colW * (i + 0.5), bw = Math.min(70, colW * 0.5), v = o.veins[0], h = v.height;
    const col = {thorium_ore: '#7bd64f', pyrolite_ore: '#ff7a2f', resonite_ore: '#b07cff'}[key] || '#33b8cc';
    if (h.best != null) {
      s += `<polygon points="${cx},${y(h.max)} ${cx + bw / 2},${y(h.best)} ${cx},${y(h.min)} ${cx - bw / 2},${y(h.best)}" fill="${col}" fill-opacity=".75" stroke="${col}"/>`;
      s += `<line x1="${cx - bw / 2 - 6}" x2="${cx + bw / 2 + 6}" y1="${y(h.best)}" y2="${y(h.best)}" stroke="var(--fg)" stroke-width="2"/>`;
      s += `<text x="${cx + bw / 2 + 8}" y="${y(h.best) + 4}">best Y ${h.best}</text>`;
    } else {
      s += `<rect x="${cx - bw / 2}" y="${y(h.max)}" width="${bw}" height="${y(h.min) - y(h.max)}" fill="${col}" fill-opacity=".6" stroke="${col}"/>`;
      s += `<text x="${cx + bw / 2 + 6}" y="${(y(h.min) + y(h.max)) / 2 + 4}">even spread</text>`;
    }
    s += `<image href="wiki/icons/${key}.png" x="${cx - 14}" y="${Hh - bot + 4}" width="28" height="28" style="image-rendering:pixelated"/>`;
    s += `<text class="lbl" x="${cx}" y="${Hh - 6}" text-anchor="middle">${esc(D.items[key].n.replace(' Ore', ''))} · ${esc(v.dimension[0])}</text>`;
  });
  return s + '</svg>';
}
function structuresHtml() {
  return D.world.structures.map(st => {
    const name = st.id.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    return `<div class="codexpg"><b>${esc(name)}</b><ul>
      <li>Biomes: ${esc(st.biomes.join(', '))}</li>
      <li>About one per ${st.spacing} × ${st.spacing} chunks, at least ${st.separation} chunks apart</li>
      ${st.awayFrom ? `<li>Never within ${st.awayFrom.chunks} chunks of ${esc(st.awayFrom.set.toLowerCase())}</li>` : ''}
      ${st.size ? `<li>${st.size} × ${st.size} blocks, only on flat${st.dryLand ? ', dry' : ''} ground${st.maxSlope ? ` (at most ${st.maxSlope} blocks height difference)` : ''}</li>` : ''}
    </ul></div>`;
  }).join('');
}

/* ------------------------------------------------------------ pages */

function factOf(id) { const it = D.items[id]; return it && it.f != null ? D.facts[it.f] : null; }
function isMachine(id) {
  const it = D.items[id], f = factOf(id);
  return it && it.k === 'block' && (it.up || (it.mb && D.multiblocks[it.mb[0]].controller === id) || (f && f.use && f.use.length));
}
function stepOf(name) { return D.steps.find(s => s.id === name); }
function codexPagesWith(id) {
  const out = [];
  D.codex.forEach((c, ci) => c.pages.forEach(p => { if (p.items.includes(id)) out.push([c, ci, p]); }));
  return out;
}
function guideCard(g) {
  return `<a class="card" href="#/guide/${g.slug}">${icon(g.icon)}<div><b>${esc(g.title)}</b><span>${esc(g.summary)}</span></div></a>`;
}
function crumbs(parts) { return `<nav class="crumbs" aria-label="Breadcrumb">${parts.map(([t, h]) => h ? `<a href="${h}">${esc(t)}</a>` : esc(t)).join(' › ')}</nav>`; }
function statsTable(st) {
  if (!st || !st.rows) return '';
  const cols = st.columns;
  return '<div class="tbl"><table>' + (cols ? `<thead><tr><th></th>${cols.map(c => `<th class="num">${esc(c)}</th>`).join('')}</tr></thead>` : '') +
    '<tbody>' + st.rows.map(r => `<tr><th>${inline(r[0])}</th>${r.slice(1).map(c => `<td${cols ? ' class="num"' : ''}>${inline(String(c))}</td>`).join('')}</tr>`).join('') +
    '</tbody></table></div>';
}
function seeLink(s) {
  const [kind, arg] = s.split(':');
  if (kind === 'guide') { const g = D.guides.find(x => x.slug === arg); return g ? guideCard(g) : ''; }
  if (kind === 'multiblock' && D.multiblocks[arg]) { const mb = D.multiblocks[arg]; return `<a class="card" href="#/multiblock/${arg}">${icon(mb.controller)}<div><b>${esc(mb.title)}</b><span>${esc(mb.about || '')}</span></div></a>`; }
  if (kind === 'item' && D.items[arg]) return `<a class="card" href="#/item/${arg}">${icon(arg)}<div><b>${esc(D.items[arg].n)}</b><span>${esc(moduleName(D.items[arg].m))}</span></div></a>`;
  return '';
}

function itemPage(id) {
  const it = D.items[id];
  if (!it) return vanillaPage(id);
  const f = factOf(id);
  let h = crumbs([['Items', '#/items'], [moduleName(it.m), `#/module/${it.m}`], [it.n]]);
  h += `<div class="ihead">${slot(id)}<div><h1>${esc(it.n)}</h1><div class="chips">${ageChip(it.a)}<a class="chip" href="#/module/${it.m}">${esc(moduleName(it.m))}</a>${isMachine(id) ? '<a class="chip" href="#/machines">Machine</a>' : ''}</div></div></div>`;
  const what = f && f.what ? f.what : it.t;
  if (what) h += `<p class="lead">${inline(what)}</p>`;
  if (it.d && (!f || !f.use)) h += `<p>${inline(it.d).replace(/\n/g, '<br>')}</p>`;
  if (f && f.items.length > 1) h += `<div class="hotbar" aria-label="All tiers">${f.items.map(i => i === id ? slot(i).replace('class="slot"', 'class="slot" style="background:#b8d98f" aria-current="true"') : slot(i)).join('')}</div>`;
  if (f && f.use && f.use.length) h += `<h2>How to use it</h2><ol class="use">${f.use.map(u => `<li>${inline(u)}</li>`).join('')}</ol>`;
  if (f && f.stats) h += `<h2>Numbers</h2>${statsTable(f.stats)}`;
  if (it.up) h += `<h2>Upgrades that fit</h2>${upgradesFor(id, false)}`;
  const kind = Object.keys(D.upgrades.cards).find(k => D.upgrades.cards[k].item === id);
  if (kind) {
    const c = D.upgrades.cards[kind];
    h += `<h2>Fits in</h2><p>${esc(c.summary)} ${esc(c.rules || '')} Up to ${c.maxStack} in one slot.</p>${machinesForCard(kind) || '<p>No machine takes it.</p>'}`;
  }
  if (it.ore) h += `<h2>Where to find it</h2>${orePanel(it.ore)}<p><a href="#/guide/finding-ores">All ores on one chart</a></p>`;
  if (it.mb) {
    const own = it.mb.filter(k => D.multiblocks[k].controller === id);
    if (own.length) h += `<h2>Build it</h2>` + viewerHtml(own[0]) + (own.length > 1 ? `<div class="grid-cards">${own.slice(1).map(k => seeLink('multiblock:' + k)).join('')}</div>` : '');
    else h += `<h2>Part of</h2><div class="grid-cards">${it.mb.map(k => seeLink('multiblock:' + k)).join('')}</div>`;
  }
  const pages = codexPagesWith(id);
  if (pages.length) h += `<h2>In the Codex</h2>` + pages.slice(0, 3).map(([c, ci, p]) => `<div class="codexpg"><b><a href="#/codex/${ci}">${esc(c.title)}</a> · ${esc(p.title)}</b><p>${esc(p.text)}</p></div>`).join('');
  const get = [];
  if (it.s) { const st = stepOf(it.s); if (st) get.push(`Unlocked at the guide step <a href="#/age/${st.age}">${esc(st.title)}</a> (Age ${st.age}).`); }
  if (it.made && it.made.length) get.push(`Made at ${it.made.map(m => ilink(m)).join(', ')}. Recipes: JEI or the Codex.`);
  if (get.length) h += `<h2>Get it</h2><p>${get.join(' ')}</p>`;
  const see = (f && f.see || []).map(seeLink).join('');
  if (see) h += `<h2>See also</h2><div class="grid-cards">${see}</div>`;
  return h;
}
function vanillaPage(id) {
  const users = Object.entries(D.multiblocks).filter(([, mb]) => id in mb.parts);
  let h = crumbs([['Items', '#/items'], [nameOf(id)]]);
  h += `<div class="ihead">${slot(id)}<div><h1>${esc(nameOf(id))}</h1><div class="chips"><span class="chip">${isTag(id) ? 'Item tag' : 'Minecraft'}</span></div></div></div>`;
  h += `<p class="lead">${isTag(id) ? 'Any item with this tag works, from any mod.' : 'A vanilla Minecraft item or block.'}</p>`;
  if (users.length) h += `<h2>Used in</h2><div class="grid-cards">${users.map(([k]) => seeLink('multiblock:' + k)).join('')}</div>`;
  return h;
}
function guidePage(slug) {
  const i = D.guides.findIndex(g => g.slug === slug);
  if (i < 0) return notFound();
  const g = D.guides[i], prev = D.guides[i - 1], next = D.guides[i + 1];
  return crumbs([['Guides', '#/guides'], [g.title]]) + `<article class="prose"><h1>${esc(g.title)}</h1><p class="lead">${esc(g.summary)}</p>${md(g.body)}</article>` +
    `<div class="grid-cards" style="margin-top:32px">${prev ? guideCard(prev) : ''}${next ? guideCard(next) : ''}</div>`;
}
function guidesPage() {
  return `<h1>Guides</h1><p class="lead">Short, visual, straight to the point.</p><div class="grid-cards">${D.guides.map(guideCard).join('')}</div>
    <h2>Codex chapters</h2><p>The in-game book, chapter by chapter.</p><div class="grid-cards">${D.codex.map((c, i) => `<a class="card" href="#/codex/${i}">${icon(c.icon)}<div><b>${esc(c.title)}</b><span>${c.pages.length} pages</span></div></a>`).join('')}</div>`;
}
function codexPage(i) {
  const c = D.codex[i];
  if (!c) return notFound();
  return crumbs([['Guides', '#/guides'], ['Codex'], [c.title]]) + `<h1>${esc(c.title)}</h1><p class="lead">From the in-game Codex.</p>` +
    c.pages.map(p => `<div class="codexpg"><b>${esc(p.title)}</b><p>${esc(p.text)}</p>${p.items.length ? hotbar(p.items) : ''}</div>`).join('');
}
function itemsPage(mod) {
  const mods = D.modules.filter(m => !mod || m.id === mod);
  let h = mod ? crumbs([['Items', '#/items'], [moduleName(mod)]]) + `<h1>${esc(moduleName(mod))}</h1>` : '<h1>All items</h1><p class="lead">Every Robotica item and block, by module. Hover for the name, click for the page.</p>';
  h += `<div class="chips">${D.modules.map(m => `<a class="chip" href="#/module/${m.id}"${m.id === mod ? ' aria-current="page"' : ''}>${esc(m.name)}</a>`).join('')}</div>`;
  for (const m of mods) {
    const ids = Object.keys(D.items).filter(i => D.items[i].m === m.id).sort((a, b) => (D.items[a].a ?? 9) - (D.items[b].a ?? 9) || a.localeCompare(b));
    if (!mod) h += `<h2><a href="#/module/${m.id}">${esc(m.name)}</a></h2>`;
    if (mod) {
      h += '<div class="grid-cards">' + ids.map(i => { const f = factOf(i); return `<a class="card" href="#/item/${i}">${icon(i)}<div><b>${esc(D.items[i].n)}</b><span>${esc(plain((f && f.what) || D.items[i].t || ''))}</span></div></a>`; }).join('') + '</div>';
    } else h += hotbar(ids);
  }
  return h;
}
function machinesPage() {
  let h = '<h1>Machines</h1><p class="lead">Every block that does something, what it is for, and which cards it takes.</p>';
  for (const m of D.modules) {
    const ids = Object.keys(D.items).filter(i => D.items[i].m === m.id && isMachine(i) && (!factOf(i) || factOf(i).items[0] === i));
    if (!ids.length) continue;
    h += `<h2>${esc(m.name)}</h2><div class="grid-cards">` + ids.map(i => {
      const f = factOf(i), n = f && f.items.length > 1 ? D.items[i].n.replace(/ Mk ?\d$| I$/, '') + ` (${f.items.length} tiers)` : D.items[i].n;
      return `<a class="card" href="#/item/${i}">${icon(i)}<div><b>${esc(n)}</b><span>${esc(plain((f && f.what) || D.items[i].t || ''))}</span></div></a>`;
    }).join('') + '</div>';
  }
  return h;
}
function upgradesPage() {
  const cards = KIND_ORDER.filter(k => D.upgrades.cards[k]).map(k => [k, D.upgrades.cards[k]]);
  return `<h1>Upgrade cards</h1><p class="lead">Cards upgrade machines. Right-click a machine with a card, or put it in an upgrade slot. One kind per slot; stackable kinds stack in their slot up to the machine's limit.</p>
    ${D.upgrades.rule ? `<p>${esc(D.upgrades.rule)}</p>` : ''}
    <div class="grid-cards">${cards.map(([k, c]) => `<a class="card" href="#/item/${c.item}">${icon(c.item)}<div><b>${esc(c.name)}</b><span>${esc(c.summary)} Max ${c.maxStack}.</span></div></a>`).join('')}</div>
    <h2>Which machine takes what</h2>${cardMatrix()}`;
}
function multiblocksPage() {
  return `<h1>Multiblocks</h1><p class="lead">Checked examples you can copy block by block.</p><div class="grid-cards">${Object.keys(D.multiblocks).map(k => seeLink('multiblock:' + k)).join('')}</div>`;
}
function multiblockPage(id) {
  const mb = D.multiblocks[id];
  if (!mb) return notFound();
  const g = D.guides.find(x => x.slug === mb.guide);
  return crumbs([['Multiblocks', '#/multiblocks'], [mb.title]]) + `<h1>${esc(mb.title)}</h1>` + viewerHtml(id, true) +
    (g ? `<h2>How it works</h2><div class="grid-cards">${guideCard(g)}${seeLink('item:' + mb.controller)}</div>` : '');
}
function progressionPage(age) {
  const ages = age == null ? D.ages : [D.ages[age]];
  let h = age == null ? '<h1>Progression</h1><p class="lead">The guide steps of the Codex, age by age. Each step unlocks the recipes shown.</p>'
    : crumbs([['Progression', '#/progression'], [`Age ${age}`]]) + `<h1>Age ${age}: ${esc(D.ages[age].name)}</h1>`;
  h += `<div class="chips">${D.ages.map(a => ageChip(a.n)).join('')}</div>`;
  for (const a of ages) {
    const steps = D.steps.filter(s => s.age === a.n);
    if (age == null) h += `<h2 id="age-${a.n}"><a href="#/age/${a.n}">Age ${a.n}: ${esc(a.name)}</a></h2>`;
    h += steps.map(s => `<div class="codexpg" id="step-${s.id}"><b>${ilink(s.icon, s.title)}${s.goal ? ' <span class="chip">goal</span>' : ''}</b><p>${esc(s.text)}</p>${s.unlocks.length ? hotbar(s.unlocks.filter(u => D.items[u])) : ''}</div>`).join('');
  }
  return h;
}
function notFound() { return '<h1>Not found</h1><p>That page does not exist. Try the search (<kbd>/</kbd>).</p>'; }

/* ------------------------------------------------------------ home, sidebar, router */

function renderHome(page) {
  page.innerHTML = '';
  page.appendChild($('#home').content.cloneNode(true));
  $('#rows', page).innerHTML = HOME_AGES.map((ids, a) =>
    `<div class="row"><a class="age" href="#/age/${a}">${esc(D.ages[a].name)}</a>${hotbar(ids.filter(i => D.items[i]))}</div>`).join('');
  page.querySelectorAll('[data-icons]').forEach(el => {
    const html = el.dataset.icons.split(',').map(i => i === 'essence_vial_full' ? 'essence_vial' : i).filter(i => D.items[i]).map(i => slot(i)).join('');
    el.innerHTML = html;
  });
  $('#homeguides', page).innerHTML = D.guides.map(guideCard).join('') +
    `<a class="card" href="#/machines">${icon('grinder_mk1')}<div><b>All machines</b><span>What every block does, with its numbers.</span></div></a>` +
    `<a class="card" href="#/upgrades">${icon('upgrade_speed')}<div><b>Upgrade cards</b><span>Which card fits which machine, and what it does there.</span></div></a>` +
    `<a class="card" href="#/progression">${icon('codex')}<div><b>Progression</b><span>Every guide step, age by age.</span></div></a>`;
}
function sidebar(cur) {
  const a = (h, t, ic) => `<a href="${h}"${h === cur ? ' aria-current="page"' : ''}>${ic ? icon(ic) : ''}<span>${esc(t)}</span></a>`;
  let h = '<h4>Start</h4>' + a('#/', 'Home', 'copper_gear') + a('#/guide/getting-started', 'Getting started', 'stumpy');
  h += '<h4>Guides</h4>' + D.guides.filter(g => g.slug !== 'getting-started').map(g => a(`#/guide/${g.slug}`, g.title, g.icon)).join('');
  h += '<h4>Browse</h4>' + a('#/machines', 'Machines', 'grinder_mk1') + a('#/upgrades', 'Upgrade cards', 'upgrade_speed') +
    a('#/multiblocks', 'Multiblocks', 'reactor_controller') + a('#/items', 'All items', 'iron_plate') + a('#/guides', 'Codex chapters', 'codex');
  h += '<h4>Items by module</h4>' + D.modules.map(m => {
    const first = Object.keys(D.items).find(i => D.items[i].m === m.id && D.items[i].k === 'block') || Object.keys(D.items).find(i => D.items[i].m === m.id);
    return a(`#/module/${m.id}`, m.name, first);
  }).join('');
  h += '<h4>Progression</h4>' + D.ages.map(x => a(`#/age/${x.n}`, `Age ${x.n}: ${x.name}`)).join('');
  return h;
}
function route() {
  const raw = location.hash || '#/';
  const page = $('#page');
  if (!raw.startsWith('#/')) {                 // landing anchors like #install
    if (!document.body.classList.contains('home') || !page.childElementCount) { document.body.classList.add('home'); renderHome(page); }
    const el = document.getElementById(raw.slice(1));
    if (el) el.scrollIntoView();
    return;
  }
  const [path] = raw.slice(2).split('?');
  const [sec, ...rest] = path.split('/');
  const arg = decodeURIComponent(rest.join('/'));
  let html = null, title = 'Robotica';
  document.body.classList.toggle('home', !sec);
  document.body.classList.remove('menu-open');
  switch (sec) {
    case '': renderHome(page); break;
    case 'item': html = itemPage(arg); title = nameOf(arg); break;
    case 'guide': html = guidePage(arg); title = (D.guides.find(g => g.slug === arg) || {}).title; break;
    case 'guides': html = guidesPage(); title = 'Guides'; break;
    case 'codex': html = codexPage(+arg); title = (D.codex[+arg] || {}).title; break;
    case 'items': html = itemsPage(); title = 'Items'; break;
    case 'module': html = itemsPage(arg); title = moduleName(arg); break;
    case 'machines': html = machinesPage(); title = 'Machines'; break;
    case 'upgrades': html = upgradesPage(); title = 'Upgrade cards'; break;
    case 'multiblocks': html = multiblocksPage(); title = 'Multiblocks'; break;
    case 'multiblock': html = multiblockPage(arg); title = (D.multiblocks[arg] || {}).title; break;
    case 'progression': html = progressionPage(); title = 'Progression'; break;
    case 'age': html = progressionPage(+arg); title = `Age ${arg}`; break;
    case 'search': openSearch(new URLSearchParams(raw.split('?')[1] || '').get('q') || ''); html = guidesPage(); break;
    default: html = notFound();
  }
  if (html != null) { page.innerHTML = html; mountViewers(page); }
  $('#side').innerHTML = sidebar('#/' + path);
  document.querySelectorAll('.topnav a').forEach(x => x.toggleAttribute('aria-current', raw.startsWith(x.getAttribute('href'))));
  document.title = title && sec ? `${title} · Robotica wiki` : 'Robotica';
  window.scrollTo(0, 0);
  if (sec) page.focus({preventScroll: true});
}

/* ------------------------------------------------------------ search */

let INDEX = null;
function buildIndex() {
  const idx = [];
  for (const [id, it] of Object.entries(D.items)) {
    const f = factOf(id);
    idx.push({g: isMachine(id) ? 'Machines' : 'Items', t: it.n, id, ic: id, h: `#/item/${id}`,
      sub: plain((f && f.what) || it.t || moduleName(it.m)), x: [id.replace(/_/g, ' '), it.t, it.d, f && f.what, moduleName(it.m)].join(' ')});
  }
  for (const g of D.guides) idx.push({g: 'Guides', t: g.title, ic: g.icon, h: `#/guide/${g.slug}`, sub: g.summary, x: plain(g.body)});
  for (const [k, mb] of Object.entries(D.multiblocks)) idx.push({g: 'Guides', t: mb.title, ic: mb.controller, h: `#/multiblock/${k}`, sub: 'Multiblock example', x: mb.about});
  D.codex.forEach((c, i) => c.pages.forEach(p => idx.push({g: 'Codex', t: `${c.title}: ${p.title}`, ic: c.icon, h: `#/codex/${i}`, sub: p.text.slice(0, 90), x: p.text})));
  idx.push({g: 'Guides', t: 'Upgrade cards: which machine takes what', ic: 'upgrade_speed', h: '#/upgrades', sub: 'Card table', x: 'cards upgrades speed efficiency fortune'});
  for (const x of idx) { x.tl = x.t.toLowerCase(); x.xl = (x.x || '').toLowerCase(); }
  return idx;
}
function search(q) {
  INDEX = INDEX || buildIndex();
  const words = q.toLowerCase().trim().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const res = [];
  for (const e of INDEX) {
    let s = 0, ok = true;
    for (const w of words) {
      let ws = 0;
      if (e.tl.startsWith(w)) ws = 12; else if (e.tl.includes(' ' + w)) ws = 9; else if (e.tl.includes(w)) ws = 6;
      else if (e.id && e.id.includes(w)) ws = 5; else if (e.xl.includes(w)) ws = 2;
      if (!ws) { ok = false; break; }
      s += ws;
    }
    if (ok) res.push([s - e.t.length / 100 + (e.g === 'Guides' ? 1 : 0), e]);
  }
  return res.sort((a, b) => b[0] - a[0]).slice(0, 40).map(r => r[1]);
}
let sel = 0;
function renderResults(q) {
  const box = $('#sres'), res = search(q);
  if (!q.trim()) { box.innerHTML = `<div class="none">Type to search ${Object.keys(D.items).length} items, ${D.guides.length} guides and the Codex.</div>`; return; }
  if (!res.length) { box.innerHTML = '<div class="none">Nothing found.</div>'; return; }
  const groups = ['Guides', 'Machines', 'Items', 'Codex'];
  let html = '', n = 0;
  for (const g of groups) {
    const rs = res.filter(r => r.g === g).slice(0, g === 'Items' ? 10 : 5);
    if (!rs.length) continue;
    html += `<h5>${g}</h5>` + rs.map(r => `<a href="${r.h}" role="option" data-n="${n++}">${icon(r.ic)}<div>${esc(r.t)}<small>${esc(r.sub || '')}</small></div></a>`).join('');
  }
  box.innerHTML = html;
  sel = 0; mark();
}
function mark() {
  const all = [...document.querySelectorAll('#sres a')];
  all.forEach((a, i) => { a.classList.toggle('sel', i === sel); a.setAttribute('aria-selected', i === sel); });
  if (all[sel]) all[sel].scrollIntoView({block: 'nearest'});
}
let lastFocus = null;
function openSearch(q = '') {
  lastFocus = document.activeElement;
  $('#sbox').classList.add('open');
  const inp = $('#sinput');
  inp.value = q; renderResults(q); inp.focus(); inp.select();
}
function closeSearch() { $('#sbox').classList.remove('open'); if (lastFocus) lastFocus.focus(); }

/* ------------------------------------------------------------ tooltip, theme, boot */

const tip = () => $('#tip');
function showTip(e) {
  const s = e.target.closest && e.target.closest('[data-item]');
  if (!s) return;
  const id = s.dataset.item, it = D.items[id], f = factOf(id);
  const desc = HOME_TAGS[id] && document.body.classList.contains('home') ? HOME_TAGS[id] : plain((f && f.what) || (it && it.t) || '');
  tip().innerHTML = `<div>${esc(nameOf(id))}</div>${desc ? `<div class="d">${esc(desc.length > 110 ? desc.slice(0, 108) + '…' : desc)}</div>` : ''}${it ? `<div class="m">Robotica</div>` : ''}`;
  tip().style.display = 'block';
  const r = s.getBoundingClientRect(), x = e.clientX ?? r.right, y = e.clientY ?? r.top;
  tip().style.left = Math.max(8, Math.min(x + 14, innerWidth - tip().offsetWidth - 8)) + 'px';
  tip().style.top = Math.max(8, y - tip().offsetHeight - 10) + 'px';
}
function boot() {
  try { const t = localStorage.getItem('theme'); if (t) document.documentElement.dataset.theme = t; } catch (e) { /* storage off */ }
  $('#themebtn').addEventListener('click', () => {
    const dark = document.documentElement.dataset.theme ? document.documentElement.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.dataset.theme = dark ? 'light' : 'dark';
    try { localStorage.setItem('theme', document.documentElement.dataset.theme); } catch (e) { /* storage off */ }
  });
  $('#menubtn').addEventListener('click', () => {
    const open = document.body.classList.toggle('menu-open');
    $('#menubtn').setAttribute('aria-expanded', open);
    if (open) { if (document.body.classList.contains('home')) { document.body.classList.remove('home'); } $('#side').scrollIntoView(); }
  });
  $('#searchbtn').addEventListener('click', () => openSearch());
  $('#sbox').addEventListener('click', e => { if (e.target.id === 'sbox') closeSearch(); });
  $('#sinput').addEventListener('input', e => renderResults(e.target.value));
  $('#sres').addEventListener('click', e => { if (e.target.closest('a')) $('#sbox').classList.remove('open'); });
  document.addEventListener('keydown', e => {
    const open = $('#sbox').classList.contains('open');
    if (!open && e.key === '/' && !/INPUT|TEXTAREA/.test(document.activeElement.tagName)) { e.preventDefault(); openSearch(); return; }
    if (!open) return;
    const n = document.querySelectorAll('#sres a').length;
    if (e.key === 'Escape') closeSearch();
    else if (e.key === 'ArrowDown') { e.preventDefault(); sel = Math.min(n - 1, sel + 1); mark(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); sel = Math.max(0, sel - 1); mark(); }
    else if (e.key === 'Enter') { const a = document.querySelectorAll('#sres a')[sel]; if (a) { $('#sbox').classList.remove('open'); location.hash = a.getAttribute('href'); } }
    else if (e.key === 'Tab') { e.preventDefault(); $('#sinput').focus(); }
  });
  document.addEventListener('mousemove', e => e.target.closest && e.target.closest('.slot[data-item], th [data-item]') ? showTip(e) : (tip().style.display = 'none'));
  document.addEventListener('focusin', e => { if (e.target.closest && e.target.closest('.slot[data-item]')) showTip({target: e.target}); });
  document.addEventListener('focusout', () => { tip().style.display = 'none'; });
  window.addEventListener('hashchange', route);
  fetch('wiki/data.json').then(r => r.json()).then(d => { D = d; route(); })
    .catch(() => { $('#page').innerHTML = '<p style="padding:24px">The wiki data did not load. Serve the docs folder over http (GitHub Pages does).</p>'; });
}
if (typeof document !== 'undefined') boot();
if (typeof module !== 'undefined') module.exports = {setData: d => { D = d; }, md, itemPage, guidePage, guidesPage, codexPage, itemsPage, machinesPage,
  upgradesPage, multiblocksPage, multiblockPage, progressionPage, search, plain};
