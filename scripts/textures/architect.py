"""Architect module textures: 4 styles x 6 roles (with random variants), the Architect Table and the builder drone.
Run: python3 scripts/textures/architect.py   (writes textures/block/*.png, textures/entity/builder_drone.png and the
Pocket Architect item sprites;
models, blockstates, recipes and lang are written by scripts/data/architect_*.py)

Walls, floors and roofs tile seamlessly: seams sit on one block edge or are staggered, and low-frequency noise wraps
at 16 px. Each has 3 variants (<name>, <name>_2, <name>_3) that the blockstate mixes with weights, so large surfaces
never show a repeating stamp. Clean Stone pillars have no joint at their ends; the other styles' trim
connects (one texture per face shape, see trim()), so posts and beams join at corners. Light blocks
are a plain frame plus a bright diffuser; <style>_light_glow is the diffuser alone, drawn as a full-bright overlay.

Two pairs, light and dark, with the same shapes so they mix in one base:
  timberframe_*  Clean Stone   light warm concrete panels with subtle seams, running-bond floor slabs, slate roof
  dark_stone_*   Dark Stone    the same in warm charcoal, with connected grooved trim
  pearl_panel_*  Pearl Panel   cool white plates with cut corners, polished tiles, tinted glass, connected trim with a glowing strip
  onyx_panel_*   Onyx Panel    the same in graphite
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, MATERIALS, Canvas, write_anim, write_block, write_item, write_png  # noqa: E402
import machine_casing as casing  # noqa: E402

# Glass glints shared by every window; light diffusers use f (edge) g (body) h (centre), nothing else does.
CLEAN = {
    '0': '#F1EFEA', '1': '#E7E4DE', '2': '#DDDAD3', '3': '#D2CEC6', '4': '#BEB9AF', '5': '#A39E94', '6': '#8A857C',
    'r': '#A3A9AE', 'R': '#92989E', 's': '#787E85', 't': '#B9BEC2',  # slate roof
    'f': '#F7E2B2', 'g': '#FFEFCB', 'h': '#FFF9EA',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
# Dark Stone: Clean Stone's shapes in warm charcoal, with a darker slate roof.
DARK = {
    '0': '#8C8A86', '1': '#7E7C78', '2': '#716F6B', '3': '#676561', '4': '#53514E', '5': '#43423F', '6': '#33322F',
    'r': '#666C72', 'R': '#5B6167', 's': '#464B51', 't': '#7A8086',
    'f': '#F7E2B2', 'g': '#FFEFCB', 'h': '#FFF9EA',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
# Pearl Panel and Onyx Panel share every shape; a cyan accent strip glows on the pillars (a, A only).
PEARL = {
    '0': '#F6F8FA', '1': '#ECF0F3', '2': '#E1E6EA', '3': '#D3D9DE', '4': '#B9C1C8', '5': '#9AA4AC', '6': '#7E8891',
    'a': '#3FC9DC', 'A': '#A6F3FA',
    'f': '#D6F2FA', 'g': '#ECFAFE', 'h': '#FFFFFF',
    'v': '#F2FBFE', 'V': '#BFE6F2', 'w': '#BFE9F530',
}
ONYX = {
    '0': '#737B83', '1': '#636B72', '2': '#555C63', '3': '#4B5258', '4': '#3C4247', '5': '#2F3438', '6': '#25292C',
    'a': '#3FC9DC', 'A': '#A6F3FA',
    'f': '#D6F2FA', 'g': '#ECFAFE', 'h': '#FFFFFF',
    'v': '#F2FBFE', 'V': '#BFE6F2', 'w': '#BFE9F530',
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


# ---------------------------------------------------------------- Clean Stone and Dark Stone (ids timberframe_*, dark_stone_*)

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


# ---------------------------------------------------------------- Pearl Panel / Onyx Panel (one shape, two palettes)

def panel_wall(v):
    """Crisp plate: a lit top/left edge, a soft seam bottom/right and cut corners, so a wall reads as a grid of plates."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '2')
    speckle(c, '1', 53, 62 + v)
    c.rect(0, 0, 16, 1, '0').rect(0, 0, 1, 15, '1')
    c.rect(0, 15, 16, 1, '4').rect(15, 0, 1, 15, '3')
    for x, y, ch in ((0, 0, '3'), (15, 0, '3'), (0, 15, '4'), (15, 15, '5'), (1, 1, '1'), (14, 14, '3')):
        c.set(x, y, ch)
    if v == 2:  # four flush bolts
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            c.set(x, y, '4').set(x + 1, y + 1 if y < 13 else y, '2')
    if v == 3:  # a small vent: three slots
        for y in (6, 8, 10):
            c.rect(5, y, 6, 1, '5').rect(5, y + 1, 6, 1, '1')
    return c.rows()


