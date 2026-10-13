"""Chunk Anchor data: block models, blockstates, item models, loot tables and recipes of the three Mks.
Run: python3 scripts/data/automation_chunk_anchor.py   (overwrites only Chunk Anchor files)

An open cage: a plinth with a Mk coloured trim, four steel pillars, an emissive ender core floating in the middle and
a cap with a small glowing emitter. ChunkAnchorRenderer spins two rings around the core (its centre is at y 8.5 px),
so the cage stays open there. MODE picks the core texture: idle (dim violet), active (cyan swirl), alert (red pulse).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from automation_data import ASSETS, GLOW, box, loot, shaped, write  # noqa: E402

SIDES = ('north', 'south', 'east', 'west')
FULL = [0, 0, 16, 16]


def glowing(el):
    for face in el['faces'].values():
        face['neoforge_data'] = dict(GLOW)
    el['shade'] = False
    return el


def full_uv(el):
    for face in el['faces'].values():
        face['uv'] = list(FULL)
    return el


def pillar(x, z):
    el = box((x, 5, z), (x + 2, 13, z + 2), 'pillar', faces=SIDES)
    for face in el['faces'].values():
        face['uv'] = [6, 3, 8, 11]
    return el


def elements():
    return [
        # plinth, Mk trim and the deck with its emitter studs
        box((0, 0, 0), (16, 4, 16), 'side', faces=SIDES + ('down',)),
        box((0.5, 4, 0.5), (15.5, 5, 15.5), 'accent', faces=SIDES),
        full_uv(box((0.5, 4, 0.5), (15.5, 5, 15.5), 'top', faces=('up',))),
        full_uv(glowing(box((0.5, 4, 0.5), (15.5, 5, 15.5), 'emitter', faces=('up',)))),
        # core socket, pillars, the floating core
        box((6, 5, 6), (10, 6, 10), 'dark'),
        pillar(1.5, 1.5), pillar(12.5, 1.5), pillar(1.5, 12.5), pillar(12.5, 12.5),
        full_uv(glowing(box((6, 6.5, 6), (10, 10.5, 10), 'core'))),
        # cap with its hanging emitter
        box((1.5, 13, 1.5), (14.5, 14, 14.5), 'dark'),
        box((3, 14, 3), (13, 16, 13), 'steel', faces=SIDES),
        full_uv(box((3, 14, 3), (13, 16, 13), 'cap', faces=('up',))),
        full_uv(glowing(box((7, 12, 7), (9, 13, 9), 'core', faces=SIDES + ('down',)))),
    ]


def main():
    textures = {'side': 'robotica:block/chunk_anchor_side', 'top': 'robotica:block/chunk_anchor_top',
                'emitter': 'robotica:block/chunk_anchor_emitter', 'pillar': 'robotica:block/chunk_anchor_pillar',
                'cap': 'robotica:block/chunk_anchor_cap', 'core': 'robotica:block/chunk_anchor_core',
                'dark': 'robotica:block/automation_dark', 'steel': 'robotica:block/automation_steel',
                'accent': 'robotica:block/automation_brass', 'particle': 'robotica:block/chunk_anchor_side'}
    write(ASSETS / 'models/block/chunk_anchor.json', {
        'render_type': 'minecraft:cutout',
        'parent': 'minecraft:block/block',
        'textures': textures,
        'elements': elements(),
    })
    cores = {'idle': None, 'active': 'chunk_anchor_core_on', 'alert': 'chunk_anchor_core_alert'}
    suffix = {'idle': '', 'active': '_on', 'alert': '_alert'}
    for tier in range(1, 4):
        name = 'chunk_anchor' if tier == 1 else f'chunk_anchor_mk{tier}'
        accent = {} if tier == 1 else {'accent': f'robotica:block/automation_mk{tier}'}
        variants = {}
        for mode, core in cores.items():
            model = name + suffix[mode]
            tex = dict(accent)
            if core:
                tex['core'] = f'robotica:block/{core}'
            if model != 'chunk_anchor':
                write(ASSETS / f'models/block/{model}.json', {'parent': 'robotica:block/chunk_anchor', 'textures': tex})
            variants[f'mode={mode}'] = {'model': f'robotica:block/{model}'}
        write(ASSETS / f'blockstates/{name}.json', {'variants': variants})
        write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{name}'})
        loot(name)
    # Mk1 at Age 1: an iron casing, a Basic Circuit and ender pearls to hold the chunk. Each Mk eats the one before.
    shaped('chunk_anchor', ['PEP', 'BIB', 'PCP'], {'P': '#c:plates/iron', 'E': '#c:ender_pearls', 'B': 'basic_circuit',
                                                'I': 'iron_casing', 'C': 'copper_coil'})
    shaped('chunk_anchor_mk2', [' A ', 'EXE', ' R '], {'A': 'advanced_circuit', 'E': '#c:ender_pearls',
                                                     'X': 'chunk_anchor', 'R': 'reinforced_casing'})
    shaped('chunk_anchor_mk3', [' Q ', 'EXE', ' B '], {'Q': 'quantum_circuit', 'E': '#c:ender_pearls',
                                                     'X': 'chunk_anchor_mk2', 'B': 'blazing_casing'})
    print('chunk anchor data written')


if __name__ == '__main__':
    main()
