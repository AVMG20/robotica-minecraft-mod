"""Mob farm textures: the shared 5x5x5 shell of the Iron Golem Farmer and the Wither Farmer, its Impact Rams and the two
roof matrices. Run: python3 scripts/textures/mob_farm.py   (writes textures/block/* and textures/entity/impact_ram.png)

The Capacitor Bank / Core Reactor shell language (energy.py) as a heavy crusher: dark steel body, red-orange hazard
brackets, tread plate, a red-orange light in the beam channels and corner lenses. Glow lives on separate overlays that
hold only the lit pixels. The shared parts use the red accent; the wither parts violet, black and soul blue.

Chars on top of the casing palette (machine_casing.py: k K, abcde body, fghij hazard metal, uvwx channel light, l L lamp):
  ( )         glass tint, glint           D M N      screen glass, grid, glare
  6           dim corner lens              01234      iron / polished steel (dark -> light), W white glint
  r R y Y     poppy core: dim, lit, hot, white          7 8        golem vine
  p P Q S T   violet: deep, dim, mid, lit, white         B C E      soul blue: dim, lit, white
  m n o s     soul sand plating: black, dark, mid, light            Z      wither bone
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import energy as en  # noqa: E402
import machine_casing as mc  # noqa: E402
from pixelart import ASSETS, MATERIALS, Canvas, material, write_anim, write_block, write_png  # noqa: E402

FARM_BODY = ('#14171A', '#22272B', '#323940', '#4A535B', '#6E7881')     # heavy dark steel
HAZARD_RED = ('#4A0F09', '#8C2415', '#E04A34', '#FF7A55', '#FFC2A6')    # bracket metal, form colour 0xE8493A
FARM_LIGHT = {'u': '#3A120C', 'v': '#E8493A', 'w': '#FF9A5A', 'x': '#FFE6C0'}
FARM_EXTRA = {
    '(': '#C8906840', ')': '#FFE8D4A8',                                  # smoky warm glass
    'D': '#140C0B', 'M': '#2A1714', 'N': '#3E2420',                      # screen glass, grid, glare
    '6': '#5A1A10',
    **material('01234', 'iron'), 'W': '#FFFFFF',
    'r': '#6E1410', 'R': '#D42A22', 'y': '#FF6A50', 'Y': '#FFD8C4',
    '7': '#2E6B24', '8': '#5FB04A',
    'p': '#140A28', 'P': '#3A2470', 'Q': '#6A4CC8', 'S': '#9A7CFF', 'T': '#E6DCFF',
    'B': '#0B5E6A', 'C': '#3CD8EA', 'E': '#CFFAFF',
    'm': '#0B0908', 'n': '#1E1712', 'o': '#2E241B', 's': '#433526', 'Z': '#34343A',
}
WITHER_SCREEN = {'D': '#0D0A16', 'M': '#1C1530', 'N': '#2C2246'}

CTRL_FRAMES, CTRL_FRAMETIME = 32, 3          # working screen loop: 96 ticks, like the reactor screen


def pal(mob='golem'):
    p = mc.casing_palette(HAZARD_RED, **dict(zip(mc.BODY, FARM_BODY)), **FARM_LIGHT, **FARM_EXTRA)
    if mob == 'wither':
        p.update(WITHER_SCREEN)
    return p


# ---------------------------------------------------------------- shell

def tread(c, x, y, w, h, phase=0):
    """Tread plate: raised 2 px dashes, alternating diagonals on a 4 px grid (lit top-left pixel, shade below)."""
    for gy in range(y, y + h - 1, 4):
        for gx in range(x, x + w - 1, 4):
            if ((gx - x) // 4 + (gy - y) // 4 + phase) % 2:
                c.set(gx, gy + 1, 'e').set(gx + 1, gy, 'd').set(gx + 1, gy + 1, 'a').set(gx, gy + 2, 'a')
            else:
                c.set(gx, gy, 'e').set(gx + 1, gy + 1, 'd').set(gx + 1, gy + 2, 'a').set(gx + 2, gy + 1, 'a')
    return c


def farm_casing():
    """Loose casing: a heavy bevelled plate, red bolts in the corners, a sunk tread plate with a hazard strip."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(0, 0, 16, 16, 'd', 'a')
    c.recess(3, 3, 10, 10, mc.BODY, fill='b')
    tread(c, 4, 4, 8, 6)
    c.hazard(4, 10, 8, 2, 'h', 'k')
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        en.bolt(c, x, y)
    return c


