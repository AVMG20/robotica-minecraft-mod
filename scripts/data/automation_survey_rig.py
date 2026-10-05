"""Survey Rig data: block model, blockstate, item model, loot table and recipe.
Run: python3 scripts/data/automation_survey_rig.py   (overwrites only Survey Rig files)

The model faces north (-Z); the blockstate turns it toward the player. A tall plated tower on a plinth, a glowing
scan band around it, a scan window on the front, a mast with a tilted scanner dish and a glowing receiver on top.
The band, window and receiver are emissive (neoforge_data block_light / sky_light 15).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from automation_data import ASSETS, box, loot, shaped, write  # noqa: E402

GLOW = {'block_light': 15, 'sky_light': 15}
SIDES = ('north', 'south', 'east', 'west')


def glowing(el):
    for face in el['faces'].values():
        face['neoforge_data'] = dict(GLOW)
    return el


def rotated(el, angle, origin=(8, 15, 8), axis='x'):
    el['rotation'] = {'origin': list(origin), 'axis': axis, 'angle': angle}
    return el


def elements():
    els = [
        # plinth with a brass trim
        box((0, 0, 0), (16, 3, 16), 'dark'),
        box((0.5, 3, 0.5), (15.5, 4, 15.5), 'brass'),
        # the tower and its corner posts
        box((2, 4, 2), (14, 13, 14), 'panel'),
        box((1.5, 4, 1.5), (3.5, 13, 3.5), 'dark'),
        box((12.5, 4, 1.5), (14.5, 13, 3.5), 'dark'),
        box((1.5, 4, 12.5), (3.5, 13, 14.5), 'dark'),
        box((12.5, 4, 12.5), (14.5, 13, 14.5), 'dark'),
        # glowing scan band around the tower
        glowing(box((1.9, 6.6, 3.5), (14.1, 7.4, 12.5), 'glow', faces=('east', 'west'))),
        glowing(box((3.5, 6.6, 13.9), (12.5, 7.4, 14.1), 'glow', faces=('south',))),
        # scan window on the front
        glowing(box((4.5, 8, 1.8), (11.5, 12, 2), 'scan', faces=('north',))),
        glowing(box((3.5, 6.6, 1.8), (12.5, 7.4, 2), 'glow', faces=('north',))),
        # cap, mast, dish and receiver
        box((3, 13, 3), (13, 14, 13), 'dark'),
        box((7, 14, 7), (9, 16, 9), 'steel'),
        rotated(box((2.5, 15, 2.5), (13.5, 16, 13.5), 'steel', faces=('north', 'south', 'east', 'west', 'down')), -22.5),
        rotated(box((2.5, 15, 2.5), (13.5, 16, 13.5), 'dish', faces=('up',)), -22.5),
        rotated(box((7.5, 16, 7.5), (8.5, 18.5, 8.5), 'steel'), -22.5),
        rotated(glowing(box((7, 18.5, 7), (9, 19.5, 9), 'glow')), -22.5),
    ]
    return els


def main():
    textures = {'panel': 'robotica:block/survey_rig_panel', 'dark': 'robotica:block/automation_dark',
                'steel': 'robotica:block/automation_steel', 'brass': 'robotica:block/automation_brass',
                'scan': 'robotica:block/survey_rig_scan', 'dish': 'robotica:block/survey_rig_dish',
                'glow': 'robotica:block/survey_rig_glow', 'particle': 'robotica:block/survey_rig_panel'}
    write(ASSETS / 'models/block/survey_rig.json', {
        'render_type': 'minecraft:cutout',
        'parent': 'minecraft:block/block',
        'textures': textures,
        'elements': elements(),
    })
    variants = {}
    for i, facing in enumerate(('north', 'east', 'south', 'west')):
        v = {'model': 'robotica:block/survey_rig'}
        if i:
            v['y'] = 90 * i
        variants[f'facing={facing}'] = v
    write(ASSETS / 'blockstates/survey_rig.json', {'variants': variants})
    write(ASSETS / 'models/item/survey_rig.json', {'parent': 'robotica:block/survey_rig'})
    loot('survey_rig')
    # Age 2 and clearly above the Excavator: Servo parts, a diamond pickaxe as the sampling head, ender pearls for
    # "pulling ores out of thin air".
    shaped('survey_rig', ['EAE', 'SXS', 'RCR'], {'E': '#c:ender_pearls', 'A': 'advanced_circuit', 'S': 'servo_actuator',
                                              'X': 'minecraft:diamond_pickaxe', 'R': 'reinforced_casing',
                                              'C': 'copper_coil'})
    print('survey rig data written')


if __name__ == '__main__':
    main()
