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
shaped('copper_cell', ['CPC', 'RBR', 'CPC'], {'C': COPPER, 'P': IRON_PLATE, 'R': REDSTONE, 'B': 'basic_circuit'}, category='equipment')
# Age 2
shaped('reinforced_casing', ['OCO', 'CDC', 'OCO'], {'O': '#c:obsidians', 'C': 'iron_casing', 'D': DIAMOND})
shaped('advanced_circuit', ['BGB', 'QDQ', 'BGB'], {'B': 'basic_circuit', 'G': GOLD, 'Q': '#c:gems/quartz', 'D': DIAMOND})
shaped('servo_actuator', ['GPG', 'MAM', 'GPG'], {'G': GOLD, 'P': IRON_PLATE, 'M': 'electric_motor', 'A': 'advanced_circuit'})
shaped('redstone_cell', ['RCR', 'RAR', 'CX '], {'R': '#c:storage_blocks/redstone', 'C': 'copper_cell', 'A': 'advanced_circuit', 'X': 'reinforced_casing'}, category='equipment')
shaped('temp_servo_core', ['DSD', 'SAS', 'DSD'], {'D': DIAMOND, 'S': 'servo_actuator', 'A': 'advanced_circuit'}, result='servo_core')
# Age 3
shaped('blazing_casing', ['NRN', 'RMR', 'NRN'], {'N': 'minecraft:netherite_scrap', 'R': 'reinforced_casing', 'M': 'magma_core'}, count=2)
shaped('quantum_circuit', ['ABA', 'BNB', 'ABA'], {'A': 'advanced_circuit', 'B': '#c:rods/blaze', 'N': '#c:ingots/netherite'})
shaped('plasma_actuator', ['PBP', 'SQS', 'PBP'], {'P': 'minecraft:prismarine_crystals', 'B': 'minecraft:blaze_powder', 'S': 'servo_actuator', 'Q': 'quantum_circuit'})
shaped('temp_magma_core', ['SMS', 'MNM', 'SMS'], {'S': 'servo_actuator', 'M': 'minecraft:magma_block', 'N': '#c:ingots/netherite'}, result='magma_core')
# Age 4
shaped('null_casing', ['SBS', 'BAB', 'SBS'], {'S': 'minecraft:shulker_shell', 'B': 'blazing_casing', 'A': 'antigrav_core'}, count=2)
shaped('null_circuit', ['QEQ', 'ENE', 'QEQ'], {'Q': 'quantum_circuit', 'E': '#c:ender_pearls', 'N': '#c:nether_stars'}, count=2)
shaped('ender_cell', ['ERE', 'NCN', 'ERE'], {'E': '#c:ender_pearls', 'R': 'redstone_cell', 'N': 'null_casing', 'C': 'null_circuit'}, category='equipment')
shaped('temp_antigrav_core', ['PCP', 'CNC', 'PCP'], {'P': 'plasma_actuator', 'C': 'minecraft:end_crystal', 'N': '#c:nether_stars'}, result='antigrav_core')

# Upgrade cards. Corners: A top-left/bottom-right, B top-right/bottom-left.
# Level N consumes level N-1, so a level 4 card carries every tier's cost.
CATALYSTS = {
    'speed': {1: ('minecraft:sugar', REDSTONE)},
    'range': {1: ('#c:gems/lapis', '#c:gems/lapis'), 2: ('#c:ender_pearls', '#c:gems/lapis')},
    'efficiency': {1: (GOLD, GOLD)},
    'fortune': {2: ('#c:storage_blocks/lapis', DIAMOND)},
    'silk': {1: ('#c:slime_balls', '#c:gems/emerald')},
    'growth': {1: ('minecraft:bone_block', 'minecraft:bone_block')},
    'void': {1: ('minecraft:cactus', '#c:obsidians')},
}
LEVELS = {'speed': (1, 4), 'range': (1, 4), 'efficiency': (1, 4), 'fortune': (2, 4), 'silk': (1, 1), 'growth': (1, 4), 'void': (1, 1)}
TIER_OVERRIDE = {('silk', 1): 2}  # silk is an Age 2 card even though it has one level


def catalyst(kind, level):
    table = CATALYSTS[kind]
    eligible = [k for k in table if k <= level]
    return table[max(eligible)] if eligible else table[min(table)]


for kind, (lo, hi) in LEVELS.items():
    for level in range(lo, hi + 1):
        tier = TIER_OVERRIDE.get((kind, level), level)
        a, b = catalyst(kind, level)
        prev = f'upgrade_{kind}_{level - 1}' if level > lo else None
        name = f'upgrade_{kind}_{level}'
        if tier == 1:
            shaped(name, ['APB', 'PCP', 'BPA'], {'A': a, 'B': b, 'P': IRON_PLATE, 'C': prev or 'basic_circuit'})
        elif tier == 2:
            shaped(name, ['AXB', 'GCG', 'BRA'], {'A': a, 'B': b, 'X': 'advanced_circuit', 'G': GOLD_PLATE, 'C': prev or DIAMOND, 'R': 'reinforced_casing'})
        elif tier == 3:
            shaped(name, ['AXB', 'GCG', 'BRA'], {'A': a, 'B': b, 'X': 'quantum_circuit', 'G': '#c:rods/blaze', 'C': prev, 'R': 'blazing_casing'})
        else:
            shaped(name, ['AXB', 'GCG', 'BRA'], {'A': a, 'B': b, 'X': 'null_circuit', 'G': 'minecraft:shulker_shell', 'C': prev, 'R': 'null_casing'})
print('core recipes written to', OUT)
