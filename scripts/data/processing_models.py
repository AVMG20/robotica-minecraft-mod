"""Writes blockstates, block/item models and loot tables of the processing module (Grinder and Electric Furnace Mk1-4).
Run: python3 scripts/data/processing_models.py   (overwrites files named after processing blocks only)

Each machine is one cube: Mk-coloured front/top/sides, a shared bottom, and while lit a coplanar full-bright overlay on
the front (sparks, glowing coils). The blockstate turns it by FACING. The loot table keeps the stored energy; the contents
spill when the machine breaks.
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


def machine_model(faces, glow=None):
    tex_vars = {f: tex(t) for f, t in faces.items()}
    tex_vars['particle'] = tex(faces['south'])
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16],
                 'faces': {d: {'texture': '#' + d, 'cullface': d} for d in DIRS}}]
    if glow:
        for f, t in glow.items():
            tex_vars['glow_' + f] = tex(t)
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {d: {'texture': '#glow_' + d, 'cullface': d, 'neoforge_data': GLOW} for d in glow}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': tex_vars, 'elements': elements}


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


for kind in ('grinder', 'electric_furnace'):
    for mk in (1, 2, 3, 4):
        name = f'{kind}_mk{mk}'
        for lit in (False, True):
            faces = {'north': f'{name}_front' + ('_on' if lit else ''), 'up': f'{name}_top',
                     'down': 'processing_machine_bottom', 'south': f'processing_mk{mk}_side',
                     'west': f'processing_mk{mk}_side', 'east': f'processing_mk{mk}_side'}
            glow = {'north': f'{kind}_front_glow'} if lit else None
            write(ASSETS / 'models/block' / f'{name}{"_on" if lit else ""}.json', machine_model(faces, glow))
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
