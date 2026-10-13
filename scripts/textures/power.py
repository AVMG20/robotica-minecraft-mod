"""Power module block textures: copper panels in a brushed steel frame, brass fittings, glowing parts on overlays.
Run: python3 scripts/textures/power.py   (writes textures/block/*.png; models are written by scripts/data/power_models.py)

Every machine face shares one construction (top-left light): a 2 px steel frame with riveted corner brackets around a
recessed copper panel. Sides carry louvres, tops a hatch or the machine's working part, bottoms stay plain. The part
that glows (fire, coil, gauge cells) is drawn on a separate *_glow texture that the model lays over the face at full
brightness; the base texture keeps a dim version of it so the face also reads when the overlay is off.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, grain, hash01, material, smooth_noise, write_anim, write_block, write_item, write_still  # noqa: E402
import machine_casing as casing  # noqa: E402

CU, ST, BR, AU, WD, SO = '01234', 'abcde', '56789', 'ABCDE', 'uvwWX', 'mnopP'
MG, NV, SL = '!#$%&', '()[]{', '<>?;:'                                                 # magma, null, slate (solar Mk3 / Mk4)
P = {'k': '#16191B', 'K': '#0B0D0E'}
P.update(material(CU, 'copper'))
P.update(material(ST, 'steel'))
P.update(material(BR, 'brass'))
P.update(material(AU, 'gold'))
P.update(material(WD, 'wood'))
P.update(material(MG, 'magma'))
P.update(material(NV, 'null'))
P.update(material(SL, 'slate'))
P.update({
    'm': '#0B1638', 'n': '#14245E', 'o': '#22408F', 'p': '#3F6FC8', 'P': '#9CC4F4',      # solar cell blue
    'f': '#FF7A1A', 'F': '#FFC23A', 'O': '#FFF3C0', 'x': '#B8261A', 'X': '#5A1408',    # flame, ember
    'y': '#2FB8CC', 'Y': '#7FEFF8', 'z': '#E8FFFF', 'Z': '#165E6A',                    # cyan energy
    'r': '#5A1A14', 'R': '#FF4A3A', 'g': '#1C4A22', 'G': '#5CFF6A',                    # LEDs off/on
    'h': '#E8B530', 'H': '#2A2418',                                                    # hazard stripes
    'i': '#ECEFF0', 'j': '#2A2F33',                                                    # gauge dial face / needle
})
P.update(grain('lL', 'copper', 0.22))
P.update(grain('sS', 'steel', 0.22))
P.update(grain('tT', 'brass', 0.22))
P.update(grain('qQ', 'gold', 0.22))
GRAIN = {CU: 'lL', ST: 'sS', BR: 'tT', AU: 'qQ'}


def plate(c, x, y, w, h, r, seed=0, **kw):
    """Brushed plate with subtle grain streaks for the power materials."""
    return c.plate(x, y, w, h, r, seed, grain=GRAIN.get(r), **kw)
TIER = {1: CU, 2: ST, 3: AU}


# ---------------------------------------------------------------- shared construction

def frame(seed=0, panel=CU, frame_ramp=ST):
    """Steel frame (2 px) with riveted corner brackets around a recessed, brushed copper panel."""
    f = frame_ramp
    c = Canvas()
    c.plate(0, 0, 16, 16, f, seed, brushed=False)
    c.rect(1, 1, 14, 14, f[2])
    c.rect(1, 14, 14, 1, f[1]).rect(14, 1, 1, 14, f[1])
    plate(c, 2, 2, 12, 12, panel, seed + 3, bevel=False, density=0.3)
    c.rect(2, 2, 12, 1, panel[1]).rect(2, 2, 1, 12, panel[1])           # frame shadow on the panel
    c.rect(3, 13, 11, 1, panel[3]).rect(13, 3, 1, 11, panel[3])          # lit lip
    c.set(2, 2, panel[0])
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):                    # corner brackets with a rivet
        c.rect(x, y, 3, 3, f[3])
        c.rect(x + 1, y + 2, 2, 1, f[1]).rect(x + 2, y + 1, 1, 2, f[1])
        c.set(x + 1, y + 1, f[4])
    c.set(0, 0, f[4]).set(15, 15, f[0])
    return c


def side():
    c = frame(1)
    c.recess(4, 4, 8, 8, CU, fill='2')
    for y in range(5, 11, 2):
        c.rect(5, y, 6, 1, 'K').rect(5, y + 1, 6, 1, '3')                # louvres
    c.rivet(3, 7, CU).rivet(12, 7, CU)
    return c.rows()


def bottom():
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, 9, density=0.25)
    c.rect(2, 2, 12, 1, 'b').rect(2, 2, 1, 12, 'b').rect(2, 13, 12, 1, 'd').rect(13, 2, 1, 12, 'd')
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):                    # feet
        c.rect(x, y, 3, 3, 'a').set(x + 1, y + 1, 'k')
    return c.rows()


def hatch_top(seed=2):
    """Generic top: frame plus a riveted service hatch with side hinges and a lever latch."""
    c = frame(seed, panel=ST)
    c.rect(3, 3, 10, 10, 'a')                                            # seam
    plate(c, 4, 4, 8, 8, ST, seed + 1, density=0.3)
    c.draw(3, 5, ['8', '6', '.', '.', '8', '6'])                         # hinges
    c.draw(10, 6, ['ke', 'kd', 'kd', 'kc'])                              # latch lever
    c.rivet(5, 5, ST).rivet(5, 10, ST)
    return c


def led(c, x, y, on, color='G'):
    c.set(x, y, color if on else {'G': 'g', 'R': 'r', 'Y': 'Z'}[color])


# ---------------------------------------------------------------- combustion generator
# Generator family body: gunmetal instead of the processing graphite, copper accents; identity colour orange-red
# ember. The front is a chamfered fire window sunk 2 px into the casing: a white firebrick firebox with a coal heap on
# a grate (the bay: GEN_PARTS of the chamber texture, model: power_models.generator_model) behind a glass glare, and
# an ember strip that stays dim red while idle; the exhaust on top shows ember glass between its slats. Working: five
# flame tongues rise, fall and sway over a glowing fuel bed in one 64 tick loop (with tileable noise scrolled upward
# for rolling licks), a hot glint runs along the ember strip and heat rolls across the exhaust in the same loop.

GEN_BODY = ('#1E2328', '#323A42', '#4C565F', '#6C7882', '#97A3AD')          # gunmetal (generators)
GEN_PAL = casing.casing_palette(MATERIALS['copper'], **dict(zip(casing.BODY, GEN_BODY)), **{
    'm': '#2A0A06', 'n': '#5C160A', 'o': '#9E2A0C', 'p': '#E0501A', 'q': '#FF8A24', 'r': '#FFC24A', 's': '#FFF4C8',
    '1': '#5A524C', '2': '#7E756C', '3': '#A69C90', '4': '#CCC3B6', '5': '#EAE4DA',      # refractory ceramic
    'E': '#6E1E0E', 'F': '#A8341A', 'G': '#D8E6EE', 'H': '#8FA6B4',                    # standby ember, glass glare
})
GEN_BAY = (3, 3, 10, 8)                           # x, y, w, h of the fire window on the front
GEN_FRAMES, GEN_FRAMETIME = 32, 2
# chamber texture regions (x, y, w, h) besides the back wall (the bay area); power_models.generator_model maps them
GEN_PARTS = {'wall_left': (0, 3, 2, 8), 'wall_right': (14, 3, 2, 8), 'floor': (3, 0, 10, 2), 'roof': (3, 14, 10, 2)}
FIRE = 'mnopqrs'
GEN_HEAP = (1, 1, 2, 2, 3, 3, 2, 2, 1, 1)                                   # idle coal heap height per column


# flame tongues: (centre x, width, mean height, height swing, cycles per loop, phase); heights as a fraction of the bay
GEN_TONGUES = ((1.5, 1.8, 0.8, 0.2, 1, 0.0), (4.6, 2.3, 1.05, 0.18, 1, 0.45), (7.8, 1.9, 0.88, 0.2, 2, 0.2),
               (3.0, 1.4, 0.7, 0.18, 2, 0.7), (6.2, 1.5, 0.75, 0.18, 1, 0.8))


def gen_heat(x, y, t):
    """Fire intensity at back wall pixel (x 0..9, y 0..7 from the top) at loop phase t (0..1): five flame tongues whose
    heights rise and fall and whose tips sway, plus tileable noise scrolled up by whole periods for rolling licks, so
    every frame follows from the last and the loop is seamless."""
    h = (7.5 - y) / 8
    v = -1.0
    for cx, w, mean, swing, cycles, phase in GEN_TONGUES:
        a = 2 * math.pi * (cycles * t + phase)
        top = mean + swing * math.sin(a)
        dx = x - cx - 0.8 * h * math.sin(a + 1.3 + h * 3)
        v = max(v, 1.3 - 1.1 * h / top - (dx / w) ** 2 * (0.3 + 0.8 * h))
    n = smooth_noise(1.5 * x, 1.0 * y + 16 * t, 11, 4, 16)
    return v + (n - 0.5) * 0.55


def gen_brick(x, y):
    """Refractory brick pattern of the firebox wall (bay coordinates): 0 mortar, 1 brick, 2 lit brick edge."""
    if y % 3 == 2:
        return 0
    if (x + (0 if (y // 3) % 2 == 0 else 3)) % 6 == 5:
        return 0
    return 2 if y % 3 == 0 else 1


def gen_chamber(lit, t=0):
    """Back wall (fire or cold firebox) in the bay area, the bay walls, floor and roof in GEN_PARTS."""
    x0, y0, w, h = GEN_BAY
    R = GEN_PARTS
    c = Canvas()
    ph = t / GEN_FRAMES
    for y in range(h - 2):
        for x in range(w):
            b = gen_brick(x, y)
            if lit:
                v = gen_heat(x, y, ph)
                i = sum(v >= k for k in (0.18, 0.32, 0.47, 0.64, 0.8, 0.97))
                if i == 0 and y >= 3:
                    i = 1                                                    # the firebox glows red behind the flames
                c.set(x0 + x, y0 + y, FIRE[i])
            else:
                c.set(x0 + x, y0 + y, '2' if b == 0 else '4' if b == 1 else '5')
    # burner rail: the fuel bed on a steel bar, glowing while working
    yb = y0 + h - 2
    for x in range(w):
        if lit:
            v = 0.5 + 0.5 * math.sin(2 * math.pi * (ph * 2 - x / 5))
            c.set(x0 + x, yb, 'r' if v > 0.7 else 'q').set(x0 + x, yb + 1, 'p' if x % 2 else 'o')
        else:
            c.set(x0 + x, yb + 1, 'd' if x % 2 else 'c')                       # grate
            top = yb + 1 - GEN_HEAP[x]                                            # cold coal heap with embers
            for y in range(top, yb + 1):
                ember = y == yb and x % 3 == 1 or y == yb - 1 and x in (4, 6)
                c.set(x0 + x, y, 'F' if ember else 'c' if y == top else 'E' if y == yb else 'a')
    # bay walls: ceramic lit by the fire, the floor is the burner rail's top
    for name in ('wall_left', 'wall_right'):
        x, y, ww, wh = R[name]
        for yy in range(wh):
            for xx in range(ww):
                inner = (xx == 1) == (name == 'wall_left')
                if lit:
                    ch = 'q' if yy >= wh - 2 else 'p' if inner and yy >= wh - 4 else 'o' if inner else 'n'
                else:
                    ch = 'E' if yy == wh - 2 else 'c' if yy == wh - 1 else '4' if inner else '3'
                c.set(x + xx, y + yy, ch)
    x, y, fw, _ = R['floor']
    for i in range(fw):
        c.set(x + i, y, ('r' if i % 2 else 'q') if lit else ('F' if i % 2 else 'E'))
        c.set(x + i, y + 1, ('q' if i % 2 else 'p') if lit else 'c')
    x, y, rw, _ = R['roof']
    c.rect(x, y, rw, 2, 'n' if lit else '2').rect(x, y + 1, rw, 1, 'm' if lit else '1')
    return c


def gen_glass():
    """Glass pane over the window: only its glare shows (cutout)."""
    return Canvas().set(4, 5, 'H').set(5, 4, 'G').set(6, 3, 'H')


def generator_front(lit):
    """Casing face: the chamfered fire window on a copper sill, the ember strip and the status lamp under it."""
    x0, y0, w, h = GEN_BAY
    c = casing.shell(1)
    c.rect(x0, y0, w, h, '.')
    for x, y in ((x0, y0), (x0 + w - 1, y0), (x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)):
        c.set(x, y, 'a')                                                  # chamfered window corners
    c.rect(x0, y0 + h, w, 1, 'h').set(x0, y0 + h, 'i').set(x0 + w - 1, y0 + h, 'g')   # copper sill
    c.rect(3, 12, 8, 1, 'k')
    for x in range(4, 10):
        c.set(x, 12, 'p' if lit else 'E' if x in (4, 9) else 'F')         # ember strip
    c.set(12, 12, 'L' if lit else 'l')
    return c


def generator_front_glow(t):
    """The ember strip blazing, a hot glint running along it; the status lamp."""
    c = Canvas()
    head = 4 + 6 * t / GEN_FRAMES
    for x in range(4, 10):
        d = abs(x - head)
        c.set(x, 12, 's' if d < 0.6 else 'r' if d < 1.6 else 'q')
    c.set(12, 12, 'L')
    return c


def generator_top():
    """Exhaust: a round port in a gunmetal collar with four copper clamps, ember tinted glass under three slats."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 4.6,
            lambda x, y: 'e' if x + y < 11 else 'd' if x + y < 19 else 'c')     # collar
    for x, y in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):
        c.set(x, y, 'h' if x + y < 15 else 'g')                                 # copper clamps
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 3.7, lambda x, y: 'F' if (x * 2 + y) % 5 == 0 else 'E')
    for y in (5, 7, 9):
        for x in range(3, 13):
            if math.hypot(x - 7.5, y - 7.5) <= 3.8:
                c.set(x, y, 'd' if x < 7 else 'c')
    casing.tier_notches(c, 1)
    return c