def farm_panel():
    """Formed wall: one calm tread plate surface across the wall, ribs every 8 px."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    for x in (3, 11):
        c.rect(x, 0, 1, 16, 'a').rect(x + 1, 0, 1, 16, 'c')
    for gx, gy in ((7, 2), (7, 10), (14, 6), (0, 14)):
        c.set(gx, gy, 'c').set(gx + 1, gy + 1, 'c')
    for x, y in ((7, 6), (15, 14)):
        c.set(x, y, 'd').set(x, y + 1, 'a')
    return c


def farm_frame():
    """Edge beam, seamless top to bottom: bolted rails either side of a dark glass channel (lit on the overlay),
    hazard ticks on the rails every 8 px."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    c.rect(0, 0, 1, 16, 'd').rect(1, 0, 1, 16, 'c').rect(14, 0, 1, 16, 'a').rect(15, 0, 1, 16, 'K')
    en.channel(c, 'bank')                                                # 4 px conduit: k housing, u glass
    for y in (3, 11):
        c.set(3, y, 'd').set(3, y + 1, 'a').set(12, y, 'd').set(12, y + 1, 'a')
    for y in (7, 15):
        c.rect(2, y, 3, 1, 'h').rect(11, y, 3, 1, 'h').set(2, y, 'g').set(13, y, 'g')
    return c


def farm_frame_glow():
    """The channel lit red-orange, a hot glint every 8 px."""
    c = Canvas()
    c.rect(7, 0, 2, 16, 'v')
    for y in (3, 11):
        c.set(7, y, 'w').set(8, y, 'w').set(7, y + 1, 'w').set(7, y - 1, 'x')
    return c


def farm_frame_corner():
    """Corner cap: a small shell round a hazard metal boss holding a red lens."""
    c = mc.shell(1)
    c.draw(4, 4, [r.translate(str.maketrans('dcbau', 'ihgf6')) for r in en.BOSS])
    return c


def farm_frame_corner_glow():
    c = Canvas()
    c.rect(7, 7, 2, 2, 'v').set(7, 7, 'x').set(8, 7, 'w').set(7, 8, 'w')
    return c


def farm_glass(joined):
    return en.shell_glass('farm', joined)                                # the reactor scheme, '(' ')' warm smoke


# ---- port

BOLT = ['.wx', 'wx.', 'xxx', '.xw', 'xw.', 'w..']
CHUTE = ['wwww', 'vwwv', '.vv.', '.vv.', '.wv.', '..v.']


