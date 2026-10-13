"""Chunk Anchor textures: a dark plinth with an emitter circle, steel pillars, and the ender core in three states
(off, a slow cyan swirl while it loads, a red pulse when it is dry or over the cap).
Run: python3 scripts/textures/chunk_anchor.py

The model (scripts/data/automation_chunk_anchor.py) marks the core and the emitter faces emissive; the spinning rings
around the core are drawn by ChunkAnchorRenderer.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import Canvas, grain, material, write_anim, write_block  # noqa: E402

ST, DK, CY, RD, VI = 'abcde', 'fghij', '01234', '56789', 'ABCDE'
P = {'k': '#0B0D0E'}
P.update(material(ST, 'steel'))
P.update(material(DK, 'dark'))
P.update(material(CY, 'cyan'))
P.update(material(RD, 'red'))
P.update(material(VI, 'purple'))
P.update(grain('sS', 'steel', 0.22))
P.update(grain('tT', 'dark', 0.25))
GRAIN = {ST: 'sS', DK: 'tT'}


def plate(c, x, y, w, h, r, seed=0, **kw):
    return c.plate(x, y, w, h, r, seed, grain=GRAIN.get(r), **kw)


def side():
    """Plinth side: dark plating, a steel band with rivets and a dim cyan slit in the middle."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, DK, 3)
    plate(c, 0, 12, 16, 4, ST, 5, density=0.15)     # the plinth (y 0-4) shows rows 12-15
    c.rivets(0, 12, 16, 4, ST, inset=0)
    c.recess(3, 13, 10, 2, DK)
    c.rect(4, 13, 8, 1, CY[0]).rect(5, 13, 6, 1, CY[1])
    return c


def top():
    """Plinth top seen through the cage: a dark deck with a ring of emitter studs around the core socket."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, DK, 7)
    c.ring(7.5, 7.5, 5.2, 6.2, DK[0])
    c.ring(7.5, 7.5, 3.0, 4.0, DK[1])
    c.disc(7.5, 7.5, 2.2, 'k')
    for i in range(8):
        a = i * math.pi / 4
        x, y = round(7.5 + 5.7 * math.cos(a)), round(7.5 + 5.7 * math.sin(a))
        c.set(x, y, CY[2])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, ST[3])
    return c


def emitter():
    """Emissive studs of the deck and cap: the same ring, bright, on a transparent layer."""
    c = Canvas()
    for i in range(8):
        a = i * math.pi / 4
        x, y = round(7.5 + 5.7 * math.cos(a)), round(7.5 + 5.7 * math.sin(a))
        c.set(x, y, CY[3])
    return c


def pillar():
    """Steel pillar: a lit left edge, a dark groove and bolt bands every 4 pixels (read through uv 6-8)."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, 11, vertical=True, bevel=False)
    c.rect(6, 0, 1, 16, ST[3]).rect(7, 0, 1, 16, ST[1])
    for y in range(1, 16, 4):
        c.rect(6, y, 2, 1, DK[2])
        c.set(6, y, ST[4])
    return c


def cap():
    """Cap top: a steel lid with a dark lens in the middle and four bolts."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, 13, density=0.15)
    plate(c, 3, 3, 10, 10, DK, 17, density=0.15)
    c.disc(7.5, 7.5, 2.6, 'k').disc(7.5, 7.5, 1.6, VI[1])
    c.set(7, 7, VI[3])
    c.rivets(3, 3, 10, 10, ST, inset=0)
    return c


def core_base(r):
    """A faceted crystal face: dark rim, mid body, a light top-left facet."""
    c = Canvas()
    c.rect(0, 0, 16, 16, r[1])
    c.shape(lambda x, y: abs(x - 7.5) + abs(y - 7.5) <= 6.5, lambda x, y: r[2])
    c.shape(lambda x, y: abs(x - 7.5) + abs(y - 7.5) <= 3.5, lambda x, y: r[3])
    c.shape(lambda x, y: x < 8 and y < 8 and abs(x - 7.5) + abs(y - 7.5) <= 6.5 and x + y > 9, lambda x, y: r[3])
    c.frame(0, 0, 16, 16, r[0])
    return c


def core_off():
    """Idle core: a dim violet crystal."""
    return core_base(VI[0] + VI[0] + VI[1] + VI[2])


def core_on(frame_no):
    """Loading core: cyan crystal with a bright swirl turning around its heart."""
    c = core_base(CY[0] + CY[1] + CY[2] + CY[3])
    for k in range(3):
        a = frame_no * math.pi / 8 + k * 2 * math.pi / 3
        for t in range(6):
            rr = 1.2 + t * 0.9
            x = round(7.5 + rr * math.cos(a + t * 0.45))
            y = round(7.5 + rr * math.sin(a + t * 0.45))
            c.set(x, y, CY[4] if t < 3 else CY[3])
    c.disc(7.5, 7.5, 1.3, CY[4])
    return c


def core_alert(frame_no):
    """Dry or over the cap: a red crystal that pulses."""
    level = (0, 1, 2, 3, 3, 2, 1, 0)[frame_no % 8]
    c = core_base(RD[0] + RD[1] + RD[1 + (level > 1)] + RD[2 + (level > 2)])
    c.disc(7.5, 7.5, 0.8 + level * 0.7, RD[3 + (level > 1)])
    return c


def main():
    write_block('chunk_anchor_side', side().rows(), P)
    write_block('chunk_anchor_top', top().rows(), P)
    write_block('chunk_anchor_emitter', emitter().rows(), P)
    write_block('chunk_anchor_pillar', pillar().rows(), P)
    write_block('chunk_anchor_cap', cap().rows(), P)
    write_block('chunk_anchor_core', core_off().rows(), P)
    write_anim('block', 'chunk_anchor_core_on', [core_on(i) for i in range(16)], P, frametime=2)
    write_anim('block', 'chunk_anchor_core_alert', [core_alert(i) for i in range(8)], P, frametime=3)
    print('chunk anchor textures written')


if __name__ == '__main__':
    main()
