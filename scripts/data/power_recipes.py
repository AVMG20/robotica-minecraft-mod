"""Writes the crafting and pressing recipes of the power module (balance ladder).
Run: python3 scripts/data/power_recipes.py   (overwrites data/robotica/recipe/<name>.json for power items only)"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/recipe'


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1, category='misc', source=None):
    """source: key of the previous Mk; the result then keeps its data (robotica:machine_upgrade)."""
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'robotica:machine_upgrade' if source else 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
            'key': {k: ing(v) for k, v in key.items()},
            'result': {'id': f'robotica:{name}', 'count': count}}
    if source:
        data['source'] = ing(key[source])
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


def shapeless(name, ingredients, result, count=1, category='misc'):
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': [ing(x) for x in ingredients],
            'result': {'id': f'robotica:{result}', 'count': count}}
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


def pressing(name, ingredient, result, time=None):
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'robotica:pressing', 'ingredient': ing(ingredient), 'result': {'id': f'robotica:{result}', 'count': 1}}
    if time:
        data['time'] = time
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


COPPER, GOLD = '#c:ingots/copper', '#c:ingots/gold'
IRON_PLATE, COPPER_PLATE, GOLD_PLATE = '#c:plates/iron', '#c:plates/copper', '#c:plates/gold'
PYROSTEEL_PLATE, RESONANT_PLATE = '#c:plates/pyrosteel', '#c:plates/resonant_alloy'
REDSTONE_BLOCK, COBBLE, QUARTZ = '#c:storage_blocks/redstone', '#c:cobblestones', '#c:gems/quartz'

# Age 0: copper, wood, stone only
shaped('winding_crank', [' S ', 'CGC', 'BBB'], {'S': '#c:rods/wooden', 'C': COPPER, 'G': '#c:gears/copper', 'B': COBBLE})

# Age 1
# Early power: a furnace in a copper shell, one iron ingot. Craftable the moment you smelt your first iron.
shaped('combustion_generator', ['CCC', 'GFG', 'CIC'],
       {'C': COPPER, 'F': '#c:player_workstations/furnaces', 'G': '#c:gears/copper', 'I': '#c:ingots/iron'})
shaped('solar_panel_mk1', ['GGG', 'CBC', 'PPP'],
       {'G': '#c:glass_blocks', 'C': COPPER_PLATE, 'B': 'basic_circuit', 'P': IRON_PLATE})
shaped('accumulator_1', ['PRP', 'CIC', 'PRP'],
       {'P': IRON_PLATE, 'R': REDSTONE_BLOCK, 'C': 'copper_coil', 'I': 'iron_casing'})
# Tesla Relays: wireless power. Tier I is a first-iron item (no gold); every tier consumes the one before.
shaped('tesla_linker', ['R', 'C', 'I'], {'R': '#c:dusts/redstone', 'C': COPPER, 'I': '#c:ingots/iron'})
shaped('tesla_coil_1', [' R ', 'CKC', ' P '], {'R': '#c:dusts/redstone', 'C': COPPER, 'K': 'copper_coil', 'P': IRON_PLATE})
shaped('tesla_coil_2', [' B ', 'GTG', 'PKP'], {'B': 'basic_circuit', 'G': GOLD, 'T': 'tesla_coil_1', 'P': IRON_PLATE, 'K': 'copper_coil'})
shaped('charger', ['CPC', 'RGR', 'CCC'],
       {'C': COPPER, 'P': IRON_PLATE, 'R': '#c:dusts/redstone', 'G': '#c:gears/copper'})
shaped('metal_press', ['PPP', 'GSG', 'PMP'],
       {'P': IRON_PLATE, 'G': '#c:gears/copper', 'S': 'minecraft:piston', 'M': 'electric_motor'})

# Age 2: each tier consumes the previous one
# Wireless Charger: a Charger with a Tesla Relay II as emitter, an advanced circuit to pick the players.
shaped('wireless_charger', [' T ', 'ACA', 'QXQ'], {'T': 'tesla_coil_2', 'A': 'advanced_circuit', 'C': 'charger', 'Q': QUARTZ,
                                                 'X': 'reinforced_casing'})
# Wireless Charger Mk2-Mk4: the Mk below with a Tesla Emitter of the next tier on top, that age's circuit, plates and casing.
shaped('wireless_charger_mk2', [' T ', 'AWA', 'PXP'], {'T': 'tesla_emitter_3', 'A': 'advanced_circuit', 'W': 'wireless_charger',
                                                     'P': GOLD_PLATE, 'X': 'reinforced_casing'}, source='W')
# Charger Mk2-Mk4: the Mk below with that age's circuit, plates and casing (4x the charge rate each).
shaped('charger_mk2', [' A ', 'PCP', ' X '], {'A': 'advanced_circuit', 'P': GOLD_PLATE, 'C': 'charger', 'X': 'reinforced_casing'}, source='C')
# Solar Mk2-Mk4: the Mk below with that age's circuit and plates (20 -> 120 -> 300 -> 800 FE/t).
shaped('solar_panel_mk2', ['QGQ', 'GSG', 'QAQ'], {'Q': QUARTZ, 'G': '#c:glass_blocks', 'S': 'solar_panel_mk1', 'A': 'advanced_circuit'},
       source='S')
shaped('accumulator_2', ['RCR', 'AXA', 'RCR'],
       {'R': REDSTONE_BLOCK, 'C': 'advanced_circuit', 'A': 'accumulator_1', 'X': 'reinforced_casing'}, source='A')
shaped('tesla_coil_3', [' A ', 'QTQ', ' X '], {'A': 'advanced_circuit', 'Q': QUARTZ, 'T': 'tesla_coil_2', 'X': 'reinforced_casing'})
# Age 3
shaped('solar_panel_mk3', ['PGP', 'GSG', 'PCP'], {'P': '#c:plates/pyrosteel', 'G': '#c:glass_blocks', 'S': 'solar_panel_mk2',
                                                 'C': 'quantum_circuit'}, source='S')
shaped('accumulator_3', ['RCR', 'AXA', 'RCR'],
       {'R': REDSTONE_BLOCK, 'C': 'quantum_circuit', 'A': 'accumulator_2', 'X': 'blazing_casing'}, source='A')
shaped('charger_mk3', [' Q ', 'PCP', ' X '], {'Q': 'quantum_circuit', 'P': PYROSTEEL_PLATE, 'C': 'charger_mk2', 'X': 'blazing_casing'}, source='C')
shaped('wireless_charger_mk3', [' T ', 'QWQ', 'PXP'], {'T': 'tesla_emitter_4', 'Q': 'quantum_circuit', 'W': 'wireless_charger_mk2',
                                                     'P': PYROSTEEL_PLATE, 'X': 'blazing_casing'}, source='W')
shaped('tesla_coil_4', [' C ', 'BTB', ' X '], {'C': 'quantum_circuit', 'B': '#c:rods/blaze', 'T': 'tesla_coil_3', 'X': 'blazing_casing'})
# Age 4
shaped('solar_panel_mk4', ['PEP', 'ESE', 'PCP'], {'P': '#c:plates/resonant_alloy', 'E': '#c:ender_pearls', 'S': 'solar_panel_mk3',
                                                 'C': 'null_circuit'}, source='S')
shaped('charger_mk4', [' N ', 'PCP', ' X '], {'N': 'null_circuit', 'P': RESONANT_PLATE, 'C': 'charger_mk3', 'X': 'null_casing'}, source='C')
shaped('wireless_charger_mk4', [' T ', 'NWN', 'PXP'], {'T': 'tesla_emitter_5', 'N': 'null_circuit', 'W': 'wireless_charger_mk3',
                                                     'P': RESONANT_PLATE, 'X': 'null_casing'}, source='W')
shaped('tesla_coil_5', [' C ', 'ETE', ' X '], {'C': 'null_circuit', 'E': '#c:ender_pearls', 'T': 'tesla_coil_4', 'X': 'null_casing'})
# Tesla Emitters: a coil splits into 4 one-link emitters of its tier, and 4 craft back into the coil.
for t in range(1, 6):
    shapeless(f'tesla_emitter_{t}', [f'tesla_coil_{t}'], f'tesla_emitter_{t}', count=4)
    shapeless(f'tesla_coil_{t}_from_emitters', [f'tesla_emitter_{t}'] * 4, f'tesla_coil_{t}')

# Metal Press: 1 ingot -> 1 plate (100 ticks at base speed)
pressing('metal_press_iron_plate', '#c:ingots/iron', 'iron_plate')
pressing('metal_press_copper_plate', '#c:ingots/copper', 'copper_plate')
pressing('metal_press_gold_plate', '#c:ingots/gold', 'gold_plate')
