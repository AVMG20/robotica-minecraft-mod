"""Writes gear crafting and smithing recipes (tools, weapons, upgrade kits, hand plates).
Run: python3 scripts/data/gear_recipes.py   (overwrites data/robotica/recipe/<name>.json for gear items only)"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/recipe'


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


def shaped(name, pattern, key, result=None, category='equipment'):
    write(name, {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
                 'key': {k: ing(v) for k, v in key.items()},
                 'result': {'id': f'robotica:{result or name}', 'count': 1}})


def shapeless(name, ingredients, result, category='misc'):
    write(name, {'type': 'minecraft:crafting_shapeless', 'category': category,
                 'ingredients': [ing(i) for i in ingredients],
                 'result': {'id': f'robotica:{result}', 'count': 1}})


def smithing(name, template, base, addition, result):
    write(name, {'type': 'minecraft:smithing_transform', 'template': ing(template), 'base': ing(base),
                 'addition': ing(addition), 'result': {'id': f'robotica:{result}', 'count': 1}})


COPPER, IRON, GOLD = '#c:ingots/copper', '#c:ingots/iron', '#c:ingots/gold'
IRON_PLATE = '#c:plates/iron'
STICK = '#c:rods/wooden'

# Hand plates: the hammer stays in the grid and loses 1 durability (HammerItem), 2 ingots -> 1 plate.
for metal, ingot in (('iron', IRON), ('copper', COPPER), ('gold', GOLD)):
    shapeless(f'{metal}_plate_from_hammer', ['tinkers_hammer', ingot, ingot], f'{metal}_plate')

# Age 0 (copper only)
shaped('tinkers_hammer', ['CCC', 'CGC', ' S '], {'C': COPPER, 'G': 'copper_gear', 'S': STICK})
shaped('felling_axe', ['CC', 'GS', ' S'], {'C': COPPER, 'G': 'copper_gear', 'S': STICK})
shaped('gearblade', ['C', 'G', 'S'], {'C': COPPER, 'G': 'copper_gear', 'S': STICK})

# Age 1
shaped('bore_drill', ['PIP', 'PMP', ' B '], {'P': IRON_PLATE, 'I': 'iron_casing', 'M': 'electric_motor', 'B': 'basic_circuit'})
shaped('chainsaw', ['PPP', 'MIB', 'P  '], {'P': IRON_PLATE, 'I': 'iron_casing', 'M': 'electric_motor', 'B': 'basic_circuit'})
shaped('tool_upgrade_kit_1', ['PCP', 'BIB', 'PCP'], {'P': IRON_PLATE, 'C': 'copper_coil', 'B': 'basic_circuit', 'I': 'iron_casing'})

# Age 2
shaped('rivet_gun', ['PPP', 'SAR', ' P '], {'P': IRON_PLATE, 'S': 'servo_actuator', 'A': 'advanced_circuit', 'R': 'reinforced_casing'})
shaped('tool_upgrade_kit_2', ['ASA', 'RKR'], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'R': 'reinforced_casing', 'K': 'tool_upgrade_kit_1'})

# Age 3 and 4 kits consume the previous kit
shaped('tool_upgrade_kit_3', ['QPQ', 'BKB'], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'B': 'blazing_casing', 'K': 'tool_upgrade_kit_2'})
shaped('tool_upgrade_kit_4', ['NPN', 'CKC'], {'N': 'null_circuit', 'P': 'plasma_actuator', 'C': 'null_casing', 'K': 'tool_upgrade_kit_3'})

# Smithing upgrades keep energy, mode and toggles (vanilla copies the component patch of the base).
smithing('servo_drill_from_bore_drill', 'tool_upgrade_kit_2', 'bore_drill', 'servo_core', 'servo_drill')
smithing('magma_drill_from_servo_drill', 'tool_upgrade_kit_3', 'servo_drill', 'magma_core', 'magma_drill')
smithing('null_drill_from_magma_drill', 'tool_upgrade_kit_4', 'magma_drill', 'antigrav_core', 'null_drill')
smithing('shock_baton_from_gearblade', 'tool_upgrade_kit_1', 'gearblade', 'electric_motor', 'shock_baton')
smithing('arc_blade_from_shock_baton', 'tool_upgrade_kit_3', 'shock_baton', 'magma_core', 'arc_blade')
smithing('null_lance_from_rivet_gun', 'tool_upgrade_kit_4', 'rivet_gun', 'antigrav_core', 'null_lance')
print('gear recipes written to', OUT)
