"""Automation textures: robot body atlases (Stumpy, Sprout), shared robot parts, Mk tier accents, Excavator,
Supply Crate and the Mk farm kits.
Run: python3 scripts/textures/automation.py

<robot>_body.png is a 32x32 atlas at the same texel density as a 16x16 texture (the models address it with uv = texel / 2):
body faces, head faces, battery pack and the eye. The small part textures (automation_*) keep an 8 pixel period so the
default uvs of tiny elements (arms, tools, hip) still land on plating.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, MATERIALS, Canvas, grain, hash01, material, write_anim, write_block, write_item, write_png  # noqa: E402
import machine_casing as casing  # noqa: E402

CU, BR, ST, DK, WD, GR, AC = '01234', '56789', 'abcde', 'fghij', 'uvwxy', 'lmnop', 'ABCDE'
BASE = {'k': '#16191B', 'V': '#0C1418', 'W': '#3A5560',
        'Y': '#7FEFF8', 'y': '#2FB8CC', 'z': '#F0FFFF', 'Z': '#16505A',
        'R': '#FF5A3A', 'r': '#7A1E14', 'T': '#FFB21E', 'H': '#2A2418'}
BASE.update(material(CU, 'copper'))
BASE.update(material(BR, 'brass'))
BASE.update(material(ST, 'steel'))
BASE.update(material(DK, 'dark'))
BASE.update(material(WD, 'wood'))
BASE.update(material(GR, 'green'))
BASE.update(grain('sS', 'steel'))
BASE.update(grain('tU', 'brass'))
BASE.update(grain('cC', 'copper'))
GRAINS = {CU: 'cC', BR: 'tU', ST: 'sS'}
TIERS = {1: 'copper', 2: 'iron', 3: 'gold', 4: 'teal'}


def pal(accent='copper', **extra):
    out = dict(BASE)
    out.update(material(AC, accent))
    out.update(extra)
    return out


def plate(c, x, y, w, h, r, seed=0, **kw):
    return c.plate(x, y, w, h, r, seed, grain=GRAINS.get(r), **kw)


# ---------------------------------------------------------------- robot atlas
# Region table (texels in the 32x32 atlas): name -> (u, v, w, h). Shared with scripts/data/automation_data.py.
ATLAS = {
    'body_front': (0, 0, 8, 6), 'body_back': (8, 0, 8, 6), 'body_side': (16, 0, 8, 6),
    'body_top': (24, 0, 8, 8), 'body_bottom': (24, 8, 8, 8),
    'head_front': (0, 6, 9, 6), 'head_back': (9, 6, 9, 6),
    'head_side': (0, 12, 7, 6), 'head_top': (7, 12, 9, 7), 'head_bottom': (16, 12, 9, 7),
    'pack_back': (0, 19, 6, 5), 'pack_side': (6, 19, 2, 5), 'pack_top': (8, 19, 6, 2),
    'eye': (14, 19, 2, 2),
    'tread_side': (0, 24, 10, 3), 'tread_top': (28, 16, 3, 10), 'tread_end': (13, 24, 3, 3),
}


def robot_atlas(kind):
    """kind 'stumpy' (copper lumberjack) or 'sprout' (brass gardener)."""
    r = CU if kind == 'stumpy' else BR
    c = Canvas(32)

    # body front: a riveted chest plate with a little status window
    u, v, w, h = ATLAS['body_front']
    plate(c, u, v, w, h, r, 1, density=0.25)
    if kind == 'stumpy':
        c.draw(u + 2, v + 1, ['.88.', '8776', '.65.'])                   # brass drive hub
        c.set(u + 1, v + 1, r[4]).set(u + 6, v + 1, r[4])
    else:
        c.draw(u + 2, v + 1, ['.mo.', 'mopm', '.mn.'])                   # leaf emblem
        c.set(u + 1, v + 1, r[4]).set(u + 6, v + 1, r[4])
    # body back (mostly behind the battery pack): vents
    u, v, w, h = ATLAS['body_back']
    plate(c, u, v, w, h, r, 2)
    c.vents(u + 1, v + 1, w - 2, 3, r, 2)
    # body sides: shoulder socket where the arm joins
    u, v, w, h = ATLAS['body_side']
    plate(c, u, v, w, h, r, 3)
    c.draw(u + 2, v + 0, ['.88.', '8776', '8765', '.65.'])
    c.set(u + 6, v + 4, r[4])
    # body top / bottom
    u, v, w, h = ATLAS['body_top']
    plate(c, u, v, w, h, r, 4)
    c.rivets(u, v, w, h, r)
    u, v, w, h = ATLAS['body_bottom']
    plate(c, u, v, w, h, DK, 5, brushed=False)

    # head front: big dark visor (the eyes are separate glowing elements in front of it) and cheek bolts
    u, v, w, h = ATLAS['head_front']
    plate(c, u, v, w, h, r, 6, density=0.15)
    c.rect(u + 1, v + 1, 7, 4, 'V').bevel(u + 1, v + 1, 7, 4, 'k', 'W')
    c.set(u + 2, v + 2, 'W')
    if kind == 'sprout':
        c.set(u + 1, v + 1, r[2]).set(u + 7, v + 1, r[2])                # rounder visor
        c.set(u + 1, v + 4, r[2]).set(u + 7, v + 4, r[2])
    c.set(u + 4, v + 5, r[1]).set(u + 3, v + 5, r[3])                    # chin seam
    # head back: two cooling slots and a serial plate
    u, v, w, h = ATLAS['head_back']
    plate(c, u, v, w, h, r, 7)
    c.vents(u + 2, v + 1, 5, 3, r, 2)
    c.rect(u + 3, v + 4, 3, 1, '6')
    # head side: round ear bolt with a highlight
    u, v, w, h = ATLAS['head_side']
    plate(c, u, v, w, h, r, 8)
    c.draw(u + 2, v + 1, ['.bb.', 'bdcb', 'bcca', '.aa.'])
    # head top: hatch seam and the antenna socket
    u, v, w, h = ATLAS['head_top']
    plate(c, u, v, w, h, r, 9)
    c.rect(u + 2, v + 1, 5, 1, r[1]).rect(u + 2, v + 1, 1, 5, r[1])
    c.rect(u + 3, v + 2, 4, 4, r[3]).rect(u + 3, v + 5, 4, 1, r[1])
    c.draw(u + 4, v + 3, ['76', '65'])
    u, v, w, h = ATLAS['head_bottom']
    plate(c, u, v, w, h, r, 10, brushed=False)

    # battery pack (back): dark case, copper terminals, a cyan charge strip
    u, v, w, h = ATLAS['pack_back']
    plate(c, u, v, w, h, DK, 11, brushed=False)
    c.draw(u + 1, v + 1, ['3..3'])                                       # terminals
    c.rect(u + 1, v + 3, 4, 1, 'Z').rect(u + 1, v + 3, 3, 1, 'y')
    u, v, w, h = ATLAS['pack_side']
    c.rect(u, v, w, h, 'g').rect(u, v, 1, h, 'h')
    u, v, w, h = ATLAS['pack_top']
    c.rect(u, v, w, h, 'h').rect(u, v, w, 1, 'i')

    # eye: glowing lens with a white catch light
    u, v, w, h = ATLAS['eye']
    c.draw(u, v, ['zY', 'Yy'])

    # treads: rubber track with lugs, road wheels on the sides
    u, v, w, h = ATLAS['tread_side']
    c.rect(u, v, w, h, 'g')
    c.rect(u, v, w, 1, 'h').rect(u, v + 2, w, 1, 'f')
    for x in range(u + 1, u + w - 1, 3):
        c.draw(x, v, ['ab', 'ba'])
    u, v, w, h = ATLAS['tread_top']
    for y in range(v, v + h):
        c.rect(u, y, w, 1, 'h' if (y - v) % 2 == 0 else 'f')
    u, v, w, h = ATLAS['tread_end']
    c.rect(u, v, w, h, 'g').rect(u, v, w, 1, 'h').set(u + 1, v + 1, 'b')
    return c


# ---------------------------------------------------------------- small part textures (8 px period)

def panels(r, rivets=True, slits=False, seed=0):
    """16x16 of four 8x8 plates: bevel light top/left, dark bottom/right, rivet top-left."""
    c = Canvas()
    for ox in (0, 8):
        for oy in (0, 8):
            plate(c, ox, oy, 8, 8, r, seed + ox + oy * 3, density=0.2)
            if rivets:
                c.rivet(ox + 2, oy + 2, r)
            if slits:
                c.vents(ox + 1, oy + 2, 6, 4, r, 2)
    return c


def wood():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for y in range(16):
        for x in range(16):
            n = (x * 5 + y * 13 + (x // 4) * 7) % 17
            if n < 2:
                c.set(x, y, 'v')
            elif n == 9:
                c.set(x, y, 'x')
    for x in range(0, 16, 4):
        c.rect(x, 0, 1, 16, 'v')
        c.rect(x + 1, 0, 1, 16, 'x')
    return c


def eye():
    """Kept for compatibility: the full glowing lens texture (the robots now use their atlas eye)."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'y').disc(7.5, 7.5, 6, 'Y').disc(5.5, 5.5, 2.2, 'z')
    return c


