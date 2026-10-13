"""Energy module textures (big power): Capacitor Bank, Tesla Spire, Core Reactor, Ring Collider, Strange Matter.
Run: python3 scripts/textures/energy.py   (writes textures/block/*.png and textures/item/strange_matter.png; models
come from scripts/data/energy_models.py)

Four families, glow on separate full-bright overlays:
- Capacitor Bank and Core Reactor: the machine casing language (machine_casing.py) at multiblock scale, see the
  'Capacitor Bank and Core Reactor shells' section. Bank: light steel, copper brackets, cyan energy; its capacitors
  and coils are light steel modules. Reactor: gunmetal, hazard amber brackets, green core light; amplifiers glow in
  their tier colour.
- Tesla Spire: brushed steel with copper windings and a cyan arc glow.
- Ring Collider: null violet plating with a white-hot plasma glow; segments are a glass beam pipe between magnets.
The Spire and the Collider share the older construction below (top-left light, bevelled plates, riveted corners).
Casing faces tile seamlessly: the frame of a casing is drawn as half-width seams so walls read as one surface.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import machine_casing as mc  # noqa: E402
from pixelart import MATERIALS, Canvas, grain, material, write_anim, write_block  # noqa: E402

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
})
GY = 'ÀÁÂÃÄ'                                  # neutral grey windings, tinted in game with the metal's map colour
P.update({ch: col for ch, col in zip(GY, ('#3C3C3C', '#7A7A7A', '#B4B4B4', '#DCDCDC', '#FFFFFF'))})
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


# ---------------------------------------------------------------- Capacitor Bank and Core Reactor shells
# The machine casing language (machine_casing.py) at multiblock scale. A formed face reads like one big machine face:
# the corner caps are small shells with the family metal brackets, the edge beams are the frame (darker body, bevelled
# edges, a channel down the middle) and the wall panels and windows are the sunk face plate, seamless across blocks.
# Loose casings tile too: their edges are half seams, so blocks side by side show one groove.
# - Bank (energy storage family): light steel body, copper brackets, cyan energy in the beam channels and corner lenses.
# - Reactor (generator family): gunmetal body, hazard amber brackets, hazard striped beams, green core light.
# Controllers carry a screen (off: dark, formed: still read-out, working: animated), ports an I/O socket.

REACTOR_BODY = ('#1E2328', '#323A42', '#4C565F', '#6C7882', '#97A3AD')   # generator gunmetal
HAZARD_AMBER = ('#6A3206', '#B0600C', '#F0A01C', '#FFC84A', '#FFE6A0')   # the reactor's bracket metal
SHELL_EXTRA = {
    'bank': {'[': '#5FE3F038', ']': '#E6FAFFA8',                        # glass tint and glint
             'D': '#0B1A20', 'M': '#173740', 'N': '#21505C',            # screen glass, grid, glare
             'q': '#1E6B2E', 'Q': '#7FD068', 'R': '#D8FFC0',            # input: green dim, lit, white
             'o': '#7A3A06', 'O': '#FFB21E', 'P': '#FFE08A'},           # output: amber dim, lit, white
    'reactor': {'(': '#6CF09A38', ')': '#D8FFE4A8',
                'D': '#0A140E', 'M': '#14281A', 'N': '#1F3A27',
                '5': '#0E3A1A', '6': '#1E6B2E', '7': '#3E9E3A', '8': '#7FD068', '9': '#D8FFC0',   # core green
                **material('01234', 'copper')},
}
SHELL_GLOW = {'bank': 'vwx', 'reactor': '789'}                           # lens / screen light: lit, bright, white


def shell_pal(fam):
    if fam == 'bank':
        return mc.casing_palette(MATERIALS['copper'], **dict(zip(mc.BODY, BANK_BODY)), **BANK_EXTRA,
                                 **SHELL_EXTRA['bank'])
    return mc.casing_palette(HAZARD_AMBER, **dict(zip(mc.BODY, REACTOR_BODY)), **SHELL_EXTRA['reactor'])


def bolt(c, x, y):
    """2x2 bolt head in the family metal."""
    c.rect(x, y, 2, 2, 'h').set(x, y, 'i').set(x + 1, y + 1, 'g')
    return c


def channel(c, fam, x=6):
    """The family's 4 px strip down a beam or casing: the bank's a dark glass energy conduit, the reactor's hazard
    stripes, sunk in a housing (shadow left, lit lip right)."""
    c.rect(x - 1, 0, 1, 16, 'a').rect(x + 4, 0, 1, 16, 'd')
    if fam == 'bank':
        c.rect(x, 0, 4, 16, 'k').rect(x + 1, 0, 2, 16, 'u')
    else:
        c.hazard(x, 0, 4, 16, 'h', 'k')
    return c


def shell_casing(fam):
    """Loose casing: a plate with half seams round it (a wall of them shows one groove between blocks), family metal
    bolts in the corners and a sunk face plate with a short family strip."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(0, 0, 16, 16, 'd', 'a')
    c.bevel(3, 3, 10, 10, 'a', 'd')
    if fam == 'bank':
        c.rect(6, 5, 4, 6, 'a').rect(7, 5, 2, 6, 'u').rect(7, 5, 2, 1, 'k').rect(9, 5, 1, 6, 'd')
    else:
        c.hazard(5, 6, 6, 4, 'h', 'k')
        c.rect(5, 5, 6, 1, 'a').rect(5, 10, 6, 1, 'd')
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        bolt(c, x, y)
    return c


def shell_panel(fam):
    """Formed wall: the sunk face plate, one surface across the wall: ribs every 8 px, a bolt between them."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    for x in (3, 11):
        c.rect(x, 0, 1, 16, 'b').rect(x + 1, 0, 1, 16, 'd')
    for x, y in ((7, 3), (15, 11)):
        c.set(x, y, 'e').set(x, y + 1, 'b')
    return c


def shell_beam(fam):
    """Formed edge beam, seamless top to bottom: the frame in the darker body, a lit outer edge and a shadowed inner
    one, bolted rails either side of the family strip."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    c.rect(0, 0, 1, 16, 'd').rect(1, 0, 1, 16, 'c').rect(14, 0, 1, 16, 'a').rect(15, 0, 1, 16, 'K')
    channel(c, fam)
    for y in (3, 11):
        c.set(3, y, 'd').set(3, y + 1, 'a').set(12, y, 'd').set(12, y + 1, 'a')
    return c


