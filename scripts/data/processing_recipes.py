"""Writes the recipes and data maps of the processing module (Grinder, Electric Furnace, dusts, grinding media).
Run: python3 scripts/data/processing_recipes.py
Writes (only files named after processing items, plus the processing data maps):
- data/robotica/recipe/<name>.json            crafting, Mk upgrades (robotica:machine_upgrade), dust smelting/blasting
- data/robotica/recipe/grinding/<name>.json   robotica:grinding recipes (unlocked by the "grinder" guide step)
- data/robotica/data_maps/item/grinding_media.json, grinding_byproducts.json

Ores, raw ores and ingots of every mod are ground by the generic c: tag rules in GrindingLogic; the recipes below only
cover what those rules can not know (stone, bones, redstone and lapis ores that drop many items, ...).
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = ROOT / 'src/main/resources/data/robotica'
OUT = DATA / 'recipe'
GRIND = OUT / 'grinding'


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def item_id(x):
    return x if ':' in x else f'robotica:{x}'


def shaped(name, pattern, key, count=1, result=None, kind='minecraft:crafting_shaped'):
    write(OUT / f'{name}.json', {'type': kind, 'category': 'misc', 'pattern': pattern,
                                 'key': {k: ing(v) for k, v in key.items()},
                                 'result': {'id': item_id(result or name), 'count': count}})


def upgrade(name, pattern, key):
    """Next Mk of a machine: keeps the energy and the carried contents of the machine in the grid."""
    shaped(name, pattern, key, kind='robotica:machine_upgrade')


def cooking(name, kind, ingredient, result, xp, time):
    write(OUT / f'{name}.json', {'type': f'minecraft:{kind}', 'category': 'misc', 'ingredient': ing(ingredient),
                                 'result': {'id': item_id(result)}, 'experience': xp, 'cookingtime': time})


def grinding(name, ingredient, result, count=1, extras=(), min_tier=None, by_tag=False):
    data = {'type': 'robotica:grinding', 'ingredient': ing(ingredient), 'result': {'id': item_id(result), 'count': count}}
    if extras:
        data['extras'] = [{'result': {'id': item_id(r), 'count': c}, 'chance': p} for r, c, p in extras]
    if min_tier:
        data['min_tier'] = min_tier
    if by_tag:
        data['by_tag'] = True
    write(GRIND / f'{name}.json', data)


IRON_PLATE = '#c:plates/iron'

# ---------------------------------------------------------------- machines
# Mk1 (Age 1, after the Basic Circuit like the Metal Press). Every later Mk consumes the one before plus two casings and
# a circuit of its age, and keeps energy and Carry contents (robotica:machine_upgrade).
shaped('grinder_mk1', ['PFP', 'GMG', 'PBP'],
       {'P': IRON_PLATE, 'F': 'minecraft:flint', 'G': 'copper_gear', 'M': 'electric_motor', 'B': 'basic_circuit'})
shaped('electric_furnace_mk1', ['PCP', 'CFC', 'PBP'],
       {'P': IRON_PLATE, 'C': 'copper_coil', 'F': 'minecraft:furnace', 'B': 'basic_circuit'})

TIERS = {
    2: ('advanced_circuit', 'reinforced_casing', '#c:gems/diamond', '#c:obsidians'),
    3: ('quantum_circuit', 'blazing_casing', 'minecraft:netherite_scrap', '#c:rods/blaze'),
    4: ('null_circuit', 'null_casing', 'minecraft:shulker_shell', '#c:ender_pearls'),
}
for mk, (circuit, casing, grinder_extra, furnace_extra) in TIERS.items():
    upgrade(f'grinder_mk{mk}', [' C ', 'XMX', 'A A'],
            {'C': circuit, 'X': casing, 'M': f'grinder_mk{mk - 1}', 'A': grinder_extra})
    upgrade(f'electric_furnace_mk{mk}', [' C ', 'XMX', 'A A'],
            {'C': circuit, 'X': casing, 'M': f'electric_furnace_mk{mk - 1}', 'A': furnace_extra})

# ---------------------------------------------------------------- dusts back to ingots
for metal, xp in (('iron', 0.7), ('gold', 1.0), ('copper', 0.7)):
    cooking(f'{metal}_ingot_from_smelting_{metal}_dust', 'smelting', f'{metal}_dust', f'minecraft:{metal}_ingot', xp, 200)
    cooking(f'{metal}_ingot_from_blasting_{metal}_dust', 'blasting', f'{metal}_dust', f'minecraft:{metal}_ingot', xp, 100)

# ---------------------------------------------------------------- grinding media
# Iron balls: one ingot and four nuggets make two, each lasts 16 ores at +25%: worth it from the first stack of ore.
shaped('iron_grinding_balls', [' N ', 'NIN', ' N '], {'N': '#c:nuggets/iron', 'I': '#c:ingots/iron'}, count=2)
# Alloy balls (stream B's alloys, matched by tag so any mod's ingot of the same name works): 4 ingots + one ball of the
# tier below make 4.
for name, alloy, below in (('ferrothorium_grinding_balls', 'ferrothorium', 'iron_grinding_balls'),
                           ('pyrosteel_grinding_balls', 'pyrosteel', 'ferrothorium_grinding_balls'),
                           ('resonant_grinding_balls', 'resonant_alloy', 'pyrosteel_grinding_balls')):
    shaped(name, [' A ', 'ABA', ' A '], {'A': f'#c:ingots/{alloy}', 'B': below}, count=4)

# ---------------------------------------------------------------- robotica:grinding
# Our dusts come from the generic tag rules; these by_tag recipes only document them for recipe lists and the Codex
# (the Grinder computes the real output from the tag rule and the config: oreDustCount, rawBonusChance, ...).
for metal in ('iron', 'gold', 'copper'):
    grinding(f'{metal}_ore_to_dust', f'#c:ores/{metal}', f'{metal}_dust', 2, by_tag=True)
    grinding(f'raw_{metal}_to_dust', f'#c:raw_materials/{metal}', f'{metal}_dust', 1,
             extras=[(f'{metal}_dust', 1, 0.25)], by_tag=True)
    grinding(f'{metal}_ingot_to_dust', f'#c:ingots/{metal}', f'{metal}_dust', 1, by_tag=True)

# Special cases the tag rules can not know.
grinding('cobblestone_to_gravel', '#c:cobblestones', 'minecraft:gravel')
grinding('gravel_to_sand', 'minecraft:gravel', 'minecraft:sand', extras=[('minecraft:flint', 1, 0.1)])
grinding('blaze_rod_to_powder', '#c:rods/blaze', 'minecraft:blaze_powder', 3, extras=[('minecraft:blaze_powder', 1, 0.5)])
grinding('bone_to_bone_meal', '#c:bones', 'minecraft:bone_meal', 6)
grinding('glowstone_to_dust', 'minecraft:glowstone', 'minecraft:glowstone_dust', 4)
grinding('wool_to_string', '#minecraft:wool', 'minecraft:string', 4)
grinding('redstone_ore', '#c:ores/redstone', 'minecraft:redstone', 6)
grinding('lapis_ore', '#c:ores/lapis', 'minecraft:lapis_lazuli', 8)
grinding('coal_ore', '#c:ores/coal', 'minecraft:coal', 2, extras=[('minecraft:coal', 1, 0.25)])

# ---------------------------------------------------------------- data maps
# Grinding media: bonus = extra main output on ores and raw ores, secondary = chance of a byproduct per ore,
# uses = ores per item, tier = lowest Grinder Mk it fits.
write(DATA / 'data_maps/item/grinding_media.json', {'values': {
    'minecraft:flint': {'bonus': 0.1, 'secondary': 0.02, 'uses': 8, 'tier': 0},
    'robotica:iron_grinding_balls': {'bonus': 0.25, 'secondary': 0.05, 'uses': 16, 'tier': 1},
    'robotica:ferrothorium_grinding_balls': {'bonus': 0.5, 'secondary': 0.1, 'uses': 32, 'tier': 2},
    'robotica:pyrosteel_grinding_balls': {'bonus': 1.0, 'secondary': 0.2, 'uses': 64, 'tier': 3},
    'robotica:resonant_grinding_balls': {'bonus': 1.5, 'secondary': 0.25, 'uses': 128, 'tier': 4},
}})

# Byproducts of grinding media: first entry that exists in the pack wins (nickel/silver/tin come from other mods).
BYPRODUCTS = {
    'iron': ['#c:dusts/nickel', '#c:dusts/tin', '#c:dusts/gold'],
    'gold': ['#c:dusts/silver', '#c:dusts/copper'],
    'copper': ['#c:dusts/gold'],
    'tin': ['#c:dusts/iron'],
    'lead': ['#c:dusts/silver'],
    'silver': ['#c:dusts/lead'],
    'nickel': ['#c:dusts/platinum', '#c:dusts/iron'],
    'osmium': ['#c:dusts/tin', '#c:dusts/iron'],
    'zinc': ['#c:dusts/lead', '#c:dusts/iron'],
    'uranium': ['#c:dusts/lead'],
}
values = {}
for metal, refs in BYPRODUCTS.items():
    values[f'#c:ores/{metal}'] = {'byproducts': refs}
    values[f'#c:raw_materials/{metal}'] = {'byproducts': refs}
values['#c:ores/redstone'] = {'byproducts': ['#c:dusts/glowstone']}
write(DATA / 'data_maps/item/grinding_byproducts.json', {'values': values})

print('processing recipes and data maps written')
