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

# Age 1: Tinker's Bench fits modules into FE tools, FE weapons and Exo armor.
shaped('tinkers_bench', ['PPP', 'GTG', 'W W'], {'P': IRON_PLATE, 'G': 'copper_gear', 'T': 'minecraft:crafting_table', 'W': '#minecraft:planks'})

# Age 1: the Age 0 tools upgrade at a smithing table (kit I + tool + Electric Motor), like every later tier.
# Kit I is cheap on purpose (5 iron, some copper and redstone, no gold): the first powered tools come in the first iron hour.
shaped('tool_upgrade_kit_1', ['PRP', 'PCP'], {'P': IRON_PLATE, 'R': '#c:dusts/redstone', 'C': 'copper_coil'})

# Lamp Rod: copper and glowstone, no parts beyond the first iron hour.
shaped('lamp_rod', ['  G', ' CR', 'C  '], {'G': '#c:dusts/glowstone', 'C': COPPER, 'R': '#c:dusts/redstone'})

# Age 2
shaped('rivet_gun', ['PPP', 'SAR', ' P '], {'P': IRON_PLATE, 'S': 'servo_actuator', 'A': 'advanced_circuit', 'R': 'reinforced_casing'})
shaped('tool_upgrade_kit_2', ['ASA', 'RKR'], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'R': 'reinforced_casing', 'K': 'tool_upgrade_kit_1'})

# Age 3 and 4 kits consume the previous kit
shaped('tool_upgrade_kit_3', ['QPQ', 'BKB'], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'B': 'blazing_casing', 'K': 'tool_upgrade_kit_2'})
shaped('tool_upgrade_kit_4', ['NPN', 'CKC'], {'N': 'null_circuit', 'P': 'plasma_actuator', 'C': 'null_casing', 'K': 'tool_upgrade_kit_3'})

# Smithing upgrades keep energy, mode and toggles (vanilla copies the component patch of the base).
smithing('bore_drill_from_tinkers_hammer', 'tool_upgrade_kit_1', 'tinkers_hammer', 'electric_motor', 'bore_drill')
smithing('chainsaw_from_felling_axe', 'tool_upgrade_kit_1', 'felling_axe', 'electric_motor', 'chainsaw')
smithing('servo_drill_from_bore_drill', 'tool_upgrade_kit_2', 'bore_drill', 'servo_core', 'servo_drill')
smithing('magma_drill_from_servo_drill', 'tool_upgrade_kit_3', 'servo_drill', 'magma_core', 'magma_drill')
smithing('null_drill_from_magma_drill', 'tool_upgrade_kit_4', 'magma_drill', 'antigrav_core', 'null_drill')
smithing('shock_baton_from_gearblade', 'tool_upgrade_kit_1', 'gearblade', 'electric_motor', 'shock_baton')
smithing('arc_blade_from_shock_baton', 'tool_upgrade_kit_3', 'shock_baton', 'magma_core', 'arc_blade')
smithing('null_lance_from_rivet_gun', 'tool_upgrade_kit_4', 'rivet_gun', 'antigrav_core', 'null_lance')
# Tool and weapon modules (Tinker's Bench). Like the Exo modules: one item per level, each level consumes the one below
# plus the next age's circuits. Lifesteal is the late-game capstone: Null Circuits, a Null Casing and a Totem of Undying.
# Lamp Placer: cheap and early (no circuit), like the Lamp Rod.
shaped('lamp_placer_module', ['GCG', 'PRP'], {'G': '#c:dusts/glowstone', 'C': 'copper_coil', 'P': IRON_PLATE, 'R': '#c:dusts/redstone'})
shaped('armor_pierce_module', ['PFP', 'CBC'], {'P': IRON_PLATE, 'F': 'minecraft:flint', 'C': 'copper_coil', 'B': 'basic_circuit'})
shaped('armor_pierce_module_2', ['ADA', ' U '], {'A': 'advanced_circuit', 'D': '#c:gems/diamond', 'U': 'armor_pierce_module'})
shaped('armor_pierce_module_3', ['QNQ', ' U '], {'Q': 'quantum_circuit', 'N': 'minecraft:netherite_scrap', 'U': 'armor_pierce_module_2'})
shaped('ricochet_module', ['SAS', 'PRP'], {'S': 'minecraft:slime_ball', 'A': 'advanced_circuit', 'P': IRON_PLATE, 'R': 'servo_actuator'})
shaped('ricochet_module_2', ['QSQ', ' U '], {'Q': 'quantum_circuit', 'S': 'minecraft:slime_block', 'U': 'ricochet_module'})
shaped('chain_lightning_module', ['QLQ', 'CBC'], {'Q': 'quantum_circuit', 'L': 'minecraft:lightning_rod', 'C': 'copper_coil',
                                                  'B': 'minecraft:blaze_rod'})
