"""Writes blockstates, block/item models, loot tables, tag fragments (mineable + matter values) and the lang fragment
of the architect module.
Run: python3 scripts/data/architect_models.py   (overwrites files named after architect blocks and the architect fragments)"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'
FRAG = ROOT / 'src/main/fragments/architect'

STYLES = ['timberframe', 'copper_works', 'steel_lab', 'null_spire']
ROLES = ['wall', 'floor', 'roof', 'pillar', 'window', 'light']

STYLE_NAMES = {'timberframe': 'Timberframe', 'copper_works': 'Copper Works', 'steel_lab': 'Steel Lab', 'null_spire': 'Null Spire'}
ROLE_NAMES = {
    'timberframe': {'wall': 'Wall', 'floor': 'Floor', 'roof': 'Roof', 'pillar': 'Beam', 'window': 'Window', 'light': 'Lantern'},
    'copper_works': {'wall': 'Wall', 'floor': 'Floor', 'roof': 'Roof', 'pillar': 'Pillar', 'window': 'Window', 'light': 'Amber Lamp'},
    'steel_lab': {'wall': 'Wall', 'floor': 'Floor', 'roof': 'Roof', 'pillar': 'Pillar', 'window': 'Window', 'light': 'Light Strip'},
    'null_spire': {'wall': 'Wall', 'floor': 'Floor', 'roof': 'Roof', 'pillar': 'Pillar', 'window': 'Energy Glass', 'light': 'Light'},
}


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def loot(name, copy_matter=False):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}'}
    if copy_matter:
        entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['robotica:architect_matter']}]
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


def simple_block(name, model):
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{model}'}}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})


# ---------- the 24 style blocks ----------
for style in STYLES:
    for role in ROLES:
        name = f'{style}_{role}'
        if role == 'pillar':
            textures = {'end': tex(f'{name}_top'), 'side': tex(name), 'particle': tex(name)}
            write(ASSETS / 'models/block' / f'{name}.json', {'parent': 'minecraft:block/cube_column', 'textures': textures})
            write(ASSETS / 'models/block' / f'{name}_horizontal.json', {'parent': 'minecraft:block/cube_column_horizontal', 'textures': textures})
            write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {
                'axis=y': {'model': f'robotica:block/{name}'},
                'axis=z': {'model': f'robotica:block/{name}_horizontal', 'x': 90},
                'axis=x': {'model': f'robotica:block/{name}_horizontal', 'x': 90, 'y': 90},
            }})
            write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})
        else:
            model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(name)}}
            if role == 'window':
                model['render_type'] = 'minecraft:translucent' if style == 'null_spire' else 'minecraft:cutout'
            write(ASSETS / 'models/block' / f'{name}.json', model)
            simple_block(name, name)
        loot(name)

# ---------- architect table ----------
write(ASSETS / 'models/block/architect_table.json', {
    'parent': 'minecraft:block/cube_bottom_top',
    'textures': {'top': tex('architect_table_top'), 'side': tex('architect_table_side'),
                 'bottom': tex('architect_table_bottom'), 'particle': tex('architect_table_side')},
})
simple_block('architect_table', 'architect_table')
loot('architect_table', copy_matter=True)

# ---------- tags ----------
axe = [f'robotica:timberframe_{r}' for r in ('wall', 'floor', 'roof', 'pillar', 'light')]
pickaxe = ['robotica:architect_table', 'robotica:timberframe_window']
for style in STYLES[1:]:
    pickaxe += [f'robotica:{style}_{r}' for r in ROLES]
write(FRAG / 'data/minecraft/tags/block/mineable/axe.json', {'replace': False, 'values': axe})
write(FRAG / 'data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': pickaxe})

# Matter values. Tag robotica:matter/<grade>_<value> lists items worth <value> of that grade each (see MatterTable).
MATTER = {
    'rustic_1': [
        '#c:cobblestones/normal', '#c:cobblestones/deepslate', '#c:cobblestones/mossy', '#c:stones', '#c:sands/colorless', '#c:sands/red',
        '#c:gravels', '#minecraft:planks', 'minecraft:sandstone', 'minecraft:red_sandstone',
        'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:rooted_dirt', 'minecraft:podzol', 'minecraft:mud',
        'minecraft:netherrack', 'minecraft:tuff', 'minecraft:andesite', 'minecraft:diorite', 'minecraft:granite', 'minecraft:deepslate',
        'minecraft:blackstone', 'minecraft:basalt', 'minecraft:smooth_basalt', 'minecraft:calcite', 'minecraft:terracotta',
        'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:deepslate_bricks',
        'minecraft:deepslate_tiles', 'minecraft:polished_andesite', 'minecraft:polished_diorite', 'minecraft:polished_granite',
        'minecraft:polished_deepslate', 'minecraft:polished_blackstone', 'minecraft:polished_blackstone_bricks',
    ],
    'rustic_4': ['#minecraft:logs'],
    'refined_1': ['minecraft:clay_ball', '#c:nuggets/iron', '#c:nuggets/gold'],
    'refined_2': ['#c:glass_blocks', '#c:bricks/normal', '#c:bricks/nether'],
    'refined_4': ['#c:ingots/copper', '#c:plates/copper', '#c:gems/quartz', 'minecraft:clay'],
    'refined_8': ['#c:ingots/iron', '#c:ingots/gold', '#c:plates/iron', '#c:plates/gold', 'minecraft:bricks'],
    'refined_36': ['#c:storage_blocks/copper'],
    'refined_72': ['#c:storage_blocks/iron', '#c:storage_blocks/gold'],
    'exotic_2': ['#c:dusts/glowstone'],
    'exotic_4': ['#c:obsidians', '#c:gems/amethyst', '#c:gems/prismarine'],
    'exotic_16': ['#c:ender_pearls'],
    'exotic_32': ['#c:gems/diamond'],
    'exotic_288': ['#c:storage_blocks/diamond'],
}
for name, values in MATTER.items():
    write(FRAG / 'data/robotica/tags/item/matter' / f'{name}.json', {'replace': False, 'values': values})

# ---------- lang ----------
lang = {'block.robotica.architect_table': 'Architect Table', 'entity.robotica.builder_drone': 'Builder Drone'}
for style in STYLES:
    lang[f'style.robotica.{style}'] = STYLE_NAMES[style]
    for role in ROLES:
        lang[f'block.robotica.{style}_{role}'] = f'{STYLE_NAMES[style]} {ROLE_NAMES[style][role]}'
lang.update({
    'module.robotica.corridor': 'Corridor',
    'module.robotica.hall': 'Hall',
    'module.robotica.workshop': 'Workshop',
    'module.robotica.storage_room': 'Storage Room',
    'module.robotica.machine_hall': 'Machine Hall',
    'module.robotica.greenhouse': 'Greenhouse',
    'module.robotica.hangar': 'Hangar',
    'module.robotica.stairwell': 'Stairwell',
    'gui.robotica.matter.rustic': 'Rustic',
    'gui.robotica.matter.refined': 'Refined',
    'gui.robotica.matter.exotic': 'Exotic',
    'gui.robotica.architect_tab_plan': 'Plan',
    'gui.robotica.architect_tab_storage': 'Storage',
    'gui.robotica.architect_clear_on': 'Clear: ON',
    'gui.robotica.architect_clear_off': 'Clear: OFF',
    'gui.robotica.architect_clear_tip': 'Clear terrain: remove blocks (no containers, no unbreakable) inside new plots and turn them into rustic matter',
    'gui.robotica.architect_queue': 'Queue build',
    'gui.robotica.architect_forget': 'Forget',
    'gui.robotica.architect_forget_tip': 'Forget the selected built plot so it can be planned again. The blocks stay.',
    'gui.robotica.architect_cancel': 'Cancel all',
    'gui.robotica.architect_cancel_one': 'Cancel this build',
    'gui.robotica.architect_energy': 'Energy',
    'gui.robotica.architect_status_0': 'Idle',
    'gui.robotica.architect_status_1': 'Building',
    'gui.robotica.architect_status_2': 'Missing: rustic matter',
    'gui.robotica.architect_status_3': 'Missing: refined matter',
    'gui.robotica.architect_status_4': 'Missing: exotic matter',
    'gui.robotica.architect_status_5': 'Missing: energy',
    'gui.robotica.architect_status_6': 'Style locked: put a better casing in the casing slot',
    'gui.robotica.architect_status_7': 'Waiting for the area to load',
    'gui.robotica.architect_cost': 'Block: %s/%s/%s',
    'gui.robotica.architect_no_plot': 'Click a plot',
    'gui.robotica.architect_plot': 'Plot %s, %s',
    'gui.robotica.architect_plot_table': 'Table plot (free)',
    'gui.robotica.architect_plot_free': 'Free',
    'gui.robotica.architect_plot_status_1': 'Queued',
    'gui.robotica.architect_plot_status_2': 'Building',
    'gui.robotica.architect_plot_status_3': 'Built',
    'gui.robotica.architect_queue_title': 'Queue (%s)',
    'gui.robotica.architect_more': '+%s more',
    'gui.robotica.architect_input': 'Materials in',
    'gui.robotica.architect_casing': 'Style casing',
    'gui.robotica.architect_upgrades': 'Upgrades',
    'gui.robotica.architect_storage_hint': 'Stone, wood, ingots, glass, quartz, obsidian and more turn into matter',
    'message.robotica.architect_not_owner': 'This Architect Table belongs to %s',
    'message.robotica.architect_bad_request': 'Pick a plot and a module first',
    'message.robotica.architect_plot_taken': 'That plot is already planned',
    'message.robotica.architect_queue_full': 'The build queue is full',
    'message.robotica.architect_style_locked': 'That style needs a better casing in the table',
    'message.robotica.architect_out_of_world': 'That plot would be outside the world',
    'tooltip.robotica.architect_table': 'Builds modular rooms from matter for %s FE per block',
    'tooltip.robotica.architect_table_matter': 'Feed it cobble, wood, ingots and more. It accepts FE and items from any side',
    'tooltip.robotica.architect_table_styles': 'Casing in the style slot unlocks styles: Iron Copper Works, Reinforced Steel Lab, Null Null Spire',
    'tooltip.robotica.matter_value': 'Matter: %s',
    'robotica.configuration.architect_table': 'Architect Table',
    'robotica.configuration.baseInterval': 'Ticks per block',
    'robotica.configuration.fePerBlock': 'FE per block',
    'robotica.configuration.energyBuffer': 'Buffer (FE)',
    'robotica.configuration.energyReceive': 'Max input (FE/t)',
    'robotica.configuration.matterCap': 'Matter cap per grade',
    'robotica.configuration.maxQueue': 'Max queued builds',
    'robotica.configuration.allowClearTerrain': 'Allow clear terrain',
    'robotica.configuration.builderDrones': 'Builder drones',
})
write(FRAG / 'assets/robotica/lang/en_us.json', lang)
print('architect models, loot, tags and lang written')
