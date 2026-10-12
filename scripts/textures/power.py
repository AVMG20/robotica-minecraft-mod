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
from pixelart import MATERIALS, Canvas, grain, hash01, material, write_anim, write_block, write_item, write_still  # noqa: E402
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

def generator_front(lit):
    """Big firebox door with grate bars, a vent strip and a status LED above."""
    c = frame(4)
    c.draw(3, 3, ['KdKdKdKd', '3.3.3.3.'])                               # vent slots
    led(c, 12, 3, lit, 'G')
    led(c, 11, 3, not lit, 'R')
    c.rect(3, 5, 10, 9, 'c').bevel(3, 5, 10, 9, 'd', 'a')                # door frame
    c.set(3, 5, 'e')
    c.rect(4, 6, 8, 6, 'K')
    if lit:
        c.vgradient(4, 6, 8, 6, 'XXXxxf')
    for x in range(5, 12, 2):                                            # grate bars
        c.rect(x, 6, 1, 6, 'b' if not lit else 'a')
        c.set(x, 6, 'c')
    c.rect(4, 12, 8, 1, 'b')
    c.rect(6, 12, 4, 1, '7').set(6, 12, '8')                             # brass handle
    return c


def generator_flames(frame_no):
    """Overlay of the burning fire box: flame tongues behind the grate bars, flickering."""
    c = Canvas()
    for x in range(4, 12, 2):
        h = 2 + int(4.4 * (0.5 + 0.5 * math.sin(frame_no * 1.7 + x * 1.3)))
        for i in range(h):
            y = 11 - i
            ch = 'O' if i == 0 else 'F' if i < 2 else 'f' if i < h - 1 else 'x'
            c.set(x, y, ch)
    c.set(4 + (frame_no * 3) % 8, 6 + frame_no % 2, 'F')                 # sparks
    for x in range(5, 12, 2):                                            # glow on the bar feet
        c.set(x, 11, 'f')
    c.set(12, 3, 'G')                                                    # running LED
    return c


def generator_top(lit):
    """Exhaust fan: round grille with a cross brace and hub, the gaps glow faintly when burning."""
    c = frame(5, panel=ST)
    gap = 'X' if lit else 'K'
    c.disc(7.5, 7.5, 5.0, 'a')
    c.disc(7.5, 7.5, 4.3, gap)
    c.ring(7.5, 7.5, 2.4, 3.1, 'c')
    c.rect(7, 3, 2, 10, 'c').rect(3, 7, 10, 2, 'c')
    c.rect(7, 3, 1, 10, 'd').rect(3, 7, 10, 1, 'd')
    c.rect(6, 6, 4, 4, 'b').bevel(6, 6, 4, 4, 'e', 'a')
    c.ring(7.5, 7.5, 4.3, 5.0, 'b')
    for x, y in ((4, 3), (3, 4), (5, 2)):
        c.set(x, y, 'd')
    c.dither(2, 2, 12, 12, 'b', 0.2, only='c')                            # soot
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

WAVE_ARCS = ((2.2, 3.0), (4.2, 5.0), (6.2, 7.0))


def wave_pixels(r0, r1):
    """Pixels of a wave arc opening upwards from the emitter dot at (7.5, 10.5) on the front."""
    out = []
    for y in range(3, 12):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 10.5)
            if r0 <= d < r1 and y <= 10 and abs(x - 7.5) <= (10.5 - y) + 1.5:
                out.append((x, y))
    return out


def wireless_front(lit):
    """Dark window with three wave arcs over an emitter dot, a status LED below."""
    c = frame(12, panel=ST)
    c.recess(3, 3, 10, 9, ST, fill='K')
    for r0, r1 in WAVE_ARCS:
        for x, y in wave_pixels(r0, r1):
            c.set(x, y, 'Z')
    c.disc(7.5, 10.5, 1.0, 'y')
    c.rect(4, 13, 8, 1, 'K')
    led(c, 7, 13, lit, 'G')
    led(c, 8, 13, lit, 'G')
    return c


def wireless_glow(frame_no):
    """The arcs light up one after another, outward, like a signal."""
    c = Canvas()
    for i, (r0, r1) in enumerate(WAVE_ARCS):
        bright = (frame_no - i) % 4 == 0
        dim = (frame_no - i) % 4 == 1
        if not (bright or dim):
            continue
        for x, y in wave_pixels(r0, r1):
            c.set(x, y, 'z' if bright else 'y')
    c.disc(7.5, 10.5, 1.0, 'Y')
    c.set(7, 13, 'G').set(8, 13, 'G')
    return c


def wireless_top():
    """Emitter plate seen from above: rings of copper windings round the mast socket."""
    c = frame(14, panel=ST)
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if 2.0 <= d <= 5.0:
                c.set(x, y, '3' if int(d * 1.5) % 2 == 0 else '1')
    c.ring(7.5, 7.5, 4.6, 5.3, '0')
    c.disc(7.5, 7.5, 1.6, 'K')
    return c