def saw():
    """Circular saw blade drawn at the 8x8 size it shows on the robot (uv 0-8), tiled 2x2: hooked teeth, polished
    ring and a brass hub, transparent between the teeth."""
    t = Canvas(8)
    teeth = 8

    def inside(x, y):
        dx, dy = x - 3.5, y - 3.5
        r = math.hypot(dx, dy)
        if r <= 2.9:
            return True
        ang = (math.degrees(math.atan2(dy, dx)) + 360) % (360 / teeth)
        return r <= 4.2 and ang < (360 / teeth) * 0.55

    t.shape(inside, lambda x, y: 'd' if x + y < 6 else 'c' if x + y < 9 else 'b')
    t.edge('a')
    t.draw(3, 3, ['97', '75'])
    t.set(2, 2, 'e').set(5, 5, 'a')
    c = Canvas()
    for ox in (0, 8):
        for oy in (0, 8):
            c.paste(t, ox, oy)
    return c


def leaf():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'n')
    for y in range(16):
        for x in range(16):
            k = (x * 3 + y * 5) % 7
            if k == 0:
                c.set(x, y, 'o')
            elif k == 4:
                c.set(x, y, 'm')
    for x, y in ((2, 2), (9, 1), (5, 6), (12, 7), (1, 10), (8, 11), (13, 13), (4, 14)):
        c.set(x, y, 'p')
    return c


