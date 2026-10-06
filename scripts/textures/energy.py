"""Energy module textures (big power): Fission Reactor, Capacitor Bank, Fusion Reactor.
Run: python3 scripts/textures/energy.py   (writes textures/block/*.png; models come from scripts/data/energy_models.py)

Three families, one construction (top-left light, bevelled plates, riveted corners, glow on separate overlays):
- Fission Reactor: dark graphite plating with hazard amber trim and a green glow (rods, gauges).
- Capacitor Bank: slate blue plating with cyan energy glow; capacitors show their cell metal, coils their windings.
- Fusion Reactor: null violet plating with a white-hot plasma glow.
Casing faces tile seamlessly: the frame of a casing is drawn as half-width seams so walls read as one surface.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import Canvas, grain, material, write_anim, write_block  # noqa: E402

DK, ST, SL, NU = 'abcde', 'fghij', 'lmnop', 'qrstu'
CU, RD, TE, CY, GR, PU, AM = '01234', '56789', 'ABCDE', 'FGHIJ', 'KLMNO', 'PQRST', 'UVWXY'
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
P.update(grain('vw', 'dark', 0.22))
P.update(grain('xy', 'slate', 0.22))
P.update({'!': '#2A2142', '#': '#382D55'})  # subtle grain of the violet plating
P.update(grain('$%', 'steel', 0.22))
P.update({
    '(': '#6CF09A38', ')': '#D8FFE4A8',      # reactor glass tint and glint
    '[': '#5FE3F038', ']': '#E6FAFFA8',      # bank glass tint and glint
    '{': '#9CF2FF', '}': '#D9FBFF', '<': '#5CC8E6', '>': '#2C7FA8', '^': '#F4FFFF',  # cryo ice
    '&': '#FFFFFF', '@': '#FFE6FF',          # white-hot plasma
})
GRAIN = {DK: 'vw', SL: 'xy', NU: '!#', ST: '$%'}

ACCENT = {DK: AM, SL: CY, NU: PU}
GLOW = {DK: GR, SL: CY, NU: PU}


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


# ---------------------------------------------------------------- fission inside

def fuel_rod_side():
    """Zircaloy tube between steel collars; a glowing green core shows through a slot. Tiles vertically."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'k')
    plate(c, 3, 0, 10, 16, ST, 51, bevel=False, vertical=True, density=0.3)
    c.rect(3, 0, 1, 16, ST[3]).rect(12, 0, 1, 16, ST[1])
    c.rect(2, 0, 12, 2, ST[1]).rect(2, 0, 12, 1, ST[3])                 # collar (top, half of a seam)
    c.rect(2, 14, 12, 2, ST[1]).rect(2, 15, 12, 1, ST[0])
    c.rect(6, 3, 4, 10, 'z').rect(7, 4, 2, 8, GR[1])                     # core slot
    c.set(6, 3, 'k')
    return c


def fuel_rod_glow():
    c = Canvas()
    c.rect(7, 4, 2, 8, GR[3])
    c.rect(7, 6, 1, 4, GR[4])
    return c


def fuel_rod_top():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'k')
    c.disc(7.5, 7.5, 6.2, ST[1]).disc(7.5, 7.5, 5.4, ST[2])
    c.ring(7.5, 7.5, 3.0, 3.9, ST[0])
    c.disc(7.5, 7.5, 2.6, GR[1])
    c.set(5, 5, ST[4])
    return c


def fuel_rod_top_glow():
    c = Canvas()
    c.disc(7.5, 7.5, 2.6, GR[3])
    c.disc(7.5, 7.5, 1.2, GR[4])
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


# ---------------------------------------------------------------- fusion

def fusion_coil_side():
    """Superconducting magnet: violet windings in a null frame with a plasma-facing slot."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, NU, 101, bevel=False, density=0.25)
    c.rect(0, 0, 16, 1, NU[3]).rect(0, 15, 16, 1, NU[0])
    c.rect(1, 2, 14, 12, 'k')
    for x in range(2, 14):
        c.rect(x, 3, 1, 10, PU[2] if x % 2 else PU[1])
        c.set(x, 3, PU[3])
    c.rect(2, 7, 12, 2, 'z').rect(3, 7, 10, 2, PU[1])
    return c


def fusion_coil_glow(frame_no):
    c = Canvas()
    for x in range(3, 13):
        lvl = 0.5 + 0.5 * math.sin(frame_no * 0.8 + x * 0.7)
        c.set(x, 7, '&' if lvl > 0.85 else PU[4] if lvl > 0.5 else PU[3])
        c.set(x, 8, PU[3] if lvl > 0.5 else PU[2])
    return c


def fusion_coil_top():
    c = casing(NU, 110)
    c.disc(7.5, 7.5, 5.8, 'k')
    c.ring(7.5, 7.5, 2.6, 5.3, PU[1]).ring(7.5, 7.5, 3.4, 4.4, PU[2])
    c.disc(7.5, 7.5, 2.0, 'z')
    return c


def fusion_casing():
    c = casing(NU, 120)
    c.set(7, 7, PU[2]).set(8, 8, PU[2])
    return c


# ---------------------------------------------------------------- output

def main():
    write_block('reactor_casing', hazard_trim(casing(DK, 1), AM).rows(), P)
    write_block('reactor_glass', glass(DK, '(', ')', 2).rows(), P)
    write_block('reactor_power_port', power_port(DK).rows(), P)
    write_block('reactor_power_port_glow', power_port_glow(DK).rows(), P)
    write_block('reactor_access_port', access_port(DK).rows(), P)
    write_block('reactor_fuel_rod', fuel_rod_side().rows(), P)
    write_block('reactor_fuel_rod_glow', fuel_rod_glow().rows(), P)
    write_block('reactor_fuel_rod_top', fuel_rod_top().rows(), P)
    write_block('reactor_fuel_rod_top_glow', fuel_rod_top_glow().rows(), P)
    write_anim('block', 'cryo_coolant', [cryo_coolant(i) for i in range(8)], P, frametime=6, interpolate=True)

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

    write_block('fusion_casing', fusion_casing().rows(), P)
    write_block('fusion_coil', fusion_coil_side().rows(), P)
    write_anim('block', 'fusion_coil_glow', [fusion_coil_glow(i) for i in range(8)], P, frametime=3)
    write_block('fusion_coil_top', fusion_coil_top().rows(), P)

    for prefix, r in (('reactor', DK), ('bank', SL), ('fusion', NU)):
        for state in ('off', 'formed', 'on'):
            write_block(f'{prefix}_controller_{state}', controller_front(r, state).rows(), P)
            write_block(f'{prefix}_controller_{state}_glow', controller_glow(r, state).rows(), P)


if __name__ == '__main__':
    main()
