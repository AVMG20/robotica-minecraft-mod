"""Logistics module data: item pipe models (core, arm and flanges, all pointing north), the multipart blockstates, item
models, loot tables and recipes.
Run: python3 scripts/data/logistics_data.py   (overwrites only files named after logistics blocks)

Texture atlas layout: see scripts/textures/logistics.py.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'

PIPES = {'item_pipe': 'item_pipe', 'item_pipe_mk2': 'item_pipe_mk2'}
# Turns a north-pointing part to each face (x 270 turns north to up, x 90 to down).
ROT = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}

CORE_UV = [0, 0, 6, 6]
ARM_X_UV = [0, 8, 5, 12]       # length along u
ARM_Y_UV = [8, 0, 12, 5]       # length along v
FLANGE_UV = [0, 0, 8, 8]
EDGE_X_UV = [0, 8, 8, 10]
EDGE_Y_UV = [8, 0, 10, 8]


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def core_element():
    return {'from': [5, 5, 5], 'to': [11, 11, 11],
            'faces': {d: {'texture': '#pipe', 'uv': CORE_UV} for d in ('north', 'south', 'west', 'east', 'up', 'down')}}


def arm_element(z0=0, z1=5):
    """The north arm: 4 x 4 tube from the block face (z 0) to the core (z 5). No end faces: a flange, the next pipe's
    arm or the inventory covers the outer end, the core the inner one."""
    return {'from': [6, 6, z0], 'to': [10, 10, z1], 'faces': {
        'west': {'texture': '#pipe', 'uv': ARM_X_UV}, 'east': {'texture': '#pipe', 'uv': ARM_X_UV},
        'up': {'texture': '#pipe', 'uv': ARM_Y_UV}, 'down': {'texture': '#pipe', 'uv': ARM_Y_UV}}}


def flange_element():
    return {'from': [4, 4, 0], 'to': [12, 12, 2], 'faces': {
        'north': {'texture': '#flange', 'uv': FLANGE_UV, 'cullface': 'north'}, 'south': {'texture': '#flange', 'uv': FLANGE_UV},
        'west': {'texture': '#flange', 'uv': EDGE_Y_UV}, 'east': {'texture': '#flange', 'uv': EDGE_Y_UV},
        'up': {'texture': '#flange', 'uv': EDGE_X_UV}, 'down': {'texture': '#flange', 'uv': EDGE_X_UV}}}


def model(textures, elements):
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}


for kind in ('insert', 'extract'):
    write(ASSETS / f'models/block/item_pipe_{kind}.json',
          model({'flange': tex(f'item_pipe_{kind}'), 'particle': tex(f'item_pipe_{kind}')}, [flange_element()]))

for name, texture in PIPES.items():
    textures = {'pipe': tex(texture), 'particle': tex(texture)}
    write(ASSETS / f'models/block/{name}_core.json', model(textures, [core_element()]))
    write(ASSETS / f'models/block/{name}_arm.json', model(textures, [arm_element()]))
    parts = [{'apply': {'model': f'robotica:block/{name}_core'}}]
    for face, rot in ROT.items():
        parts.append({'when': {face: 'pipe|insert|extract'}, 'apply': dict({'model': f'robotica:block/{name}_arm'}, **rot)})
        for kind in ('insert', 'extract'):
            parts.append({'when': {face: kind}, 'apply': dict({'model': f'robotica:block/item_pipe_{kind}'}, **rot)})
    write(ASSETS / f'blockstates/{name}.json', {'multipart': parts})
    # The item: a straight piece, core with arms to the north and south, a bit larger in the inventory.
    south = arm_element(11, 16)
    item = model(textures, [core_element(), arm_element(), south])
    del item['render_type']
    item['display'] = {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
    }
    write(ASSETS / f'models/item/{name}.json', item)
    write(DATA / f'loot_table/blocks/{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': f'robotica:{name}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


def shaped(name, pattern, key, count):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: ({'tag': v[1:]} if v.startswith('#') else {'item': v}) for k, v in key.items()},
        'result': {'id': f'robotica:{name}', 'count': count},
    })


# First iron: copper plates around glass and redstone. Mk2: eight pipes around an Electric Motor (twice the rate,
# four times the items per pull).
shaped('item_pipe', ['PPP', 'GRG', 'PPP'], {'P': '#c:plates/copper', 'G': '#c:glass_blocks', 'R': '#c:dusts/redstone'}, 8)
shaped('item_pipe_mk2', ['PPP', 'PMP', 'PPP'], {'P': 'robotica:item_pipe', 'M': 'robotica:electric_motor'}, 8)