def accent():
    """Tier accent: rows 0-5 the antenna bulb (glossy), rows 6-7 the belt with a buckle in the middle."""
    c = Canvas()
    c.rect(0, 0, 16, 6, 'C').rect(0, 0, 16, 1, 'D').rect(0, 5, 16, 1, 'B')
    c.draw(6, 1, ['DE', 'DD', 'CC'])
    c.draw(7, 2, ['E'])
    c.rect(0, 6, 16, 2, 'C').rect(0, 6, 16, 1, 'D').rect(0, 7, 16, 1, 'B')
    for x in range(1, 16, 3):
        c.set(x, 6, 'E')
    c.rect(7, 6, 2, 2, '8').set(7, 6, '9').set(8, 7, '5')               # buckle
    c.rect(0, 8, 16, 8, 'C')
    plate(c, 0, 8, 16, 8, AC, 3, brushed=False)
    return c


# ---------------------------------------------------------------- excavator
# A drill cage: four corner pillars on a base slab under a lintel slab, the motor on top; through the open bay on every
# side (2 px deep) the drill column shows: chuck, auger and bit (scripts/data/automation_data.py excavator_elements).
# Industrial grey body, tier metal brackets (casing.PROCESSING_TIERS: copper, gold, magma, null), amber work lights:
# a lamp bar under every lintel and a beacon in every top corner (dim standby amber while idle). Working: the auger
# turns (its flutes scroll round), the bit glows hot, the beacons sweep round the corners, cyan light runs up the
# pillars and round the motor cap. Every face samples its default uv (element position).

