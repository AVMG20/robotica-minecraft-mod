"""Writes the Robotica guide: an advancement tree that walks a new player through the ages and unlocks the recipes of the
next step in the vanilla recipe book, plus the step list the Codex "Next steps" chapter reads.

Run: python3 scripts/data/codex_guide.py
Writes
- data/robotica/advancement/guide/<step>.json
- assets/robotica/codex/guide.json            (ordered steps for the Codex)
- fragments/codex/.../lang/en_us.json          (advancements.robotica.guide.<step>.title / .description)
Every Robotica recipe except the Architect's (owned by the architect module) must be unlocked by exactly one step; the
script fails otherwise, so new recipes get a place in the guide.
"""
import json
import re
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'
ADV = RES / 'data/robotica/advancement/guide'
RECIPES = RES / 'data/robotica/recipe'
GUIDE = RES / 'assets/robotica/codex/guide.json'
LANG = ROOT / 'src/main/fragments/codex/assets/robotica/lang/en_us.json'

ARCHITECT_PREFIXES = ('timberframe_', 'copper_works_', 'steel_lab_', 'null_spire_', 'architect_')


def has(*items):
    return {'trigger': 'minecraft:inventory_changed',
            'conditions': {'items': [{'items': list(f'robotica:{i}' if ':' not in i else i for i in items)}]}}


def placed(*blocks):
    return {'trigger': 'minecraft:placed_block', 'conditions': {'location': [
        {'condition': 'minecraft:location_check',
         'predicate': {'block': {'blocks': [f'robotica:{b}' for b in blocks]}}}]}}


def milestone(name):
    return {'trigger': 'robotica:milestone', 'conditions': {'milestone': name}}


def in_structure(structure):
    return {'trigger': 'minecraft:location', 'conditions': {'player': {'location': {'structures': structure}}}}


def killed(entity):
    return {'trigger': 'minecraft:player_killed_entity', 'conditions': {'entity': {'type': entity}}}


def cards(*kinds):
    return [f'upgrade_{k}' for k in kinds]


# Industry module (ores, alloys, machines, RTG): recipe unlocks per step.
INDUSTRY_THORIUM = ['raw_thorium_block', 'raw_thorium_from_block', 'thorium_block', 'thorium_ingot_from_block',
                    'thorium_ingot_from_smelting', 'thorium_ingot_from_blasting', 'thorium_ingot_from_dust_smelting',
                    'thorium_ingot_from_dust_blasting', 'thorium_ingot_from_ore_smelting', 'thorium_ingot_from_ore_blasting',
                    'thorium_dust_from_hammer', 'graphite_dust_from_hammer', 'grinding_graphite_dust',
                    'thorium_plate_from_hammer', 'metal_press_thorium_plate', 'alloy_smelter_mk1', 'alloying_ferrothorium']
INDUSTRY_FERROTHORIUM = ['ferrothorium_plate_from_hammer', 'metal_press_ferrothorium_plate']
INDUSTRY_AGE2 = ['centrifuge_mk1', 'assembler_mk1', 'centrifuging_depleted_fuel_pellet', 'centrifuging_magma_cream',
                 'centrifuging_glistering_melon_slice']
INDUSTRY_ASSEMBLER = ['rtg', 'alloy_smelter_mk2', 'assembling_thermocouple', 'assembling_thorium_fuel_pellet',
                      'assembling_basic_circuit', 'assembling_electric_motor', 'assembling_advanced_circuit',
                      'assembling_servo_actuator', 'assembling_reinforced_casing']
INDUSTRY_PYROLITE = ['exo_helmet_mk3_from_mk2', 'exo_chestplate_mk3_from_mk2', 'exo_leggings_mk3_from_mk2', 'exo_boots_mk3_from_mk2', 'transfer_coil_elite', 'pyrolite_block', 'pyrolite_shard_from_block', 'pyrolite_shard_from_ore_smelting',
                     'pyrolite_shard_from_ore_blasting', 'pyrolite_dust_from_hammer', 'grinding_pyrolite_dust',
                     'centrifuging_pyrolite_dust', 'alloying_pyrosteel', 'pyrosteel_plate_from_hammer',
                     'metal_press_pyrosteel_plate', 'assembling_enriched_fuel_pellet', 'assembling_superconductor_coil',
                     'assembling_quantum_circuit', 'assembling_plasma_actuator', 'assembling_blazing_casing',
                     'alloy_smelter_mk3', 'centrifuge_mk2', 'assembler_mk2']
