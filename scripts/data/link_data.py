"""Link data: Quantum Link block models, blockstates, item models, loot tables and recipes.
Run: python3 scripts/data/link_data.py   (overwrites only files of link blocks and items)
Textures come from scripts/textures/link.py. The floating crystal is drawn by the block entity renderer (LinkRenderer).
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


def profile_faces(x0, x1, y0, y1, side='side', front='front', **extra):
    """The four sides of a box, reading rows 16 - y1 .. 16 - y0 of the profile textures; north is the front."""
    uv = [x0, 16 - y1, x1, 16 - y0]
    return {d: face(front if d == 'north' else side, uv, **extra) for d in SIDES}


def link_model(tier, crystal=False):
    """Pedestal: base slab, body with a light slit (a lens on the front), four corner pillars, cap and the emitter disc
    the crystal floats over. Light parts are full-bright overlay faces."""
    p = f'robotica:block/quantum_link_{tier}'
    textures = {'side': f'{p}_side', 'front': f'{p}_front', 'side_glow': f'{p}_side_glow', 'front_glow': f'{p}_front_glow',
                'top': f'{p}_top', 'emitter': f'{p}_emitter', 'trim': f'{p}_trim',
                'bottom': 'robotica:block/quantum_link_bottom', 'particle': f'{p}_side'}
    full = [0, 0, 16, 16]
    elements = [
        # base y 0-3
        box(0, 0, 0, 16, 3, 16, dict(profile_faces(0, 16, 0, 3),
                                     up=face('bottom', full), down=face('bottom', full, cullface='down'))),
        # body y 3-11
        box(2, 3, 2, 14, 11, 14, profile_faces(2, 14, 3, 11)),
        box(2, 3, 2, 14, 11, 14, profile_faces(2, 14, 3, 11, 'side_glow', 'front_glow', neoforge_data=GLOW), shade=False),
        # cap y 11-13
        box(1, 11, 1, 15, 13, 15, dict(profile_faces(1, 15, 11, 13), up=face('top', [1, 1, 15, 15]), down=face('bottom', [1, 1, 15, 15]))),
        # emitter y 13-14
        box(5, 13, 5, 11, 14, 11, profile_faces(5, 11, 13, 14)),
        box(5, 13, 5, 11, 14, 11, dict(profile_faces(5, 11, 13, 14, 'side_glow', 'side_glow', neoforge_data=GLOW),
                                       up=face('emitter', [5, 5, 11, 11], neoforge_data=GLOW)), shade=False),
    ]
    # corner pillars y 3-11
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        elements.append(box(x, 3, z, x + 2, 11, z + 2, {d: face('trim', [x, 5, x + 2, 13]) for d in SIDES}))
    if crystal:
        # the item shows a small crystal over the emitter (in the world the renderer draws it in the network colour)
        textures['crystal'] = 'robotica:block/quantum_crystal_item'
        gem = {d: face('crystal', [4, 0, 12, 16], neoforge_data=GLOW) for d in SIDES}
        gem['up'] = face('crystal', [4, 4, 12, 12], neoforge_data=GLOW)
        gem['down'] = face('crystal', [4, 4, 12, 12], neoforge_data=GLOW)
        elements.append(box(6.5, 14.5, 6.5, 9.5, 19.5, 9.5, gem, shade=False,
                            rotation={'origin': [8, 16, 8], 'axis': 'y', 'angle': 45}))
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
        write(ASSETS / f'models/item/{name}.json', link_model(tier, crystal=True))
        loot(name)

    # Age 2: ender pearls tie the pair, a diamond focuses the beam. Upgrades keep the previous Mk in the recipe.
    shaped('quantum_link_mk1', ['EAE', 'SXS', 'GDG'], {
        'E': '#c:ender_pearls', 'A': 'advanced_circuit', 'S': 'servo_actuator', 'X': 'reinforced_casing',
        'G': '#c:plates/gold', 'D': '#c:gems/diamond'})
    # Age 3: reaches other dimensions (Magma Core, as the Warp Pad Mk2).
    shaped('quantum_link_mk2', ['BMB', 'QLQ', 'PEP'], {
        'B': 'blazing_casing', 'M': 'magma_core', 'Q': 'quantum_circuit', 'L': 'quantum_link_mk1',
        'P': 'plasma_actuator', 'E': '#c:ender_pearls'}, source='L')
    # Age 4
    shaped('quantum_link_mk3', ['ECE', 'NLN', 'ECE'], {
        'E': '#c:ender_pearls', 'C': 'null_circuit', 'N': 'null_casing', 'L': 'quantum_link_mk2'}, source='L')
    print('link data written')


if __name__ == '__main__':
    main()