def generator_top_glow(t):
    """Heat glowing up through the exhaust grille, a brighter band rolling across with the fire."""
    c = Canvas()
    ph = t / GEN_FRAMES * 2 * math.pi
    for y in range(3, 13):
        for x in range(3, 13):
            if math.hypot(x - 7.5, y - 7.5) <= 3.7 and y not in (5, 7, 9):
                v = 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.5 - 2 * ph)
                c.set(x, y, 'r' if v > 0.8 else 'q' if v > 0.45 else 'p')
    return c


# ---------------------------------------------------------------- metal press
# The processing machines' casing (scripts/textures/machine_casing.py) with a press bay cut 2 px deep into the front
# (model: power_models.press_model), framed like a hydraulic press: a light steel crown and uprights around two cyan
# hydraulic columns, a safety-yellow ram over a steel die bed, and a yellow bed lamp under the bay (dim at standby).
# The bay texture holds the back wall in 4..11 x 3..12 and the bay's ceiling (rows 0-1), floor (rows 14-15) and side
# walls (cols 0-1, 14-15) around it.
# Working, one 32 tick stroke: an ingot slides onto the die, the ram drops on it, flattens it to a plate in a cyan
# flash, holds, rises slowly and the plate slides out. Bay, glow, columns, bed lamp and top share the loop.

