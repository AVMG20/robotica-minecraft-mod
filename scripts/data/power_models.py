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
# One cube with per-machine top, shared sides and bottom, plus a coplanar full-bright overlay for the glowing parts
# when lit (fire, die seam, coil). Front faces north; the blockstate turns it.
GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')


def machine_model(textures, glow=None):
    """textures: face -> texture name; glow: face -> overlay texture name (drawn full bright)."""
    tex_vars = {f: tex(t) for f, t in textures.items()}
    tex_vars['particle'] = tex(textures['south'])
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16],
                 'faces': {d: {'texture': '#' + d, 'cullface': d} for d in DIRS}}]
    if glow:
        for f, t in glow.items():
            tex_vars['glow_' + f] = tex(t)
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {d: {'texture': '#glow_' + d, 'cullface': d, 'neoforge_data': GLOW} for d in glow}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': tex_vars, 'elements': elements}


MACHINES = {
    # name: (top off, top on, glow on top when lit)
    'combustion_generator': ('combustion_generator_top', 'combustion_generator_top_on', None),
    'metal_press': ('metal_press_top', 'metal_press_top', None),
    'charger': ('charger_top', 'charger_top_on', 'charger_top_glow'),
}
for name, (top, top_on, top_glow) in MACHINES.items():
    for lit in (False, True):
        faces = {'north': f'{name}_front' + ('_on' if lit else ''), 'up': top_on if lit else top,
                 'down': 'power_machine_bottom', 'south': 'power_machine_side', 'west': 'power_machine_side',
                 'east': 'power_machine_side'}
        glow = None
        if lit:
            glow = {'north': f'{name}_front_glow'}
            if top_glow:
                glow['up'] = top_glow
        block_model(name + ('_on' if lit else ''), machine_model(faces, glow))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n + ('_on' if c['lit'] else ''), [('lit', [False, True])])})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- accumulators: the charge gauge glows always ----------
for tier in (1, 2, 3):
    name = f'accumulator_{tier}'
    faces = {'north': f'{name}_front', 'up': f'{name}_top', 'down': 'power_machine_bottom',
             'south': f'{name}_side', 'west': f'{name}_side', 'east': f'{name}_side'}
    glow = {'north': 'accumulator_front_glow', 'south': 'accumulator_side_glow', 'west': 'accumulator_side_glow',
            'east': 'accumulator_side_glow'}
    block_model(name, machine_model(faces, glow))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n)})
    item_model(name, f'robotica:block/{name}')
    loot(name, copy_energy=True)

# ---------- solar panels: a 6 px slab, cells catch a faint glow ----------
for mk in (1, 2):
    name = f'solar_panel_mk{mk}'
    side_uv = [0, 0, 16, 6]
    block_model(name, {
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:cutout',
        'textures': {'top': tex(f'{name}_top'), 'side': tex(f'{name}_side'), 'bottom': tex('power_machine_bottom'),
                     'glow': tex(f'{name}_glow'), 'particle': tex(f'{name}_side')},
        'elements': [{
            'from': [0, 0, 0], 'to': [16, 6, 16],
            'faces': {
                'down': {'texture': '#bottom', 'cullface': 'down'},
                'up': {'texture': '#top'},
                'north': {'texture': '#side', 'uv': side_uv, 'cullface': 'north'},
                'south': {'texture': '#side', 'uv': side_uv, 'cullface': 'south'},
                'west': {'texture': '#side', 'uv': side_uv, 'cullface': 'west'},
                'east': {'texture': '#side', 'uv': side_uv, 'cullface': 'east'},
            },
        }, {
            'from': [0, 0, 0], 'to': [16, 6, 16], 'shade': False,
            'faces': {'up': {'texture': '#glow', 'neoforge_data': {'block_light': 9, 'sky_light': 9, 'ambient_occlusion': False}}},
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
