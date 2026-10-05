"""Architect module textures: 4 styles x 6 roles (with random variants), the Architect Table and the builder drone.
Run: python3 scripts/textures/architect.py   (writes textures/block/*.png and textures/entity/builder_drone.png;
models, blockstates, recipes and lang are written by scripts/data/architect_*.py)

Walls, floors and roofs tile seamlessly: seams sit on one block edge or are staggered, and low-frequency noise wraps
at 16 px. Each has 3 variants (<name>, <name>_2, <name>_3) that the blockstate mixes with weights, so large surfaces
never show a repeating stamp. Pillars have no joint at their ends, so stacked posts read as one column. Light blocks
are a plain frame plus a bright diffuser; <style>_light_glow is the diffuser alone, drawn as a full-bright overlay.

One family of light-to-mid stone, from plain to detailed (registry ids are historic and stay):
  timberframe_*   Clean Stone     light warm concrete panels with subtle seams, running-bond floor slabs, slate roof
  copper_works_*  Smooth Panel    pearl white near-seamless panels, glossy floor tiles, standing seam roof
  steel_lab_*     Detailed Stone  bevelled limestone bricks, chiseled floor tiles, clay shingle roof, fluted pillar
  null_spire_*    Tech Stone      cool light stone with steel inlays, rivets and a thin cyan accent line
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, Canvas, write_block, write_png  # noqa: E402

# Glass glints shared by every window; light diffusers use f (edge) g (body) h (centre), nothing else does.
CLEAN = {
    '0': '#F1EFEA', '1': '#E7E4DE', '2': '#DDDAD3', '3': '#D2CEC6', '4': '#BEB9AF', '5': '#A39E94', '6': '#8A857C',
    'r': '#A3A9AE', 'R': '#92989E', 's': '#787E85', 't': '#B9BEC2',  # slate roof
    'f': '#F7E2B2', 'g': '#FFEFCB', 'h': '#FFF9EA',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
SMOOTH = {
    '0': '#F7F8F9', '1': '#EFF1F3', '2': '#E7EAED', '3': '#DCE0E4', '4': '#C7CDD2', '5': '#AEB5BC', '6': '#949CA4',
    'f': '#DCEFF7', 'g': '#EDF8FC', 'h': '#FFFFFF',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
DETAILED = {
    '0': '#F2EADB', '1': '#E8DEC9', '2': '#DCD0B8', '3': '#CEC0A5', '4': '#B8A88C', '5': '#9E8E73', '6': '#857760',
    'r': '#B88D72', 'R': '#A67A5E', 's': '#87604A', 't': '#CDA488',  # clay shingles
    'f': '#FFD99A', 'g': '#FFE9BF', 'h': '#FFF7E3',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
TECH = {
    '0': '#EEF1F3', '1': '#E3E7EA', '2': '#D7DCE0', '3': '#C9CFD4', '4': '#B2B9BF', '5': '#99A1A8',
    'N': '#E2E7EA', 'M': '#B8C0C6', 'm': '#8F989F', 'n': '#6F787F',  # steel inlay, light to dark
    'a': '#3CC6D6', 'A': '#A6F2F9',  # accent line
    'f': '#B2EEF5', 'g': '#D8F9FC', 'h': '#F5FEFF',
    'v': '#BFF4FA', 'V': '#7FDCE899', 'w': '#BFEFF52A',
}


# ---------------------------------------------------------------- helpers

def n(x, y, s=1):
    """Deterministic hash noise 0..65535."""
    h = (x * 374761393 + y * 668265263 + s * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


def vnoise(x, y, seed, cell=4):
    """Smooth value noise that wraps at 16 px, 0..1."""
    g = 16 // cell

    def r(i, j):
        return n(i % g, j % g, seed) / 65535.0

    fx, fy = x / cell, y / cell
    i, j = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - i, fy - j
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = r(i, j) + (r(i + 1, j) - r(i, j)) * tx
    b = r(i, j + 1) + (r(i + 1, j + 1) - r(i, j + 1)) * tx
    return a + (b - a) * ty


def field(seed, octaves=((8, 0.6), (4, 0.4))):
    return lambda x, y: sum(vnoise(x, y, seed + k, cell) * w for k, (cell, w) in enumerate(octaves))


def tone(c, ramp, f, x0=0, y0=0, w=16, h=16, jitter=0.08, seed=5):
    """Fills a box from a 0..1 field mapped onto a ramp of chars (dark to light), with a little per-pixel jitter."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = f(x, y) + (n(x, y, seed) / 65535.0 - 0.5) * jitter * 2
            c.set(x, y, ramp[max(0, min(len(ramp) - 1, int(v * len(ramp))))])


