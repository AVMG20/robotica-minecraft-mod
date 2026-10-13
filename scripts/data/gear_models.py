"""Writes the gear module's block models and blockstates: the Tinker's Bench (with its item model and loot table) and the
Spark Lamp. Run: python3 scripts/data/gear_models.py
The lamp model is empty (the wisp is drawn in code); the blockstate keeps one variant per facing."""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from pixelart import ASSETS, GLOW_FACE, ROOT  # noqa: E402

DATA = ROOT / 'src/main/resources/data/robotica'
SIDES = ('north', 'south', 'west', 'east')
HORIZONTAL = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def box(a, b, faces, cull=()):
    return {'from': a, 'to': b, 'faces': {d: dict(f, cullface=d) if d in cull else f for d, f in faces.items()}}


def vice_face(zone, size):
    """A face of the vice: the top-left `size` of one zone of tinkers_bench_vice."""
    return {'texture': '#vice', 'uv': [zone[0], zone[1], zone[0] + size[0], zone[1] + size[1]]}


# Zones of tinkers_bench_vice (scripts/textures/gear.py bench_vice)
JAW_FACE, JAW_SIDE, JAW_TOP, HANDLE, BASE_SIDE, BASE_TOP = (0, 0), (4, 0), (0, 4), (0, 8), (0, 10), (8, 0)


def tinkers_bench():
    """Workshop bench, front (drawer, vice) to the north. Textures: scripts/textures/gear.py. Worktop y 12..16 over the
    full block, apron y 8..12 and light steel legs inset 1 px, a graphite shelf y 2..4 between the legs, a steel vice
    on the front left corner of the top and the lit module slot as a full-bright overlay on the top. Side faces use the
    default UVs, so every part maps onto its piece of the tinkers_bench_side / _front picture."""
    side = {d: {'texture': '#front' if d == 'north' else '#side'} for d in SIDES}
    leg = {d: {'texture': '#side'} for d in SIDES}
    els = [
        box([0, 12, 0], [16, 16, 16], dict(side, up={'texture': '#top'}, down={'texture': '#bottom'}), SIDES + ('up',)),
        box([1, 8, 1], [15, 12, 15], dict(side, down={'texture': '#bottom'})),
        box([2, 2, 2], [14, 4, 14], dict(side, up={'texture': '#bottom'}, down={'texture': '#bottom'})),
    ]
    for x in (1, 12):
        for z in (1, 12):
            els.append(box([x, 0, z], [x + 3, 8, z + 3], dict(leg, down={'texture': '#bottom'}), ('down',)))
    els += [
        box([10, 16, 1], [14, 17, 6], {'north': vice_face(BASE_SIDE, (4, 1)), 'south': vice_face(BASE_SIDE, (4, 1)),
                                       'west': vice_face(BASE_SIDE, (5, 1)), 'east': vice_face(BASE_SIDE, (5, 1)),
                                       'up': vice_face(BASE_TOP, (4, 5))}),
        box([10, 17, 1], [14, 19, 2], {'north': vice_face(JAW_FACE, (4, 2)), 'south': vice_face(JAW_FACE, (4, 2)),
                                       'west': vice_face(JAW_SIDE, (1, 2)), 'east': vice_face(JAW_SIDE, (1, 2)),
                                       'up': vice_face(JAW_TOP, (4, 1))}),
        box([10, 17, 3], [14, 19, 6], {'north': vice_face(JAW_FACE, (4, 2)), 'south': vice_face(JAW_FACE, (4, 2)),
                                       'west': vice_face(JAW_SIDE, (3, 2)), 'east': vice_face(JAW_SIDE, (3, 2)),
                                       'up': vice_face(JAW_TOP, (4, 3))}),
        box([9, 17, 0], [15, 18, 1], {d: vice_face(HANDLE, (1 if d in ('west', 'east') else 6, 1))
                                      for d in ('north', 'south', 'up', 'down', 'west', 'east')}),
        {'from': [0, 16, 0], 'to': [16, 16, 16], 'shade': False,
         'faces': {'up': {'texture': '#glow', 'cullface': 'up', 'neoforge_data': dict(GLOW_FACE)}}},
    ]
    t = {k: f'robotica:block/tinkers_bench_{k}' for k in ('top', 'side', 'front', 'bottom', 'vice')}
    t['glow'] = 'robotica:block/tinkers_bench_top_glow'
    t['particle'] = t['side']
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': t, 'elements': els}


def tinkers_bench_state():
    return {'variants': {f'facing={f}': dict({'model': 'robotica:block/tinkers_bench'}, **({'y': r} if r else {}))
                         for f, r in HORIZONTAL.items()}}


def loot(name):
    return {'type': 'minecraft:block', 'pools': [{
        'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': f'robotica:{name}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}], 'random_sequence': f'robotica:blocks/{name}'}


def spark_lamp():
    """No geometry, only the particle texture: the wisp is drawn client side (gear.client.SparkWisps), so it can float,
    face the camera and glow additively. Textures: scripts/textures/gear.py (spark_wisp, spark_wisp_glow)."""
    return {'textures': {'particle': 'robotica:block/spark_lamp_core'}, 'elements': []}


def spark_lamp_state():
    return {'variants': {f'facing={f}': {'model': 'robotica:block/spark_lamp'} for f in ('up', 'down', 'north', 'east', 'south', 'west')}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    write(ASSETS / 'models/block/tinkers_bench.json', tinkers_bench())
    write(ASSETS / 'blockstates/tinkers_bench.json', tinkers_bench_state())
    write(ASSETS / 'models/item/tinkers_bench.json', {'parent': 'robotica:block/tinkers_bench'})
    write(DATA / 'loot_table/blocks/tinkers_bench.json', loot('tinkers_bench'))
    write(ASSETS / 'models/block/spark_lamp.json', spark_lamp())
    write(ASSETS / 'blockstates/spark_lamp.json', spark_lamp_state())
    print('gear models written')


if __name__ == '__main__':
    main()