PRESS_TICKS = 32
PRESS_PAL = casing.casing_palette(MATERIALS['copper'], **{
    '6': '#8E99A2', '7': '#D6DEE3',                                       # ram rod
    'm': '#3C454D', 'n': '#56616A', 'o': '#7A868F', 'p': '#A6B1B9', 'q': '#D2DAE0',      # light steel frame
    'r': '#4A3A10', 's': '#8E6A12', 't': '#D49E1C', 'y': '#F6C93A', 'z': '#FFF2A6',      # safety yellow
    '8': '#7A6018', 'G': '#2A5762', 'H': '#4C8C99',                       # bed lamp off, column glass
})
RAM = [0] * 8 + [1, 1, 2, 3] + [3] * 6 + [2, 2, 2, 1, 1, 1, 1] + [0] * 7    # ram drop (px) per tick
IMPACT = RAM.index(3)


def press_slide(t):
    """Conveyor offset during the dwell: the plate leaves to the right, the next ingot comes in from the left."""
    return min(t, 8)


def press_bay(t, lit):
    """Back wall and bay walls at tick t of the stroke (idle: ram up, an ingot on the die)."""
    c = Canvas()
    c.rect(4, 3, 8, 10, 'o').rect(4, 3, 1, 10, 'n').rect(11, 3, 1, 10, 'n')   # steel back wall, guide rails
    c.rect(4, 3, 8, 1, 'p').rect(5, 3, 6, 1, 'q').rect(6, 4, 4, 1, 'n')  # crown and rod gland
    c.rect(4, 12, 8, 1, 'q').set(11, 12, 'p')                            # die bed
    p = RAM[t] if lit else 0
    if p:
        c.rect(7, 5, 2, p, '6').rect(7, 5, 1, p, '7')                    # rod
    y = 5 + p                                                             # ram: yellow head, die face
    c.rect(4, y, 8, 1, 'y').rect(4, y + 1, 8, 1, 't').rect(4, y + 2, 8, 1, 's').rect(5, y + 2, 6, 1, 'u')
    c.set(4, y, 'z').set(11, y, 't').set(11, y + 1, 's')
    s = press_slide(t) if lit else 8
    if lit and IMPACT <= t:
        c.rect(4, 11, 8, 1, 'h').set(4, 11, 'i').set(11, 11, 'g')         # flattened plate
    else:
        if lit and s < 8:
            c.rect(4 + s, 11, 8 - s, 1, 'h')                              # last plate leaving
        for x in range(s - 2, s + 2):                                     # ingot coming in
            if 4 <= x < 12:
                c.set(x, 10, 'i' if x < s + 1 else 'h').set(x, 11, 'h' if x < s + 1 else 'g')
    c.rect(3, 0, 10, 2, 'n').rect(3, 1, 10, 1, 'o')                      # ceiling (row 0 at the back)
    c.rect(3, 14, 10, 1, 'y').rect(3, 15, 10, 1, 't')                    # floor: the yellow bed plate (row 14)
    c.rect(0, 3, 2, 10, 'o').rect(0, 3, 1, 10, 'p')                      # side walls (col 0 and 15 at the front)
    c.rect(14, 3, 2, 10, 'n').rect(15, 3, 1, 10, 'o')
    return c


def press_glow(t):
    """Die face charging on the drop, the impact flash spreading along the die and fading."""
    c = Canvas()
    y = 5 + RAM[t] + 2
    d = t - IMPACT
    if 8 <= t < IMPACT:
        c.rect(5, y, 6, 1, 'v').rect(6, y, 4, 1, 'w')
    elif 0 <= d < 6:
        c.rect(5, y, 6, 1, 'xxwwvv'[d])
        for x in range(4, 12):
            if abs(x - 7.5) <= 1.5 + d * 1.5:
                c.set(x, 11, 'x' if d < 2 else 'w' if d < 4 else 'v')
        if d < 4:
            c.set(4, 10 - d, 'w').set(11, 10 - d, 'w')                    # sparks up the guides
        c.rect(3, 14, 10, 1, 'xwwvv.'[d])                                 # floor lit by the flash
        if d < 3:
            c.rect(5, 15, 6, 1, 'v')
    return c


def press_front(lit):
    """Casing face with the press bay in a light steel press frame (crown, uprights, hydraulic columns), the yellow
    bed lamp under the bay and the status lamp."""
    c = casing.shell(1)
    c.rect(2, 2, 12, 1, 'q').rect(2, 2, 1, 12, 'p').rect(13, 2, 1, 12, 'o')   # crown and uprights
    c.set(13, 2, 'p').set(2, 13, 'o')
    for x in (3, 12):                                                     # hydraulic columns
        c.rect(x, 3, 1, 10, 'v' if lit else 'G').set(x, 3, 'n').set(x, 12, 'm')
        if not lit:
            c.rect(x, 4, 1, 3, 'H')
    c.rect(3, 13, 10, 1, 's').rect(4, 13, 8, 1, 't' if lit else '8')     # bed lamp
    c.rect(4, 3, 8, 10, '.')
    c.rect(7, 1, 2, 1, 'L' if lit else 'l')
    return c


def press_front_glow(t):
    """Lamp, the bed lamp (flashing white on impact), and the columns: pressure rushes down them on the drop, flashes
    on impact, drains up on the return."""
    c = Canvas().rect(7, 1, 2, 1, 'L')
    head = None
    if 8 <= t < IMPACT:
        head = 4 + (t - 8) * 3                                            # fast pulse down
    elif IMPACT + 6 <= t < IMPACT + 13:
        head = 11 - (t - IMPACT - 6)                                      # slow pulse up with the return
    d = t - IMPACT
    for x in (3, 12):
        for y in range(4, 12):
            ch = 'v'
            if 0 <= d < 3:
                ch = 'x' if d == 0 else 'w'
            elif head is not None and abs(y - head) <= 1:
                ch = 'x' if y == head else 'w'
            c.set(x, y, ch)
    c.rect(4, 13, 8, 1, 'z' if 0 <= d < 3 else 'y')
    return c


