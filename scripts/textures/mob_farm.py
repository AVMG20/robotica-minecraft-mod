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
  m n o s     soul sand: black, dark, mid, light                    Z z    wither skull, its lit brow
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
    'm': '#0B0908', 'n': '#1E1712', 'o': '#2E241B', 's': '#433526', 'Z': '#585862', 'z': '#8C8C98',
}
WITHER_SCREEN = {'D': '#0D0A16', 'M': '#1C1530', 'N': '#2C2246'}

CTRL_FRAMES, CTRL_FRAMETIME = 32, 3          # working screen loop: 96 ticks, like the reactor screen


def pal(mob='golem'):
    p = mc.casing_palette(HAZARD_RED, **dict(zip(mc.BODY, FARM_BODY)), **FARM_LIGHT, **FARM_EXTRA)
    if mob == 'wither':
        p.update(WITHER_SCREEN)
    return p


# ---------------------------------------------------------------- shell

def farm_casing():
    """Loose casing: the shell plate (half seams, red bolts in the corners) round a sunk knurled tread plate."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(0, 0, 16, 16, 'd', 'a')
    c.bevel(3, 3, 10, 10, 'a', 'd')
    c.rect(4, 4, 8, 8, 'b')
    for y in range(4, 12):                                               # diagonal knurl: lit ridge, shadow below it
        for x in range(4, 12):
            if (x + y) % 4 == 0:
                c.set(x, y, 'd')
            elif (x + y) % 4 == 1:
                c.set(x, y, 'a')
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
    """Edge beam, seamless top to bottom: bolted rails either side of a dark glass channel (lit on the overlay)."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    c.rect(0, 0, 1, 16, 'd').rect(1, 0, 1, 16, 'c').rect(14, 0, 1, 16, 'a').rect(15, 0, 1, 16, 'K')
    en.channel(c, 'bank')                                                # 4 px conduit: k housing, u glass
    for y in (3, 11):
        c.set(3, y, 'd').set(3, y + 1, 'a').set(12, y, 'd').set(12, y + 1, 'a')
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

BOLT = ['..ab', '.ab.', 'abba', '.ba.', 'ba..', 'a...']


def farm_port():
    """Farm Port: a red terminal ring round a dark socket with the energy bolt, on a grilled hatch (FE in, loot out)."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, 'b').bevel(3, 3, 10, 10, 'a', 'd')
    c.grille(4, 4, 8, 8, 'K', 'a')
    c.disc(7.5, 7.5, 4.6, 'a')
    c.ring(7.5, 7.5, 3.3, 4.2, 'g').ring(7.5, 7.5, 3.3, 3.7, 'h')
    c.disc(7.5, 7.5, 3.0, 'D')
    c.draw(6, 5, [r.replace('a', '6').replace('b', 'r') for r in BOLT])
    return c


def farm_port_glow():
    c = Canvas()
    c.ring(7.5, 7.5, 3.3, 4.2, 'v').ring(7.5, 7.5, 3.3, 3.7, 'w')
    c.draw(6, 5, [r.replace('a', 'y').replace('b', 'Y') for r in BOLT])
    return c


# ---- impact ram
# The block model sinks the ram head 2 px into a 10 px bore on each side (replicator_models.py): the side texture has
# the hole, impact_ram_head the head face (3..12) and the bore wall (rows 14-15). The renderer (FarmRenderer) punches the
# same head out of the inner face, from textures/entity/impact_ram.png.

def ram_head(c, x, y):
    """10x10 ram head face: a polished steel striking plate in a red hazard metal rim, a nut where the rod sits."""
    for j in range(10):
        for i in range(10):
            if i in (0, 9) or j in (0, 9):
                ch = 'K'
            elif i == 1 or j == 1:
                ch = 'i'
            elif i == 8 or j == 8:
                ch = 'g'
            else:
                ch = '2'
            c.set(x + i, y + j, ch)
    c.set(x + 1, y + 8, 'h').set(x + 8, y + 1, 'h')
    c.rect(x + 2, y + 2, 6, 1, '4').rect(x + 2, y + 2, 1, 6, '3').set(x + 2, y + 2, 'W')
    c.rect(x + 2, y + 7, 6, 1, '0').rect(x + 7, y + 2, 1, 6, '1')
    c.rect(x + 4, y + 4, 2, 2, '1').set(x + 4, y + 4, '0').set(x + 5, y + 5, '3')
    return c


def impact_ram_side():
    """Housing face: the heavy shell round an open 10 px bore (the head sits 2 px down it)."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, '.')
    return c


def impact_ram_head():
    c = Canvas()
    ram_head(c, 3, 3)
    c.rect(3, 14, 10, 2, 'a')                                            # bore wall
    return c


def impact_ram_top():
    """Housing top: the hazard bracketed shell over the hydraulic cylinder's polished cap."""
    c = mc.shell(1)
    c.recess(3, 3, 10, 10, mc.BODY, fill='b')
    c.disc(7.5, 7.5, 4.2, 'K')
    c.disc(7.5, 7.5, 3.6, '1')
    c.disc(7.0, 7.0, 3.0, '2')
    c.disc(7.0, 7.0, 2.0, '3')
    c.disc(7.5, 7.5, 1.2, '0')
    c.set(5, 5, '4').set(6, 5, '4').set(5, 6, '4')
    return c


