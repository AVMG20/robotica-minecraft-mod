"""Writes the gear module's block models and blockstates: the Spark Lamp.
Run: python3 scripts/data/gear_models.py
The lamp model stands on the floor pointing up; the blockstate turns it onto walls and the ceiling (like the vanilla
lightning rod). Textures: scripts/textures/gear.py (lamp_core, lamp_glow)."""
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
    """A small floating wisp: a 2x2x2 full-bright core inside three crossed translucent halo planes (8x8, two vertical
    planes at 45 degrees and one flat), all centred 5 px off the surface it hangs on. Both textures are animated."""
    core, glow = '#core', '#glow'
    elements = [box([7, 4, 7], [9, 6, 9], {f: (core, [7, 7, 9, 9]) for f in ('up', 'down', *SIDES)}, glow=True)]

    def plane(frm, to, faces, tilt=True):
        out = {'from': frm, 'to': to, 'shade': False,
               'faces': {f: {'texture': glow, 'uv': [4, 4, 12, 12], 'neoforge_data': dict(GLOW_FACE)} for f in faces}}
        if tilt:
            out['rotation'] = {'origin': [8, 5, 8], 'axis': 'y', 'angle': 45}
        return out

    elements += [plane([4, 1, 8], [12, 9, 8], ('north', 'south')),
                 plane([8, 1, 4], [8, 9, 12], ('west', 'east')),
                 plane([4, 5, 4], [12, 5, 12], ('up', 'down'), tilt=False)]
    return {'ambientocclusion': False, 'render_type': 'minecraft:translucent',
            'textures': {'particle': 'robotica:block/spark_lamp_core', 'core': 'robotica:block/spark_lamp_core',
                         'glow': 'robotica:block/spark_lamp_glow'},
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
