"""Writes core crafting recipes (balance ladder, cells, temp boss cores, upgrade cards).
Run: python3 scripts/data/core_recipes.py   (overwrites data/robotica/recipe/<name>.json for core items only)"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/recipe'


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, result=None, count=1, category='misc'):
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
            'key': {k: ing(v) for k, v in key.items()},
            'result': {'id': f'robotica:{result or name}', 'count': count}}
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


COPPER, IRON, GOLD = '#c:ingots/copper', '#c:ingots/iron', '#c:ingots/gold'
REDSTONE, COBBLE, DIAMOND = '#c:dusts/redstone', '#c:cobblestones', '#c:gems/diamond'
IRON_PLATE, GOLD_PLATE = '#c:plates/iron', '#c:plates/gold'

# Age 0
shaped('copper_gear', [' C ', 'CSC', ' C '], {'C': COPPER, 'S': COBBLE})
shaped('clockwork_mechanism', ['PGP', 'GSG', 'PGP'], {'P': '#minecraft:planks', 'G': 'copper_gear', 'S': COBBLE})
shaped('wooden_chassis', ['LSL', 'SBS', 'LSL'], {'L': '#minecraft:logs', 'S': COBBLE, 'B': '#c:storage_blocks/copper'})
shaped('mainspring', ['CCC', 'CGC', 'CCC'], {'C': COPPER, 'G': 'copper_gear'}, category='equipment')
# Age 1
shaped('copper_coil', ['CCC', 'CIC', 'CCC'], {'C': COPPER, 'I': IRON})
shaped('iron_casing', ['III', 'IRI', 'III'], {'I': IRON, 'R': REDSTONE})
shaped('basic_circuit', ['RRR', 'CGC', 'PPP'], {'R': REDSTONE, 'C': COPPER, 'G': GOLD, 'P': IRON_PLATE})
shaped('electric_motor', ['PRP', 'CMC', 'PRP'], {'P': IRON_PLATE, 'R': REDSTONE, 'C': 'copper_coil', 'M': 'clockwork_mechanism'})
# First rechargeable battery: first-iron hour, no gold. Redstone is what makes it a cell.
shaped('copper_cell', ['CRC', 'PRP', 'CRC'], {'C': COPPER, 'P': IRON_PLATE, 'R': REDSTONE}, category='equipment')
# Age 2
shaped('reinforced_casing', ['OCO', 'CDC', 'OCO'], {'O': '#c:obsidians', 'C': 'iron_casing', 'D': DIAMOND})
shaped('advanced_circuit', ['BGB', 'QDQ', 'BGB'], {'B': 'basic_circuit', 'G': GOLD, 'Q': '#c:gems/quartz', 'D': DIAMOND})
shaped('servo_actuator', ['GPG', 'MAM', 'GPG'], {'G': GOLD, 'P': IRON_PLATE, 'M': 'electric_motor', 'A': 'advanced_circuit'})
shaped('redstone_cell', ['RCR', 'RAR', 'CX '], {'R': '#c:storage_blocks/redstone', 'C': 'copper_cell', 'A': 'advanced_circuit', 'X': 'reinforced_casing'}, category='equipment')
# The Scrap Colossus (boss module) is the real source of Servo Cores. This fallback stays for Peaceful worlds and servers
# that turn the Rusted Foundry off, but it costs diamond blocks instead of diamonds (about 860 IE, a Signal Flare is ~24).
shaped('temp_servo_core', ['DSD', 'SAS', 'DSD'], {'D': '#c:storage_blocks/diamond', 'S': 'servo_actuator', 'A': 'reinforced_casing'}, result='servo_core')
# Age 3
shaped('blazing_casing', ['NRN', 'RMR', 'NRN'], {'N': 'minecraft:netherite_scrap', 'R': 'reinforced_casing', 'M': 'magma_core'}, count=2)
shaped('quantum_circuit', ['ABA', 'BNB', 'ABA'], {'A': 'advanced_circuit', 'B': '#c:rods/blaze', 'N': '#c:ingots/netherite'})
shaped('plasma_actuator', ['PBP', 'SQS', 'PBP'], {'P': 'minecraft:prismarine_crystals', 'B': 'minecraft:blaze_powder', 'S': 'servo_actuator', 'Q': 'quantum_circuit'})
# temp boss recipe: must clearly cost more than the Servo Core (4 actuators), so it also needs 2 quantum circuits
shaped('temp_magma_core', ['SQS', 'MNM', 'SQS'], {'S': 'servo_actuator', 'Q': 'quantum_circuit', 'M': 'minecraft:magma_block', 'N': '#c:ingots/netherite'}, result='magma_core')
# Age 4
shaped('null_casing', ['SBS', 'BAB', 'SBS'], {'S': 'minecraft:shulker_shell', 'B': 'blazing_casing', 'A': 'antigrav_core'}, count=2)
shaped('null_circuit', ['QEQ', 'ENE', 'QEQ'], {'Q': 'quantum_circuit', 'E': '#c:ender_pearls', 'N': '#c:nether_stars'}, count=2)
shaped('ender_cell', ['ERE', 'NCN', 'ERE'], {'E': '#c:ender_pearls', 'R': 'redstone_cell', 'N': 'null_casing', 'C': 'null_circuit'}, category='equipment')
shaped('temp_antigrav_core', ['PCP', 'CNC', 'PCP'], {'P': 'plasma_actuator', 'C': 'minecraft:end_crystal', 'N': '#c:nether_stars'}, result='antigrav_core')

# Upgrade cards: one card per kind, stackable kinds stack in a machine slot (every card adds a step, see Upgrades.java).
# Age 1 cards: two catalysts in the corners, iron plates and a Basic Circuit. Age 2 cards: gold plates and an Advanced Circuit.
# The price of going fast is paid in FE (speed costs grow with the square, very steeply on the Excavator) and in the
# slot caps (robots take as many speed/range/growth cards as their Mk tier).
CARDS = {
    'speed': (1, 'minecraft:sugar', REDSTONE),
    'efficiency': (1, GOLD, GOLD),
    'growth': (1, 'minecraft:bone_block', 'minecraft:bone_block'),
    'void': (1, 'minecraft:cactus', '#c:obsidians'),
    'pickup': (1, '#c:ender_pearls', REDSTONE),
    'range': (2, '#c:ender_pearls', '#c:gems/lapis'),
    'fortune': (2, '#c:storage_blocks/lapis', DIAMOND),
    'silk': (2, '#c:slime_balls', '#c:gems/emerald'),
}
for f in OUT.glob('upgrade_*.json'):
    f.unlink()
for kind, (age, a, b) in CARDS.items():
    name = f'upgrade_{kind}'
    if age == 1:
        shaped(name, ['APB', 'PCP', 'BPA'], {'A': a, 'B': b, 'P': IRON_PLATE, 'C': 'basic_circuit'})
    else:
        shaped(name, ['AGB', 'GCG', 'BGA'], {'A': a, 'B': b, 'G': GOLD_PLATE, 'C': 'advanced_circuit'})
print('core recipes written to', OUT)