INDUSTRY_RESONITE = ['exo_helmet_mk4_from_mk3', 'exo_chestplate_mk4_from_mk3', 'exo_leggings_mk4_from_mk3', 'exo_boots_mk4_from_mk3', 'capacitor_resonant', 'resonant_amplifier', 'resonant_segment', 'resonite_block', 'resonite_crystal_from_block', 'resonite_crystal_from_ore_smelting',
                     'resonite_crystal_from_ore_blasting', 'resonite_dust_from_hammer', 'grinding_resonite_dust',
                     'alloying_resonant_alloy', 'resonant_alloy_plate_from_hammer', 'metal_press_resonant_alloy_plate',
                     'assembling_resonant_lattice', 'assembling_fusion_fuel_pellet', 'assembling_null_circuit',
                     'assembling_null_casing', 'alloy_smelter_mk4', 'centrifuge_mk3', 'centrifuge_mk4', 'assembler_mk3',
                     'assembler_mk4']


# (name, parent, icon, frame, age, title, description, criterion, recipes, xp). A list of criteria means any one of them.
STEPS = [
    ('root', None, 'codex', 'task', 0, 'Robotica',
     'The Codex shows your next steps and every recipe.',
     has('codex', 'minecraft:copper_ingot'),
     ['codex', 'copper_gear', 'tinkers_hammer', 'felling_axe', 'gearblade', 'supply_crate'], 0),
    ('copper_gear', 'root', 'copper_gear', 'task', 0, 'Cogs and Teeth',
     'Craft a Copper Gear.',
     has('copper_gear'),
     ['clockwork_mechanism', 'wooden_chassis', 'mainspring', 'winding_crank', 'stumpy', 'sprout'], 5),
    ('robot_built', 'copper_gear', 'stumpy', 'task', 0, 'Some Assembly Required',
     'Craft Stumpy or Sprout.',
     has('stumpy', 'sprout'), [], 5),
    ('mainspring', 'robot_built', 'mainspring', 'task', 0, 'Wind It Up',
     'Wind a Mainspring at a Winding Crank.',
     milestone('wind_spring'), [], 5),
    ('first_robot', 'mainspring', 'stumpy', 'task', 0, 'Hello, Stumpy',
     'Place Stumpy by trees or Sprout by crops, next to a chest.',
     placed('stumpy', 'sprout'), [], 5),
    ('robot_working', 'first_robot', 'sprout', 'goal', 0, 'Hands Free',
     'Right-click your robot with the wound Mainspring.',
     milestone('robot_working'), [], 20),
    ('hammer', 'copper_gear', 'tinkers_hammer', 'task', 0, 'Hammer Time',
     "Craft a Tinker's Hammer.",
     has('tinkers_hammer'),
     ['iron_plate_from_hammer', 'copper_plate_from_hammer', 'gold_plate_from_hammer'], 5),
    ('first_iron', 'hammer', 'minecraft:iron_ingot', 'goal', 1, 'First Iron',
     'Smelt iron: it makes a generator, a battery and power tools.',
     has('minecraft:iron_ingot'),
     ['combustion_generator', 'charger', 'copper_cell', 'copper_coil', 'iron_casing', 'electric_motor', 'basic_circuit',
      'tool_upgrade_kit_1', 'bore_drill_from_tinkers_hammer', 'chainsaw_from_felling_axe', 'tesla_coil_1', 'tesla_linker',
      'mining_drone', 'hauler_drone', 'signal_flare', 'tinkers_bench', 'item_pipe', 'lamp_placer_module'], 10),
    ('mining_drone', 'first_iron', 'mining_drone', 'task', 1, 'Dig Buddy',
     'Craft a Mining Drone Mk1.',
     has('mining_drone'), [], 10),
    ('hauler_drone', 'first_iron', 'hauler_drone', 'task', 1, 'Airlift',
     'Craft a Hauler Drone Mk1 and pick up a mob with it.',
     has('hauler_drone'), [], 5),
    ('generator', 'first_iron', 'combustion_generator', 'task', 1, 'Fire It Up',
     'Place a Combustion Generator.',
     placed('combustion_generator', 'solar_panel_mk1'), [], 5),
    ('charger', 'generator', 'charger', 'task', 1, 'Topped Up',
     'Place a Charger next to the generator.',
     placed('charger'), ['lamp_rod'], 5),
    ('copper_cell', 'charger', 'copper_cell', 'task', 1, 'Rechargeable',
     'Craft a Copper Cell.',
     has('copper_cell'), [], 5),
    ('tesla', 'generator', 'tesla_coil_1', 'task', 1, 'Plugged In',
     'Put a Tesla Coil on your generator and link a machine to it.',
     placed('tesla_coil_1', 'tesla_coil_2', 'tesla_coil_3', 'tesla_coil_4', 'tesla_coil_5'), [], 5),
    ('power_tool', 'first_iron', 'bore_drill', 'task', 1, 'Power Tools',
     'Smith your hammer into a Bore Drill or your axe into a Chainsaw.',
     has('bore_drill', 'chainsaw'), [], 10),
    ('basic_circuit', 'first_iron', 'basic_circuit', 'goal', 1, 'Age 1: Wired',
     'Craft a Basic Circuit. It opens most of Age 1.',
     has('basic_circuit'),
     ['solar_panel_mk1', 'tesla_coil_2', 'accumulator_1', 'metal_press', 'metal_press_iron_plate',
      'metal_press_copper_plate', 'metal_press_gold_plate', 'shock_baton_from_gearblade', 'farm_kit_mk2', 'warp_pad',
      'recall_remote', 'storage_terminal', 'storage_expansion_mk1', 'sentry_drone', 'courier_drone', 'courier_remote', 'exo_helmet_mk1', 'exo_chestplate_mk1', 'exo_leggings_mk1', 'exo_boots_mk1', 'night_vision_module', 'step_assist_module', 'spring_heels_module', 'servo_stride_module_1',
      'grinder_mk1', 'electric_furnace_mk1', 'bank_casing', 'bank_glass', 'bank_controller', 'bank_port', 'capacitor_copper', 'transfer_coil_basic', 'auto_feeder_module', 'kinetic_generator_module', 'magnet_module', 'fall_dampener_module', 'capacitor_plating_module', 'item_pipe_mk2',
      'armor_pierce_module', 'overclock_module', 'fortune_module', 'silk_touch_module', 'auto_pickup_module',
      'void_filter_module', 'sharpened_edge_module', 'looting_module', 'thermal_edge_module'] + cards('speed', 'efficiency', 'growth', 'void', 'height') + ['rancher'], 20),
    ('exo', 'basic_circuit', 'exo_chestplate_mk1', 'task', 1, 'Suit Up',
     'Craft an Exo-Frame piece.',
     has('exo_helmet_mk1', 'exo_chestplate_mk1', 'exo_leggings_mk1', 'exo_boots_mk1'), [], 10),
    ('drones', 'basic_circuit', 'sentry_drone', 'task', 1, 'Air Support',
     'Craft a Sentry or Courier Drone Mk1.',
     has('sentry_drone', 'courier_drone'), [], 10),
    ('rancher', 'basic_circuit', 'rancher', 'task', 1, 'Home on the Range',
     'Craft a Rancher Mk1 and place it in an animal pen.',
     has('rancher', 'rancher_mk2'), ['rancher_mk2'], 10),
    ('grinder', 'basic_circuit', 'grinder_mk1', 'task', 1, 'Grind It Down',
     'Place a Grinder.',
     placed('grinder_mk1', 'grinder_mk2', 'grinder_mk3', 'grinder_mk4'),
     ['iron_grinding_balls'] + [f'{m}_ingot_from_{k}_{m}_dust' for m in ('iron', 'gold', 'copper') for k in ('smelting', 'blasting')]
     + [f'grinding/{r}' for m in ('iron', 'gold', 'copper') for r in (f'{m}_ore_to_dust', f'raw_{m}_to_dust', f'{m}_ingot_to_dust')]
     + [f'grinding/{r}' for r in ('cobblestone_to_gravel', 'gravel_to_sand', 'blaze_rod_to_powder', 'bone_to_bone_meal',
                                  'glowstone_to_dust', 'wool_to_string', 'redstone_ore', 'lapis_ore', 'coal_ore')], 10),
    ('metal_press', 'basic_circuit', 'metal_press', 'task', 1, 'Press Ahead',
     'Place a Metal Press.',
     placed('metal_press'), [], 5),
    ('storage', 'basic_circuit', 'storage_terminal', 'task', 1, 'Big Chest',
     'Place a Storage Terminal.',
     placed('storage_terminal'), [], 5),
    ('capacitor_bank', 'basic_circuit', 'bank_controller', 'task', 1, 'Bank on It',
     'Build a Capacitor Bank.',
     milestone('bank_formed'), [], 15),
    ('upgrade_card', 'basic_circuit', 'upgrade_speed', 'task', 1, 'Plug and Play',
     'Craft an upgrade card and right-click a robot or machine with it.',
     has(*cards('speed', 'efficiency', 'growth', 'void')), [], 5),
    ('farm_kit', 'upgrade_card', 'farm_kit_mk2', 'task', 1, 'Bigger Fields',
     'Upgrade a robot with a Farm Kit Mk2.',
     milestone('farm_kit'), [], 10),
    ('warp', 'basic_circuit', 'warp_pad', 'task', 1, 'Home Run',
     'Place and power two Warp Pads, then travel between them.',
     milestone('warp'), [], 10),
    ('thorium', 'basic_circuit', 'raw_thorium', 'task', 1, 'Green Glow',
     'Mine Thorium Ore (Y -48 to 32).',
     has('raw_thorium', 'thorium_ore', 'deepslate_thorium_ore', 'thorium_ingot'), INDUSTRY_THORIUM, 10),
    ('ferrothorium', 'thorium', 'ferrothorium_ingot', 'task', 2, 'Alloyed',
     'Make Ferrothorium in an Alloy Smelter.',
     has('ferrothorium_ingot'), INDUSTRY_FERROTHORIUM, 10),
    ('diamonds', 'basic_circuit', 'minecraft:diamond', 'goal', 2, 'Diamonds!',
     'Find diamonds: they make the Excavator Mk1 and the Age 2 parts.',
     has('minecraft:diamond'), ['excavator', 'reinforced_casing', 'advanced_circuit'], 20),
    ('excavator', 'diamonds', 'excavator', 'task', 2, 'Dig Deep',
     'Place an Excavator with a chest next to it.',
     placed('excavator', 'excavator_mk2', 'excavator_mk3', 'excavator_mk4'), ['excavator_mk2'], 10),
    ('survey_rig', 'excavator', 'survey_rig', 'task', 2, 'No Holes Barred',
     'Place a Survey Rig.',
     placed('survey_rig', 'survey_rig_mk2', 'survey_rig_mk3', 'survey_rig_mk4'), ['survey_rig_mk2'], 20),
    ('age2', 'diamonds', 'advanced_circuit', 'goal', 2, 'Age 2: Servo',
     'Craft an Advanced Circuit or a Reinforced Casing.',
     has('advanced_circuit', 'reinforced_casing'),
     ['servo_actuator', 'grinder_mk2', 'electric_furnace_mk2', 'ferrothorium_grinding_balls', 'storage_expansion_mk2', 'redstone_cell', 'temp_servo_core', 'solar_panel_mk2', 'accumulator_2', 'tesla_coil_3', 'item_pipe_mk3', 'rivet_gun',
      'tool_upgrade_kit_2', 'farm_kit_mk3', 'essence_vial', 'replicator_frame', 'replicator_glass',
      'replicator_controller', 'survey_rig', 'mining_drone_mk2', 'sentry_drone_mk2', 'courier_drone_mk2', 'hauler_drone_mk2', 'exo_helmet_mk2_from_mk1', 'exo_chestplate_mk2_from_mk1', 'exo_leggings_mk2_from_mk1', 'exo_boots_mk2_from_mk1', 'rebreather_module', 'rebreather_module_from_prismarine', 'jet_assist_module', 'servo_stride_module_2', 'robot_hud_module',
      'capacitor_redstone', 'transfer_coil_advanced', 'reactor_casing', 'reactor_glass', 'reactor_controller', 'reactor_power_port', 'reactor_access_port', 'flux_amplifier', 'graphite_damper', 'spire_base', 'spire_crown', 'night_vision_module_2', 'solar_weave_module', 'sonar_pulse_module', 'med_injector_module', 'hydro_fins_module', 'spring_heels_module_2',
      'fall_dampener_module_2', 'magnet_module_2', 'capacitor_plating_module_2', 'power_regulator_module', 'colossus_altar',
      'armor_pierce_module_2', 'ricochet_module', 'overclock_module_2', 'fortune_module_2', 'sharpened_edge_module_2',
      'looting_module_2'] + cards('range', 'fortune', 'silk') + INDUSTRY_AGE2 + ['wireless_charger'], 30),
    ('assembler', 'age2', 'assembler_mk1', 'task', 2, 'Machine Made',
     'Place an Assembler.',
     placed('assembler_mk1', 'assembler_mk2', 'assembler_mk3', 'assembler_mk4'), INDUSTRY_ASSEMBLER, 15),
    ('rtg', 'assembler', 'rtg', 'task', 2, 'Slow Burn',
     'Place a Radioisotope Generator and fuel it.',
     placed('rtg'), [], 15),
    ('tesla_spire', 'assembler', 'spire_crown', 'goal', 2, 'Ride the Lightning',
     'Build a Tesla Spire under open sky.',
     milestone('spire_formed'), [], 30),
    ('core_reactor', 'age2', 'reactor_controller', 'goal', 2, 'Heart of the Machine',
     'Run a Core Reactor with a core inside.',
     milestone('core_reactor_running'), [], 40),
    ('foundry', 'age2', 'signal_flare', 'task', 2, 'Signs of Scrap',
     'Find a Rusted Foundry (copper ruins in plains, deserts, badlands) or craft a Signal Flare.',
     [in_structure('robotica:rusted_foundry'), has('signal_flare')], [], 10),
    ('colossus', 'foundry', 'colossus_altar', 'goal', 2, 'Scrap Heap',
     'Summon and beat the Scrap Colossus.',
     killed('robotica:scrap_colossus'), [], 50),
    ('servo_core', 'colossus', 'servo_core', 'goal', 2, 'Core Memory',
     'Get a Servo Core: it makes the Servo Drill and the Mob Replicator.',
     has('servo_core'), ['servo_drill_from_bore_drill'], 30),
    ('servo_drill', 'servo_core', 'servo_drill', 'task', 2, 'Vein Glory',
     'Smith your Bore Drill into a Servo Drill.',
     has('servo_drill'), [], 10),
    ('vial', 'age2', 'essence_vial', 'task', 2, 'Sample Size',
     'Fill an Essence Vial.',
     milestone('vial_complete'), [], 10),
    ('replicator', 'servo_core', 'replicator_controller', 'goal', 2, 'Copy That',
     'Build the Mob Replicator.',
     milestone('replicator_formed'), [], 30),
    ('age3', 'servo_core', 'quantum_circuit', 'goal', 3, 'Age 3: Deep',
     'Craft a Quantum Circuit.',
     has('quantum_circuit', 'plasma_actuator'),
     ['blazing_casing', 'quantum_circuit', 'plasma_actuator', 'solar_panel_mk3', 'grinder_mk3', 'electric_furnace_mk3', 'pyrosteel_grinding_balls', 'storage_expansion_mk3', 'temp_magma_core', 'accumulator_3', 'tesla_coil_4', 'item_pipe_mk4',
      'tool_upgrade_kit_3', 'rift_upgrade', 'rift_remote', 'servo_stride_module_3', 'kinetic_shield_module', 'cryo_coolant', 'pyro_amplifier', 'night_vision_module_3', 'sonar_pulse_module_2', 'jet_assist_module_2', 'med_injector_module_2', 'hazard_seal_module',
      'dash_thrusters_module', 'spring_heels_module_3', 'fall_dampener_module_3', 'magnet_module_3', 'capacitor_plating_module_3',
      'power_regulator_module_2', 'mining_drone_mk3', 'excavator_mk3', 'survey_rig_mk3', 'armor_pierce_module_3', 'ricochet_module_2',
      'chain_lightning_module', 'chain_lightning_module_2', 'overclock_module_3', 'fortune_module_3', 'sharpened_edge_module_3',
      'looting_module_3', 'ignition_charge', 'forge_altar'], 50),
    ('pyrolite', 'age3', 'pyrolite_shard', 'task', 3, 'Fire Stone',
     'Mine Pyrolite Ore in the Nether, most of it in basalt deltas.',
     has('pyrolite_shard', 'pyrolite_ore'), INDUSTRY_PYROLITE, 20),
    ('cinder_forge', 'age3', 'ignition_charge', 'task', 3, 'Into the Fire',
     'Find a Cinder Forge (blackstone ruins in Nether caves) or craft an Ignition Charge.',
     [in_structure('robotica:cinder_forge'), has('ignition_charge')], [], 20),
    ('tyrant', 'cinder_forge', 'forge_altar', 'goal', 3, 'Quenched',
     'Summon and beat the Forge Tyrant.',
     killed('robotica:forge_tyrant'), [], 80),
    ('magma_core', 'tyrant', 'magma_core', 'goal', 3, 'Hot Core',
     'Get a Magma Core: Magma Drill, Arc Blade, Rift Upgrade, Age 3 mobs in the replicator.',
     has('magma_core'), ['magma_drill_from_servo_drill', 'arc_blade_from_shock_baton'], 50),
    ('magma_drill', 'magma_core', 'magma_drill', 'task', 3, 'Smelt as You Go',
     'Smith a Magma Drill. Turn on auto-smelt in its settings.',
     has('magma_drill'), [], 20),
    ('exo_mk3', 'magma_core', 'exo_chestplate_mk3', 'task', 3, 'Blazing Frame',
     'Smith an Exo piece to Mk3. Flight needs a Mk3 chestplate.',
     has('exo_helmet_mk3', 'exo_chestplate_mk3', 'exo_leggings_mk3', 'exo_boots_mk3'), [], 30),
    ('age4', 'magma_core', 'null_circuit', 'goal', 4, 'Age 4: Antigrav',
     'Craft a Null Circuit.',
     has('null_circuit'),
     ['null_casing', 'null_circuit', 'ender_cell', 'solar_panel_mk4', 'grinder_mk4', 'electric_furnace_mk4', 'resonant_grinding_balls', 'temp_antigrav_core', 'tool_upgrade_kit_4', 'tesla_coil_5', 'collider_controller', 'accelerator_segment',
      'farm_kit_mk4', 'gate_controller', 'linking_card', 'capacitor_ender', 'sonar_pulse_module_3', 'jet_assist_module_3', 'med_injector_module_3',
      'power_regulator_module_3', 'excavator_mk4', 'survey_rig_mk4', 'chain_lightning_module_3', 'lifesteal_module'], 80),
    ('resonite', 'age4', 'resonite_crystal', 'task', 4, 'Echoes',
     'Mine Resonite Ore on the outer End islands.',
     has('resonite_crystal', 'resonite_ore'), INDUSTRY_RESONITE, 30),
    ('antigrav_core', 'age4', 'antigrav_core', 'goal', 4, 'Weightless',
     'Get an Antigrav Core: Null Drill, Null Lance, Portal Projectors.',
     has('antigrav_core'), ['null_drill_from_magma_drill', 'null_lance_from_rivet_gun', 'flight_module', 'kinetic_shield_module_2',
                           'kinetic_shield_module_3'], 80),
    ('null_drill', 'antigrav_core', 'null_drill', 'challenge', 4, 'Big Holes',
     'Smith a Null Drill and dig a 12x12x12 cube in one swing.',
     has('null_drill'), [], 100),
    ('ring_collider', 'age4', 'collider_controller', 'challenge', 4, 'Smash Hit',
     'Light the beam of a Ring Collider.',
     milestone('collider_running'), ['assembling_null_circuit_from_strange_matter', 'assembling_antigrav_core_from_strange_matter'], 100),
    ('flight', 'antigrav_core', 'flight_module', 'challenge', 4, 'Lift Off',
     'Put a Flight Module in an Exo chestplate and press {key:key.robotica.exo.toggle_flight} to fly.',
     has('flight_module'), [], 50),
    ('exo_mk4', 'antigrav_core', 'exo_chestplate_mk4', 'challenge', 4, 'Null Frame',
     'Smith an Exo piece to Mk4.',
     has('exo_helmet_mk4', 'exo_chestplate_mk4', 'exo_leggings_mk4', 'exo_boots_mk4'), [], 80),
    ('portal', 'antigrav_core', 'gate_controller', 'challenge', 4, 'Through the Lens',
     'Link two Portal Projectors with a Linking Card and step through the portal.',
     milestone('portal'), [], 100),
]