def shell_beam_glow(fam):
    """The bank's conduit lit, a brighter glint every 8 px."""
    c = Canvas()
    c.rect(7, 0, 2, 16, 'v')
    for y in (3, 11):
        c.set(7, y, 'w').set(8, y, 'w').set(7, y + 1, 'w')
    return c


BOSS = ['..dddd..', '.dcccccb', 'dccKKcca', 'dcKuuKba', 'dcKuuKba', 'dccKKbba', '.bbbbba.', '..aaaa..']


def shell_corner(fam):
    """Corner cap: a small shell round a round boss holding a lens (a steel boss with a cyan lens for the bank, an amber
    one with a green lens for the reactor)."""
    c = mc.shell(1)
    boss = BOSS if fam == 'bank' else [r.translate(str.maketrans('dcbau', 'ihgf6')) for r in BOSS]
    c.draw(4, 4, boss)
    return c


def shell_corner_glow(fam):
    lit, bright, white = SHELL_GLOW[fam]
    c = Canvas()
    c.rect(7, 7, 2, 2, lit).set(7, 7, white).set(8, 7, bright).set(7, 8, bright)
    return c


def shell_glass(fam, joined=0):
    """Window in the shell's 2 px frame: the frame only on the sides that join no neighbour (1 top, 2 right, 4 bottom,
    8 left), family metal brackets where two framed sides meet, so a wall of panes reads as one window."""
    tint, glint = ('[', ']') if fam == 'bank' else ('(', ')')
    t, rt, b, lf = (not joined & 1, not joined & 2, not joined & 4, not joined & 8)
    c = Canvas()
    c.rect(0, 0, 16, 16, tint)
    if joined:      # part of a pane: fewer glints, so a wall of them reads calm
        c.line(3, 6, 6, 3, glint).line(4, 6, 6, 4, glint)
        c.line(9, 13, 12, 10, glint)
    else:
        c.line(4, 10, 10, 4, glint).line(5, 10, 10, 5, glint)
        c.line(10, 12, 12, 10, glint)
    if t:
        c.rect(0, 0, 16, 1, 'd').rect(0, 1, 16, 1, 'b')
    if b:
        c.rect(0, 15, 16, 1, 'a').rect(0, 14, 16, 1, 'b')
    if lf:
        c.rect(0, 0, 1, 16, 'd').rect(1, 0, 1, 16, 'b')
    if rt:
        c.rect(15, 0, 1, 16, 'a').rect(14, 0, 1, 16, 'b')
    if t and lf:
        c.set(1, 1, 'b')
    if b and rt:
        c.set(14, 14, 'b')
    for x, y, sx, sy, s1, s2 in ((0, 0, 1, 1, t, lf), (15, 0, -1, 1, t, rt), (0, 15, 1, -1, b, lf),
                                 (15, 15, -1, -1, b, rt)):
        if s1 and s2:                                                    # bracket, as on the shell
            for i in range(4):
                for j in range(2):
                    c.set(x + sx * i, y + sy * j, 'h').set(x + sx * j, y + sy * i, 'h')
            c.set(x + sx * 3, y, 'g').set(x, y + sy * 3, 'g')
            c.set(x + sx * 3, y + sy, 'f').set(x + sx, y + sy * 3, 'f')
            c.set(x, y, 'K')
    return c


# ---- controllers: a screen in the shell, the status lamp above it

CTRL_FRAMES, CTRL_FRAMETIME = 32, 3          # working screen loop: 96 ticks
SCREEN = (3, 3, 10, 10)                      # x, y, w, h of the screen recess; the picture is the 8x8 inside it


def shell_controller(fam, state):
    """Off: dark glass with a glare. Formed and working: a faint grid on the glass, the picture on the overlay."""
    c = mc.shell(1)
    x, y, w, h = SCREEN
    c.rect(x, y, w, h, 'D').bevel(x, y, w, h, 'a', 'd')
    if state == 'off':
        c.set(x + 1, y + 1, 'N').set(x + 2, y + 1, 'N').set(x + 1, y + 2, 'N')
    else:
        for gx in range(x + 1, x + w - 1):
            for gy in range(y + 1, y + h - 1):
                if (gx - x) % 3 == 0 and (gy - y) % 3 == 0:
                    c.set(gx, gy, 'M')
    c.rect(7, 1, 2, 1, 'l')
    return c


BANK_BARS = (4, 7, 10)                       # x of the bank screen's three 2 px bars


def bank_screen(c, t=None):
    """Charge read-out: three level bars on a base line. Still (t None) they stand at rest; working, they rise and
    fall slowly out of step and a soft scan line sweeps down the glass once per loop."""
    c.rect(4, 11, 8, 1, 'u')
    for i, x in enumerate(BANK_BARS):
        if t is None:
            h = (4, 6, 5)[i]
        else:
            h = round(5 + 2.4 * math.sin(2 * math.pi * (t / CTRL_FRAMES + i / 3)))
        top = 11 - h
        c.rect(x, top, 2, h, 'v').rect(x + 1, top, 1, h, 'y')
        c.rect(x, top, 2, 1, 'w').set(x, top, 'x')
    if t is not None:
        scan = 4 + (t * 8) // CTRL_FRAMES                                  # rows 4..11
        for xx in range(4, 12):
            ch = c.get(xx, scan)
            c.set(xx, scan, {'v': 'w', 'y': 'v', 'w': 'x', 'u': 'v'}.get(ch, 'u') if ch != 'x' else 'x')
    return c


def reactor_screen(c, t=None):
    """Core output trace: still, a flat line; working, a calm sine wave scrolling across, its crests brightest."""
    for x in range(4, 12):
        if t is None:
            c.set(x, 8, '7')
            continue
        yy = 7.5 + 2.4 * math.sin(2 * math.pi * ((x - 4) / 8 - t / CTRL_FRAMES * 2))
        y0 = 7.5 + 2.4 * math.sin(2 * math.pi * ((x - 5) / 8 - t / CTRL_FRAMES * 2))
        a, b = sorted((round(yy), round(y0)))
        for y in range(a, b + 1):
            c.set(x, y, '7')
        c.set(x, round(yy), '9' if round(yy) <= 5 else '8')
    c.rect(4, 11, 8, 1, '6')
    return c


def shell_controller_glow(fam, t=None):
    c = Canvas().rect(7, 1, 2, 1, 'L')
    return bank_screen(c, t) if fam == 'bank' else reactor_screen(c, t)


# ---- ports