def press_top():
    """Hydraulic cylinder head: a light steel cap in a safety-yellow ring around a cyan pressure core."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 4.3,
            lambda x, y: 'y' if x + y < 12 else 't' if x + y < 17 else 's')     # yellow ring
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 3.2,
            lambda x, y: 'q' if x + y < 11 else 'p' if x + y < 15 else 'o')     # cap
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 2.0, lambda x, y: 'm')  # core socket
    c.rect(7, 7, 2, 2, 'u')
    casing.tier_notches(c, 1)
    return c


def press_top_glow(t):
    """The pressure core flares on the drop and the impact."""
    d = t - IMPACT
    c = Canvas()
    c.rect(7, 7, 2, 2, 'x' if 0 <= d < 3 else 'w' if 8 <= t < IMPACT or 3 <= d < 6 else 'v')
    return c


def write_press():
    def anim(name, fn):
        """The loop starts just after the impact, so a still (item, screenshot) shows the press at work."""
        frames = [fn((t + IMPACT + 1) % PRESS_TICKS) for t in range(PRESS_TICKS)]
        write_anim('block', name, frames, PRESS_PAL, frametime=1)
    write_block('metal_press_front', press_front(False).rows(), PRESS_PAL)
    write_block('metal_press_front_on', press_front(True).rows(), PRESS_PAL)
    write_block('metal_press_bay', press_bay(0, False).rows(), PRESS_PAL)
    write_block('metal_press_top', press_top().rows(), PRESS_PAL)
    anim('metal_press_front_glow', press_front_glow)
    anim('metal_press_bay_on', lambda t: press_bay(t, True))
    anim('metal_press_bay_glow', press_glow)
    anim('metal_press_top_glow', press_top_glow)


# ---------------------------------------------------------------- charger
# Energy storage family (light steel body, copper accents) with a teal identity. The front is a charging dock sunk 2 px
# into the casing (model: power_models.charger_model): a white power cell stands in a copper cradle between two teal
# coil pillars whose contacts touch its sides. The dock texture holds the back wall in 3..12 x 3..11, the dock's
# ceiling (row 1), floor (row 14) and side walls (cols 1 and 14), and the cell's top (row 12) and sides (col 2); the
# cell's front reuses its back wall pixels (CH_CELL). Working, one 48 tick loop: pulses run along the pillars into the
# contacts, the cell's window fills row by row and flashes when full, energy chases along the rail under the dock into
# it, and the induction rings on top light from the rim inwards.

ACC_CHARGER_BODY = dict(zip('abcde', ('#4A535B', '#6F7A83', '#9AA5AE', '#C4CDD4', '#EBF0F3')))   # light steel
CH_FRAMES, CH_FRAMETIME = 24, 2
CH_EXTRA = {
    't': '#123F38', 'T': '#1F7563', 'q': '#1ECB9C', 'Q': '#86F7D2', 'r': '#E4FFF6',     # teal: glass, dim, lit, bright, white
    'm': '#E4EAEE', 'n': '#B4BFC6', 'o': '#FFFFFF', 'p': '#7E8A94',                     # ceramic power cell
}
CH_CELL = (5, 4, 6, 7)                                                    # x, y, w, h of the cell on the back wall
CH_WINDOW = (7, 5, 2, 5)                                                  # its charge window
CH_START = 16                                                             # loop start: cell nearly full (stills show it)


def ch_pal():
    return casing.casing_palette(MATERIALS['copper'], **ACC_CHARGER_BODY, **CH_EXTRA)


def charger_dock():
    """Back wall, dock walls and cell faces."""
    c = Canvas()
    c.rect(3, 3, 10, 9, 'b').rect(3, 3, 10, 1, 'a')                      # back wall
    for x in (3, 12):                                                     # coil pillars
        c.rect(x, 4, 1, 7, 'T').set(x, 4, 'a').set(x, 10, 'a')
    for x in (4, 11):                                                     # contacts: copper jaws round a teal conduit
        c.set(x, 6, 'h').set(x, 7, 'T').set(x, 8, 'g')
    x, y, w, h = CH_CELL
    c.rect(7, 3, 2, 1, 'h').set(8, 3, 'g')                                # cell terminal
    c.rect(x, y, w, h, 'm').rect(x, y, w, 1, 'o').rect(x, y, 1, h, 'o')
    c.rect(x + w - 1, y + 1, 1, h - 1, 'n').rect(x + 1, y + h - 1, w - 1, 1, 'p')
    wx, wy, ww, wh = CH_WINDOW
    c.rect(wx, wy, ww, wh, 'T').rect(wx + ww - 1, wy, 1, wh, 't')         # charge window
    c.rect(4, 11, 8, 1, 'h').set(4, 11, 'i').set(11, 11, 'g')             # copper cradle
    c.rect(5, 11, 6, 1, 'g')                                              # cell shadow on it
    c.rect(x, 12, w, 1, 'o').set(x + w - 1, 12, 'm')                      # cell top
    c.rect(2, y, 1, h, 'n')                                               # cell sides
    c.rect(3, 1, 10, 1, 'b')                                              # ceiling
    c.rect(3, 14, 10, 1, 'd')                                             # floor
    c.rect(1, 3, 1, 9, 'c').rect(14, 3, 1, 9, 'b')                        # side walls
    return c


def charger_dock_glow(f):
    """Cell window filling a row every 4 frames and flashing when full, pulses running down and up the pillars into
    the contacts."""
    c = Canvas()
    wx, wy, ww, wh = CH_WINDOW
    n = min(wh, f // 4 + 1)
    flash = f - 20
    for i in range(n):
        ch = 'Q' if i == n - 1 else 'q'
        if flash >= 0:
            ch = 'rQqq'[flash]
        c.rect(wx, wy + wh - 1 - i, ww, 1, ch)
    p = (f % 8) // 2                                                      # pulse step 0..3: pillar ends -> contact row
    for pillar, contact in ((3, 4), (12, 11)):
        for y in range(4, 11):
            c.set(pillar, y, 'Q' if abs(y - 7) == 3 - p else 'q')
        c.set(contact, 7, 'r' if p == 3 else 'Q' if p == 0 else 'q')
    return c


def charger_front(lit):
    """Casing face with the dock opening, a teal rail under it and the status lamp."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 9, '.')
    c.rect(3, 12, 10, 1, 'T').set(3, 12, 'a').set(12, 12, 'a')            # rail
    c.rect(7, 1, 2, 1, 'L' if lit else 'l')
    return casing.tier_notches(c, 1)


