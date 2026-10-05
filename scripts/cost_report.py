#!/usr/bin/env python3
"""Raw-material cost report for every Robotica item.

Parses all recipes in data/robotica/recipe (crafting_shaped, crafting_shapeless, smithing_transform, stonecutting,
robotica:pressing), resolves every ingredient recursively (cheapest recipe wins) down to raw materials and writes
docs/COSTS.md sorted by age.

Run: python3 scripts/cost_report.py            (writes docs/COSTS.md)
     python3 scripts/cost_report.py --stdout   (print instead)

Conventions
- Tags map to one representative vanilla item (TAGS below). #c:plates/<metal> resolves to the Metal Press recipe (1 ingot).
- Boss cores (servo/magma/antigrav core) use their temp_ recipes and are marked [temp].
- Tools that are only consumed as a catalyst (Tinker's Hammer in hand-plate recipes) are never the cheapest route.
- Iron-equivalent score = sum(raw * WEIGHT). Weights are a rough rarity scale, not a market price.
"""
import json
import pathlib
import sys
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[1]
RECIPES = ROOT / 'src/main/resources/data/robotica/recipe'
OUT = ROOT / 'docs/COSTS.md'

# --- raw material model -------------------------------------------------------------------------------------------
COLUMNS = ['iron', 'copper', 'gold', 'redstone', 'diamond', 'netherite', 'ender_pearl', 'nether_star']
WEIGHT = {
    'iron': 1.0, 'copper': 0.4, 'gold': 3.0, 'redstone': 0.4, 'diamond': 10.0, 'netherite': 60.0,
    'ender_pearl': 8.0, 'nether_star': 80.0,
    # "other" raw items
    'lapis': 0.5, 'quartz': 1.0, 'obsidian': 1.5, 'blaze_rod': 5.0, 'blaze_powder': 2.5, 'shulker_shell': 20.0,
    'prismarine_crystals': 3.0, 'magma_block': 2.0, 'end_crystal': 25.0, 'emerald': 6.0, 'slime_ball': 2.0,
    'glowstone': 2.0, 'sea_lantern': 4.0, 'amethyst': 1.0, 'wool': 0.2, 'glass': 0.1, 'dye': 0.1, 'bone_block': 0.5,
    'cactus': 0.1, 'sugar': 0.1, 'paper': 0.1, 'stone_axe': 0.0,
    'cobblestone': 0.02, 'planks': 0.05, 'logs': 0.1, 'stone': 0.02, 'stick': 0.02,
}
DEFAULT_OTHER_WEIGHT = 0.5