def farm_port():
    """Farm Port: a dark socket with the energy bolt (FE in) beside a hopper chute (loot out), marks dim."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, 'D').bevel(3, 3, 10, 10, 'a', 'd')
    c.draw(4, 5, [r.replace('w', '6').replace('x', 'r') for r in BOLT])
    c.rect(7, 4, 1, 8, 'b')                                              # divider
    c.draw(8, 5, [r.replace('w', '6').replace('v', 'r') for r in CHUTE])
    return c


def farm_port_glow():
    c = Canvas()
    c.draw(4, 5, BOLT).replace('x', 'Y').replace('w', 'y')
    c.draw(8, 5, CHUTE)
    return c


# ---- impact ram

def hazard_ring(c, cx, cy, r0, r1, a='h', b='k', n=10):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if r0 <= d <= r1:
                ang = (math.atan2(y - cy, x - cx) + math.pi) / (2 * math.pi)
                c.set(x, y, a if int(ang * n) % 2 == 0 else b)
    return c


def impact_ram_side():
    """Piston housing face: a heavy shell with a round polished ram head in a red hazard rim."""
    c = mc.shell(1)
    c.disc(7.5, 7.5, 6.0, 'a')
    hazard_ring(c, 7.5, 7.5, 4.3, 5.6)
    c.ring(7.5, 7.5, 5.6, 6.0, 'K')
    c.disc(7.5, 7.5, 4.2, '2')
    for y in range(16):                                                  # polished: lit top-left, shade bottom-right
        for x in range(16):
            if c.get(x, y) == '2':
                t = (x - 7.5) + (y - 7.5)
                c.set(x, y, '3' if t < -3 else '1' if t > 3 else '2')
    c.ring(7.5, 7.5, 3.6, 4.2, '1')
    c.line(5, 5, 6, 4, 'W').set(5, 6, '4')
    c.disc(7.5, 7.5, 1.2, 'K').set(7, 7, '1')                            # centre bolt
    return c


def impact_ram_top():
    """Housing top: the hazard bracketed shell over a vented hydraulic block round the cylinder cap."""
    c = mc.shell(1)
    c.recess(3, 3, 10, 10, mc.BODY, fill='b')
    c.vents(4, 4, 8, 8, mc.BODY)
    c.disc(7.5, 7.5, 3.4, 'K')
    c.disc(7.5, 7.5, 2.8, '1')
    c.disc(7.0, 7.0, 2.0, '2')
    c.set(6, 6, '4').set(7, 6, '3').set(6, 7, '3')
    c.set(7, 8, '0').set(8, 8, '0').set(8, 7, '0')
    return c


def ram_entity():
    """Moving ram head (entity renderer, every box face maps one half). Rows 0-7: the head plate, polished steel in a
    red hazard rim. Rows 8-15: the rod, chrome streaks along it."""
    c = Canvas()
    for y in range(8):
        for x in range(16):
            c.set(x, y, '3' if y < 3 else '2' if y < 6 else '1')
    c.rect(2, 2, 12, 1, '4').rect(3, 2, 3, 1, 'W')
    c.rect(1, 1, 14, 1, '2')
    c.rect(1, 6, 14, 1, '0')
    for x in range(16):                                                  # hazard rim top and bottom
        c.set(x, 0, 'h' if (x // 2) % 2 == 0 else 'k')
        c.set(x, 7, 'k' if (x // 2) % 2 == 0 else 'h')
    for y in range(1, 7):
        c.set(0, y, 'h' if y % 2 else 'f').set(15, y, 'f' if y % 2 else 'h')
    c.set(4, 4, '0').set(11, 4, '0')                                     # bolt heads
    c.set(4, 3, '4').set(11, 3, '4')
    rod = '01234W32'
    for y in range(8, 16):
        c.rect(0, y, 16, 1, rod[y - 8])
    for x, y, ch in ((3, 11, '3'), (4, 11, '3'), (10, 12, 'W'), (11, 12, 'W'), (7, 10, '4'), (13, 14, '1'),
                     (1, 13, '4'), (8, 9, '2')):
        c.set(x, y, ch)
    return c


# ---- roof matrices

CELLS = (4, 7, 10)                           # x / y of the golem core's 2x2 cells


def golem_matrix():
    """Iron block plating round a sunk core grid of poppy red cells between iron bars."""
    c = Canvas()
    c.plate(0, 0, 16, 16, '01234', 11, density=0.25)
    c.bevel(1, 1, 14, 14, '3', '1')
    c.recess(3, 3, 10, 10, '01234', fill='2')
    for gx in CELLS:
        for gy in CELLS:
            c.rect(gx, gy, 2, 2, 'r')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rect(x, y, 2, 2, '3').set(x, y, '4').set(x + 1, y + 1, '1')
    return c


def golem_matrix_glow():
    c = Canvas()
    for gx in CELLS:
        for gy in CELLS:
            c.rect(gx, gy, 2, 2, 'R').set(gx, gy, 'y')
    c.rect(7, 7, 2, 2, 'y').set(7, 7, 'Y')
    return c


SKULL = ['.ZZZZ.', 'ZZZZZZ', 'ZmZZmZ', 'ZZZZZZ', '.Z..Z.']
SKULLS = ((5, 2), (1, 9), (10, 9))


def wither_matrix():
    """Soul sand and black plating with three wither skull sockets; their eyes glow violet."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'n')
    c.speckle(0, 0, 16, 16, 'mos', 0.35, 21)
    c.bevel(0, 0, 16, 16, 's', 'm')
    for x, y in SKULLS:
        c.rect(x - 1, y - 1, 8, 7, 'm')
        c.rect(x - 1, y + 5, 8, 1, 'o')
        c.draw(x, y, SKULL)
        c.set(x + 1, y, 'c').set(x, y + 1, 'c')                          # bone highlight
        c.set(x + 1, y + 2, 'p').set(x + 4, y + 2, 'p')
    return c


