"""Shared casing of the processing machines (Grinder, Electric Furnace, Metal Press, Alloy Smelter, Centrifuge,
Assembler): dark graphite panels, chamfered corners with the tier's metal on the corner brackets, and a cyan energy
channel that lights up while the machine works. Used by scripts/textures/processing.py, industry.py and power.py.

Reserved palette chars (machine textures add their own on top of casing_palette()):
  k K        outline, void            abcde  graphite body (deep -> highlight)
  fghij      tier metal ramp          u v w x  energy: unlit glass, cyan, bright cyan, white core
  l L        status lamp off / on
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, material, write_anim, write_block  # noqa: E402

BODY, TRIM = 'abcde', 'fghij'
GRAPHITE = ('#121519', '#1E2329', '#2E353D', '#465059', '#6C7883')
ENERGY = {'u': '#17343C', 'v': '#2FB8CC', 'w': '#8CF4FF', 'x': '#F0FFFF'}

# Tier metal of each machine family (Mk1 -> Mk4)
PROCESSING_TIERS = {1: MATERIALS['copper'], 2: MATERIALS['gold'], 3: MATERIALS['magma'], 4: MATERIALS['null']}
INDUSTRY_TIERS = {1: MATERIALS['copper'], 2: ('#1E2626', '#3A4746', '#62736E', '#95A8A0', '#D2E2DA'),
                  3: ('#3A0E08', '#6E2210', '#A8421C', '#E07A3A', '#FFC58A'),
                  4: ('#160A2E', '#36206A', '#5E46B0', '#8EA0F0', '#D8F4FF')}


def casing_palette(trim_ramp, **extra):
    """Palette with the graphite body, the given tier metal ramp and the energy colours."""
    p = {'k': '#0D0F12', 'K': '#06070A', 'l': '#1E3A2A', 'L': '#6CFFB0'}
    p.update(dict(zip(BODY, GRAPHITE)))
    p.update(dict(zip(TRIM, trim_ramp)))
    p.update(ENERGY)
    p.update(extra)
    return p


def shell(tier=1):
    """The face every machine side starts from: a bevelled graphite panel in a 2 px frame with chamfered corners and
    tier metal brackets (one notch per Mk on the lower right bracket). Leaves rows/cols 3..12 free for the face."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.rect(1, 1, 14, 14, 'b')
    c.bevel(0, 0, 16, 16, 'd', 'a')                                       # outer edge
    c.bevel(2, 2, 12, 12, 'a', 'd')                                       # inner panel sunk into the frame
    c.rect(3, 3, 10, 10, 'c')
    for x, y, sx, sy in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for i in range(5):                                                # 2 px L bracket in the tier metal
            for j in range(2):
                c.set(x + sx * i, y + sy * j, 'h').set(x + sx * j, y + sy * i, 'h')
        c.set(x + sx * 4, y, 'g').set(x, y + sy * 4, 'g')
        c.set(x + sx * 4, y + sy, 'f').set(x + sx, y + sy * 4, 'f')
        c.set(x + sx, y + sy, 'g')
        c.set(x, y, 'K')                                                  # chamfer
    for i in range(1, 4):                                                 # light from the top-left
        c.set(i, 0, 'j').set(0, i, 'j')
    return c


def tier_notches(c, tier, x=12, y=14):
    """Mk marks: one lit tier-metal pixel per Mk along the bottom frame, right to left."""
    for i in range(tier):
        c.set(x - i * 2, y, 'i')
    return c


def side(tier):
    """Side panel: an energy channel up the middle between two banks of cooling slats."""
    c = shell(tier)
    for y in range(4, 12, 2):                                             # cooling slats
        c.rect(3, y, 3, 1, 'a').rect(3, y + 1, 3, 1, 'd')
        c.rect(10, y, 3, 1, 'a').rect(10, y + 1, 3, 1, 'd')
    c.rect(6, 2, 4, 12, 'b')                                              # channel housing
    c.rect(6, 2, 1, 12, 'a').rect(9, 2, 1, 12, 'd')
    c.rect(7, 2, 2, 12, 'u')                                              # unlit glass
    c.rect(7, 2, 2, 1, 'k').rect(7, 13, 2, 1, 'k')
    tier_notches(c, tier)
    return c


def side_glow(frame, frames=16):
    """The channel lit cyan with a bright pulse travelling up it."""
    c = Canvas()
    head = 12 - (frame * 12 // frames)
    for y in range(3, 13):
        d = (y - head) % 12
        ch = 'x' if d == 0 else 'w' if d in (1, 11) else 'v'
        c.rect(7, y, 2, 1, ch)
    return c


def bottom():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    c.bevel(0, 0, 16, 16, 'c', 'a')
    c.recess(3, 3, 10, 10, 'abcde', fill='b')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rect(x, y, 2, 2, 'a').set(x, y, 'c')
    return c


SIDE_GLOW_FRAMES, SIDE_GLOW_FRAMETIME = 16, 2


def write_side_glow():
    """machine_side_glow: the lit energy channel every casing side shares while its machine works."""
    write_anim('block', 'machine_side_glow', [side_glow(i) for i in range(SIDE_GLOW_FRAMES)],
               casing_palette(MATERIALS['copper']), frametime=SIDE_GLOW_FRAMETIME)
