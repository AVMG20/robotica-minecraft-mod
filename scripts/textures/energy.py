"""Energy module textures (big power): Capacitor Bank, Tesla Spire, Core Reactor, Ring Collider, Strange Matter.
Run: python3 scripts/textures/energy.py   (writes textures/block/*.png and textures/item/strange_matter.png; models
come from scripts/data/energy_models.py)

Four families, one construction (top-left light, bevelled plates, riveted corners, glow on separate overlays):
- Core Reactor: dark graphite plating with hazard amber trim and a green glow; amplifiers glow in their tier colour.
- Capacitor Bank: slate blue plating with cyan energy glow; capacitors show their cell metal, coils their windings.
- Tesla Spire: brushed steel with copper windings and a cyan arc glow.
- Ring Collider: null violet plating with a white-hot plasma glow; segments are a glass beam pipe between magnets.
Casing faces tile seamlessly: the frame of a casing is drawn as half-width seams so walls read as one surface.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import Canvas, grain, material, write_anim, write_block  # noqa: E402

DK, ST, SL, NU = 'abcde', 'fghij', 'lmnop', 'qrstu'
CU, RD, TE, CY, GR, PU, AM = '01234', '56789', 'ABCDE', 'FGHIJ', 'KLMNO', 'PQRST', 'UVWXY'
MG = '*+=~/'
P = {'k': '#121417', 'z': '#08090B', '.': '#00000000'}
P.update(material(DK, 'dark'))
P.update(material(ST, 'steel'))
P.update(material(SL, 'slate'))
P.update({ch: col for ch, col in zip(NU, ('#100C1C', '#1F1832', '#30264A', '#4A3E6C', '#7C6EA8'))})  # dusk violet plating
P.update(material(CU, 'copper'))
P.update(material(RD, 'red'))
P.update(material(TE, 'teal'))
P.update(material(CY, 'cyan'))
P.update(material(GR, 'green'))
P.update(material(PU, 'purple'))
P.update(material(AM, 'amber'))
P.update(material(MG, 'magma'))
P.update(grain('vw', 'dark', 0.22))
P.update(grain('xy', 'slate', 0.22))
P.update({'!': '#2A2142', '#': '#382D55'})  # subtle grain of the violet plating
P.update(grain('$%', 'steel', 0.22))
P.update({
    '(': '#6CF09A38', ')': '#D8FFE4A8',      # reactor glass tint and glint
    '[': '#5FE3F038', ']': '#E6FAFFA8',      # bank glass tint and glint
    '{': '#9CF2FF', '}': '#D9FBFF', '<': '#5CC8E6', '>': '#2C7FA8', '^': '#F4FFFF',  # cryo ice
    '&': '#FFFFFF', '@': '#FFE6FF',          # white-hot plasma
    '-': '#7FE8FF70', '_': '#E6FAFFC8',      # accelerator pipe tint and glint
    ':': '#B48CFF70', ';': '#F0E2FFC8',      # resonant pipe tint and glint
    '|': '#3A8FA0C8', '?': '#7A50C0C8',      # pipe walls (accelerator, resonant)
    '`': '#9FF0FF60',                        # spire crown orb glass
})
GRAIN = {DK: 'vw', SL: 'xy', NU: '!#', ST: '$%'}

ACCENT = {DK: AM, SL: CY, NU: PU, ST: CU}
GLOW = {DK: GR, SL: CY, NU: PU, ST: CY}


def plate(c, x, y, w, h, r, seed=0, **kw):
    return c.plate(x, y, w, h, r, seed, grain=GRAIN.get(r), **kw)


# ---------------------------------------------------------------- shared construction

def casing(r, seed=0):
    """Seamless casing: brushed plate, half seams on the edges (two blocks side by side show one full seam),
    riveted corner gussets and a recessed centre panel."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, r, seed, bevel=False, density=0.28)
    c.rect(0, 0, 16, 1, r[3]).rect(0, 0, 1, 16, r[3])                   # lit top/left seam half
    c.rect(0, 15, 16, 1, r[0]).rect(15, 0, 1, 16, r[0])                  # dark bottom/right seam half
    c.set(0, 15, r[1]).set(15, 0, r[1])
    for x, y in ((1, 1), (12, 1), (1, 12), (12, 12)):                    # corner gussets
        c.rect(x, y, 3, 3, r[3])
        c.rect(x + 1, y + 2, 2, 1, r[1]).rect(x + 2, y + 1, 1, 2, r[1])
        c.set(x + 1, y + 1, r[4])
    c.recess(5, 5, 6, 6, r, fill=r[2])                                   # centre panel
    plate(c, 6, 6, 4, 4, r, seed + 5, bevel=False, density=0.3)
    return c


