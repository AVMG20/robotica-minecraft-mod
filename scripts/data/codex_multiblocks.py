"""Writes the example builds the Codex draws on its layout pages, from the wiki's checked multiblocks.

Run: python3 scripts/data/codex_multiblocks.py
Reads  scripts/wiki/multiblocks.json (checked against the real structure rules by scripts/wiki/multiblock_check.py)
Writes assets/robotica/codex/multiblocks.json: per structure its layers (bottom first, rows north to south, columns west
to east) and a legend from each letter to the item the Codex shows (empty for air).
A Codex page shows one with "layout": "<id>" in chapters.json.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'scripts/wiki/multiblocks.json'
OUT = ROOT / 'src/main/resources/assets/robotica/codex/multiblocks.json'

sys.path.insert(0, str(ROOT / 'scripts/wiki'))
import multiblock_check  # noqa: E402

# Blocks without an item of their own: what the Codex shows instead.
ITEM_FOR_BLOCK = {'minecraft:water': 'minecraft:water_bucket'}
AIR = {'air', 'minecraft:air'}


def item_of(block):
    if block in AIR:
        return ''
    block = block if ':' in block else f'robotica:{block}'
    return ITEM_FOR_BLOCK.get(block, block)


def main():
    source = json.loads(SOURCE.read_text())
    out = {}
    for mb_id, mb in source.items():
        problems = multiblock_check.check(mb)
        if problems:
            sys.exit(f'{mb_id}: ' + '; '.join(problems))
        out[mb_id] = {'layers': mb['layers'], 'legend': {k: item_of(v) for k, v in mb['legend'].items()}}
    OUT.write_text(json.dumps({'multiblocks': out}, indent=2) + '\n')
    print(f'{len(out)} multiblock layouts')


if __name__ == '__main__':
    main()
