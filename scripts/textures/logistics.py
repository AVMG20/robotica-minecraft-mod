"""Logistics module textures: the item pipes (copper Item Pipe, steel-and-brass Item Pipe Mk2) and the two connection
flanges (Insert blue, Extract orange).
Run: python3 scripts/textures/logistics.py   (writes textures/block/item_pipe*.png; models, blockstates, loot and
recipes are written by scripts/data/logistics_data.py)

Each pipe texture is a small atlas the models address by UV:
  (0,0)-(6,6)   core face: tube ring around a dark glass window
  (0,8)-(5,12)  arm side, length along x (5 x 4)
  (8,0)-(12,5)  arm side, length along y (4 x 5)
the rest is plain tube, so break particles look right. Flange textures:
  (0,0)-(8,8)   flange face, (0,8)-(8,10) edge along x, (8,0)-(10,8) edge along y.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import Canvas, material, write_block  # noqa: E402

CU, ST, BR, BL, OR = '01234', 'abcde', '56789', 'fghij', 'pqrst'
P = {'k': '#16191B', 'K': '#0B0D0E', 'V': '#0E1A1F', 'W': '#2A4650'}
P.update(material(CU, 'copper'))
P.update(material(ST, 'steel'))
P.update(material(BR, 'brass'))
P.update(material(BL, 'cyan'))
P.update(material(OR, 'amber'))


def tube_rows(r, n):
    """Cross-section shading of a round tube n px wide: highlight, mids, shadow."""
    if n == 4:
        return [r[3], r[2], r[2], r[1]]
    return [r[4]] + [r[3]] + [r[2]] * (n - 4) + [r[1], r[0]]


def pipe(tube, band):
    c = Canvas()
    # plain tube everywhere (particles), bands every 4 px
    for y in range(16):
        for x in range(16):
            c.set(x, y, tube_rows(tube, 4)[y % 4] if x % 4 else band[2])
    # core face: tube ring, dark glass window with a glint
    c.rect(0, 0, 6, 6, tube[2]).bevel(0, 0, 6, 6, tube[3], tube[1])
    c.set(0, 0, tube[4]).set(5, 5, tube[0])
    c.rect(1, 1, 4, 4, 'V').bevel(1, 1, 4, 4, 'K', tube[1])
    c.set(2, 2, 'W')
    # arm sides: tube shading across, a band ring near the core end
    across = tube_rows(tube, 4)
    for i in range(4):
        for j in range(5):
            c.set(j, 8 + i, across[i])
            c.set(8 + i, j, across[i])
    for i in range(4):
        c.set(3, 8 + i, band[3] if i == 0 else band[1] if i == 3 else band[2])
        c.set(8 + i, 3, band[3] if i == 0 else band[1] if i == 3 else band[2])
    return c


def flange(ring):
    c = Canvas()
    c.rect(0, 0, 16, 16, ST[2])
    # face: steel plate, coloured ring, dark bore
    c.rect(0, 0, 8, 8, ST[2]).bevel(0, 0, 8, 8, ST[3], ST[1])
    c.set(0, 0, ST[4]).set(7, 7, ST[0])
    c.rect(1, 1, 6, 6, ring[2]).bevel(1, 1, 6, 6, ring[3], ring[1])
    c.rect(2, 2, 4, 4, 'k').bevel(2, 2, 4, 4, 'K', ring[0])
    for x, y in ((0, 3), (7, 4), (3, 0), (4, 7)):
        c.set(x, y, ST[4])                                              # bolt heads
    # edges: steel with a coloured stripe
    c.rect(0, 8, 8, 2, ST[2]).rect(0, 8, 8, 1, ST[3]).rect(2, 8, 4, 2, ring[2])
    c.rect(8, 0, 2, 8, ST[2]).rect(8, 0, 1, 8, ST[3]).rect(8, 2, 2, 4, ring[2])
    return c


def main():
    write_block('item_pipe', pipe(CU, ST).rows(), P)
    write_block('item_pipe_mk2', pipe(ST, BR).rows(), P)
    write_block('item_pipe_insert', flange(BL).rows(), P)
    write_block('item_pipe_extract', flange(OR).rows(), P)


if __name__ == '__main__':
    main()
