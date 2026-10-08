"""Builds the Robotica wiki data for the GitHub Pages site (docs/index.html is the app).

Run: python3 scripts/wiki/build_wiki.py
Reads (generated, never hand-copied)
- lang fragments src/main/fragments/*/assets/robotica/lang/en_us.json   names, tooltips, module of every item
- item and block models + textures                                     icons (flat items, isometric blocks)
- data/robotica/recipe/**                                              "made in" hints only (JEI and the Codex show recipes)
- scripts/data/codex_guide.py (STEPS) + codex/guide.json               progression by Age, age of every item
- assets/robotica/codex/chapters.json                                  Codex pages per item
- worldgen + biome modifiers + loot tables + block tags                where to find ores, the Rusted Foundry
Joins the curated sources next to this script
- upgrades.json        which machine takes which card and what it does; slots and caps come from UpgradeRules.java
- multiblocks.json     example structures, checked by multiblock_check.py    (keep in sync with the structure rules)
- items/*.json         what each item does, how to use it, key numbers       (keep in sync with configs)
- guides/*.md          the wiki guides
Writes docs/wiki/data.json, docs/wiki/icons/*.png and docs/wiki/formed/*.png. Idempotent: files are only rewritten when their bytes change.
"""
import importlib.util
import json
import math
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SRC = ROOT / 'src/main'
RES = SRC / 'resources'
ASSETS = RES / 'assets/robotica'
DATA = RES / 'data/robotica'
OUT = ROOT / 'docs/wiki'
ICONS = OUT / 'icons'

sys.path.insert(0, str(ROOT / 'scripts'))
sys.path.insert(0, str(HERE))
import model_preview as mp  # noqa: E402  (PNG reader and isometric model renderer)
import scene_preview  # noqa: E402  (formed multiblock views)

MODULE_NAMES = {
    'core': 'Parts', 'power': 'Power', 'energy': 'Big energy', 'industry': 'Industry', 'processing': 'Ore processing',
    'automation': 'Robots and quarries', 'drones': 'Drones', 'gear': 'Tools and weapons', 'exo': 'Exo-Frame',
    'architect': 'Base builder', 'replicator': 'Mob replicator', 'warp': 'Warp', 'storage': 'Storage',
    'boss': 'Bosses', 'codex': 'Codex', 'multiblock': 'Multiblocks', 'compat': 'Compat', 'sounds': 'Sounds',
}
MODULE_ORDER = ['core', 'power', 'automation', 'processing', 'industry', 'energy', 'gear', 'drones', 'exo',
                'replicator', 'storage', 'warp', 'architect', 'boss', 'codex']
AGE_NAMES = ['Clockwork', 'Wired', 'Servo', 'Deep', 'Antigrav']

WARN = []
_written = {'changed': 0, 'same': 0}


def warn(msg):
    WARN.append(msg)


