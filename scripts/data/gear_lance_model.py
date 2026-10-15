"""Writes the Null Lance item models: a 3D void rifle in the hands and on the head, the flat icon in GUIs, on the
ground and in item frames (neoforge:separate_transforms). Run: python3 scripts/data/gear_lance_model.py
Textures: scripts/textures/gear.py (lance, write_lance_parts).

Model space: the barrel runs along -z (muzzle at z -14, butt pad at z 26.5), barrel axis at x 8, y 8, top up.
Charge stages: the item property robotica:charge (gear.client.LanceModel) is 0.25/0.5/0.75 for 1/2/3 of the 4 barrel
coils lit (rear to front) and 1.0 at full charge: all coils, the void cell, the side windows and the emitter run hot.
null_lance_held is the idle 3D model (it holds the textures and display transforms), null_lance_held_<n> the staged
ones; null_lance and null_lance_charge_<n> wrap them with the flat icon for the GUI, ground and fixed views."""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from pixelart import ASSETS, GLOW_FACE  # noqa: E402

MODELS = ASSETS / 'models/item'
DIRS = ('north', 'south', 'west', 'east', 'up', 'down')
COILS = (4.5, 1.0, -2.5, -6.0)          # rear face z of each coil ring is this + 1.5, rear to front
STAGES = (0.25, 0.5, 0.75, 1.0)


def face_size(d, a, b):
    dx, dy, dz = (b[i] - a[i] for i in range(3))
    return {'north': (dx, dy), 'south': (dx, dy), 'west': (dz, dy), 'east': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[d]


def part(a, b, tex, glow=False, at=(0, 0), only=DIRS, rot=None):
    """A box with every listed face mapped onto a rect of its own size at `at` in the texture (kept inside 16x16)."""
    faces = {}
    for d in only:
        w, h = (min(16.0, s) for s in face_size(d, a, b))
        u, v = min(at[0], 16 - w), min(at[1], 16 - h)
        f = {'texture': '#' + tex, 'uv': [round(u, 3), round(v, 3), round(u + w, 3), round(v + h, 3)]}
        if glow:
            f['neoforge_data'] = dict(GLOW_FACE)
        faces[d] = f
    el = {'from': a, 'to': b, 'faces': faces}
    if glow:
        el['shade'] = False
    if rot:
        el['rotation'] = rot
    return el


def elements(lit=0, hot=False):
    """`lit` coils glow (rear first); `hot` is full charge."""
    core = 'core_hot' if hot else 'core'
    els = [
        # stock and butt pad
        part([6.5, 3.5, 25], [9.5, 9.75, 26.5], 'metal', at=(0, 4)),
        part([7, 5, 18], [9, 9.5, 25], 'body', at=(0, 7)),
        part([6.75, 8.75, 19], [9.25, 10, 23.5], 'body', at=(8, 0)),
        # receiver, its side windows onto the void core, the raked grip and the trigger
        part([6, 6, 8], [10, 11, 18], 'body', at=(0, 1)),
        part([5.9, 7.5, 10.5], [10.1, 9, 15.5], core, glow=True, only=('west', 'east')),
        part([5.95, 9.75, 8], [10.05, 10.25, 18], 'trim', only=('west', 'east')),
        part([6.95, 7.75, 18], [9.05, 8.25, 25], 'trim', only=('west', 'east')),
        part([7, 0.5, 14.5], [9, 6, 17], 'metal', at=(4, 3), rot={'angle': -22.5, 'axis': 'x', 'origin': [8, 6, 16]}),
        part([7.5, 4, 10.5], [8.5, 4.75, 14.5], 'metal', at=(8, 8)),
        part([7.6, 4.75, 12.25], [8.4, 6, 13], 'metal', at=(12, 0)),
        # void cell on top: a crystal on its edge between two clamps
        part([6.5, 11, 9.5], [9.5, 11.75, 16.5], 'metal', at=(0, 9)),
        part([6.5, 11, 9], [9.5, 13, 10], 'metal', at=(9, 1)),
        part([6.5, 11, 16], [9.5, 13, 17], 'metal', at=(9, 1)),
        part([7, 11.75, 10], [9, 13.75, 16], core, glow=True, at=(3, 3), rot={'angle': 45, 'axis': 'z', 'origin': [8, 12.75, 13]}),
        # barrel with its top spine, muzzle, fork prongs and the emitter between them
        part([7, 7, -9], [9, 9, 8], 'metal', at=(0, 0)),
        part([7.5, 9, -7], [8.5, 9.5, 8], 'body', at=(0, 0)),
        part([6.5, 6.5, -11], [9.5, 9.5, -9], 'metal', at=(4, 9)),
        part([7.5, 9.5, -14], [8.5, 10.25, -9.5], 'metal', at=(2, 12)),
        part([7.5, 5.75, -14], [8.5, 6.5, -9.5], 'metal', at=(2, 12)),
        part([7.5, 7.5, -12], [8.5, 8.5, -11], core if hot else 'metal', glow=hot, at=(6, 6), only=('north', 'west', 'east', 'up', 'down')),
    ]
    for i, z in enumerate(COILS):
        on = i < lit
        els.append(part([6, 6, z], [10, 10, z + 1.5], 'coil_lit' if on else 'coil', glow=on, at=(2 * i, 4 * i)))
    return els


# Rifle hold. First person sits on the plain hand translation of RivetGunClient.heldPose (side * 0.56, -0.52, -0.72):
# the barrel runs straight down the view to the lower right of the crosshair. Third person: the grip in the hand, the
# barrel forward. Left-hand views mirror these (ItemTransform.apply).
HAND_1P = {'rotation': [0, 10, 0], 'translation': [-4, 3.4, -6.6], 'scale': [0.42, 0.42, 0.42]}
HAND_3P = {'rotation': [90, 0, 0], 'translation': [0, 4.5, 3], 'scale': [0.6, 0.6, 0.6]}
DISPLAY = {
    'firstperson_righthand': HAND_1P, 'firstperson_lefthand': HAND_1P,
    'thirdperson_righthand': HAND_3P, 'thirdperson_lefthand': HAND_3P,
    'head': {'rotation': [0, 90, 0], 'translation': [0, 14, 0], 'scale': [0.6, 0.6, 0.6]},
}
TEXTURES = {k: f'robotica:item/null_lance_{k}' for k in ('body', 'metal', 'trim', 'coil', 'coil_lit', 'core', 'core_hot')}
TEXTURES['particle'] = 'robotica:item/null_lance'
FLAT = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'robotica:item/null_lance'}}


def wrapper(held, overrides=None):
    m = {'loader': 'neoforge:separate_transforms', 'gui_light': 'front', 'base': {'parent': held},
         'perspectives': {'gui': FLAT, 'ground': FLAT, 'fixed': FLAT}}
    if overrides:
        m['overrides'] = overrides
    return m


def write(name, data):
    path = MODELS / f'{name}.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    write('null_lance_held', {'textures': TEXTURES, 'display': DISPLAY, 'elements': elements()})
    overrides = []
    for n, stage in enumerate(STAGES, 1):
        write(f'null_lance_held_{n}', {'parent': 'robotica:item/null_lance_held', 'elements': elements(n, stage >= 1.0)})
        write(f'null_lance_charge_{n}', wrapper(f'robotica:item/null_lance_held_{n}'))
        overrides.append({'predicate': {'robotica:charge': stage}, 'model': f'robotica:item/null_lance_charge_{n}'})
    write('null_lance', wrapper('robotica:item/null_lance_held', overrides))
    print('null lance models written')


if __name__ == '__main__':
    main()
