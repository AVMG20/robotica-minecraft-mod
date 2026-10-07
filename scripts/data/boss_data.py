"""Writes the data files of the boss module: recipes, loot tables, worldgen (structures + structure sets), and the
Colossus Altar and Forge Altar blockstates and models. Textures: scripts/textures/boss.py. Structure template: scripts/data/boss_structure.py.
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
    # Age 3 summon: an Age 2 mechanism (Servo Actuator), blaze rods, gold and a fire charge. The fight is the price.
    shaped('ignition_charge', ['GFG', 'BSB', 'G G'],
           {'G': '#c:ingots/gold', 'F': 'minecraft:fire_charge', 'B': '#c:rods/blaze', 'S': 'servo_actuator'})
    # Age 3 arena, expensive: four Reinforced Casings, a Quantum Circuit and a Plasma Actuator. No Magma Core needed, so
    # servers without the Cinder Forge (config) can still build an arena.
    shaped('forge_altar', ['RQR', 'NPN', 'RMR'],
           {'R': 'reinforced_casing', 'Q': 'quantum_circuit', 'N': 'minecraft:netherite_scrap', 'P': 'plasma_actuator',
            'M': 'minecraft:magma_block'})


def loot_tables():
    loot('entities/scrap_colossus', 'minecraft:entity', [
        pool([item('servo_core')]),
        pool([item('minecraft:copper_ingot', 6, 12, looting=3)]),
        pool([item('minecraft:iron_ingot', 3, 7, looting=2)]),
        pool([item('minecraft:raw_copper', 4, 8, weight=3), item('minecraft:iron_nugget', 8, 16, weight=3),
              item('minecraft:redstone', 4, 10, weight=2), item('copper_gear', 2, 4, weight=2),
              item('electric_motor', weight=1)], rolls=2),
    ])
    loot('entities/forge_tyrant', 'minecraft:entity', [
        pool([item('magma_core')]),
        pool([item('minecraft:gold_ingot', 6, 12, looting=3)]),
        pool([item('minecraft:blaze_rod', 3, 6, looting=2)]),
        pool([item('minecraft:netherite_scrap')], conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.5}]),
        pool([item('minecraft:magma_cream', 4, 8, weight=3), item('minecraft:quartz', 8, 16, weight=3),
              item('pyrolite_shard', 2, 5, weight=2), item('minecraft:gilded_blackstone', 1, 3, weight=2),
              item('quantum_circuit', weight=1)], rolls=2),
    ])
    loot('entities/scrap_drone', 'minecraft:entity', [
        pool([item('minecraft:iron_nugget', 0, 2, looting=1)]),
        pool([item('minecraft:redstone', 0, 1)]),
    ])
    # the ruin's altar stays a ruin piece: mining it gives building blocks, not a free altar
    altar_loot('colossus_altar', 'minecraft:copper_block')
    altar_loot('forge_altar', 'minecraft:gilded_blackstone')
    loot('chests/rusted_foundry', 'minecraft:chest', [
        pool([item('signal_flare')]),
        pool([item('signal_flare')], conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.3}]),
        pool([item('minecraft:copper_ingot', 2, 6, weight=4), item('minecraft:iron_ingot', 1, 4, weight=3),
              item('minecraft:redstone', 2, 6, weight=3), item('minecraft:gunpowder', 1, 3, weight=2),
              item('copper_gear', 1, 3, weight=3), item('iron_plate', 1, 3, weight=2), item('copper_coil', 1, 2, weight=1),
              item('basic_circuit', weight=1), item('electric_motor', weight=1)], rolls=uniform(3, 6)),
    ])
    loot('chests/cinder_forge', 'minecraft:chest', [
        pool([item('ignition_charge')]),
        pool([item('ignition_charge')], conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.3}]),
        pool([item('minecraft:gold_ingot', 2, 6, weight=4), item('minecraft:blaze_rod', 1, 3, weight=3),
              item('minecraft:magma_cream', 2, 4, weight=3), item('minecraft:quartz', 4, 10, weight=3),
              item('pyrolite_shard', 1, 3, weight=2), item('advanced_circuit', weight=2), item('servo_actuator', weight=1),
              item('minecraft:netherite_scrap', weight=1)], rolls=uniform(3, 6)),
    ])


def altar_loot(name, ruin_drop):
    write(DATA / f'loot_table/blocks/{name}.json', {
        'type': 'minecraft:block', 'random_sequence': f'robotica:blocks/{name}',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                   'entries': [{'type': 'minecraft:alternatives', 'children': [
                       {'type': 'minecraft:item', 'name': f'robotica:{name}', 'conditions': [
                           {'condition': 'minecraft:block_state_property', 'block': f'robotica:{name}',
                            'properties': {'natural': 'false'}}]},
                       {'type': 'minecraft:item', 'name': ruin_drop, 'functions': [
                           {'function': 'minecraft:set_count', 'count': 2}]}]}]}]})


def worldgen():
    write(DATA / 'worldgen/structure/rusted_foundry.json', {
        'type': 'robotica:rusted_foundry', 'biomes': '#robotica:has_structure/rusted_foundry',
        'step': 'surface_structures', 'spawn_overrides': {}, 'terrain_adaptation': 'beard_thin'})
    write(DATA / 'worldgen/structure_set/rusted_foundry.json', {
        'structures': [{'structure': 'robotica:rusted_foundry', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': 48, 'separation': 16, 'salt': 19071905,
                      'exclusion_zone': {'other_set': 'minecraft:villages', 'chunk_count': 4}}})
    # Nether: a cave floor between the lava sea and the roof (see CinderForgeStructure), away from fortresses and bastions
    write(DATA / 'worldgen/structure/cinder_forge.json', {
        'type': 'robotica:cinder_forge', 'biomes': '#robotica:has_structure/cinder_forge',
        'step': 'surface_structures', 'spawn_overrides': {}, 'terrain_adaptation': 'beard_thin'})
    write(DATA / 'worldgen/structure_set/cinder_forge.json', {
        'structures': [{'structure': 'robotica:cinder_forge', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': 32, 'separation': 12, 'salt': 19071906,
                      'exclusion_zone': {'other_set': 'minecraft:nether_complexes', 'chunk_count': 3}}})


def altar_models(name):
    tex = {'side': f'robotica:block/{name}_side', 'top': f'robotica:block/{name}_top',
           'bottom': f'robotica:block/{name}_bottom', 'particle': f'robotica:block/{name}_side'}
    faces = {'down': '#bottom', 'up': '#top', 'north': '#side', 'south': '#side', 'west': '#side', 'east': '#side'}
    lit = dict(tex, side_glow=f'robotica:block/{name}_side_glow', top_glow=f'robotica:block/{name}_top_glow')
    glow = {'up': '#top_glow', 'north': '#side_glow', 'south': '#side_glow', 'west': '#side_glow', 'east': '#side_glow'}
    write(ASSETS / f'models/block/{name}.json', glow_cube(lit, faces, glow))
    write(ASSETS / f'models/block/{name}_cold.json', glow_cube(tex, faces, {}))
    write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{name}'})
    variants = {}
    for natural in ('false', 'true'):
        for ready in ('false', 'true'):
            model = f'robotica:block/{name}' if ready == 'true' else f'robotica:block/{name}_cold'
            variants[f'natural={natural},ready={ready}'] = {'model': model}
    write(ASSETS / f'blockstates/{name}.json', {'variants': variants})


def main():
    recipes()
    loot_tables()
    worldgen()
    altar_models('colossus_altar')
    altar_models('forge_altar')
    print('boss data written')


if __name__ == '__main__':
    main()
