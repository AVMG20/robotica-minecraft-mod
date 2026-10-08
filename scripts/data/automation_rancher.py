"""Writes the Rancher recipes (automation module).
Run: python3 scripts/data/automation_rancher.py   (overwrites data/robotica/recipe/rancher*.json)

Mk1 is Age 1: shears, a bucket, a Basic Circuit, an Iron Casing and two Electric Motors (one more motor than the Sentry
Drone). Mk2 is the Age 2 smithing upgrade like the drone Mk2s (Advanced Circuit template, Servo Actuator); smithing keeps
energy, settings and battery. Item models come from scripts/textures/rancher.py.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/data/robotica/recipe'


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


write('rancher', {
    'type': 'minecraft:crafting_shaped', 'category': 'equipment',
    'pattern': ['SCB', 'MIM', 'P P'],
    'key': {k: ing(v) for k, v in {
        'S': '#c:tools/shear', 'C': 'basic_circuit', 'B': '#c:buckets/empty',
        'M': 'electric_motor', 'I': 'iron_casing', 'P': '#c:plates/iron'}.items()},
    'result': {'id': 'robotica:rancher', 'count': 1}})

write('rancher_mk2', {
    'type': 'minecraft:smithing_transform', 'template': ing('advanced_circuit'), 'base': ing('rancher'),
    'addition': ing('servo_actuator'), 'result': {'id': 'robotica:rancher_mk2', 'count': 1}})
print('rancher recipes written to', OUT)
