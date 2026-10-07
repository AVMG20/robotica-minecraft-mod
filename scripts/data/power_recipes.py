"""Writes the crafting and pressing recipes of the power module (balance ladder, see docs/DESIGN.md).
Run: python3 scripts/data/power_recipes.py   (overwrites data/robotica/recipe/<name>.json for power items only)"""
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


def pressing(name, ingredient, result, time=None):
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'robotica:pressing', 'ingredient': ing(ingredient), 'result': {'id': f'robotica:{result}', 'count': 1}}
    if time:
        data['time'] = time
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


COPPER, GOLD = '#c:ingots/copper', '#c:ingots/gold'
IRON_PLATE, COPPER_PLATE = '#c:plates/iron', '#c:plates/copper'
REDSTONE_BLOCK, COBBLE, QUARTZ = '#c:storage_blocks/redstone', '#c:cobblestones', '#c:gems/quartz'

# Age 0: copper, wood, stone only
shaped('winding_crank', [' S ', 'CGC', 'BBB'], {'S': 'minecraft:stick', 'C': COPPER, 'G': 'copper_gear', 'B': COBBLE})

# Age 1
# Early power: a furnace in a copper shell, one iron ingot. Craftable the moment you smelt your first iron.
shaped('combustion_generator', ['CCC', 'GFG', 'CIC'],
       {'C': COPPER, 'F': 'minecraft:furnace', 'G': 'copper_gear', 'I': '#c:ingots/iron'})
shaped('solar_panel_mk1', ['GGG', 'CBC', 'PPP'],
       {'G': '#c:glass_blocks', 'C': COPPER_PLATE, 'B': 'basic_circuit', 'P': IRON_PLATE})
shaped('accumulator_1', ['PRP', 'CIC', 'PRP'],
       {'P': IRON_PLATE, 'R': REDSTONE_BLOCK, 'C': 'copper_coil', 'I': 'iron_casing'})
# Tesla Coils: wireless power. Tier I is a first-iron item (no gold); every tier consumes the one before.
shaped('tesla_linker', ['R', 'C', 'I'], {'R': '#c:dusts/redstone', 'C': COPPER, 'I': '#c:ingots/iron'})
shaped('tesla_coil_1', [' R ', 'CKC', ' P '], {'R': '#c:dusts/redstone', 'C': COPPER, 'K': 'copper_coil', 'P': IRON_PLATE})
shaped('tesla_coil_2', [' B ', 'GTG', 'PKP'], {'B': 'basic_circuit', 'G': GOLD, 'T': 'tesla_coil_1', 'P': IRON_PLATE, 'K': 'copper_coil'})
shaped('charger', ['CPC', 'RGR', 'CCC'],
       {'C': COPPER, 'P': IRON_PLATE, 'R': '#c:dusts/redstone', 'G': 'copper_gear'})
shaped('metal_press', ['PPP', 'GSG', 'PMP'],
       {'P': IRON_PLATE, 'G': 'copper_gear', 'S': 'minecraft:piston', 'M': 'electric_motor'})

# Age 2: each tier consumes the previous one
# Wireless Charger: a Charger with a Tesla Coil II as emitter, an advanced circuit to pick the players.
shaped('wireless_charger', [' T ', 'ACA', 'QXQ'], {'T': 'tesla_coil_2', 'A': 'advanced_circuit', 'C': 'charger', 'Q': QUARTZ,
                                                 'X': 'reinforced_casing'})
shaped('solar_panel_mk2', ['SQS', 'QAQ', 'SQS'], {'S': 'solar_panel_mk1', 'Q': QUARTZ, 'A': 'advanced_circuit'})
shaped('accumulator_2', ['RCR', 'AXA', 'RCR'],
       {'R': REDSTONE_BLOCK, 'C': 'advanced_circuit', 'A': 'accumulator_1', 'X': 'reinforced_casing'})
shaped('tesla_coil_3', [' A ', 'QTQ', ' X '], {'A': 'advanced_circuit', 'Q': QUARTZ, 'T': 'tesla_coil_2', 'X': 'reinforced_casing'})
# Age 3
# Solar Mk3 / Mk4: two panels of the tier below (2 x 80 -> 200, 2 x 200 -> 500 FE/t), that age's circuit and alloy plates.
shaped('solar_panel_mk3', ['PGP', 'SCS', 'PGP'], {'P': '#c:plates/pyrosteel', 'G': '#c:glass_blocks', 'S': 'solar_panel_mk2',
                                                 'C': 'quantum_circuit'})
shaped('accumulator_3', ['RCR', 'AXA', 'RCR'],
       {'R': REDSTONE_BLOCK, 'C': 'quantum_circuit', 'A': 'accumulator_2', 'X': 'blazing_casing'})
shaped('tesla_coil_4', [' C ', 'BTB', ' X '], {'C': 'quantum_circuit', 'B': '#c:rods/blaze', 'T': 'tesla_coil_3', 'X': 'blazing_casing'})
# Age 4
shaped('solar_panel_mk4', ['PEP', 'SCS', 'PEP'], {'P': '#c:plates/resonant_alloy', 'E': '#c:ender_pearls', 'S': 'solar_panel_mk3',
                                                 'C': 'null_circuit'})
shaped('tesla_coil_5', [' C ', 'ETE', ' X '], {'C': 'null_circuit', 'E': '#c:ender_pearls', 'T': 'tesla_coil_4', 'X': 'null_casing'})

# Metal Press: 1 ingot -> 1 plate (100 ticks at base speed)
pressing('metal_press_iron_plate', '#c:ingots/iron', 'iron_plate')
pressing('metal_press_copper_plate', '#c:ingots/copper', 'copper_plate')
pressing('metal_press_gold_plate', '#c:ingots/gold', 'gold_plate')