def speckle(c, chars, every, seed=1, x0=0, y0=0, w=16, h=16, only=None):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = n(x, y, seed)
            if v % every == 0 and (only is None or c.get(x, y) in only):
                c.set(x, y, chars[(v >> 4) % len(chars)])


def glow_only(rows, glow_chars):
    """Keeps only the glowing pixels (the overlay layer of a light block)."""
    return [''.join(ch if ch in glow_chars else '.' for ch in row) for row in rows]


def diffuser(c, x0, size):
    """Bright, even light panel: a soft f rim, g body and an h centre. No pictures, no ribs."""
    for y in range(x0, x0 + size):
        for x in range(x0, x0 + size):
            d = max(abs(x - 7.5), abs(y - 7.5))
            edge = (size / 2) - 0.5
            c.set(x, y, 'f' if d >= edge else 'g' if d >= edge - 2 or (abs(x - 7.5) + abs(y - 7.5)) > edge + 1 else 'h')


def light_frame(c, outer_light, outer_dark, ring, width=2):
    """Plain square frame, bevelled from the top-left, `width` px wide."""
    c.rect(0, 0, 16, 16, ring)
    c.rect(0, 0, 16, 1, outer_light).rect(0, 0, 1, 16, outer_light)
    c.rect(0, 15, 16, 1, outer_dark).rect(15, 0, 1, 16, outer_dark)
    if width > 1:
        c.rect(width - 1, width - 1, 16 - 2 * (width - 1), 1, outer_dark).rect(width - 1, width - 1, 1, 16 - 2 * (width - 1), outer_dark)
        c.rect(width - 1, 16 - width, 16 - 2 * (width - 1), 1, outer_light).rect(16 - width, width - 1, 1, 16 - 2 * (width - 1), outer_light)


def glass(c):
    """Two diagonal glints and a corner sparkle on clear glass."""
    for ox, oy in ((3, 9), (9, 3)):
        c.set(ox, oy + 2, 'v').set(ox + 1, oy + 1, 'v').set(ox + 2, oy, 'v')
    c.set(12, 4, 'V').set(11, 5, 'V')


def window_frame(c, light, mid, dark):
    c.frame(0, 0, 16, 16, mid)
    c.rect(0, 0, 16, 1, light).rect(0, 0, 1, 15, light)
    c.rect(0, 15, 16, 1, dark).rect(15, 1, 1, 15, dark)


# ---------------------------------------------------------------- Clean Stone (tier 1, ids timberframe_*)

def clean_wall(v):
    """One concrete panel per block: a soft seam on the right and bottom, a faint lit edge top and left."""
    c = Canvas()
    ramp = {1: '32222111', 2: '3322221', 3: '32222211'}[v]
    tone(c, ramp, field(10 + v, ((8, 0.75), (4, 0.25))), jitter=0.04, seed=3 + v)
    speckle(c, '3', 37, 7 + v, only='12')
    c.rect(0, 0, 16, 1, '1').rect(0, 0, 1, 15, '1')
    c.rect(0, 15, 16, 1, '4').rect(15, 0, 1, 15, '4')
    if v == 3:  # formwork tie holes
        for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
            c.set(x, y, '5').set(x + 1, y, '3').set(x, y + 1, '3').set(x + 1, y + 1, '1')
    return c.rows()


