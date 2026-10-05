"""Storage module textures: the Storage Terminal (a steel cabinet with copper fittings and a small glowing screen) and
the three Storage Expansion modules.
Run: python3 scripts/textures/storage.py   (writes textures/block/storage_terminal_*.png and textures/item/storage_expansion_mk*.png;
models, blockstates, loot and recipes are written by scripts/data/storage_data.py)

The front carries a dim screen in the base texture; the bright version of the screen text and the LEDs lives on a
separate storage_terminal_front_glow overlay that the lit model lays over the face at full brightness.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import Canvas, grain, material, write_block, write_item  # noqa: E402

ST, CU, DK = 'abcde', '01234', 'fghij'
P = {'k': '#16191B', 'K': '#0B0D0E'}
P.update(material(ST, 'steel'))
P.update(material(CU, 'copper'))
P.update(material(DK, 'dark'))
P.update(grain('sS', 'steel', 0.22))
P.update(grain('lL', 'copper', 0.22))
P.update({
    'V': '#0A1418', 'W': '#10242C',                                    # screen glass
    'Z': '#165E6A', 'Y': '#7FEFF8', 'y': '#2FB8CC', 'z': '#E8FFFF',   # screen text: dim, bright, mid, highlight
    'x': '#4A1A14', 'R': '#FF5A3A', 'q': '#1C4A22', 'G': '#5CFF6A',   # LEDs off / on
    'o': '#E8B530', 'O': '#FFE07A',                                    # gold contacts
})
GRAIN = {ST: 'sS', CU: 'lL'}


def plate(c, x, y, w, h, r, seed=0, **kw):
    return c.plate(x, y, w, h, r, seed, grain=GRAIN.get(r), **kw)


# ---------------------------------------------------------------- block

def steel_body(seed):
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, seed)
    return c


def front():
    c = steel_body(11)
    # copper corner rivets
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, CU[3])
    # screen: recessed dark glass with dim text lines
    c.screen(3, 2, 9, 6, DK, glass='V', glare='W')
    for x in range(4, 10):
        c.set(x, 3, 'Z')
    for x in range(4, 8):
        c.set(x, 5, 'Z')
    c.set(9, 5, 'Z')
    # LEDs (off)
    c.set(13, 3, 'q').set(13, 5, 'x')
    # two drawer fronts with copper handles
    for y in (9, 12):
        plate(c, 2, y, 12, 3, ST, 5 + y, density=0.1)
        c.rect(6, y + 1, 4, 1, CU[2])
        c.set(6, y + 1, CU[4]).set(9, y + 1, CU[1])
    # copper trim lines top and bottom
    c.rect(2, 8, 12, 1, CU[1])
    c.rect(2, 15, 12, 1, ST[0])
    return c


def front_glow():
    c = Canvas()
    for x in range(4, 10):
        c.set(x, 3, 'Y')
    for x in range(4, 8):
        c.set(x, 5, 'y')
    c.set(9, 5, 'y')
    c.set(4, 3, 'z').set(4, 5, 'z')
    c.set(13, 3, 'G').set(13, 5, 'R')
    return c


def side():
    c = steel_body(21)
    c.rect(1, 1, 14, 1, ST[1]).rect(1, 14, 14, 1, ST[1])
    c.vents(3, 4, 10, 6, ST, 2)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, CU[3])
    c.rect(7, 11, 2, 3, ST[1])                      # hinge-like seam
    c.rect(7, 11, 1, 3, ST[3])
    return c


def top():
    c = steel_body(31)
    plate(c, 2, 2, 12, 12, CU, 7, density=0.3)
    c.bevel(2, 2, 12, 12, CU[3], CU[1])
    plate(c, 4, 4, 8, 8, ST, 9, density=0.15)
    c.vents(5, 5, 6, 6, ST, 2)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, ST[4])
    return c


def bottom():
    c = Canvas()
    plate(c, 0, 0, 16, 16, DK, 41)
    c.rect(2, 2, 12, 12, DK[1])
    c.bevel(2, 2, 12, 12, DK[0], DK[3])
    return c


# ---------------------------------------------------------------- items

TIER = {1: 'copper', 2: 'cyan', 3: 'magma'}
T = 'ABCDE'


def expansion(mk):
    """A memory module card: slate board, tier coloured stripe, steel chip, gold contacts, one dot per Mk."""
    c = Canvas()
    c.rect(2, 1, 12, 14, 'k')
    c.rect(3, 2, 10, 12, DK[2])
    c.bevel(3, 2, 10, 12, DK[3], DK[1])
    c.rect(4, 3, 8, 2, T[2])
    c.rect(4, 3, 8, 1, T[3])
    c.rect(4, 4, 8, 1, T[1])
    c.rect(6, 6, 4, 4, ST[2])                        # chip
    c.bevel(6, 6, 4, 4, ST[4], ST[1])
    c.set(7, 7, ST[0]).set(8, 7, ST[0])
    for x in (5, 10):                                # chip pins
        for y in (6, 8):
            c.set(x, y, ST[3])
    for i in range(mk):                              # tier dots
        c.set(5 + i * 2, 11, T[4]).set(5 + i * 2, 12, T[2])
    for x in range(4, 12, 2):                        # gold contacts
        c.set(x, 13, 'o').set(x + 1, 13, 'O')
    return c


def main():
    write_block('storage_terminal_front', front().rows(), P)
    write_block('storage_terminal_front_glow', front_glow().rows(), P)
    write_block('storage_terminal_side', side().rows(), P)
    write_block('storage_terminal_top', top().rows(), P)
    write_block('storage_terminal_bottom', bottom().rows(), P)
    for mk, name in TIER.items():
        pal = dict(P)
        pal.update(material(T, name))
        write_item(f'storage_expansion_mk{mk}', expansion(mk).rows(), pal)
    print('storage textures written')


if __name__ == '__main__':
    main()
