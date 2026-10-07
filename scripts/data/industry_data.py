"""Writes the data of the industry module: recipes, blockstates, block/item models, loot tables, tag fragments and the
reactor fuel data maps. Worldgen is written by scripts/data/industry_worldgen.py, textures by scripts/textures/industry.py.
Run: python3 scripts/data/industry_data.py   (overwrites files named after industry items only)

Recipe families (file name prefix):
- alloying_*      robotica:alloying     Alloy Smelter (3 inputs, 1 result)
- centrifuging_*  robotica:centrifuging Centrifuge (1 input, up to 4 results with chances)
- assembling_*    robotica:assembling   Assembler (up to 6 sized inputs, 1 result); also cheaper routes to ladder parts
- grinding_*      robotica:grinding     Grinder of the processing module (special outputs only; ores, raw materials and
                                        ingots are ground by tag there)
- metal_press_*   robotica:pressing     plates
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/robotica'
DATA = RES / 'data/robotica'
RECIPES = DATA / 'recipe'
FRAG = ROOT / 'src/main/fragments/industry'

MACHINES = ('alloy_smelter', 'centrifuge', 'assembler')
TIERS = (1, 2, 3, 4)
ORES = ('thorium_ore', 'deepslate_thorium_ore', 'pyrolite_ore', 'resonite_ore')
STORAGE = ('raw_thorium_block', 'thorium_block', 'pyrolite_block', 'resonite_block')
GLOW_ORES = ('pyrolite_ore', 'resonite_ore', 'pyrolite_block', 'resonite_block')


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def rid(x):
    return x if ':' in x else f'robotica:{x}'


def ing(x):
    """'#tag', 'item' or a list of those (any of them)."""
    if isinstance(x, (list, tuple)):
        return [ing(i) for i in x]
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': rid(x)}


# =================================================================================================== recipes

def recipe(name, obj):
    write(RECIPES / f'{name}.json', obj)


def shaped(name, pattern, key, result=None, count=1):
    recipe(name, {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                  'key': {k: ing(v) for k, v in key.items()}, 'result': {'id': rid(result or name), 'count': count}})


def shapeless(name, ingredients, result, count=1):
    recipe(name, {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                  'ingredients': [ing(i) for i in ingredients], 'result': {'id': rid(result), 'count': count}})


def cooking(name, ingredient, result, xp=0.7):
    recipe(name, {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': ing(ingredient),
                  'result': {'id': rid(result)}, 'experience': xp, 'cookingtime': 200})
    recipe(name.replace('smelting', 'blasting'),
           {'type': 'minecraft:blasting', 'category': 'misc', 'ingredient': ing(ingredient),
            'result': {'id': rid(result)}, 'experience': xp, 'cookingtime': 100})


def pressing(name, ingredient, result, time=None):
    data = {'type': 'robotica:pressing', 'ingredient': ing(ingredient), 'result': {'id': rid(result), 'count': 1}}
    if time:
        data['time'] = time
    recipe(name, data)


def sized(x, count=1):
    return {'ingredient': ing(x), 'count': count}


def result(item, count=1, chance=None):
    r = {'id': rid(item)}
    if count != 1:
        r['count'] = count
    if chance is not None:
        r['chance'] = chance
    return r


def processing(kind, name, inputs, results, time=None, power=None, fortune=None):
    """inputs: list of (ingredient, count) or ingredient; results: list of result() dicts.
    fortune: whether Fortune cards may add a main result; left out it is the machine default (alloying only)."""
    data = {'type': f'robotica:{kind}',
            'inputs': [sized(*i) if isinstance(i, tuple) else sized(i) for i in inputs],
            'results': results}
    if time:
        data['time'] = time
    if power:
        data['power'] = power
    if fortune is not None:
        data['fortune'] = fortune
    recipe(f'{kind}_{name}', data)


def grinding(name, ingredient, item, count=1, extras=None):
    """Special Grinder outputs for the processing module (its recipe type, see the module docs)."""
    data = {'type': 'robotica:grinding', 'ingredient': ing(ingredient), 'result': {'id': rid(item), 'count': count}}
    if extras:
        data['extras'] = [{'result': {'id': rid(i), 'count': c}, 'chance': ch} for i, c, ch in extras]
    recipe(f'grinding_{name}', data)


HAMMER = 'tinkers_hammer'
IRON_PLATE, COPPER_PLATE, GOLD_PLATE = '#c:plates/iron', '#c:plates/copper', '#c:plates/gold'
THORIUM = ['#c:ingots/thorium', '#c:dusts/thorium']
PYROLITE = ['#c:gems/pyrolite', '#c:dusts/pyrolite']
RESONITE = ['#c:gems/resonite', '#c:dusts/resonite']
FERRO, PYRO, RESO = '#c:plates/ferrothorium', '#c:plates/pyrosteel', '#c:plates/resonant_alloy'

# Per age: alloy plate, circuit, Assembler-only part. Higher Mks of a machine are the previous Mk + 5 plates + these.
AGE_PARTS = {
    2: (FERRO, 'advanced_circuit', 'thermocouple'),
    3: (PYRO, 'quantum_circuit', 'superconductor_coil'),
    4: (RESO, 'null_circuit', 'resonant_lattice'),
}
BASE_AGE = {'alloy_smelter': 1, 'centrifuge': 2, 'assembler': 2}


def recipes():
    for old in RECIPES.glob('*.json'):
        if old.stem.startswith(('alloying_', 'centrifuging_', 'assembling_', 'grinding_')):
            old.unlink()

    # ---- storage blocks, smelting, hand plates and dusts
    shaped('raw_thorium_block', ['RRR', 'RRR', 'RRR'], {'R': '#c:raw_materials/thorium'})
    shapeless('raw_thorium_from_block', ['#c:storage_blocks/raw_thorium'], 'raw_thorium', 9)
    shaped('thorium_block', ['III', 'III', 'III'], {'I': '#c:ingots/thorium'})
    shapeless('thorium_ingot_from_block', ['#c:storage_blocks/thorium'], 'thorium_ingot', 9)
    shaped('pyrolite_block', ['GGG', 'GGG', 'GGG'], {'G': '#c:gems/pyrolite'})
    shapeless('pyrolite_shard_from_block', ['#c:storage_blocks/pyrolite'], 'pyrolite_shard', 9)
    shaped('resonite_block', ['GGG', 'GGG', 'GGG'], {'G': '#c:gems/resonite'})
    shapeless('resonite_crystal_from_block', ['#c:storage_blocks/resonite'], 'resonite_crystal', 9)

    cooking('thorium_ingot_from_smelting', '#c:raw_materials/thorium', 'thorium_ingot')
    cooking('thorium_ingot_from_dust_smelting', '#c:dusts/thorium', 'thorium_ingot', 0.2)
    cooking('thorium_ingot_from_ore_smelting', '#c:ores/thorium', 'thorium_ingot')
    cooking('pyrolite_shard_from_ore_smelting', '#c:ores/pyrolite', 'pyrolite_shard', 1.0)
    cooking('resonite_crystal_from_ore_smelting', '#c:ores/resonite', 'resonite_crystal', 1.5)

    # The Tinker's Hammer cracks a material into one dust (no gain: the Grinder of the processing module doubles ores).
    shapeless('thorium_dust_from_hammer', [HAMMER, '#c:raw_materials/thorium'], 'thorium_dust')
    shapeless('pyrolite_dust_from_hammer', [HAMMER, '#c:gems/pyrolite'], 'pyrolite_dust')
    shapeless('resonite_dust_from_hammer', [HAMMER, '#c:gems/resonite'], 'resonite_dust')
    shapeless('graphite_dust_from_hammer', [HAMMER, '#minecraft:coals'], 'graphite_dust')

    for metal, time in (('thorium', 100), ('ferrothorium', 120), ('pyrosteel', 160), ('resonant_alloy', 200)):
        shapeless(f'{metal}_plate_from_hammer', [HAMMER, f'#c:ingots/{metal}', f'#c:ingots/{metal}'], f'{metal}_plate')
        pressing(f'metal_press_{metal}_plate', f'#c:ingots/{metal}', f'{metal}_plate', time)

    # ---- Grinder (processing module) special outputs
    grinding('graphite_dust', '#minecraft:coals', 'graphite_dust')
    grinding('pyrolite_dust', '#c:gems/pyrolite', 'pyrolite_dust')
    grinding('resonite_dust', '#c:gems/resonite', 'resonite_dust')

    # ---- Alloy Smelter: Robotica alloys exist only here
    processing('alloying', 'ferrothorium', [['#c:ingots/iron', '#c:dusts/iron'], THORIUM],
               [result('ferrothorium_ingot')], time=200)
    processing('alloying', 'pyrosteel', ['#c:ingots/ferrothorium', PYROLITE, 'minecraft:blaze_powder'],
               [result('pyrosteel_ingot')], time=300, power=120)
    processing('alloying', 'resonant_alloy', ['#c:ingots/pyrosteel', RESONITE, '#c:ender_pearls'],
               [result('resonant_alloy_ingot')], time=400, power=400)

    # ---- Centrifuge
    # The rare result goes first: recipe viewers and the item audit read the first result.
    processing('centrifuging', 'depleted_fuel_pellet', ['depleted_fuel_pellet'],
               [result('radiant_isotope', chance=0.1), result('thorium_dust'), result('graphite_dust', chance=0.5)],
               time=400, power=100)
    processing('centrifuging', 'magma_cream', ['minecraft:magma_cream'],
               [result('minecraft:slime_ball'), result('minecraft:blaze_powder')], time=200)
    processing('centrifuging', 'pyrolite_dust', ['#c:dusts/pyrolite'],
               [result('minecraft:glowstone_dust', 2), result('minecraft:blaze_powder', chance=0.25)], time=200)
    processing('centrifuging', 'glistering_melon_slice', ['minecraft:glistering_melon_slice'],
               [result('minecraft:melon_slice'), result('minecraft:gold_nugget', 4)], time=160)

    # ---- Assembler: parts that exist only here ...
    processing('assembling', 'thermocouple', [('#c:plates/thorium', 2), (COPPER_PLATE, 2), 'basic_circuit'],
               [result('thermocouple')], time=300)
    processing('assembling', 'superconductor_coil', [(PYRO, 4), ('copper_coil', 2), ('#c:dusts/graphite', 2), 'quantum_circuit'],
               [result('superconductor_coil')], time=600, power=400)
    # Gem or dust: the Grinder turns resonite ore into dust, and nothing turns dust back into crystals.
    processing('assembling', 'resonant_lattice', [(RESO, 4), (RESONITE, 2), 'superconductor_coil', 'null_circuit'],
               [result('resonant_lattice')], time=800, power=1500)
    # ... the reactor fuel ...
    processing('assembling', 'thorium_fuel_pellet', [('#c:dusts/thorium', 2), '#c:dusts/graphite', FERRO],
               [result('thorium_fuel_pellet')], time=200)
    processing('assembling', 'enriched_fuel_pellet', ['thorium_fuel_pellet', PYROLITE, 'minecraft:magma_cream'],
               [result('enriched_fuel_pellet')], time=300, power=200)
    processing('assembling', 'fusion_fuel_pellet', [('#c:dusts/resonite', 2), 'radiant_isotope'],
               [result('fusion_fuel_pellet')], time=400, power=1000)
    # ... and cheaper routes to the ladder parts than the crafting table (about a quarter less raw material).
    processing('assembling', 'basic_circuit', [('#c:dusts/redstone', 2), GOLD_PLATE, (IRON_PLATE, 2), COPPER_PLATE],
               [result('basic_circuit')], time=200)
    processing('assembling', 'electric_motor', [(IRON_PLATE, 3), ('copper_coil', 2), '#c:dusts/redstone', 'clockwork_mechanism'],
               [result('electric_motor')], time=200)
    processing('assembling', 'advanced_circuit', [('basic_circuit', 3), (GOLD_PLATE, 2), '#c:gems/quartz', '#c:gems/diamond', FERRO],
               [result('advanced_circuit')], time=300, power=160)
    processing('assembling', 'servo_actuator', [('electric_motor', 2), (GOLD_PLATE, 2), (FERRO, 2), 'advanced_circuit'],
               [result('servo_actuator')], time=300, power=160)
    processing('assembling', 'reinforced_casing', [('iron_casing', 3), ('#c:obsidians', 2), '#c:gems/diamond', (FERRO, 2)],
               [result('reinforced_casing')], time=300, power=160)
    processing('assembling', 'quantum_circuit', [('advanced_circuit', 3), ('#c:rods/blaze', 2), '#c:ingots/netherite', (PYRO, 2)],
               [result('quantum_circuit')], time=400, power=400)
    processing('assembling', 'plasma_actuator', [('servo_actuator', 2), ('minecraft:prismarine_crystals', 2), 'minecraft:blaze_powder',
                                                 'quantum_circuit', (PYRO, 2)],
               [result('plasma_actuator')], time=400, power=400)
    processing('assembling', 'blazing_casing', [('reinforced_casing', 3), ('minecraft:netherite_scrap', 2), 'magma_core', (PYRO, 2)],
               [result('blazing_casing', 2)], time=400, power=400)
    processing('assembling', 'null_circuit', [('quantum_circuit', 3), ('#c:ender_pearls', 2), '#c:nether_stars', (RESO, 2)],
               [result('null_circuit', 2)], time=600, power=1000)
    processing('assembling', 'null_casing', [('blazing_casing', 3), ('minecraft:shulker_shell', 2), 'antigrav_core', (RESO, 2)],
               [result('null_casing', 2)], time=600, power=1000)

    # ---- machines
    shaped('alloy_smelter_mk1', ['PKP', 'FCF', 'PBP'],
           {'P': IRON_PLATE, 'K': 'copper_coil', 'F': 'minecraft:furnace', 'C': 'iron_casing', 'B': 'basic_circuit'})
    shaped('centrifuge_mk1', ['PAP', 'MGM', 'PXP'],
           {'P': FERRO, 'A': 'advanced_circuit', 'M': 'electric_motor', 'G': 'minecraft:cauldron', 'X': 'iron_casing'})
    shaped('assembler_mk1', ['PAP', 'MTM', 'PSP'],
           {'P': FERRO, 'A': 'advanced_circuit', 'M': 'electric_motor', 'T': 'minecraft:crafting_table', 'S': 'servo_actuator'})
    for machine in MACHINES:
        for tier in TIERS[1:]:
            age = min(4, BASE_AGE[machine] + tier - 1)
            plate, circuit, special = AGE_PARTS[age]
            shaped(f'{machine}_mk{tier}', ['PCP', 'PMP', 'PSP'],
                   {'P': plate, 'C': circuit, 'S': special, 'M': f'{machine}_mk{tier - 1}'})
    shaped('rtg', ['PTP', 'GXG', 'PTP'],
           {'P': FERRO, 'T': 'thermocouple', 'G': '#c:glass_blocks', 'X': 'reinforced_casing'})


# =================================================================================================== models and loot

def tex(name):
    return f'robotica:block/{name}'


GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def cube(textures, glow=None):
    """Full cube with per-face textures (dir -> texture name) and an optional full-bright overlay (dir -> texture)."""
    tv = {d: tex(t) for d, t in textures.items()}
    tv['particle'] = tex(textures.get('south', textures['north']))
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16],
                 'faces': {d: {'texture': '#' + d, 'cullface': d} for d in DIRS}}]
    if glow:
        for d, t in glow.items():
            tv['glow_' + d] = tex(t)
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {d: {'texture': '#glow_' + d, 'cullface': d, 'neoforge_data': dict(GLOW)} for d in glow}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': tv, 'elements': elements}


def block_model(name, obj):
    write(ASSETS / 'models/block' / f'{name}.json', obj)


def item_block(name, model=None):
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{model or name}'})


def simple_blockstate(name):
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{name}'}}})


def machine_blockstate(name):
    variants = {}
    for lit in (False, True):
        for facing, y in FACING_Y.items():
            entry = {'model': f'robotica:block/{name}' + ('_on' if lit else '')}
            if y:
                entry['y'] = y
            variants[f'facing={facing},lit={str(lit).lower()}'] = entry
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': variants})


def loot_self(name):
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': rid(name)}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}'})


def loot_ore(name, drop):
    silk = {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [
        {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'conditions': [silk], 'name': rid(name)},
            {'type': 'minecraft:item', 'name': rid(drop), 'functions': [
                {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                {'function': 'minecraft:explosion_decay'}]}]}]}],
        'random_sequence': f'robotica:blocks/{name}'})


def models():
    for name in ORES + STORAGE:
        if name in GLOW_ORES:
            block_model(name, cube({d: name for d in DIRS}, {d: name + '_glow' for d in DIRS}))
        else:
            block_model(name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(name)}})
        simple_blockstate(name)
        item_block(name)
    for name, drop in (('thorium_ore', 'raw_thorium'), ('deepslate_thorium_ore', 'raw_thorium'),
                       ('pyrolite_ore', 'pyrolite_shard'), ('resonite_ore', 'resonite_crystal')):
        loot_ore(name, drop)
    for name in STORAGE:
        loot_self(name)

    for machine in MACHINES:
        for tier in TIERS:
            name = f'{machine}_mk{tier}'
            side = f'industry_side_mk{tier}'
            faces = {'down': 'industry_bottom', 'up': f'{machine}_top', 'south': side, 'west': side, 'east': side}
            block_model(name, cube(dict(faces, north=f'{name}_front')))
            block_model(name + '_on', cube(dict(faces, north=f'{name}_front_on'), {'north': f'{machine}_front_glow'}))
            machine_blockstate(name)
            item_block(name)
            loot_self(name)
    faces = {'down': 'industry_bottom', 'up': 'rtg_top', 'south': 'rtg_side', 'west': 'rtg_side', 'east': 'rtg_side'}
    block_model('rtg', cube(dict(faces, north='rtg_front')))
    block_model('rtg_on', cube(dict(faces, north='rtg_front'), {'north': 'rtg_front_glow', 'up': 'rtg_top_glow'}))
    machine_blockstate('rtg')
    item_block('rtg')
    loot_self('rtg')


# =================================================================================================== tags and data maps

def tag(kind, path, values):
    """kind: 'block' or 'item'; path like 'c:ores/thorium' or 'minecraft:mineable/pickaxe'."""
    ns, p = path.split(':')
    write(FRAG / f'data/{ns}/tags/{kind}/{p}.json', {'replace': False, 'values': [v if v.startswith('#') or ':' in v else rid(v) for v in values]})


def tags():
    machines = [f'{m}_mk{t}' for m in MACHINES for t in TIERS] + ['rtg']
    tag('block', 'minecraft:mineable/pickaxe', list(ORES) + list(STORAGE) + machines)
    tag('block', 'minecraft:needs_stone_tool', ['thorium_ore', 'deepslate_thorium_ore', 'raw_thorium_block', 'thorium_block'])
    tag('block', 'minecraft:needs_iron_tool', ['pyrolite_ore', 'pyrolite_block'])
    tag('block', 'minecraft:needs_diamond_tool', ['resonite_ore', 'resonite_block'])
    for kind in ('block', 'item'):
        tag(kind, 'c:ores', ['#c:ores/thorium', '#c:ores/pyrolite', '#c:ores/resonite'])
        tag(kind, 'c:ores/thorium', ['thorium_ore', 'deepslate_thorium_ore'])
        tag(kind, 'c:ores/pyrolite', ['pyrolite_ore'])
        tag(kind, 'c:ores/resonite', ['resonite_ore'])
        tag(kind, 'c:ores_in_ground/stone', ['thorium_ore'])
        tag(kind, 'c:ores_in_ground/deepslate', ['deepslate_thorium_ore'])
        tag(kind, 'c:ores_in_ground/netherrack', ['pyrolite_ore'])
        tag(kind, 'c:ore_rates/singular', list(ORES))
        tag(kind, 'c:storage_blocks', ['#c:storage_blocks/thorium', '#c:storage_blocks/raw_thorium',
                                       '#c:storage_blocks/pyrolite', '#c:storage_blocks/resonite'])
        tag(kind, 'c:storage_blocks/thorium', ['thorium_block'])
        tag(kind, 'c:storage_blocks/raw_thorium', ['raw_thorium_block'])
        tag(kind, 'c:storage_blocks/pyrolite', ['pyrolite_block'])
        tag(kind, 'c:storage_blocks/resonite', ['resonite_block'])
    tag('item', 'c:raw_materials', ['#c:raw_materials/thorium'])
    tag('item', 'c:raw_materials/thorium', ['raw_thorium'])
    ingots = ('thorium', 'ferrothorium', 'pyrosteel', 'resonant_alloy')
    tag('item', 'c:ingots', [f'#c:ingots/{m}' for m in ingots])
    for m in ingots:
        tag('item', f'c:ingots/{m}', [f'{m}_ingot'])
    tag('item', 'c:plates', [f'#c:plates/{m}' for m in ingots])
    for m in ingots:
        tag('item', f'c:plates/{m}', [f'{m}_plate'])
    dusts = ('thorium', 'pyrolite', 'resonite', 'graphite')
    tag('item', 'c:dusts', [f'#c:dusts/{m}' for m in dusts])
    for m in dusts:
        tag('item', f'c:dusts/{m}', [f'{m}_dust'])
    tag('item', 'c:gems', ['#c:gems/pyrolite', '#c:gems/resonite'])
    tag('item', 'c:gems/pyrolite', ['pyrolite_shard'])
    tag('item', 'c:gems/resonite', ['resonite_crystal'])
    tag('item', 'robotica:rtg_fuel', ['thorium_fuel_pellet'])


def data_maps():
    """Fuel stats for the big energy module's reactors (contract: docs/plans/0.3-armor-power.md)."""
    waste = 'robotica:depleted_fuel_pellet'
    write(DATA / 'data_maps/item/reactor_fuel.json', {'values': {
        'robotica:thorium_fuel_pellet': {'heat': 400, 'ticks': 12000, 'waste': waste},
        'robotica:enriched_fuel_pellet': {'heat': 1000, 'ticks': 12000, 'waste': waste},
    }})
    write(DATA / 'data_maps/item/fusion_fuel.json', {'values': {
        'robotica:fusion_fuel_pellet': {'power': 200000, 'ticks': 2400},
    }})