def clean_floor(v):
    """Running-bond stone slabs, 16x8, the lower row shifted half a slab."""
    c = Canvas()
    tone(c, {1: '32221', 2: '322211', 3: '332221'}[v], field(20 + v), jitter=0.05, seed=21 + v)
    speckle(c, '3', 41, 22 + v, only='12')
    for y0, jx in ((0, 15), (8, 7)):
        c.rect(0, y0, 16, 1, '1')
        c.rect(0, y0 + 7, 16, 1, '4')
        c.rect(jx, y0, 1, 7, '4').set((jx + 1) % 16, y0 + 1, '1')
        for y in range(y0 + 1, y0 + 7):
            c.set((jx + 1) % 16, y, '1')
    if v == 3:  # a small chip in a slab corner
        c.set(9, 6, '3').set(10, 6, '3').set(10, 5, '3')
    return c.rows()


def clean_roof(v):
    """Slate courses: 4 px rows of staggered 8 px slates."""
    c = Canvas()
    for ri, y0 in enumerate(range(0, 16, 4)):
        shift = 4 if ri % 2 else 0
        for tx in range(-8, 16, 8):
            x0 = tx + shift
            k = n((x0 % 16) // 4, ri, 40 + v) % 7
            base = 'R' if (v >= 2 and k < 2) else 'r'
            c.rect(x0, y0, 8, 4, base)
            c.rect(x0, y0, 8, 1, 't')
            c.rect(x0, y0 + 3, 8, 1, 's')
            c.rect(x0 + 7, y0, 1, 3, 's')
    if v == 3:
        c.set(5, 6, 't').set(12, 13, 't')
    return c.rows()


def clean_pillar_side():
    c = Canvas()
    for x, ch in enumerate('1001122222223345'):
        c.rect(x, 0, 1, 16, ch)
    tone(c, '22221', lambda x, y: vnoise(x, y, 50, 8) * 0.5 + 0.3, 4, 0, 8, 16, jitter=0.05, seed=51)
    return c.rows()


def clean_pillar_end():
    c = Canvas()
    tone(c, '3222211', field(55), jitter=0.04, seed=56)
    c.bevel(0, 0, 16, 16, '1', '5')
    c.bevel(3, 3, 10, 10, '4', '0')
    return c.rows()


def clean_window():
    c = Canvas()
    window_frame(c, '1', '3', '5')
    c.frame(1, 1, 14, 14, '2').rect(1, 14, 14, 1, '4').rect(14, 1, 1, 14, '4')
    glass(c)
    return c.rows()


def clean_light():
    c = Canvas()
    light_frame(c, '1', '5', '3')
    diffuser(c, 2, 12)
    return c.rows()


# ---------------------------------------------------------------- Smooth Panel (tier 2, ids copper_works_*)

def smooth_wall(v):
    """Near-seamless: almost flat, a hairline joint on the bottom edge (and on the right for one variant)."""
    c = Canvas()
    tone(c, '22222211', field(60 + v, ((16, 0.6), (8, 0.4))), jitter=0.025, seed=61 + v)
    c.rect(0, 15, 16, 1, '3')
    if v == 2:
        c.rect(15, 0, 1, 15, '3')
    if v == 3:  # a slightly brighter panel
        c.rect(0, 0, 16, 15, '1')
        tone(c, '2111110', field(65, ((16, 1.0),)), 0, 0, 16, 15, jitter=0.02, seed=66)
    return c.rows()


def smooth_floor(v):
    """Large glossy tiles, hairline joints, a faint soft sheen."""
    c = Canvas()
    tone(c, '3322221', field(70 + v, ((16, 0.6), (8, 0.4))), jitter=0.025, seed=71 + v)
    for y in range(16):  # soft sheen band, wraps
        for x in range(16):
            if (x + y) % 16 in (5, 6, 7) and c.get(x, y) in '23':
                c.set(x, y, '1' if (x + y) % 16 == 6 else c.get(x, y) if c.get(x, y) == '2' else '2')
    c.rect(0, 15, 16, 1, '4').rect(15, 0, 1, 16, '4')
    if v == 3:
        c.rect(7, 0, 1, 15, '3')
    return c.rows()


def smooth_roof(v):
    """Standing seam panels, mid grey."""
    c = Canvas()
    tone(c, '5444', lambda x, y: vnoise(x, y * 0.25, 80 + v, 4), jitter=0.04, seed=81 + v)
    for x in range(0, 16, 8):
        c.rect(x, 0, 1, 16, '3').rect(x + 1, 0, 1, 16, '6')
    if v >= 2:
        y = 5 if v == 2 else 12
        c.rect(2, y, 6, 1, '6').rect(10, y, 6, 1, '6')
    return c.rows()


def smooth_pillar_side():
    c = Canvas()
    for x, ch in enumerate('2100011122223345'):
        c.rect(x, 0, 1, 16, ch)
    return c.rows()


def smooth_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '2').bevel(0, 0, 16, 16, '1', '4')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 < d < 6.3:
                c.set(x, y, '3' if y + x > 15 else '4')
    return c.rows()


