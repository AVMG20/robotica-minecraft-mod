"""Processing module textures: Grinder and Electric Furnace (Mk1-Mk4), dusts, grinding balls.
Run: python3 scripts/textures/processing.py   (writes textures; models are written by scripts/data/processing_models.py)

Machine faces follow the power module's construction (top-left light, 2 px frame with riveted corner brackets around a
recessed panel) with a steel panel and a frame in the tier's metal: Mk1 copper, Mk2 gold, Mk3 magma, Mk4 null. Tier
pips sit on the sides. Grinder front: two toothed crushing rollers behind a dark throat, sparks on the glow overlay
while working. Electric Furnace front: a heating chamber with coil rows that glow orange while working.
Items use the shared item style (dark outline, auto-shaded from the top-left).
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, grain, material, write_anim, write_block, write_item  # noqa: E402

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


# ---------------------------------------------------------------- shared faces

def side(tier):
    """Louvred side panel with the tier pips (one per Mk) in the frame's metal."""
    f = TIER[tier]
    c = frame(tier, 10 + tier)
    c.recess(4, 4, 8, 8, ST, fill='b')
    for y in range(5, 11, 2):
        c.rect(5, y, 6, 1, 'K').rect(5, y + 1, 6, 1, 'd')
    for i in range(tier):
        c.set(4 + i * 2, 12, f[4]).set(5 + i * 2, 12, f[1])
    return c


def bottom():
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, 9, density=0.25)
    c.rect(2, 2, 12, 1, 'b').rect(2, 2, 1, 12, 'b').rect(2, 13, 12, 1, 'd').rect(13, 2, 1, 12, 'd')
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):
        c.rect(x, y, 3, 3, 'a').set(x + 1, y + 1, 'k')
    return c


# ---------------------------------------------------------------- grinder

def roller(c, cx, cy, r, phase, lit):
    """A toothed crushing roller seen end-on."""
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > r + 0.6:
                continue
            ang = (math.degrees(math.atan2(dy, dx)) + phase) % 45
            if d > r - 0.6:
                if ang < 22:
                    c.set(x, y, 'c' if dx + dy < 0 else 'b')
                continue
            c.set(x, y, 'd' if dx + dy < -1 else 'b' if dx + dy > 1 else 'c')
    c.set(int(cx), int(cy), 'a' if not lit else 'x')


def grinder_front(tier, lit):
    c = frame(tier, 20 + tier)
    c.recess(3, 3, 10, 10, ST, fill='K')                                  # throat
    roller(c, 5.5, 8.0, 2.6, 0, lit)
    roller(c, 10.5, 8.0, 2.6, 22, lit)
    c.rect(3, 3, 10, 1, '6').rect(3, 12, 10, 1, '7')                      # feed lip and chute
    c.hazard(4, 12, 8, 1, 'h', 'H')
    if lit:
        c.set(8, 7, 'x').set(8, 9, 'x')
    led(c, 12, 2, lit)
    return c


def grinder_glow(frame_no):
    """Sparks flying out of the bite between the rollers."""
    c = Canvas()
    c.set(8, 8, 'F')
    c.set(8, 7 + frame_no % 3, 'O')
    for i in range(3):
        k = (frame_no + i * 3) % 8
        x = 8 + (k - 4) // 2
        y = 9 + k // 2
        if 3 < y < 13 and 3 < x < 13:
            c.set(x, y, 'f' if i else 'F')
    c.set(12, 2, 'G')
    return c


def grinder_top(tier):
    """Feed hopper: a square funnel with a grate over the throat."""
    c = frame(tier, 30 + tier)
    c.recess(3, 3, 10, 10, ST, fill='a')
    c.recess(5, 5, 6, 6, ST, fill='K')
    for x in range(5, 11, 2):
        c.rect(x, 5, 1, 6, 'b')
    c.rect(5, 7, 6, 1, 'b')
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        c.set(x, y, 'c')
    return c


# ---------------------------------------------------------------- electric furnace

def furnace_front(tier, lit):
    c = frame(tier, 40 + tier)
    c.recess(3, 3, 10, 8, ST, fill='K')                                   # chamber window
    for y in (5, 7, 9):
        c.rect(4, y, 8, 1, 'f' if lit else '7')
        if lit:
            c.set(4 + (y * 3) % 8, y, 'F')
    c.rect(3, 12, 10, 1, '6').set(3, 12, '8')                             # door handle rail
    c.rect(6, 12, 4, 1, 'd')
    led(c, 12, 2, lit)
    return c


def furnace_glow(frame_no):
    """The heating coils pulse along their length."""
    c = Canvas()
    for i, y in enumerate((5, 7, 9)):
        for x in range(4, 12):
            k = (x + frame_no + i * 2) % 8
            c.set(x, y, 'O' if k == 0 else 'F' if k < 3 else 'f')
    c.set(12, 2, 'G')
    return c


def furnace_top(tier):
    """Heat vents: a perforated grille with a hot-plate symbol."""
    c = frame(tier, 50 + tier)
    c.recess(3, 3, 10, 10, ST, fill='b')
    c.grille(4, 4, 8, 8, 'K', 'd')
    c.ring(7.5, 7.5, 2.0, 2.8, 'x')
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


def main():
    write_block('processing_machine_bottom', bottom().rows(), P)
    for tier in (1, 2, 3, 4):
        write_block(f'processing_mk{tier}_side', side(tier).rows(), P)
        write_block(f'grinder_mk{tier}_front', grinder_front(tier, False).rows(), P)
        write_block(f'grinder_mk{tier}_front_on', grinder_front(tier, True).rows(), P)
        write_block(f'grinder_mk{tier}_top', grinder_top(tier).rows(), P)
        write_block(f'electric_furnace_mk{tier}_front', furnace_front(tier, False).rows(), P)
        write_block(f'electric_furnace_mk{tier}_front_on', furnace_front(tier, True).rows(), P)
        write_block(f'electric_furnace_mk{tier}_top', furnace_top(tier).rows(), P)
    write_anim('block', 'grinder_front_glow', [grinder_glow(i) for i in range(8)], P, frametime=2)
    write_anim('block', 'electric_furnace_front_glow', [furnace_glow(i) for i in range(8)], P, frametime=3)

    for metal in DUST_COLORS:
        rows, pal = dust(metal)
        write_item(f'{metal}_dust', rows, pal)
    for name in BALL_MATERIAL:
        rows, pal = balls(name)
        write_item(f'{name}_grinding_balls', rows, pal)


if __name__ == '__main__':
    main()