def charger_glow(f):
    """Lamp, and energy chasing along the rail from both ends into the dock."""
    c = Canvas().rect(7, 1, 2, 1, 'L')
    for x in range(4, 8):
        k = (x - 4 - f // 2) % 4
        ch = 'r' if k == 0 else 'Q' if k == 1 else 'q'
        c.set(x, 12, ch).set(15 - x, 12, ch)
    return c


def ch_rings():
    """Pixels of the two induction rings on top, outer (an octagon round 4..11) first, and the centre contact."""
    outer = [(x, y) for x in range(6, 10) for y in (4, 11)] + [(x, y) for x in (4, 11) for y in range(6, 10)]
    outer += [(5, 5), (10, 5), (5, 10), (10, 10)]
    inner = [(7, 6), (8, 6), (7, 9), (8, 9), (6, 7), (6, 8), (9, 7), (9, 8)]
    centre = [(7, 7), (8, 7), (7, 8), (8, 8)]
    return outer, inner, centre


def charger_top():
    """Induction pad: an octagonal steel plate with two teal rings round a copper contact."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.rect(4, 4, 8, 8, 'd').rect(5, 5, 6, 6, 'c')
    outer, inner, centre = ch_rings()
    for x, y in outer + inner:
        c.set(x, y, 'T')
    for x, y in centre:
        c.set(x, y, 'h')
    c.set(7, 7, 'i').set(8, 8, 'g')
    return casing.tier_notches(c, 1)


def charger_top_glow(f):
    """The rings light from the rim inwards, the contact flares as the pulse arrives (interpolated)."""
    c = Canvas()
    for i, ring in enumerate(ch_rings()):
        k = (f - i * 3) % 12
        ch = 'r' if k == 0 else 'Q' if k in (1, 11) else 'q'
        for x, y in ring:
            c.set(x, y, ch)
    return c


# ---------------------------------------------------------------- wireless charger
# Energy storage and charging family body: light steel, copper accents; identity colour violet. The front is a dark
# glass broadcast dish (octagonal rings round an emitter node), the top the same dish round the mast, which carries a
# halo ring and a violet orb (power_models.wireless_model). Working: a wave leaves the node every 24 ticks and glides
# outward ring by ring, fading, on the front and the top at once; node and orb flash as it leaves.

WL_BODY = ('#4A535B', '#6F7A83', '#9AA5AE', '#C4CDD4', '#EBF0F3')           # light steel (storage and charging)
WL_PAL = casing.casing_palette(MATERIALS['copper'], **dict(zip(casing.BODY, WL_BODY)), **{
    '6': '#1E0E3C', '7': '#3E1F78', '8': '#6A3EC4', '9': '#A97CFF', '0': '#ECE2FF',       # violet, deep -> white
    'D': '#151922', 'M': '#262C3A', 'N': '#3A4256',                                     # dark glass
})
WL_FRAMES, WL_FRAMETIME = 24, 1
WL_WAVES = 1                                       # waves travelling at once


def wl_slot(x, y):
    """Octagonal ring index of a pixel round the face centre: 1 the 2x2 node, 2..4 the rings, 5 the dish lip."""
    ax, ay = abs(x - 7.5), abs(y - 7.5)
    return int(max(ax, ay, (ax + ay) * 0.75) + 0.5)


def wl_wave(slot, t, r0, r1):
    """Brightness (0..1) of a ring slot at frame t: WL_WAVES waves travel from r0 to r1 per loop, handed from one slot
    to the next (so they glide between pixels), dimming as they grow."""
    best = 0.0
    for k in range(WL_WAVES):
        f = (t / WL_FRAMES + k / WL_WAVES) % 1
        r = r0 + (r1 - r0) * f
        best = max(best, max(0.0, 1 - 1.6 * abs(slot - r)) * (1 - 0.5 * f))
    return best


def wl_level(v):
    return '0' if v > 0.6 else '9' if v > 0.32 else '8' if v > 0.12 else None


def wl_dish(c, top=False, lit=False):
    """The octagonal dark glass dish: a bevelled lip, the violet emitter node and a violet ring groove (dark while
    working, so only the travelling waves light the rings)."""
    for y in range(3, 13):
        for x in range(3, 13):
            s = wl_slot(x, y)
            if s == 5:
                c.set(x, y, 'e' if x + y < 15 else 'b')
            elif s < 5:
                groove = 'D' if lit else '7' if x + y > 14 else '8'
                c.set(x, y, ('9' if lit else '8') if s == 1 else groove if s == 3 else
                      'M' if s == 2 and x + y < 15 and not top else 'D')
    return c


def wl_pulse(t):
    """Orb and node brightness (0..1): a flash as each wave is born, fading over the following frames."""
    f = (t / WL_FRAMES * WL_WAVES) % 1
    return max(0.0, 1 - f * 3)


def wireless_front(lit):
    """Light steel face with the dark glass dish, the status lamp above it."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 10, 'c')
    wl_dish(c, lit=lit)
    c.rect(7, 1, 2, 1, 'L' if lit else 'l')
    return c


def wireless_front_glow(t):
    """The node flashing as each wave leaves it, the waves gliding out over the rings."""
    c = Canvas().rect(7, 1, 2, 1, 'L')
    for y in range(3, 13):
        for x in range(3, 13):
            s = wl_slot(x, y)
            if s == 1:
                c.set(x, y, wl_level(wl_wave(s, t, 1.0, 5.0)) or '9')
            elif s < 5:
                ch = wl_level(wl_wave(s, t, 1.0, 5.0))
                if ch:
                    c.set(x, y, ch)
    return c


def wireless_top(lit=False):
    """Emitter plate: the same dish round the mast collar (which covers the node and the first ring)."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 10, 'c')
    wl_dish(c, top=True, lit=lit)
    casing.tier_notches(c, 1)
    return c


def wireless_top_glow(t):
    """The same waves gliding out from under the mast collar."""
    c = Canvas()
    for y in range(3, 13):
        for x in range(3, 13):
            s = wl_slot(x, y)
            if 2 < s < 5:
                ch = wl_level(wl_wave(s, t, 1.0, 5.0))
                if ch:
                    c.set(x, y, ch)
    return c


def wireless_mast(t=None):
    """Mast parts (power_models.wireless_model maps them): collar top in 0..3 x 0..3, collar side in 0..3 x 4, halo
    ring in 5..10 x 5..10, rod in 12..13 x 0..15. Working (frame t), the halo flashes with each wave."""
    lit = t is not None
    flash = lit and wl_pulse(t) > 0.4
    c = Canvas()
    c.rect(0, 0, 4, 4, 'd').bevel(0, 0, 4, 4, 'e', 'b').rect(1, 1, 2, 2, 'a')       # collar top round the rod
    c.draw(0, 4, ['e8' + ('9' if lit else '8') + 'c'])                                 # collar side, violet band
    for i in range(6):
        for j in range(6):
            if (i in (0, 5) or j in (0, 5)) and (i, j) not in ((0, 0), (0, 5), (5, 0), (5, 5)):
                c.set(5 + i, 5 + j, '0' if flash else ('9' if lit else '8') if i + j < 5 else ('8' if lit else '7'))
    c.rect(12, 0, 1, 16, 'e').rect(13, 0, 1, 16, 'c')                                 # rod
    for y in (3, 9):
        c.set(12, y, 'c').set(13, y, 'b')
    return c


def wireless_orb(t=None):
    """Orb (sampled 5..10): violet glass, light from the top left; working, it flashes white with each ring."""
    c = Canvas()
    for x in range(5, 11):
        for y in range(5, 11):
            d = math.hypot(x - 7.0, y - 7.0)
            if t is None:
                c.set(x, y, '9' if d < 1.0 else '8' if d < 2.6 else '7')
            else:
                p = wl_pulse(t)
                c.set(x, y, '0' if d < 1.0 + 2 * p else '9' if d < 2.8 + p else '8')
    return c


# ---------------------------------------------------------------- solar panels
# Generator family: a gunmetal slab with tier metal corner brackets (the processing ramp: Mk1 copper, Mk2 gold,
# Mk3 magma, Mk4 null) round blue monocrystalline cells with clipped corners (Mk1 2x2, Mk2+ 3x3) and the light
# backsheet showing where four cells meet. The cell glass deepens per Mk (Mk1 blue, Mk2 deep blue, Mk3 blue-black,
# Mk4 blue-violet). A glint of sky light sweeps across the glass every 96 ticks (the top is animated, no overlay); the
# sides carry a lip, a recessed rail and the Mk notches.

SOLAR_CELLS = {                                     # cell glass per Mk: gap, shadow edge, cell, lit edge
    1: ('#0E2150', '#183A86', '#2A5CB8', '#4F86DA'),
    2: ('#0A1840', '#122E70', '#1E4AA2', '#3E72CC'),
    3: ('#070F2A', '#0E1F52', '#183684', '#3260B4'),
    4: ('#0E0A30', '#1C1A66', '#2E34A0', '#5A62D8'),
}
SOLAR_FRAMES, SOLAR_FRAMETIME = 32, 3               # glint loop: a 12 frame sweep, then a pause
SOLAR_GRID = {1: ((1, 9), 6), 2: ((1, 6, 11), 4)}  # cell origins (x and y) and size: Mk1 2x2, Mk2+ 3x3


def solar_pal(mk):
    m, n, o, p = SOLAR_CELLS[mk]
    return casing.casing_palette(casing.PROCESSING_TIERS[mk], **dict(zip(casing.BODY, GEN_BODY)), **{
        'm': m, 'n': n, 'o': o, 'p': p, 'q': '#9CC8F6', 'r': '#EAF6FF',                  # cells, glint
        's': '#56637A', 'S': '#93A2B8',                                                 # backsheet
    })


def solar_cell(mk, x, y):
    """(x, y inside the cell, cell size) of a top pixel, or None on the frame and the gaps."""
    origins, size = SOLAR_GRID[min(mk, 2)]
    for cx in origins:
        for cy in origins:
            if cx <= x < cx + size and cy <= y < cy + size:
                return x - cx, y - cy, size
    return None


def solar_top(mk, t):
    """Top at frame t of the glint loop."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')                                             # gaps: the dark backsheet rails
    c.bevel(0, 0, 16, 16, 'd', 'a')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):                     # tier metal corner brackets
        sx, sy = (1 if x == 0 else -1), (1 if y == 0 else -1)
        for i in range(3):
            c.set(x + sx * i, y, 'h').set(x, y + sy * i, 'h')
        c.set(x + sx * 2, y, 'g').set(x, y + sy * 2, 'g')
        c.set(x, y, 'i' if x + y < 15 else 'f' if x + y > 15 else 'h')
    c.rect(1, 1, 14, 14, 'm')                                             # dark gaps between the cells
    for y in range(16):
        for x in range(16):
            cell = solar_cell(mk, x, y)
            if not cell:
                continue
            u, v, n = cell
            if u in (0, n - 1) and v in (0, n - 1):
                continue                                                  # clipped cell corners
            c.set(x, y, 'p' if u == 0 or v == 0 else 'n' if u == n - 1 or v == n - 1 else 'o')
    origins, size = SOLAR_GRID[min(mk, 2)]
    for x in origins[1:]:                                                 # backsheet diamonds where four cells meet
        for y in origins[1:]:
            gx, gy = x - 1, y - 1
            if mk == 1:                                                   # 2 px gaps: a 2x2 centre
                c.rect(gx - 1, gy - 2, 2, 4, 's').rect(gx - 2, gy - 1, 4, 2, 's').rect(gx - 1, gy - 1, 2, 2, 'S')
            else:
                c.set(gx, gy, 'S').set(gx - 1, gy, 's').set(gx + 1, gy, 's').set(gx, gy - 1, 's').set(gx, gy + 1, 's')
    for i in range(mk):                                                   # Mk notches on the bottom rail
        c.set(12 - i * 2, 15, 'i')
    return solar_glint(c, t)