# vanilla items that expand to raw materials (item -> {raw: amount}); everything else counts as one "other" item
VANILLA = {
    'minecraft:iron_ingot': {'iron': 1}, 'minecraft:copper_ingot': {'copper': 1}, 'minecraft:gold_ingot': {'gold': 1},
    'minecraft:redstone': {'redstone': 1}, 'minecraft:diamond': {'diamond': 1},
    'minecraft:netherite_ingot': {'netherite': 1}, 'minecraft:netherite_scrap': {'netherite': 0.25},
    'minecraft:ender_pearl': {'ender_pearl': 1}, 'minecraft:nether_star': {'nether_star': 1},
    'minecraft:iron_block': {'iron': 9}, 'minecraft:copper_block': {'copper': 9}, 'minecraft:gold_block': {'gold': 9},
    'minecraft:redstone_block': {'redstone': 9}, 'minecraft:diamond_block': {'diamond': 9},
    'minecraft:lapis_lazuli': {'lapis': 1}, 'minecraft:lapis_block': {'lapis': 9},
    'minecraft:quartz': {'quartz': 1}, 'minecraft:quartz_block': {'quartz': 4},
    'minecraft:obsidian': {'obsidian': 1},
    'minecraft:blaze_rod': {'blaze_rod': 1}, 'minecraft:blaze_powder': {'blaze_powder': 1},
    'minecraft:shulker_shell': {'shulker_shell': 1}, 'minecraft:prismarine_crystals': {'prismarine_crystals': 1},
    'minecraft:magma_block': {'magma_block': 1}, 'minecraft:end_crystal': {'end_crystal': 1},
    'minecraft:emerald': {'emerald': 1}, 'minecraft:slime_ball': {'slime_ball': 1},
    'minecraft:glowstone': {'glowstone': 1}, 'minecraft:sea_lantern': {'sea_lantern': 1},
    'minecraft:bone_block': {'bone_block': 1}, 'minecraft:cactus': {'cactus': 1}, 'minecraft:sugar': {'sugar': 1},
    'minecraft:paper': {'paper': 1}, 'minecraft:cobblestone': {'cobblestone': 1}, 'minecraft:oak_planks': {'planks': 1},
    'minecraft:oak_log': {'logs': 1}, 'minecraft:stick': {'stick': 1},
    'minecraft:white_wool': {'wool': 1}, 'minecraft:glass': {'glass': 1}, 'minecraft:white_dye': {'dye': 1},
    # a few crafted vanilla items worth expanding
    'minecraft:furnace': {'cobblestone': 8}, 'minecraft:crafting_table': {'planks': 4},
    'minecraft:piston': {'iron': 1, 'redstone': 1, 'cobblestone': 4, 'planks': 3},
    'minecraft:iron_pickaxe': {'iron': 3, 'stick': 2}, 'minecraft:diamond_pickaxe': {'diamond': 3, 'stick': 2}, 'minecraft:stone_axe': {'cobblestone': 3, 'stick': 2},
    'minecraft:stone_hoe': {'cobblestone': 2, 'stick': 2},
    'minecraft:redstone_lamp': {'redstone': 4, 'glowstone': 4}, 'minecraft:torch': {'stick': 1},
    'minecraft:glass_bottle': {'glass': 3},
    'minecraft:stone': {'stone': 1}, 'minecraft:stone_bricks': {'stone': 1},
    'minecraft:white_concrete': {'stone': 1}, 'minecraft:light_gray_concrete': {'stone': 1},
    'minecraft:polished_andesite': {'stone': 1}, 'minecraft:blackstone': {'stone': 1},
    'minecraft:polished_blackstone': {'stone': 1}, 'minecraft:cobbled_deepslate': {'cobblestone': 1},
    'minecraft:iron_bars': {'iron': 0.5},
}

# tag -> representative item
TAGS = {
    'c:ingots/iron': 'minecraft:iron_ingot', 'c:ingots/copper': 'minecraft:copper_ingot',
    'c:ingots/gold': 'minecraft:gold_ingot', 'c:ingots/netherite': 'minecraft:netherite_ingot',
    'c:plates/iron': 'robotica:iron_plate', 'c:plates/copper': 'robotica:copper_plate',
    'c:plates/gold': 'robotica:gold_plate',
    'c:dusts/redstone': 'minecraft:redstone', 'c:storage_blocks/redstone': 'minecraft:redstone_block',
    'c:storage_blocks/copper': 'minecraft:copper_block', 'c:storage_blocks/lapis': 'minecraft:lapis_block',
    'c:gems/diamond': 'minecraft:diamond', 'c:gems/quartz': 'minecraft:quartz', 'c:gems/lapis': 'minecraft:lapis_lazuli',
    'c:gems/emerald': 'minecraft:emerald', 'c:obsidians': 'minecraft:obsidian',
    'c:chests/wooden': 'minecraft:chest', 'c:chests': 'minecraft:chest',
    'c:cobblestones': 'minecraft:cobblestone', 'c:cobblestones/normal': 'minecraft:cobblestone',
    'c:stones': 'minecraft:stone', 'c:ender_pearls': 'minecraft:ender_pearl', 'c:nether_stars': 'minecraft:nether_star',
    'c:rods/blaze': 'minecraft:blaze_rod', 'c:rods/wooden': 'minecraft:stick', 'c:slime_balls': 'minecraft:slime_ball',
    'c:glass_blocks': 'minecraft:glass', 'c:dyes/white': 'minecraft:white_dye',
    'minecraft:planks': 'minecraft:oak_planks', 'minecraft:logs': 'minecraft:oak_log',
    'minecraft:wool': 'minecraft:white_wool', 'minecraft:wooden_slabs': 'minecraft:oak_planks',
    'minecraft:wooden_stairs': 'minecraft:oak_planks',
}

