"""Writes blockstates, block/item models and loot tables of the power module.
Run: python3 scripts/data/power_models.py   (overwrites files named after power blocks only)"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def block_model(name, obj):
    write(ASSETS / 'models/block' / f'{name}.json', obj)


def item_model(name, parent):
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': parent})


def blockstate(name, obj):
    write(ASSETS / 'blockstates' / f'{name}.json', obj)


def loot(name, copy_energy=False):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}'}
    if copy_energy:
        entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['robotica:energy']}]
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def facing_variants(model_for, extra=()):
    """model_for(extra_values dict) -> model name; yields every facing x extra combination."""
    variants = {}
    combos = [{}]
    for prop, values in extra:
        combos = [dict(c, **{prop: v}) for c in combos for v in values]
    for combo in combos:
        for facing, y in FACING_Y.items():
            key = ','.join([f'facing={facing}'] + [f'{k}={str(v).lower()}' for k, v in combo.items()])
            entry = {'model': f'robotica:block/{model_for(combo)}'}
            if y:
                entry['y'] = y
            variants[key] = entry
    return variants


# ---------- orientable machines (generator, press, charger) ----------
for name in ('combustion_generator', 'metal_press', 'charger'):
    for suffix in ('', '_on'):
        block_model(f'{name}{suffix}', {
            'parent': 'minecraft:block/orientable',
            'textures': {'top': tex('power_machine_top'), 'side': tex('power_machine_side'),
                         'front': tex(f'{name}_front{suffix}'), 'particle': tex('power_machine_side')},
        })
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n + ('_on' if c['lit'] else ''), [('lit', [False, True])])})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- accumulators ----------
for tier in (1, 2, 3):
    name = f'accumulator_{tier}'
    block_model(name, {
        'parent': 'minecraft:block/orientable',
        'textures': {'top': tex(f'{name}_top'), 'side': tex(f'{name}_side'), 'front': tex(f'{name}_front'),
                     'particle': tex(f'{name}_side')},
    })
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n)})
    item_model(name, f'robotica:block/{name}')
    loot(name, copy_energy=True)

# ---------- solar panels ----------
for mk in (1, 2):
    name = f'solar_panel_mk{mk}'
    block_model(name, {
        'parent': 'minecraft:block/block',
        'textures': {'top': tex(f'{name}_top'), 'side': tex(f'{name}_side'), 'particle': tex(f'{name}_side')},
        'elements': [{
            'from': [0, 0, 0], 'to': [16, 6, 16],
            'faces': {
                'down': {'texture': '#side', 'cullface': 'down'},
                'up': {'texture': '#top'},
                'north': {'texture': '#side', 'uv': [0, 0, 16, 6], 'cullface': 'north'},
                'south': {'texture': '#side', 'uv': [0, 0, 16, 6], 'cullface': 'south'},
                'west': {'texture': '#side', 'uv': [0, 0, 16, 6], 'cullface': 'west'},
                'east': {'texture': '#side', 'uv': [0, 0, 16, 6], 'cullface': 'east'},
            },
        }],
    })
    blockstate(name, {'variants': {'': {'model': f'robotica:block/{name}'}}})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- conduits ----------
for name in ('copper_conduit', 'gold_conduit'):
    t = {'conduit': tex(name), 'particle': tex(name)}
    block_model(f'{name}_core', {'parent': 'minecraft:block/block', 'textures': t, 'elements': [{
        'from': [6, 6, 6], 'to': [10, 10, 10],
        'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}}]})
    block_model(f'{name}_arm', {'parent': 'minecraft:block/block', 'textures': t, 'elements': [{
        'from': [6, 6, 0], 'to': [10, 10, 6],
        'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'west', 'east')}}]})
    block_model(f'{name}_port', {'parent': 'minecraft:block/block', 'textures': t, 'elements': [{
        'from': [5, 5, 0], 'to': [11, 11, 2],
        'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}}]})
    # arm models point north; rotate for the other faces
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    multipart = [{'apply': {'model': f'robotica:block/{name}_core'}}]
    for face, r in rot.items():
        multipart.append({'when': {face: 'conduit|block'}, 'apply': dict({'model': f'robotica:block/{name}_arm'}, **r)})
        multipart.append({'when': {face: 'block'}, 'apply': dict({'model': f'robotica:block/{name}_port'}, **r)})
    blockstate(name, {'multipart': multipart})
    # inventory model: a straight piece with a port on each end
    write(ASSETS / 'models/item' / f'{name}.json', {
        'parent': 'minecraft:block/block', 'textures': t,
        'elements': [
            {'from': [6, 6, 6], 'to': [10, 10, 10], 'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}},
            {'from': [0, 6, 6], 'to': [6, 10, 10], 'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'west')}},
            {'from': [10, 6, 6], 'to': [16, 10, 10], 'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'east')}},
            {'from': [0, 5, 5], 'to': [2, 11, 11], 'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}},
            {'from': [14, 5, 5], 'to': [16, 11, 11], 'faces': {d: {'texture': '#conduit'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}},
        ],
    })
    loot(name)

# ---------- winding crank ----------
crank_tex = {'base': tex('winding_crank_base'), 'metal': tex('winding_crank_metal'), 'spring': tex('winding_crank_spring'),
             'particle': tex('winding_crank_base')}


def all_faces(texture):
    return {d: {'texture': texture} for d in ('down', 'up', 'north', 'south', 'west', 'east')}


crank_elements = [
    {'from': [2, 0, 2], 'to': [14, 4, 14], 'faces': all_faces('#base')},            # base plate
    {'from': [7, 4, 7], 'to': [9, 12, 9], 'faces': all_faces('#metal')},             # shaft
    {'from': [7, 11, 2], 'to': [9, 13, 9], 'faces': all_faces('#metal')},            # arm (points north)
    {'from': [7, 7, 2], 'to': [9, 11, 4], 'faces': all_faces('#base')},              # grip
]
block_model('winding_crank', {'parent': 'minecraft:block/block', 'textures': crank_tex, 'elements': crank_elements})
block_model('winding_crank_loaded', {'parent': 'minecraft:block/block', 'textures': crank_tex, 'elements': [crank_elements[0], {
    'from': [3, 4, 3], 'to': [13, 8, 13],
    'faces': dict(all_faces('#metal'), up={'texture': '#spring'}),
}] + crank_elements[1:]})
variants = {}
for spring in (False, True):
    for rotation in range(4):
        entry = {'model': 'robotica:block/winding_crank' + ('_loaded' if spring else '')}
        if rotation:
            entry['y'] = 90 * rotation
        variants[f'rotation={rotation},spring={str(spring).lower()}'] = entry
blockstate('winding_crank', {'variants': variants})
item_model('winding_crank', 'robotica:block/winding_crank')
loot('winding_crank')