def panel_floor(v):
    """Large polished tiles with hairline joints and a faint diagonal sheen."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '2')
    speckle(c, '1', 47, 72 + v)
    for y in range(16):
        for x in range(16):
            if (x + y) % 16 in (4, 5) and c.get(x, y) in '23':
                c.set(x, y, '1')
    c.rect(0, 0, 16, 1, '1').rect(0, 0, 1, 16, '1')
    c.rect(0, 15, 16, 1, '4').rect(15, 0, 1, 16, '4')
    if v == 2:  # quartered tile
        c.rect(1, 7, 14, 1, '4').rect(7, 1, 1, 14, '4').rect(1, 8, 14, 1, '1').rect(8, 1, 1, 14, '1')
    if v == 3:  # four flush bolts
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            c.set(x, y, '4')
    return c.rows()


def panel_roof(v):
    """Standing seam metal roof: 8 px panels with a raised seam."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    speckle(c, '35', 23, 82 + v)
    for x in range(0, 16, 8):
        c.rect(x, 0, 1, 16, '3').rect(x + 1, 0, 1, 16, '6')
    if v >= 2:
        y = 5 if v == 2 else 12
        c.rect(2, y, 6, 1, '5').rect(10, (y + 7) % 16, 6, 1, '5')
    return c.rows()


# ---------------------------------------------------------------- connected trim (every style but Clean Stone)

# Arms a trim face connects through, in texture space: top, right, bottom, left. The models rotate each shape, so
# don't light it from one side.
TRIM_SHAPES = {'none': '', 'end': 'T', 'straight': 'TB', 'corner': 'TR', 'tee': 'TRL', 'cross': 'TRBL'}
# Dark Stone: a plain groove. Pearl and Onyx: a recessed channel with a glowing strip (a).
GROOVE = ('4', '3')
STRIP = ('a', '5', '4')


def trim(arms, channel):
    """A trim face: body, a 2 px rim (dark edge, light line) on every side it does not connect through, rims that turn
    at inner corners, and a channel from the centre out through each connected side (a dot when nothing connects)."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '2')
    ends = {'T': (8, 0), 'R': (16, 8), 'B': (8, 16), 'L': (0, 8)}
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            d = max(abs(px - 8), abs(py - 8))  # Chebyshev distance to the centre: square joins
            for arm in arms:
                ex, ey = ends[arm]
                if ex == 8 and min(8, ey) <= py <= max(8, ey):
                    d = min(d, abs(px - 8))
                elif ey == 8 and min(8, ex) <= px <= max(8, ex):
                    d = min(d, abs(py - 8))
            k = int(d)
            if k < len(channel):
                c.set(x, y, channel[k])
    rim = {'T': lambda i: (i, 0), 'B': lambda i: (i, 15), 'L': lambda i: (0, i), 'R': lambda i: (15, i)}
    inner = {'T': lambda i: (i, 1), 'B': lambda i: (i, 14), 'L': lambda i: (1, i), 'R': lambda i: (14, i)}
    for side in 'TRBL':
        if side in arms:
            continue
        for i in range(16):
            c.set(*inner[side](i), '1')
        for i in range(16):
            c.set(*rim[side](i), '5')
    # an inner corner (both sides connected): the neighbours' rims meet here
    for a1, a2, (cx, cy, dx, dy) in (('T', 'R', (15, 0, -1, 1)), ('R', 'B', (15, 15, -1, -1)),
                                      ('B', 'L', (0, 15, 1, -1)), ('L', 'T', (0, 0, 1, 1))):
        if a1 in arms and a2 in arms:
            c.set(cx, cy, '5').set(cx + dx, cy, '5').set(cx, cy + dy, '5').set(cx + dx, cy + dy, '1')
    return c.rows()


def panel_window():
    """Thin frame, lightly tinted glass."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    window_frame(c, '1', '3', '5')
    glass(c)
    return c.rows()


