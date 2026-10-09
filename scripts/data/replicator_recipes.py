"""Writes the crafting recipes of the replicator module (balance ladder).
Run: python3 scripts/data/replicator_recipes.py   (overwrites data/robotica/recipe/<name>.json for replicator items only)

A full replicator is 24 frame/glass blocks plus the controller. The frame costs one Reinforced Casing (32 raw iron)
per 4 blocks, so a build is about 6 casings, 24 plates and 24 obsidian before the controller (4 more casings, a Servo
Core and 2 Servo Actuators). That is the Age 2 investment this machine is meant to be."""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/recipe'


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1, category='misc'):
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
            'key': {k: ing(v) for k, v in key.items()},
            'result': {'id': f'robotica:{name}', 'count': count}}
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


IRON_PLATE, QUARTZ, DIAMOND = '#c:plates/iron', '#c:gems/quartz', '#c:gems/diamond'
GLASS, OBSIDIAN = '#c:glass_blocks', '#c:obsidians'

# Age 2: vial = glass bottle + basic circuit + quartz + slime (a sample item, not a machine part)
shaped('essence_vial', [' A ', 'QBQ', ' S '],
       {'A': 'basic_circuit', 'Q': QUARTZ, 'B': 'minecraft:glass_bottle', 'S': '#c:slime_balls'})
# 4 frames per reinforced casing
shaped('replicator_frame', ['POP', 'ORO', 'POP'],
       {'P': IRON_PLATE, 'O': OBSIDIAN, 'R': 'reinforced_casing'}, count=4)
# 4 panes per frame
shaped('replicator_glass', ['GQG', 'QFQ', 'GQG'],
       {'G': GLASS, 'Q': QUARTZ, 'F': 'replicator_frame'}, count=4)
# servo actuators, advanced circuit and the servo core
shaped('replicator_controller', ['RAR', 'ZCZ', 'RDR'],
       {'R': 'reinforced_casing', 'A': 'advanced_circuit', 'Z': 'servo_actuator', 'C': 'servo_core', 'D': DIAMOND})