# --- ages ---------------------------------------------------------------------------------------------------------
AGE = {
    0: ['copper_gear', 'mainspring', 'clockwork_mechanism', 'wooden_chassis', 'stumpy', 'sprout', 'supply_crate',
        'winding_crank', 'tinkers_hammer', 'felling_axe', 'gearblade', 'codex', 'timberframe_*'],
    1: ['iron_plate', 'copper_plate', 'gold_plate', 'copper_coil', 'iron_casing', 'basic_circuit', 'electric_motor',
        'copper_cell', 'combustion_generator', 'solar_panel_mk1', 'accumulator_1', 'tesla_linker', 'tesla_coil_1', 'tesla_coil_2',
        'charger', 'metal_press', 'bore_drill', 'chainsaw', 'tool_upgrade_kit_1', 'shock_baton',
        'farm_kit_mk2', 'architect_table', 'copper_works_*', 'recall_remote', 'warp_pad', 'upgrade_speed',
        'upgrade_efficiency', 'upgrade_growth', 'upgrade_void'],
    2: ['reinforced_casing', 'advanced_circuit', 'servo_actuator', 'redstone_cell', 'servo_core', 'solar_panel_mk2',
        'accumulator_2', 'tesla_coil_3', 'rivet_gun', 'tool_upgrade_kit_2', 'servo_drill', 'farm_kit_mk3', 'essence_vial', 'excavator', 'survey_rig',
        'replicator_*', 'steel_lab_*', 'upgrade_range', 'upgrade_fortune', 'upgrade_silk'],
    3: ['blazing_casing', 'quantum_circuit', 'plasma_actuator', 'magma_core', 'accumulator_3', 'tesla_coil_4', 'tool_upgrade_kit_3',
        'magma_drill', 'arc_blade', 'rift_upgrade', 'rift_remote'],
    4: ['null_casing', 'null_circuit', 'ender_cell', 'antigrav_core', 'tesla_coil_5', 'tool_upgrade_kit_4', 'null_drill',
        'null_lance', 'farm_kit_mk4', 'gate_controller', 'linking_card', 'null_spire_*'],
}


def match(pattern, name):
    if pattern.endswith('*'):
        return name.startswith(pattern[:-1])
    if '*' in pattern:
        pre, post = pattern.split('*', 1)
        return name.startswith(pre) and name.endswith(post)
    return name == pattern


def age_of(name):
    for age, pats in AGE.items():
        if name in pats:
            return age
    for age, pats in AGE.items():
        if any(match(p, name) for p in pats if '*' in p):
            return age
    return 9


# --- recipe parsing -----------------------------------------------------------------------------------------------
def ing_key(i):
    if isinstance(i, list):
        i = i[0]
    return ('#' + i['tag']) if 'tag' in i else i['item']


def load_recipes():
    """result item id -> list of (kind, {ingredient key: count}, result count, recipe name)"""
    out = defaultdict(list)
    for f in sorted(RECIPES.glob('*.json')):
        r = json.loads(f.read_text())
        t = r['type'].split(':')[1]
        counts = defaultdict(int)
        if t == 'crafting_shaped':
            for row in r['pattern']:
                for ch in row:
                    if ch != ' ':
                        counts[ing_key(r['key'][ch])] += 1
        elif t == 'crafting_shapeless':
            for i in r['ingredients']:
                counts[ing_key(i)] += 1
        elif t == 'smithing_transform':
            for k in ('template', 'base', 'addition'):
                counts[ing_key(r[k])] += 1
        elif t == 'stonecutting':
            counts[ing_key(r['ingredient'])] += 1
        elif t == 'pressing':
            counts[ing_key(r['ingredient'])] += 1
        else:
            print('unknown recipe type', t, f.name, file=sys.stderr)
            continue
        res = r['result']
        out[res['id']].append((t, dict(counts), res.get('count', 1), f.stem))
    return out


RECIPE_MAP = load_recipes()
NAMES = sorted(k.split(':')[1] for k in RECIPE_MAP if k.startswith('robotica:'))
MEMO = {}


def score(cost):
    s = 0.0
    for k, v in cost.items():
        s += v * WEIGHT.get(k, DEFAULT_OTHER_WEIGHT)
    return s