# =================================================================================================== lang

NAMES = {
    'block': {
        'thorium_ore': 'Thorium Ore', 'deepslate_thorium_ore': 'Deepslate Thorium Ore', 'pyrolite_ore': 'Pyrolite Ore',
        'resonite_ore': 'Resonite Ore', 'raw_thorium_block': 'Block of Raw Thorium', 'thorium_block': 'Block of Thorium',
        'pyrolite_block': 'Block of Pyrolite', 'resonite_block': 'Block of Resonite', 'rtg': 'Radioisotope Generator',
    },
    'item': {
        'raw_thorium': 'Raw Thorium', 'thorium_ingot': 'Thorium Ingot', 'thorium_dust': 'Thorium Dust',
        'thorium_plate': 'Thorium Plate', 'graphite_dust': 'Graphite Dust', 'pyrolite_shard': 'Pyrolite Shard',
        'pyrolite_dust': 'Pyrolite Dust', 'resonite_crystal': 'Resonite Crystal', 'resonite_dust': 'Resonite Dust',
        'ferrothorium_ingot': 'Ferrothorium Ingot', 'ferrothorium_plate': 'Ferrothorium Plate',
        'pyrosteel_ingot': 'Pyrosteel Ingot', 'pyrosteel_plate': 'Pyrosteel Plate',
        'resonant_alloy_ingot': 'Resonant Alloy Ingot', 'resonant_alloy_plate': 'Resonant Alloy Plate',
        'thermocouple': 'Thermocouple', 'superconductor_coil': 'Superconductor Coil', 'resonant_lattice': 'Resonant Lattice',
        'thorium_fuel_pellet': 'Thorium Fuel Pellet', 'enriched_fuel_pellet': 'Enriched Fuel Pellet',
        'depleted_fuel_pellet': 'Depleted Fuel Pellet', 'radiant_isotope': 'Radiant Isotope',
        'fusion_fuel_pellet': 'Fusion Fuel Pellet',
    },
}
MACHINE_NAMES = {'alloy_smelter': 'Alloy Smelter', 'centrifuge': 'Centrifuge', 'assembler': 'Assembler'}

