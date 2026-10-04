"""
Static asset audit. Run after `./gradlew runGameTestServer` (which writes run/robotica-audit/items.txt).

Checks for every Robotica item: item model exists, every texture/parent it references exists,
an en_us name exists (merged fragments), and for block items: blockstate, block models and loot table.
Also checks the Codex chapters only reference real items.
Exit code 1 when anything is missing.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/robotica'
DATA = RES / 'data/robotica'
LANG = ROOT / 'build/generated/fragments/assets/robotica/lang/en_us.json'
ITEMS = ROOT / 'run/robotica-audit/items.txt'

problems = []


def model_path(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    return ns, RES / f'assets/{ns}/models/{path}.json'


def texture_ok(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    return ns != 'robotica' or (RES / f'assets/robotica/textures/{path}.png').exists()


def check_model(ref, owner, seen):
    if ref in seen:
        return
    seen.add(ref)
    ns, p = model_path(ref)
    if ns != 'robotica':
        return
    if not p.exists():
        problems.append(f'{owner}: missing model {ref}')
        return
    m = json.loads(p.read_text())
    for tex in m.get('textures', {}).values():
        if not tex.startswith('#') and not texture_ok(tex):
            problems.append(f'{owner}: model {ref} uses missing texture {tex}')
    if 'parent' in m:
        check_model(m['parent'], owner, seen)


def models_in_blockstate(bs):
    out = []
    def walk(v):
        if isinstance(v, dict):
            if 'model' in v:
                out.append(v['model'])
            for x in v.values():
                walk(x)
        elif isinstance(v, list):
            for x in v:
                walk(x)
    walk(bs)
    return out


if not ITEMS.exists():
    sys.exit('Run ./gradlew runGameTestServer first (it writes run/robotica-audit/items.txt).')
items = ITEMS.read_text().split()
lang = json.loads(LANG.read_text()) if LANG.exists() else {}

for name in items:
    seen = set()
    item_model = ASSETS / f'models/item/{name}.json'
    if not item_model.exists():
        problems.append(f'{name}: missing item model')
    else:
        check_model(f'robotica:item/{name}', name, seen)
    blockstate = ASSETS / f'blockstates/{name}.json'
    is_block = blockstate.exists()
    if f'item.robotica.{name}' not in lang and f'block.robotica.{name}' not in lang:
        problems.append(f'{name}: missing lang name')
    if is_block:
        for ref in models_in_blockstate(json.loads(blockstate.read_text())):
            check_model(ref, name, seen)
        if not (DATA / f'loot_table/blocks/{name}.json').exists():
            problems.append(f'{name}: block without loot table')

codex = ASSETS / 'codex/chapters.json'
if codex.exists():
    for ch in json.loads(codex.read_text())['chapters']:
        for ref in [ch['icon']] + [i for p in ch['pages'] for i in p.get('items', [])]:
            if ref.startswith('robotica:') and ref.split(':')[1] not in items:
                problems.append(f'codex: unknown item {ref} in chapter "{ch["title"]}"')

for p in problems:
    print('MISSING', p)
print(f'{len(items)} items checked, {len(problems)} problems')
sys.exit(1 if problems else 0)
