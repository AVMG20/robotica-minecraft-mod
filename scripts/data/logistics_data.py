"""Logistics module data: item pipe models (node, arm and connector, all pointing north), the multipart blockstates,
item models, loot tables and recipes.
Run: python3 scripts/data/logistics_data.py   (overwrites only files named after logistics blocks)

A pipe is a 4 x 4 tube: each linked face draws an arm from the block face to the centre, so a straight run is one
smooth tube; the junction node is only drawn where the tube bends, splits or ends. Insert and Extract faces add a
connector (a plate on the block and a collar with chevrons). Keep the sizes in step with ItemPipeBlock (shapes) and
ItemPipeGlow (the light on the channel). Texture atlas layout: see scripts/textures/logistics.py.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'

PIPES = ('item_pipe', 'item_pipe_mk2', 'item_pipe_mk3', 'item_pipe_mk4')
FACES = ('north', 'south', 'west', 'east', 'up', 'down')
AXIS = {'north': 'z', 'south': 'z', 'west': 'x', 'east': 'x', 'up': 'y', 'down': 'y'}
ARM, NONE = 'pipe|insert|extract', 'none'
# Turns a north-pointing part to each face (x 270 turns north to up, x 90 to down).
ROT = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}

NODE_UV = [0, 8, 6, 14]
# arm sides, u or v 0 at the block face (the clamp); flipped where the face's own axis runs the other way
ARM_UV = {'west': [0, 0, 8, 4], 'east': [8, 0, 0, 4], 'up': [8, 0, 12, 8], 'down': [8, 8, 12, 0]}
ARM_UV_SOUTH = {'west': [8, 0, 0, 4], 'east': [0, 0, 8, 4], 'up': [8, 8, 12, 0], 'down': [8, 0, 12, 8]}
PLATE_UV = [0, 0, 10, 10]
PLATE_EDGE_X, PLATE_EDGE_Y = [0, 15, 10, 16], [10, 0, 11, 10]
COLLAR_UV = {'west': [12, 0, 15, 6], 'east': [15, 0, 12, 6], 'up': [0, 11, 6, 14], 'down': [0, 14, 6, 11]}
COLLAR_END_UV = [10, 10, 16, 16]


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def node_element():
    return {'from': [5, 5, 5], 'to': [11, 11, 11],
            'faces': {d: {'texture': '#pipe', 'uv': NODE_UV} for d in FACES}}


def arm_element(south=False):
    """The north arm: the 4 x 4 tube from the block face (z 0) to the centre. No end faces: the next pipe's arm, a
    connector or the node covers them. south=True: the same from the centre to z 16 (item model)."""
    uv = ARM_UV_SOUTH if south else ARM_UV
    return {'from': [6, 6, 8 if south else 0], 'to': [10, 10, 16 if south else 8],
            'faces': {d: {'texture': '#pipe', 'uv': uv[d]} for d in ('west', 'east', 'up', 'down')}}


def connector_elements():
    plate = {'from': [3, 3, 0], 'to': [13, 13, 1], 'faces': {
        'north': {'texture': '#connector', 'uv': PLATE_UV, 'cullface': 'north'}, 'south': {'texture': '#connector', 'uv': PLATE_UV},
        'west': {'texture': '#connector', 'uv': PLATE_EDGE_Y}, 'east': {'texture': '#connector', 'uv': PLATE_EDGE_Y},
        'up': {'texture': '#connector', 'uv': PLATE_EDGE_X}, 'down': {'texture': '#connector', 'uv': PLATE_EDGE_X}}}
    collar = {'from': [5, 5, 1], 'to': [11, 11, 4], 'faces': dict(
        {d: {'texture': '#connector', 'uv': COLLAR_UV[d]} for d in ('west', 'east', 'up', 'down')},
        south={'texture': '#connector', 'uv': COLLAR_END_UV})}
    return [plate, collar]


def model(textures, elements):
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}


def node_condition():
    """The node shows unless the pipe is a straight run (arms on exactly two opposite faces): two arms on different
    axes, one arm alone, or no arm at all."""
    terms = []
    for i, a in enumerate(FACES):
        for b in FACES[i + 1:]:
            if AXIS[a] != AXIS[b]:
                terms.append({a: ARM, b: ARM})
    for a in FACES:
        terms.append({f: ARM if f == a else NONE for f in FACES})
    terms.append({f: NONE for f in FACES})
    return {'OR': terms}


for kind in ('insert', 'extract'):
    write(ASSETS / f'models/block/item_pipe_{kind}.json',
          model({'connector': tex(f'item_pipe_{kind}'), 'particle': tex(f'item_pipe_{kind}')}, connector_elements()))

for name in PIPES:
    textures = {'pipe': tex(name), 'particle': tex(name)}
    write(ASSETS / f'models/block/{name}_node.json', model(textures, [node_element()]))
    write(ASSETS / f'models/block/{name}_arm.json', model(textures, [arm_element()]))
    parts = [{'when': node_condition(), 'apply': {'model': f'robotica:block/{name}_node'}}]
    for face, rot in ROT.items():
        parts.append({'when': {face: ARM}, 'apply': dict({'model': f'robotica:block/{name}_arm'}, **rot)})
        for kind in ('insert', 'extract'):
            parts.append({'when': {face: kind}, 'apply': dict({'model': f'robotica:block/item_pipe_{kind}'}, **rot)})
    write(ASSETS / f'blockstates/{name}.json', {'multipart': parts})
    # The item: a straight piece with a node in the middle, a bit larger in the inventory.
    item = model(textures, [node_element(), arm_element(), arm_element(True)])
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
# Mk3 (Age 2) and Mk4 (Age 3): eight pipes of the tier below around that age's actuator.
shaped('item_pipe_mk3', ['PPP', 'PAP', 'PPP'], {'P': 'robotica:item_pipe_mk2', 'A': 'robotica:servo_actuator'}, 8)
shaped('item_pipe_mk4', ['PPP', 'PAP', 'PPP'], {'P': 'robotica:item_pipe_mk3', 'A': 'robotica:plasma_actuator'}, 8)
