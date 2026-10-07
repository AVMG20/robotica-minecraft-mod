"""Writes the gear module's block models and blockstates: the Spark Lamp.
Run: python3 scripts/data/gear_models.py
The lamp model stands on the floor pointing up; the blockstate turns it onto walls and the ceiling (like the vanilla
lightning rod). Texture atlas and UVs: scripts/textures/gear.py (lamp_body, lamp_bulb)."""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from pixelart import ASSETS, GLOW_FACE  # noqa: E402

SIDES = ('north', 'south', 'west', 'east')


def box(frm, to, faces, glow=False):
    """Element with the given faces: name -> (texture var, uv)."""
    out = {'from': frm, 'to': to, 'faces': {}}
    if glow:
        out['shade'] = False
    for name, (tex, uv) in faces.items():
        face = {'texture': tex, 'uv': uv}
        if glow:
            face['neoforge_data'] = dict(GLOW_FACE)
        if name == 'down' and frm[1] == 0:
            face['cullface'] = 'down'
        out['faces'][name] = face
    return out


def sides(tex, uv):
    return {s: (tex, uv) for s in SIDES}


def spark_lamp():
    body, bulb = '#body', '#bulb'
    elements = [
        box([5, 0, 5], [11, 1, 11], {'up': (body, [0, 0, 6, 6]), 'down': (body, [0, 0, 6, 6]), **sides(body, [0, 6, 6, 7])}),
        box([6.5, 1, 6.5], [9.5, 2.5, 9.5], sides(body, [8, 0, 11, 2])),
        box([7, 2.5, 7], [9, 6.5, 9], sides(bulb, [5, 2, 11, 14]), glow=True),
    ]
    for x, z in ((6.5, 6.5), (9, 6.5), (6.5, 9), (9, 9)):
        elements.append(box([x, 2.5, z], [x + 0.5, 6.5, z + 0.5], sides(body, [12, 0, 13, 4])))
    elements += [
        box([6.5, 6.5, 6.5], [9.5, 7.5, 9.5], {'up': (body, [8, 2, 11, 5]), 'down': (body, [8, 2, 11, 5]), **sides(body, [8, 0, 11, 1])}),
        box([7.5, 7.5, 7.5], [8.5, 8.25, 8.5], {'up': (body, [9, 3, 10, 4]), **sides(body, [9, 0, 10, 1])}),
    ]
    return {'ambientocclusion': False, 'render_type': 'minecraft:cutout',
            'textures': {'particle': 'robotica:block/spark_lamp', 'body': 'robotica:block/spark_lamp',
                         'bulb': 'robotica:block/spark_lamp_bulb'},
            'elements': elements}


def spark_lamp_state():
    rot = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'east': {'x': 90, 'y': 90}, 'south': {'x': 90, 'y': 180},
           'west': {'x': 90, 'y': 270}}
    return {'variants': {f'facing={f}': {'model': 'robotica:block/spark_lamp', **r} for f, r in rot.items()}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    write(ASSETS / 'models/block/spark_lamp.json', spark_lamp())
    write(ASSETS / 'blockstates/spark_lamp.json', spark_lamp_state())
    print('gear models written')


if __name__ == '__main__':
    main()