def panel_light():
    c = Canvas()
    light_frame(c, '1', '5', '3', width=1)
    diffuser(c, 1, 14)
    return c.rows()


# ---------------------------------------------------------------- Architect Table

# A design station in the machine set's language (model: scripts/data/architect_models.py): a light steel tabletop
# with copper corner caps, graphite corner posts and plinth, and a body set 1 px back between the posts whose four
# sides are azure tinted glass with a white elevation drawing of a house. The tabletop holds a holo blueprint 1 px
# deep: a floor plan on a grid, full-bright, with a scan line sweeping across it (the table has no idle state).
# Identity colour: blueprint azure. Chars on top of the casing palette (abcde light steel, fghij copper):
#   12345  graphite (posts, plinth, bezel)   A..F  holo blueprint, deep -> white
#   G H    side glass, its glare             I J   drawing lines, bright / dim     M N  light strip on the tabletop edge

TABLE = casing.casing_palette(
    casing.INDUSTRY_TIERS[1], a='#4A535B', b='#6F7A83', c='#9AA5AE', d='#C4CDD4', e='#EBF0F3',
    **dict(zip('12345', casing.GRAPHITE)),
    A='#123A6E', B='#1D5296', C='#3A7CD0', D='#7DB6FF', E='#C4E0FF', F='#FFFFFF',
    G='#3C73B6', H='#6497D4', I='#D5E8FF', J='#8DB7E8', M='#4F8BD6', N='#A6CEFF')
HOLO_FRAMES, HOLO_FRAMETIME = 32, 2           # one sweep across the blueprint: 64 ticks


def table_side():
    """One side as seen: tabletop edge (rows 0-2), posts (cols 0-1, 14-15) and the glass body between them
    (rows 3-13), plinth (rows 14-15). The model maps each element onto its part of this picture."""
    c = Canvas()
    c.rect(0, 0, 16, 3, 'c').rect(0, 0, 16, 1, 'd').rect(0, 2, 16, 1, 'b')          # tabletop edge
    c.rect(4, 1, 8, 1, 'N').set(4, 1, 'M').set(11, 1, 'M')                          # azure light strip (full-bright overlay)
    for x0, s in ((0, 1), (15, -1)):                                                # copper corner caps
        c.set(x0, 0, 'K').set(x0 + s, 0, 'j').set(x0 + 2 * s, 0, 'i')
        c.set(x0, 1, 'h').set(x0 + s, 1, 'h').set(x0 + 2 * s, 1, 'g')
        c.set(x0, 2, 'g').set(x0 + s, 2, 'f').set(x0 + 2 * s, 2, 'f')
    c.rect(1, 3, 14, 11, 'c').bevel(2, 3, 12, 11, 'd', 'b')                         # body frame
    c.rect(3, 4, 10, 9, 'G')                                                        # tinted glass
    c.set(11, 4, 'H').set(12, 4, 'H').set(12, 5, 'H')                               # glare
    c.rect(3, 12, 10, 1, 'J')                                                       # ground line
    c.line(4, 8, 7, 5, 'I').line(8, 5, 11, 8, 'I').rect(4, 8, 8, 1, 'I')            # gable roof
    c.rect(4, 9, 1, 3, 'I').rect(11, 9, 1, 3, 'I')                                  # walls
    c.rect(7, 10, 2, 2, 'I')                                                        # door
    c.set(5, 10, 'J').set(10, 10, 'J').set(6, 10, 'J').set(9, 10, 'J')              # windows
    c.rect(7, 7, 2, 1, 'J')                                                         # attic window
    for x, hi, lo in ((0, '4', '2'), (14, '3', '2')):                               # graphite posts
        c.rect(x, 3, 1, 11, hi).rect(x + 1, 3, 1, 11, lo)
        c.rect(x, 3, 2, 1, 'g').rect(x, 13, 2, 1, 'f').set(x, 3, 'h')               # copper collars
    c.rect(0, 14, 16, 1, '3').rect(0, 15, 16, 1, '2')                               # plinth
    c.rect(0, 14, 2, 2, 'g').rect(14, 14, 2, 2, 'g').set(0, 14, 'h').set(14, 14, 'h')
    c.set(0, 15, 'K').set(15, 15, 'K')
    return c.rows()