EX_GREY = ('#2A2C2F', '#43464A', '#62666B', '#8A8F95', '#B9BEC3')        # industrial grey body
EX_EXTRA = dict(zip('abcde', EX_GREY))
EX_EXTRA.update(zip('12345', MATERIALS['steel']))                          # drill steel
EX_EXTRA.update({'6': '#4A2E08', '7': '#8A5410', '8': '#B8761A',          # amber lens: dark, standby
                 'A': '#D88A12', 'B': '#FFB21E', 'C': '#FFE08A', 'D': '#FFFBE6',   # amber lit
                 'H': '#C2410C', 'I': '#FF8A1E'})                                  # hot bit
EX_FRAMES, EX_FRAMETIME = 16, 2                                            # every working loop: 32 ticks
EX_BEACONS = ((1, 1), (13, 1), (13, 13), (1, 13))                          # top corner lenses NW, NE, SE, SW
EX_AMBER = '678ABCD'                                                       # amber from dark to white-hot
EX_DRILL = (5, 6)                                                          # auger: first col, width (core face)
EX_SHADE = '234432'                                                        # auger cylinder shading per col


def ex_pal(tier=1):
    return casing.casing_palette(casing.PROCESSING_TIERS[tier], **EX_EXTRA)


def ex_frame(tier):
    """Outer face of every side (rows 2-15): the pillars (cols 0-2, 13-15) with an energy channel, the lintel (rows
    2-3) with the amber lamp bar under it, the base slab (rows 14-15); tier metal brackets on the corners. The bay
    (cols 3-12, rows 4-13) is empty space in the model."""
    c = Canvas()
    c.rect(0, 2, 16, 14, 'c')
    c.rect(0, 2, 16, 1, 'e').rect(0, 3, 16, 1, 'b')                        # lintel
    c.rect(4, 3, 8, 1, '7').set(4, 3, '6').set(11, 3, '6')                 # lamp bar
    c.rect(0, 14, 16, 1, 'd').rect(0, 15, 16, 1, 'a')                      # base
    for outer, mid, inner in ((0, 1, 2), (15, 14, 13)):                    # pillars
        c.rect(outer, 2, 1, 14, 'd' if outer == 0 else 'b')
        c.rect(inner, 4, 1, 10, 'b' if outer == 0 else 'd')
        c.rect(mid, 5, 1, 8, 'u').set(mid, 4, 'a').set(mid, 13, 'e')
    for x, sx in ((0, 1), (15, -1)):                                       # tier brackets, top and bottom
        for y, sy in ((2, 1), (15, -1)):
            for i in range(4):
                c.set(x + sx * i, y, 'h')
            c.set(x, y + sy, 'h').set(x, y + 2 * sy, 'g')
            c.set(x + sx * 3, y, 'g')
    c.set(0, 2, 'j').set(1, 2, 'i').set(15, 15, 'f')
    for i in range(tier):                                                  # Mk marks on the base
        c.set(6 + i * 2, 14, 'i')
    return c


def ex_frame_glow(frame=None):
    """Lamp bars: standby amber when idle (frame None); working they blaze and a pulse runs up the pillar
    channels."""
    c = Canvas()
    if frame is None:
        return c.rect(4, 3, 8, 1, '8').set(4, 3, '7').set(11, 3, '7')
    head = 12 - frame * 8 // EX_FRAMES
    for y in range(5, 13):
        d = (y - head) % 8
        ch = 'x' if d == 0 else 'w' if d in (1, 7) else 'v'
        c.set(1, y, ch).set(14, y, ch)
    return c.rect(4, 3, 8, 1, 'B').rect(5, 3, 6, 1, 'C').set(4, 3, 'A').set(11, 3, 'A')