def smooth_window():
    c = Canvas()
    window_frame(c, '0', '2', '4')
    glass(c)
    return c.rows()


def smooth_light():
    c = Canvas()
    light_frame(c, '1', '4', '2', width=1)
    diffuser(c, 1, 14)
    return c.rows()


# ---------------------------------------------------------------- Detailed Stone (tier 3, ids steel_lab_*)

def detailed_wall(v):
    """Bevelled limestone bricks, 8x4, staggered by half a brick per course."""
    c = Canvas()
    for ri, y0 in enumerate(range(0, 16, 4)):
        shift = 4 if ri % 2 else 0
        for tx in range(-8, 16, 8):
            x0 = tx + shift
            k = n((x0 % 16) // 4, ri, 100 + v) % 9
            ramp = '3321' if k < 2 else '43221' if k == 4 else '32211'
            tone(c, ramp, field(100 + ri + 4 * v), x0, y0, 8, 4, jitter=0.1, seed=101 + v)
            c.rect(x0, y0, 7, 1, '0' if k else '1').rect(x0, y0, 1, 3, '1')
            c.rect(x0 + 1, y0 + 2, 6, 1, '4').rect(x0 + 6, y0 + 1, 1, 2, '4')
            c.rect(x0, y0 + 3, 8, 1, '5').rect(x0 + 7, y0, 1, 4, '5')
    if v == 2:  # one brick with a chiseled cross
        c.set(10, 5, '4').set(11, 6, '4').set(12, 5, '4').set(11, 5, '0')
    if v == 3:  # weathered corner
        c.set(1, 9, '4').set(2, 10, '3').set(13, 1, '3')
    return c.rows()


def detailed_floor(v):
    """8x8 bevelled tiles in a checker of plain and chiseled (inner square) tiles."""
    c = Canvas()
    for ty in range(2):
        for tx in range(2):
            x0, y0 = tx * 8, ty * 8
            chiseled = (tx + ty) % 2 == 1
            tone(c, '3222' if chiseled else '32211', field(110 + tx + 2 * ty + 4 * v), x0, y0, 8, 8, jitter=0.08, seed=111 + v)
            c.rect(x0, y0, 7, 1, '0').rect(x0, y0, 1, 7, '0')
            c.rect(x0 + 1, y0 + 6, 6, 1, '4').rect(x0 + 6, y0 + 1, 1, 6, '4')
            c.rect(x0, y0 + 7, 8, 1, '5').rect(x0 + 7, y0, 1, 8, '5')
            if chiseled:  # raised 3x3 boss with a cast shadow
                c.rect(x0 + 2, y0 + 2, 3, 3, '1' if v != 2 else '0')
                c.rect(x0 + 3, y0 + 5, 3, 1, '4').rect(x0 + 5, y0 + 3, 1, 3, '4')
    return c.rows()


def detailed_roof(v):
    """Clay shingles, 4 px rows of staggered 4 px tiles with a lit top and a shadowed lap."""
    c = Canvas()
    for ri, y0 in enumerate(range(0, 16, 4)):
        shift = 2 if ri % 2 else 0
        for tx in range(-4, 16, 4):
            x0 = tx + shift
            k = n((x0 % 16) // 2, ri, 150 + v) % 5
            c.rect(x0, y0, 4, 4, 'R' if k == 0 and v >= 2 else 'r')
            c.rect(x0, y0, 3, 1, 't')
            c.rect(x0, y0 + 3, 4, 1, 's')
            c.rect(x0 + 3, y0, 1, 3, 'R' if k else 's')
    return c.rows()


def detailed_pillar_side():
    """Fluted column: three flutes between bevelled edges."""
    c = Canvas()
    for x, ch in enumerate('1054213421342145'):
        c.rect(x, 0, 1, 16, ch)
    return c.rows()


def detailed_pillar_end():
    c = Canvas()
    tone(c, '32211', field(165), jitter=0.06, seed=166)
    c.bevel(0, 0, 16, 16, '0', '5')
    c.bevel(2, 2, 12, 12, '4', '1')
    c.bevel(4, 4, 8, 8, '1', '4')
    c.bevel(6, 6, 4, 4, '4', '0')
    return c.rows()


def detailed_window():
    c = Canvas()
    window_frame(c, '1', '3', '5')
    c.rect(7, 1, 1, 14, '3').rect(8, 1, 1, 14, '4')  # mullion
    c.rect(1, 7, 14, 1, '3').rect(1, 8, 14, 1, '4')  # transom
    c.set(3, 11, 'v').set(4, 10, 'v').set(11, 4, 'v').set(12, 3, 'V').set(10, 12, 'V')
    return c.rows()


def detailed_light():
    c = Canvas()
    light_frame(c, '0', '5', '3')
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):  # chiseled corner blocks
        c.rect(x, y, 2, 2, '4').set(x, y, '1')
    diffuser(c, 2, 12)
    return c.rows()


# ---------------------------------------------------------------- Tech Stone (tier 4, ids null_spire_*)

def tech_wall(v):
    """Light stone panel above a continuous steel inlay band with a thin accent line and rivets."""
    c = Canvas()
    tone(c, '322211', field(130 + v), 0, 0, 16, 11, jitter=0.05, seed=131 + v)
    c.rect(0, 0, 16, 1, '1').rect(0, 0, 1, 11, '1').rect(15, 0, 1, 11, '4')
    c.rect(0, 11, 16, 1, 'M').rect(0, 12, 16, 1, 'a').rect(0, 13, 16, 1, 'm').rect(0, 14, 16, 1, 'n')
    tone(c, '322211', field(135 + v), 0, 15, 16, 1, jitter=0.05, seed=136 + v)
    for x in (3, 11):  # rivets sit on the band edge, the accent line runs between them
        c.set(x, 11, 'N').set(x, 12, 'm').set(x, 13, 'n')
    if v == 2:  # flush stone access plate in a thin steel frame, rivets in the corners
        c.frame(4, 2, 8, 7, 'm').rect(5, 3, 6, 5, '1').rect(5, 7, 6, 1, '3')
        for x, y in ((4, 2), (11, 2), (4, 8), (11, 8)):
            c.set(x, y, 'N')
    if v == 3:  # vertical inlay from the band into the panel, ending in an accent dot
        c.rect(7, 4, 1, 7, 'm').rect(8, 4, 1, 7, 'n').set(7, 3, 'a').set(8, 3, 'A')
    return c.rows()


def tech_floor(v):
    """8x8 stone tiles framed by steel inlay lines, rivets where they cross."""
    c = Canvas()
    tone(c, '32221', field(140 + v), jitter=0.06, seed=141 + v)
    for t in (7, 15):
        c.rect(0, t, 16, 1, 'm').rect(t, 0, 1, 16, 'm')
    for t in (0, 8):
        c.rect(0, t, 16, 1, '1').rect(t, 0, 1, 16, '1')
        c.rect(0, t + 6, 16, 1, '3').rect(t + 6, 0, 1, 16, '3')
    for t in (7, 15):
        c.rect(0, t, 16, 1, 'm').rect(t, 0, 1, 16, 'm')
    for x in (7, 15):
        for y in (7, 15):
            c.set(x, y, 'N')
    if v == 2:  # one tile is a steel plate
        c.rect(8, 0, 7, 7, 'M').bevel(8, 0, 7, 7, 'N', 'm')
        c.set(9, 1, 'n').set(13, 1, 'n').set(9, 5, 'n').set(13, 5, 'n')
    if v == 3:  # accent along one inlay segment
        c.rect(0, 7, 7, 1, 'a')
    return c.rows()


def tech_roof(v):
    """Mid grey stone plates with steel seams and rivets."""
    c = Canvas()
    tone(c, '5444', field(150 + v), jitter=0.06, seed=151 + v)
    for y0 in (0, 8):
        c.rect(0, y0, 16, 1, '3')
        c.rect(0, y0 + 7, 16, 1, 'n')
    for x, y0 in ((7, 0), (15, 8)):
        c.rect(x, y0, 1, 7, 'm')
    for x, y in ((2, 2), (12, 2), (5, 10), (10, 10)):
        c.set(x, y, 'M')
    if v == 3:
        c.rect(1, 15, 6, 1, 'a')
    return c.rows()


def tech_pillar_side():
    c = Canvas()
    for x, ch in enumerate('NM1122nan2223mnn'):
        c.rect(x, 0, 1, 16, ch)
    for y in (2, 10):  # rivets on the steel edges
        c.set(1, y, 'N').set(14, y, 'M')
    return c.rows()


def tech_pillar_end():
    c = Canvas()
    tone(c, '32211', field(165), jitter=0.05, seed=166)
    c.bevel(0, 0, 16, 16, 'M', 'n')
    c.bevel(1, 1, 14, 14, 'm', 'N')
    c.rect(6, 6, 4, 4, 'n').rect(7, 7, 2, 2, 'a').set(7, 7, 'A')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'M')
    return c.rows()


