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

# Registry ids are historic (timberframe_* ... null_spire_*) and stay; the names follow the looks.
STYLE_NAMES = {'timberframe': 'Clean Stone', 'copper_works': 'Smooth Panel', 'steel_lab': 'Detailed Stone', 'null_spire': 'Tech Stone'}
ROLE_NAME = {'wall': 'Wall', 'floor': 'Floor', 'roof': 'Roof', 'pillar': 'Pillar', 'window': 'Window', 'light': 'Light'}
ROLE_NAMES = {style: ROLE_NAME for style in STYLES}


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def loot(name, copy_matter=False):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}'}
    pool = {'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}
    if copy_matter:
        # the table keeps everything when picked up, so it always drops (no explosion roll)
        entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity',
                               'include': ['robotica:architect_matter', 'robotica:architect_build', 'robotica:contents']}]
        del pool['conditions']
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [pool],
        'random_sequence': f'robotica:blocks/{name}',
    })


def simple_block(name, model):
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{model}'}}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})


# ---------- the 24 style blocks ----------
# Walls, floors and roofs pick one of three textures per position (weights below), so big surfaces never repeat.
WEIGHTS = {'wall': [8, 3, 1], 'floor': [6, 3, 2], 'roof': [6, 3, 2]}
DIRS = ['down', 'up', 'north', 'south', 'west', 'east']


def variant(name, v):
    return name if v == 1 else f'{name}_{v}'


def light_model(name):
    """Panel plus a full-bright overlay of its diffuser (NeoForge per-face light data), so it glows in the dark."""
    glow = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
    return {
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:cutout',
        'textures': {'particle': tex(name), 'base': tex(name), 'glow': tex(f'{name}_glow')},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 16, 16],
             'faces': {d: {'texture': '#base', 'cullface': d} for d in DIRS}},
            {'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
             'faces': {d: {'texture': '#glow', 'cullface': d, 'neoforge_data': glow} for d in DIRS}},
        ],
    }


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
        elif role in WEIGHTS:
            models = []
            for v, weight in enumerate(WEIGHTS[role], start=1):
                model = variant(name, v)
                if role == 'roof':
                    # Seen from inside, a roof block is the ceiling: its underside shows the style's wall surface.
                    write(ASSETS / 'models/block' / f'{model}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
                        'top': tex(model), 'side': tex(model), 'bottom': tex(f'{style}_wall'), 'particle': tex(model)}})
                else:
                    write(ASSETS / 'models/block' / f'{model}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(model)}})
                models.append({'model': f'robotica:block/{model}', 'weight': weight})
            write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': models}})
        elif role == 'light':
            write(ASSETS / 'models/block' / f'{name}.json', light_model(name))
            write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{name}'}}})
        else:
            write(ASSETS / 'models/block' / f'{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(name)},
                  'render_type': 'minecraft:translucent' if style == 'null_spire' else 'minecraft:cutout'})
            write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{name}'}}})
        write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})
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
pickaxe = ['robotica:architect_table']
for style in STYLES:
    pickaxe += [f'robotica:{style}_{r}' for r in ROLES]
