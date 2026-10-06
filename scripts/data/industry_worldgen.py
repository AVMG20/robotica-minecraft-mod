"""Writes the ore generation of the industry module: configured features, placed features and NeoForge biome modifiers.
Run: python3 scripts/data/industry_worldgen.py

- Thorium: Overworld, stone and deepslate variants, trapezoid Y -48..32 (most around Y -8), 9 veins of up to 8 per chunk.
- Pyrolite: Nether, in netherrack, Y 10..117, 10 veins of up to 9; basalt deltas get 8 more veins that also replace
  basalt and blackstone.
- Resonite: End, outer islands only (highlands, midlands, barrens), in end stone, Y 16..80, 5 veins of up to 5.
Packs can switch a deposit off by overriding its biome modifier with {"type": "neoforge:none"}.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = ROOT / 'src/main/resources/data/robotica'


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tag_target(tag, block):
    return {'target': {'predicate_type': 'minecraft:tag_match', 'tag': tag}, 'state': {'Name': f'robotica:{block}'}}


def block_target(match, block):
    return {'target': {'predicate_type': 'minecraft:block_match', 'block': match}, 'state': {'Name': f'robotica:{block}'}}


def ore(name, size, targets, count, height, biomes, air_discard=0.0):
    write(DATA / 'worldgen/configured_feature' / f'{name}.json', {
        'type': 'minecraft:ore',
        'config': {'size': size, 'discard_chance_on_air_exposure': air_discard, 'targets': targets}})
    write(DATA / 'worldgen/placed_feature' / f'{name}.json', {
        'feature': f'robotica:{name}',
        'placement': [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:height_range', 'height': height}, {'type': 'minecraft:biome'}]})
    write(DATA / 'neoforge/biome_modifier' / f'{name}.json', {
        'type': 'neoforge:add_features', 'biomes': biomes, 'features': f'robotica:{name}', 'step': 'underground_ores'})


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}


def trapezoid(lo, hi):
    return {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}


def main():
    ore('ore_thorium', 8, [tag_target('minecraft:stone_ore_replaceables', 'thorium_ore'),
                           tag_target('minecraft:deepslate_ore_replaceables', 'deepslate_thorium_ore')],
        9, trapezoid(-48, 32), '#minecraft:is_overworld')
    ore('ore_pyrolite', 9, [block_target('minecraft:netherrack', 'pyrolite_ore')],
        10, uniform(10, 117), '#minecraft:is_nether')
    ore('ore_pyrolite_deltas', 9, [tag_target('minecraft:base_stone_nether', 'pyrolite_ore')],
        8, uniform(10, 117), 'minecraft:basalt_deltas')
    ore('ore_resonite', 5, [block_target('minecraft:end_stone', 'resonite_ore')],
        5, uniform(16, 80), ['minecraft:end_highlands', 'minecraft:end_midlands', 'minecraft:end_barrens'], 0.3)
    print('industry worldgen written')


if __name__ == '__main__':
    main()