shaped('chain_lightning_module_2', ['QPQ', ' U '], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'U': 'chain_lightning_module'})
shaped('chain_lightning_module_3', ['NLN', ' U '], {'N': 'null_circuit', 'L': 'minecraft:lightning_rod', 'U': 'chain_lightning_module_2'})
shaped('lifesteal_module', ['NTN', 'GCG'], {'N': 'null_circuit', 'T': 'minecraft:totem_of_undying', 'G': 'minecraft:ghast_tear',
                                            'C': 'null_casing'})
# Tool modules: they replace enchanting (Efficiency, Fortune, Silk Touch, Unbreaking).
shaped('overclock_module', ['RMR', 'PBP'], {'R': '#c:dusts/redstone', 'M': 'electric_motor', 'P': IRON_PLATE, 'B': 'basic_circuit'})
shaped('overclock_module_2', ['ASA', ' U '], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'U': 'overclock_module'})
shaped('overclock_module_3', ['QPQ', ' U '], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'U': 'overclock_module_2'})
shaped('fortune_module', ['LGL', 'PBP'], {'L': '#c:gems/lapis', 'G': GOLD, 'P': IRON_PLATE, 'B': 'basic_circuit'})
shaped('fortune_module_2', ['ADA', ' U '], {'A': 'advanced_circuit', 'D': '#c:gems/diamond', 'U': 'fortune_module'})
shaped('fortune_module_3', ['QEQ', ' U '], {'Q': 'quantum_circuit', 'E': '#c:gems/emerald', 'U': 'fortune_module_2'})
shaped('silk_touch_module', ['SGS', 'PBP'], {'S': '#c:strings', 'G': GOLD, 'P': IRON_PLATE, 'B': 'basic_circuit'})
shaped('auto_pickup_module', ['PHP', 'CBC'], {'P': IRON_PLATE, 'H': 'minecraft:hopper', 'C': 'copper_coil', 'B': 'basic_circuit'})
shaped('void_filter_module', ['POP', 'CBC'], {'P': IRON_PLATE, 'O': '#c:obsidians', 'C': 'copper_coil', 'B': 'basic_circuit'})
# Weapon modules: they replace Sharpness, Looting and Fire Aspect.
shaped('sharpened_edge_module', ['PSP', 'CBC'], {'P': IRON_PLATE, 'S': 'minecraft:iron_sword', 'C': 'copper_coil', 'B': 'basic_circuit'})
shaped('sharpened_edge_module_2', ['ADA', ' U '], {'A': 'advanced_circuit', 'D': '#c:gems/diamond', 'U': 'sharpened_edge_module'})
shaped('sharpened_edge_module_3', ['QBQ', ' U '], {'Q': 'quantum_circuit', 'B': 'minecraft:blaze_rod', 'U': 'sharpened_edge_module_2'})
shaped('looting_module', ['LEL', 'PBP'], {'L': '#c:gems/lapis', 'E': '#c:gems/emerald', 'P': IRON_PLATE, 'B': 'basic_circuit'})
shaped('looting_module_2', ['AGA', ' U '], {'A': 'advanced_circuit', 'G': '#c:storage_blocks/gold', 'U': 'looting_module'})
shaped('looting_module_3', ['QDQ', ' U '], {'Q': 'quantum_circuit', 'D': '#c:gems/diamond', 'U': 'looting_module_2'})
shaped('thermal_edge_module', ['PFP', 'CBC'], {'P': IRON_PLATE, 'F': 'minecraft:flint_and_steel', 'C': 'copper_coil', 'B': 'basic_circuit'})
print('gear recipes written to', OUT)