def wither_matrix_glow():
    c = Canvas()
    for x, y in SKULLS:
        c.set(x + 1, y + 2, 'S').set(x + 4, y + 2, 'S')
        c.set(x + 1, y + 1, 'Q').set(x + 4, y + 1, 'Q')
        c.set(x + 2, y + 4, 'P').set(x + 3, y + 4, 'P')
    x, y = SKULLS[0]
    c.set(x + 1, y + 2, 'T').set(x + 4, y + 2, 'T')
    return c


# ---- controllers: a screen in the shell (energy.shell_controller), the mob on the overlay

GOLEM = ['.IIIIII.', 'IHHHHHHI', 'I.eNNe.I', 'IIINNIII', 'gIINNIIg', '.IINNII.', '..IIII..']
WITHER = ['.HHHHHH.', '.HeHHeH.', '.HHHHHH.', '...HH...', 'sss..sss', 'ses..ses', 'sss..sss']


def farmer_controller(state):
    c = mc.shell(1)
    x, y, w, h = en.SCREEN
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


ICON = {
    'golem': (GOLEM, {'dim': {'I': '1', 'H': '2', 'N': '0', 'e': 'r', 'g': '7'},
                      'lit': {'I': '3', 'H': '4', 'N': '2', 'e': 'R', 'g': '8'}}),
    'wither': (WITHER, {'dim': {'H': 'P', 's': 'P'}, 'lit': {'H': 'S', 's': 'Q'}}),
}


def farmer_glow(mob, t=None):
    """Formed (t None): the mob mark dim. Working: the mark lit, a status bar filling once per loop (red for the golem,
    soul blue for the wither); the golem's eyes flash, the wither's heads light their eyes one after another."""
    c = Canvas().rect(7, 1, 2, 1, 'L')
    rows, shades = ICON[mob]
    shade = shades['dim' if t is None else 'lit']
    c.draw(4, 4, [''.join(shade.get(ch, '.') for ch in r) for r in rows])
    if t is None:
        return c
    if mob == 'golem':
        if t % 16 >= 14:
            c.replace('R', 'y')
    else:
        head = t * 3 // CTRL_FRAMES                                      # centre, left, right
        for i, (x, y) in enumerate(((6, 5), (9, 5), (5, 9), (10, 9))):
            c.set(x, y, 'T' if (0 if i < 2 else i - 1) == head else 'S')
    bar, hot = ('v', 'x') if mob == 'golem' else ('C', 'E')
    n = 1 + t * 8 // CTRL_FRAMES
    c.rect(4, 11, n, 1, bar).set(3 + n, 11, hot)
    return c


# ---------------------------------------------------------------- output

def main():
    p = pal()
    write_block('farm_casing', farm_casing().rows(), p)
    write_block('farm_panel', farm_panel().rows(), p)
    write_block('farm_frame', farm_frame().rows(), p)
    write_block('farm_frame_glow', farm_frame_glow().rows(), p)
    write_block('farm_frame_corner', farm_frame_corner().rows(), p)
    write_block('farm_frame_corner_glow', farm_frame_corner_glow().rows(), p)
    for joined in range(16):
        write_block(f'farm_glass/{joined}', farm_glass(joined).rows(), p)
    write_block('farm_port', farm_port().rows(), p)
    write_block('farm_port_glow', farm_port_glow().rows(), p)
    write_block('impact_ram', impact_ram_side().rows(), p)
    write_block('impact_ram_top', impact_ram_top().rows(), p)
    write_block('golem_matrix', golem_matrix().rows(), p)
    write_block('golem_matrix_glow', golem_matrix_glow().rows(), p)
    write_block('wither_matrix', wither_matrix().rows(), p)
    write_block('wither_matrix_glow', wither_matrix_glow().rows(), p)
    write_png(ASSETS / 'textures/entity/impact_ram.png', ram_entity().rows(), p)
    for mob, name in (('golem', 'iron_golem_farmer'), ('wither', 'wither_farmer')):
        mp = pal(mob)
        for state in ('off', 'formed', 'on'):
            write_block(f'{name}_{state}', farmer_controller(state).rows(), mp)
        write_block(f'{name}_formed_glow', farmer_glow(mob).rows(), mp)
        write_anim('block', f'{name}_on_glow', [farmer_glow(mob, t) for t in range(CTRL_FRAMES)], mp,
                   frametime=CTRL_FRAMETIME)


if __name__ == '__main__':
    main()