def solar_glint(c, t):
    """Sky glint: a soft diagonal band crossing the glass from the top left during the first 12 frames."""
    if t >= 12:
        return c
    pos = -4 + t * 38 / 12                                                # x + y of the band centre
    for y in range(1, 15):
        for x in range(1, 15):
            d = abs(x + y - pos)
            ch = 'r' if d < 0.5 else 'q' if d < 1.3 else 'p' if d < 2.3 and c.get(x, y) in 'mno' else None
            if ch:
                c.set(x, y, ch)
    return c


def solar_side(mk):
    """Only rows 0-5 show on the 6 px tall sides: top lip, a recessed rail with dark glass, the base with Mk notches,
    tier metal brackets at both ends."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.rect(0, 0, 16, 1, 'e').rect(0, 1, 16, 1, 'd')
    c.rect(2, 2, 12, 2, 'a').rect(3, 2, 10, 1, 'u').rect(3, 3, 10, 1, 'k')
    c.rect(0, 4, 16, 1, 'c').rect(0, 5, 16, 1, 'a')
    for x in (0, 15):
        c.rect(x, 0, 1, 6, 'h').set(x, 0, 'i').set(x, 5, 'f')
    c.set(1, 0, 'h').set(14, 0, 'g').set(1, 5, 'g').set(14, 5, 'g')
    for i in range(mk):
        c.set(12 - i * 2, 4, 'i')
    return c


# ---------------------------------------------------------------- accumulators
# Energy storage family: the casing construction in light steel. Every side: one dark glass cell column between
# cooling slats, the front marked by a status lamp; top: four cell heads on a glass bus cross. Tiers: copper, gold, magma (PROCESSING_TIERS 1-3).
# The block state `charge` (0-ACC_CELLS) fills the cells 2 rows per level on full-bright overlays shared by every tier,
# with a calm shimmer rising through the charge.

ACC_CELLS = 5
ACC_FRAMES, ACC_FRAMETIME = 32, 2
ACC_EXTRA = {
    'm': '#1D5260', 'n': '#2E7A88', 'o': '#8AD4DE', 'p': '#123039',     # empty cell glass: mid, lit edge, glare, shade
    'y': '#137489',                                                     # deep charge
}
ACC_SIDE_CELL = (6, 10)


def acc_pal(tier=1):
    return casing.casing_palette(casing.PROCESSING_TIERS[tier], **ACC_CHARGER_BODY, **ACC_EXTRA)


def acc_cell(c, x0, x1):
    """Empty glass cell column x0..x1-1 over rows 3..12, shaded round, sunk into the panel."""
    w = x1 - x0
    for x in range(x0, x1):
        i = x - x0
        c.rect(x, 3, 1, 10, 'n' if i == 0 else 'p' if i == w - 1 else 'm')
    c.rect(x0 + 1, 4, 1, 3, 'o').set(x0 + 1, 8, 'o')                    # glare
    c.rect(x0 - 1, 2, 1, 12, 'a').rect(x0 - 1, 2, w + 1, 1, 'a')         # shadowed rim top left, lit bottom right
    c.rect(x1, 2, 1, 12, 'd').rect(x0, 13, w + 1, 1, 'd')


def acc_fill(c, x0, x1, level, t):
    """Charge in a cell column: 2 rows per level from the bottom, round shading, a bright surface and a soft band of
    light rising through it (one pass per loop)."""
    w = x1 - x0
    top = 13 - 2 * level
    band = 13.5 - (t / ACC_FRAMES) * 14                                   # rows 13.5 .. -0.5
    up = {'y': 'v', 'v': 'w', 'w': 'x', 'x': 'x'}
    for y in range(top, 13):
        for x in range(x0, x1):
            i = x - x0
            ch = 'w' if i == 0 else 'y' if i == w - 1 else 'v'
            if y == top:
                ch = 'x' if i <= 1 else 'w'
            elif abs(y - band) < 0.75:
                ch = up[ch]
            c.set(x, y, ch)


def accumulator_side(tier):
    c = casing.shell(tier)
    for y in range(4, 12, 2):                                             # cooling slats
        c.rect(3, y, 2, 1, 'a').rect(3, y + 1, 2, 1, 'd')
        c.rect(11, y, 2, 1, 'a').rect(11, y + 1, 2, 1, 'd')
    acc_cell(c, *ACC_SIDE_CELL)
    return casing.tier_notches(c, tier)


def accumulator_side_glow(level, t):
    c = Canvas()
    acc_fill(c, *ACC_SIDE_CELL, level, t)
    return c


def accumulator_front(tier):
    """The side's cell column plus the status lamp that marks the front."""
    return accumulator_side(tier).rect(7, 1, 2, 1, 'l')


def accumulator_front_glow(level, t):
    return accumulator_side_glow(level, t).rect(7, 1, 2, 1, 'L')


ACC_HEADS = ((3, 3), (9, 3), (3, 9), (9, 9))                             # top left corners of the 4x4 cell heads


