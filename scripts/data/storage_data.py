"""Storage module data: Storage Terminal models and blockstate (a lit variant with the screen full-bright and
animated), the block loot table and the recipes of the terminal and the three Storage Expansions.
Run: python3 scripts/data/storage_data.py   (overwrites only files named after storage blocks and items)

Item models of the expansions are written by scripts/textures/storage.py.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from machine_models import machine_model  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'

FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def terminal_model(lit):
    """The shared machine casing in light steel; the screen sits 1 px deep (SCREEN in scripts/textures/storage.py:
    x 3..12, y 3..10 of the front) with its bezel walls round it, full-bright and animated while powered."""
    suffix = '_on' if lit else ''
    faces = {'north': 'storage_terminal_front' + suffix, 'up': 'storage_terminal_top', 'down': 'storage_terminal_bottom',
             'south': 'storage_terminal_side', 'west': 'storage_terminal_side', 'east': 'storage_terminal_side'}
    x0, x1, y0, y1, z = 3, 13, 5, 13, 1

    def el(a, b, d, uv):
        return {'from': a, 'to': b, 'faces': {d: {'texture': '#screen', 'uv': uv}}, 'glow': lit}
    els = [el([x0, y0, z], [x1, y1, z], 'north', [3, 3, 13, 11]),
           el([x0, y0, 0], [x0, y1, z], 'east', [13, 3, 14, 11]),
           el([x1, y0, 0], [x1, y1, z], 'west', [2, 3, 3, 11]),
           el([x0, y1, 0], [x1, y1, z], 'down', [3, 2, 13, 3]),
           el([x0, y0, 0], [x1, y0, z], 'up', [3, 11, 13, 12])]
    glow = {'north': 'storage_terminal_front_glow', 'up': 'storage_terminal_top_glow'} if lit else None
    return machine_model(faces, glow, els, {'screen': 'storage_terminal_screen' + suffix}, lit=lit)


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
               # the items travel inside the terminal, so it always drops (no explosion roll)
               'entries': [{'type': 'minecraft:item', 'name': 'robotica:storage_terminal',
                            'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity',
                                           'include': ['robotica:contents']}]}]}],
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
       {'C': CHEST, 'I': 'iron_casing', 'R': COPPER, 'T': '#c:player_workstations/crafting_tables', 'B': 'basic_circuit'})
# Expansions climb the component ladder; each Mk eats the one before.
shaped('storage_expansion_mk1', ['CPC', 'PIP', 'CBC'],
       {'C': CHEST, 'P': IRON_PLATE, 'I': 'iron_casing', 'B': 'basic_circuit'})
shaped('storage_expansion_mk2', ['CGC', 'AMX', 'CGC'],
       {'C': CHEST, 'G': GOLD_PLATE, 'A': 'advanced_circuit', 'M': 'storage_expansion_mk1', 'X': 'reinforced_casing'})
shaped('storage_expansion_mk3', ['CDC', 'QMB', 'CDC'],
       {'C': CHEST, 'D': DIAMOND, 'Q': 'quantum_circuit', 'M': 'storage_expansion_mk2', 'B': 'blazing_casing'})
print('storage data written')