PORT_IN = ['Q........Q', 'QQ......QQ', '.QQ.RR.QQ.', '.QQ.RR.QQ.', 'QQ......QQ', 'Q........Q']
PORT_OUT = ['..Q....Q..', '.QQ....QQ.', 'QQ..RR..QQ', 'QQ..RR..QQ', '.QQ....QQ.', '..Q....Q..']


def bank_port(output):
    """Bank Port: a dark glass socket with chevrons pointing in (input, green) or out (output, amber)."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, 'm').rect(3, 3, 10, 1, 'p').rect(3, 3, 1, 10, 'p')
    c.draw(3, 5, PORT_OUT if output else PORT_IN)
    c.replace('QR', 'o' if output else 'q', 3, 5, 10, 6)
    return c


def bank_port_glow(output):
    c = Canvas()
    c.draw(3, 5, PORT_OUT if output else PORT_IN)
    if output:
        c.replace('Q', 'O').replace('R', 'P')
    return c


def power_port():
    """Core Reactor Power Port: a copper terminal ring round a dark socket with the amber bolt mark (FE out)."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.disc(7.5, 7.5, 4.6, 'a')
    c.ring(7.5, 7.5, 3.3, 4.2, '2').ring(7.5, 7.5, 3.3, 3.7, '3')
    c.disc(7.5, 7.5, 3.0, 'D')
    c.draw(6, 5, ['..hi', '.hi.', 'hiih', '.ih.', 'ih..', 'h...'])
    return c


def power_port_glow():
    c = Canvas()
    c.ring(7.5, 7.5, 3.3, 4.2, '7').ring(7.5, 7.5, 3.3, 3.7, '8')
    return c