def hazard_trim(c, a):
    """Amber/black hazard stripes along the bottom edge (reactor family)."""
    for x in range(1, 15):
        c.set(x, 14, a[2] if (x // 2) % 2 == 0 else 'k')
    return c


def glass(r, tint, glint, seed=0):
    """Translucent pane in a thin frame of the family's metal, with streaked glints; edges tile into a grid."""
    c = Canvas()
    c.rect(0, 0, 16, 16, tint)
    c.frame(0, 0, 16, 16, r[1])
    c.rect(0, 0, 16, 1, r[3]).rect(0, 0, 1, 16, r[3])
    c.set(0, 0, r[4]).set(15, 15, r[0])
    c.line(3, 10, 10, 3, glint).line(4, 10, 10, 4, glint)
    c.line(10, 13, 13, 10, glint)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, r[2])
    return c


def screen(c, x, y, w, h, r):
    c.screen(x, y, w, h, r, glass='z')
    return c


# ---------------------------------------------------------------- controllers

def controller_front(r, state):
    """Display panel with a bar graph and three status lamps; state: off / formed / on."""
    c = casing(r, 11)
    c.rect(2, 2, 12, 12, r[1]).bevel(2, 2, 12, 12, r[3], r[0])
    screen(c, 3, 3, 10, 7, r)
    g = GLOW[r]
    if state != 'off':
        heights = (3, 5, 4, 2, 5) if state == 'on' else (1, 2, 1, 1, 2)
        for i, h in enumerate(heights):
            c.rect(4 + i * 2, 9 - h, 1, h, g[1])
    lamps = {'off': ('5', 'k', 'k'), 'formed': ('k', g[1], 'k'), 'on': ('k', g[1], ACCENT[r][2])}[state]
    for i, ch in enumerate(lamps):
        c.set(4 + i * 3, 12, ch).set(5 + i * 3, 12, 'k' if ch == 'k' else ch)
    if r == DK:
        hazard_trim(c, AM)
    return c


def controller_glow(r, state):
    c = Canvas()
    if state == 'off':
        c.set(4, 12, '7').set(5, 12, '7')
        return c
    g = GLOW[r]
    heights = (3, 5, 4, 2, 5) if state == 'on' else (1, 2, 1, 1, 2)
    for i, h in enumerate(heights):
        c.rect(4 + i * 2, 9 - h, 1, h, g[3])
        c.set(4 + i * 2, 9 - h, g[4])
    c.set(7, 12, g[3]).set(8, 12, g[3])
    if state == 'on':
        c.set(10, 12, ACCENT[r][3]).set(11, 12, ACCENT[r][3])
    return c


# ---------------------------------------------------------------- ports

def power_port(r):
    """Copper terminal block with a bolt mark: FE out."""
    c = casing(r, 21)
    c.recess(3, 3, 10, 10, r, fill='k')
    c.ring(7.5, 7.5, 3.4, 4.3, CU[2]).ring(7.5, 7.5, 3.4, 3.8, CU[3])  # copper terminal ring
    c.draw(6, 4, ['..WX', '.WX.', 'WXXW', '.XW.', 'XW..', 'W...'])       # bolt
    if r == DK:
        hazard_trim(c, AM)
    return c


def power_port_glow(r):
    c = Canvas()
    g = GLOW[r]
    c.ring(7.5, 7.5, 3.7, 4.4, g[2])
    for x, y in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):
        c.set(x, y, g[3])
    return c