def accumulator_top(tier):
    """Four cell heads (steel collars round a dark glass core) on a glass bus cross."""
    c = casing.shell(tier)
    c.rect(3, 7, 10, 2, 'a').rect(7, 3, 2, 10, 'a')                      # glass bus cross
    c.rect(4, 7, 8, 2, 'm').rect(7, 4, 2, 8, 'm').rect(4, 7, 8, 1, 'n').rect(7, 4, 1, 8, 'n')
    c.rect(7, 7, 2, 2, 'p')                                               # hub
    for x, y in ACC_HEADS:
        c.rect(x, y, 4, 4, 'd')
        c.rect(x, y, 4, 1, 'e').rect(x, y, 1, 4, 'e').rect(x + 3, y + 1, 1, 3, 'b').rect(x + 1, y + 3, 3, 1, 'b')
        c.set(x, y, 'c').set(x + 3, y, 'c').set(x, y + 3, 'c').set(x + 3, y + 3, 'a')
        c.rect(x + 1, y + 1, 2, 2, 'm').set(x + 1, y + 1, 'o').set(x + 2, y + 2, 'p')
    return casing.tier_notches(c, tier)


def accumulator_top_glow(level):
    """Cell cores and hub lit, brighter the fuller the cells."""
    c = Canvas()
    c.rect(4, 7, 8, 2, 'y').rect(7, 4, 2, 8, 'y').rect(4, 7, 8, 1, 'v').rect(7, 4, 1, 8, 'v')
    core = 'v' if level < 3 else 'w'
    for x, y in ACC_HEADS:
        c.rect(x + 1, y + 1, 2, 2, core).set(x + 1, y + 1, 'x' if level == ACC_CELLS else 'w')
    c.rect(7, 7, 2, 2, core if level < ACC_CELLS else 'x')
    return c


# ---------------------------------------------------------------- tesla coil
# Tesla blocks are a low machine platform in the tier metal (I copper, II iron, III gold, IV magma, V null) with a
# glowing lens, under an energy crystal that TeslaCoilRenderer draws floating over it. Crystal and glow colours follow
# the tier too: I cyan, II blue, III amber, IV ember, V violet (dark .. white hot).
TESLA_CRYSTAL = {
    1: ('#0B3F52', '#127E98', '#2FC4DE', '#8AF0FF', '#F0FFFF'),
    2: ('#10246A', '#2448C0', '#4F7EF4', '#A8C4FF', '#F0F4FF'),
    3: ('#6A3A06', '#C07810', '#F2AE2A', '#FFE08A', '#FFFBEA'),
    4: ('#62140A', '#C2321A', '#F26A34', '#FFB088', '#FFF0E4'),
    5: ('#2C0E5C', '#6428BC', '#9C5CF4', '#D6B4FF', '#FAF2FF'),
}
GUNMETAL = {'0': '#16191C', '1': '#22272B', '2': '#2E343A', '3': '#3C444B', '4': '#58626B'}
TESLA_METAL = {1: 'copper', 2: 'iron', 3: 'gold', 4: 'magma', 5: 'null'}
# Platform parts seen from above, as half widths round the block centre; keep in step with scripts/data/power_models.py.
# base: the tier metal plate; seam: the glowing line under the housing; housing: the raised gunmetal ring with a tier
# metal top round the projector well; well: the glowing lens at its bottom; slit: the light slits on the base.
TESLA_PARTS = {
    'coil': {'base': 6, 'seam': 4.4, 'housing': 4, 'well': 2, 'slit': (5, 5.5)},
    'emitter': {'base': 4, 'seam': 3.3, 'housing': 3, 'well': 1.5, 'slit': None},
}


def tesla_palette(tier):
    return dict(GUNMETAL) | dict(zip('TUVWX', MATERIALS[TESLA_METAL[tier]])) | dict(zip('abcde', TESLA_CRYSTAL[tier]))


def tesla_top(tier, kind):
    """Top of a Tesla platform, pixel for pixel (every part's top shows its own area): a bevelled tier metal base, and
    the housing's tier metal rim with a lit outer edge and a dark lip into the well."""
    parts = TESLA_PARTS[kind]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = max(abs(dx), abs(dy))
            lit = dx < 0 and abs(dx) >= abs(dy) or dy < 0 and abs(dy) > abs(dx)
            if d < parts['well']:
                c.set(x, y, '0')
            elif d < parts['housing']:
                if d < parts['well'] + 1:
                    ch = 'T' if lit else 'U'                       # lip into the well
                elif d > parts['housing'] - 1:
                    ch = 'X' if lit else 'V'
                else:
                    ch = 'W' if hash01(x, y, 5 + tier) < 0.25 else 'V'
                c.set(x, y, ch)
            elif d < parts['base']:
                if d > parts['base'] - 1:
                    ch = 'W' if lit else 'T'
                else:
                    ch = 'V' if hash01(x, y, 2 + tier) < 0.18 else 'U'
                c.set(x, y, ch)
    return c


def tesla_side(tier):
    """Sides, one row per pixel of height: row 0 the base (tier metal with seams), rows 1-2 the gunmetal housing."""
    c = Canvas()
    for x in range(16):
        c.set(x, 0, 'T' if x % 4 == 3 else 'U')
        c.set(x, 1, '2' if x % 4 else '1')
        c.set(x, 2, '1')
        for y in range(3, 16):
            c.set(x, y, 'U')
    return c


def tesla_crystal(tier):
    """Facet of the energy crystal. TeslaCoilRenderer maps the texture down the whole crystal (tip at the top centre),
    so every facet shows a bright core band that fades towards its edges and the base, a lit left edge and a few
    sparkles."""
    rows = []
    for y in range(16):
        row = ''
        for x in range(16):
            d = abs(x - 7.5)
            v = min(3.9 if y else 3.2, 3.2 - y / 15 * 1.3 + (0.9 if d < 2 else 0.45 if d < 4 else 0) - (0.5 if x > 13 else 0)
                    + (0.8 if x < 1 else 0))
            if (x * 7 + y * 13) % 29 == 0 and 2 < x < 13:
                v += 1
            row += 'abcde'[max(0, min(4, int(v)))]
        rows.append(row)
    return rows, tesla_palette(tier)