def table_top():
    """Tabletop: the light steel casing frame round the blueprint opening (cols/rows 2-13 transparent)."""
    c = casing.shell(1)
    c.rect(2, 2, 12, 12, '.')
    return c.rows()


def table_bottom():
    """Underside; the model also uses it for the plinth top, the tabletop underside and the blueprint bezel."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '2').bevel(0, 0, 16, 16, '3', '1')
    c.rect(3, 3, 10, 10, '1').bevel(3, 3, 10, 10, '1', '3')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rect(x, y, 2, 2, 'f').set(x, y, 'h')
    return c.rows()


# Floor plan in the blueprint (x/y 4..11): outer walls with a doorway, an inner wall with a gap
PLAN_WALLS = ([(x, 4) for x in range(4, 12)] + [(x, 11) for x in range(4, 12) if x not in (7, 8)]
              + [(4, y) for y in range(4, 12)] + [(11, y) for y in range(4, 12)])
PLAN_INNER = [(8, y) for y in range(5, 11) if y not in (7, 8)]
PLAN_MARKS = [(2, 2), (3, 2), (2, 3), (13, 2), (12, 2), (13, 3), (2, 13), (3, 13), (2, 12), (13, 13), (12, 13), (13, 12)]


def table_holo(frame):
    """The blueprint (x/y 2..13): grid, floor plan and corner marks, brightened behind a scan line sweeping top to
    bottom (12 rows plus a 4 row pause, half a row per frame; the mcmeta interpolates between frames)."""
    ramp = 'ABCDEF'
    c = Canvas()
    c.rect(2, 2, 12, 12, 'A')
    for i in (4, 7, 10):                                                            # grid
        c.rect(2, i, 12, 1, 'B').rect(i, 2, 1, 12, 'B')
    c.rect(5, 5, 6, 6, 'B')                                                         # building footprint
    for x, y in PLAN_MARKS + PLAN_INNER:
        c.set(x, y, 'C' if (x, y) in PLAN_MARKS else 'D')
    for x, y in PLAN_WALLS:
        c.set(x, y, 'E')
    head = 2 + frame * 16 / HOLO_FRAMES                                             # scan line row, 2 .. 18
    for y in range(2, 14):
        d = head - y
        boost = 2 if 0 <= d < 1 else 1 if 1 <= d < 3 else 0
        for x in range(2, 14) if boost else ():
            c.set(x, y, ramp[min(5, ramp.index(c.get(x, y)) + boost)])
    return c


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


CLEAN_SHAPES = (clean_wall, clean_floor, clean_roof, clean_pillar_side, clean_pillar_end, clean_window, clean_light)
DARK_SHAPES = (clean_wall, clean_floor, clean_roof, None, None, clean_window, clean_light)
PANEL_SHAPES = (panel_wall, panel_floor, panel_roof, None, None, panel_window, panel_light)
# style -> (palette, shapes, connected trim channel or None for Clean Stone's own pillar)
STYLES = {
    'timberframe': (CLEAN, CLEAN_SHAPES, None),
    'dark_stone': (DARK, DARK_SHAPES, GROOVE),
    'pearl_panel': (PEARL, PANEL_SHAPES, STRIP),
    'onyx_panel': (ONYX, PANEL_SHAPES, STRIP),
}
VARIANTS = 3


def variant_name(base, v):
    return base if v == 1 else f'{base}_{v}'



# ---------- Pocket Architect ----------
# A hand-held blueprint tablet with an ender eye lens. Mk1: copper body, white plan lines. Mk2: steel body, cyan lines.

def pocket_palette(mk2):
    p = {'k': '#1E1A1A', 'B': '#16335F', 'b': '#24508F',
         'g': '#1E6B2E', 'G': '#3E9E3A', 'h': '#9BE07F', 'p': '#0E1A12'}
    p.update(zip('01234', MATERIALS['steel' if mk2 else 'copper']))
    p['L'] = '#7FF0FA' if mk2 else '#DDEEFF'
    p['r'] = MATERIALS['gold'][3] if mk2 else MATERIALS['copper'][3]
    return p


def pocket(mk2):
    c = Canvas()
    c.rect(2, 1, 12, 14, '2')
    c.auto_shade({'2': ('1', '3')})
    c.recess(3, 5, 10, 8, '01234', fill='B')
    plan = ['...LL...', '..LLLL..', '.LLLLLL.', '..L..L..', '..L.LL..', '..LLLL..']
    for dy, row in enumerate(plan):
        for dx, ch in enumerate(row):
            c.set(4 + dx, 6 + dy, ch if ch != '.' else ('b' if dx % 3 == 0 and dy % 3 == 0 else 'B'))
    # ender eye lens in a rim
    c.rect(5, 2, 6, 2, 'r')
    c.set(6, 2, 'g').set(7, 2, 'h').set(8, 2, 'G').set(9, 2, 'g')
    c.set(6, 3, 'p').set(7, 3, 'G').set(8, 3, 'g').set(9, 3, 'p')
    c.set(4, 13, 'L' if mk2 else '4').set(7, 13, '1').set(8, 13, '1').set(11, 13, 'r')
    if mk2:
        for y in range(5, 13):
            c.set(2, y, 'L')
    c.outline('k')
    return c.rows()

def main():
    for style, (pal, shapes, channel) in STYLES.items():
        wall, floor, roof, pside, pend, window, light = shapes
        for v in range(1, VARIANTS + 1):
            write_block(variant_name(f'{style}_wall', v), wall(v), pal)
            write_block(variant_name(f'{style}_floor', v), floor(v), pal)
            write_block(variant_name(f'{style}_roof', v), roof(v), pal)
        if channel is None:
            write_block(f'{style}_pillar', pside(), pal)
            write_block(f'{style}_pillar_top', pend(), pal)
        else:
            for shape, arms in TRIM_SHAPES.items():
                rows = trim(arms, channel)
                write_block(f'{style}_pillar_{shape}', rows, pal)
                if 'a' in channel:
                    write_block(f'{style}_pillar_{shape}_glow', glow_only(rows, 'a'), pal)
        write_block(f'{style}_window', window(), pal)
        rows = light()
        write_block(f'{style}_light', rows, pal)
        write_block(f'{style}_light_glow', glow_only(rows, 'fgh'), pal)
    write_block('architect_table_top', table_top(), TABLE)
    write_block('architect_table_side', table_side(), TABLE)
    write_block('architect_table_bottom', table_bottom(), TABLE)
    write_block('architect_table_side_glow', glow_only(table_side(), 'MN'), TABLE)
    write_anim('block', 'architect_table_holo', [table_holo(f) for f in range(HOLO_FRAMES)], TABLE,
               frametime=HOLO_FRAMETIME, interpolate=True)
    write_png(ASSETS / 'textures/entity/builder_drone.png', drone_texture(), DRONE, size=32)
    write_item('pocket_architect', pocket(False), pocket_palette(False))
    write_item('pocket_architect_mk2', pocket(True), pocket_palette(True))


if __name__ == '__main__':
    main()