(FRAG / 'data/minecraft/tags/block/mineable/axe.json').unlink(missing_ok=True)  # every style is stone now
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
    'gui.robotica.matter.rustic': 'Rustic',
    'gui.robotica.matter.refined': 'Refined',
    'gui.robotica.matter.exotic': 'Exotic',
    'gui.robotica.architect_build': 'Build',
    'gui.robotica.architect_build_tip': 'Build every planned plot and update the buildings next to them',
    'gui.robotica.architect_cancel': 'Cancel',
    'gui.robotica.architect_cancel_tip': 'Take all queued plots off the plan. Blocks already placed stay.',
    'gui.robotica.architect_clear_on': 'Clear terrain: ON',
    'gui.robotica.architect_clear_off': 'Clear terrain: OFF',
    'gui.robotica.architect_clear_tip': 'Break blocks in the way, chests too (their items are kept). Slow. Junk like cobble and dirt is voided, the rest goes into a chest touching the table',
    'gui.robotica.architect_status_0': 'Idle',
    'gui.robotica.architect_status_1': 'Building',
    'gui.robotica.architect_status_2': 'Needs rustic',
    'gui.robotica.architect_status_3': 'Needs refined',
    'gui.robotica.architect_status_4': 'Needs exotic',
    'gui.robotica.architect_status_5': 'Needs energy',
    'gui.robotica.architect_status_6': 'Style locked',
    'gui.robotica.architect_status_7': 'Area not loaded',
    'gui.robotica.architect_status_8': 'Ready',
    'gui.robotica.architect_status_tip_2': 'Feed stone, dirt, sand or wood, then press Build',
    'gui.robotica.architect_status_tip_3': 'Feed ingots, glass, bricks or quartz, then press Build',
    'gui.robotica.architect_status_tip_4': 'Feed obsidian, amethyst, glowstone, pearls or diamonds, then press Build',
    'gui.robotica.architect_status_tip_5': 'Put a wound Mainspring or a cell in the battery slot, or connect a generator',
    'gui.robotica.architect_status_tip_6': 'Put the casing this style needs back in the casing slot',
    'gui.robotica.architect_progress': 'Building: %s%%',
    'gui.robotica.architect_cost_tip': 'Per block: %s rustic, %s refined, %s exotic, %s FE',
    'gui.robotica.architect_needs_casing_1': 'Needs an Iron Casing',
    'gui.robotica.architect_needs_casing_2': 'Needs a Reinforced Casing',
    'gui.robotica.architect_needs_casing_3': 'Needs a Blazing Casing',
    'gui.robotica.architect_needs_casing_4': 'Needs a Null Casing',
    'gui.robotica.architect_door_add': 'Add a door here',
    'gui.robotica.architect_door_remove': 'Remove this door',
    'gui.robotica.architect_wall_add': 'Add an inner wall with a doorway',
    'gui.robotica.architect_wall_close': 'Close the doorway',
    'gui.robotica.architect_wall_remove': 'Remove this wall',
    'gui.robotica.architect_plot_table': 'Table plot: click to build here',
    'gui.robotica.architect_plot_free': 'Click to plan a building',
    'gui.robotica.architect_plot_queued': 'Queued',
    'gui.robotica.architect_plot_building': 'Building',
    'gui.robotica.architect_plot_changed': 'Built, changes pending',
    'gui.robotica.architect_plot_built': 'Built',
    'gui.robotica.architect_plot_forget': 'Shift-click to forget (blocks stay)',
    'gui.robotica.architect_casing': 'Casing: unlocks styles',
    'gui.robotica.architect_battery': 'Battery: a wound Mainspring or any FE cell',
    'gui.robotica.architect_storage_hint': 'Stone, wood, ingots, glass, quartz, obsidian and more turn into matter',
    'message.robotica.architect_not_owner': 'This Architect Table belongs to %s',
    'message.robotica.architect_bad_request': 'That is not a plot',
    'message.robotica.architect_no_wall': 'Inner walls go between two planned plots',
    'message.robotica.architect_queue_full': 'Too many plots queued',
    'message.robotica.architect_style_locked': 'That style needs a better casing in the table',
    'message.robotica.architect_out_of_world': 'That plot would be outside the world',
    'message.robotica.architect_last_door': 'Every building keeps at least one door',
    'tooltip.robotica.architect_table': 'Builds 9x9 buildings from matter, from %s FE per block',
    'tooltip.robotica.architect_table_matter': 'Feed it cobble, wood, ingots and more',
    'tooltip.robotica.matter_value': 'Matter: %s',
    'robotica.configuration.architect_table': 'Architect Table',
    'robotica.configuration.baseInterval': 'Ticks per block',
    'robotica.configuration.fePerBlock': 'FE per block',
    'robotica.configuration.energyBuffer': 'Energy buffer (FE)',  # shared key, same text as automation
    'robotica.configuration.energyReceive': 'Max input (FE/t)',
    'robotica.configuration.matterCap': 'Matter cap per grade',
    'robotica.configuration.maxQueue': 'Max queued plots',
    'robotica.configuration.allowClearTerrain': 'Allow clear terrain',
    'robotica.configuration.clearInterval': 'Ticks per cleared block',
    'robotica.configuration.builderDrones': 'Builder drones',
})
# keep keys added by hand (demolish, config) and overwrite only the generated ones
_lang_file = FRAG / 'assets/robotica/lang/en_us.json'
_existing = json.loads(_lang_file.read_text()) if _lang_file.exists() else {}
write(_lang_file, {**_existing, **lang})
print('architect models, loot, tags and lang written')