def wireless_mast():
    """Brass mast for the emitter rod (16x16, the model samples a thin strip)."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, BR, 5, vertical=True, density=0.3)
    for y in range(0, 16, 4):
        c.rect(0, y, 16, 1, '6')
    return c


# ---------------------------------------------------------------- solar panels

SOLAR_FRAME = {1: CU, 2: AU, 3: MG, 4: SL}


def solar_top(mk):
    frame_r = SOLAR_FRAME[mk]
    c = Canvas()
    c.plate(0, 0, 16, 16, frame_r, 13, brushed=False)
    if mk == 1:
        for i in range(2):
            for j in range(2):
                x, y = 1 + i * 7, 1 + j * 7
                c.vgradient(x, y, 7, 7, 'pon')
                c.rect(x, y + 6, 7, 1, 'm').rect(x + 6, y, 1, 7, 'm')
                c.rect(x, y, 7, 1, 'p').rect(x, y, 1, 7, 'p')
                c.rect(x + 3, y + 1, 1, 5, 'n').rect(x + 1, y + 3, 5, 1, 'n')   # busbars
                c.set(x + 1, y + 1, 'P')
        c.rect(1, 7, 14, 1, frame_r[1]).rect(7, 1, 1, 14, frame_r[1]).rect(8, 1, 1, 14, frame_r[3])
        c.line(2, 5, 5, 2, 'P').line(9, 12, 12, 9, 'p')                     # sky glint
        return c
    # Mk2-Mk4: 3x3 cells of 4 px; Mk3 dark cells with hot busbars, Mk4 violet-black cells around a null core
    cell = {2: 'onnm', 3: 'nnmm', 4: 'nm(('}[mk]
    edge_lo, edge_hi, glint = ('m', 'p', 'P') if mk < 4 else ('(', ']', '{')
    for i in range(3):
        for j in range(3):
            x, y = 1 + i * 5, 1 + j * 5
            c.vgradient(x, y, 4, 4, cell)
            c.rect(x, y + 3, 4, 1, edge_lo).rect(x + 3, y, 1, 4, edge_lo)
            c.rect(x, y, 4, 1, edge_hi).rect(x, y, 1, 4, edge_hi)
            if mk == 3:
                c.rect(x + 1, y + 2, 2, 1, '$')                              # heat busbar
            c.set(x + 1, y + 1, glint)
    for k in (5, 10):
        c.rect(1, k, 14, 1, frame_r[1]).rect(k, 1, 1, 14, frame_r[1])
    if mk == 4:
        c.rect(6, 6, 4, 4, '(').rect(7, 7, 2, 2, '[').set(7, 7, '{')        # null core in the middle cell
    c.line(2, 4, 4, 2, glint).line(12, 14, 14, 12, edge_hi)
    if mk != 4:
        c.line(7, 9, 9, 7, 'P')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, frame_r[4] if mk < 4 else '{')
    return c


def solar_glow(mk):
    """Faint charge glints on the cells (the cells catch the light even at dusk)."""
    c = Canvas()
    if mk == 1:
        c.set(2, 2, 'Y').set(9, 2, 'Y').set(2, 9, 'Y').set(9, 9, 'Y')
        return c
    spark = {2: 'Y', 3: 'F', 4: '{'}[mk]
    for i in range(3):
        for j in range(3):
            c.set(2 + i * 5, 2 + j * 5, spark)
    c.set(7, 7, {2: 'z', 3: 'O', 4: '{'}[mk])
    if mk == 3:
        for i in range(3):
            for j in range(3):
                c.set(2 + i * 5, 3 + j * 5, '%')
    if mk == 4:
        c.set(8, 7, ']').set(7, 8, ']').set(8, 8, '[')
    return c


def solar_side(mk):
    """Only rows 0-5 show on the 6 px tall sides: frame lip, cell edge, mounting rail with bolts."""
    r = SOLAR_FRAME[mk]
    c = Canvas()
    c.plate(0, 0, 16, 16, ST, 14, density=0.1)
    c.rect(0, 0, 16, 2, r[3]).rect(0, 0, 16, 1, r[4]).rect(0, 2, 16, 1, r[1])
    c.rect(0, 3, 16, 3, 'b').rect(0, 3, 16, 1, 'c').rect(0, 5, 16, 1, 'a')
    for x in (1, 7, 13):
        c.set(x, 4, 'e').set(x + 1, 5, 'a')
    for x in (4, 10):
        c.rect(x, 3, 2, 3, 'K')
    if mk >= 3:
        band = r if mk == 3 else NV
        c.rect(7, 3, 2, 3, band[2]).set(7, 3, band[3])                      # tier band
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
    write_block('combustion_generator_front', generator_front(False).rows(), P)
    write_block('combustion_generator_front_on', generator_front(True).rows(), P)
    write_anim('block', 'combustion_generator_front_glow', [generator_flames(i) for i in range(8)], P, frametime=3)
    write_block('combustion_generator_top', generator_top(False).rows(), P)
    write_block('combustion_generator_top_on', generator_top(True).rows(), P)


def write_charger():
    write_block('charger_front', charger_front(False).rows(), P)
    write_block('charger_front_on', charger_front(True).rows(), P)
    write_anim('block', 'charger_front_glow', [charger_glow(i) for i in range(10)], P, frametime=2)
    write_block('charger_top', charger_top(False).rows(), P)
    write_block('charger_top_on', charger_top(True).rows(), P)
    write_block('charger_top_glow', charger_top_glow().rows(), P)


def write_wireless():
    write_block('wireless_charger_front', wireless_front(False).rows(), P)
    write_block('wireless_charger_front_on', wireless_front(True).rows(), P)
    write_anim('block', 'wireless_charger_front_glow', [wireless_glow(i) for i in range(8)], P, frametime=3)
    write_block('wireless_charger_top', wireless_top().rows(), P)
    write_block('wireless_charger_mast', wireless_mast().rows(), P)


def write_solar():
    for mk in (1, 2, 3, 4):
        write_block(f'solar_panel_mk{mk}_top', solar_top(mk).rows(), P)
        write_block(f'solar_panel_mk{mk}_side', solar_side(mk).rows(), P)
        write_block(f'solar_panel_mk{mk}_glow', solar_glow(mk).rows(), P)


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