def access_port(r):
    """Hatch with a funnel grid: items in and out."""
    c = casing(r, 31)
    c.recess(3, 3, 10, 10, r, fill=r[0])
    c.grille(4, 4, 8, 8, 'z', r[3])
    c.rect(3, 7, 10, 2, ST[2]).rect(3, 7, 10, 1, ST[3])                  # cross bar
    c.rect(7, 3, 2, 10, ST[2]).rect(7, 3, 1, 10, ST[3])
    c.disc(7.5, 7.5, 1.4, AM[2])
    if r == DK:
        hazard_trim(c, AM)
    return c


def bank_port(output):
    """Bank Port: arrow pointing in (input, green) or out (output, amber), cyan rails."""
    r = SL
    c = casing(r, 41 if output else 42)
    c.recess(3, 3, 10, 10, r, fill='k')
    col = AM[1] if output else GR[1]
    if output:
        c.draw(4, 4, ['....11..', '.....11.', '11111111', '11111111', '.....11.', '....11..'])
    else:
        c.draw(4, 4, ['..11....', '.11.....', '11111111', '11111111', '.11.....', '..11....'])
    c.replace('1', col, 4, 4, 8, 6)
    c.rect(4, 11, 8, 1, CY[0])
    return c


def bank_port_glow(output):
    c = Canvas()
    col = AM[3] if output else GR[3]
    if output:
        c.draw(4, 4, ['....11..', '.....11.', '11111111', '11111111', '.....11.', '....11..'])
    else:
        c.draw(4, 4, ['..11....', '.11.....', '11111111', '11111111', '.11.....', '..11....'])
    c.replace('1', col)
    c.rect(4, 11, 8, 1, CY[2])
    return c


# ---------------------------------------------------------------- core reactor inside

def sprite(rows, ramp):
    """Rows of '1'..'4' (ramp steps) and '.', mapped onto a ramp."""
    return [''.join(ramp[int(ch)] if ch.isdigit() else ch for ch in row) for row in rows]


CRYSTAL = ['..43..', '.4332.', '433321', '433221', '.3221.', '..21..']
AMP = {1: RD, 2: MG, 3: CY}


def amplifier_side(tier):
    """Modulator plate: graphite collars around a window holding a crystal in the tier colour, fed by coil bands."""
    m = AMP[tier]
    c = Canvas()
    plate(c, 0, 0, 16, 16, DK, 130 + tier, bevel=False, density=0.28)
    c.rect(0, 0, 16, 1, DK[3]).rect(0, 0, 1, 16, DK[3]).rect(0, 15, 16, 1, DK[0]).rect(15, 0, 1, 16, DK[0])
    for y in (1, 12):                                                    # collars
        c.rect(1, y, 14, 3, DK[2]).bevel(1, y, 14, 3, DK[4], DK[0])
        c.rivet(2, y + 1, ST).rivet(12, y + 1, ST)
    c.rect(2, 4, 12, 8, 'k')                                             # window
    for x in (2, 3, 12, 13):                                             # coil bands
        for y in range(4, 12):
            c.set(x, y, m[1] if (y + x) % 2 else m[0])
    c.rect(4, 4, 8, 8, 'z')
    c.draw(5, 5, sprite(CRYSTAL, m))
    for i in range(tier):                                                # tier pips on the lower collar
        c.set(6 + i * 2, 13, m[2])
    return c


def amplifier_glow(tier):
    m = AMP[tier]
    c = Canvas()
    c.draw(5, 5, sprite(CRYSTAL, m[:1] + m[2:] + m[4]))
    c.set(7, 6, '&').set(7, 7, m[4])
    for i in range(tier):
        c.set(6 + i * 2, 13, m[3])
    return c