def main():
    names = [s[0] for s in STEPS]
    assert len(names) == len(set(names)), 'duplicate step'
    recipe_ids = {p.relative_to(RECIPES).with_suffix('').as_posix() for p in RECIPES.rglob('*.json')}
    unlocked = {}
    for name, parent, icon, frame, age, title, desc, crit, recipes, xp in STEPS:
        assert parent is None or parent in names[:names.index(name)], f'{name}: parent {parent} must come first'
        for r in recipes:
            if r not in recipe_ids:
                sys.exit(f'{name}: unknown recipe {r}')
            if r in unlocked:
                sys.exit(f'{r} unlocked by {unlocked[r]} and {name}')
            unlocked[r] = name
    missing = sorted(r for r in recipe_ids if r not in unlocked and not r.startswith(ARCHITECT_PREFIXES))
    if missing:
        sys.exit('recipes without a guide step: ' + ', '.join(missing))

    for old in ADV.glob('*.json'):
        old.unlink()
    ADV.mkdir(parents=True, exist_ok=True)
    lang = json.loads(LANG.read_text()) if LANG.exists() else {}
    lang = {k: v for k, v in lang.items() if not k.startswith('advancements.robotica.guide.')}
    guide = []
    for name, parent, icon, frame, age, title, desc, crit, recipes, xp in STEPS:
        key = f'advancements.robotica.guide.{name}'
        icon = icon if ':' in icon else f'robotica:{icon}'
        # {key:<key mapping>} in a description shows the player's binding for that key mapping
        keys = re.findall(r'\{key:([a-z0-9_.]+)\}', desc)
        desc = re.sub(r'\{key:[a-z0-9_.]+\}', '%s', desc)
        description = {'translate': key + '.description'}
        if keys:
            description['with'] = [{'keybind': k} for k in keys]
        display = {'icon': {'id': icon}, 'title': {'translate': key + '.title'},
                   'description': description, 'frame': frame,
                   'show_toast': parent is not None, 'announce_to_chat': False, 'hidden': False}
        if parent is None:
            display['background'] = 'minecraft:textures/block/cut_copper.png'
        if isinstance(crit, list):
            criteria = {'done' if i == 0 else f'done_{i}': c for i, c in enumerate(crit)}
            adv = {'display': display, 'criteria': criteria, 'requirements': [list(criteria)]}
        else:
            adv = {'display': display, 'criteria': {'done': crit}, 'requirements': [['done']]}
        if parent is not None:
            adv = {'parent': f'robotica:guide/{parent}', **adv}
        rewards = {}
        if recipes:
            rewards['recipes'] = [f'robotica:{r}' for r in recipes]
        if xp:
            rewards['experience'] = xp
        if rewards:
            adv['rewards'] = rewards
        (ADV / f'{name}.json').write_text(json.dumps(adv, indent=2) + '\n')
        lang[key + '.title'] = title
        lang[key + '.description'] = desc
        step = {'id': f'robotica:guide/{name}', 'parent': f'robotica:guide/{parent}' if parent else None,
                'icon': icon, 'age': age}
        if keys:
            step['keys'] = keys
        guide.append(step)
    GUIDE.write_text(json.dumps({'steps': guide}, indent=2) + '\n')
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
    print(f'{len(STEPS)} guide steps, {len(unlocked)} recipes unlocked')


if __name__ == '__main__':
    main()
