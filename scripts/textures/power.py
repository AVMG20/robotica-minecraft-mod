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
# between two cyan hydraulic columns (model: power_models.press_model). The bay texture holds the back wall in
# 4..11 x 3..12 and the bay's ceiling (rows 0-1), floor (rows 14-15) and side walls (cols 0-1, 14-15) around it.
# Working, one 32 tick stroke: an ingot slides onto the die, the ram drops on it, flattens it to a plate in a cyan
# flash, holds, rises slowly and the plate slides out. Bay, glow, columns and top share the loop.

PRESS_TICKS = 32
PRESS_PAL = casing.casing_palette(MATERIALS['copper'], **{
    '6': '#8E99A2', '7': '#D6DEE3',                                       # ram rod
})
RAM = [0] * 8 + [1, 1, 2, 3] + [3] * 6 + [2, 2, 2, 1, 1, 1, 1] + [0] * 7    # ram drop (px) per tick
IMPACT = RAM.index(3)


def press_slide(t):
    """Conveyor offset during the dwell: the plate leaves to the right, the next ingot comes in from the left."""
    return min(t, 8)


def press_bay(t, lit):
    """Back wall and bay walls at tick t of the stroke (idle: ram up, an ingot on the die)."""
    c = Canvas()
    c.rect(4, 3, 8, 10, 'k')
    c.rect(4, 3, 8, 1, 'b').rect(5, 3, 6, 1, 'c').rect(6, 4, 4, 1, 'a')  # crown and rod gland
    c.rect(4, 12, 8, 1, 'd').set(4, 12, 'e')                             # die bed
    p = RAM[t] if lit else 0
    if p:
        c.rect(7, 5, 2, p, '6').rect(7, 5, 1, p, '7')                    # rod
    y = 5 + p                                                             # ram: bevelled head, guide shoes, die face
    c.rect(4, y, 8, 1, 'e').rect(4, y + 1, 8, 1, 'd').rect(5, y + 2, 6, 1, 'u')
    c.rect(4, y, 1, 3, 'h').rect(11, y, 1, 3, 'g').set(4, y, 'i').set(11, y + 2, 'f').set(4, y + 2, 'g')
    s = press_slide(t) if lit else 8
    if lit and IMPACT <= t:
        c.rect(4, 11, 8, 1, 'h').set(4, 11, 'i').set(11, 11, 'g')         # flattened plate
    else:
        if lit and s < 8:
            c.rect(4 + s, 11, 8 - s, 1, 'h')                              # last plate leaving
        for x in range(s - 2, s + 2):                                     # ingot coming in
            if 4 <= x < 12:
                c.set(x, 10, 'i' if x < s + 1 else 'h').set(x, 11, 'h' if x < s + 1 else 'g')
    c.rect(3, 0, 10, 2, 'a').rect(3, 1, 10, 1, 'b')                      # ceiling (row 0 at the back)
    c.rect(3, 14, 10, 2, 'c').rect(3, 14, 10, 1, 'd')                    # floor: the die bed's front (row 14)
    c.rect(0, 3, 2, 10, 'b').rect(0, 3, 1, 10, 'c')                      # side walls (col 0 and 15 at the front)
    c.rect(14, 3, 2, 10, 'a').rect(15, 3, 1, 10, 'b')
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
    """Casing face with the press bay between two hydraulic columns, and the status lamp."""
    c = casing.shell(1)
    c.rect(4, 3, 8, 10, '.')
    for x in (3, 12):
        c.rect(x, 3, 1, 10, 'v' if lit else 'u').set(x, 3, 'h').set(x, 12, 'g')
    c.rect(7, 1, 2, 1, 'L' if lit else 'l')
    return c


def press_front_glow(t):
    """Lamp, and the columns: pressure rushes down them on the drop, flashes on impact, drains up on the return."""
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
    return c


def press_top():
    """Hydraulic cylinder head: a round graphite cap in a copper ring around a cyan pressure core."""
    c = casing.shell(1)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 4.3,
            lambda x, y: 'i' if x + y < 12 else 'h' if x + y < 17 else 'g')     # copper ring
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 3.2,
            lambda x, y: 'd' if x + y < 13 else 'c')                             # cap
    c.shape(lambda x, y: math.hypot(x - 7.5, y - 7.5) <= 2.0, lambda x, y: 'a')  # core socket
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

def charger_front(lit):
    """Charging bay between two coil stacks, a lightning mark on the back wall, a charge meter below."""
    c = frame(8)
    c.recess(3, 3, 10, 8, CU, fill='K')
    for x in (3, 11):                                                    # coil stacks
        c.rect(x, 3, 2, 8, '1')
        for y in range(3, 11, 2):
            c.rect(x, y, 2, 1, '3').set(x, y, '4')
    bolt = ((8, 4), (7, 5), (6, 6), (7, 6), (8, 6), (9, 6), (8, 7), (7, 8), (7, 9))
    for x, y in bolt:
        c.set(x, y, 'Z')
    c.rect(4, 12, 8, 1, 'K')                                             # charge meter
    for i in range(4):
        c.rect(4 + i * 2, 12, 1, 1, 'Z')
    led(c, 12, 12, lit, 'G')
    return c


