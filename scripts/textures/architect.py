"""Architect module textures: 24 building blocks (4 styles x 6 roles), the Architect Table and the builder drone.
Run: python3 scripts/textures/architect.py   (writes textures/block/*.png and textures/entity/builder_drone.png;
models, blockstates, recipes and lang are written by scripts/data/architect_*.py)

Style identities
  Timberframe  oak beams, white plaster, clay roof, lantern light
  Copper Works riveted copper plates, bolted tread floor, scale roof, amber lamp
  Steel Lab    white steel panels with seams, grated floor, framed glass, cyan light strip
  Null Spire   dark glossy panels, glowing teal seams, energy glass
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, Canvas, write_block, write_png  # noqa: E402

# Palette conventions per style: 1-5 main ramp (light to dark), 6-8 accent ramp, f/g/h glow (dim, bright, white),
# k/K near black, v/V glass, others per style.
TIMBER = {
    '1': '#F2EEDF', '2': '#E6DFCB', '3': '#D3CBB0', '4': '#B5AC90',  # plaster
    '5': '#C9985C', '6': '#A97A44', '7': '#7C5430', '8': '#4E3219',  # oak
    'r': '#A5543A', 'R': '#84402C', 's': '#5E2B1E', 't': '#BC6A4B',  # clay roof tiles
    'k': '#2A2623', 'f': '#FFB21E', 'g': '#FFE08A', 'h': '#FFF6D0', 'G': '#F4C95A',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
COPPER = {
    '1': '#F4B77A', '2': '#E8A060', '3': '#C87533', '4': '#8A4A22', '5': '#5A2F14',
    '6': '#F2D27A', '7': '#B8902A', '8': '#7A5E18',  # brass
    'Z': '#4FA88A', 'z': '#2F7A62',  # patina
    'f': '#FF9A1E', 'g': '#FFC93C', 'h': '#FFEBA8', 'k': '#2A1A10',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
STEEL = {
    '1': '#F7FAFB', '2': '#E2E9EC', '3': '#C2CBCF', '4': '#8D969B', '5': '#5A6367',
    'k': '#2A3033', 'K': '#171B1D',
    'f': '#2FB8CC', 'g': '#5FE3F0', 'h': '#D8FBFF',
    'v': '#E4F6FB', 'V': '#9FDCEA',
}
NULL = {
    '1': '#4A4270', '2': '#2F2850', '3': '#1D182F', '4': '#130F22', '5': '#0A0812',
    'f': '#1E8F7F', 'g': '#2FD0B4', 'h': '#B8FFF0', 'k': '#0A0812',
    'v': '#2FD0B455', 'V': '#7FF0DAA0', 'w': '#2FD0B433',
}


def n(x, y, s=1):
    """Deterministic hash noise 0..65535."""
    return (x * 73856093 ^ y * 19349663 ^ s * 83492791) & 0xFFFF


def speckle(c, x0, y0, w, h, chars, every, seed=1):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = n(x, y, seed)
            if v % every == 0:
                c.set(x, y, chars[(v >> 4) % len(chars)])


def glass_frame(c, light, mid, dark, rivet=None, muntin=True):
    """Window frame: 2px bevelled frame, transparent opening, cross muntin, sparse glass highlights."""
    c.rect(0, 0, 16, 16, mid).bevel(0, 0, 16, 16, light, dark)
    c.rect(1, 1, 14, 14, mid).bevel(1, 1, 14, 14, light, dark)
    c.rect(2, 2, 12, 12, '.')
    if muntin:
        c.rect(7, 2, 2, 12, mid).rect(2, 7, 12, 2, mid)
        c.rect(7, 2, 1, 12, light).rect(2, 7, 12, 1, light)
        c.rect(8, 2, 1, 12, dark).rect(2, 8, 12, 1, dark)
    if rivet:
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            c.set(x, y, rivet)


def sparkle(c, ch='v', alt='V'):
    for ox, oy in ((3, 3), (9, 3), (3, 9), (9, 9)):
        c.set(ox, oy, ch).set(ox + 1, oy, ch).set(ox, oy + 1, ch)
        c.set(ox + 2, oy + 2, alt)


def disc(c, r_outer, rings, cx=7.5, cy=7.5, outside=None):
    """Concentric end cap: rings = [(radius, char), ...] from outer to inner."""
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            ch = outside
            for radius, rc in rings:
                if d <= radius:
                    ch = rc
            if ch:
                c.set(x, y, ch)


def shingles(c, rows, base, light, dark, offset_rows=True, patina=None):
    """Overlapping tile rows, each 4px high with 8px wide tiles; patina picks random tiles."""
    for ri, y0 in enumerate(range(0, 16, 4)):
        shift = 4 if offset_rows and ri % 2 else 0
        for tx in range(-8, 16, 8):
            x0 = tx + shift
            ch = base
            if patina and n(x0, y0, 7) % 3 == 0:
                ch = patina[0]
            c.rect(x0, y0, 8, 4, ch)
            c.rect(x0, y0, 8, 1, patina[1] if patina and ch == patina[0] else light)
            c.rect(x0, y0 + 3, 8, 1, dark)
            c.rect(x0 + 7, y0, 1, 4, dark)


# ---------------------------------------------------------------- Timberframe

def timber_beam_h(c, x, y, w, h=2):
    c.rect(x, y, w, h, '6').rect(x, y, w, 1, '5').rect(x, y + h - 1, w, 1, '7')
    for i in range(x + 2, x + w, 5):
        c.set(i, y + (1 if h > 1 else 0), '7')


def timber_beam_v(c, x, y, h, w=2):
    c.rect(x, y, w, h, '6').rect(x, y, 1, h, '5').rect(x + w - 1, y, 1, h, '7')
    for i in range(y + 2, y + h, 5):
        c.set(x + (1 if w > 1 else 0), i, '7')


def timber_wall():
    c = Canvas()
    c.rect(0, 0, 16, 16, '2')
    speckle(c, 0, 0, 16, 16, '13', 9, 3)
    speckle(c, 0, 0, 16, 16, '3', 23, 5)
    # plaster edge shading next to the beams
    c.rect(2, 2, 12, 1, '3').rect(2, 2, 1, 12, '3')
    # diagonal brace
    for i in range(2, 14):
        x, y = i, 15 - i
        c.set(x, y, '6').set(x + 1, y, '5').set(x, y + 1, '7')
    timber_beam_h(c, 0, 0, 16)
    timber_beam_h(c, 0, 14, 16)
    timber_beam_v(c, 0, 0, 16)
    timber_beam_v(c, 14, 0, 16)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.rect(x, y, 2, 2, '6').set(x, y, '5').set(x + 1, y + 1, '7')
    return c.rows()


def timber_floor():
    c = Canvas()
    for row in range(4):
        y = row * 4
        c.rect(0, y, 16, 4, '6')
        c.rect(0, y, 16, 1, '5')
        c.rect(0, y + 3, 16, 1, '7')
        seam = (row * 5 + 3) % 16
        c.rect(seam, y, 1, 4, '7')
        c.set(seam - 1, y + 1, '8').set((seam + 8) % 16, y + 1, '8')  # nails
        for i in range(3):
            c.set((n(row, i, 4) % 14) + 1, y + 1 + (n(i, row, 2) % 2), '5' if i % 2 else '7')
    return c.rows()


def timber_roof():
    c = Canvas()
    shingles(c, 4, 'r', 't', 's')
    speckle(c, 0, 0, 16, 16, 'R', 17, 9)
    return c.rows()


def timber_pillar_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, '6')
    c.rect(0, 0, 1, 16, '7').rect(15, 0, 1, 16, '8').rect(1, 0, 2, 16, '5')
    for x in (4, 8, 11):
        for y in range(16):
            if n(x, y, 8) % 4:
                c.set(x, y, '7' if x != 8 else '5')
    for y in (3, 11):
        c.rect(5, y, 3, 2, '8').set(6, y, '7')
    c.rect(0, 0, 16, 1, '7').rect(0, 15, 16, 1, '8')
    return c.rows()


def timber_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '7')
    for i, ch in enumerate('78565'):
        c.frame(i * 1, i * 1, 16 - i * 2, 16 - i * 2, '6' if i % 2 == 0 else '5')
    c.frame(0, 0, 16, 16, '8')
    c.rect(6, 6, 4, 4, '7').rect(7, 7, 2, 2, '8')
    return c.rows()


def timber_window():
    c = Canvas()
    glass_frame(c, '5', '6', '7')
    sparkle(c)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, '8')
    return c.rows()


def timber_light():
    c = Canvas()
    c.rect(0, 0, 16, 16, '2')
    # warm glow, brighter in the middle
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, 'h' if d < 2.6 else 'g' if d < 5.0 else 'G')
    timber_beam_h(c, 0, 0, 16)
    timber_beam_h(c, 0, 14, 16)
    timber_beam_v(c, 0, 0, 16)
    timber_beam_v(c, 14, 0, 16)
    # lantern: chain, cap, cage, flame
    c.rect(7, 2, 2, 2, 'k')
    c.rect(5, 4, 6, 1, 'k').rect(6, 3, 4, 1, 'k')
    c.rect(5, 5, 1, 6, 'k').rect(10, 5, 1, 6, 'k').rect(5, 11, 6, 1, 'k').rect(6, 12, 4, 1, 'k')
    c.rect(6, 5, 4, 6, 'h')
    c.rect(7, 6, 2, 4, 'f').rect(7, 7, 2, 2, 'g')
    c.set(7, 6, 'f').set(8, 9, 'f')
    c.rect(7, 5, 2, 1, 'k')
    return c.rows()


# ---------------------------------------------------------------- Copper Works

def rivet(c, x, y):
    c.set(x, y, '6').set(x + 1, y + 1, '8')


def copper_wall():
    c = Canvas()
    for px in (0, 8):
        for py in (0, 8):
            c.rect(px, py, 8, 8, '3').bevel(px, py, 8, 8, '2', '4')
            for rx, ry in ((px + 1, py + 1), (px + 5, py + 1), (px + 1, py + 5), (px + 5, py + 5)):
                rivet(c, rx, ry)
            speckle(c, px + 2, py + 2, 4, 4, '2', 7, px + py + 3)
    # verdigris stains running down from the seams
    for x in (3, 4, 12):
        for y in range(8, 8 + 2 + n(x, 0, 3) % 4):
            c.set(x, y, 'Z' if y % 2 else 'z')
    return c.rows()


def copper_floor():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3').bevel(0, 0, 16, 16, '2', '5')
    c.rect(1, 1, 14, 14, '3').bevel(1, 1, 14, 14, '4', '2')
    # tread marks: short diagonals in a grid
    for ty in range(3, 13, 3):
        for tx in range(3, 13, 3):
            if (tx // 3 + ty // 3) % 2:
                c.set(tx, ty, '2').set(tx + 1, ty + 1, '4')
            else:
                c.set(tx + 1, ty, '2').set(tx, ty + 1, '4')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rect(x, y, 2, 2, '5').set(x, y, '6').set(x + 1, y + 1, '8')
    return c.rows()


def copper_roof():
    c = Canvas()
    shingles(c, 4, '3', '2', '5', patina=('Z', 'z'))
    return c.rows()


def copper_pillar_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    c.rect(0, 0, 2, 16, '2').rect(2, 0, 1, 16, '1').rect(13, 0, 3, 16, '4').rect(15, 0, 1, 16, '5')
    for y in (0, 7, 14):
        c.rect(0, y, 16, 2, '6').rect(0, y, 16, 1, '6').rect(0, y + 1, 16, 1, '8')
        c.set(3, y, 'h').set(12, y, 'h')
    for y in (3, 4, 5, 10, 11, 12):
        c.set(7, y, '4').set(8, y, '2')
    return c.rows()


def copper_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    disc(c, 7.5, [(7.6, '3'), (6.5, '6'), (5.2, '8'), (4.6, '3'), (2.2, '4')], outside='4')
    c.rect(7, 7, 2, 2, '6').set(7, 7, 'h')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.set(x, y, '6').set(x + 1, y + 1, '5')
    return c.rows()


def copper_window():
    c = Canvas()
    glass_frame(c, '2', '3', '4', rivet='6')
    sparkle(c)
    return c.rows()


def copper_light():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3').bevel(0, 0, 16, 16, '2', '5')
    c.rect(1, 1, 14, 14, '3').bevel(1, 1, 14, 14, '4', '2')
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, 'h' if d < 2.4 else 'g' if d < 4.8 else 'f')
    # lamp cage bars
    for x in (5, 10):
        c.rect(x, 2, 1, 12, '4')
    c.rect(2, 7, 12, 2, '4').rect(2, 7, 12, 1, '3')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, '6')
    return c.rows()


# ---------------------------------------------------------------- Steel Lab

def steel_wall():
    c = Canvas()
    for px in (0, 8):
        for py in (0, 8):
            c.rect(px, py, 8, 8, '2').bevel(px, py, 8, 8, '1', '4')
            c.set(px + 1, py + 1, '4').set(px + 6, py + 6, '4')
    # status light and a vent on two panels
    c.rect(11, 2, 2, 2, 'f').set(11, 2, 'h').set(12, 2, 'g')
    for y in (11, 12, 13):
        c.rect(2, y, 4, 1, '4')
    # seam shadow
    c.rect(7, 0, 1, 16, '3').rect(0, 7, 16, 1, '3')
    return c.rows()


def steel_floor():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'K')
    for y in range(16):
        for x in range(16):
            bar_x = x % 4 < 2
            bar_y = y % 4 < 2
            if bar_x or bar_y:
                c.set(x, y, '3')
                if x % 4 == 0 or y % 4 == 0:
                    c.set(x, y, '2')
                elif x % 4 == 1 and y % 4 == 1:
                    c.set(x, y, '4')
            else:
                c.set(x, y, 'K' if (x + y) % 3 else 'k')
    c.frame(0, 0, 16, 16, '4')
    c.rect(1, 1, 14, 1, '2')
    return c.rows()


def steel_roof():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    for py in (0, 8):
        c.rect(0, py, 16, 8, '3').bevel(0, py, 16, 8, '2', '4')
        c.rect(7, py + 3, 2, 2, '4').set(7, py + 3, '2')
    c.rect(4, 0, 1, 16, '4').rect(11, 0, 1, 16, '4')
    c.rect(4, 3, 1, 2, '2').rect(11, 11, 1, 2, '2')
    return c.rows()


def steel_pillar_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    c.rect(0, 0, 2, 16, '2').rect(2, 0, 1, 16, '1').rect(13, 0, 3, 16, '4')
    for x in (5, 10):
        c.rect(x, 0, 1, 16, '4')
    for y in (0, 15):
        c.rect(0, y, 16, 1, '4')
    c.rect(0, 6, 16, 4, '4').rect(0, 6, 16, 1, '3').rect(0, 9, 16, 1, '5')
    c.rect(6, 7, 4, 2, 'K').rect(7, 7, 2, 2, 'g').set(7, 7, 'h')
    return c.rows()


def steel_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    disc(c, 7.5, [(7.6, '3'), (6.3, '4'), (5.4, '2'), (3.4, '3'), (2.0, 'K'), (1.2, 'g')], outside='4')
    c.set(7, 7, 'h')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, '2')
    return c.rows()


def steel_window():
    c = Canvas()
    glass_frame(c, '1', '2', '4')
    # cyan tint corners
    for ox, oy in ((2, 2), (11, 2), (2, 11), (11, 11)):
        c.set(ox, oy, 'V')
    sparkle(c, 'v', 'g')
    c.rect(3, 3, 1, 1, 'g')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, '4')
    return c.rows()


def steel_light():
    c = Canvas()
    c.rect(0, 0, 16, 16, '2').bevel(0, 0, 16, 16, '1', '4')
    c.rect(1, 1, 14, 14, '2').bevel(1, 1, 14, 14, '3', '1')
    c.rect(2, 4, 12, 8, 'K')
    c.rect(2, 4, 12, 1, '5').rect(2, 11, 12, 1, '5')
    c.rect(3, 5, 10, 6, 'f')
    c.rect(3, 6, 10, 4, 'g')
    c.rect(4, 7, 8, 2, 'h')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, '4')
    return c.rows()


# ---------------------------------------------------------------- Null Spire

def null_wall():
    c = Canvas()
    for px in (0, 8):
        for py in (0, 8):
            c.rect(px, py, 8, 8, '3').bevel(px, py, 8, 8, '2', '5')
            # glossy streak
            c.set(px + 2, py + 2, '1').set(px + 3, py + 2, '1').set(px + 2, py + 3, '1')
            c.set(px + 4, py + 1, '2').set(px + 1, py + 4, '2')
    # glowing teal seams
    for i in range(16):
        c.set(7, i, 'f').set(8, i, 'g')
        c.set(i, 7, 'f').set(i, 8, 'g')
    c.rect(7, 7, 2, 2, 'h')
    # rune dots
    for x, y in ((3, 4), (12, 11)):
        c.set(x, y, 'g')
    return c.rows()


def null_floor():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    for px in range(0, 16, 4):
        for py in range(0, 16, 4):
            c.rect(px, py, 4, 4, '3' if (px + py) % 8 == 0 else '2').bevel(px, py, 4, 4, '1' if (px + py) % 8 else '2', '5')
    c.rect(0, 7, 16, 2, '5').rect(7, 0, 2, 16, '5')
    for i in range(16):
        c.set(i, 7, 'f').set(7, i, 'f')
    c.rect(6, 6, 4, 4, 'f').rect(7, 7, 2, 2, 'h')
    c.set(6, 6, '5').set(9, 9, '5').set(9, 6, '5').set(6, 9, '5')
    return c.rows()


def null_roof():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4').bevel(0, 0, 16, 16, '2', '5')
    for i in range(1, 15):
        c.set(i, i, 'f').set(15 - i, i, 'f')
        if i % 3 == 0:
            c.set(i, i, 'g').set(15 - i, i, 'g')
    c.rect(6, 6, 4, 4, '3').bevel(6, 6, 4, 4, '2', '5')
    c.rect(7, 7, 2, 2, 'g')
    return c.rows()


def null_pillar_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    c.rect(0, 0, 2, 16, '2').rect(2, 0, 1, 16, '1').rect(13, 0, 3, 16, '4').rect(15, 0, 1, 16, '5')
    c.rect(6, 0, 1, 16, 'f').rect(9, 0, 1, 16, 'f').rect(7, 0, 2, 16, 'g')
    for y in (0, 15):
        c.rect(0, y, 16, 1, '5')
    for y in (4, 11):
        c.rect(3, y, 10, 1, '5').rect(7, y, 2, 1, 'h')
    return c.rows()


def null_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    disc(c, 7.5, [(7.6, '3'), (6.6, '5'), (5.6, 'g'), (4.6, '3'), (3.0, '4'), (1.5, 'h')], outside='4')
    c.frame(0, 0, 16, 16, '5')
    return c.rows()


def null_window():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'v')
    for i in (5, 10):
        c.rect(i, 0, 1, 16, 'V').rect(0, i, 16, 1, 'V')
    for i in range(16):
        if i % 4 == 0:
            c.set(i, i, 'V').set(15 - i, i, 'V')
    c.frame(0, 0, 16, 16, '3')
    c.frame(1, 1, 14, 14, 'f')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, '5')
    c.rect(7, 7, 2, 2, 'h')
    return c.rows()


def null_light():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3').bevel(0, 0, 16, 16, '2', '5')
    c.frame(1, 1, 14, 14, '5')
    for y in range(2, 14):
        for x in range(2, 14):
            d = abs(x - 7.5) + abs(y - 7.5)
            c.set(x, y, 'h' if d < 3 else 'g' if d < 7 else 'f')
    c.frame(2, 2, 12, 12, 'f')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, '5')
    return c.rows()


# ---------------------------------------------------------------- Architect Table

TABLE = {
    'i': '#8D969B', 'I': '#C2CBCF', 'j': '#5A6367', 'k': '#2A3033',
    'c': '#C87533', 'C': '#E8A060', 'd': '#8A4A22',
    'b': '#2B5C8F', 'B': '#3E78B4', 'w': '#E6F0FF', 'y': '#5FE3F0', 'Y': '#D8FBFF',
    'o': '#A97A44', 'O': '#C9985C',
}


def table_top():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'j').bevel(0, 0, 16, 16, 'I', 'k')
    c.rect(1, 1, 14, 14, 'c').bevel(1, 1, 14, 14, 'C', 'd')
    c.rect(2, 2, 12, 12, 'b')
    for i in range(2, 14, 3):
        c.rect(i, 2, 1, 12, 'B').rect(2, i, 12, 1, 'B')
    # 5x5 plot grid with the table plot in the middle
    for gy in range(5):
        for gx in range(5):
            x, y = 3 + gx * 2, 3 + gy * 2
            c.rect(x, y, 2, 2, 'w' if (gx, gy) != (2, 2) else 'y')
            c.rect(x, y, 1, 1, 'b' if (gx, gy) != (2, 2) else 'Y')
    c.set(2, 2, 'I')
    return c.rows()


def table_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'i').bevel(0, 0, 16, 16, 'I', 'j')
    c.rect(0, 0, 16, 3, 'c').bevel(0, 0, 16, 3, 'C', 'd')
    c.rect(2, 5, 12, 9, 'j').frame(2, 5, 12, 9, 'k')
    c.rect(3, 6, 10, 7, 'b')
    # drafting decal: T-square and a plot outline
    c.rect(3, 6, 10, 1, 'O').rect(3, 6, 1, 7, 'O')
    c.rect(6, 8, 5, 3, 'w').frame(6, 8, 5, 3, 'w').rect(7, 9, 3, 1, 'b')
    c.set(12, 12, 'y').set(11, 12, 'B')
    for x in (1, 14):
        c.set(x, 4, 'I').set(x, 14, 'j')
    return c.rows()


def table_bottom():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'j').bevel(0, 0, 16, 16, 'i', 'k')
    c.rect(2, 2, 12, 12, 'i').bevel(2, 2, 12, 12, 'I', 'j')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'k')
    return c.rows()


# ---------------------------------------------------------------- builder drone (32x32 entity texture)

DRONE = {'a': '#4D5558', 'A': '#8D969B', 'D': '#2A3033', 'e': '#5FE3F0', 'E': '#D8FBFF', 'c': '#C87533', 'C': '#E8A060'}


def drone_texture():
    c = Canvas(32)
    # body 6x6x6 at (0,0): top (6,0), bottom (12,0), left (0,6), front (6,6), right (12,6), back (18,6)
    for (x, y) in ((6, 0), (12, 0), (0, 6), (6, 6), (12, 6), (18, 6)):
        c.rect(x, y, 6, 6, 'a').bevel(x, y, 6, 6, 'A', 'D')
    c.rect(8, 8, 2, 2, 'e').set(8, 8, 'E')  # eye on the front face
    c.rect(7, 11, 4, 1, 'c')
    c.rect(2, 2, 2, 2, 'c')
    # rotor 4x0.5x4 at (0,14)
    c.rect(0, 14, 16, 6, 'D')
    c.rect(4, 14, 4, 4, 'A').rect(0, 18, 16, 1, 'a')
    return c.rows()


def main():
    styles = {
        'timberframe': (TIMBER, [timber_wall, timber_floor, timber_roof, timber_pillar_side, timber_pillar_end, timber_window, timber_light]),
        'copper_works': (COPPER, [copper_wall, copper_floor, copper_roof, copper_pillar_side, copper_pillar_end, copper_window, copper_light]),
        'steel_lab': (STEEL, [steel_wall, steel_floor, steel_roof, steel_pillar_side, steel_pillar_end, steel_window, steel_light]),
        'null_spire': (NULL, [null_wall, null_floor, null_roof, null_pillar_side, null_pillar_end, null_window, null_light]),
    }
    names = ['wall', 'floor', 'roof', 'pillar', 'pillar_top', 'window', 'light']
    for style, (palette, makers) in styles.items():
        for name, maker in zip(names, makers):
            write_block(f'{style}_{name}', maker(), palette)
    write_block('architect_table_top', table_top(), TABLE)
    write_block('architect_table_side', table_side(), TABLE)
    write_block('architect_table_bottom', table_bottom(), TABLE)
    write_png(ASSETS / 'textures/entity/builder_drone.png', drone_texture(), DRONE, size=32)


if __name__ == '__main__':
    main()
