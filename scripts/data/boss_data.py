"""Writes the data files of the boss module: recipes, loot tables, worldgen (structure + structure set), and the
Colossus Altar blockstate and models. Textures: scripts/textures/boss.py. Structure template: scripts/data/boss_structure.py.
Run: python3 scripts/data/boss_data.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from pixelart import glow_cube  # noqa: E402

RES = ROOT / 'src/main/resources'
DATA = RES / 'data/robotica'
ASSETS = RES / 'assets/robotica'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1, category='misc'):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
        'key': {k: ing(v) for k, v in key.items()}, 'result': {'id': f'robotica:{name}', 'count': count}})


def item(name, lo=1, hi=None, weight=1, looting=None):
    entry = {'type': 'minecraft:item', 'name': name if ':' in name else f'robotica:{name}', 'weight': weight}
    functions = []
    if hi is not None and (lo, hi) != (1, 1):
        functions.append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}})
    if looting:
        functions.append({'function': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting',
                          'count': {'type': 'minecraft:uniform', 'min': 0, 'max': looting}})
    if functions:
        entry['functions'] = functions
    return entry


def pool(entries, rolls=1, conditions=None):
    p = {'rolls': rolls, 'bonus_rolls': 0.0, 'entries': entries}
    if conditions:
        p['conditions'] = conditions
    return p


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min': lo, 'max': hi}


def loot(path, kind, pools):
    write(DATA / f'loot_table/{path}.json', {'type': kind, 'pools': pools, 'random_sequence': f'robotica:{path}'})


def recipes():
    # Age 1-2 cost: one Electric Motor (the first-iron mechanism) plus copper, redstone and gunpowder. The fight is the price.
    shaped('signal_flare', ['CGC', 'RMR', 'C C'],
           {'C': '#c:ingots/copper', 'G': '#c:gunpowders', 'R': '#c:dusts/redstone', 'M': 'electric_motor'})
    # Age 2, expensive (about 340 IE): four Reinforced Casings around a Servo Actuator. No Servo Core needed, so servers
    # without the Rusted Foundry (config) can still build an arena.
    shaped('colossus_altar', ['RAR', 'OSO', 'RBR'],
           {'R': 'reinforced_casing', 'A': 'advanced_circuit', 'O': '#c:obsidians', 'S': 'servo_actuator',
            'B': '#c:storage_blocks/copper'})


def loot_tables():
    loot('entities/scrap_colossus', 'minecraft:entity', [
        pool([item('servo_core')]),
        pool([item('minecraft:copper_ingot', 6, 12, looting=3)]),
        pool([item('minecraft:iron_ingot', 3, 7, looting=2)]),
        pool([item('minecraft:raw_copper', 4, 8, weight=3), item('minecraft:iron_nugget', 8, 16, weight=3),
              item('minecraft:redstone', 4, 10, weight=2), item('copper_gear', 2, 4, weight=2),
              item('electric_motor', weight=1)], rolls=2),
    ])
    loot('entities/scrap_drone', 'minecraft:entity', [
        pool([item('minecraft:iron_nugget', 0, 2, looting=1)]),
        pool([item('minecraft:redstone', 0, 1)]),
    ])
    write(DATA / 'loot_table/blocks/colossus_altar.json', {
        'type': 'minecraft:block', 'random_sequence': 'robotica:blocks/colossus_altar',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                   'entries': [{'type': 'minecraft:alternatives', 'children': [
                       {'type': 'minecraft:item', 'name': 'robotica:colossus_altar', 'conditions': [
                           {'condition': 'minecraft:block_state_property', 'block': 'robotica:colossus_altar',
                            'properties': {'natural': 'false'}}]},
                       # the ruin's altar stays a ruin piece: mining it gives copper, not a free altar
                       {'type': 'minecraft:item', 'name': 'minecraft:copper_block', 'functions': [
                           {'function': 'minecraft:set_count', 'count': 2}]}]}]}]})
    loot('chests/rusted_foundry', 'minecraft:chest', [
        pool([item('signal_flare')]),
        pool([item('signal_flare')], conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.3}]),
        pool([item('minecraft:copper_ingot', 2, 6, weight=4), item('minecraft:iron_ingot', 1, 4, weight=3),
              item('minecraft:redstone', 2, 6, weight=3), item('minecraft:gunpowder', 1, 3, weight=2),
              item('copper_gear', 1, 3, weight=3), item('iron_plate', 1, 3, weight=2), item('copper_coil', 1, 2, weight=1),
              item('basic_circuit', weight=1), item('electric_motor', weight=1)], rolls=uniform(3, 6)),
    ])


def worldgen():
    write(DATA / 'worldgen/structure/rusted_foundry.json', {
        'type': 'robotica:rusted_foundry', 'biomes': '#robotica:has_structure/rusted_foundry',
        'step': 'surface_structures', 'spawn_overrides': {}, 'terrain_adaptation': 'beard_thin'})
    write(DATA / 'worldgen/structure_set/rusted_foundry.json', {
        'structures': [{'structure': 'robotica:rusted_foundry', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': 48, 'separation': 16, 'salt': 19071905,
                      'exclusion_zone': {'other_set': 'minecraft:villages', 'chunk_count': 4}}})


def altar_models():
    tex = {'side': 'robotica:block/colossus_altar_side', 'top': 'robotica:block/colossus_altar_top',
           'bottom': 'robotica:block/colossus_altar_bottom', 'particle': 'robotica:block/colossus_altar_side'}
    faces = {'down': '#bottom', 'up': '#top', 'north': '#side', 'south': '#side', 'west': '#side', 'east': '#side'}
    lit = dict(tex, side_glow='robotica:block/colossus_altar_side_glow', top_glow='robotica:block/colossus_altar_top_glow')
    glow = {'up': '#top_glow', 'north': '#side_glow', 'south': '#side_glow', 'west': '#side_glow', 'east': '#side_glow'}
    write(ASSETS / 'models/block/colossus_altar.json', glow_cube(lit, faces, glow))
    write(ASSETS / 'models/block/colossus_altar_cold.json', glow_cube(tex, faces, {}))
    write(ASSETS / 'models/item/colossus_altar.json', {'parent': 'robotica:block/colossus_altar'})
    variants = {}
    for natural in ('false', 'true'):
        for ready in ('false', 'true'):
            model = 'robotica:block/colossus_altar' if ready == 'true' else 'robotica:block/colossus_altar_cold'
            variants[f'natural={natural},ready={ready}'] = {'model': model}
    write(ASSETS / 'blockstates/colossus_altar.json', {'variants': variants})


def main():
    recipes()
    loot_tables()
    worldgen()
    altar_models()
    print('boss data written')


if __name__ == '__main__':
    main()