def ex_wall():
    """Bay side walls (inner pillar faces, cols 0-1 and 14-15 show) and their rows."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    c.rect(0, 4, 16, 1, 'c')
    return c


def ex_floor():
    """Base slab top: a grating rim in each bay (rows/cols 0-1 and 14-15)."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    for i in range(0, 16, 2):
        c.set(i, 1, 'a').set(i, 14, 'a').set(1, i, 'a').set(14, i, 'a')
    c.rect(0, 0, 16, 1, 'd').rect(0, 15, 16, 1, 'd').rect(0, 0, 1, 16, 'd').rect(15, 0, 1, 16, 'd')
    return c


def ex_ceiling():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'a')
    return c


def ex_core(phase=None):
    """Drill column on the bay's back wall (cols 4-11, rows 4-13): mid grey wall, the chuck with a cyan collar, the
    turning auger (flutes scroll with phase) and the bit. phase None: at rest."""
    x0, w = EX_DRILL
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    for x, edge in ((3, 'd'), (12, 'b')):                                 # guide rails
        c.rect(x, 4, 1, 10, edge)
    c.rect(3, 13, 10, 1, 'b')
    c.rect(x0, 4, w, 1, 'a').rect(x0, 5, w, 1, 'u')                       # chuck, collar
    p = phase or 0
    for y in range(6, 12):
        for i in range(w):
            k = (i + y + p) % 4
            ch = '1' if k == 0 else EX_SHADE[i] if k != 1 else str(min(5, int(EX_SHADE[i]) + 1))
            c.set(x0 + i, y, ch)
    c.rect(x0 + 1, 12, 4, 1, '2').set(x0 + 2, 12, '4').set(x0 + 3, 12, '3')   # bit
    c.rect(x0 + 2, 13, 2, 1, '2').set(x0 + 2, 13, '3')
    return c


