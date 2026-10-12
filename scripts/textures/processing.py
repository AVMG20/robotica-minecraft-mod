"""Processing module textures: Grinder and Electric Furnace (Mk1-Mk4), dusts, grinding balls.
Run: python3 scripts/textures/processing.py   (writes textures; models are written by scripts/data/processing_models.py)

Machines use the shared casing (machine_casing.py), framed in the tier's metal: Mk1 copper, Mk2 gold, Mk3 magma,
Mk4 null. Grinder: a crusher bay cut into the front, two counter-rotating gears with cyan teeth crushing ore onto a
glowing heap, a ribbed feed hopper on top. Electric Furnace: an induction chamber with a billet levitating over a cyan
plate, white-hot with heat radiating while working; glowing exhaust louvres on top.
Items use the shared item style (dark outline, auto-shaded from the top-left).
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, grain, material, write_anim, write_block, write_item  # noqa: E402
import machine_casing as casing  # noqa: E402

ST = 'abcde'
TIER = {1: '01234', 2: 'ABCDE', 3: '!#$%&', 4: '()*+,'}
P = {'k': '#16191B', 'K': '#0B0D0E'}
P.update(material(ST, 'steel'))
P.update(material(TIER[1], 'copper'))
P.update(material(TIER[2], 'gold'))
P.update(material(TIER[3], 'magma'))
P.update(material(TIER[4], 'null'))
P.update(material('56789', 'dark'))
P.update(grain('sS', 'steel', 0.22))
P.update({
    'f': '#FF7A1A', 'F': '#FFC23A', 'O': '#FFF3C0', 'x': '#B8261A', 'X': '#5A1408',    # heat, sparks
    'g': '#1C4A22', 'G': '#5CFF6A', 'r': '#5A1A14', 'R': '#FF4A3A',                    # LEDs off/on
    'h': '#E8B530', 'H': '#2A2418',                                                    # hazard stripes
})


def plate(c, x, y, w, h, r, seed=0, **kw):
    return c.plate(x, y, w, h, r, seed, grain='sS' if r == ST else None, **kw)


def frame(tier, seed=0, panel=ST):
    """Tier coloured frame (2 px) with riveted corner brackets around a recessed, brushed steel panel."""
    f = TIER[tier]
    c = Canvas()
    c.plate(0, 0, 16, 16, f, seed, brushed=False)
    c.rect(1, 1, 14, 14, f[2])
    c.rect(1, 14, 14, 1, f[1]).rect(14, 1, 1, 14, f[1])
    plate(c, 2, 2, 12, 12, panel, seed + 3, bevel=False, density=0.3)
    c.rect(2, 2, 12, 1, panel[1]).rect(2, 2, 1, 12, panel[1])
    c.rect(3, 13, 11, 1, panel[3]).rect(13, 3, 1, 11, panel[3])
    c.set(2, 2, panel[0])
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):
        c.rect(x, y, 3, 3, f[3])
        c.rect(x + 1, y + 2, 2, 1, f[1]).rect(x + 2, y + 1, 1, 2, f[1])
        c.set(x + 1, y + 1, f[4])
    c.set(0, 0, f[4]).set(15, 15, f[0])
    return c


def led(c, x, y, on):
    c.set(x, y, 'G' if on else 'g')


# ---------------------------------------------------------------- grinder
# The front is the casing with a crusher bay cut 2 px deep into it (model: processing_models.grinder_model). The bay
# texture holds the back wall in 2..13 x 3..12 and the bay's ceiling (rows 0-1), floor (rows 14-15) and side walls
# (cols 0-1, 14-15) around it. Two toothed crusher gears counter-rotate into the bite; working, their teeth glow cyan,
# the crushed ore drops as hot grains onto a glowing heap on the energy tray.

GRINDER_FRAMES = 24                                                       # bay animation loop, 1 tick per frame
G_EXTRA = {
    '1': '#3A1006', '2': '#9A2E0C', '3': '#FF7A1A', '4': '#FFC23A', '5': '#FFF3C0',     # crushed ore heat
    '6': '#7E8A94', '7': '#C2CDD5', '8': '#4E4844', '9': '#7A706A',                     # gear teeth, ore
}
# Rim of a 6x6 gear, clockwise from the top left; the left gear sits at 2..7 x 4..9, the right one mirrors it
RIM = ((1, 0), (2, 0), (3, 0), (4, 0), (5, 1), (5, 2), (5, 3), (5, 4),
       (4, 5), (3, 5), (2, 5), (1, 5), (0, 4), (0, 3), (0, 2), (0, 1))
GEAR_X, GEAR_Y = 2, 4


def grinder_palette(tier=1):
    return casing.casing_palette(casing.PROCESSING_TIERS[tier], **G_EXTRA)


def mirrored(half):
    """The left half drawn on a clear canvas plus its mirror image: the right gear turns the other way."""
    c = Canvas()
    c.paste(half)
    return c.paste(half.flip_h())


def gear(step):
    """Left gear turning clockwise (one rim pixel a tick): four teeth on a steel rim around a dark face and axle."""
    x0, y0 = GEAR_X, GEAR_Y
    c = Canvas()
    c.rect(x0 + 1, y0 + 1, 4, 4, 'c').rect(x0 + 1, y0 + 1, 3, 1, 'd').rect(x0 + 1, y0 + 1, 1, 3, 'd')
    c.rect(x0 + 2, y0 + 2, 2, 2, '6').set(x0 + 2, y0 + 2, '7').set(x0 + 3, y0 + 3, 'b')
    for i, (x, y) in enumerate(RIM):
        k = (i - step) % 4
        c.set(x0 + x, y0 + y, '7' if k == 0 else '6' if k in (1, 3) else 'a')
    return c


def grinder_bay(frame, lit):
    """Back wall and bay walls; frame counts ticks (idle draws the gears at rest)."""
    c = Canvas()
    c.rect(2, 3, 12, 10, 'k')
    c.rect(2, 10, 12, 3, 'a').rect(2, 10, 12, 1, 'K')                    # discharge pit
    c.rect(3, 12, 10, 1, 'u')                                             # energy tray
    c.rect(5, 11, 6, 1, '8').rect(4, 12, 8, 1, '8').rect(6, 11, 4, 1, '9')   # crushed heap
    c.paste(mirrored(gear(frame if lit else 0)))
    c.rect(7, 3 + (frame % 6 // 3 if lit else 1), 2, 1, '9').set(8, 3 + (frame % 6 // 3 if lit else 1), '8')   # ore feeding in
    c.rect(2, 0, 12, 2, 'a').rect(2, 1, 12, 1, 'b')                      # ceiling (row 0 at the back)
    c.rect(2, 14, 12, 2, 'b').rect(2, 14, 12, 1, 'c')                    # floor (row 14 at the front)
    c.rect(5, 15, 6, 1, '8').rect(6, 15, 4, 1, '9').rect(6, 14, 4, 1, '8')
    c.rect(0, 3, 2, 10, 'b').rect(0, 3, 1, 10, 'c')                      # side walls (col 0 and 15 at the front)
    c.rect(14, 3, 2, 10, 'a').rect(15, 3, 1, 10, 'b')
    return c


def grinder_glow(frame):
    """Cyan teeth, hot grains dropping out of the bite, the glowing heap and the tray's energy flowing in under it."""
    c = Canvas()
    teeth = Canvas()
    for i, (x, y) in enumerate(RIM):
        if (i - frame) % 4 == 0:
            teeth.set(GEAR_X + x, GEAR_Y + y, 'w')
    c.paste(mirrored(teeth))
    c.rect(7, 9, 2, 1, '2')                                               # bite
    if frame % 3 == 0:                                                    # grains dropping onto the heap
        c.set(7, 10, '4')
    if frame % 3 == 1:
        c.set(8, 10, '3')
    tray = Canvas()
    for x in range(3, 8):                                                 # energy flowing in from both ends
        tray.set(x, 12, 'w' if (x - frame // 3) % 4 == 0 else 'v')
    c.paste(mirrored(tray))
    c.rect(6, 11, 4, 1, '2').rect(7, 11, 2, 1, '3').set(7 + frame // 12, 11, '4')       # heap, glowing where grains land
    c.rect(5, 12, 6, 1, '2').rect(6, 12, 4, 1, '3')
    return c


def grinder_front(tier, lit):
    """Casing face with the crusher bay cut out of it and the status lamp."""
    c = casing.shell(tier)
    c.rect(2, 3, 12, 10, '.')
    c.rect(7, 1, 2, 1, 'L' if lit else 'l')
    return casing.tier_notches(c, tier)


def grinder_front_glow():
    return Canvas().rect(7, 1, 2, 1, 'L')


def grinder_top_glow():
    """The hopper's feed lights."""
    return Canvas().rect(3, 6, 1, 4, 'v').rect(12, 6, 1, 4, 'v')


def grinder_top(tier):
    """Feed hopper: a stepped funnel down to a slot over the bite, the gear tops ribbed below it."""
    c = casing.shell(tier)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.rect(4, 4, 8, 8, 'a').bevel(4, 4, 8, 8, 'k', 'c')
    c.rect(5, 4, 6, 8, 'K')
    for y in range(5, 11):
        rib = y % 2 == 0
        c.rect(5, y, 3, 1, '6' if rib else 'c').rect(8, y, 3, 1, '6' if rib else 'c')
        c.set(5, y, 'b').set(10, y, 'b')
        c.set(7, y, 'd' if rib else 'b').set(8, y, 'd' if rib else 'b')
    c.rect(5, 4, 6, 1, 'K').rect(5, 11, 6, 1, 'a')
    c.rect(3, 6, 1, 4, 'u').rect(12, 6, 1, 4, 'u')                       # feed lights
    casing.tier_notches(c, tier)
    return c


# ---------------------------------------------------------------- electric furnace
# An induction kiln sunk 2 px into the casing: a white ceramic lining with a red heating coil snaking across the back,
# and a steel billet (a real model part) levitating over a cyan induction plate. Working, the lining burns, the coil
# glows and the billet turns white-hot with arcs of heat radiating off it. One chamber texture shared by every tier
# holds the back wall, the bay walls and the billet faces (FURNACE_PARTS); the working one is animated and
# full-bright. Below the bay a steel strip with a red heat gauge. Top: a light steel exhaust hood whose louvres show
# the red coil, glowing while working. Red is the furnace's identity colour.

FURNACE_HEAT = {
    'm': '#24100C', 'n': '#5E1A0C', 'o': '#B83A0E', 'p': '#F2771A', 'q': '#FFC04A', 'y': '#FFF4C8',  # ember -> white
    '1': '#746C62', '2': '#A0988C', '3': '#C8C1B4', '4': '#E6E0D4',                                  # ceramic lining
    '5': '#5A1610', '6': '#AE2E1E', '7': '#E04A32',                                                  # cold red coil
    'A': '#3E474F', 'B': '#5C6770', 'C': '#808B94', 'D': '#A6B0B8', 'E': '#CDD5DB',                  # light steel
    'z': '#6A1E16', 'Z': '#963222',                                                                  # red gauge glass
}
STEEL = 'ABCDE'
FURNACE_BAY = (3, 3, 10, 8)                     # x, y, w, h of the chamber opening on the front
FURNACE_FRAMES, FURNACE_FRAMETIME = 16, 2
FURNACE_COIL_ROWS = (4, 6, 8)                   # back wall rows the coil runs along, turning at the ends
HEAT_RANK = 'mnopqy'
# chamber texture regions (x, y, w, h) besides the back wall; scripts/data/processing_models.py maps faces onto them
FURNACE_PARTS = {'wall_left': (0, 3, 2, 8), 'wall_right': (14, 3, 2, 8), 'floor': (3, 0, 10, 2),
                 'roof': (3, 14, 10, 2), 'billet_front': (3, 11, 6, 2), 'billet_top': (3, 13, 6, 1),
                 'billet_end': (10, 11, 1, 2)}


def furnace_pal(tier=1):
    return casing.casing_palette(casing.PROCESSING_TIERS[tier], **FURNACE_HEAT)


def furnace_coil():
    """(x, y) of the coil on the back wall: three runs joined at alternating ends."""
    pts = [(x, y) for y in FURNACE_COIL_ROWS for x in range(4, 12)]
    return pts + [(11, 5), (4, 7)]


def heat_haze(c, t):
    """Chamber air: a steady glow round the billet, two arcs of heat radiating out of it and fading as they grow; the
    coil glows through it."""
    x0, y0, w, _ = FURNACE_BAY
    coil = set(furnace_coil())
    for y in range(y0, 10):
        for x in range(x0, x0 + w):
            d = math.hypot((x - 7.5) / 1.5, (y - 9.0))
            ch = 'p' if d < 1.7 else 'o' if d < 2.5 else 'n' if d < 3.1 else 'm'
            if (x, y) in coil and HEAT_RANK.index(ch) < 2:
                ch = 'o' if (x + y) % 2 else 'n'
            for k in range(2):
                r = 3.1 + ((t / FURNACE_FRAMES + k / 2) % 1) * 3.8
                if abs(d - r) < 0.45:
                    ch = 'p' if r < 4.0 else 'o' if r < 5.6 else 'n'
            c.set(x, y, ch)


def furnace_chamber(lit, t=0):
    """Back wall in the bay area, the other faces in FURNACE_PARTS."""
    x0, y0, w, h = FURNACE_BAY
    R = FURNACE_PARTS
    c = Canvas()
    if lit:
        heat_haze(c, t)
    else:                                                                 # ceramic lining, the coil across it
        c.rect(x0, y0, w, 7, '3').rect(x0, y0, w, 1, '2').rect(x0, y0, 1, 7, '2').rect(x0 + w - 1, y0, 1, 7, '4')
        for x, y in furnace_coil():
            c.set(x, y, '7' if (x + y) % 2 else '6')
            if c.get(x, y + 1) in '34':
                c.set(x, y + 1, '2')                                      # its shadow on the lining
    # induction plate: its back edge on the wall, its top on the floor; two pulses run out from the middle
    x, y, fw, _ = R['floor']
    c.rect(x0, 10, w, 1, 'C').rect(x, y, fw, 1, 'n' if lit else '3')
    head = t * 8 // FURNACE_FRAMES
    for i in range(w):
        d = (abs(i - 4.5) - 0.5 - head) % 4
        ch = ('x' if d == 0 else 'w' if d == 1 else 'v') if lit else 'u'
        if 0 < i < w - 1:
            c.set(x0 + i, 10, ch)
        c.set(x + i, y + 1, ch)
    # billet
    x, y, bw, _ = R['billet_front']
    tx, ty, _, _ = R['billet_top']
    ex, ey, _, _ = R['billet_end']
    if lit:
        c.rect(x, y, bw, 1, 'q').rect(x + 2, y, 2, 1, 'y').rect(x, y + 1, bw, 1, 'p').rect(x + 1, y + 1, 4, 1, 'q')
        c.set(x, y + 1, 'o').set(x + bw - 1, y + 1, 'o')
        glint = round(2.5 + 2.5 * math.sin(t / FURNACE_FRAMES * 2 * math.pi))
        c.rect(tx, ty, bw, 1, 'q').rect(tx + 1, ty, 4, 1, 'y').set(tx + glint, ty, 'x')
        c.set(ex, ey, 'p').set(ex, ey + 1, 'o')
    else:                                                                 # cold steel
        c.rect(x, y, bw, 1, 'D').rect(x, y + 1, bw, 1, 'B').set(x, y, 'E').set(x + bw - 1, y + 1, 'A')
        c.rect(tx, ty, bw, 1, 'E').set(tx + bw - 1, ty, 'D')
        c.set(ex, ey, 'C').set(ex, ey + 1, 'A')
    # bay walls: lining with the coil's feed running up the inside, the plate's edge at the bottom
    for name in ('wall_left', 'wall_right'):
        x, y, ww, wh = R[name]
        front = x if name == 'wall_left' else x + 1                       # column at the bay mouth
        c.rect(x, y, ww, wh, 'n' if lit else '2')
        c.rect(front, y, 1, wh, 'm' if lit else '3')
        c.rect(x + 1 - (front - x), y + 1, 1, wh - 3, 'o' if lit else '6')
        c.rect(x, y + wh - 1, ww, 1, 'v' if lit else 'u')
    x, y, ww, _ = R['roof']
    c.rect(x, y, ww, 2, 'n' if lit else '2').rect(x, y + 1, ww, 1, 'm' if lit else '1')
    return c


def furnace_front(tier, lit):
    """The chamber opening on a tier metal sill, a steel strip below it with the heat gauge and the status lamp."""
    x0, y0, w, h = FURNACE_BAY
    c = casing.shell(tier)
    c.rect(x0, y0, w, h, '.')
    c.rect(x0, y0 + h, w, 1, 'h').set(x0, y0 + h, 'i').set(x0 + w - 1, y0 + h, 'g')   # tier metal sill
    c.rect(3, 12, 10, 1, 'C').set(3, 12, 'D').set(11, 12, 'B')                # steel strip
    c.rect(4, 12, 6, 1, 'z').set(6, 12, 'Z').set(7, 12, 'Z')                  # heat gauge, red glass
    c.set(12, 12, 'L' if lit else 'l')
    return c


def furnace_glow(t):
    """Front overlay: the heat gauge with a glint running along it, the status lamp."""
    c = Canvas()
    head = 4 + t * 12 // FURNACE_FRAMES
    for x in range(4, 10):
        c.set(x, 12, 'y' if x == head else 'q' if abs(x - head) == 1 else 'p')
    c.set(12, 12, 'L')
    return c


def furnace_top(tier):
    """Exhaust hood: a light steel plate with a recessed bank of louvres, the red coil showing through them."""
    c = casing.shell(tier)
    c.rect(3, 3, 10, 10, 'C').bevel(3, 3, 10, 10, 'E', 'A')
    c.rect(4, 4, 8, 7, 'B').bevel(4, 4, 8, 7, 'A', 'D')
    for y in (5, 7, 9):
        c.rect(5, y, 6, 1, '6').rect(5, y + 1, 6, 1, 'D')
        c.set(5, y, '5').set(10, y, '5').set(7, y, '7').set(8, y, '7')
    c.rect(5, 11, 6, 1, 'B').set(5, 11, 'A').set(10, 11, 'D')                 # hood lip
    casing.tier_notches(c, tier)
    return c


def furnace_top_glow(t):
    """Heat glowing through the louvres, a brighter wave crossing them."""
    c = Canvas()
    ph = t / FURNACE_FRAMES * 2 * math.pi
    for i, y in enumerate((5, 7, 9)):
        for x in range(5, 11):
            v = 0.5 + 0.5 * math.sin(x * 0.8 - ph + i * 1.1)
            c.set(x, y, 'q' if v > 0.85 else 'p' if v > 0.4 else 'o')
    return c


# ---------------------------------------------------------------- items

DUST_COLORS = {
    'iron': ('#6E6862', '#A8A09A', '#D8D0C8', '#F4EEE8'),
    'gold': ('#8A5A08', '#D89A1A', '#F6C93C', '#FFF0A0'),
    'copper': ('#6A3214', '#B4602A', '#E08A4C', '#FFC89A'),
}


def dust(name):
    """A soft heap of powder with a few loose grains."""
    lo, mid, hi, glint = DUST_COLORS[name]
    pal = {'k': '#2A2420', '1': lo, '2': mid, '3': hi, '4': glint}
    c = Canvas()

    def inside(x, y):
        h = 5.5 + (abs(x - 7.5) / 6.5) ** 1.8 * 7.0
        return 2 <= x <= 13 and h <= y <= 13
    c.shape(inside, lambda x, y: '2')
    c.speckle(2, 4, 12, 10, '13', 0.22, seed=sum(map(ord, name)), only='2')
    c.auto_shade({'2': ('1', '3')})
    c.set(6, 7, '4').set(7, 6, '3')
    c.outline('k')
    for x, y in ((2, 14), (14, 13), (12, 14)):
        c.set(x, y, '2')
    return c.rows(), pal


BALL_MATERIAL = {
    'iron': 'iron',
    'ferrothorium': None,       # olive-green steel, own ramp below
    'pyrosteel': 'magma',
    'resonant': 'teal',
}
FERRO = ('#2C3326', '#4E5A40', '#7D8B6A', '#B2C29A', '#E6F2D2')


def balls(name):
    """Three polished balls in a heap, each with a highlight on its upper left."""
    ramp = FERRO if BALL_MATERIAL[name] is None else MATERIALS[BALL_MATERIAL[name]]
    pal = {'k': '#1A1818'}
    pal.update({str(i + 1): col for i, col in enumerate(ramp)})
    c = Canvas()
    for cx, cy, r in ((5.0, 10.0, 3.6), (11.0, 10.0, 3.6), (8.0, 5.0, 3.6)):
        def inside(x, y, cx=cx, cy=cy, r=r):
            return math.hypot(x - cx, y - cy) <= r

        def fill(x, y, cx=cx, cy=cy, r=r):
            d = math.hypot(x - cx + 1.2, y - cy + 1.2)
            return '5' if d < 1.0 else '4' if d < 2.0 else '3' if d < 3.3 else '2'
        c.shape(inside, fill)
        c.ring(cx, cy, r - 0.5, r + 0.1, '1')
    c.outline('k')
    return c.rows(), pal


def write_grinder():
    from pixelart import write_still
    pal = grinder_palette()
    for tier in (1, 2, 3, 4):
        tp = grinder_palette(tier)
        write_block(f'grinder_mk{tier}_front', grinder_front(tier, False).rows(), tp)
        write_block(f'grinder_mk{tier}_front_on', grinder_front(tier, True).rows(), tp)
        write_block(f'grinder_mk{tier}_top', grinder_top(tier).rows(), tp)
    write_still('block', 'grinder_front_glow', grinder_front_glow(), pal)
    write_block('grinder_top_glow', grinder_top_glow().rows(), pal)
    write_block('grinder_bay', grinder_bay(0, False).rows(), pal)
    write_anim('block', 'grinder_bay_on', [grinder_bay(i, True) for i in range(GRINDER_FRAMES)], pal, frametime=1)
    write_anim('block', 'grinder_bay_glow', [grinder_glow(i) for i in range(GRINDER_FRAMES)], pal, frametime=1)


def write_furnace():
    for tier in (1, 2, 3, 4):
        p = furnace_pal(tier)
        write_block(f'electric_furnace_mk{tier}_front', furnace_front(tier, False).rows(), p)
        write_block(f'electric_furnace_mk{tier}_front_on', furnace_front(tier, True).rows(), p)
        write_block(f'electric_furnace_mk{tier}_top', furnace_top(tier).rows(), p)
    p = furnace_pal()
    frames = range(FURNACE_FRAMES)
    write_block('electric_furnace_chamber', furnace_chamber(False).rows(), p)
    write_anim('block', 'electric_furnace_chamber_on', [furnace_chamber(True, t) for t in frames], p,
               frametime=FURNACE_FRAMETIME, interpolate=True)
    write_anim('block', 'electric_furnace_front_glow', [furnace_glow(t) for t in frames], p,
               frametime=FURNACE_FRAMETIME, interpolate=True)
    write_anim('block', 'electric_furnace_top_glow', [furnace_top_glow(t) for t in frames], p,
               frametime=FURNACE_FRAMETIME, interpolate=True)


def main():
    write_block('processing_machine_bottom', casing.bottom().rows(), casing.casing_palette(casing.PROCESSING_TIERS[1]))
    casing.write_side_glow()
    for tier in (1, 2, 3, 4):
        write_block(f'processing_mk{tier}_side', casing.side(tier).rows(), casing.casing_palette(casing.PROCESSING_TIERS[tier]))
    write_grinder()
    write_furnace()

    for metal in DUST_COLORS:
        rows, pal = dust(metal)
        write_item(f'{metal}_dust', rows, pal)
    for name in BALL_MATERIAL:
        rows, pal = balls(name)
        write_item(f'{name}_grinding_balls', rows, pal)


if __name__ == '__main__':
    main()
