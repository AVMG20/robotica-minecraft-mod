"""Writes the structure templates of the boss module.
- data/robotica/structure/rusted_foundry.nbt : the Rusted Foundry, a ruined 25x25 hall of stone bricks and rusted copper
  with broken walls, roof beams with hanging chains, a round arena floor and the Colossus Altar in the centre (12, 1, 12).
- data/robotica/structure/boss_arena.nbt     : an empty 9x8x9 game test area.
Run: python3 scripts/data/boss_structure.py   (deterministic: the same file every run)
"""
import gzip
import math
import pathlib
import random
import struct

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/structure'
DATA_VERSION = 3955  # 1.21.1

# ---------------------------------------------------------------- minimal NBT writer

TAG_BYTE, TAG_INT, TAG_LONG, TAG_STRING, TAG_LIST, TAG_COMPOUND = 1, 3, 4, 8, 9, 10


class Int(int):
    pass


def _str(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _tag_id(v):
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, int):
        return TAG_INT
    raise TypeError(v)


def _payload(v):
    t = _tag_id(v)
    if t == TAG_COMPOUND:
        out = b''
        for k, x in v.items():
            out += bytes([_tag_id(x)]) + _str(k) + _payload(x)
        return out + b'\x00'
    if t == TAG_LIST:
        inner = _tag_id(v[0]) if v else 0
        return bytes([inner]) + struct.pack('>i', len(v)) + b''.join(_payload(x) for x in v)
    if t == TAG_STRING:
        return _str(v)
    return struct.pack('>i', v)


def write_nbt(path, root):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(bytes([TAG_COMPOUND]) + _str('') + _payload(root), mtime=0))


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.nbt = {}

    def set(self, x, y, z, name, nbt=None, **props):
        sx, sy, sz = self.size
        if 0 <= x < sx and 0 <= y < sy and 0 <= z < sz:
            self.blocks[(x, y, z)] = (name if ':' in name else 'minecraft:' + name, tuple(sorted(props.items())))
            if nbt is not None:
                self.nbt[(x, y, z)] = nbt
            else:
                self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def write(self, path):
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            state = self.blocks[pos]
            if state not in index:
                index[state] = len(palette)
                entry = {'Name': state[0]}
                if state[1]:
                    entry['Properties'] = {k: str(v).lower() for k, v in state[1]}
                palette.append(entry)
            b = {'pos': [pos[0], pos[1], pos[2]], 'state': index[state]}
            if pos in self.nbt:
                b['nbt'] = self.nbt[pos]
            blocks.append(b)
        write_nbt(path, {'size': list(self.size), 'entities': [], 'blocks': blocks, 'palette': palette,
                         'DataVersion': DATA_VERSION})
        return len(blocks), len(palette)


# ---------------------------------------------------------------- Rusted Foundry

SIZE, HEIGHT, C = 25, 12, 12
rng = random.Random(1907)


def pick(*weighted):
    total = sum(w for _, w in weighted)
    r = rng.random() * total
    for name, w in weighted:
        r -= w
        if r <= 0:
            return name
    return weighted[-1][0]


def wall_stone():
    return pick(('stone_bricks', 5), ('cracked_stone_bricks', 3), ('andesite', 1), ('cobblestone', 1))


def wall_copper():
    return pick(('weathered_copper', 4), ('oxidized_copper', 3), ('exposed_copper', 2), ('weathered_cut_copper', 3),
                ('oxidized_cut_copper', 2))


