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
from pixelart import Canvas, grain, material, write_anim, write_block, write_still  # noqa: E402

CU, ST, BR, AU, WD, SO = '01234', 'abcde', '56789', 'ABCDE', 'uvwWX', 'mnopP'
P = {'k': '#16191B', 'K': '#0B0D0E'}
P.update(material(CU, 'copper'))
P.update(material(ST, 'steel'))
P.update(material(BR, 'brass'))
P.update(material(AU, 'gold'))
P.update(material(WD, 'wood'))
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

def press_front(lit):
    c = frame(6)
    c.rect(3, 2, 1, 12, '7').rect(12, 2, 1, 12, '6')                     # hydraulic rods
    c.set(3, 2, '9').set(12, 2, '8')
    c.plate(4, 2, 8, 5, ST, 11)                                          # ram
    c.rect(6, 3, 4, 1, 'a').rect(6, 4, 4, 1, 'd')
    c.hazard(4, 6, 8, 1, 'h', 'H')
    c.rect(4, 7, 8, 2, 'K')
    c.rect(5, 8, 6, 1, '2').set(5, 8, '4').set(10, 8, '1')              # copper sheet in the die
    c.hazard(4, 9, 8, 1, 'H', 'h')
    c.plate(4, 10, 8, 3, ST, 12)                                         # anvil
    c.rect(6, 11, 4, 1, 'a')
    if lit:
        c.rect(5, 7, 6, 1, 'Z')
    led(c, 13, 6, lit, 'G')
    return c


def press_glow(frame_no):
    c = Canvas()
    c.rect(4, 7, 8, 1, 'y').rect(5, 7, 6, 1, 'Y')
    c.set(4 + frame_no % 8, 7, 'z')
    c.set(13, 6, 'G')
    return c


def press_top():
    """Hydraulic cylinder head: a brass piston cap ringed with hazard stripes."""
    c = frame(7, panel=ST)
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.0 <= d <= 5.2:
                c.set(x, y, 'h' if int((math.atan2(y - 7.5, x - 7.5) + 3.2) * 2.6) % 2 else 'H')
    c.disc(7.5, 7.5, 3.6, '7')
    c.ring(7.5, 7.5, 2.4, 3.6, '6')
    for x, y in ((5, 5), (6, 4), (4, 6), (5, 4), (4, 5)):
        c.set(x, y, '8')
    c.set(5, 5, '9')
    c.rect(6, 6, 4, 4, 'b').bevel(6, 6, 4, 4, 'd', 'a').set(7, 7, 'e')
    return c


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


# ---------------------------------------------------------------- solar panels

def solar_top(mk):
    frame_r = CU if mk == 1 else AU
    c = Canvas()
    c.plate(0, 0, 16, 16, frame_r, 13, brushed=False)
    n = 2 if mk == 1 else 3
    size = 7 if mk == 1 else 4
    for i in range(n):
        for j in range(n):
            x, y = 1 + i * (size + 1 if mk == 2 else size), 1 + j * (size + 1 if mk == 2 else size)
            c.vgradient(x, y, size, size, 'pon' if mk == 1 else 'onnm')
            c.rect(x, y + size - 1, size, 1, 'm').rect(x + size - 1, y, 1, size, 'm')
            c.rect(x, y, size, 1, 'p').rect(x, y, 1, size, 'p')
            if mk == 1:
                c.rect(x + 3, y + 1, 1, size - 2, 'n').rect(x + 1, y + 3, size - 2, 1, 'n')   # busbars
            c.set(x + 1, y + 1, 'P')
    if mk == 1:
        c.rect(1, 7, 14, 1, frame_r[1]).rect(7, 1, 1, 14, frame_r[1]).rect(8, 1, 1, 14, frame_r[3])
        c.line(2, 5, 5, 2, 'P').line(9, 12, 12, 9, 'p')                     # sky glint
    else:
        for k in (5, 10):
            c.rect(1, k, 14, 1, frame_r[1]).rect(k, 1, 1, 14, frame_r[1])
        c.line(2, 4, 4, 2, 'P').line(7, 9, 9, 7, 'P').line(12, 14, 14, 12, 'p')
        for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
            c.set(x, y, 'E')
    return c


def solar_glow(mk):
    """Faint charge glints on the cells (the cells catch the light even at dusk)."""
    c = Canvas()
    if mk == 1:
        c.set(2, 2, 'Y').set(9, 2, 'Y').set(2, 9, 'Y').set(9, 9, 'Y')
    else:
        for i in range(3):
            for j in range(3):
                c.set(2 + i * 5, 2 + j * 5, 'Y')
        c.set(7, 7, 'z')
    return c