def amplifier_top(tier):
    m = AMP[tier]
    c = casing(DK, 140 + tier)
    c.disc(7.5, 7.5, 5.6, 'k')
    c.ring(7.5, 7.5, 3.4, 5.1, m[0]).ring(7.5, 7.5, 4.0, 4.6, m[1])
    c.disc(7.5, 7.5, 2.6, m[2])
    return c


def amplifier_top_glow(tier):
    m = AMP[tier]
    c = Canvas()
    c.disc(7.5, 7.5, 2.6, m[3])
    c.disc(7.5, 7.5, 1.2, m[4])
    c.set(7, 7, '&')
    return c


def damper_side():
    """Stacked graphite moderator bricks with cold cyan channels between the courses."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, DK, 151, bevel=False, density=0.4)
    for y in (5, 10):                                                    # cooling channels
        c.rect(0, y, 16, 1, CY[0])
    for row, y in enumerate((1, 6, 11)):                                 # brick joints, offset per course
        off = 0 if row % 2 == 0 else 4
        for x in range(off, 16, 8):
            c.rect(x, y, 1, 4, 'k').rect((x + 1) % 16, y, 1, 4, DK[3])
    c.rect(0, 0, 16, 1, DK[2]).rect(0, 15, 16, 1, DK[0])
    return c


def damper_top():
    c = casing(DK, 160)
    c.recess(2, 2, 12, 12, DK, fill=DK[1])
    for x in range(3, 13, 3):                                            # moderator channel grid
        for y in range(3, 13, 3):
            c.rect(x, y, 2, 2, 'z').set(x, y, CY[0])
    return c


def cryo_coolant(frame_no):
    """Frosty cyan block with a slow shimmer: cracked ice facets around a cold core."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = (math.sin(x * 0.9 + y * 0.5) + math.cos(y * 0.8 - x * 0.3)) * 0.5
            c.set(x, y, '{' if v > 0.25 else '<' if v < -0.45 else '}')
    for a, b in (((1, 3), (6, 7)), ((6, 7), (4, 13)), ((6, 7), (13, 5)), ((13, 5), (14, 12)), ((9, 1), (13, 5))):
        c.line(a[0], a[1], b[0], b[1], '>')
    s = frame_no % 8
    for i in range(3):
        c.set((3 + s * 2 + i * 5) % 16, (2 + i * 6 + s) % 16, '^')
    c.frame(0, 0, 16, 16, '<')
    c.rect(0, 0, 16, 1, '}').rect(0, 0, 1, 16, '}')
    return c


# ---------------------------------------------------------------- bank inside

CAP = {0: CU, 1: RD, 2: TE, 3: PU}


def capacitor_side(tier):
    """Cell bank: three vertical cells in the tier metal with charge windows, slate end caps."""
    m = CAP[tier]
    c = Canvas()
    plate(c, 0, 0, 16, 16, SL, 61 + tier, bevel=False, density=0.25)
    c.rect(0, 0, 16, 2, SL[3]).rect(0, 14, 16, 2, SL[1]).rect(0, 15, 16, 1, SL[0])
    for i in range(3):
        x = 1 + i * 5
        c.rect(x, 2, 4, 12, m[2]).rect(x, 2, 1, 12, m[3]).rect(x + 3, 2, 1, 12, m[1])
        c.rect(x + 1, 4, 2, 8, 'z').rect(x + 1, 7, 2, 5, CY[1])            # charge window
        c.set(x + 1, 2, m[4])
    return c


def capacitor_side_glow():
    c = Canvas()
    for i in range(3):
        x = 1 + i * 5
        c.rect(x + 1, 7, 2, 5, CY[2])
        c.rect(x + 1, 7, 1, 1, CY[4])
    return c


def capacitor_top(tier):
    m = CAP[tier]
    c = casing(SL, 70 + tier)
    c.recess(3, 3, 10, 10, SL, fill='k')
    for x, y in ((5, 5), (9, 5), (5, 9), (9, 9)):                         # terminals
        c.rect(x, y, 2, 2, m[2]).set(x, y, m[4])
    c.set(7, 7, CY[1]).set(8, 8, CY[1])
    return c


