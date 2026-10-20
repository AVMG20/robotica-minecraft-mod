"""Link data: Quantum Link block models, blockstates, item models, loot tables and recipes.
Run: python3 scripts/data/link_data.py   (overwrites only files of link blocks and items)
Textures come from scripts/textures/link.py. The network colour (the core in the chamber, the top socket) is drawn by
the block entity renderer (LinkRenderer).
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'

TIERS = ('mk1', 'mk2', 'mk3')
GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
SIDES = ('north', 'south', 'east', 'west')
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def face(tex, uv, **extra):
    f = {'texture': '#' + tex, 'uv': uv}
    f.update(extra)
    return f


def box(x0, y0, z0, x1, y1, z1, faces, **extra):
    el = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': faces}
    el.update(extra)
    return el


def shell_faces(y0, y1, glow=False, cull=False, **extra):
    """The four sides of a full-width band (base or cap) from the shell profile textures; north is the front."""
    side, front = ('shell_glow', 'shell_front_glow') if glow else ('shell', 'shell_front')
    uv = [0, 16 - y1, 16, 16 - y0]
    faces = {}
    for d in SIDES:
        f = face(front if d == 'north' else side, uv, **extra)
        if cull:
            f['cullface'] = d
        faces[d] = f
    return faces


# corner pillars, 3 px square, y 3-13; the faces towards the middle of x and z edge the windows and carry light rails
PILLARS = ((0, 0), (13, 0), (0, 13), (13, 13))


def link_model(tier, item=False):
    """Open containment chamber with a flat top (a Tesla Relay sits on it): base, flat cap, four corner pillars with
    light rails, emitter pads on the chamber floor and ceiling. The renderer draws the core floating in the chamber and
    the socket in the top; the item shows a crystal in their place. Light parts are full-bright overlay faces."""
    p = f'robotica:block/quantum_link_{tier}'
    textures = {k: f'{p}_{k}' for k in ('shell', 'shell_front', 'shell_glow', 'shell_front_glow', 'pillar', 'pillar_glow',
                                         'top', 'top_glow', 'pad', 'pad_glow')}
    textures.update({'bottom': 'robotica:block/quantum_link_bottom', 'particle': f'{p}_shell'})
    full = [0, 0, 16, 16]
    lit = {'neoforge_data': GLOW}
    elements = [
        # base y 0-3, its top is the chamber floor
        box(0, 0, 0, 16, 3, 16, dict(shell_faces(0, 3), up=face('pad', full), down=face('bottom', full, cullface='down'))),
        box(0, 0, 0, 16, 3, 16, dict(shell_faces(0, 3, glow=True, **lit), up=face('pad_glow', full, **lit)), shade=False),
        # cap y 13-16, flat top, its underside is the chamber ceiling
        box(0, 13, 0, 16, 16, 16, dict(shell_faces(13, 16, cull=True), up=face('top', full, cullface='up'),
                                       down=face('pad', full))),
        box(0, 13, 0, 16, 16, 16, dict(shell_faces(13, 16, glow=True, **lit), up=face('top_glow', full, cullface='up', **lit),
                                       down=face('pad_glow', full, **lit)), shade=False),
    ]
    for x, z in PILLARS:
        uv = [0, 3, 3, 13]
        elements.append(box(x, 3, z, x + 3, 13, z + 3, {d: face('pillar', uv) for d in SIDES}))
        inner = ('east' if x == 0 else 'west', 'south' if z == 0 else 'north')
        elements.append(box(x, 3, z, x + 3, 13, z + 3, {d: face('pillar_glow', uv, **lit) for d in inner}, shade=False))
    if item:
        textures['core'] = 'robotica:block/quantum_link_item_core'
        textures['crystal'] = 'robotica:block/quantum_link_item_crystal'
        elements.append(box(0, 13, 0, 16, 16, 16, {'up': face('core', full, **lit)}, shade=False))
        gem = {d: face('crystal', [4, 0, 12, 16], **lit) for d in SIDES}
        gem['up'] = face('crystal', [4, 4, 12, 12], **lit)
        gem['down'] = face('crystal', [4, 4, 12, 12], **lit)
        elements.append(box(6.5, 5.5, 6.5, 9.5, 10.5, 9.5, gem, shade=False, rotation={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}))
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}


def loot(name):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}', 'functions': [
        {'function': 'minecraft:copy_components', 'source': 'block_entity',
         'include': ['robotica:link_settings', 'robotica:energy']}]}
    write(DATA / f'loot_table/blocks/{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1, category='misc', source=None):
    """source: key of the previous Mk; the result then keeps its data (robotica:machine_upgrade)."""
    data = {'type': 'robotica:machine_upgrade' if source else 'minecraft:crafting_shaped', 'category': category,
            'pattern': pattern, 'key': {k: ing(v) for k, v in key.items()},
            'result': {'id': f'robotica:{name}', 'count': count}}
    if source:
        data['source'] = ing(key[source])
    write(DATA / f'recipe/{name}.json', data)


def main():
    for tier in TIERS:
        name = f'quantum_link_{tier}'
        write(ASSETS / f'models/block/{name}.json', link_model(tier))
        variants = {}
        for facing, y in FACING_Y.items():
            v = {'model': f'robotica:block/{name}'}
            if y:
                v['y'] = y
            variants[f'facing={facing}'] = v
        write(ASSETS / f'blockstates/{name}.json', {'variants': variants})
        write(ASSETS / f'models/item/{name}.json', link_model(tier, item=True))
        loot(name)

    # Age 2: ender pearls tie the pair, a diamond focuses the beam. Upgrades keep the previous Mk in the recipe.
    shaped('quantum_link_mk1', ['EAE', 'SXS', 'GDG'], {
        'E': '#c:ender_pearls', 'A': 'advanced_circuit', 'S': 'servo_actuator', 'X': 'reinforced_casing',
        'G': '#c:plates/gold', 'D': '#c:gems/diamond'})
    # Age 3: Magma Core, as the Warp Pad Mk2.
    shaped('quantum_link_mk2', ['BMB', 'QLQ', 'PEP'], {
        'B': 'blazing_casing', 'M': 'magma_core', 'Q': 'quantum_circuit', 'L': 'quantum_link_mk1',
        'P': 'plasma_actuator', 'E': '#c:ender_pearls'}, source='L')
    # Age 4
    shaped('quantum_link_mk3', ['ECE', 'NLN', 'ECE'], {
        'E': '#c:ender_pearls', 'C': 'null_circuit', 'N': 'null_casing', 'L': 'quantum_link_mk2'}, source='L')
    print('link data written')


if __name__ == '__main__':
    main()
