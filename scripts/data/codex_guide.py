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


# (name, parent, icon, frame, age, title, description, criterion, recipes, xp). A list of criteria means any one of them.
STEPS = [
    ('root', None, 'codex', 'task', 0, 'Robotica',
     'Robots do the boring work for you. Start with copper, wood and cobblestone. Your Codex has the recipes.',
     has('codex', 'minecraft:copper_ingot'),
     ['codex', 'copper_gear', 'tinkers_hammer', 'felling_axe', 'gearblade', 'supply_crate'], 0),
    ('copper_gear', 'root', 'copper_gear', 'task', 0, 'Cogs and Teeth',
     'Craft a Copper Gear: four copper ingots around a cobblestone.',
     has('copper_gear'),
     ['clockwork_mechanism', 'wooden_chassis', 'mainspring', 'winding_crank', 'stumpy', 'sprout'], 5),
    ('robot_built', 'copper_gear', 'stumpy', 'task', 0, 'Some Assembly Required',
     'Craft Stumpy or Sprout. Next: wind a Mainspring to power it.',
     has('stumpy', 'sprout'), [], 5),
    ('mainspring', 'robot_built', 'mainspring', 'task', 0, 'Wind It Up',
     'Put a Mainspring in a Winding Crank, then hold right-click on the crank with an empty hand.',
     milestone('wind_spring'), [], 5),
    ('first_robot', 'mainspring', 'stumpy', 'task', 0, 'Hello, Stumpy',
     'Place Stumpy among trees or Sprout in a field. Put a chest or Supply Crate right next to it.',
     placed('stumpy', 'sprout'), [], 5),
    ('robot_working', 'first_robot', 'sprout', 'goal', 0, 'Hands Free',
     'Right-click your robot with the wound Mainspring. It starts working on its own.',
     milestone('robot_working'), [], 20),
    ('hammer', 'copper_gear', 'tinkers_hammer', 'task', 0, 'Hammer Time',
     "Make a Tinker's Hammer. It mines 3x3, and with two ingots in the crafting grid it makes a plate.",
     has('tinkers_hammer'),
     ['iron_plate_from_hammer', 'copper_plate_from_hammer', 'gold_plate_from_hammer'], 5),
    ('first_iron', 'hammer', 'minecraft:iron_ingot', 'goal', 1, 'Iron Age',
     'Smelt iron. A burner generator, your first battery and power tools are a few ingots away.',
     has('minecraft:iron_ingot'),
     ['combustion_generator', 'charger', 'copper_cell', 'copper_coil', 'iron_casing', 'electric_motor', 'basic_circuit',
      'tool_upgrade_kit_1', 'bore_drill_from_tinkers_hammer', 'chainsaw_from_felling_axe', 'tesla_coil_1', 'tesla_linker',
      'mining_drone', 'signal_flare', 'tinkers_bench'], 10),
    ('mining_drone', 'first_iron', 'mining_drone', 'task', 1, 'Dig Buddy',
     'Craft a Mining Drone. Give it a battery and torches, aim it, and it digs a 3x3 tunnel and brings the loot back.',
     has('mining_drone'), [], 10),
    ('generator', 'first_iron', 'combustion_generator', 'task', 1, 'Fire It Up',
     'Place a Combustion Generator and right-click it with coal or logs. It powers what touches it.',
     placed('combustion_generator', 'solar_panel_mk1'), [], 5),
    ('charger', 'generator', 'charger', 'task', 1, 'Topped Up',
     'Place a Charger touching the generator and right-click it with a cell or a drill.',
     placed('charger'), [], 5),
    ('copper_cell', 'charger', 'copper_cell', 'task', 1, 'Rechargeable',
     'Craft a Copper Cell. In your inventory it recharges the FE tool in your hand.',
     has('copper_cell'), [], 5),
    ('tesla', 'generator', 'tesla_coil_1', 'task', 1, 'Plugged In',
     'Put a Tesla Coil on your generator. Sneak-right-click it with a Tesla Linker, then right-click a machine.',
     placed('tesla_coil_1', 'tesla_coil_2', 'tesla_coil_3', 'tesla_coil_4', 'tesla_coil_5'), [], 5),
    ('power_tool', 'first_iron', 'bore_drill', 'task', 1, 'Power Tools',
     'Smithing table: Tool Upgrade Kit I + your hammer + an Electric Motor make a Bore Drill. The felling axe becomes a Chainsaw.',
     has('bore_drill', 'chainsaw'), [], 10),
    ('basic_circuit', 'first_iron', 'basic_circuit', 'goal', 1, 'Wired',
     'Craft a Basic Circuit (it needs gold). Solar, upgrade cards, farm kits and warp pads open up.',
     has('basic_circuit'),
     ['solar_panel_mk1', 'tesla_coil_2', 'accumulator_1', 'metal_press', 'metal_press_iron_plate',
      'metal_press_copper_plate', 'metal_press_gold_plate', 'shock_baton_from_gearblade', 'farm_kit_mk2', 'warp_pad',
      'recall_remote', 'storage_terminal', 'storage_expansion_mk1', 'sentry_drone', 'courier_drone', 'courier_remote', 'exo_helmet_mk1', 'exo_chestplate_mk1', 'exo_leggings_mk1', 'exo_boots_mk1', 'night_vision_module', 'step_assist_module', 'spring_heels_module', 'servo_stride_module_1', 'grinder_mk1', 'electric_furnace_mk1'] + cards('speed', 'efficiency', 'growth', 'void', 'pickup', 'height', 'carry'), 20),
    ('exo', 'basic_circuit', 'exo_chestplate_mk1', 'task', 1, 'Suit Up',
     'Craft an Exo-Frame piece. Sneak-right-click it to add a module, press J to switch modules.',
     has('exo_helmet_mk1', 'exo_chestplate_mk1', 'exo_leggings_mk1', 'exo_boots_mk1'), [], 10),
    ('drones', 'basic_circuit', 'sentry_drone', 'task', 1, 'Air Support',
     'Craft a Sentry Drone to guard your base, or a Courier Drone to carry items between chests.',
     has('sentry_drone', 'courier_drone'), [], 10),
    ('grinder', 'basic_circuit', 'grinder_mk1', 'task', 1, 'Grind It Down',
     'Place a Grinder: every ore becomes two dusts. Smelt them, or feed an Electric Furnace. Flint in the media slot adds a little more.',
     placed('grinder_mk1', 'grinder_mk2', 'grinder_mk3', 'grinder_mk4'),
     ['iron_grinding_balls'] + [f'{m}_ingot_from_{k}_{m}_dust' for m in ('iron', 'gold', 'copper') for k in ('smelting', 'blasting')]
     + [f'grinding/{r}' for m in ('iron', 'gold', 'copper') for r in (f'{m}_ore_to_dust', f'raw_{m}_to_dust', f'{m}_ingot_to_dust')]
     + [f'grinding/{r}' for r in ('cobblestone_to_gravel', 'gravel_to_sand', 'blaze_rod_to_powder', 'bone_to_bone_meal',
                                  'glowstone_to_dust', 'wool_to_string', 'redstone_ore', 'lapis_ore', 'coal_ore')], 10),
    ('metal_press', 'basic_circuit', 'metal_press', 'task', 1, 'Press Ahead',
     'Place a Metal Press: one ingot makes one plate, twice what the hammer gives.',
     placed('metal_press'), [], 5),
    ('storage', 'basic_circuit', 'storage_terminal', 'task', 1, 'Big Chest',
     'Place a Storage Terminal: 81 slots, a search box, a crafting grid. Expansions add more. It needs a little power.',
     placed('storage_terminal'), [], 5),
    ('upgrade_card', 'basic_circuit', 'upgrade_speed', 'task', 1, 'Plug and Play',
     'Craft an upgrade card and right-click a robot or machine with it. More cards of a kind stack.',
     has(*cards('speed', 'efficiency', 'growth', 'void', 'pickup')), [], 5),
    ('farm_kit', 'upgrade_card', 'farm_kit_mk2', 'task', 1, 'Bigger Fields',
     'Right-click a placed Stumpy or Sprout with a Farm Kit Mk2. It also takes a second speed card now.',
     milestone('farm_kit'), [], 10),
    ('warp', 'basic_circuit', 'warp_pad', 'task', 1, 'Home Run',
     'Place two Warp Pads, power them, stand on one and right-click it.',
     milestone('warp'), [], 10),
    ('diamonds', 'basic_circuit', 'minecraft:diamond', 'goal', 2, 'Diamonds!',
     'Find diamonds. They make the Excavator and the Age 2 parts.',
     has('minecraft:diamond'), ['excavator', 'reinforced_casing', 'advanced_circuit'], 20),
    ('excavator', 'diamonds', 'excavator', 'task', 2, 'Dig Deep',
     'Place an Excavator with a chest next to it. It digs a hole down to bedrock, slowly, unless you feed it speed cards and power.',
     placed('excavator'), [], 10),
    ('survey_rig', 'excavator', 'survey_rig', 'task', 2, 'No Holes Barred',
     'Servo parts make a Survey Rig: it counts the ores in its chunk and pulls them out without digging. Slow, and hungry for power.',
     placed('survey_rig'), [], 20),
    ('age2', 'diamonds', 'advanced_circuit', 'goal', 2, 'Servo Age',
     'Craft an Advanced Circuit or a Reinforced Casing: diamonds, obsidian and quartz.',
     has('advanced_circuit', 'reinforced_casing'),
     ['servo_actuator', 'grinder_mk2', 'electric_furnace_mk2', 'ferrothorium_grinding_balls', 'storage_expansion_mk2', 'redstone_cell', 'temp_servo_core', 'solar_panel_mk2', 'accumulator_2', 'tesla_coil_3', 'rivet_gun',
      'tool_upgrade_kit_2', 'farm_kit_mk3', 'essence_vial', 'replicator_frame', 'replicator_glass',
      'replicator_controller', 'survey_rig', 'mining_drone_mk2', 'sentry_drone_mk2', 'courier_drone_mk2', 'exo_helmet_mk2_from_mk1', 'exo_chestplate_mk2_from_mk1', 'exo_leggings_mk2_from_mk1', 'exo_boots_mk2_from_mk1', 'rebreather_module', 'rebreather_module_from_prismarine', 'jet_assist_module', 'servo_stride_module_2', 'robot_hud_module', 'fall_dampener_module', 'magnet_module', 'colossus_altar'] + cards('range', 'fortune', 'silk'), 30),
    ('foundry', 'age2', 'signal_flare', 'task', 2, 'Signs of Scrap',
     'Find a Rusted Foundry (a ruined copper hall in plains, deserts and badlands) or craft a Signal Flare.',
     [in_structure('robotica:rusted_foundry'), has('signal_flare')], [], 10),
    ('colossus', 'foundry', 'colossus_altar', 'goal', 2, 'Scrap Heap',
     'Use a Signal Flare on a Colossus Altar and defeat the Scrap Colossus. Hit its open core while it overheats.',
     killed('robotica:scrap_colossus'), [], 50),
    ('servo_core', 'colossus', 'servo_core', 'goal', 2, 'Core Memory',
     'Get a Servo Core. It upgrades your Bore Drill and builds the Mob Replicator.',
     has('servo_core'), ['servo_drill_from_bore_drill'], 30),
    ('servo_drill', 'servo_core', 'servo_drill', 'task', 2, 'Vein Glory',
     'Smith your Bore Drill into a Servo Drill: 5x5 and vein mining.',
     has('servo_drill'), [], 10),
    ('vial', 'age2', 'essence_vial', 'task', 2, 'Sample Size',
     'Right-click the same kind of monster eight times with an Essence Vial.',
     milestone('vial_complete'), [], 10),
    ('replicator', 'servo_core', 'replicator_controller', 'goal', 2, 'Copy That',
     'Build the 3x3x3 Mob Replicator around an empty centre, with at least one Replicator Glass.',
     milestone('replicator_formed'), [], 30),
    ('age3', 'servo_core', 'quantum_circuit', 'goal', 3, 'Deep Age',
     'Craft a Quantum Circuit: blaze rods and netherite from the Nether.',
     has('quantum_circuit', 'plasma_actuator'),
     ['blazing_casing', 'quantum_circuit', 'plasma_actuator', 'grinder_mk3', 'electric_furnace_mk3', 'pyrosteel_grinding_balls', 'storage_expansion_mk3', 'temp_magma_core', 'accumulator_3', 'tesla_coil_4',
      'tool_upgrade_kit_3', 'rift_upgrade', 'rift_remote', 'servo_stride_module_3', 'kinetic_shield_module'], 50),
    ('magma_core', 'age3', 'magma_core', 'goal', 3, 'Hot Core',
     'Get a Magma Core: Magma Drill, Arc Blade, Rift Upgrade and Age 3 mobs in the replicator.',
     has('magma_core'), ['magma_drill_from_servo_drill', 'arc_blade_from_shock_baton'], 50),
    ('magma_drill', 'magma_core', 'magma_drill', 'task', 3, 'Smelt as You Go',
     'Smith a Magma Drill. Turn on auto-smelt in its settings.',
     has('magma_drill'), [], 20),
    ('age4', 'magma_core', 'null_circuit', 'goal', 4, 'Antigrav Age',
     'Craft a Null Circuit: ender pearls and a nether star.',
     has('null_circuit'),
     ['null_casing', 'null_circuit', 'ender_cell', 'grinder_mk4', 'electric_furnace_mk4', 'resonant_grinding_balls', 'temp_antigrav_core', 'tool_upgrade_kit_4', 'tesla_coil_5',
      'farm_kit_mk4', 'gate_controller', 'linking_card', 'flight_module'], 80),
    ('antigrav_core', 'age4', 'antigrav_core', 'goal', 4, 'Weightless',
     'Get an Antigrav Core: the Null Drill, the Null Lance and Portal Projectors.',
     has('antigrav_core'), ['null_drill_from_magma_drill', 'null_lance_from_rivet_gun'], 80),
    ('null_drill', 'antigrav_core', 'null_drill', 'challenge', 4, 'Big Holes',
     'Smith a Null Drill and dig a 12x12x12 cube in one swing.',
     has('null_drill'), [], 100),
    ('flight', 'antigrav_core', 'flight_module', 'challenge', 4, 'Lift Off',
     'Put a Flight Module in an Exo chestplate and press K to fly.',
     has('flight_module'), [], 50),
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
        display = {'icon': {'id': icon}, 'title': {'translate': key + '.title'},
                   'description': {'translate': key + '.description'}, 'frame': frame,
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
        guide.append({'id': f'robotica:guide/{name}', 'parent': f'robotica:guide/{parent}' if parent else None,
                      'icon': icon, 'age': age})
    GUIDE.write_text(json.dumps({'steps': guide}, indent=2) + '\n')
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
    print(f'{len(STEPS)} guide steps, {len(unlocked)} recipes unlocked')


if __name__ == '__main__':
    main()
