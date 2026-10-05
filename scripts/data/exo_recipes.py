"""Writes the Exo-Frame crafting and smithing recipes (armor Mk1/Mk2 and the modules).
Run: python3 scripts/data/exo_recipes.py   (overwrites data/robotica/recipe/<name>.json for exo items only)

Ladder: Mk1 (Age 1) = iron armor piece + iron casing, electric motor, copper coil. Mk2 = smithing, consumes the Mk1 piece
(energy and modules are kept) plus a reinforced casing as template and a servo actuator. Modules follow the ages of their parts;
every upgrade module consumes the one below it.
"""
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


def shapeless(name, ingredients, result=None, category='equipment'):
    write(name, {'type': 'minecraft:crafting_shapeless', 'category': category,
                 'ingredients': [ing(i) for i in ingredients],
                 'result': {'id': f'robotica:{result or name}', 'count': 1}})


def smithing(name, template, base, addition, result):
    write(name, {'type': 'minecraft:smithing_transform', 'template': ing(template), 'base': ing(base),
                 'addition': ing(addition), 'result': {'id': f'robotica:{result}', 'count': 1}})


PLATE = '#c:plates/iron'
GOLD_PLATE = '#c:plates/gold'

# ---- Mk1 armor (Age 1): iron armor piece + casing, motors, coils, circuits
shaped('exo_helmet_mk1', ['CIC', 'BHB'], {'C': 'copper_coil', 'I': 'iron_casing', 'B': 'basic_circuit', 'H': 'minecraft:iron_helmet'})
shaped('exo_chestplate_mk1', ['CIC', 'MHM', 'ICI'], {'C': 'copper_coil', 'I': 'iron_casing', 'M': 'electric_motor', 'H': 'minecraft:iron_chestplate'})
shaped('exo_leggings_mk1', ['MIM', 'CHC'], {'M': 'electric_motor', 'I': 'iron_casing', 'C': 'copper_coil', 'H': 'minecraft:iron_leggings'})
shaped('exo_boots_mk1', ['MHM', 'CIC'], {'M': 'electric_motor', 'I': 'iron_casing', 'C': 'copper_coil', 'H': 'minecraft:iron_boots'})

# ---- Mk2 (Age 2): smithing keeps energy and installed modules
for piece in ('helmet', 'chestplate', 'leggings', 'boots'):
    smithing(f'exo_{piece}_mk2_from_mk1', 'reinforced_casing', f'exo_{piece}_mk1', 'servo_actuator', f'exo_{piece}_mk2')

# ---- Helmet modules
shapeless('night_vision_module', ['basic_circuit', 'copper_coil', 'minecraft:golden_carrot', PLATE])
shapeless('rebreather_module', ['advanced_circuit', 'minecraft:heart_of_the_sea', 'copper_coil', PLATE])
shapeless('rebreather_module_from_prismarine', ['advanced_circuit', 'minecraft:prismarine_crystals', 'minecraft:prismarine_crystals',
                                                'minecraft:prismarine_crystals', 'copper_coil'], 'rebreather_module')
shapeless('robot_hud_module', ['advanced_circuit', 'minecraft:glass_pane', 'minecraft:redstone', 'copper_coil'])

# ---- Chest modules
shaped('jet_assist_module', ['FAF', 'PSP'], {'F': 'minecraft:feather', 'A': 'advanced_circuit', 'S': 'servo_actuator', 'P': PLATE})
shaped('kinetic_shield_module', ['GQG', 'IAI'], {'G': GOLD_PLATE, 'Q': 'quantum_circuit', 'I': PLATE, 'A': 'plasma_actuator'})
shaped('flight_module', ['NCN', 'PJP'], {'N': 'null_circuit', 'C': 'antigrav_core', 'P': 'plasma_actuator', 'J': 'jet_assist_module'})

# ---- Leg modules
shaped('servo_stride_module_1', ['CMC', 'PBP'], {'C': 'copper_coil', 'M': 'electric_motor', 'B': 'basic_circuit', 'P': PLATE})
shaped('servo_stride_module_2', ['ASA', ' U '], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'U': 'servo_stride_module_1'})
shaped('servo_stride_module_3', ['QPQ', ' U '], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'U': 'servo_stride_module_2'})
shaped('step_assist_module', ['IPI', 'CMC'], {'I': PLATE, 'P': 'minecraft:piston', 'C': 'copper_coil', 'M': 'electric_motor'})

# ---- Boot modules
shapeless('spring_heels_module', ['minecraft:slime_block', 'electric_motor', 'copper_coil', 'basic_circuit'])
shapeless('fall_dampener_module', ['advanced_circuit', 'servo_actuator', 'minecraft:slime_block', 'copper_coil'])
shaped('magnet_module', ['ICI', 'RAR'], {'I': '#c:ingots/iron', 'C': 'copper_coil', 'R': 'minecraft:redstone', 'A': 'advanced_circuit'})
print('exo recipes written to', OUT)
