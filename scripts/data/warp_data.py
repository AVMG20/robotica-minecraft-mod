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


def portal_model():
    """A thin plane in the XY plane, rendered translucent; the blockstate rotates it for the Z axis."""
    return {
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:translucent',
        'textures': {'portal': 'robotica:block/gate_portal', 'particle': 'robotica:block/gate_portal'},
        'elements': [{
            'from': [0, 0, 7.5], 'to': [16, 16, 8.5],
            'faces': {'north': face('portal', [0, 0, 16, 16]), 'south': face('portal', [0, 0, 16, 16])},
        }],
    }


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

    # Gate frame
    write(ASSETS / 'models/block/gate_frame.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'robotica:block/gate_frame'}})
    write(ASSETS / 'blockstates/gate_frame.json', {'variants': {'': {'model': 'robotica:block/gate_frame'}}})
    write(ASSETS / 'models/item/gate_frame.json', {'parent': 'robotica:block/gate_frame'})

    # Gate controller (facing x active)
    for suffix, front in (('', 'gate_controller_front'), ('_on', 'gate_controller_front_on')):
        write(ASSETS / f'models/block/gate_controller{suffix}.json', {
            'parent': 'minecraft:block/orientable',
            'textures': {'top': 'robotica:block/gate_controller_top', 'side': 'robotica:block/gate_controller_side',
                         'front': f'robotica:block/{front}', 'particle': 'robotica:block/gate_controller_side'}})
    variants = {}
    for active in ('false', 'true'):
        model = 'robotica:block/gate_controller' + ('_on' if active == 'true' else '')
        for i, facing in enumerate(('north', 'east', 'south', 'west')):
            v = {'model': model}
            if i:
                v['y'] = 90 * i
            variants[f'active={active},facing={facing}'] = v
    write(ASSETS / 'blockstates/gate_controller.json', {'variants': variants})
    write(ASSETS / 'models/item/gate_controller.json', {'parent': 'robotica:block/gate_controller'})

    # Gate portal (no item)
    write(ASSETS / 'models/block/gate_portal_x.json', portal_model())
    write(ASSETS / 'blockstates/gate_portal.json', {'variants': {
        'axis=x': {'model': 'robotica:block/gate_portal_x'},
        'axis=z': {'model': 'robotica:block/gate_portal_x', 'y': 90},
    }})

    for name in ('warp_pad', 'gate_frame', 'gate_controller'):
        loot(name)

    # Recipes. Age 2 pad, Age 3 rift upgrade (with the Magma Core), Age 1 remote, Age 3 remote upgrade, Age 4 gate.
    shaped('warp_pad', ['EAE', 'GRG', 'PPP'], {
        'E': '#c:ender_pearls', 'A': 'advanced_circuit', 'G': '#c:plates/gold', 'R': 'reinforced_casing', 'P': '#c:plates/iron'})
    shaped('rift_upgrade', ['BQB', 'EME', ' P '], {
        'B': 'blazing_casing', 'Q': 'quantum_circuit', 'E': '#c:ender_pearls', 'M': 'magma_core', 'P': 'plasma_actuator'})
    shaped('recall_remote', ['PEP', 'ICI', 'PBP'], {
        'P': '#c:plates/iron', 'E': '#c:ender_pearls', 'I': 'iron_casing', 'C': 'copper_cell', 'B': 'basic_circuit'}, category='equipment')
    smithing('rift_remote', 'tool_upgrade_kit_3', 'recall_remote', 'quantum_circuit', 'rift_remote')
    shaped('gate_frame', ['OPO', 'PNP', 'OPO'], {
        'O': '#c:obsidians', 'P': '#c:plates/iron', 'N': 'null_casing'}, count=4)
    shaped('gate_controller', ['FCF', 'NAN', 'FEF'], {
        'F': 'gate_frame', 'C': 'null_circuit', 'N': 'null_casing', 'A': 'antigrav_core', 'E': '#c:ender_pearls'})
    shaped('linking_card', ['NEN', 'PPP'], {'N': 'null_circuit', 'E': '#c:ender_pearls', 'P': 'minecraft:paper'})
    print('warp data written')


if __name__ == '__main__':
    main()