def write_bytes(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_bytes() == data:
        _written['same'] += 1
        return
    path.write_bytes(data)
    _written['changed'] += 1


def png(img):
    import struct
    import zlib
    raw = bytearray()
    for row in img:
        raw.append(0)
        for p in row:
            raw.extend(p)

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    h, w = len(img), len(img[0])
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


def short(rid):
    """robotica:foo -> foo; other ids stay namespaced."""
    return rid[len('robotica:'):] if rid.startswith('robotica:') else rid


# ---------------------------------------------------------------- lang and items

def load_lang():
    lang, owner = {}, {}
    for f in sorted(SRC.glob('fragments/*/assets/robotica/lang/en_us.json')):
        module = f.parts[-5]
        for k, v in json.loads(f.read_text()).items():
            lang[k] = v
            owner.setdefault(k, module)
    extra = ASSETS / 'lang/en_us.json'
    if extra.exists():
        lang.update(json.loads(extra.read_text()))
    return lang, owner


def item_ids(lang):
    ids = set()
    for k in lang:
        parts = k.split('.')
        if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == 'robotica':
            ids.add(parts[2])
    models = {p.stem for p in (ASSETS / 'models/item').glob('*.json')}
    return sorted(ids & models), sorted(ids - models)


def display_name(lang, i):
    return lang.get(f'item.robotica.{i}') or lang.get(f'block.robotica.{i}')


def tooltip(lang, i):
    first = lang.get(f'tooltip.robotica.{i}') or lang.get(f'item.robotica.{i}.tooltip')
    details = lang.get(f'tooltip.robotica.{i}.details')
    # lines with config arguments are shown in game only; the wiki item entries give the default numbers
    clean = lambda s: None if not s or re.search(r'%\d*\$?[sd]', s) else s.replace('%%', '%')
    return clean(first), clean(details)


# ---------------------------------------------------------------- icons

ICON_PX = 64            # block sprites: 64x64, one block = 16 model units at scale 2
ISO_SCALE = 2.0
ISO_OX, ISO_OY = 4, 48  # screen position of model point (0, 0, 0) inside the sprite (the app tiles with the same numbers)


def flat_icon(textures):
    layers = [textures[k] for k in sorted(textures) if k.startswith('layer')]
    if not layers:
        return None
    w0 = mp.texture(mp.tex_ref(textures, layers[0]))[0]
    img = [[(0, 0, 0, 0)] * w0 for _ in range(w0)]
    for ref in layers:
        tw, th, px = mp.texture(mp.tex_ref(textures, ref))
        for y in range(min(th, w0)):
            for x in range(min(tw, w0)):
                if px[y][x][3] > 0:
                    img[y][x] = px[y][x]
    return img


def block_icon(model_ref):
    img = [[(0, 0, 0, 0)] * ICON_PX for _ in range(ICON_PX)]
    zbuf = [[-1e9] * ICON_PX for _ in range(ICON_PX)]
    mp.render_model(img, zbuf, model_ref, ISO_OX, ISO_OY, ISO_SCALE)
    return img


def make_icon(item_id):
    """Returns (png bytes, kind) for a Robotica item: 'block' isometric render or 'item' flat texture."""
    ref = f'robotica:item/{item_id}'
    try:
        textures, elements = mp.resolve(ref)
    except (FileNotFoundError, KeyError) as e:
        warn(f'icon {item_id}: model chain not readable ({e})')
        return None, None
    if elements:
        img = block_icon(ref)
        if any(p[3] for row in img for p in row):
            return png(img), 'block'
        warn(f'icon {item_id}: empty block render')
    img = flat_icon(textures)
    if img is None:
        tex = textures.get('particle') or next((v for v in textures.values() if not v.startswith('#')), None)
        if tex:
            tw, th, px = mp.texture(tex)
            img = [row[:tw] for row in px[:tw]]
    if img is None:
        warn(f'icon {item_id}: no texture')
        return None, None
    return png(img), 'item'


def build_icons(ids):
    kinds = {}
    for i in ids:
        data, kind = make_icon(i)
        if data:
            write_bytes(ICONS / f'{i}.png', data)
            kinds[i] = kind
    keep = {f'{i}.png' for i in kinds}
    for old in ICONS.glob('*.png'):
        if old.name not in keep:
            old.unlink()
    return kinds


# ---------------------------------------------------------------- recipes (hints only), guide steps, ages

STATIONS = {
    'minecraft:crafting_shaped': 'minecraft:crafting_table', 'minecraft:crafting_shapeless': 'minecraft:crafting_table',
    'robotica:machine_upgrade': 'minecraft:crafting_table', 'minecraft:smithing_transform': 'minecraft:smithing_table',
    'minecraft:smelting': 'minecraft:furnace', 'minecraft:blasting': 'minecraft:blast_furnace',
    'minecraft:stonecutting': 'minecraft:stonecutter', 'robotica:grinding': 'grinder_mk1',
    'robotica:alloying': 'alloy_smelter_mk1', 'robotica:assembling': 'assembler_mk1',
    'robotica:centrifuging': 'centrifuge_mk1', 'robotica:pressing': 'metal_press',
}


def recipe_outputs(r):
    out = []
    res = r.get('result')
    if isinstance(res, dict):
        out.append(res.get('id') or res.get('item'))
    elif isinstance(res, str):
        out.append(res)
    for x in r.get('results', []):
        out.append(x.get('id') or x.get('item'))
    for x in r.get('extras', []):
        out.append(x['result']['id'])
    return [o for o in out if o]


def load_recipes():
    """recipe id -> (type, outputs). Counts every type so nothing is silently dropped."""
    recipes, counts = {}, {}
    for f in sorted((DATA / 'recipe').rglob('*.json')):
        r = json.loads(f.read_text())
        rid = str(f.relative_to(DATA / 'recipe').with_suffix(''))
        t = r.get('type')
        counts[t] = counts.get(t, 0) + 1
        if t not in STATIONS:
            warn(f'recipe {rid}: unknown type {t} (no "made in" hint)')
        recipes[rid] = (t, recipe_outputs(r))
    return recipes, counts


def load_codex_guide():
    spec = importlib.util.spec_from_file_location('codex_guide', ROOT / 'scripts/data/codex_guide.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.STEPS


def criterion_items(crit):
    crits = crit if isinstance(crit, list) else [crit]
    items = []
    for c in crits:
        for group in c.get('conditions', {}).get('items', []):
            items += group.get('items', [])
        for loc in c.get('conditions', {}).get('location', []):
            items += loc.get('predicate', {}).get('block', {}).get('blocks', [])
    return [short(i) for i in items]


# Key mappings named in guide steps ({key:<mapping>} in codex_guide.py): how the wiki names them.
KEY_NAMES = {'key.robotica.exo.toggle_flight': 'the Flight key (default K)',
             'key.robotica.exo.open_modules': 'the Exo Modules key (default J)'}


def step_text(lang, key, desc):
    text = lang.get(key + '.description', desc)
    for k in re.findall(r'\{key:([a-z0-9_.]+)\}', desc):
        if k not in KEY_NAMES:
            warn(f'{key}: no wiki name for key mapping {k}')
        text = text.replace('%s', KEY_NAMES.get(k, k), 1)
    return text


def build_steps(lang, recipes):
    steps, age_of, step_of = [], {}, {}
    for name, parent, icon, frame, age, title, desc, crit, unlocks, xp in load_codex_guide():
        key = f'advancements.robotica.guide.{name}'
        made = []
        for rid in unlocks:
            for out in recipes.get(rid, (None, []))[1]:
                o = short(out)
                if o not in made:
                    made.append(o)
                if o not in age_of or age < age_of[o]:
                    age_of[o] = age
                    step_of[o] = name
        steps.append({'id': name, 'parent': parent, 'icon': short(icon if ':' in icon else f'robotica:{icon}'),
                      'age': age, 'goal': frame != 'task', 'title': lang.get(key + '.title', title),
                      'text': step_text(lang, key, desc), 'unlocks': made, 'needs': criterion_items(crit)})
    # Items no recipe makes (boss drops, ores) take the age of the first step that asks for them.
    for s in steps:
        for i in s['needs']:
            if i not in age_of:
                age_of[i] = s['age']
                step_of[i] = s['id']
    return steps, age_of, step_of


def made_in(recipes):
    where = {}
    for rid, (t, outs) in recipes.items():
        st = STATIONS.get(t)
        if not st:
            continue
        for o in outs:
            lst = where.setdefault(short(o), [])
            if st not in lst:
                lst.append(st)
    return where


# ---------------------------------------------------------------- codex chapters

def build_codex():
    chapters = json.loads((ASSETS / 'codex/chapters.json').read_text())['chapters']
    out = []
    for c in chapters:
        if c.get('special'):
            continue
        pages = []
        for p in c.get('pages', []):
            page = {'title': p.get('title', ''), 'text': p.get('text', ''), 'items': [short(i) for i in p.get('items', [])]}
            if p.get('layout'):
                page['layout'] = p['layout']
            pages.append(page)
        out.append({'title': c['title'], 'icon': short(c.get('icon', '')), 'pages': pages})
    return out


# ---------------------------------------------------------------- world: ores and structures

HOSTS = {'minecraft:stone_ore_replaceables': 'stone', 'minecraft:deepslate_ore_replaceables': 'deepslate',
         'minecraft:base_stone_nether': 'netherrack, basalt, blackstone', 'minecraft:netherrack': 'netherrack',
         'minecraft:end_stone': 'end stone'}
DIMENSION_OF_TAG = {'#minecraft:is_overworld': 'Overworld', '#minecraft:is_nether': 'Nether', '#minecraft:is_end': 'The End'}


def pretty_biome(b):
    b = b.lstrip('#').split(':', 1)[-1].replace('is_', '').replace('_', ' ')
    return b[:1].upper() + b[1:]


def dimension_of(biomes):
    if isinstance(biomes, str):
        biomes = [biomes]
    dims, names = [], []
    for b in biomes:
        if b in DIMENSION_OF_TAG:
            dims.append(DIMENSION_OF_TAG[b])
            names.append('all biomes')
            continue
        name = b.split(':', 1)[-1]
        dims.append('The End' if name.startswith('end_') else 'Nether' if name in (
            'basalt_deltas', 'nether_wastes', 'soul_sand_valley', 'crimson_forest', 'warped_forest') else 'Overworld')
        names.append(pretty_biome(b))
    return sorted(set(dims)), names


def height_of(h):
    lo, hi = h['min_inclusive'], h['max_inclusive']
    lo = lo.get('absolute', lo.get('above_bottom'))
    hi = hi.get('absolute', hi.get('below_top'))
    kind = h.get('type', 'minecraft:uniform').split(':')[1]
    out = {'min': lo, 'max': hi, 'shape': kind}
    if kind == 'trapezoid':
        out['best'] = (lo + hi) // 2   # trapezoid with plateau 0: a triangle peaking in the middle
    return out


def loot_drop(block):
    f = DATA / 'loot_table/blocks' / f'{block}.json'
    if not f.exists():
        return None
    entries = json.loads(f.read_text())['pools'][0]['entries'][0]
    drop = {'silk': False, 'fortune': False, 'item': None}
    for child in entries.get('children', [entries]):
        conds = json.dumps(child.get('conditions', []))
        if 'silk_touch' in conds:
            drop['silk'] = True
            continue
        drop['item'] = short(child['name'])
        drop['fortune'] = any(fn.get('enchantment') == 'minecraft:fortune' for fn in child.get('functions', []))
    return drop


def tool_tier(block):
    for tier in ('diamond', 'iron', 'stone'):
        for f in SRC.glob(f'fragments/*/data/minecraft/tags/block/needs_{tier}_tool.json'):
            if f'robotica:{block}' in json.loads(f.read_text()).get('values', []):
                return tier
    return 'wood'


def ore_xp():
    src = (SRC / 'java/com/arno/robotica/industry/IndustryRegistry.java').read_text()
    return {m[0]: (int(m[1]), int(m[2])) for m in re.findall(r'ore\("(\w+)", UniformInt\.of\((\d+), (\d+)\)', src)}


def biome_tag(path):
    for f in list(SRC.glob(f'fragments/*/data/robotica/tags/worldgen/biome/{path}.json')) + [DATA / 'tags/worldgen/biome' / f'{path}.json']:
        if f.exists():
            return json.loads(f.read_text())['values']
    warn(f'biome tag robotica:{path} not found')
    return []


def build_world():
    wg = DATA / 'worldgen'
    xp = ore_xp()
    ores = {}
    for mod in sorted((DATA / 'neoforge/biome_modifier').glob('*.json')):
        bm = json.loads(mod.read_text())
        features = bm['features'] if isinstance(bm['features'], list) else [bm['features']]
        for feat in features:
            name = feat.split(':')[1]
            placed = json.loads((wg / 'placed_feature' / f'{name}.json').read_text())
            conf = json.loads((wg / 'configured_feature' / f'{short(placed["feature"])}.json').read_text())
            count, height = None, None
            for p in placed['placement']:
                if p['type'] == 'minecraft:count':
                    count = p['count']
                elif p['type'] == 'minecraft:height_range':
                    height = height_of(p['height'])
            dims, biomes = dimension_of(bm['biomes'])
            blocks = [short(t['state']['Name']) for t in conf['config']['targets']]
            hosts = []
            for t in conf['config']['targets']:
                tgt = t['target'].get('tag') or t['target'].get('block', '')
                hosts.append(HOSTS.get(tgt, pretty_biome(tgt)))
            vein = {'feature': name, 'dimension': dims, 'biomes': biomes, 'perChunk': count, 'size': conf['config']['size'],
                    'height': height, 'hosts': hosts, 'airExposureDiscard': conf['config'].get('discard_chance_on_air_exposure', 0)}
            key = blocks[0]
            ore = ores.setdefault(key, {'blocks': blocks, 'veins': []})
            ore['veins'].append(vein)
    for key, ore in ores.items():
        ore['tool'] = tool_tier(key)
        ore['drop'] = loot_drop(key)
        if key in xp:
            ore['xp'] = list(xp[key])
    structures = []
    for s in sorted((wg / 'structure').glob('*.json')):
        st = json.loads(s.read_text())
        name = s.stem
        sset = json.loads((wg / 'structure_set' / f'{name}.json').read_text())
        biomes = st['biomes']
        if isinstance(biomes, str) and biomes.startswith('#robotica:'):
            biomes = biome_tag(biomes[len('#robotica:'):])
        pl = sset['placement']
        entry = {'id': name, 'biomes': [pretty_biome(b) for b in biomes], 'spacing': pl.get('spacing'),
                 'separation': pl.get('separation'), 'step': st.get('step')}
        java = SRC / 'java/com/arno/robotica'
        for f in java.rglob('*.java'):
            if f.stem.lower() == name.replace('_', '') + 'structure':
                src = f.read_text()
                for key, pat in (('size', r'int SIZE = (\d+)'), ('maxSlope', r'int MAX_SLOPE = (\d+)')):
                    m = re.search(pat, src)
                    if m:
                        entry[key] = int(m.group(1))
                entry['dryLand'] = 'OCEAN_FLOOR_WG' in src
        if 'exclusion_zone' in pl:
            entry['awayFrom'] = {'set': pretty_biome(pl['exclusion_zone']['other_set']), 'chunks': pl['exclusion_zone']['chunk_count']}
        structures.append(entry)
    return ores, structures


# ---------------------------------------------------------------- curated sources

JAVA = SRC / 'java/com/arno/robotica'


def card_rules():
    """Card stacks, the Mk rule and the fixed machines, read from the Java code so the wiki cannot drift."""
    kind_src = (JAVA / 'core/upgrade/UpgradeKind.java').read_text()
    kinds = {k.lower(): {'maxStack': int(a), 'age': int(b)}
             for k, a, b in re.findall(r'^\s+([A-Z]+)\((\d+), (\d+)\)[,;]', kind_src, re.M)}
    cfg = (JAVA / 'core/CoreConfig.java').read_text()
    per = {k: int(re.search(r'defineInRange\("%s", (\d+)' % k, cfg).group(1))
           for k in ('speedCapPerMk', 'efficiencyCapPerMk', 'rangeCapPerMk', 'fortuneCapMax')}
    rules = (JAVA / 'core/upgrade/UpgradeRules.java').read_text()
    max_mk = int(re.search(r'MAX_MK = (\d+);', rules).group(1))
    extra = int(re.search(r'return clampMk\(mk\) \+ (\d+);', rules).group(1))
    fixed = {}
    for name, slots, caps in re.findall(r'^\s+([A-Z_]+)\((\d+)((?:, limit\(UpgradeKind\.[A-Z]+, \d+\))+)\)[,;]', rules, re.M):
        fixed[name] = {'slots': int(slots), 'caps': {k.lower(): min(int(n), kinds[k.lower()]['maxStack'])
                                                    for k, n in re.findall(r'UpgradeKind\.([A-Z]+), (\d+)', caps)}}

    def mk_cap(mk, kind):
        cap = {'speed': per['speedCapPerMk'] * mk, 'efficiency': per['efficiencyCapPerMk'] * mk,
               'range': per['rangeCapPerMk'] * mk, 'fortune': min(mk, per['fortuneCapMax']), 'growth': mk}.get(kind, 1)
        return min(kinds[kind]['maxStack'], cap)

    mks = range(1, max_mk + 1)
    text = (f'Machines with a Mk: Mk + {extra} card slots; speed {per["speedCapPerMk"]} per Mk, efficiency '
            f'{per["efficiencyCapPerMk"]}, range {per["rangeCapPerMk"]} and growth 1 per Mk, fortune up to '
            f'{per["fortuneCapMax"]}, silk and void 1. Machines without a Mk have fixed slots.')
    return kinds, [mk + extra for mk in mks], text, fixed, mks, mk_cap


def load_upgrades(ids):
    up = json.loads((HERE / 'upgrades.json').read_text())
    kinds, mk_slots, rule_text, fixed, mks, mk_cap = card_rules()
    for k, c in up['cards'].items():
        if k in kinds:
            c.update(kinds[k])
        else:
            warn(f'upgrades.json: unknown card kind {k}')
    for m in up['machines']:
        rule = m.get('rule', '')
        if rule == 'mk':
            m['slots'] = mk_slots
            m['caps'] = {}
            for k in m['effects']:
                if k in kinds:
                    caps = [mk_cap(mk, k) for mk in mks]
                    m['caps'][k] = caps[0] if len(set(caps)) == 1 else caps
        elif rule.startswith('fixed:') and rule[6:] in fixed:
            m['slots'] = fixed[rule[6:]]['slots']
            m['caps'] = fixed[rule[6:]]['caps']
            for k in m['effects']:
                if k not in m['caps']:
                    warn(f'upgrades.json {m["id"]}: {rule[6:]} takes no {k} card')
        else:
            warn(f'upgrades.json {m["id"]}: rule must be mk or fixed:<UpgradeRules.Fixed name>, is {rule!r}')
            m['slots'], m['caps'] = 0, {}
        for i in m['items'] + [t for t in m.get('tierItems', []) if t]:
            if i not in ids:
                warn(f'upgrades.json {m["id"]}: unknown item {i}')
        for k in m['caps']:
            if k not in up['cards']:
                warn(f'upgrades.json {m["id"]}: unknown card kind {k}')
            if k not in m['effects']:
                warn(f'upgrades.json {m["id"]}: no effect text for {k}')
    table = {'tiers': [f'Mk{mk}' for mk in mks], 'slots': mk_slots,
             'caps': {k: [mk_cap(mk, k) for mk in mks] for k in ('speed', 'efficiency', 'range', 'growth', 'fortune', 'silk', 'void')}}
    return {'cards': up['cards'], 'machines': up['machines'], 'rule': rule_text, 'mkRule': table}


def load_multiblocks(ids):
    import multiblock_check
    mbs = json.loads((HERE / 'multiblocks.json').read_text())
    out = {}
    for key, mb in mbs.items():
        problems = multiblock_check.check(mb)
        if problems:
            sys.exit(f'multiblock {key} is not valid: ' + '; '.join(problems))
        for b in mb['legend'].values():
            if ':' not in b and b != 'air' and b not in ids:
                warn(f'multiblock {key}: unknown block {b}')
        out[key] = {k: mb[k] for k in ('title', 'controller', 'guide', 'about', 'notes', 'legend', 'layers') if k in mb}
        out[key]['parts'] = multiblock_check.parts(mb)
        out[key]['size'] = [len(mb['layers'][0][0]), len(mb['layers']), len(mb['layers'][0])]
        if mb.get('check') in scene_preview.FORMED_CHECKS:      # the finished build, as the game draws it formed
            write_bytes(OUT / 'formed' / f'{key}.png', scene_preview.render_png(key))
            out[key]['formed'] = True
    return out


def load_facts(ids):
    facts = {}
    for f in sorted((HERE / 'items').glob('*.json')):
        for e in json.loads(f.read_text())['entries']:
            for i in e['items']:
                if i not in ids:
                    warn(f'{f.name}: unknown item {i}')
                if i in facts:
                    warn(f'{f.name}: {i} has two entries')
                facts[i] = e
    return facts


FRONT = re.compile(r'^---\n(.*?)\n---\n', re.S)


def load_guides():
    guides = []
    for f in sorted((HERE / 'guides').glob('*.md')):
        text = f.read_text()
        m = FRONT.match(text)
        meta = {}
        if m:
            for line in m.group(1).splitlines():
                k, _, v = line.partition(':')
                meta[k.strip()] = v.strip()
            text = text[m.end():]
        guides.append({'slug': f.stem, 'title': meta.get('title', f.stem), 'icon': meta.get('icon', 'codex'),
                       'order': int(meta.get('order', 99)), 'summary': meta.get('summary', ''), 'body': text.strip() + '\n'})
    guides.sort(key=lambda g: (g['order'], g['slug']))
    return guides


def strings(x):
    if isinstance(x, str):
        yield x
    elif isinstance(x, dict):
        for v in x.values():
            yield from strings(v)
    elif isinstance(x, list):
        for v in x:
            yield from strings(v)


REF = re.compile(r'\[\[([^\]|]+)(?:\|[^\]]*)?\]\]|\{\{(items|multiblock|upgrades|guide|image)\s+([^}]*)\}\}')


def check_refs(where, text, ids, mbs, guide_slugs, upgrade_items):
    for m in REF.finditer(text):
        if m.group(1):
            ref = m.group(1).strip()
            if ':' not in ref and not ref.startswith('#') and ref not in ids:
                warn(f'{where}: unknown item [[{ref}]]')
            continue
        kind, arg = m.group(2), m.group(3).strip()
        if kind == 'items':
            for i in arg.split():
                if ':' not in i and i not in ids:
                    warn(f'{where}: unknown item {i} in {{{{items}}}}')
        elif kind == 'multiblock' and arg not in mbs:
            warn(f'{where}: unknown multiblock {arg}')
        elif kind == 'guide' and arg not in guide_slugs:
            warn(f'{where}: unknown guide {arg}')
        elif kind == 'upgrades' and arg not in upgrade_items:
            warn(f'{where}: no upgrade row for {arg}')
        elif kind == 'image' and not (ROOT / 'docs' / arg.split('|')[0]).exists():
            warn(f'{where}: missing image {arg}')


# ---------------------------------------------------------------- main

def vanilla_name(rid):
    return rid.split(':', 1)[1].replace('_', ' ').capitalize()


def main():
    lang, owner = load_lang()
    ids, no_model = item_ids(lang)
    for i in no_model:
        if (ASSETS / 'blockstates' / f'{i}.json').exists():
            continue  # a block without an item (Spark Lamp)
        warn(f'item {i}: has a name but no item model (left out)')
    idset = set(ids)
    kinds = build_icons(ids)
    recipes, recipe_counts = load_recipes()
    steps, age_of, step_of = build_steps(lang, recipes)
    where = made_in(recipes)
    ores, structures = build_world()
    upgrades = load_upgrades(idset)
    mbs = load_multiblocks(idset)
    facts = load_facts(idset)
    guides = load_guides()
    slugs = {g['slug'] for g in guides}
    upgrade_of = {}
    for m in upgrades['machines']:
        for i in m['items']:
            upgrade_of[i] = m['id']
    for g in guides:
        check_refs(f'guide {g["slug"]}', g['body'], idset, mbs, slugs, upgrade_of)
    fact_list, fact_index = [], {}
    for i, e in facts.items():
        key = id(e)
        if key not in fact_index:
            fact_index[key] = len(fact_list)
            fact_list.append({k: v for k, v in e.items() if k != 'items'} | {'items': e['items']})
            check_refs(f'facts {e["items"][0]}', '\n'.join(strings(e)), idset, mbs, slugs, upgrade_of)
            for see in e.get('see', []):
                kind, _, arg = see.partition(':')
                if not ((kind == 'guide' and arg in slugs) or (kind == 'multiblock' and arg in mbs) or (kind == 'item' and arg in idset)):
                    warn(f'facts {e["items"][0]}: broken see link {see}')
            if 'image' in e and not (ROOT / 'docs' / e['image'].split('|')[0]).exists():
                warn(f'facts {e["items"][0]}: missing image {e["image"]}')
    mb_of = {}
    for key, mb in mbs.items():
        for b in mb['parts']:
            mb_of.setdefault(b, []).append(key)
    ore_of = {}
    for key, ore in ores.items():
        for b in ore['blocks']:
            ore_of[b] = key
        if ore['drop'] and ore['drop']['item']:
            ore_of.setdefault(ore['drop']['item'], key)

    items = {}
    for i in ids:
        module = owner.get(f'item.robotica.{i}') or owner.get(f'block.robotica.{i}')
        tip, details = tooltip(lang, i)
        it = {'n': display_name(lang, i), 'm': module, 'k': kinds.get(i, 'item')}
        if i in age_of:
            it['a'] = age_of[i]
            it['s'] = step_of[i]
        if tip:
            it['t'] = tip
        if details:
            it['d'] = details
        if where.get(i):
            it['made'] = where[i]
        if i in facts:
            it['f'] = fact_index[id(facts[i])]
        if i in upgrade_of:
            it['up'] = upgrade_of[i]
        if i in mb_of:
            it['mb'] = mb_of[i]
        if i in ore_of:
            it['ore'] = ore_of[i]
        items[i] = it
        if not it['n']:
            warn(f'item {i}: no name')
        if i not in kinds:
            warn(f'item {i}: no icon')
    vanilla = set()
    blob = json.dumps([guides, mbs, fact_list, upgrades, steps, list(where.values())])
    for m in re.finditer(r'minecraft:[a-z0-9_/]+', blob):
        vanilla.add(m.group(0))

    data = {
        'ages': [{'n': n, 'name': lang.get(f'age.robotica.{n}', AGE_NAMES[n])} for n in range(5)],
        'modules': [{'id': m, 'name': MODULE_NAMES.get(m, m.capitalize())}
                    for m in MODULE_ORDER + sorted({it['m'] for it in items.values()} - set(MODULE_ORDER))
                    if any(it['m'] == m for it in items.values())],
        'items': items,
        'vanilla': {v: vanilla_name(v) for v in sorted(vanilla)},
        'facts': fact_list,
        'steps': steps,
        'codex': build_codex(),
        'upgrades': upgrades,
        'multiblocks': mbs,
        'guides': guides,
        'world': {'ores': ores, 'structures': structures},
    }
    write_bytes(OUT / 'data.json', (json.dumps(data, ensure_ascii=False, separators=(',', ':'), sort_keys=True) + '\n').encode())

    no_facts = [i for i in ids if i not in facts]
    print(f'{len(items)} items ({sum(1 for k in kinds.values() if k == "block")} block icons), '
          f'{len(fact_list)} fact entries ({len(no_facts)} items without), {len(guides)} guides, {len(mbs)} multiblocks, '
          f'{len(upgrades["machines"])} upgrade rows, {len(steps)} guide steps, {len(ores)} ores, {len(structures)} structures')
    print('recipes read for "made in" hints: ' + ', '.join(f'{t} {n}' for t, n in sorted(recipe_counts.items())))
    print(f'files: {_written["changed"]} written, {_written["same"]} unchanged; data.json {(OUT / "data.json").stat().st_size // 1024} KB')
    if no_facts:
        print('items without curated facts: ' + ' '.join(no_facts))
    for w in WARN:
        print('WARN', w)


if __name__ == '__main__':
    main()