def solar_side(mk):
    """Only rows 0-5 show on the 6 px tall sides: frame lip, cell edge, mounting rail with bolts."""
    r = CU if mk == 1 else AU
    c = Canvas()
    c.plate(0, 0, 16, 16, ST, 14, density=0.1)
    c.rect(0, 0, 16, 2, r[3]).rect(0, 0, 16, 1, r[4]).rect(0, 2, 16, 1, r[1])
    c.rect(0, 3, 16, 3, 'b').rect(0, 3, 16, 1, 'c').rect(0, 5, 16, 1, 'a')
    for x in (1, 7, 13):
        c.set(x, 4, 'e').set(x + 1, 5, 'a')
    for x in (4, 10):
        c.rect(x, 3, 2, 3, 'K')
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


def accumulator_side_glow():
    c = Canvas()
    c.rect(7, 6, 2, 6, 'y').rect(7, 9, 1, 3, 'Y')
    return c


def accumulator_front(tier):
    r = TIER[tier]
    c = frame(30 + tier, panel=CU, frame_ramp=r)
    c.recess(4, 2, 8, 12, r, fill='K')                                   # glass gauge
    for i in range(4):
        y = 4 + i * 2 + i // 2 * 0
        c.rect(5, 3 + i * 2 + 1, 6, 1, 'Z')
    for i in range(tier):                                                # tier pips
        c.set(3, 4 + i * 2, 'G' if False else r[4])
    return c


def accumulator_front_glow(frame_no):
    """Charge cells, a soft wave running up the glass."""
    c = Canvas()
    for i in range(4):
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


# ---------------------------------------------------------------- conduit

def conduit(r):
    """Twisted cable: diagonal strands with a 4 px period so every 4x4 window of the arms reads as cable."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = (x + y) % 4
            c.set(x, y, (r[1], r[2], r[3], r[2])[k])
            if k == 2 and (x * 3 + y) % 8 == 0:
                c.set(x, y, r[4])
    for x in range(16):
        for y in (0, 15):
            c.set(x, y, r[0] if y == 15 else r[1])
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

def main():
    write_block('power_machine_side', side(), P)
    write_block('power_machine_top', hatch_top().rows(), P)
    write_block('power_machine_bottom', bottom(), P)

    write_block('combustion_generator_front', generator_front(False).rows(), P)
    write_block('combustion_generator_front_on', generator_front(True).rows(), P)
    write_anim('block', 'combustion_generator_front_glow', [generator_flames(i) for i in range(8)], P, frametime=3)
    write_block('combustion_generator_top', generator_top(False).rows(), P)
    write_block('combustion_generator_top_on', generator_top(True).rows(), P)

    write_block('metal_press_front', press_front(False).rows(), P)
    write_block('metal_press_front_on', press_front(True).rows(), P)
    write_anim('block', 'metal_press_front_glow', [press_glow(i) for i in range(8)], P, frametime=2)
    write_block('metal_press_top', press_top().rows(), P)

    write_block('charger_front', charger_front(False).rows(), P)
    write_block('charger_front_on', charger_front(True).rows(), P)
    write_anim('block', 'charger_front_glow', [charger_glow(i) for i in range(10)], P, frametime=2)
    write_block('charger_top', charger_top(False).rows(), P)
    write_block('charger_top_on', charger_top(True).rows(), P)
    write_block('charger_top_glow', charger_top_glow().rows(), P)

    for mk in (1, 2):
        write_block(f'solar_panel_mk{mk}_top', solar_top(mk).rows(), P)
        write_block(f'solar_panel_mk{mk}_side', solar_side(mk).rows(), P)
        write_block(f'solar_panel_mk{mk}_glow', solar_glow(mk).rows(), P)

    for tier in (1, 2, 3):
        write_block(f'accumulator_{tier}_side', accumulator_side(tier).rows(), P)
        write_block(f'accumulator_{tier}_front', accumulator_front(tier).rows(), P)
        write_block(f'accumulator_{tier}_top', accumulator_top(tier).rows(), P)
    write_anim('block', 'accumulator_front_glow', [accumulator_front_glow(i) for i in range(6)], P, frametime=4)
    write_still('block', 'accumulator_side_glow', accumulator_side_glow(), P)

    write_block('copper_conduit', conduit(CU).rows(), P)
    write_block('gold_conduit', conduit(AU).rows(), P)
    write_block('winding_crank_base', crank_base().rows(), P)
    write_block('winding_crank_metal', crank_metal().rows(), P)
    write_block('winding_crank_spring', crank_spring().rows(), P)


if __name__ == '__main__':
    main()