# Shift details: one short line, only what the name and the normal tooltip do not say.
DETAILS = {
    'thorium_ore': 'Overworld, Y -48 to 32',
    'deepslate_thorium_ore': 'Overworld, Y -48 to 32',
    'pyrolite_ore': 'Nether, richest in basalt deltas',
    'resonite_ore': 'Outer End islands. Rare',
    'graphite_dust': 'Ground coal or charcoal',
    'ferrothorium_ingot': 'Alloy Smelter: iron + thorium',
    'pyrosteel_ingot': 'Alloy Smelter: ferrothorium + pyrolite + blaze powder',
    'resonant_alloy_ingot': 'Alloy Smelter: pyrosteel + resonite + ender pearl',
    'thermocouple': 'Assembler only',
    'superconductor_coil': 'Assembler only',
    'resonant_lattice': 'Assembler only',
    'thorium_fuel_pellet': 'RTG: 150 FE/t for 20 minutes. Also reactor fuel',
    'enriched_fuel_pellet': '2.5x the heat of a thorium pellet',
    'depleted_fuel_pellet': 'Centrifuge: thorium dust, rarely a Radiant Isotope',
    'radiant_isotope': 'Centrifuge: 10% from Depleted Fuel Pellets',
}


def lang():
    out = {}
    for kind, names in NAMES.items():
        for k, v in names.items():
            out[f'{kind}.robotica.{k}'] = v
    for m, name in MACHINE_NAMES.items():
        for t in TIERS:
            out[f'block.robotica.{m}_mk{t}'] = f'{name} Mk{t}'
    for k, v in DETAILS.items():
        out[f'tooltip.robotica.{k}.details'] = v
    out.update({
        'tooltip.robotica.alloy_smelter': 'Smelts Robotica alloys',
        'tooltip.robotica.centrifuge': 'Separates materials',
        'tooltip.robotica.assembler': 'Builds parts from plates and circuits',
        'tooltip.robotica.industry_tier': 'Mk%s: %sx speed, %s upgrade slots',
        'tooltip.robotica.industry_upgrade': 'Right-click the placed previous Mk to upgrade it',
        'tooltip.robotica.rtg': 'Burns Thorium Fuel Pellets: %s FE/t',
        'tooltip.robotica.rtg_pellet': 'One pellet lasts %s minutes',
        'message.robotica.machine_upgraded': 'Upgraded to %s',
        'gui.robotica.industry_using': 'Using %s FE/t',
        'gui.robotica.industry_time': '%s s per craft',
        'gui.robotica.industry_use_tip': 'FE/t it draws right now. The Mk and speed cards raise it, efficiency cards lower it',
        'gui.robotica.slot_waste': 'Waste',
        'gui.robotica.rtg_left': 'This pellet: %s left',
        'gui.robotica.rtg_decaying': 'Decaying: %s FE/t',
        'gui.robotica.rtg_full': 'Paused: buffer full',
        'gui.robotica.rtg_no_fuel': 'No fuel pellet',
        'jei.robotica.category.alloying': 'Alloy Smelter',
        'jei.robotica.category.centrifuging': 'Centrifuge',
        'jei.robotica.category.assembling': 'Assembler',
        'jei.robotica.chance': '%s%% chance',
        'jei.robotica.info.thorium_ore': 'Found in the Overworld from Y -48 to 32 (most around Y -8), in stone and deepslate. Common enough to mine by hand. Drops Raw Thorium.',
        'jei.robotica.info.pyrolite_ore': 'Found in Nether netherrack, more of it in basalt deltas. Drops a Pyrolite Shard. Needs an iron pickaxe.',
        'jei.robotica.info.resonite_ore': 'Found in end stone on the outer End islands (not the main island). Rare. Drops a Resonite Crystal. Needs a diamond pickaxe.',
        'jei.robotica.info.depleted_fuel_pellet': 'Waste of a burned fuel pellet, from the RTG or a fission reactor. Put it in a Centrifuge: thorium dust back, sometimes graphite, and a 10% chance of a Radiant Isotope.',
        'jei.robotica.info.thorium_fuel_pellet': 'RTG fuel: %s FE/t for %s minutes, one pellet at a time. Also fuel for the fission reactor, which gets more out of it.',
        'robotica.configuration.machines': 'Industry machines',
        'robotica.configuration.alloySmelterPower': 'Alloy Smelter power (FE/t)',
        'robotica.configuration.centrifugePower': 'Centrifuge power (FE/t)',
        'robotica.configuration.assemblerPower': 'Assembler power (FE/t)',
        'robotica.configuration.machineBuffer': 'Machine buffer (FE)',
        'robotica.configuration.machineInput': 'Machine input (FE/t)',
        'robotica.configuration.fortuneBonus': 'Fortune card bonus (% per card)',
        'robotica.configuration.rtg': 'Radioisotope Generator',
        'robotica.configuration.rtgPower': 'Output (FE/t)',
        'robotica.configuration.rtgPelletTicks': 'Ticks per pellet',
        'robotica.configuration.rtgBuffer': 'Buffer (FE)',
        'robotica.configuration.rtgOutput': 'Max push (FE/t)',
    })
    for t in TIERS:
        out[f'robotica.configuration.tierSpeedMk{t}'] = f'Mk{t} speed'
    write(FRAG / 'assets/robotica/lang/en_us.json', dict(sorted(out.items())))


if __name__ == '__main__':
    lang()
    recipes()
    models()
    tags()
    data_maps()
    print('industry data written')
