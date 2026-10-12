"""Survey Rig data: block model, blockstate, item model, loot table and recipe.
Run: python3 scripts/data/automation_survey_rig.py   (overwrites only Survey Rig files)

The model faces north (-Z); the blockstate turns it toward the player. A plinth, a tower with a depth display on its
front, a mast with a scanner dish tilted toward the front and its amber feed lamp (textures:
scripts/textures/survey_rig.py). Coplanar full-bright overlays carry the lights: the plinth's work light strip (at
standby while idle) and, working, the dish's scan rings, the display's scan bar and the tower's energy channels.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from automation_data import ASSETS, loot, shaped, write  # noqa: E402

GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
SIDES = ('north', 'east', 'south', 'west')


def el(a, b, faces, glow=False):
    e = {'from': list(a), 'to': list(b), 'faces': faces}
    if glow:
        e['shade'] = False
        for f in faces.values():
            f['neoforge_data'] = dict(GLOW)
    return e


def f(tex, cull=None, uv=None):
    face = {'texture': '#' + tex}
    if uv:
        face['uv'] = uv
    if cull:
        face['cullface'] = cull
    return face


def rotated(e):
    """Tilt a dish part back 22.5 degrees round the dish centre, so the dish faces up and forward."""
    e['rotation'] = {'origin': [8, 13.6, 8], 'axis': 'x', 'angle': -22.5}
    return e


def elements(lit):
    els = [
        el((0, 0, 0), (16, 4, 16), dict({d: f('base_side', d) for d in SIDES}, up=f('base_top'),
                                        down=f('bottom', 'down'))),
        el((2, 4, 2), (14, 11, 14), dict({d: f('side') for d in ('east', 'south', 'west')}, north=f('front'),
                                         up=f('top'))),
        el((3, 5, 1), (13, 10, 2), {d: f('screen') for d in ('north', 'east', 'west', 'up', 'down')}),
        el((6.5, 11, 6.5), (9.5, 13.5, 9.5), {d: f('mast') for d in SIDES}),
        rotated(el((2.5, 13.35, 2.5), (13.5, 13.85, 13.5), {'up': f('dish'), 'down': f('dish_back')})),
        rotated(el((7, 13.85, 7), (9, 15.35, 9), dict({d: f('feed', uv=[0, 0, 2, 1.5]) for d in SIDES},
                                                     up=f('feed', uv=[0, 0, 2, 2])), glow=True)),
        el((0, 0, 0), (16, 4, 16), {d: f('base_glow', d) for d in SIDES}, glow=True),
    ]
    if lit:
        els += [
            el((2, 4, 2), (14, 11, 14), {d: f('channel') for d in ('east', 'south', 'west')}, glow=True),
            el((3, 5, 0.99), (13, 10, 0.99), {'north': f('screen_glow')}, glow=True),
            rotated(el((2.5, 13.86, 2.5), (13.5, 13.86, 13.5), {'up': f('dish_glow')}, glow=True)),
        ]
    return els


def textures(tier, lit):
    mk = f'robotica:block/survey_rig_mk{tier}'
    on = '_on' if lit else ''
    t = {d: f'{mk}_{d}' for d in ('base_side', 'base_top', 'side', 'front', 'top', 'dish')}
    t['particle'] = f'{mk}_side'
    for part in ('bottom', 'dish_back', 'mast'):
        t[part] = f'robotica:block/survey_rig_{part}'
    for part in ('screen', 'feed', 'base_glow'):
        t[part] = f'robotica:block/survey_rig_{part}{on}'
    if lit:
        t['channel'] = 'robotica:block/survey_rig_channel_glow'
        for part in ('screen_glow', 'dish_glow'):
            t[part] = f'robotica:block/survey_rig_{part}'
    return t


def main():
    # Mk1-4: full models per Mk (tier metal trims) and state (LIT: scan rings, scan bar, lamps, energy channels).
    for tier in range(1, 5):
        name = 'survey_rig' if tier == 1 else f'survey_rig_mk{tier}'
        for lit in (False, True):
            write(ASSETS / f'models/block/{name}{"_on" if lit else ""}.json', {
                'render_type': 'minecraft:cutout',
                'parent': 'minecraft:block/block',
                'textures': textures(tier, lit),
                'elements': elements(lit),
            })
        variants = {}
        for lit in (False, True):
            for i, facing in enumerate(('north', 'east', 'south', 'west')):
                v = {'model': f'robotica:block/{name}' + ('_on' if lit else '')}
                if i:
                    v['y'] = 90 * i
                variants[f'facing={facing},lit={str(lit).lower()}'] = v
        write(ASSETS / f'blockstates/{name}.json', {'variants': variants})
        write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{name}'})
        loot(name)
    # Age 2 and clearly above the Excavator: Servo parts, a diamond pickaxe as the sampling head, ender pearls for
    # "pulling ores out of thin air".
    shaped('survey_rig', ['EAE', 'SXS', 'RCR'], {'E': '#c:ender_pearls', 'A': 'advanced_circuit', 'S': 'servo_actuator',
                                              'X': 'minecraft:diamond_pickaxe', 'R': 'reinforced_casing',
                                              'C': 'copper_coil'})
    # Mk2-4 consume the Mk before; a diamond-studded Mk2 at Age 2, then that age's circuit, actuator and casing.
    shaped('survey_rig_mk2', ['DAD', 'SXS', ' R '], {'D': '#c:gems/diamond', 'A': 'advanced_circuit', 'S': 'servo_actuator',
                                                   'X': 'survey_rig', 'R': 'reinforced_casing'})
    shaped('survey_rig_mk3', [' Q ', 'PXP', ' B '], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'X': 'survey_rig_mk2',
                                                   'B': 'blazing_casing'})
    shaped('survey_rig_mk4', [' N ', 'PXP', 'ECE'], {'N': 'null_circuit', 'P': 'plasma_actuator', 'X': 'survey_rig_mk3',
                                                   'E': '#c:ender_pearls', 'C': 'null_casing'})
    print('survey rig data written')


if __name__ == '__main__':
    main()