def tesla_glow(tier):
    """Full-bright lens, seam and slit light: white hot in the middle, the tier colour outside."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            v = 4.3 - d / 2.2 + (hash01(x, y, 30 + tier) - 0.5) * 0.5
            c.set(x, y, 'abcde'[max(2, min(4, int(round(v))))])
    return c


def tesla_linker():
    """Hand tool: steel grip bottom left, copper coil wrap, a forked brass probe with a cyan spark top right."""
    c = Canvas()
    for i in range(10):
        x, y = 2 + i, 13 - i
        c.set(x, y, 'c').set(x + 1, y, 'b').set(x, y - 1, 'd')
    for i in range(3):                                                     # grip
        x, y = 2 + i, 13 - i
        c.set(x, y, 'K').set(x + 1, y, 'j').set(x, y - 1, 'j')
    for i in range(4, 8):                                                  # copper wrap
        x, y = 2 + i, 13 - i
        c.set(x, y, '3' if i % 2 else '1').set(x + 1, y, '2').set(x, y - 1, '4' if i % 2 else '2')
    c.set(12, 3, '8').set(13, 2, '9').set(11, 2, '7').set(13, 4, '7')     # brass fork
    c.set(14, 1, 'Y').set(12, 1, 'y').set(14, 3, 'y').set(15, 0, 'z').set(13, 0, 'y').set(15, 2, 'y')
    c.outline('K')
    return c


# ---------------------------------------------------------------- winding crank

def crank_base():
    """Dark oak plinth with brass corner plates."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for y in range(16):
        for x in range(16):
            n = (x * 7 + (y // 4) * 5) % 11
            if n == 0:
                c.set(x, y, 'v')
            elif n == 5:
                c.set(x, y, 'W')
    for y in (0, 4, 8, 12):
        c.rect(0, y, 16, 1, 'u')
        c.rect(0, y + 1, 16, 1, 'W')
    for x, y in ((0, 0), (12, 0), (0, 12), (12, 12)):
        c.rect(x, y, 4, 4, '7').bevel(x, y, 4, 4, '8', '5').set(x + 1, y + 1, '9').set(x + 2, y + 2, '5')
    return c


def crank_metal():
    c = Canvas()
    c.plate(0, 0, 16, 16, BR, 50, vertical=True)
    for y in range(2, 15, 4):
        c.rect(1, y, 14, 1, '5').rect(1, y + 1, 14, 1, '8')
    return c


def crank_spring():
    """Top of a wound spring drum: a brass spiral round a steel arbor."""
    c = Canvas()
    c.plate(0, 0, 16, 16, BR, 51, brushed=False)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r > 7.2:
                continue
            a = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            c.set(x, y, '8' if (r / 1.6 + a) % 1.0 < 0.5 else '6')
    c.ring(7.5, 7.5, 6.6, 7.4, '5')
    c.rect(6, 6, 4, 4, 'b').bevel(6, 6, 4, 4, 'd', 'a').set(7, 7, 'e')
    return c


# ---------------------------------------------------------------- output

def write_generator():
    p, frames = GEN_PAL, range(GEN_FRAMES)
    write_block('combustion_generator_front', generator_front(False).rows(), p)
    write_block('combustion_generator_front_on', generator_front(True).rows(), p)
    write_block('combustion_generator_top', generator_top().rows(), p)
    write_block('combustion_generator_side', casing.side(1).rows(), p)
    write_block('combustion_generator_bottom', casing.bottom().rows(), p)
    write_block('combustion_generator_glass', gen_glass().rows(), p)
    write_block('combustion_generator_chamber', gen_chamber(False).rows(), p)
    write_anim('block', 'combustion_generator_chamber_on', [gen_chamber(True, t) for t in frames], p,
               frametime=GEN_FRAMETIME, interpolate=True)
    write_anim('block', 'combustion_generator_front_glow', [generator_front_glow(t) for t in frames], p,
               frametime=GEN_FRAMETIME, interpolate=True)
    write_anim('block', 'combustion_generator_top_glow', [generator_top_glow(t) for t in frames], p,
               frametime=GEN_FRAMETIME, interpolate=True)


def write_charger():
    p = ch_pal()
    frames = [(f + CH_START) % CH_FRAMES for f in range(CH_FRAMES)]
    write_block('charger_front', charger_front(False).rows(), p)
    write_block('charger_front_on', charger_front(True).rows(), p)
    write_block('charger_top', charger_top().rows(), p)
    write_block('charger_side', casing.side(1).rows(), p)
    write_block('charger_bottom', casing.bottom().rows(), p)
    write_block('charger_dock', charger_dock().rows(), p)
    write_anim('block', 'charger_dock_glow', [charger_dock_glow(f) for f in frames], p, frametime=CH_FRAMETIME)
    write_anim('block', 'charger_front_glow', [charger_glow(f) for f in frames], p, frametime=CH_FRAMETIME)
    write_anim('block', 'charger_top_glow', [charger_top_glow(f) for f in frames], p, frametime=CH_FRAMETIME,
               interpolate=True)


def write_wireless():
    p, frames = WL_PAL, [(t + WL_FRAMES // 2) % WL_FRAMES for t in range(WL_FRAMES)]   # stills show a wave halfway
    write_block('wireless_charger_front', wireless_front(False).rows(), p)
    write_block('wireless_charger_front_on', wireless_front(True).rows(), p)
    write_block('wireless_charger_top', wireless_top().rows(), p)
    write_block('wireless_charger_top_on', wireless_top(True).rows(), p)
    write_block('wireless_charger_side', casing.side(1).rows(), p)
    write_block('wireless_charger_bottom', casing.bottom().rows(), p)
    write_block('wireless_charger_mast', wireless_mast().rows(), p)
    write_anim('block', 'wireless_charger_mast_on', [wireless_mast(t) for t in frames], p,
               frametime=WL_FRAMETIME, interpolate=True)
    write_block('wireless_charger_orb', wireless_orb().rows(), p)
    write_anim('block', 'wireless_charger_orb_on', [wireless_orb(t) for t in frames], p,
               frametime=WL_FRAMETIME, interpolate=True)
    write_anim('block', 'wireless_charger_front_glow', [wireless_front_glow(t) for t in frames], p,
               frametime=WL_FRAMETIME, interpolate=True)
    write_anim('block', 'wireless_charger_top_glow', [wireless_top_glow(t) for t in frames], p,
               frametime=WL_FRAMETIME, interpolate=True)


def write_solar():
    for mk in (1, 2, 3, 4):
        p = solar_pal(mk)
        frames = [solar_top(mk, (t + 4) % SOLAR_FRAMES) for t in range(SOLAR_FRAMES)]   # stills show the glint
        write_anim('block', f'solar_panel_mk{mk}_top', frames, p,
                   frametime=SOLAR_FRAMETIME, interpolate=True)
        write_block(f'solar_panel_mk{mk}_side', solar_side(mk).rows(), p)
    write_block('solar_panel_bottom', casing.bottom().rows(), solar_pal(1))


def write_accumulators():
    for tier in (1, 2, 3):
        p = acc_pal(tier)
        write_block(f'accumulator_{tier}_side', accumulator_side(tier).rows(), p)
        write_block(f'accumulator_{tier}_front', accumulator_front(tier).rows(), p)
        write_block(f'accumulator_{tier}_top', accumulator_top(tier).rows(), p)
    p = acc_pal()
    write_block('accumulator_bottom', casing.bottom().rows(), p)
    frames = range(ACC_FRAMES)
    for level in range(1, ACC_CELLS + 1):
        write_anim('block', f'accumulator_front_glow_{level}', [accumulator_front_glow(level, t) for t in frames], p,
                   frametime=ACC_FRAMETIME, interpolate=True)
        write_anim('block', f'accumulator_side_glow_{level}', [accumulator_side_glow(level, t) for t in frames], p,
                   frametime=ACC_FRAMETIME, interpolate=True)
        write_block(f'accumulator_top_glow_{level}', accumulator_top_glow(level).rows(), p)


def main():
    casing.write_side_glow()

    write_generator()
    write_press()
    write_block('metal_press_side', casing.side(1).rows(), casing.casing_palette(MATERIALS['copper']))
    write_block('metal_press_bottom', casing.bottom().rows(), casing.casing_palette(MATERIALS['copper']))
    write_charger()
    write_wireless()
    write_solar()
    write_accumulators()

    for tier in TESLA_CRYSTAL:
        pal = tesla_palette(tier)
        rows, _ = tesla_crystal(tier)
        write_block(f'tesla_crystal_{tier}', rows, pal)
        write_block(f'tesla_glow_{tier}', tesla_glow(tier).rows(), pal)
        write_block(f'tesla_side_{tier}', tesla_side(tier).rows(), pal)
        for kind in TESLA_PARTS:
            write_block(f'tesla_{kind}_{tier}_top', tesla_top(tier, kind).rows(), pal)
    write_item('tesla_linker', tesla_linker().rows(), P | {'b': '#8D9599', 'c': '#C4CBCE', 'd': '#5A6266'})
    write_block('winding_crank_base', crank_base().rows(), P)
    write_block('winding_crank_metal', crank_metal().rows(), P)
    write_block('winding_crank_spring', crank_spring().rows(), P)


if __name__ == '__main__':
    main()
