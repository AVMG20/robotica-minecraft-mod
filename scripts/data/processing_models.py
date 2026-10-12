"""Writes blockstates, block/item models and loot tables of the processing module (Grinder and Electric Furnace Mk1-4).
Run: python3 scripts/data/processing_models.py   (overwrites files named after processing blocks only)

Each machine is the shared casing (scripts/data/machine_models.py) with its own front and top, one function per machine.
The blockstate turns it by FACING. The loot table keeps the stored energy; the contents
spill when the machine breaks.
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


def casing(tier):
    return {'down': 'processing_machine_bottom', 'south': f'processing_mk{tier}_side',
            'west': f'processing_mk{tier}_side', 'east': f'processing_mk{tier}_side'}


# ---------------------------------------------------------------- grinder

def grinder_model(tier, lit):
    """Casing with a crusher bay 2 px deep in the front: back wall at z 2 and four walls, all from the grinder_bay
    texture (the back wall in 2..13 x 3..12, the walls around it); lit, the animated bay plus its full-bright glow."""
    name = f'grinder_mk{tier}'
    faces = dict(casing(tier), north=f'{name}_front' + ('_on' if lit else ''), up=f'{name}_top')
    bay = '#bay'
    elements = [
        {'from': [2, 3, 2], 'to': [14, 13, 3], 'faces': {'north': {'texture': bay, 'uv': [2, 3, 14, 13]}}},
        {'from': [2, 13, 0], 'to': [14, 14, 2], 'faces': {'down': {'texture': bay, 'uv': [14, 0, 2, 2]}}},
        {'from': [2, 2, 0], 'to': [14, 3, 2], 'faces': {'up': {'texture': bay, 'uv': [14, 14, 2, 16]}}},
        {'from': [14, 3, 0], 'to': [15, 13, 2], 'faces': {'west': {'texture': bay, 'uv': [0, 3, 2, 13]}}},
        {'from': [1, 3, 0], 'to': [2, 13, 2], 'faces': {'east': {'texture': bay, 'uv': [14, 3, 16, 13]}}},
    ]
    textures = {'bay': 'grinder_bay_on' if lit else 'grinder_bay'}
    if lit:
        textures['bay_glow'] = 'grinder_bay_glow'
        elements += [
            {'from': [2, 3, 1.99], 'to': [14, 13, 2], 'glow': True,
             'faces': {'north': {'texture': '#bay_glow', 'uv': [2, 3, 14, 13]}}},
        ]
    glow = {'north': 'grinder_front_glow', 'up': 'grinder_top_glow'} if lit else None
    return machine_model(faces, glow, elements, textures, lit=lit)


# ---------------------------------------------------------------- electric furnace

def furnace_model(tier, lit):
    """The front opens into a chamber 2 px deep (FURNACE_BAY in scripts/textures/processing.py: x 3..12, y 3..10 of
    the front texture) with a billet floating in it; all their faces come from one chamber texture (FURNACE_PARTS
    there), full-bright while working."""
    name = f'electric_furnace_mk{tier}'
    faces = dict(casing(tier), north=f'{name}_front' + ('_on' if lit else ''), up=f'{name}_top')
    x0, x1, y0, y1, z = 3, 13, 5, 13, 2                                   # bay in model space

    def face(d, uv):
        return {'from': [x0 if d != 'west' else x1, y0 if d != 'down' else y1, 0 if d != 'north' else z],
                'to': [x1 if d != 'east' else x0, y1 if d != 'up' else y0, z],
                'faces': {d: {'texture': '#chamber', 'uv': uv}}, 'glow': lit}
    bay = [face('north', [3, 3, 13, 11]), face('east', [14, 3, 16, 11]), face('west', [0, 3, 2, 11]),
           face('up', [13, 0, 3, 2]), face('down', [13, 14, 3, 16]),
           {'from': [5, 6, 0.5], 'to': [11, 8, 1.5], 'glow': lit,                # billet, levitating over the plate
            'faces': {'north': {'texture': '#chamber', 'uv': [3, 11, 9, 13]},
                      'up': {'texture': '#chamber', 'uv': [9, 13, 3, 14]},
                      'east': {'texture': '#chamber', 'uv': [10, 11, 11, 13]},
                      'west': {'texture': '#chamber', 'uv': [10, 11, 11, 13]},
                      'down': {'texture': '#chamber', 'uv': [3, 12, 9, 13]}}}]
    glow = {'north': 'electric_furnace_front_glow', 'up': 'electric_furnace_top_glow'} if lit else None
    return machine_model(faces, glow, bay, {'chamber': 'electric_furnace_chamber' + ('_on' if lit else '')}, lit=lit)


def loot(name):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}',
             'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity',
                            'include': ['robotica:energy']}]}
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


MODELS = {'grinder': grinder_model, 'electric_furnace': furnace_model}
for kind, model in MODELS.items():
    for mk in (1, 2, 3, 4):
        name = f'{kind}_mk{mk}'
        for lit in (False, True):
            write(ASSETS / 'models/block' / f'{name}{"_on" if lit else ""}.json', model(mk, lit))
        variants = {}
        for lit in (False, True):
            for facing, y in FACING_Y.items():
                entry = {'model': f'robotica:block/{name}{"_on" if lit else ""}'}
                if y:
                    entry['y'] = y
                variants[f'facing={facing},lit={str(lit).lower()}'] = entry
        write(ASSETS / 'blockstates' / f'{name}.json', {'variants': variants})
        write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})
        loot(name)

print('processing models written')