def build_foundry():
    t = Template(SIZE, HEIGHT, SIZE)
    # everything inside starts as air, so the hall carves out the terrain it stands in
    for x in range(SIZE):
        for z in range(SIZE):
            for y in range(1, HEIGHT):
                t.set(x, y, z, 'air')

    # floor: stone bricks and rubble, a round arena of polished andesite with a copper ring and grate spokes
    for x in range(SIZE):
        for z in range(SIZE):
            d = math.hypot(x - C, z - C)
            if d <= 2.5:
                block = 'polished_andesite'
            elif abs(d - 9.0) < 0.55:
                block = 'weathered_cut_copper'
            elif d < 9.0 and (x == C or z == C):
                block = 'waxed_weathered_copper_grate'
            elif d < 9.0:
                block = pick(('polished_andesite', 6), ('andesite', 2), ('cracked_stone_bricks', 1))
            else:
                block = pick(('stone_bricks', 5), ('cracked_stone_bricks', 3), ('cobblestone', 2), ('gravel', 1),
                             ('mossy_cobblestone', 1))
            t.set(x, 0, z, block)

    # dais: a 3x3 copper plinth with the altar in the middle, bottom slab steps around it
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                t.set(C + dx, 1, C + dz, 'waxed_weathered_cut_copper_slab', type='bottom', waterlogged=False)
            else:
                t.set(C + dx, 1, C + dz, 'waxed_weathered_cut_copper')
    t.set(C, 1, C, 'robotica:colossus_altar', natural=True, ready=True)

    # outer walls: stone bricks below, rusted copper above, broken to uneven heights, doorways in the middle of each side
    def wall_height(i, side):
        n = math.sin(i * 0.9 + side * 2.1) + math.sin(i * 0.37 + side * 5.3)
        return max(1, min(8, int(round(6 + n * 2.0 - (1 if rng.random() < 0.15 else 0)))))

    perimeter = []
    for i in range(SIZE):
        perimeter += [(i, 0, 0), (i, SIZE - 1, 1), (0, i, 2), (SIZE - 1, i, 3)]
    for x, z, side in perimeter:
        i = x if side < 2 else z
        if C - 1 <= i <= C + 1:                                    # doorway
            h = 0
        else:
            h = wall_height(i, side)
        corner = i in (0, SIZE - 1)
        pillar = i in (0, 6, SIZE - 7, SIZE - 1)
        if pillar:
            h = 9 if corner or rng.random() < 0.6 else 6
        for y in range(1, h + 1):
            if pillar:
                block = 'polished_andesite' if y <= 2 else pick(('oxidized_cut_copper', 3), ('weathered_cut_copper', 2))
            elif y <= 2:
                block = wall_stone()
            elif y in (3, 4) and i % 4 == 2:
                block = 'iron_bars'
            else:
                block = wall_copper()
            t.set(x, y, z, block)
        if 0 < h < 8 and not pillar and rng.random() < 0.25:     # loose rubble on top of a broken wall
            t.set(x, h + 1, z, 'cobblestone_slab', type='bottom', waterlogged=False)
    # lintels over the doorways
    for side_xz in ((C, 0), (C, SIZE - 1), (0, C), (SIZE - 1, C)):
        for k in (-2, -1, 0, 1, 2):
            x, z = side_xz
            if x in (0, SIZE - 1):
                z = C + k
            else:
                x = C + k
            t.set(x, 5, z, 'chiseled_stone_bricks' if k == 0 else 'stone_bricks')

    # roof beams across the hall with gaps, chains hanging from them, some ending in a lantern
    for bz, broken_from in ((6, 15), (18, 99)):
        for x in range(1, SIZE - 1):
            if x >= broken_from or rng.random() < 0.15:
                if x >= broken_from and rng.random() < 0.2:      # fallen beam piece on the floor
                    t.set(x, 1, bz, 'oxidized_cut_copper_slab', type='bottom', waterlogged=False)
                continue
            t.set(x, 9, bz, 'oxidized_cut_copper')
        for x in range(3, SIZE - 3, 4):
            if t.get(x, 9, bz) != 'minecraft:oxidized_cut_copper':
                continue
            length = 2 + rng.randrange(3)
            for y in range(8, 8 - length, -1):
                t.set(x, y, bz, 'chain', axis='y', waterlogged=False)
            if rng.random() < 0.6:
                t.set(x, 8 - length, bz, 'lantern', hanging=True, waterlogged=False)

    # lights on the pillars (waxed bulbs keep their glow), lightning rods as snapped antennas
    for x, z in ((6, 1), (SIZE - 7, 1), (6, SIZE - 2), (SIZE - 7, SIZE - 2), (1, 6), (1, SIZE - 7), (SIZE - 2, 6), (SIZE - 2, SIZE - 7)):
        t.set(x, 4, z, 'waxed_exposed_copper_bulb', lit=True, powered=False)
    t.set(0, 10, 0, 'lightning_rod', facing='up', powered=False, waterlogged=False)
    t.set(SIZE - 1, 10, SIZE - 1, 'lightning_rod', facing='up', powered=False, waterlogged=False)

    # old machinery along the walls
    t.set(2, 1, 3, 'blast_furnace', facing='east', lit=False)
    t.set(2, 1, 4, 'blast_furnace', facing='east', lit=False)
    t.set(2, 2, 3, 'oxidized_cut_copper_slab', type='bottom', waterlogged=False)
    t.set(SIZE - 3, 1, 3, 'damaged_anvil', facing='north')
    t.set(SIZE - 3, 1, SIZE - 4, 'smithing_table')
    t.set(SIZE - 3, 1, SIZE - 5, 'oxidized_copper')
    t.set(SIZE - 3, 2, SIZE - 5, 'hopper', facing='down', enabled=True)
    for x, z in ((4, SIZE - 3), (5, SIZE - 3), (4, SIZE - 4)):
        t.set(x, 1, z, 'gravel')
    t.set(4, 2, SIZE - 3, 'gravel')
    t.set(SIZE - 4, 1, 9, 'iron_bars')
    t.set(SIZE - 4, 1, 10, 'iron_bars')
    # the loot chest in a corner: scrap, parts and a Signal Flare to start the fight
    t.set(2, 1, SIZE - 3, 'chest', {'id': 'minecraft:chest', 'LootTable': 'robotica:chests/rusted_foundry'},
          facing='east', type='single', waterlogged=False)
    # cobwebs high in the corners
    for x, z in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        for y in (6, 7):
            if rng.random() < 0.7:
                t.set(x, y, z, 'cobweb')
    return t


def build_arena():
    return Template(9, 8, 9)


def main():
    n, p = build_foundry().write(OUT / 'rusted_foundry.nbt')
    print(f'rusted_foundry.nbt: {n} blocks, {p} states')
    build_arena().write(OUT / 'boss_arena.nbt')
    print('boss_arena.nbt written')


if __name__ == '__main__':
    main()
