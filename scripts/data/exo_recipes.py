"""Writes the Exo-Frame crafting and smithing recipes (armor Mk1/Mk2 and the modules).
Run: python3 scripts/data/exo_recipes.py   (overwrites data/robotica/recipe/<name>.json for exo items only)

Ladder: Mk1 (Age 1) = iron armor piece + iron casing, electric motor, copper coil. Mk2/Mk3/Mk4 = smithing, each consumes
the piece of the mark below (energy, modules, switches and the core are kept): template Reinforced / Blazing / Null Casing,
addition Servo Actuator / Plasma Actuator / Null Circuit. Modules follow the ages of their parts; every level of a module
consumes the level below (Age 2 levels: Advanced Circuits, Age 3: Quantum Circuits, Age 4: Null Circuits).
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


def level2(kind, center, first=None):
    """Level II (Age 2): two Advanced Circuits and an Age 2 flavour part around level I."""
    first = first or f'{kind}_module'
    shaped(f'{kind}_module_2', ['ACA', ' U '], {'A': 'advanced_circuit', 'C': center, 'U': first})


def level3(kind, center, prev=None, result=None):
    """Age 3 level (usually III): two Quantum Circuits and an Age 3 part around the previous level."""
    shaped(result or f'{kind}_module_3', ['QCQ', ' U '], {'Q': 'quantum_circuit', 'C': center, 'U': prev or f'{kind}_module_2'})


def level_null(name, prev, center, extra=None):
    """Age 4 levels: two Null Circuits and an Age 4 part (plus shulker shells when given) around the previous level."""
    if extra:
        shaped(name, ['NCN', 'SUS'], {'N': 'null_circuit', 'C': center, 'U': prev, 'S': extra})
    else:
        shaped(name, ['NCN', ' U '], {'N': 'null_circuit', 'C': center, 'U': prev})


# ---- Mk1 armor (Age 1): iron armor piece + casing, motors, coils, circuits
shaped('exo_helmet_mk1', ['CIC', 'BHB'], {'C': 'copper_coil', 'I': 'iron_casing', 'B': 'basic_circuit', 'H': 'minecraft:iron_helmet'})
shaped('exo_chestplate_mk1', ['CIC', 'MHM', 'ICI'], {'C': 'copper_coil', 'I': 'iron_casing', 'M': 'electric_motor', 'H': 'minecraft:iron_chestplate'})
shaped('exo_leggings_mk1', ['MIM', 'CHC'], {'M': 'electric_motor', 'I': 'iron_casing', 'C': 'copper_coil', 'H': 'minecraft:iron_leggings'})
shaped('exo_boots_mk1', ['MHM', 'CIC'], {'M': 'electric_motor', 'I': 'iron_casing', 'C': 'copper_coil', 'H': 'minecraft:iron_boots'})

# ---- Mk2 (Age 2), Mk3 (Age 3), Mk4 (Age 4): smithing keeps energy, installed modules, switches and the core
for piece in ('helmet', 'chestplate', 'leggings', 'boots'):
    smithing(f'exo_{piece}_mk2_from_mk1', 'reinforced_casing', f'exo_{piece}_mk1', 'servo_actuator', f'exo_{piece}_mk2')
    smithing(f'exo_{piece}_mk3_from_mk2', 'blazing_casing', f'exo_{piece}_mk2', 'superconductor_coil', f'exo_{piece}_mk3')
    smithing(f'exo_{piece}_mk4_from_mk3', 'null_casing', f'exo_{piece}_mk3', 'resonant_lattice', f'exo_{piece}_mk4')

# ---- Helmet modules
shapeless('night_vision_module', ['basic_circuit', 'copper_coil', 'minecraft:golden_carrot', PLATE])
level2('night_vision', 'minecraft:spider_eye')
level3('night_vision', 'minecraft:blaze_rod')
shapeless('rebreather_module', ['advanced_circuit', 'minecraft:heart_of_the_sea', 'copper_coil', PLATE])
shapeless('rebreather_module_from_prismarine', ['advanced_circuit', 'minecraft:prismarine_crystals', 'minecraft:prismarine_crystals',
                                                'minecraft:prismarine_crystals', 'copper_coil'], 'rebreather_module')
shapeless('robot_hud_module', ['advanced_circuit', 'minecraft:glass_pane', 'minecraft:redstone', 'copper_coil'])
shapeless('auto_feeder_module', ['basic_circuit', 'electric_motor', 'minecraft:hopper', PLATE])
shaped('solar_weave_module', ['SAS', 'CPC'], {'S': 'solar_panel_mk1', 'A': 'advanced_circuit', 'C': 'copper_coil', 'P': PLATE})
shaped('sonar_pulse_module', ['ZAZ', 'CNC'], {'Z': 'minecraft:amethyst_shard', 'A': 'advanced_circuit', 'C': 'copper_coil', 'N': 'minecraft:note_block'})
# Sonar levels run one age later: II is Age 3, III Age 4.
level3('sonar_pulse', 'minecraft:prismarine_crystals', 'sonar_pulse_module', 'sonar_pulse_module_2')
level_null('sonar_pulse_module_3', 'sonar_pulse_module_2', 'minecraft:ender_eye')

# ---- Chest modules
shaped('jet_assist_module', ['FAF', 'PSP'], {'F': 'minecraft:feather', 'A': 'advanced_circuit', 'S': 'servo_actuator', 'P': PLATE})
shaped('jet_assist_module_2', ['QCQ', ' U '], {'Q': 'quantum_circuit', 'C': 'plasma_actuator', 'U': 'jet_assist_module'})
level_null('jet_assist_module_3', 'jet_assist_module_2', 'minecraft:shulker_shell')
shaped('flight_module', ['NCN', 'PJP'], {'N': 'null_circuit', 'C': 'antigrav_core', 'P': 'plasma_actuator', 'J': 'jet_assist_module'})
shaped('kinetic_shield_module', ['GQG', 'IAI'], {'G': GOLD_PLATE, 'Q': 'quantum_circuit', 'I': PLATE, 'A': 'plasma_actuator'})
level_null('kinetic_shield_module_2', 'kinetic_shield_module', 'null_casing')
level_null('kinetic_shield_module_3', 'kinetic_shield_module_2', 'null_casing', 'minecraft:shulker_shell')
shaped('med_injector_module', ['GAG', 'CBC'], {'G': 'minecraft:glistering_melon_slice', 'A': 'advanced_circuit', 'C': 'copper_coil',
                                                'B': 'minecraft:glass_bottle'})
level3('med_injector', 'minecraft:golden_apple', 'med_injector_module', 'med_injector_module_2')
level_null('med_injector_module_3', 'med_injector_module_2', 'minecraft:ghast_tear')
shaped('hazard_seal_module', ['GQG', 'PMP'], {'G': 'minecraft:glass', 'Q': 'quantum_circuit', 'P': PLATE, 'M': 'minecraft:milk_bucket'})

# ---- Leg modules
shaped('servo_stride_module_1', ['CMC', 'PBP'], {'C': 'copper_coil', 'M': 'electric_motor', 'B': 'basic_circuit', 'P': PLATE})
shaped('servo_stride_module_2', ['ASA', ' U '], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'U': 'servo_stride_module_1'})
shaped('servo_stride_module_3', ['QPQ', ' U '], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'U': 'servo_stride_module_2'})
shaped('kinetic_generator_module', ['PMP', 'CGC'], {'P': PLATE, 'M': 'electric_motor', 'C': 'copper_coil', 'G': 'copper_gear'})
shaped('dash_thrusters_module', ['BPB', 'FCF'], {'B': 'minecraft:blaze_powder', 'P': 'plasma_actuator', 'F': 'minecraft:fire_charge',
                                                  'C': 'copper_coil'})

# ---- Boot modules
shaped('step_assist_module', ['PIP', 'C C'], {'P': PLATE, 'I': 'minecraft:piston', 'C': 'copper_coil'})
shapeless('spring_heels_module', ['minecraft:slime_block', 'electric_motor', 'copper_coil', 'basic_circuit'])
level2('spring_heels', 'minecraft:slime_block')
level3('spring_heels', 'minecraft:slime_block')
shapeless('fall_dampener_module', ['minecraft:slime_block', 'minecraft:slime_block', 'electric_motor', 'basic_circuit'])
level2('fall_dampener', 'servo_actuator')
level3('fall_dampener', 'minecraft:phantom_membrane')
shaped('magnet_module', ['ICI', 'RBR'], {'I': '#c:ingots/iron', 'C': 'copper_coil', 'R': 'minecraft:redstone', 'B': 'basic_circuit'})
level2('magnet', 'minecraft:iron_block')
level3('magnet', 'minecraft:lodestone')
shaped('hydro_fins_module', ['SAS', 'PCP'], {'S': 'minecraft:prismarine_shard', 'A': 'advanced_circuit', 'P': PLATE, 'C': 'copper_coil'})

# ---- Any piece
shaped('capacitor_plating_module', ['PCP', 'RBR'], {'P': PLATE, 'C': 'copper_cell', 'R': 'minecraft:redstone', 'B': 'basic_circuit'})
level2('capacitor_plating', 'redstone_cell')
level3('capacitor_plating', 'redstone_cell')
shaped('power_regulator_module', ['GAG', 'CRC'], {'G': GOLD_PLATE, 'A': 'advanced_circuit', 'C': 'copper_coil', 'R': 'minecraft:comparator'})
shaped('power_regulator_module_2', ['QRQ', ' U '], {'Q': 'quantum_circuit', 'R': 'minecraft:redstone_block', 'U': 'power_regulator_module'})
level_null('power_regulator_module_3', 'power_regulator_module_2', 'minecraft:redstone_block')
print('exo recipes written to', OUT)