def add(into, cost, mult=1.0):
    for k, v in cost.items():
        into[k] = into.get(k, 0.0) + v * mult


def cost_of(item, stack=()):
    """Raw cost dict of one `item`, or None if unresolvable (cycle)."""
    if item.startswith('#'):
        rep = TAGS.get(item[1:])
        if rep is None:
            print('unmapped tag', item, file=sys.stderr)
            return {item[1:]: 1.0}
        return cost_of(rep, stack)
    if item in MEMO:
        return MEMO[item]
    if item in stack:
        return None
    if item in VANILLA:
        return {k: float(v) for k, v in VANILLA[item].items()}
    if not item.startswith('robotica:'):
        return {item.split(':')[1]: 1.0}
    best = None
    for kind, ingredients, count, name in RECIPE_MAP.get(item, []):
        total = {}
        ok = True
        for ingk, n in ingredients.items():
            c = cost_of(ingk, stack + (item,))
            if c is None:
                ok = False
                break
            add(total, c, n)
        if not ok:
            continue
        total = {k: v / count for k, v in total.items()}
        if best is None or score(total) < score(best):
            best = total
    if best is None and item in RECIPE_MAP:
        return None
    if best is None:
        print('no recipe for', item, file=sys.stderr)
        best = {item: 1.0}
    MEMO[item] = best
    return best


def fmt(v):
    if abs(v - round(v)) < 1e-6:
        return str(int(round(v)))
    return f'{v:.2f}'.rstrip('0').rstrip('.')


def main():
    rows = []
    for n in NAMES:
        c = cost_of('robotica:' + n)
        if c is None:
            continue
        other = {k: v for k, v in c.items() if k not in COLUMNS}
        kinds = sorted({k for k, _, _, _ in RECIPE_MAP['robotica:' + n]})
        note = ''
        if n in ('servo_core', 'magma_core', 'antigrav_core'):
            note = '[temp] boss core'
        elif 'smithing_transform' in kinds:
            note = 'smithing'
        rows.append((age_of(n), score(c), n, c, other, note))
    rows.sort(key=lambda r: (r[0], r[1], r[2]))

    lines = ['# Robotica raw-material costs', '',
             'Generated by `python3 scripts/cost_report.py`. Do not edit by hand.', '',
             'Each item is costed by its cheapest recipe, recursively down to raw materials. Tags map to a representative',
             'vanilla item; plates cost the Metal Press price (1 ingot); boss cores use their `temp_` recipes. Stack recipes',
             'are divided by their output count. Iron-equivalent (IE) weights: iron 1, copper 0.4, redstone 0.4, gold 3,',
             'diamond 10, ender pearl 8, netherite ingot 60, nether star 80, other items from 0.02 (cobblestone) to 25.',
             '"other" lists non-column raw items (quartz, obsidian, blaze rods, shulker shells, ...).', '']
    names = {0: 'Age 0 - Clockwork', 1: 'Age 1 - Wired', 2: 'Age 2 - Servo', 3: 'Age 3 - Deep', 4: 'Age 4 - Antigrav',
             9: 'Unsorted'}
    head = '| item | iron | copper | gold | redstone | diamond | netherite | ender pearl | nether star | other | IE |'
    sep = '|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|'
    cur = None
    for age, sc, n, c, other, note in rows:
        if age != cur:
            cur = age
            lines += ['', f'## {names[age]}', '', head, sep]
        cols = [fmt(c.get(k, 0)) if c.get(k, 0) else '' for k in COLUMNS]
        oth = ', '.join(f'{k} {fmt(v)}' for k, v in sorted(other.items(), key=lambda kv: -kv[1] * WEIGHT.get(kv[0], DEFAULT_OTHER_WEIGHT)))
        label = f'`{n}`' + (f' {note}' if note else '')
        lines.append(f'| {label} | ' + ' | '.join(cols) + f' | {oth} | {fmt(round(sc, 1))} |')
    text = '\n'.join(lines) + '\n'
    if '--stdout' in sys.argv:
        print(text)
    else:
        OUT.write_text(text)
        print('wrote', OUT, f'({len(rows)} items)')


if __name__ == '__main__':
    main()
