"""Writes the recipes of the architect module: the Architect Table (Age 0), 8 bulk blocks + 1 tier material -> 8 style blocks
for every style block, and stonecutter recipes between the roles of one style.
Run: python3 scripts/data/architect_recipes.py   (overwrites data/robotica/recipe/<name>.json for architect items only)"""
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


def shaped(name, pattern, key, count=1, category='building'):
    write(name, {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
                 'key': {k: ing(v) for k, v in key.items()},
                 'result': {'id': f'robotica:{name}', 'count': count}})


def stonecutting(name, source, result):
    write(name, {'type': 'minecraft:stonecutting', 'ingredient': ing(source), 'result': {'id': f'robotica:{result}', 'count': 1}})


IRON_PLATE = '#c:plates/iron'

# Age 0: a starter machine, no iron. Copper, cobble, a crafting table and a Clockwork Mechanism for the drafting arm.
shaped('architect_table', ['CTC', 'GMG', 'SSS'],
       {'C': '#c:ingots/copper', 'T': '#c:player_workstations/crafting_tables', 'G': '#c:gears/copper', 'M': 'clockwork_mechanism',
        'S': '#c:cobblestones/normal'},
       category='misc')

# (bulk, centre) per style and role. Every pair is unique, 8 bulk around 1 centre gives 8 blocks.
HAND = {
    'timberframe': {
        'wall': ('#minecraft:planks', '#c:dyes/white'),
        'floor': ('#minecraft:wooden_slabs', '#c:dyes/white'),
        'roof': ('#minecraft:wooden_stairs', '#c:dyes/white'),
        'pillar': ('#minecraft:logs', '#c:dyes/white'),
        'window': ('#c:glass_blocks', '#c:dyes/white'),
        'light': ('#minecraft:planks', 'minecraft:torch'),
    },
    'dark_stone': {
        'wall': ('#minecraft:planks', '#minecraft:coals'),
        'floor': ('#minecraft:wooden_slabs', '#minecraft:coals'),
        'roof': ('#minecraft:wooden_stairs', '#minecraft:coals'),
        'pillar': ('#minecraft:logs', '#minecraft:coals'),
        'window': ('#c:glass_blocks', '#minecraft:coals'),
        'light': ('dark_stone_wall', 'minecraft:torch'),
    },
    # Age 1: light and dark stone around an Iron Plate
    'pearl_panel': {
        'wall': ('minecraft:smooth_stone', IRON_PLATE),
        'floor': ('minecraft:polished_diorite', IRON_PLATE),
        'roof': ('#c:stones', IRON_PLATE),
        'pillar': ('minecraft:polished_andesite', IRON_PLATE),
        'window': ('#c:glass_blocks', 'pearl_panel_wall'),
        'light': ('pearl_panel_wall', 'minecraft:lantern'),
    },
    'onyx_panel': {
        'wall': ('#c:cobblestones/deepslate', IRON_PLATE),
        'floor': ('minecraft:polished_deepslate', IRON_PLATE),
        'roof': ('minecraft:deepslate_tiles', IRON_PLATE),
        'pillar': ('minecraft:deepslate_bricks', IRON_PLATE),
        'window': ('#c:glass_blocks', 'onyx_panel_wall'),
        'light': ('onyx_panel_wall', 'minecraft:lantern'),
    },
}

for style, roles in HAND.items():
    for role, (bulk, centre) in roles.items():
        shaped(f'{style}_{role}', ['AAA', 'ABA', 'AAA'], {'A': bulk, 'B': centre}, count=8)
    # stonecutter: the structural roles convert into each other
    for other in ('floor', 'roof', 'pillar'):
        stonecutting(f'{style}_wall_to_{other}_stonecutting', f'{style}_wall', f'{style}_{other}')
        stonecutting(f'{style}_{other}_to_wall_stonecutting', f'{style}_{other}', f'{style}_wall')
print('architect recipes written')
