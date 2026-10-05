"""Storage module data: Storage Terminal models and blockstate (a lit variant with a full-bright screen overlay), the
block loot table and the recipes of the terminal and the three Storage Expansions.
Run: python3 scripts/data/storage_data.py   (overwrites only files named after storage blocks and items)

Item models of the expansions are written by scripts/textures/storage.py.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'

GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def terminal_model(lit):
    textures = {'north': tex('storage_terminal_front'), 'south': tex('storage_terminal_side'),
                'west': tex('storage_terminal_side'), 'east': tex('storage_terminal_side'),
                'up': tex('storage_terminal_top'), 'down': tex('storage_terminal_bottom'),
                'particle': tex('storage_terminal_side')}
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16],
                 'faces': {d: {'texture': '#' + d, 'cullface': d} for d in DIRS}}]
    if lit:
        textures['glow_north'] = tex('storage_terminal_front_glow')
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {'north': {'texture': '#glow_north', 'cullface': 'north', 'neoforge_data': GLOW}}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}


write(ASSETS / 'models/block/storage_terminal.json', terminal_model(False))
write(ASSETS / 'models/block/storage_terminal_on.json', terminal_model(True))
variants = {}
for lit in (False, True):
    for facing, y in FACING_Y.items():
        entry = {'model': 'robotica:block/storage_terminal' + ('_on' if lit else '')}
        if y:
            entry['y'] = y
        variants[f'facing={facing},lit={str(lit).lower()}'] = entry
write(ASSETS / 'blockstates/storage_terminal.json', {'variants': variants})
write(ASSETS / 'models/item/storage_terminal.json', {'parent': 'robotica:block/storage_terminal'})
write(DATA / 'loot_table/blocks/storage_terminal.json', {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
               'entries': [{'type': 'minecraft:item', 'name': 'robotica:storage_terminal'}],
               'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
    'random_sequence': 'robotica:blocks/storage_terminal',
})


# ---------------------------------------------------------------- recipes

def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key):
    write(DATA / 'recipe' / f'{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: ing(v) for k, v in key.items()}, 'result': {'id': f'robotica:{name}', 'count': 1}})


CHEST, COPPER, IRON_PLATE, GOLD_PLATE, DIAMOND = '#c:chests/wooden', '#c:ingots/copper', '#c:plates/iron', '#c:plates/gold', '#c:gems/diamond'

# Age 1: two chests, an iron casing, a crafting table, copper and a Basic Circuit.
shaped('storage_terminal', ['CIC', 'RTR', ' B '],
       {'C': CHEST, 'I': 'iron_casing', 'R': COPPER, 'T': 'minecraft:crafting_table', 'B': 'basic_circuit'})
# Expansions climb the component ladder; each Mk eats the one before.
shaped('storage_expansion_mk1', ['CPC', 'PIP', 'CBC'],
       {'C': CHEST, 'P': IRON_PLATE, 'I': 'iron_casing', 'B': 'basic_circuit'})
shaped('storage_expansion_mk2', ['CGC', 'AMX', 'CGC'],
       {'C': CHEST, 'G': GOLD_PLATE, 'A': 'advanced_circuit', 'M': 'storage_expansion_mk1', 'X': 'reinforced_casing'})
shaped('storage_expansion_mk3', ['CDC', 'QMB', 'CDC'],
       {'C': CHEST, 'D': DIAMOND, 'Q': 'quantum_circuit', 'M': 'storage_expansion_mk2', 'B': 'blazing_casing'})
print('storage data written')
