"""Writes blockstates, block/item models, loot tables, tag fragments (mineable + matter values) and the lang fragment
of the architect module (Pocket Architect names too).
Run: python3 scripts/data/architect_models.py   (overwrites files named after architect blocks and the architect fragments)"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'
FRAG = ROOT / 'src/main/fragments/architect'

STYLES = ['timberframe', 'dark_stone', 'pearl_panel', 'onyx_panel']
ROLES = ['wall', 'floor', 'roof', 'pillar', 'window', 'light']

# Clean Stone keeps the timberframe_* ids.
STYLE_NAMES = {'timberframe': 'Clean Stone', 'dark_stone': 'Dark Stone', 'pearl_panel': 'Pearl Panel', 'onyx_panel': 'Onyx Panel'}
# Styles with a glowing trim strip (<name>_glow textures) and tinted windows.
PANEL_STYLES = {'pearl_panel', 'onyx_panel'}
# Styles with connected pillars (ConnectedPillarBlock).
CONNECTED_TRIM = {'dark_stone', 'pearl_panel', 'onyx_panel'}
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


# ---------- the style blocks ----------
# Walls, floors and roofs pick one of three textures per position (weights below), so big surfaces never repeat.
WEIGHTS = {'wall': [8, 3, 1], 'floor': [6, 3, 2], 'roof': [6, 3, 2]}
DIRS = ['down', 'up', 'north', 'south', 'west', 'east']


def variant(name, v):
    return name if v == 1 else f'{name}_{v}'


GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


# ---------- connected trim (every style but Clean Stone) ----------
# ConnectedPillarBlock has a boolean per side. Each face picks one of the trim textures (scripts/textures/architect.py)
# from the four sides around it, turned to fit. Texture edges (top, right, bottom, left) per face, as world sides:
FACE_EDGES = {
    'north': ('up', 'west', 'down', 'east'), 'south': ('up', 'east', 'down', 'west'),
    'east': ('up', 'north', 'down', 'south'), 'west': ('up', 'south', 'down', 'north'),
    'up': ('north', 'east', 'south', 'west'), 'down': ('south', 'east', 'north', 'west'),
}
TRIM_SHAPES = {'none': '', 'end': 'T', 'straight': 'TB', 'corner': 'TR', 'tee': 'TRL', 'cross': 'TRBL'}
CW = {'T': 'R', 'R': 'B', 'B': 'L', 'L': 'T'}


def trim_texture(arms):
    """(shape, clockwise rotation) that draws exactly these texture-space arms."""
    for shape, base in TRIM_SHAPES.items():
        turned = set(base)
        for rot in (0, 90, 180, 270):
            if turned == set(arms):
                return shape, rot
            turned = {CW[a] for a in turned}
    raise ValueError(arms)


def trim_face(name, d, shape, rot, glow, cull=True):
    faces = [{'texture': f'#{shape}', 'rotation': rot}]
    if glow:
        faces.append({'texture': f'#{shape}_glow', 'rotation': rot, 'neoforge_data': GLOW})
    elements = []
    for i, face in enumerate(faces):
        if not face['rotation']:
            del face['rotation']
        if cull:
            face['cullface'] = d
        element = {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: face}}
        if i:
            element['shade'] = False
        elements.append(element)
    return elements


def trim_textures(name, glow, shapes=TRIM_SHAPES):
    t = {'particle': tex(f'{name}_straight')}
    for shape in shapes:
        t[shape] = tex(f'{name}_{shape}')
        if glow:
            t[f'{shape}_glow'] = tex(f'{name}_{shape}_glow')
    return t


def connected_pillar(name, glow):
    """16 one-face models per face and a multipart blockstate; the item looks like a standing post."""
    parts = []
    for d, edges in FACE_EDGES.items():
        for mask in range(16):
            arms = ''.join(e for i, e in enumerate('TRBL') if mask >> i & 1)
            shape, rot = trim_texture(arms)
            model = f'{name}/{d}_{mask}'
            write(ASSETS / 'models/block' / f'{model}.json', {
                'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
                'textures': trim_textures(name, glow, (shape,)),
                'elements': trim_face(name, d, shape, rot, glow)})
            parts.append({'when': {edges[i]: 'true' if mask >> i & 1 else 'false' for i in range(4)},
                          'apply': {'model': f'robotica:block/{model}'}})
    write(ASSETS / 'blockstates' / f'{name}.json', {'multipart': parts})
    elements = []
    for d in DIRS:
        elements += trim_face(name, d, 'straight' if d not in ('up', 'down') else 'none', 0, glow, cull=False)
    write(ASSETS / 'models/item' / f'{name}.json', {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': trim_textures(name, glow, ('straight', 'none')), 'elements': elements})


def light_model(name):
    """Panel plus a full-bright overlay of its diffuser (NeoForge per-face light data), so it glows in the dark."""
    glow = GLOW
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
            if style in CONNECTED_TRIM:
                connected_pillar(name, style in PANEL_STYLES)
                loot(name)
                continue
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
                  'render_type': 'minecraft:translucent' if style in PANEL_STYLES else 'minecraft:cutout'})
            write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{name}'}}})
        write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})
        loot(name)

# ---------- architect table ----------
def table_model():
    """Design station (textures: scripts/textures/architect.py): tabletop slab y 13..16 and plinth y 0..2 over the
    full block, graphite corner posts, a glass body 1 px back between them, and the holo blueprint 1 px deep in the
    tabletop (full-bright) with its bezel. Side faces use the default UVs, so every part maps onto its piece of the
    one architect_table_side picture."""
    sides = ('north', 'south', 'west', 'east')

    def box(a, b, faces, cull=()):
        return {'from': a, 'to': b, 'faces': {d: dict(f, cullface=d) if d in cull else f for d, f in faces.items()}}
    side = {d: {'texture': '#side'} for d in sides}
    bezel = {'texture': '#bottom', 'uv': [3, 1, 13, 2]}
    els = [
        box([0, 0, 0], [16, 2, 16], dict(side, down={'texture': '#bottom'}, up={'texture': '#bottom'}),
            sides + ('down',)),
        box([0, 13, 0], [16, 16, 16], dict(side, up={'texture': '#top'}, down={'texture': '#bottom'}),
            sides + ('up',)),
        box([1, 2, 1], [15, 13, 15], side),
    ]
    for x in (0, 14):
        for z in (0, 14):
            els.append(box([x, 2, z], [x + 2, 13, z + 2], side))
    els.append({'from': [2, 15, 2], 'to': [14, 15, 14], 'shade': False,
                'faces': {'up': {'texture': '#holo', 'neoforge_data': dict(GLOW)}}})
    els += [{'from': [2, 15, 2], 'to': [2, 16, 14], 'faces': {'east': bezel}},
            {'from': [14, 15, 2], 'to': [14, 16, 14], 'faces': {'west': bezel}},
            {'from': [2, 15, 2], 'to': [14, 16, 2], 'faces': {'south': bezel}},
            {'from': [2, 15, 14], 'to': [14, 16, 14], 'faces': {'north': bezel}},
            {'from': [0, 13, 0], 'to': [16, 16, 16], 'shade': False,      # light strip on the tabletop edge
             'faces': {d: {'texture': '#glow', 'cullface': d, 'neoforge_data': dict(GLOW)} for d in sides}}]
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'textures': {'top': tex('architect_table_top'), 'side': tex('architect_table_side'),
                         'bottom': tex('architect_table_bottom'), 'holo': tex('architect_table_holo'),
                         'glow': tex('architect_table_side_glow'),
                         'particle': tex('architect_table_side')},
            'elements': els}


write(ASSETS / 'models/block/architect_table.json', table_model())
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
}
for _old in (FRAG / 'data/robotica/tags/item/matter').glob('*.json'):
    if _old.stem not in MATTER:
        _old.unlink()  # a grade or value that is gone
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
    'gui.robotica.architect_build': 'Build',
    'gui.robotica.architect_build_tip': 'Builds the planned plots\nUpdates the buildings next to them',
    'gui.robotica.architect_cancel': 'Cancel',
    'gui.robotica.architect_cancel_tip': 'Takes queued plots and restyles off the plan\nPlaced blocks stay',
    'gui.robotica.architect_clear_on': 'Clear terrain: ON',
    'gui.robotica.architect_clear_off': 'Clear terrain: OFF',
    'gui.robotica.architect_clear_tip': 'Slowly breaks blocks in the way\nVoids junk\nStores the rest in a chest touching the table, chest contents included',
    'gui.robotica.architect_status_0': 'Idle',
    'gui.robotica.architect_status_1': 'Building',
    'gui.robotica.architect_status_2': 'Needs rustic',
    'gui.robotica.architect_status_3': 'Needs refined',
    'gui.robotica.architect_status_5': 'Needs energy',
    'gui.robotica.architect_status_6': 'Style locked',
    'gui.robotica.architect_status_7': 'Area not loaded',
    'gui.robotica.architect_status_8': 'Ready',
    'gui.robotica.architect_status_tip_0': 'Click a plot on the grid to plan a building',
    'gui.robotica.architect_status_tip_8': 'Press Build to start',
    'gui.robotica.architect_status_tip_2': 'Feed stone, dirt, sand or wood, then press Build',
    'gui.robotica.architect_status_tip_3': 'Feed ingots, glass, bricks or quartz, then press Build',
    'gui.robotica.architect_status_tip_5': 'Put a wound Mainspring or a cell in the battery slot, or connect a generator',
    'gui.robotica.architect_status_tip_6': 'Put the casing this style needs back in the casing slot',
    'gui.robotica.architect_progress': 'Building: %s%%',
    'gui.robotica.architect_cost_tip': 'Per block: %s rustic, %s refined, %s FE',
    'gui.robotica.architect_chip_restyle': 'Click to restyle the selected plot',
    'gui.robotica.architect_chip_pick': 'Click to use it for new plots, doors and walls',
    'gui.robotica.architect_needs_casing_1': 'Needs an Iron Casing',
    'gui.robotica.architect_door_add': 'Add a door here',
    'gui.robotica.architect_door_remove': 'Remove this door',
    'gui.robotica.architect_door_restyle': 'Rebuild this door frame in',
    'gui.robotica.architect_wall_add': 'Add an inner wall with a doorway',
    'gui.robotica.architect_wall_close': 'Close the doorway',
    'gui.robotica.architect_wall_remove': 'Remove this wall',
    'gui.robotica.architect_wall_restyle': 'Rebuild this wall in',
    'gui.robotica.architect_plot_table': 'Table plot: click to build here',
    'gui.robotica.architect_plot_free': 'Click to plan a building',
    'gui.robotica.architect_plot_queued': 'Queued',
    'gui.robotica.architect_plot_building': 'Building',
    'gui.robotica.architect_plot_changed': 'Built, changes pending',
    'gui.robotica.architect_plot_built': 'Built',
    'gui.robotica.architect_plot_unqueue': 'Click to take it off the plan',
    'gui.robotica.architect_plot_select': 'Click to select: restyle or demolish just this plot',
    'gui.robotica.architect_plot_selected': 'Selected: pick a style to restyle it, or Demolish it',
    'gui.robotica.architect_plot_deselect': 'Click again or right-click to deselect',
    'gui.robotica.architect_plot_forget': 'Shift-click to forget (blocks stay)',
    'gui.robotica.architect_casing': 'Casing: unlocks styles',
    'gui.robotica.architect_battery': 'Battery: a wound Mainspring or any FE cell',
    'gui.robotica.architect_storage_hint': 'Stone, wood, ingots, glass, quartz and more turn into matter',
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
    'item.robotica.pocket_architect': 'Pocket Architect Mk1',
    'item.robotica.pocket_architect_mk2': 'Pocket Architect Mk2',
    'tooltip.robotica.pocket_architect': 'Turns junk you pick up into rustic matter, and matter into building blocks',
    'tooltip.robotica.pocket_architect_mk2': 'Turns junk you pick up into matter, and matter into building blocks of every style',
    'tooltip.robotica.pocket_architect_use': 'Right-click a block: place the picked block. Sneak-right-click: open. Sneak-right-click an Architect Table: move the matter into it',
    'tooltip.robotica.pocket_architect_matter': '%s %s',
    'tooltip.robotica.pocket_architect_places': 'Places: %s',
    'gui.robotica.pocket_architect_junk': 'Junk',
    'gui.robotica.pocket_architect_junk_slot': 'Click with an item to add it, click to remove it',
    'gui.robotica.pocket_architect_junk_on': 'Eat junk: ON',
    'gui.robotica.pocket_architect_junk_off': 'Eat junk: OFF',
    'gui.robotica.pocket_architect_junk_tip': 'Junk you pick up turns into matter',
    'gui.robotica.pocket_architect_feed': 'Wood, stone and dirt turn into matter',
    'gui.robotica.pocket_architect_feed_mk2': 'Wood, stone, ingots, glass and more turn into matter',
    'gui.robotica.pocket_architect_cost': 'Per block: %s rustic',
    'gui.robotica.pocket_architect_cost_refined': 'Per block: %s rustic, %s refined',
    'gui.robotica.pocket_architect_needs_mk2': 'Needs a Pocket Architect Mk2',
    'gui.robotica.pocket_architect_take': 'Click: take 1. Shift-click: take 64',
    'gui.robotica.pocket_architect_pick': 'Right-click: pick it for placing',
    'gui.robotica.pocket_architect_unpick': 'Right-click: unpick it',
    'message.robotica.pocket_architect_locked': 'That style needs a Pocket Architect Mk2',
    'message.robotica.pocket_architect_full': 'Inventory full',
    'message.robotica.pocket_architect_no_rustic': 'Not enough rustic matter',
    'message.robotica.pocket_architect_no_refined': 'Not enough refined matter',
    'message.robotica.pocket_architect_empty': 'The Pocket Architect is empty',
    'message.robotica.pocket_architect_table_full': 'The table is full',
    'message.robotica.pocket_architect_moved': 'Moved %s rustic and %s refined into the table',
    'message.robotica.pocket_architect_not_junk': 'Only items worth matter can be junk',
    'message.robotica.pocket_architect_not_junk_mk1': 'The Mk1 only takes items worth rustic matter',
})
# keep keys added by hand (demolish, config) and overwrite only the generated ones
_lang_file = FRAG / 'assets/robotica/lang/en_us.json'
_existing = json.loads(_lang_file.read_text()) if _lang_file.exists() else {}
# drop old generated names so removed styles leave no keys
_existing = {k: v for k, v in _existing.items()
             if not k.startswith(('block.robotica.', 'style.robotica.', 'gui.robotica.architect_needs_casing_'))}
write(_lang_file, {**lang, **{k: v for k, v in _existing.items() if k not in lang}})
print('architect models, loot, tags and lang written')