COIL = {0: CU, 1: AM, 2: PU}


def coil_side(tier):
    """Transfer coil: tight windings in the tier metal around a slate core, energy gap in the middle."""
    m = COIL[tier]
    c = Canvas()
    plate(c, 0, 0, 16, 16, SL, 81 + tier, bevel=False, density=0.25)
    c.rect(0, 0, 16, 1, SL[3]).rect(0, 15, 16, 1, SL[0])
    c.rect(2, 1, 12, 14, 'k')
    for y in range(1, 15):
        c.rect(3, y, 10, 1, m[2] if y % 2 else m[1])
        c.set(3, y, m[3] if y % 2 else m[2]).set(12, y, m[0])
    c.rect(2, 7, 12, 2, 'z').rect(4, 7, 8, 2, CY[1])
    return c


def coil_glow(tier):
    c = Canvas()
    c.rect(4, 7, 8, 2, CY[3])
    c.rect(5 + tier * 2, 7, 2, 1, CY[4])
    return c


def coil_top(tier):
    m = COIL[tier]
    c = casing(SL, 90 + tier)
    c.disc(7.5, 7.5, 5.8, 'k')
    c.ring(7.5, 7.5, 2.4, 5.3, m[1]).ring(7.5, 7.5, 3.2, 4.2, m[2])
    c.disc(7.5, 7.5, 2.0, CY[1])
    return c


# ---------------------------------------------------------------- tesla spire

def spire_base_side():
    """Steel housing with a copper winding band and ceramic insulators: the foot of the column."""
    c = casing(ST, 170)
    c.rect(1, 5, 14, 6, 'k')
    for y in range(5, 11):
        c.rect(2, y, 12, 1, CU[2] if y % 2 else CU[1])
        c.set(2, y, CU[3] if y % 2 else CU[2]).set(13, y, CU[0])
    for x in (4, 8, 11):                                                 # insulators
        c.rect(x, 4, 1, 8, 'k').set(x, 4, ST[4])
    c.rect(2, 7, 12, 2, 'z').rect(3, 7, 10, 1, CY[1])
    return c


def spire_base_side_glow():
    c = Canvas()
    c.rect(3, 7, 10, 1, CY[3])
    c.set(5, 7, CY[4]).set(10, 7, CY[4])
    return c


def spire_base_top():
    """Copper terminal plate the column stands on."""
    c = casing(ST, 180)
    c.disc(7.5, 7.5, 5.8, 'k')
    c.disc(7.5, 7.5, 5.2, CU[1]).disc(7.5, 7.5, 4.4, CU[2])
    c.ring(7.5, 7.5, 2.2, 3.0, CU[0])
    c.set(5, 5, CU[4]).set(6, 5, CU[3])
    return c


def crown_copper():
    c = Canvas()
    plate(c, 0, 0, 16, 16, CU, 190, bevel=False, density=0.1)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, CU)
    return c


def crown_coil():
    """Toroid windings: tight copper turns with dark gaps."""
    c = Canvas()
    c.rect(0, 0, 16, 16, CU[1])
    for x in range(16):
        c.rect(x, 0, 1, 16, CU[2] if x % 2 else CU[1])
        c.set(x, 8, CU[3] if x % 2 else CU[2]).set(x, 12, CU[0])
    for y in (0, 15):
        c.rect(0, y, 16, 1, CU[0])
    return c