def charger_glow(frame_no):
    c = Canvas()
    bolt = ((8, 4), (7, 5), (6, 6), (7, 6), (8, 6), (9, 6), (8, 7), (7, 8), (7, 9))
    for i, (x, y) in enumerate(bolt):
        c.set(x, y, 'z' if (i - frame_no) % 9 < 2 else 'Y')
    for x in (4, 11):                                                    # coil edges glow
        for y in range(3, 11, 2):
            if (y // 2 + frame_no) % 4 == 0:
                c.set(x, y, 'Y')
            else:
                c.set(x, y, 'y')
    for i in range(4):
        if i <= frame_no % 5:
            c.set(4 + i * 2, 12, 'Y')
    c.set(12, 12, 'G')
    return c


def charger_top(lit):
    """Induction coil seen from above: concentric copper windings round a steel core."""
    c = frame(10, panel=ST)
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 5.0:
                c.set(x, y, '3' if int(d * 1.6) % 2 == 0 else '1')
    c.ring(7.5, 7.5, 4.6, 5.3, '0')
    for x, y in ((4, 5), (5, 4), (6, 3)):
        c.set(x, y, '4')
    c.disc(7.5, 7.5, 1.5, 'Z' if not lit else 'y')
    return c


def charger_top_glow():
    c = Canvas()
    c.disc(7.5, 7.5, 1.5, 'Y')
    c.set(7, 7, 'z')
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

def accumulator_side(tier):
    r = TIER[tier]
    c = frame(20 + tier, panel=CU, frame_ramp=r)
    for y in range(4, 12, 2):                                            # heat sink fins
        c.rect(4, y, 8, 1, '3').rect(4, y + 1, 8, 1, '0')
        c.set(4, y, '4')
    c.rect(7, 3, 2, 10, 'K').rect(7, 4, 2, 8, 'Z')                       # sight glass
    c.set(7, 4, 'y')
    return c


# The front gauge has ACC_CELLS charge cells; the block state `charge` (0-ACC_CELLS) says how many are lit.
ACC_CELLS = 5
ACC_GLASS = [0, 2, 3, 5, 6, 8]                                           # sight glass rows lit per charge level


def accumulator_side_glow(level):
    """Sight glass filled from the bottom (8 rows, y 4-11) to the charge level."""
    c = Canvas()
    h = ACC_GLASS[level]
    if h:
        c.rect(7, 12 - h, 2, h, 'y').rect(7, 12 - h, 1, h, 'Y')
    return c


def accumulator_front(tier):
    r = TIER[tier]
    c = frame(30 + tier, panel=CU, frame_ramp=r)
    c.recess(4, 2, 8, 12, r, fill='K')                                   # glass gauge
    for i in range(ACC_CELLS):                                           # dark cells, bottom (y 12) to top (y 4)
        c.rect(5, 12 - i * 2, 6, 1, 'Z')
    for i in range(tier):                                                # tier pips
        c.set(3, 4 + i * 2, r[4])
    return c


def accumulator_front_glow(level, frame_no):
    """The lowest `level` charge cells lit, a soft wave running up the lit ones."""
    c = Canvas()
    for i in range(level):
        y = 12 - i * 2
        k = (i - frame_no) % 6
        c.rect(5, y, 6, 1, 'z' if k == 0 else 'Y' if k == 1 else 'y')
        c.set(5, y, 'Y' if k > 1 else 'z')
    return c


def accumulator_top(tier):
    """Two terminal posts (+ brass in a red collar, - steel in a black one), polarity marks and tier pips."""
    r = TIER[tier]
    c = frame(40 + tier, panel=r, frame_ramp=ST if tier != 2 else CU)
    c.draw(3, 4, ['.RRR.', 'R989R', 'R878R', 'R776R', '.r55.'])      # + post, red collar
    c.draw(8, 4, ['.KKK.', 'Kedck', 'Kdcbk', 'Kcbbk', '.kaa.'])      # - post, black collar
    c.draw(3, 10, ['.R.', 'RRR', '.R.'])                                 # +
    c.draw(9, 11, ['KKK'])                                               # -
    for i in range(tier):
        c.set(12, 4 + i * 2, r[4]).set(12, 5 + i * 2, r[1])
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


def tesla_tip():
    """Full-bright energy ball: white hot core fading to cyan."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, 'z' if d < 1.6 else 'Y' if d < 3.2 else 'y' if d < 5.5 else 'Z')
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
    write_block('charger_front', charger_front(False).rows(), P)
    write_block('charger_front_on', charger_front(True).rows(), P)
    write_anim('block', 'charger_front_glow', [charger_glow(i) for i in range(10)], P, frametime=2)
    write_block('charger_top', charger_top(False).rows(), P)
    write_block('charger_top_on', charger_top(True).rows(), P)
    write_block('charger_top_glow', charger_top_glow().rows(), P)


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
        write_block(f'accumulator_{tier}_side', accumulator_side(tier).rows(), P)
        write_block(f'accumulator_{tier}_front', accumulator_front(tier).rows(), P)
        write_block(f'accumulator_{tier}_top', accumulator_top(tier).rows(), P)
    for level in range(1, ACC_CELLS + 1):
        write_anim('block', f'accumulator_front_glow_{level}', [accumulator_front_glow(level, i) for i in range(6)], P, frametime=4)
        write_still('block', f'accumulator_side_glow_{level}', accumulator_side_glow(level), P)


def main():
    # the generator, chargers and solar panels sit in the processing machines' shared casing (Mk1 copper)
    write_block('power_machine_side', casing.side(1).rows(), casing.casing_palette(MATERIALS['copper']))
    write_block('power_machine_bottom', casing.bottom().rows(), casing.casing_palette(MATERIALS['copper']))
    casing.write_side_glow()

    write_generator()
    write_press()
    write_block('metal_press_side', casing.side(1).rows(), casing.casing_palette(MATERIALS['copper']))
    write_block('metal_press_bottom', casing.bottom().rows(), casing.casing_palette(MATERIALS['copper']))
    write_charger()
    write_wireless()
    write_solar()
    write_accumulators()

    write_block('tesla_coil_tip', tesla_tip().rows(), P)
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
