"""Writes the drone recipes of the drones module.
Run: python3 scripts/data/drones_recipes.py   (overwrites data/robotica/recipe/<name>.json for drone items only)

Ladder: the Buddy Bot is an early helper (crossbow, chest, iron pickaxe, clockwork mechanism, copper coil, a little iron and
redstone, no gold or diamond), the Courier Drone is Age 1 parts (motor, casing, circuit). Mk2 are Age 2 smithing upgrades that
keep energy, inventory and settings; the Buddy Bot Mk3 and Mk4 are Age 3 and Age 4 smithing upgrades. Item models come from scripts/textures/drones.py.
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

# Buddy Bot Mk1 (first iron hour, no gold): a chest for the cargo, a crossbow for the bolts, an iron pickaxe for the drill,
# clockwork and a copper coil as the motor
shaped('buddy_bot', ['XHP', 'ICI', 'RWR'],
       {'X': '#c:tools/crossbow', 'H': '#c:chests/wooden', 'P': 'minecraft:iron_pickaxe', 'I': IRON, 'C': 'clockwork_mechanism',
        'R': '#c:dusts/redstone', 'W': 'copper_coil'})
# Courier Drone: hopper as the cargo hold
shaped('courier_drone', [' H ', 'BIM', ' R '],
       {'H': 'minecraft:hopper', 'B': 'basic_circuit', 'I': 'iron_casing', 'M': 'electric_motor', 'R': '#c:dusts/redstone'})
# Buddy Bot Mk2 to Mk4: smithing keeps the data components of the base (energy, cargo, settings)
smithing('buddy_bot_mk2', 'advanced_circuit', 'buddy_bot', 'servo_actuator', 'buddy_bot_mk2')
smithing('buddy_bot_mk3', 'quantum_circuit', 'buddy_bot_mk2', 'plasma_actuator', 'buddy_bot_mk3')
smithing('buddy_bot_mk4', 'null_circuit', 'buddy_bot_mk3', 'antigrav_core', 'buddy_bot_mk4')
# Courier Remote: a circuit and a little redstone in an iron shell
shaped('courier_remote', [' R ', 'ICI'], {'R': '#c:dusts/redstone', 'I': IRON, 'C': 'basic_circuit'})
smithing('courier_drone_mk2', 'advanced_circuit', 'courier_drone', 'servo_actuator', 'courier_drone_mk2')
# Hauler Drone (first iron hour, like the Buddy Bot): a lead as the winch line, clockwork and a copper coil, two iron plates
shaped('hauler_drone', [' C ', 'PWP', ' L '],
       {'C': 'clockwork_mechanism', 'P': '#c:plates/iron', 'W': 'copper_coil', 'L': 'minecraft:lead'})
smithing('hauler_drone_mk2', 'advanced_circuit', 'hauler_drone', 'servo_actuator', 'hauler_drone_mk2')
print('drone recipes written to', OUT)