def ram_entity():
    """Moving ram (FarmRenderer, 32x16): head face 0,0-10,10; head edge 10,0-20,4 (u across, v front to back); rod
    20,0-24,16 (u across, v along); bore bottom 24,0-32,8 (drawn over the model's head while the ram is out)."""
    c = Canvas(32)
    ram_head(c, 0, 0)
    for i in range(10):
        c.set(10 + i, 0, 'h')
        c.set(10 + i, 1, '3').set(10 + i, 2, '2').set(10 + i, 3, '0')
    c.set(10, 1, '2').set(19, 2, '1')
    for y in range(16):
        c.set(20, y, '1').set(21, y, '3').set(22, y, '4').set(23, y, '1')
    for y in (3, 9, 14):
        c.set(22, y, 'W')
    c.rect(24, 0, 8, 8, 'K').rect(25, 1, 6, 6, 'a').rect(27, 3, 2, 2, 'K')
    return c


# ---- roof matrices

CELLS = (4, 7, 10)                           # x / y of the golem core's 2x2 cells


def golem_matrix():
    """The shell round a sunk iron core: a 3x3 grid of poppy red cells between iron bars (the cells glow)."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, '1')
    c.rect(3, 3, 10, 1, '0').rect(3, 3, 1, 10, '0').rect(3, 12, 10, 1, '2').rect(12, 3, 1, 10, '2')
    for b in (6, 9):                                                     # iron bars
        c.rect(4, b, 8, 1, '2').rect(b, 4, 1, 8, '2')
    for gx in CELLS:
        for gy in CELLS:
            c.rect(gx, gy, 2, 2, 'r')
    return c


def golem_matrix_glow():
    c = Canvas()
    for gx in CELLS:
        for gy in CELLS:
            c.rect(gx, gy, 2, 2, 'R').set(gx, gy, 'y')
    c.rect(7, 7, 2, 2, 'y').set(7, 7, 'Y')
    return c


SKULL = ['.ZZZZZZ.', 'ZzzZZZZZ', 'ZeeZZeeZ', 'ZeeZZeeZ', 'ZZZmmZZZ', '.ZZZZZZ.', '.ZmZmZm.']


def wither_matrix():
    """The shell round sunk soul sand holding a wither skull; its eye sockets glow violet, nose and teeth soul blue."""
    c = mc.shell(1)
    c.rect(3, 3, 10, 10, 'n')
    c.speckle(3, 3, 10, 10, 'mos', 0.35, 21)
    c.rect(3, 3, 10, 1, 'm').rect(3, 3, 1, 10, 'm')
    c.draw(4, 5, SKULL)
    c.replace('e', 'p', 4, 5, 8, 7)
    return c


def wither_matrix_glow():
    c = Canvas()
    c.draw(4, 5, [r.replace('Z', '.').replace('z', '.') for r in SKULL])
    c.replace('e', 'Q').replace('m', 'B')
    c.set(5, 7, 'S').set(9, 7, 'S').set(6, 8, 'P').set(10, 8, 'P')
    return c


# ---- controllers: a screen in the shell (energy.shell_controller), the mob on the overlay

GOLEM = ['.IIIIII.', 'IHHHHHHI', 'I.eNNe.I', 'IIINNIII', 'gIINNIIg', '.IINNII.', '..IIII..']
WITHER = [r.replace('Z', 'H').replace('m', '.').replace('e', 'E') for r in SKULL]     # the skull, sockets lit


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
    'wither': (WITHER, {'dim': {'H': 'P', 'z': 'Q', 'E': 'B'}, 'lit': {'H': 'Q', 'z': 'S', 'E': 'C'}}),
}


def farmer_glow(mob, t=None):
    """Formed (t None): the mob mark dim. Working: the mark lit, a status bar filling once per loop (red for the golem,
    soul blue for the wither); the eyes flash once a loop."""
    c = Canvas().rect(7, 1, 2, 1, 'L')
    rows, shades = ICON[mob]
    shade = shades['dim' if t is None else 'lit']
    c.draw(4, 4, [''.join(shade.get(ch, '.') for ch in r) for r in rows])
    if t is None:
        return c
    if t % 16 >= 14:
        c.replace('R', 'y').replace('C', 'E')
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
    write_block('impact_ram_head', impact_ram_head().rows(), p)
    write_block('impact_ram_top', impact_ram_top().rows(), p)
    write_block('golem_matrix', golem_matrix().rows(), p)
    write_block('golem_matrix_glow', golem_matrix_glow().rows(), p)
    write_block('wither_matrix', wither_matrix().rows(), p)
    write_block('wither_matrix_glow', wither_matrix_glow().rows(), p)
    write_png(ASSETS / 'textures/entity/impact_ram.png', ram_entity().rows()[:16], p, size=None)
    for mob, name in (('golem', 'iron_golem_farmer'), ('wither', 'wither_farmer')):
        mp = pal(mob)
        for state in ('off', 'formed', 'on'):
            write_block(f'{name}_{state}', farmer_controller(state).rows(), mp)
        write_block(f'{name}_formed_glow', farmer_glow(mob).rows(), mp)
        write_anim('block', f'{name}_on_glow', [farmer_glow(mob, t) for t in range(CTRL_FRAMES)], mp,
                   frametime=CTRL_FRAMETIME)


if __name__ == '__main__':
    main()
