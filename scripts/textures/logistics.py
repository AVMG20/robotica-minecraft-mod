"""Logistics module textures: the item pipes (Mk1 to Mk4) and their two connectors (Insert cyan, Extract amber).
Run: python3 scripts/textures/logistics.py   (writes textures/block/item_pipe*.png; models, blockstates, loot and
recipes are written by scripts/data/logistics_data.py)

A pipe is a slim slate tube with a tinted channel down each side in its Mk colour and a clamp in the Mk trim where two
pipes meet (Mk1 copper, Mk2 ferrothorium, Mk3 pyrosteel, Mk4 resonant, as on the machines). The client lights the
channel when items pass (ItemPipeGlow), so the channel sits on rows/columns 1 and 2 of the arm's 4 px side.

Pipe atlas (models address it by UV):
  (0,0)-(8,4)    arm side, length along u, u 0 at the block face (clamp)
  (8,0)-(12,8)   arm side, length along v, v 0 at the block face
  (0,8)-(6,14)   junction node face
the rest is plain frame, so break particles look right.
Connector atlas:
  (0,0)-(10,10)  plate face, ring in the mode colour around the tube
  (0,15)-(10,16) plate edge along x, (10,0)-(11,10) plate edge along y
  (0,11)-(6,14)  collar side, length along v (v 0 at the plate): chevrons point into the block (Insert) or out (Extract)
  (12,0)-(15,6)  collar side, length along u (u 0 at the plate)
  (10,10)-(16,16) collar end
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, material, write_block  # noqa: E402
from industry import RAMPS  # noqa: E402

F, T, C, M = 'abcde', 'fghij', 'lmnop', 'qrstu'     # frame (slate), Mk trim (clamps), channel tint, mode colour
TRIM = {1: MATERIALS['copper'], 2: RAMPS['ferrothorium'], 3: RAMPS['pyrosteel'], 4: RAMPS['resonant']}
# channel: tinted glass with a lit streak, brighter than the trim
CHANNEL = {
    1: ('#3A1C0A', '#7A3E14', '#C26A2A', '#F2A060', '#FFE0B0'),
    2: ('#10301A', '#1E5A2E', '#3E9A4A', '#86DA6A', '#DCFFC0'),
    3: ('#3A0808', '#7A1410', '#C42A1E', '#FF6A48', '#FFD0B8'),
    4: ('#1A0E3E', '#36227A', '#6A4AD0', '#A890FF', '#ECE4FF'),
}
MODE = {'insert': MATERIALS['cyan'], 'extract': MATERIALS['amber']}


def pal(tier=None, mode=None):
    p = {'k': '#0E1014', 'K': '#07080A'}
    p.update(material(F, 'slate'))
    if tier:
        p.update(dict(zip(T, TRIM[tier])))
        p.update(dict(zip(C, CHANNEL[tier])))
    if mode:
        p.update(dict(zip(M, MODE[mode])))
    return p


# 4 px tube cross-section, top to bottom: lit frame edge, channel (lit), channel (deep), frame shadow
def across(i, f=F, c=C):
    return (f[3], c[2], c[1], f[1])[i]


def pipe(tier):
    c = Canvas()
    c.rect(0, 0, 16, 16, F[2])
    for y in range(16):
        for x in range(16):
            c.set(x, y, across(y % 4) if x % 8 else F[1])
    # arm sides: frame rails, the channel, a clamp ring at the block face and a pip every 4 px on the channel
    for i in range(4):
        for j in range(8):
            ch = across(i)
            if j == 0:
                ch = (T[3], T[2], T[2], T[1])[i]
            elif i == 1 and j in (4, 5):
                ch = C[3]
            c.set(j, i, ch)
            c.set(8 + i, j, ch)
    # junction node: bevelled slate, a dark recess with trim corners and the channel ending in a lit window
    c.rect(0, 8, 6, 6, F[2]).bevel(0, 8, 6, 6, F[3], F[1])
    c.set(0, 8, F[4]).set(5, 13, F[0])
    c.rect(1, 9, 4, 4, F[0])
    for x, y in ((1, 9), (4, 9), (1, 12), (4, 12)):
        c.set(x, y, T[2])
    c.set(1, 9, T[3]).set(4, 12, T[1])
    c.rect(2, 10, 2, 2, C[2]).set(2, 10, C[3]).set(3, 11, C[1])
    return c


def chevron(c, x, y, w, h, along_v, inward, mode):
    """Chevrons on a collar side: 3 px long, pointing toward the plate (Insert) or away from it (Extract)."""
    rows = ['..##..', '.#..#.', '#....#'] if inward else ['#....#', '.#..#.', '..##..']
    for a in range(3):
        for b in range(6):
            if rows[a][b] == '#':
                c.set(x + b, y + a, mode[3]) if along_v else c.set(x + a, y + b, mode[3])
            elif along_v:
                c.set(x + b, y + a, mode[1] if b in (0, 5) else 'k')
            else:
                c.set(x + a, y + b, mode[1] if b in (0, 5) else 'k')


def connector(kind):
    c = Canvas()
    c.rect(0, 0, 16, 16, F[2])
    # plate: bevelled slate with a ring in the mode colour just outside the collar (the middle is under the collar)
    c.rect(0, 0, 10, 10, F[2]).bevel(0, 0, 10, 10, F[3], F[1])
    c.set(0, 0, F[4]).set(9, 9, F[0])
    c.bevel(1, 1, 8, 8, M[3], M[1]).set(8, 1, M[2]).set(1, 8, M[2])
    c.rect(2, 2, 6, 6, F[1])
    # plate edges: slate with a mode stripe in the middle
    c.rect(0, 15, 10, 1, F[1]).rect(3, 15, 4, 1, M[2])
    c.rect(10, 0, 1, 10, F[1]).rect(10, 3, 1, 4, M[2])
    # collar sides: chevrons in the mode colour
    chevron(c, 0, 11, 6, 3, True, kind == 'insert', M)
    chevron(c, 12, 0, 3, 6, False, kind == 'insert', M)
    # collar end, toward the pipe: slate with the mode ring
    c.rect(10, 10, 6, 6, F[2]).bevel(10, 10, 6, 6, F[3], F[1])
    c.rect(11, 11, 4, 4, M[1]).rect(12, 12, 2, 2, 'K')
    return c


def main():
    for tier, name in ((1, 'item_pipe'), (2, 'item_pipe_mk2'), (3, 'item_pipe_mk3'), (4, 'item_pipe_mk4')):
        write_block(name, pipe(tier).rows(), pal(tier))
    for kind in ('insert', 'extract'):
        write_block(f'item_pipe_{kind}', connector(kind).rows(), pal(mode=kind))


if __name__ == '__main__':
    main()
