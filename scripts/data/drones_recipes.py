"""Writes the drone recipes of the drones module.
Run: python3 scripts/data/drones_recipes.py   (overwrites data/robotica/recipe/<name>.json for drone items only)

Ladder: the Mining Drone is an early helper (iron pickaxe, clockwork mechanism, copper coil, a little iron and redstone, no gold
or diamond), the Sentry Drone is Age 1 parts (motor, casing, circuit), the Courier Drone Age 1 parts too. Mk2 are Age 2 smithing
upgrades that keep energy, inventory and settings. Item models come from scripts/textures/drones.py.
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


def shaped(name, pattern, key, category='equipment'):
    write(name, {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
                 'key': {k: ing(v) for k, v in key.items()},
                 'result': {'id': f'robotica:{name}', 'count': 1}})


def smithing(name, template, base, addition, result):
    write(name, {'type': 'minecraft:smithing_transform', 'template': ing(template), 'base': ing(base),
                 'addition': ing(addition), 'result': {'id': f'robotica:{result}', 'count': 1}})


IRON = '#c:ingots/iron'

# Mining Drone: iron pickaxe on top, clockwork mechanism in the middle, copper coil as the motor
shaped('mining_drone', [' P ', 'ICI', 'RWR'],
       {'P': 'minecraft:iron_pickaxe', 'I': IRON, 'C': 'clockwork_mechanism', 'R': 'minecraft:redstone', 'W': 'copper_coil'})
# Sentry Drone: crossbow as the cannon, motor, casing and circuit
shaped('sentry_drone', [' X ', 'BIM', ' R '],
       {'X': 'minecraft:crossbow', 'B': 'basic_circuit', 'I': 'iron_casing', 'M': 'electric_motor', 'R': 'minecraft:redstone'})
# Courier Drone: hopper as the cargo hold
shaped('courier_drone', [' H ', 'BIM', ' R '],
       {'H': 'minecraft:hopper', 'B': 'basic_circuit', 'I': 'iron_casing', 'M': 'electric_motor', 'R': 'minecraft:redstone'})
# Mk2: smithing keeps the data components of the base (energy, inventory, settings)
smithing('mining_drone_mk2', 'advanced_circuit', 'mining_drone', 'servo_actuator', 'mining_drone_mk2')
smithing('sentry_drone_mk2', 'advanced_circuit', 'sentry_drone', 'servo_actuator', 'sentry_drone_mk2')
print('drone recipes written to', OUT)