def crown_orb():
    """Glass discharge sphere caged in copper bands."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '`')
    c.frame(0, 0, 16, 16, CU[1])
    c.rect(7, 0, 2, 16, CU[2]).rect(7, 0, 1, 16, CU[3])
    c.line(2, 5, 5, 2, ']').line(2, 6, 6, 2, ']').line(11, 13, 13, 11, ']')
    return c


def crown_core(lit):
    c = Canvas()
    c.rect(0, 0, 16, 16, 'z' if not lit else CY[2])
    c.disc(7.5, 7.5, 6.0, CY[0] if not lit else CY[3])
    c.disc(7.5, 7.5, 3.5, CY[1] if not lit else CY[4])
    if lit:
        c.disc(7.5, 7.5, 1.8, '&')
    return c


# ---------------------------------------------------------------- ring collider

SEG = {1: (SL, CU, CY, '-', '_', '|'), 2: (NU, PU, PU, ':', ';', '?')}


def segment_base(tier):
    """Mounting slab of a ring segment: riveted plate with a cable channel along the beam."""
    r, m, g = SEG[tier][:3]
    c = Canvas()
    plate(c, 0, 0, 16, 16, r, 200 + tier, density=0.25)
    c.rivets(0, 0, 16, 16, r, inset=1)
    c.rect(1, 12, 14, 3, r[1]).bevel(1, 12, 14, 3, r[3], r[0])           # side face (the slab is 4 px tall)
    c.rect(3, 13, 10, 1, 'k').rect(4, 13, 8, 1, g[0])
    return c


def segment_coil(tier):
    """Dipole magnet: windings of the tier metal around a yoke, a charge line through the middle."""
    r, m, g = SEG[tier][:3]
    c = Canvas()
    c.rect(0, 0, 16, 16, r[1])
    for y in range(16):
        c.rect(0, y, 16, 1, m[2] if y % 2 else m[1])
        c.set(0, y, m[3]).set(15, y, m[0])
    c.rect(0, 0, 16, 1, r[3]).rect(0, 15, 16, 1, r[0])
    c.rect(0, 7, 16, 2, 'z').rect(1, 7, 14, 1, g[1])
    if tier == 2:
        for x in (3, 8, 12):
            c.set(x, 4, '&').set(x, 11, PU[4])
    return c


def segment_coil_glow(tier):
    g = SEG[tier][2]
    c = Canvas()
    c.rect(1, 7, 14, 1, g[3])
    for x in (2, 7, 12):
        c.set(x, 7, g[4])
    return c


def segment_pipe(tier):
    """Glass beam pipe: tinted pane with darker walls along the edges and long glints."""
    tint, glint, wall = SEG[tier][3:]
    c = Canvas()
    c.rect(0, 0, 16, 16, tint)
    for i in (6, 9):
        c.rect(0, i, 16, 1, wall).rect(i, 0, 1, 16, wall)
    for x in range(0, 16, 4):
        c.set(x, 7, glint).set(7, x + 1, glint)
    return c


def collider_casing():
    c = casing(NU, 120)
    c.set(7, 7, PU[2]).set(8, 8, PU[2])
    return c


def strange_matter(frame_no):
    """A dark sphere of quark matter wrapped in a violet swirl, teal sparks orbiting it."""
    c = Canvas()
    c.disc(7.5, 7.5, 6.2, 'k')
    c.disc(7.5, 7.5, 5.4, NU[1])
    c.disc(6.8, 6.8, 3.6, NU[2])
    c.disc(6.2, 6.2, 1.6, NU[3])
    a0 = frame_no * math.pi / 4
    for arm in (0, math.pi):                                             # two swirl arms
        for i in range(16):
            t = i / 15
            a = a0 + arm + t * math.pi * 1.4
            rr = 0.8 + t * 4.4
            x, y = 7.5 + math.cos(a) * rr, 7.5 + math.sin(a) * rr
            c.set(int(round(x)), int(round(y)), PU[4] if t < 0.3 else PU[3] if t < 0.7 else PU[2])
    for k in range(3):                                                   # orbiting sparks
        a = -a0 * 0.5 + k * 2 * math.pi / 3
        x, y = 7.5 + math.cos(a) * 6.6, 7.5 + math.sin(a) * 6.6
        c.set(int(round(x)), int(round(y)), TE[4] if k == frame_no % 3 else TE[3])
    c.set(5, 5, '&')
    return c


# ---------------------------------------------------------------- output

def main():
    write_block('reactor_casing', hazard_trim(casing(DK, 1), AM).rows(), P)
    write_block('reactor_glass', glass(DK, '(', ')', 2).rows(), P)
    write_block('reactor_power_port', power_port(DK).rows(), P)
    write_block('reactor_power_port_glow', power_port_glow(DK).rows(), P)
    write_block('reactor_access_port', access_port(DK).rows(), P)
    write_anim('block', 'cryo_coolant', [cryo_coolant(i) for i in range(8)], P, frametime=6, interpolate=True)
    for tier, name in ((1, 'flux'), (2, 'pyro'), (3, 'resonant')):
        write_block(f'{name}_amplifier', amplifier_side(tier).rows(), P)
        write_block(f'{name}_amplifier_glow', amplifier_glow(tier).rows(), P)
        write_block(f'{name}_amplifier_top', amplifier_top(tier).rows(), P)
        write_block(f'{name}_amplifier_top_glow', amplifier_top_glow(tier).rows(), P)
    write_block('graphite_damper', damper_side().rows(), P)
    write_block('graphite_damper_top', damper_top().rows(), P)

    write_block('bank_casing', casing(SL, 3).rows(), P)
    write_block('bank_glass', glass(SL, '[', ']', 4).rows(), P)
    write_block('bank_port_input', bank_port(False).rows(), P)
    write_block('bank_port_input_glow', bank_port_glow(False).rows(), P)
    write_block('bank_port_output', bank_port(True).rows(), P)
    write_block('bank_port_output_glow', bank_port_glow(True).rows(), P)
    for tier, name in enumerate(('copper', 'redstone', 'ender', 'resonant')):
        write_block(f'capacitor_{name}', capacitor_side(tier).rows(), P)
        write_block(f'capacitor_{name}_top', capacitor_top(tier).rows(), P)
    write_block('capacitor_glow', capacitor_side_glow().rows(), P)
    for tier, name in enumerate(('basic', 'advanced', 'elite')):
        write_block(f'transfer_coil_{name}', coil_side(tier).rows(), P)
        write_block(f'transfer_coil_{name}_glow', coil_glow(tier).rows(), P)
        write_block(f'transfer_coil_{name}_top', coil_top(tier).rows(), P)

    write_block('spire_casing', casing(ST, 5).rows(), P)
    write_block('spire_base', spire_base_side().rows(), P)
    write_block('spire_base_glow', spire_base_side_glow().rows(), P)
    write_block('spire_base_top', spire_base_top().rows(), P)
    write_block('spire_crown_copper', crown_copper().rows(), P)
    write_block('spire_crown_coil', crown_coil().rows(), P)
    write_block('spire_crown_orb', crown_orb().rows(), P)
    write_block('spire_crown_core', crown_core(False).rows(), P)
    write_block('spire_crown_core_lit', crown_core(True).rows(), P)

    write_block('collider_casing', collider_casing().rows(), P)
    for tier, name in ((1, 'accelerator'), (2, 'resonant')):
        write_block(f'{name}_segment_base', segment_base(tier).rows(), P)
        write_block(f'{name}_segment_coil', segment_coil(tier).rows(), P)
        write_block(f'{name}_segment_coil_glow', segment_coil_glow(tier).rows(), P)
        write_block(f'{name}_segment_pipe', segment_pipe(tier).rows(), P)
    write_anim('item', 'strange_matter', [strange_matter(i) for i in range(8)], P, frametime=3)

    for prefix, r in (('reactor', DK), ('bank', SL), ('spire', ST), ('collider', NU)):
        for state in ('off', 'formed', 'on'):
            write_block(f'{prefix}_controller_{state}', controller_front(r, state).rows(), P)
            write_block(f'{prefix}_controller_{state}_glow', controller_glow(r, state).rows(), P)


if __name__ == '__main__':
    main()