def tech_window():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    window_frame(c, 'M', 'm', 'n')
    c.rect(1, 0, 14, 1, 'a')
    for ox, oy in ((3, 9), (9, 3)):
        c.set(ox, oy + 2, 'V').set(ox + 1, oy + 1, 'V').set(ox + 2, oy, 'V')
    return c.rows()


def tech_light():
    c = Canvas()
    light_frame(c, 'M', 'n', 'm')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, 'N')
    diffuser(c, 2, 12)
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


STYLES = {
    'timberframe': (CLEAN, clean_wall, clean_floor, clean_roof, clean_pillar_side, clean_pillar_end, clean_window, clean_light, 'fgh'),
    'copper_works': (SMOOTH, smooth_wall, smooth_floor, smooth_roof, smooth_pillar_side, smooth_pillar_end, smooth_window, smooth_light, 'fgh'),
    'steel_lab': (DETAILED, detailed_wall, detailed_floor, detailed_roof, detailed_pillar_side, detailed_pillar_end, detailed_window, detailed_light, 'fgh'),
    'null_spire': (TECH, tech_wall, tech_floor, tech_roof, tech_pillar_side, tech_pillar_end, tech_window, tech_light, 'fgh'),
}
VARIANTS = 3


def variant_name(base, v):
    return base if v == 1 else f'{base}_{v}'


def main():
    for style, (pal, wall, floor, roof, pside, pend, window, light, glow) in STYLES.items():
        for v in range(1, VARIANTS + 1):
            write_block(variant_name(f'{style}_wall', v), wall(v), pal)
            write_block(variant_name(f'{style}_floor', v), floor(v), pal)
            write_block(variant_name(f'{style}_roof', v), roof(v), pal)
        write_block(f'{style}_pillar', pside(), pal)
        write_block(f'{style}_pillar_top', pend(), pal)
        write_block(f'{style}_window', window(), pal)
        rows = light()
        write_block(f'{style}_light', rows, pal)
        write_block(f'{style}_light_glow', glow_only(rows, glow), pal)
    write_block('architect_table_top', table_top(), TABLE)
    write_block('architect_table_side', table_side(), TABLE)
    write_block('architect_table_bottom', table_bottom(), TABLE)
    write_png(ASSETS / 'textures/entity/builder_drone.png', drone_texture(), DRONE, size=32)


if __name__ == '__main__':
    main()