def access_port():
    """Core Reactor Access Port: a grilled hatch held by a steel cross with an amber hub (items in and out)."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, 'a').bevel(3, 3, 10, 10, 'a', 'd')
    c.grille(4, 4, 8, 8, 'K', 'b')
    c.rect(3, 7, 10, 2, 'c').rect(3, 7, 10, 1, 'd')
    c.rect(7, 3, 2, 10, 'c').rect(7, 3, 1, 10, 'd')
    c.rect(7, 7, 2, 2, 'h').set(7, 7, 'i').set(8, 8, 'g')
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


def amplifier_crystal(tier):
    """The floating crystal of an amplifier: facets in the tier colour, drawn full bright."""
    m = AMP[tier]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = (x * 3 + y * 5) % 11
            c.set(x, y, m[4] if v == 0 else m[3] if v < 4 else m[2] if v < 8 else m[1])
    c.line(0, 0, 15, 15, m[4]).line(15, 0, 0, 15, m[3])
    c.frame(0, 0, 16, 16, m[2])
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
# Capacitors and Transfer Coils: energy storage family, the casing construction in light steel (brackets in the tier
# colour) so a bank's inside reads as a rack of modules through its glass. Capacitors are storage cells: a round can
# in a coloured sleeve with a cyan charge stripe that a calm shimmer climbs, an aluminium vent top. Transfer coils are
# I/O conductors: banks of windings round a cyan current band that runs right round the block (the four sides join
# up), with pulses chasing round it, and round the ring on top. More pulses per tier.
# Capacitor tiers (sleeve): copper, redstone red, ender green, resonant indigo. Coil tiers (windings): copper, gold,
# superconductor frost.

BANK_BODY = ('#4A535B', '#6F7A83', '#9AA5AE', '#C4CDD4', '#EBF0F3')   # light steel
BANK_EXTRA = {'m': '#163A44', 'n': '#2A6A78', 'p': '#0D2229', 'y': '#137489'}   # dark glass, lit edge, shade, deep charge
CAP_SLEEVE = {
    0: MATERIALS['copper'],
    1: ('#3E0608', '#7A0E10', '#BE1E1A', '#F0473A', '#FFB0A2'),          # redstone
    2: ('#062C26', '#0C5A48', '#169070', '#46CC9C', '#C0FFE4'),          # ender
    3: ('#160A2E', '#36206A', '#5E46B0', '#8EA0F0', '#D8F4FF'),          # resonant
}
COIL_METAL = {
    0: MATERIALS['copper'],
    1: MATERIALS['gold'],
    2: ('#0E1E36', '#1C3E6C', '#2F6CB0', '#78B8F0', '#E0F4FF'),          # superconductor frost
}
BANK_FRAMES = 16


def bank_pal(ramp):
    return mc.casing_palette(ramp, **dict(zip(mc.BODY, BANK_BODY)), **BANK_EXTRA)


CAN_X = range(3, 13)
CAN_SLEEVE = 'ghijiihhgf'        # sleeve ramp across the can (round, lit from the left)
CAN_CRIMP = 'cdeeddcccb'         # aluminium crimp ring
CAP_STRIPE = (9, 5, 2, 7)        # x, y, w, h of the charge stripe


def capacitor_side(tier):
    """A round can filling the bay: aluminium crimp, groove, coloured sleeve with a dark glass charge stripe."""
    c = mc.shell(tier + 1)
    for i, x in enumerate(CAN_X):
        c.set(x, 3, CAN_CRIMP[i]).set(x, 4, 'a')
        c.rect(x, 5, 1, 7, CAN_SLEEVE[i])
        c.set(x, 12, 'fghij'[max(0, 'fghij'.index(CAN_SLEEVE[i]) - 2)])
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):                     # rounded can corners
        c.set(x, y, 'b')
    x, y, w, h = CAP_STRIPE
    c.rect(x, y, w, h, 'm').rect(x, y, 1, h, 'n').set(x + 1, y + h - 1, 'p')
    return mc.tier_notches(c, tier + 1)


def capacitor_side_glow(t):
    """Charge stripe lit, a soft band of light climbing it once per loop (interpolated)."""
    c = Canvas()
    x, y, w, h = CAP_STRIPE
    band = y + h + 1 - (t / BANK_FRAMES) * (h + 4)
    for yy in range(y, y + h):
        d = abs(yy - band)
        lo, hi = ('w', 'x') if d < 0.8 else ('v', 'w') if d < 1.8 else ('y', 'v')
        c.set(x, yy, hi).set(x + 1, yy, lo)
    c.set(x, y, 'x').set(x + 1, y, 'w')
    return c


def capacitor_top(tier):
    """The can seen from above: a ring of sleeve round an aluminium lid scored with a vent cross."""
    c = mc.shell(tier + 1)
    c.rect(3, 3, 10, 10, 'b')
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            lit = -(dx + dy) / max(d, 0.1)                                  # light from the top left
            if d <= 5.2:
                c.set(x, y, 'i' if lit > 0.5 else 'g' if lit < -0.5 else 'h')
            if d <= 3.9:
                c.set(x, y, 'e' if lit > 0.3 and d > 2.6 else 'c' if lit < -0.3 and d > 2.6 else 'd')
    for i in range(5, 11):                                                  # vent score
        c.set(i, 7, 'b').set(7, i, 'b').set(i, 8, 'c').set(8, i, 'c')
    c.rect(7, 7, 2, 2, 'a')
    return mc.tier_notches(c, tier + 1)


WIND_ROWS = (3, 4, 5, 10, 11, 12)
WIND_SHADE = 'ghiijhhggf'


def coil_side(tier):
    """Two banks of windings over and under a glass current band that crosses the whole face."""
    c = mc.shell(tier + 1)
    for y in WIND_ROWS:
        for i, x in enumerate(CAN_X):
            ch = WIND_SHADE[i]
            if y in (4, 11):                                                # groove between two turns
                ch = 'g' if ch in 'ij' else 'f'
            c.set(x, y, ch)
    for x in range(16):
        c.set(x, 6, 'a').set(x, 9, 'd')
        c.rect(x, 7, 1, 2, 'u')
    for x in (2, 13):                                                       # band collars
        c.rect(x, 6, 1, 4, 'k')
    return mc.tier_notches(c, tier + 1)


def coil_glow(tier, t):
    """The current band lit, pulses chasing left to right (round the block: the four sides join up)."""
    c = Canvas()
    n = (1, 2, 4)[tier]
    for x in range(16):
        d = (t - x) % (16 // n)
        ch = 'x' if d == 0 else 'w' if d == 1 else 'w' if d == 2 and n < 4 else 'v'
        c.rect(x, 7, 1, 2, ch)
    return c


def coil_top(tier):
    """Looking down the coil: a winding ring round a glass current ring and a steel core pin."""
    c = mc.shell(tier + 1)
    c.rect(3, 3, 10, 10, 'b')
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            lit = -(dx + dy) / max(d, 0.1)
            if d <= 5.2:
                ch = 'i' if lit > 0.5 else 'g' if lit < -0.5 else 'h'
                c.set(x, y, ch if d > 4.2 or d < 3.3 else 'fghij'['fghij'.index(ch) - 1])
            if d < 2.9:
                c.set(x, y, 'u')
            if d < 1.0:
                c.set(x, y, 'd')
    return mc.tier_notches(c, tier + 1)


def coil_top_glow(tier, t):
    """The current ring lit with marks circling it (the same way round as the band on the sides)."""
    c = Canvas()
    n = (1, 2, 4)[tier]
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if 1.0 <= d < 2.9:
                a = (math.atan2(dy, dx) / (2 * math.pi)) % 1
                k = (a * n + t / BANK_FRAMES) % 1
                c.set(x, y, 'x' if k < 0.12 else 'w' if k < 0.3 else 'v')
    return c


def write_bank_inside():
    for tier, name in enumerate(('copper', 'redstone', 'ender', 'resonant')):
        pal = bank_pal(CAP_SLEEVE[tier])
        write_block(f'capacitor_{name}', capacitor_side(tier).rows(), pal)
        write_block(f'capacitor_{name}_top', capacitor_top(tier).rows(), pal)
    write_anim('block', 'capacitor_glow', [capacitor_side_glow(t) for t in range(BANK_FRAMES)], bank_pal(CAP_SLEEVE[0]),
               frametime=4, interpolate=True)
    for tier, name in enumerate(('basic', 'advanced', 'elite')):
        pal = bank_pal(COIL_METAL[tier])
        write_block(f'transfer_coil_{name}', coil_side(tier).rows(), pal)
        write_anim('block', f'transfer_coil_{name}_glow', [coil_glow(tier, t) for t in range(BANK_FRAMES)], pal,
                   frametime=2)
        write_block(f'transfer_coil_{name}_top', coil_top(tier).rows(), pal)
        write_anim('block', f'transfer_coil_{name}_top_glow', [coil_top_glow(tier, t) for t in range(BANK_FRAMES)],
                   pal, frametime=2, interpolate=True)


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


def crown_coil():
    """Secondary winding of the neck: tight copper turns with dark gaps."""
    c = Canvas()
    for y in range(16):
        c.rect(0, y, 16, 1, CU[2] if y % 2 else CU[1])
        c.set(0, y, CU[3] if y % 2 else CU[2]).set(15, y, CU[0])
    for x in (4, 11):                                                    # insulating straps
        c.rect(x, 0, 1, 16, 'k').set(x, 0, ST[3])
    return c


def crown_ring():
    """Polished steel toroid: a bright band where it faces the light, darker towards the edges."""
    c = Canvas()
    for y in range(16):
        band = abs(y - 6) / 9.0
        ch = ST[4] if band < 0.12 else ST[3] if band < 0.4 else ST[2] if band < 0.75 else ST[1]
        c.rect(0, y, 16, 1, ch)
    for x in range(0, 16, 5):                                            # panel seams
        c.rect(x, 0, 1, 16, ST[1])
    c.set(2, 5, '&').set(7, 6, '&').set(12, 5, '&')
    return c


def crown_core(lit):
    """Discharge sphere: dark glass with a cold core, white-hot while the spire runs."""
    c = Canvas()
    c.rect(0, 0, 16, 16, CY[0] if not lit else CY[2])
    c.disc(7.5, 7.5, 6.5, 'k' if not lit else CY[3])
    c.disc(7.5, 7.5, 4.5, CY[0] if not lit else CY[4])
    c.disc(7.5, 7.5, 2.2, CY[1] if not lit else '&')
    c.set(4, 4, CY[3] if not lit else '&').set(5, 4, CY[2] if not lit else '&')
    return c


def spire_coil():
    """Formed conductor shell: the metal wound into tight turns (grey, tinted with the block's map colour in game),
    seamless top to bottom, with a dark slit down the middle where the arc light runs."""
    c = Canvas()
    for y in range(16):
        c.rect(0, y, 16, 1, (GY[3], GY[2], GY[1], GY[0])[y % 4])
    for x in range(16):
        for y in range(0, 16, 4):
            if (x * 5 + y * 3) % 11 == 0:
                c.set(x, y, GY[4])
    c.rect(7, 0, 2, 16, 'k')
    for y in range(0, 16, 4):
        c.set(6, y + 1, GY[1]).set(9, y + 1, GY[1])
    return c


def spire_rail():
    """Steel stay along the corners of the formed column: a bevelled bar with rivets, seamless top to bottom."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, 190, bevel=False, vertical=True, density=0.3)
    for x0 in (0, 4, 8, 12):
        c.rect(x0, 0, 1, 16, ST[4]).rect(x0 + 1, 0, 1, 16, ST[3]).rect(x0 + 3, 0, 1, 16, ST[0])
        for y in (2, 10):
            c.set(x0 + 1, y, ST[4]).set(x0 + 2, y + 1, ST[1])
    return c


def spire_coil_glow():
    """Arc light down the slit (white, tinted and pulsed by the renderer)."""
    c = Canvas()
    for y in range(16):
        c.set(7, y, '&' if y % 5 else '^').set(8, y, '^' if y % 3 else '&')
    return c


# ---------------------------------------------------------------- ring collider
# Ring Collider in the machine casing language (machine_casing.py). The controller is a violet-tinted graphite casing
# with resonant alloy brackets. Its front is the beam aperture: a polished steel bezel held by four quadrupole clamps
# on the diagonals, and 2 px behind it the collision chamber (its own elements, scripts/data/energy_models.py): eight
# detector cells between steel spokes round the collision space, the beam tube across it. The beam line enters the
# front frame at both sides. The sides where the pipes join carry a flanged beam port on the energy channel, the back
# the plain channel, the top a window over the beam line round a lens. Formed: standby glow (dim tube and beam line,
# standby lamps). Running: bunches stream in along the tube to a white collision point, two lights circle the
# detector rim the opposite ways and meet at the tube ends; every running loop is CL_FRAMES x CL_FRAMETIME ticks,
# interpolated.
# Segments: a graphite slab with tier brackets, a glass beam pipe with steel flanges at the block edges, a magnet on
# straights (two yoke plates round a coil pack with a slot on top) and a bending magnet on corners. Accelerator
# Segments are graphite with copper windings and cyan, Resonant Segments violet-tinted graphite with resonant alloy
# and violet. ColliderRenderer lights the beam and the magnet slots while the ring charges and runs.
# Chars on top of the casing palette: '12345' light steel, 'm n o' chamber (void, dark, glass lit edge),
# 'p q r s t' plasma light (deep -> white), '6 7 8' pipe glass (tint, wall, glint).
import machine_casing as mc  # noqa: E402

CL_FRAMES, CL_FRAMETIME = 16, 2
CL_HOLE, CL_RIM = 5.0, 6.1                                               # aperture and bezel radius on the front
CL_STEEL = ('#2E3440', '#4E5666', '#7A8496', '#AAB4C6', '#E2E8F4')
CL_BODY = {1: mc.GRAPHITE, 2: ('#121119', '#1D1C28', '#2C2A3B', '#434158', '#68657F')}
CL_METAL = {1: mc.INDUSTRY_TIERS[1], 2: mc.INDUSTRY_TIERS[4]}
CL_LIGHT = {1: ('#0F3A46', '#1B8098', '#3FD6EE', '#B8F6FF', '#FFFFFF'),
            2: ('#2A1458', '#5A36C4', '#9C70FF', '#DCCCFF', '#FFFFFF')}
CL_ENERGY = {1: dict(mc.ENERGY), 2: {'u': '#1E1838', 'v': '#7A50F0', 'w': '#C8B0FF', 'x': '#F8F2FF'}}
CL_GLASS = {1: ('#6FE0F560', '#3E98AAE0', '#E6FAFFC0'), 2: ('#A884FF68', '#7E5CD8E0', '#F0E2FFC0')}
CL_SEG = {1: 'accelerator', 2: 'resonant'}


def cl_pal(tier):
    """1: Accelerator Segment (graphite, copper, cyan); 2: Resonant Segment and the controller (violet-tinted
    graphite, resonant alloy, violet)."""
    extra = dict(zip(mc.BODY, CL_BODY[tier]))
    extra.update(zip('12345', CL_STEEL))
    extra.update(zip('pqrst', CL_LIGHT[tier]))
    extra.update(zip('678', CL_GLASS[tier]))
    extra.update(CL_ENERGY[tier])
    extra.update({'m': '#07060C', 'n': '#1E1A30', 'o': '#3A2E6A'})
    return mc.casing_palette(CL_METAL[tier], **extra)


def cl_dist(x, y):
    return math.hypot(x - 7.5, y - 7.5)


def cl_lean(x, y):
    """< 0 towards the light (top-left), > 0 away from it."""
    return (x - 7.5) + (y - 7.5)


def cl_wave(frame, offset=0.0):
    """0..1 cosine over the loop, 1 at frame `offset` (in loop fractions)."""
    return 0.5 + 0.5 * math.cos(2 * math.pi * (frame / CL_FRAMES - offset))


def cl_stream(d, frame):
    """Brightness 0..1 of a bunch train streaming inwards: d is the distance from the collision point in pixels,
    one bunch every 4 px, moving 4 px per loop."""
    phase = (d + 4.0 * frame / CL_FRAMES) % 4.0
    return max(0.0, 1.0 - phase / 2.2)


def cl_light(b, floor='q'):
    """Brightness 0..1 -> plasma char."""
    return 't' if b > 0.8 else 's' if b > 0.55 else 'r' if b > 0.25 else floor


def bezel(c, cx, cy, r0, r1, lip=0.6):
    """Polished steel ring: the inner lip shadowed at the top-left, the face lit there."""
    for y in range(16):
        for x in range(16):
            d, lean = math.hypot(x - cx, y - cy), (x - cx) + (y - cy)
            if r0 <= d < r1:
                if d < r0 + lip:
                    c.set(x, y, '2' if lean < -1 else '4' if lean > 1 else '3')
                else:
                    c.set(x, y, '5' if lean < -2 else '2' if lean > 2 else '4')
    return c


BEAM_STUBS = (0, 1, 14, 15)                                              # beam line in the front frame, rows 7-8


def collider_front():
    """Beam aperture (transparent: the chamber shows through) in a steel bezel with four quadrupole clamps on the
    diagonals, the beam line entering through the frame at both sides, standby and run lamps on the top frame and a
    beam readout on the bottom frame."""
    c = mc.shell(4)
    bezel(c, 7.5, 7.5, CL_HOLE, CL_RIM)
    for y in range(16):
        for x in range(16):
            if cl_dist(x, y) < CL_HOLE:
                c.set(x, y, '.')
    for x, y, sx, sy in ((3, 3, 1, 1), (12, 3, -1, 1), (3, 12, 1, -1), (12, 12, -1, -1)):
        c.set(x, y, 'h').set(x + sx, y, 'h').set(x, y + sy, 'h')
    c.set(3, 3, 'i').set(12, 12, 'g')
    for x in BEAM_STUBS:
        c.set(x, 7, 'u').set(x, 8, 'u')
    c.rect(6, 1, 4, 1, 'k').set(6, 1, 'l').set(7, 1, 'l').set(9, 1, 'l')  # lamps: standby x2, running
    c.rect(5, 14, 6, 1, 'k')                                              # beam readout
    return c


def collider_front_glow(state, frame=0):
    """formed: standby lamps, a dim beam line and readout; on: the run lamp, bunches streaming in along the beam line,
    the readout breathing with the chamber."""
    c = Canvas()
    c.set(6, 1, 'r').set(7, 1, 'r')
    if state == 'formed':
        for x in BEAM_STUBS:
            c.set(x, 7, 'p').set(x, 8, 'p')
        c.rect(5, 14, 1, 1, 'p')
        return c
    c.set(9, 1, 'L')
    for x in BEAM_STUBS:
        ch = cl_light(cl_stream(abs(x - 7.5) - 1, frame))
        c.set(x, 7, ch).set(x, 8, ch)
    lvl = 4 + round(2 * cl_wave(frame))                                  # readout: 4..6 bars
    for i in range(6):
        c.set(5 + i, 14, ('s' if i == lvl - 1 else 'r') if i < lvl else 'p')
    return c


# Chamber texture: the visible disc (d < CL_HOLE) is the chamber back; rows 0-1 hold the beam tube's front face,
# row 14 its top and bottom and row 15 the bore walls (outside the disc, so they never show on the back).
TRACK = (2.1, 4.6)                                                       # detector: eight glass cells
HALO = 2.1                                                               # collision space round the beam


def track_cell(x, y):
    """Index 0-7 of the detector cell at x, y, or None on the steel between the cells and off the track."""
    if not TRACK[0] <= cl_dist(x, y) < TRACK[1]:
        return None
    a = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 360
    return None if a % 45 < 5 or a % 45 > 40 else int(a // 45)


def collider_chamber():
    """A detector of eight glass cells between steel spokes round the dark collision space; the beam tube across."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'm')
    for y in range(16):
        for x in range(16):
            d, lean = cl_dist(x, y), cl_lean(x, y)
            if d < HALO:
                c.set(x, y, 'n')
            elif d < TRACK[1]:
                if track_cell(x, y) is None:
                    c.set(x, y, '3' if lean < -1 else '2')              # steel spokes
                else:
                    c.set(x, y, 'o' if d < 3.0 else 'u' if lean < 2 else 'n')
    c.rect(0, 0, 16, 1, 'o').rect(0, 1, 16, 1, 'u')                      # tube front: glass, lit at the top
    c.rect(0, 14, 16, 1, '3')                                            # tube top and bottom: steel
    c.rect(0, 15, 16, 1, 'a')                                            # bore walls
    return c


def cell_angle(i):
    return math.radians(i * 45 + 22.5)


def collider_chamber_glow(state, frame=0):
    """formed: the tube and collision point dim; on: bunches streaming in along the tube to a white collision point
    with a breathing halo, and two lights circling the detector rim the opposite ways (the ring's two bunches),
    meeting at the tube ends."""
    c = Canvas()
    if state == 'formed':
        c.rect(0, 0, 16, 2, 'p')
        c.rect(7, 0, 2, 2, 'q')
        return c
    breathe = cl_wave(frame)
    a1 = math.pi + math.pi * frame / 8                                   # half a turn per loop each way
    a2 = math.pi - math.pi * frame / 8
    cells = []
    for i in range(8):
        near = min(abs((cell_angle(i) - a + math.pi) % (2 * math.pi) - math.pi) for a in (a1, a2))
        cells.append(max(0.0, 1.0 - near / 1.4))
    for y in range(16):
        for x in range(16):
            d, i = cl_dist(x, y), track_cell(x, y)
            if i is not None and d >= 3.5 and cells[i] > 0.15:
                c.set(x, y, cl_light(cells[i], 'p'))
            elif i is None and d < HALO and abs(y - 7.5) < 2:
                c.set(x, y, 'q' if breathe > 0.5 and abs(x - 7.5) < 1 else 'p')
    for x in range(16):
        dx = abs(x - 7.5)
        b = 1.0 if dx < 1 else cl_stream(dx - 1, frame)
        c.set(x, 1, cl_light(b, 'r'))
        c.set(x, 0, 't' if dx < 1 else 's' if b > 0.8 else 'r')
    return c


def port_flange(c):
    """Square steel flange the beam pipe bolts onto (the pipe is 4 px, x/y 6..9), bolts in the tier metal."""
    c.rect(5, 5, 6, 6, '3')
    c.bevel(5, 5, 6, 6, '4', '1')
    c.rect(6, 6, 4, 4, 'u')
    c.bevel(6, 6, 4, 4, 'k', 'o')
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        c.set(x, y, 'h')
    c.set(4, 4, 'i')
    return c


def collider_port():
    """Side where a beam pipe joins: the casing side's energy channel feeding a flanged beam port."""
    return port_flange(mc.side(0))


def collider_port_glow(state, frame=0):
    """formed: the port dim; on: the channel's pulse and the port glowing with the beam."""
    c = Canvas()
    if state == 'formed':
        c.rect(7, 7, 2, 2, 'p')
        return c
    c.paste(mc.side_glow(frame, CL_FRAMES))
    c.rect(5, 5, 6, 6, '.')
    b = cl_wave(frame, 0.25)
    c.rect(7, 7, 2, 2, 'q' if b < 0.3 else 'r').rect(7, 7, 2, 2, 'r')
    c.set(7, 7, 's' if b > 0.5 else 'r').set(8, 8, 'q')
    c.set(7, 6, 'q').set(8, 6, 'q').set(6, 7, 'q').set(6, 8, 'q')
    return c


def collider_top():
    """A window over the beam line and a lens over the collision point, held by four clamps."""
    c = mc.shell(4)
    for x in range(16):
        c.set(x, 6, '1').set(x, 7, 'u').set(x, 8, 'u').set(x, 9, '4')
    bezel(c, 7.5, 7.5, 2.0, 3.4, lip=0.5)
    c.disc(7.5, 7.5, 1.9, 'u')
    for x, y in ((4, 4), (10, 4), (4, 10), (10, 10)):
        c.rect(x, y, 2, 2, 'h').set(x, y, 'i')
    c.set(6, 6, 'o')
    return c


def collider_top_glow(state, frame=0):
    """formed: the window and lens dim; on: bunches streaming in along the window, the lens breathing."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = cl_dist(x, y)
            if d < 1.9:
                c.set(x, y, 'p' if state == 'formed' else 't' if d < 0.6 + 0.8 * cl_wave(frame) else 's')
            elif 7 <= y <= 8 and (x < 4 or x > 11):
                c.set(x, y, 'p' if state == 'formed' else cl_light(cl_stream(abs(x - 7.5) - 3.4, frame)))
    return c


# ---------------------------------------------------------------- ring segments

def seg_brackets(c, x0, y0, x1, y1, n=3):
    """Tier metal L brackets on the corners of x0..x1, y0..y1 (inclusive), light from the top-left."""
    for x, sx in ((x0, 1), (x1, -1)):
        for y, sy in ((y0, 1), (y1, -1)):
            for i in range(n):
                c.set(x + sx * i, y, 'h').set(x, y + sy * i, 'h')
            c.set(x + sx * (n - 1), y, 'g').set(x, y + sy * (n - 1), 'g')
    c.set(x0, y0, 'j').set(x1, y1, 'f')
    return c


def segment_base():
    """Top of the slab: a bevelled graphite deck with tier brackets in the corners and bolted pads, a sunken mount in
    the middle where the pipe stands."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(0, 0, 16, 16, 'd', 'a')
    c.bevel(1, 1, 14, 14, 'c', 'b')
    seg_brackets(c, 1, 1, 14, 14, 4)
    c.recess(5, 5, 6, 6, 'abcde', fill='b')
    c.rect(7, 7, 2, 2, 'a')
    for x, y in ((7, 2), (2, 7), (13, 8), (8, 13)):                      # cable stubs on the edges, unlit energy
        c.set(x, y, 'u').set(x + (1 if y in (2, 13) else 0), y + (1 if x in (2, 13) else 0), 'u')
    return c


# Parts atlases of a segment (explicit UVs in scripts/data/energy_models.py)
SLAB_SIDE = (0, 0, 16, 4)
YOKE_FACE = (0, 4, 10, 12)          # 10 wide (across the beam) x 8 tall, the beam port in the middle
COIL = (10, 4, 16, 12)              # 6 along the beam x 8 across; windings run across, a slot down the middle
CORNER_SIDE = (0, 0, 8, 8)          # bending magnet (segment_corner): side with the beam port
CORNER_TOP = (8, 0, 16, 8)          # its top: a coil round a square yoke
FLANGE = (0, 8, 6, 14)              # pipe flange face, its edge is the top row


def port_plate(c, x0, y0, w, h, ring):
    """Graphite plate with tier brackets and a steel port ring (6 px) round the 4 px pipe at (ring)."""
    c.rect(x0, y0, w, h, 'c')
    c.bevel(x0, y0, w, h, 'd', 'a')
    seg_brackets(c, x0, y0, x0 + w - 1, y0 + h - 1, 2)
    rx, ry = ring
    c.rect(rx, ry, 6, 6, '2').bevel(rx, ry, 6, 6, '3', '1')
    c.rect(rx + 1, ry + 1, 4, 4, 'm').bevel(rx + 1, ry + 1, 4, 4, 'k', 'n')
    return c


def segment_parts():
    c = Canvas()
    # slab side: lit edge, a band with an unlit energy slot, brackets at the ends
    c.rect(0, 0, 16, 4, 'b')
    c.rect(0, 0, 16, 1, 'd').rect(0, 3, 16, 1, 'a')
    c.rect(3, 2, 10, 1, 'k').rect(4, 2, 8, 1, 'u')
    for x in (0, 1, 14, 15):
        c.rect(x, 0, 1, 4, 'h')
    c.set(0, 0, 'j').set(1, 0, 'i').set(15, 3, 'f').set(14, 3, 'g')
    c.set(2, 1, 'e').set(13, 1, 'e')
    # yoke plate face: the port round the pipe (plate cols 3..6, rows 2..5)
    x0, y0 = YOKE_FACE[:2]
    port_plate(c, x0, y0, 10, 8, (x0 + 2, y0 + 1))
    # coil pack: windings across the beam (stripes along u), a dark slot down the middle (lit by ColliderRenderer)
    x0, y0, x1, y1 = COIL
    for x in range(x0, x1):
        for y in range(y0, y1):
            c.set(x, y, 'h' if (x - x0) % 2 == 0 else 'g')
        c.set(x, y0, 'i' if (x - x0) % 2 == 0 else 'h')
        c.set(x, y1 - 1, 'f')
        c.set(x, y0 + 3, 'k').set(x, y0 + 4, 'u')
    return c


def segment_corner():
    """Bending magnet (side: windings round the beam port; top: a coil round a square yoke) and the pipe flange."""
    c = Canvas()
    for x in range(8):                                                   # side: windings round the beam port
        for y in range(8):
            c.set(x, y, 'h' if x % 2 == 0 else 'g')
        c.set(x, 0, 'i' if x % 2 == 0 else 'h').set(x, 7, 'f')
    c.rect(0, 0, 1, 8, 'c').rect(7, 0, 1, 8, 'a').set(0, 0, 'd')
    c.rect(2, 2, 4, 4, 'm').rect(2, 2, 4, 1, 'k').rect(2, 2, 1, 4, 'k').rect(3, 5, 3, 1, 'n').rect(5, 3, 1, 3, 'n')
    x0, y0 = CORNER_TOP[:2]
    c.rect(x0, y0, 8, 8, 'h')
    c.bevel(x0, y0, 8, 8, 'i', 'f')
    for y in range(y0 + 1, y0 + 7, 2):
        c.rect(x0 + 1, y, 6, 1, 'g')
    c.rect(x0 + 2, y0 + 2, 4, 4, 'b').bevel(x0 + 2, y0 + 2, 4, 4, 'a', 'd')
    c.rect(x0 + 3, y0 + 3, 2, 2, 'u').set(x0 + 3, y0 + 3, 'k')
    x0, y0 = FLANGE[:2]
    c.rect(x0, y0, 6, 6, '3').bevel(x0, y0, 6, 6, '4', '1')
    c.rect(x0 + 1, y0 + 1, 4, 4, 'm').bevel(x0 + 1, y0 + 1, 4, 4, 'k', 'n')
    return c


def segment_pipe():
    """Glass beam pipe, sampled at its element position: walls at 6 and 9 (both ways), glints along it."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '6')
    for i in (6, 9):
        c.rect(0, i, 16, 1, '7').rect(i, 0, 1, 16, '7')
    for x in (2, 5, 11, 14):
        c.set(x, 7, '8').set(7, x, '8')
    return c


def write_collider():
    pal = cl_pal(2)
    run = range(CL_FRAMES)

    def anim(name, frames):
        write_anim('block', name, frames, pal, frametime=CL_FRAMETIME, interpolate=True)
    write_block('collider_controller_front', collider_front().rows(), pal)
    write_block('collider_controller_front_formed', collider_front_glow('formed').rows(), pal)
    anim('collider_controller_front_running', [collider_front_glow('on', f) for f in run])
    write_block('collider_controller_chamber', collider_chamber().rows(), pal)
    write_block('collider_controller_chamber_formed', collider_chamber_glow('formed').rows(), pal)
    anim('collider_controller_chamber_running', [collider_chamber_glow('on', f) for f in run])
    write_block('collider_controller_port', collider_port().rows(), pal)
    write_block('collider_controller_port_formed', collider_port_glow('formed').rows(), pal)
    anim('collider_controller_port_running', [collider_port_glow('on', f) for f in run])
    write_block('collider_controller_top', collider_top().rows(), pal)
    write_block('collider_controller_top_formed', collider_top_glow('formed').rows(), pal)
    anim('collider_controller_top_running', [collider_top_glow('on', f) for f in run])
    write_block('collider_controller_bottom', mc.bottom().rows(), pal)
    for tier, name in CL_SEG.items():
        p = cl_pal(tier)
        write_block(f'{name}_segment_base', segment_base().rows(), p)
        write_block(f'{name}_segment_parts', segment_parts().rows(), p)
        write_block(f'{name}_segment_corner', segment_corner().rows(), p)
        write_block(f'{name}_segment_pipe', segment_pipe().rows(), p)


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
    for k in range(3):                                                   # orbiting sparks, a third of a turn per loop
        a = -a0 / 3 + k * 2 * math.pi / 3
        x, y = 7.5 + math.cos(a) * 6.6, 7.5 + math.sin(a) * 6.6
        c.set(int(round(x)), int(round(y)), TE[4] if k == frame_no % 3 else TE[3])
    c.set(5, 5, '&')
    return c


# ---------------------------------------------------------------- output

def write_shells():
    for fam in ('bank', 'reactor'):
        pal = shell_pal(fam)
        write_block(f'{fam}_casing', shell_casing(fam).rows(), pal)
        write_block(f'{fam}_panel', shell_panel(fam).rows(), pal)
        write_block(f'{fam}_frame', shell_beam(fam).rows(), pal)
        write_block(f'{fam}_frame_corner', shell_corner(fam).rows(), pal)
        write_block(f'{fam}_frame_corner_glow', shell_corner_glow(fam).rows(), pal)
        for joined in range(16):
            write_block(f'{fam}_glass/{joined}', shell_glass(fam, joined).rows(), pal)
        for state in ('off', 'formed', 'on'):
            write_block(f'{fam}_controller_{state}', shell_controller(fam, state).rows(), pal)
        write_block(f'{fam}_controller_formed_glow', shell_controller_glow(fam).rows(), pal)
        write_anim('block', f'{fam}_controller_on_glow',
                   [shell_controller_glow(fam, t) for t in range(CTRL_FRAMES)], pal, frametime=CTRL_FRAMETIME)
    pal = shell_pal('bank')
    write_block('bank_frame_glow', shell_beam_glow('bank').rows(), pal)
    for mode, output in (('input', False), ('output', True)):
        write_block(f'bank_port_{mode}', bank_port(output).rows(), pal)
        write_block(f'bank_port_{mode}_glow', bank_port_glow(output).rows(), pal)
    pal = shell_pal('reactor')
    write_block('reactor_power_port', power_port().rows(), pal)
    write_block('reactor_power_port_glow', power_port_glow().rows(), pal)
    write_block('reactor_access_port', access_port().rows(), pal)


def main():
    write_shells()
    write_anim('block', 'cryo_coolant', [cryo_coolant(i) for i in range(8)], P, frametime=6, interpolate=True)
    for tier, name in ((1, 'flux'), (2, 'pyro'), (3, 'resonant')):
        write_block(f'{name}_amplifier', amplifier_side(tier).rows(), P)
        write_block(f'{name}_amplifier_top', amplifier_top(tier).rows(), P)
        write_block(f'{name}_amplifier_top_glow', amplifier_top_glow(tier).rows(), P)
        write_block(f'{name}_amplifier_crystal', amplifier_crystal(tier).rows(), P)
    write_block('graphite_damper', damper_side().rows(), P)
    write_block('graphite_damper_top', damper_top().rows(), P)

    write_bank_inside()

    write_block('spire_casing', casing(ST, 5).rows(), P)
    write_block('spire_base', spire_base_side().rows(), P)
    write_block('spire_base_glow', spire_base_side_glow().rows(), P)
    write_block('spire_base_top', spire_base_top().rows(), P)
    write_block('spire_crown_coil', crown_coil().rows(), P)
    write_block('spire_crown_ring', crown_ring().rows(), P)
    write_block('spire_crown_core', crown_core(False).rows(), P)
    write_block('spire_crown_core_lit', crown_core(True).rows(), P)

    write_block('spire_coil', spire_coil().rows(), P)
    write_block('spire_coil_glow', spire_coil_glow().rows(), P)
    write_block('spire_rail', spire_rail().rows(), P)

    write_collider()
    write_anim('item', 'strange_matter', [strange_matter(i) for i in range(8)], P, frametime=3)

    for prefix, r in (('spire', ST),):
        for state in ('off', 'formed', 'on'):
            write_block(f'{prefix}_controller_{state}', controller_front(r, state).rows(), P)
            write_block(f'{prefix}_controller_{state}_glow', controller_glow(r, state).rows(), P)


if __name__ == '__main__':
    main()
