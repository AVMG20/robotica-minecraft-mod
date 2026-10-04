"""Warp data: block models, blockstates, item models, loot tables and recipes.
Run: python3 scripts/data/warp_data.py   (overwrites only files of warp blocks and items)
Textures come from scripts/textures/warp.py.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def face(tex, uv=None, **extra):
    f = {'texture': '#' + tex}
    if uv:
        f['uv'] = uv
    f.update(extra)
    return f


# ---- Warp Pad: one 16x16x8 slab; side texture rows 0-7 map straight onto the 8 pixel height ----

def pad_model(rift):
    prefix = 'warp_pad_rift' if rift else 'warp_pad'
    side_uv = [0, 0, 16, 8]
    elements = [{
        'from': [0, 0, 0], 'to': [16, 8, 16],
        'faces': {
            'up': face('top'),
            'down': face('base'),
            'north': face('side', side_uv), 'south': face('side', side_uv),
            'east': face('side', side_uv), 'west': face('side', side_uv),
        },
    }]
    textures = {'top': f'robotica:block/{prefix}_top', 'side': f'robotica:block/{prefix}_side',
                'base': 'robotica:block/warp_pad_base', 'particle': f'robotica:block/{prefix}_side'}
    if rift:
        textures['stud'] = 'robotica:block/warp_pad_rift_stud'
        for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
            elements.append({
                'from': [x, 8, z], 'to': [x + 2, 10, z + 2],
                'faces': {d: face('stud', [0, 0, 16, 16]) for d in ('up', 'north', 'south', 'east', 'west')},
            })
    return {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}


def box(x0, y0, z0, x1, y1, z1, faces, rotate=False, extra=None):
    el = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': faces}
    if rotate:
        el['rotation'] = {'origin': [8, y0, 8], 'axis': 'y', 'angle': 45}
    return el


def side_faces(y0, y1, tex='side', skip_up=False, up='top', down='top', extra_up=None):
    """Four side faces reading the matching rows of the profile texture (v = 16 - top .. 16 - bottom), plus up and down."""
    uv = [0, 16 - y1, 16, 16 - y0]
    faces = {d: face(tex, uv) for d in ('north', 'south', 'east', 'west')}
    if not skip_up:
        faces['up'] = face(up, [0, 0, 16, 16], **(extra_up or {}))
    faces['down'] = face(down, [0, 0, 16, 16])
    return faces


def projector_model(on):
    """Squat octagonal emitter (a square plus the same square turned 45 degrees, the turned one 0.02 px taller so its top
    wins), 12 px tall, with a lens on top and a prow on the front (north). Lit lens and prow are emissive faces."""
    lens_tex, fin_tex = ('lens_on', 'fin_on') if on else ('lens', 'fin')
    glow = {'neoforge_data': {'block_light': 15, 'sky_light': 15}} if on else {'neoforge_data': {'block_light': 5, 'sky_light': 5}}
    elements = []
    # base, neck, head (each: axis aligned box + 45 degree box)
    for (a, b, y0, y1) in ((2.5, 13.5, 0, 3), (4, 12, 3, 8), (3, 13, 8, 11)):
        elements.append(box(a, y0, a, b, y1, b, side_faces(y0, y1)))
        elements.append(box(a, y0, a, b, y1 + 0.02, b, side_faces(y0, y1), rotate=True))
    # lens: emissive
    lens_faces = {d: face(lens_tex, [0, 0, 16, 16], **glow) for d in ('north', 'south', 'east', 'west', 'up')}
    elements.append(box(5, 11, 5, 11, 12, 11, lens_faces))
    elements.append(box(5, 11, 5, 11, 12.02, 11, lens_faces, rotate=True))
    # prow on the front
    fin_faces = {d: face(fin_tex, [0, 0, 16, 16], **glow) for d in ('north', 'east', 'west', 'up')}
    elements.append(box(6.5, 3, 1.5, 9.5, 9, 3.5, fin_faces))
    textures = {'side': 'robotica:block/projector_side', 'top': 'robotica:block/projector_top',
                'lens': 'robotica:block/projector_lens', 'lens_on': 'robotica:block/projector_lens_on',
                'fin': 'robotica:block/projector_fin', 'fin_on': 'robotica:block/projector_fin_on',
                'particle': 'robotica:block/projector_side'}
    return {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}


def loot(name):
    write(DATA / f'loot_table/blocks/{name}.json', {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0,
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{'type': 'minecraft:item', 'name': f'robotica:{name}'}],
        }],
        'random_sequence': f'robotica:blocks/{name}',
    })


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1, category='misc'):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
        'key': {k: ing(v) for k, v in key.items()},
        'result': {'id': f'robotica:{name}', 'count': count}})


def smithing(name, template, base, addition, result):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:smithing_transform',
        'template': ing(template), 'base': ing(base), 'addition': ing(addition),
        'result': {'id': f'robotica:{result}', 'count': 1}})


def main():
    # Warp Pad
    write(ASSETS / 'models/block/warp_pad.json', pad_model(False))
    write(ASSETS / 'models/block/warp_pad_rift.json', pad_model(True))
    write(ASSETS / 'blockstates/warp_pad.json', {'variants': {
        'rift=false': {'model': 'robotica:block/warp_pad'},
        'rift=true': {'model': 'robotica:block/warp_pad_rift'},
    }})
    write(ASSETS / 'models/item/warp_pad.json', {'parent': 'robotica:block/warp_pad'})

    # Portal Projector (registry id gate_controller): facing x active. The portal itself is drawn by a block entity renderer.
    write(ASSETS / 'models/block/gate_controller.json', projector_model(False))
    write(ASSETS / 'models/block/gate_controller_on.json', projector_model(True))
    variants = {}
    for active in ('false', 'true'):
        model = 'robotica:block/gate_controller' + ('_on' if active == 'true' else '')
        for i, facing in enumerate(('north', 'east', 'south', 'west')):
            v = {'model': model}
            if i:
                v['y'] = 90 * i
            variants[f'active={active},facing={facing}'] = v
    write(ASSETS / 'blockstates/gate_controller.json', {'variants': variants})
    write(ASSETS / 'models/item/gate_controller.json', {'parent': 'robotica:block/gate_controller_on'})

    for name in ('warp_pad', 'gate_controller'):
        loot(name)

    # Recipes. Age 2 pad, Age 3 rift upgrade (with the Magma Core), Age 1 remote, Age 3 remote upgrade, Age 4 portal projector.
    shaped('warp_pad', ['EAE', 'GRG', 'PPP'], {
        'E': '#c:ender_pearls', 'A': 'advanced_circuit', 'G': '#c:plates/gold', 'R': 'reinforced_casing', 'P': '#c:plates/iron'})
    shaped('rift_upgrade', ['BQB', 'EME', ' P '], {
        'B': 'blazing_casing', 'Q': 'quantum_circuit', 'E': '#c:ender_pearls', 'M': 'magma_core', 'P': 'plasma_actuator'})
    shaped('recall_remote', ['PEP', 'ICI', 'PBP'], {
        'P': '#c:plates/iron', 'E': '#c:ender_pearls', 'I': 'iron_casing', 'C': 'copper_cell', 'B': 'basic_circuit'}, category='equipment')
    smithing('rift_remote', 'tool_upgrade_kit_3', 'recall_remote', 'quantum_circuit', 'rift_remote')
    # The projector absorbs what the 4x5 frame used to cost (about 17 frame blocks per gate: obsidian, iron plate, null casing):
    # three Null Casings instead of one, in total about the same raw cost as controller plus frame had.
    shaped('gate_controller', ['ONO', 'EAE', 'NCN'], {
        'O': '#c:obsidians', 'N': 'null_casing', 'E': '#c:ender_pearls', 'A': 'antigrav_core', 'C': 'null_circuit'})
    shaped('linking_card', [' N ', 'PEP'], {'N': 'null_circuit', 'E': '#c:ender_pearls', 'P': 'minecraft:paper'})
    print('warp data written')


if __name__ == '__main__':
    main()