def ex_core_glow(frame):
    """Collar lit with a bright step running round it, the bit hot (pulsing gently)."""
    x0, w = EX_DRILL
    c = Canvas()
    for i in range(w):
        c.set(x0 + i, 5, 'w' if (i - frame // 2) % 3 == 0 else 'v')
    hot = 0.5 + 0.5 * math.sin(frame / EX_FRAMES * 2 * math.pi)
    c.rect(x0 + 1, 12, 4, 1, 'H').set(x0 + 2, 12, 'I').set(x0 + 3, 12, 'I')
    c.rect(x0 + 2, 13, 2, 1, 'B' if hot > 0.5 else 'I').set(x0 + 2, 13, 'C' if hot > 0.5 else 'B')
    return c


def ex_beacons(c, levels):
    """2x2 amber lenses in the top corners, each lit to its level (0 dark .. 4 white-hot)."""
    for (x, y), lv in zip(EX_BEACONS, levels):
        ramp = EX_AMBER[lv:lv + 3]
        c.rect(x, y, 2, 2, ramp[1]).set(x, y, ramp[2]).set(x + 1, y + 1, ramp[0])
    return c


def ex_beacon_levels(frame):
    """A rotating beam: each corner peaks in turn (NW, NE, SE, SW) and fades out behind it."""
    out = []
    for i in range(4):
        ph = (frame / EX_FRAMES - i / 4) % 1
        v = max(0.0, math.cos(ph * 2 * math.pi)) ** 2
        out.append(2 + round(v * 2))
    return out


def ex_top(tier):
    """Lintel top: a rim round the motor (3..12), the beacon lenses in tier metal corner plates."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(0, 0, 16, 16, 'e', 'a')
    c.rect(3, 3, 10, 10, 'a')
    for x, y in EX_BEACONS:
        c.rect(x - 1, y - 1, 4, 4, 'h').set(x - 1, y - 1, 'j' if (x, y) == (1, 1) else 'i')
    return ex_beacons(c, [0, 0, 0, 0])


def ex_top_glow(frame=None):
    """Beacons: standby amber when idle (frame None), a beam sweeping round the corners while working."""
    return ex_beacons(Canvas(), [1, 1, 1, 1] if frame is None else ex_beacon_levels(frame))


def ex_bottom():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b').bevel(0, 0, 16, 16, 'c', 'a')
    c.rect(5, 5, 6, 6, 'a').rect(6, 6, 4, 4, 'K')
    return c


def ex_dist(x, y):
    return math.hypot(x - 7.5, y - 7.5)


EX_RING = sorted(((x, y) for y in range(16) for x in range(16) if 3.0 <= ex_dist(x, y) < 3.9),
                 key=lambda p: math.atan2(p[1] - 7.5, p[0] - 7.5))


def ex_motor_top(tier):
    """Motor cap (cols/rows 3-12): a glass ring round a domed steel hub with a cross slot, tier metal corners."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(3, 3, 10, 10, 'e', 'b')
    for y in range(16):
        for x in range(16):
            d = ex_dist(x, y)
            lean = (x - 7.5) + (y - 7.5)
            if d < 2.2:
                c.set(x, y, '5' if lean < -1.5 else '2' if lean > 1.5 else '4')
            elif d < 3.0:
                c.set(x, y, '3' if lean < 0 else '2')
            elif d < 3.9:
                c.set(x, y, 'u')
    c.rect(7, 6, 2, 4, '1').rect(6, 7, 4, 2, '1').set(7, 7, '3')            # cross slot
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'h')
    return c


def ex_motor_glow(frame):
    """Four arcs of cyan chasing round the motor ring (one turn per loop)."""
    c = Canvas()
    arm = len(EX_RING) // 4
    step = frame * arm // EX_FRAMES
    for i, (x, y) in enumerate(EX_RING):
        behind = (step - i) % arm
        c.set(x, y, 'xwv'[behind] if behind < 3 else 'u')
    return c


def ex_motor_side():
    """Motor sides (rows 0-1): cooling ribs."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.rect(0, 0, 16, 1, 'e')
    for x in range(4, 12):
        c.set(x, 1, 'a' if x % 2 else 'd')
    return c


def write_excavator():
    p = ex_pal()
    for tier in (1, 2, 3, 4):
        tp = ex_pal(tier)
        write_block(f'excavator_mk{tier}_frame', ex_frame(tier).rows(), tp)
        write_block(f'excavator_mk{tier}_top', ex_top(tier).rows(), tp)
        write_block(f'excavator_mk{tier}_motor', ex_motor_top(tier).rows(), tp)
    for name, fn in (('bottom', ex_bottom), ('wall', ex_wall), ('floor', ex_floor), ('ceiling', ex_ceiling),
                     ('motor_side', ex_motor_side)):
        write_block(f'excavator_{name}', fn().rows(), p)
    write_block('excavator_core', ex_core().rows(), p)
    write_block('excavator_top_glow', ex_top_glow().rows(), p)
    write_block('excavator_frame_glow', ex_frame_glow().rows(), p)
    frames = range(EX_FRAMES)
    anim = {'excavator_core_on': (ex_core, False), 'excavator_core_glow': (ex_core_glow, True),
            'excavator_frame_glow_on': (ex_frame_glow, False), 'excavator_top_glow_on': (ex_top_glow, True),
            'excavator_motor_glow': (ex_motor_glow, True)}
    for name, (fn, smooth) in anim.items():
        arg = (lambda i: -i) if fn is ex_core else (lambda i: i)
        write_anim('block', name, [fn(arg(i)) for i in frames], p, frametime=EX_FRAMETIME, interpolate=smooth)


# Supply Crate Mk1-Mk4: a wooden crate (plank boards inside a frame of edge boards, a diagonal brace) shod in the tier
# metal, with a latch whose cyan lock light and Mk pips stay lit at night (copied onto a full-bright *_glow overlay).
# More Mk, more metal: corner caps on every Mk, straps along the vertical edges from Mk3, a full metal edge on Mk4.
CRATE_CYAN = {'q': '#2FB8CC', 'Q': '#8CF4FF'}
# Darker, cooler wood than oak, so every tier metal (copper included) stands out on it.
CRATE_WOOD = dict(zip(WD, ('#24180F', '#3E2B1C', '#5C412B', '#7C5B3C', '#A07C55')))


def crate_pal(tier=1):
    return pal(TIERS[tier], **CRATE_CYAN, **CRATE_WOOD)


def glow_only(c, keep='qQY'):
    out = Canvas()
    for y in range(16):
        for x in range(16):
            if c.get(x, y) in keep:
                out.set(x, y, c.get(x, y))
    return out


def crate_planks(c, vertical=False, seed=0):
    """Boards 4 px wide: lit edge, plank, a little grain, dark joint."""
    for a in range(16):
        for b in range(16):
            x, y = (b, a) if not vertical else (a, b)
            k = a % 4
            ch = 'x' if k == 0 else 'u' if k == 3 else 'w'
            if ch == 'w' and hash01(b + (a // 4) * 5, a // 4, seed) < 0.12:
                ch = 'v'
            c.set(x, y, ch)
    return c


def crate_frame(c, tier):
    """Edge boards around the face (raised: lit outer edge, shadow inside), then the tier metal on top."""
    c.frame(0, 0, 16, 16, 'w').frame(1, 1, 14, 14, 'w')
    c.rect(0, 0, 16, 1, 'x').rect(0, 0, 1, 16, 'x')                      # lit outer edge
    c.rect(1, 15, 15, 1, 'v').rect(15, 1, 1, 15, 'v')
    c.frame(2, 2, 12, 12, 'u')                                           # shadow inside the frame
    for i in (5, 10):                                                    # nails along the edge boards
        c.set(i, 1, 'v').set(1, i, 'v').set(i, 14, 'v').set(14, i, 'v')
    if tier >= 3:                                                        # straps down the vertical edges
        c.rect(0, 3, 2, 10, 'C').rect(14, 3, 2, 10, 'C').rect(0, 3, 1, 10, 'D').rect(15, 3, 1, 10, 'B')
    if tier >= 4:                                                        # and along the top and bottom
        c.rect(3, 0, 10, 2, 'C').rect(3, 14, 10, 2, 'C').rect(3, 0, 10, 1, 'D').rect(3, 15, 10, 1, 'B')
    for x, y, sx, sy in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for i in range(4):                                               # 2 px L corner cap
            for j in range(2):
                c.set(x + sx * i, y + sy * j, 'C').set(x + sx * j, y + sy * i, 'C')
        c.set(x, y, 'D').set(x + sx, y + sy, 'E')                       # rivet
        if tier < 4:
            c.set(x + sx * 3, y + sy, 'A')
        if tier < 3:
            c.set(x + sx, y + sy * 3, 'A')
    return c


def crate_brace(c):
    """Diagonal board from the bottom-left corner to the top-right one, over the planks."""
    for i in range(10):
        x, y = 3 + i, 12 - i
        c.set(x, y, 'y').set(x, y + 1 if y < 12 else y, 'x')
        if x + 1 <= 12:
            c.set(x + 1, y, 'x')
        if y + 2 <= 12:
            c.set(x, y + 2, 'u')
    return c


def crate_front(tier):
    """Front: boards, a tier metal latch with a cyan lock light and a plate with one lit pip per Mk."""
    c = crate_frame(crate_planks(Canvas(), seed=1), tier)
    c.rect(6, 2, 4, 6, 'C').bevel(6, 2, 4, 6, 'D', 'A')                 # latch hasp
    c.rect(7, 3, 2, 1, 'B')
    c.set(7, 5, 'Y').set(8, 5, 'q')                                      # lock light
    c.rect(7, 6, 2, 1, 'B')
    c.rect(3, 10, 10, 3, 'C').bevel(3, 10, 10, 3, 'D', 'A')             # Mk plate: a 4 segment bar, one lit per Mk
    for i in range(4):
        lit = i < tier
        c.set(4 + i * 2, 11, 'Y' if lit else 'Z').set(5 + i * 2, 11, 'q' if lit else 'k')
    return c


def crate_side(tier):
    """Side: boards with the diagonal brace."""
    return crate_frame(crate_brace(crate_planks(Canvas(), seed=2)), tier)


def crate_top(tier):
    """Lid: boards running the other way and a tier metal carry handle across the middle."""
    c = crate_frame(crate_planks(Canvas(), vertical=True, seed=3), tier)
    c.rect(4, 7, 8, 2, 'C').rect(4, 7, 8, 1, 'D').set(4, 8, 'A').set(11, 8, 'A')
    c.rect(3, 7, 1, 2, 'B').rect(12, 7, 1, 2, 'B')                       # handle brackets
    return c


def crate_bottom():
    return crate_frame(crate_planks(Canvas(), seed=4), 1)


def kit(tier):
    """Mk kit: a tier coloured toolbox, latch, handle and one cyan pip per tier above 1."""
    c = Canvas()
    c.rect(1, 6, 14, 8, 'C')                                             # box
    c.rect(1, 6, 14, 1, 'D').rect(1, 13, 14, 1, 'B').rect(1, 6, 1, 8, 'D').rect(14, 6, 1, 8, 'B')
    c.rect(1, 4, 14, 2, 'B').rect(1, 4, 14, 1, 'E')                      # lid
    c.rect(5, 1, 6, 1, 'b').rect(5, 2, 1, 2, 'b').rect(10, 2, 1, 2, 'b').set(5, 1, 'd')   # handle
    c.rect(1, 8, 14, 1, 'A')                                             # lid seam
    c.draw(7, 7, ['87', '65'])                                           # latch
    for i in range(tier - 1):
        c.set(3 + i * 2, 11, 'Y')
    c.draw(11, 10, ['.o', 'on'])                                         # little sprout decal
    c.outline('k')
    return c


def main():
    for kind in ('stumpy', 'sprout'):
        write_png(ASSETS / 'textures/block' / f'{kind}_body.png', robot_atlas(kind).rows(), pal(), size=32)
    write_block('automation_copper', panels(CU).rows(), pal())
    write_block('automation_brass', panels(BR, seed=4).rows(), pal())
    write_block('automation_steel', panels(ST, seed=8).rows(), pal())
    write_block('automation_dark', panels(DK, rivets=False, slits=True).rows(), pal())
    write_block('automation_wood', wood().rows(), pal())
    for tier, mat in TIERS.items():
        write_block(f'automation_mk{tier}', accent().rows(), pal(mat))
    write_block('automation_eye', eye().rows(), pal())
    write_block('automation_saw', saw().rows(), pal())
    write_block('automation_leaf', leaf().rows(), pal())
    write_excavator()
    for tier, mat in TIERS.items():
        name = 'supply_crate' if tier == 1 else f'supply_crate_mk{tier}'
        for face, draw in (('front', crate_front), ('side', crate_side), ('top', crate_top)):
            c = draw(tier)
            write_block(f'{name}_{face}', c.rows(), crate_pal(tier))
            if face == 'front':
                write_block(f'{name}_front_glow', glow_only(c).rows(), crate_pal(tier))
    write_block('supply_crate_bottom', crate_bottom().rows(), crate_pal())
    for tier in (2, 3, 4):
        write_item(f'farm_kit_mk{tier}', kit(tier).rows(), pal(TIERS[tier]))


if __name__ == '__main__':
    main()
