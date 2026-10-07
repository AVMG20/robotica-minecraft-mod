"""Gear textures: tools, weapons, upgrade kits. Run: python3 scripts/textures/gear.py
Tools are handheld item models drawn on the diagonal (working end up-right, grip down-left), lit from the top-left.
Tier look: copper (Age 0) -> steel/cyan (1) -> servo teal (2) -> magma red (3) -> null purple/teal (4); every drill tier
adds to the silhouette so the tiers read apart at a glance."""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, write_block, write_item  # noqa: E402

# '12345' tier material (deep -> highlight), 'abcde' steel, '678' brass, 'w W v' wood, 'y Y z' tier glow
TIER = {
    'copper': ('copper', 'cyan'), 'steel': ('steel', 'cyan'), 'servo': (('#0E3540', '#14566A', '#2A8FA6', '#7FE3F5', '#E0FBFF'), 'amber'),
    'magma': ('magma', 'amber'), 'null': ('null', 'teal'),
}
SHADE = {'3': ('2', '4'), 'c': ('b', 'd'), 'W': ('w', 'v'), '7': ('6', '8')}


def pal(tier):
    main, glow = TIER[tier]
    ramp = MATERIALS[main] if isinstance(main, str) else main
    p = {'k': '#1E1A1A', 'K': '#0E0C0C'}
    p.update(dict(zip('12345', ramp)))
    p.update(dict(zip('abcde', MATERIALS['steel'])))
    b = MATERIALS['brass']
    p.update({'6': b[1], '7': b[2], '8': b[3]})
    p.update({'w': '#4A2E18', 'W': '#7A5230', 'v': '#A87A48'})
    g = MATERIALS[glow]
    p.update({'Z': g[1], 'y': g[2], 'Y': g[3], 'z': g[4]})
    return p


def stamp(c, x0, y0, x1, y1, ch, size=1):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        c.rect(x, y, size, size, ch)


def finish(c):
    c.auto_shade(SHADE)
    c.outline('k')
    return c.rows()


def handle(c, x0=2, y0=13, x1=8, y1=7):
    stamp(c, x0, y0, x1, y1, 'W', 2)


# ---------------------------------------------------------------- tools

def hammer():
    """Mallet: copper head between steel caps, a band of cyan in the middle, wooden handle."""
    c = Canvas()
    handle(c, 2, 13, 8, 7)
    c.rect(3, 2, 10, 6, '3')
    c.rect(3, 2, 3, 6, 'c').rect(10, 2, 3, 6, 'c')
    c.rect(6, 2, 4, 6, '3')
    c.auto_shade(SHADE)
    c.rect(7, 3, 2, 4, '2').set(7, 4, 'y').set(7, 5, 'Y')
    c.set(4, 3, 'e').set(11, 3, 'd')
    c.outline('k')
    return c.rows()


def axe():
    """Felling axe: a flared copper blade on one side of the haft top, steel cutting edge, steel poll on the other."""
    c = Canvas()
    handle(c, 2, 13, 10, 5)
    cx, cy = 9.0, 7.0                     # where the haft meets the head
    r2 = 2 ** 0.5
    for y in range(16):
        for x in range(16):
            u = ((cx - x) + (cy - y)) / r2          # outwards along the blade (up-left)
            v = ((x - cx) + (cy - y)) / r2          # along the haft (up-right)
            half = 1.3 + 0.55 * max(u, 0)           # flare towards the edge
            if -2.2 <= u <= 6.6 and abs(v - 0.8) <= half:
                if u > 6.6 - 1.4 * (1 - min(1.0, (half - abs(v - 0.8)) / 1.2)) + 0.3:
                    continue                          # rounded cutting edge
                ch = '3'
                if u > 5.2:
                    ch = 'd'
                elif v - 0.8 > half - 1.0:
                    ch = '4'
                elif v - 0.8 < -half + 1.0:
                    ch = '2'
                if u < -0.6:
                    ch = 'c' if v - 0.8 >= 0 else 'b'
                c.set(x, y, ch)
    c.set(8, 5, 'y').set(7, 6, 'Y')
    c.outline('k')
    return c.rows()


def drill(tier):
    """Pistol-grip drill on the diagonal. 1 plain bit, 2 battery pack and longer bit, 3 cooling fins and a burning
    tip, 4 a floating crystal and an energy bit."""
    c = Canvas()
    stamp(c, 5, 10, 8, 13, 'w', 2)                                      # grip
    stamp(c, 2, 10, 7, 5, '3', 4)                                       # barrel
    c.rect(1, 11, 3, 3, '2')                                            # rear cap
    if tier >= 2:
        c.rect(7, 13, 4, 2, 'c').rect(8, 13, 2, 1, 'y')                  # battery pack
    if tier == 3:
        for x, y in ((3, 6), (5, 4), (7, 2)):
            c.set(x, y, '3').set(x - 1, y + 1, '3')                     # cooling fins
    stamp(c, 9, 6, 10, 5, 'c', 2)                                       # chuck
    length = {1: 3, 2: 4, 3: 4, 4: 4}[tier]
    for i in range(length + 1):                                         # bit: a spiral cone
        x, y = 11 + i, 4 - i
        c.set(x, y, 'd' if i % 2 == 0 else 'b')
        if i < length - 1:
            c.set(x - 1, y, 'c' if i % 2 else 'e').set(x, y + 1, 'b')
    c.auto_shade(SHADE)
    c.draw(4, 7, ['yY', 'Zy'])                                          # status window
    c.set(3, 7, '5')
    if tier == 3:
        c.set(11 + length, 4 - length, 'z').set(10 + length, 4 - length, 'Y')
    if tier == 4:
        c.set(11 + length, 4 - length, 'z')
        for i in range(length):
            c.set(11 + i, 4 - i, 'y' if i % 2 else 'Y')
        c.draw(11, 9, ['.4.', '454', '.3.'])                            # floating crystal
        c.set(14, 7, 'y').set(13, 11, 'Y')
    c.outline('k')
    return c.rows()


def chainsaw():
    """Long steel guide bar on the diagonal ringed by chain teeth, a chunky tier body with a loop handle."""
    c = Canvas()
    stamp(c, 7, 8, 13, 2, 'c', 2)                                       # bar
    c.rect(2, 8, 6, 5, '3')                                             # body
    c.draw(2, 5, ['.ww.', 'w..w', 'w..w'])                              # loop handle
    c.rect(6, 13, 2, 2, 'w')                                            # rear grip
    c.auto_shade(SHADE)
    for i in range(7):                                                  # teeth along both edges
        c.set(7 + i, 7 - i, 'e' if i % 2 else '.')
        c.set(9 + i, 9 - i, 'a' if i % 2 == 0 else 'b')
    c.set(14, 1, 'd').set(15, 1, 'b')
    c.set(4, 10, 'y').set(5, 10, 'Y').set(3, 9, '5')
    c.outline('k')
    return c.rows()


# ---------------------------------------------------------------- weapons

def sword(gear_guard=False, glow_edge=False):
    c = Canvas()
    stamp(c, 2, 12, 4, 10, 'w', 2)                                      # grip
    c.rect(1, 13, 2, 2, '7')                                            # pommel
    stamp(c, 5, 9, 12, 2, '3', 2)                                       # blade
    c.set(13, 1, '3').set(14, 1, '3').set(13, 2, '3')
    if gear_guard:
        c.draw(3, 7, ['.7.7.', '77777', '.777.', '77777', '.7.7.'])
    else:
        stamp(c, 3, 8, 7, 12, '7', 2)
    c.auto_shade(SHADE)
    for i in range(7):                                                  # edge line
        c.set(6 + i, 8 - i, 'z' if glow_edge and i % 3 == 0 else '5' if not glow_edge else 'Y')
    if gear_guard:
        c.set(5, 9, 'b')
    c.outline('k')
    return c.rows()


def baton():
    c = Canvas()
    stamp(c, 2, 13, 7, 8, 'w', 2)                                        # rubber grip
    stamp(c, 7, 8, 11, 4, 'c', 2)                                        # steel rod
    stamp(c, 11, 4, 13, 2, '3', 2)                                       # coil head
    c.auto_shade(SHADE)
    for i in range(3):
        c.set(11 + i, 4 - i, 'y')
    c.set(12, 3, 'Y')
    c.set(14, 1, 'z').set(15, 0, 'Y').set(15, 2, 'y').set(13, 0, 'y')   # sparks
    c.draw(2, 9, ['.7', '7.'])
    c.outline('k')
    return c.rows()


def rivet_gun():
    c = Canvas()
    c.rect(2, 5, 11, 4, '3')                                             # barrel housing
    c.rect(13, 6, 2, 2, 'c')                                             # muzzle
    stamp(c, 3, 9, 4, 13, '2', 2)                                        # grip
    c.rect(4, 3, 6, 2, 'c')                                              # top magazine
    c.auto_shade(SHADE)
    c.rect(5, 4, 4, 1, '7').set(5, 4, '8')                               # rivet magazine
    c.rect(8, 6, 3, 2, 'K').set(9, 6, 'Y').set(10, 6, 'y').set(9, 7, 'y')
    c.rect(6, 9, 2, 1, 'b')
    c.set(15, 6, 'e')
    c.outline('k')
    return c.rows()


def lance():
    c = Canvas()
    stamp(c, 1, 14, 9, 6, '2', 2)                                        # shaft
    c.draw(9, 1, ['....4z', '...45Y', '..453.', '.343..', '.33...', 'c.....'])   # crystal head
    c.auto_shade(SHADE)
    for x, y in ((3, 11), (6, 8)):
        c.set(x, y, 'y')
    c.set(12, 4, 'Y')
    c.draw(7, 6, ['cc', 'c.'])
    c.outline('k')
    return c.rows()


def kit(level):
    """Tool upgrade kit: a wrench crossed over a cog in the tier material, one pip per level."""
    c = Canvas()
    c.disc(9.5, 6.5, 4.6, '3')
    for a in range(8):
        ang = a * math.pi / 4
        c.rect(round(9.5 + 5.2 * math.cos(ang)) - 0, round(6.5 + 5.2 * math.sin(ang)), 1, 1, '3')
    c.disc(9.5, 6.5, 1.6, '.')
    stamp(c, 2, 13, 8, 7, '7', 2)                                        # brass wrench handle
    c.draw(7, 4, ['77.77', '77.77', '77777'])                            # wrench jaw
    c.auto_shade(SHADE)
    for i in range(level):
        c.set(1 + i * 2, 15, 'Y').set(2 + i * 2, 15, '.')
    c.set(7, 4, '8').set(10, 3, '5')
    c.outline('k')
    return c.rows()

# ---------------------------------------------------------------- Tinker's Bench (block)

def planks(c, x0=0, y0=0, w=16, h=16):
    """Oak-like planks: rows of 4 px boards, dark seams, staggered joints, light top edge per board."""
    c.rect(x0, y0, w, h, 'W')
    for y in range(y0, y0 + h):
        if (y - y0) % 4 == 3:
            c.rect(x0, y, w, 1, 'w')
        elif (y - y0) % 4 == 0:
            c.rect(x0, y, w, 1, 'v')
    for y in range(y0, y0 + h, 4):
        joint = x0 + (5 if (y // 4) % 2 == 0 else 11)
        if joint < x0 + w:
            c.rect(joint, y, 1, 3, 'w')


def bench_top():
    """Work surface: planks with a steel plate holding a copper gear, and a glowing card slot."""
    c = Canvas()
    planks(c)
    c.rect(2, 2, 7, 7, 'c').frame(2, 2, 7, 7, 'b').rect(2, 2, 7, 1, 'd').set(2, 2, 'e')
    c.rect(4, 4, 3, 3, '3').set(4, 4, '4').set(6, 6, '2').set(5, 5, 'K')       # copper gear hub
    for x, y in ((5, 3), (3, 5), (7, 5), (5, 7)):
        c.set(x, y, '3')
    c.rect(10, 10, 4, 4, 'b').rect(11, 11, 2, 2, 'y')                         # card slot glow
    c.frame(0, 0, 16, 16, 'w')
    return c.rows()


def bench_side():
    """Side: planks under a steel table edge, with a hammer hanging on two hooks."""
    c = Canvas()
    planks(c)
    c.rect(0, 0, 16, 2, 'c').rect(0, 0, 16, 1, 'd').rect(0, 2, 16, 1, 'b')      # steel table edge
    c.rect(4, 5, 8, 3, '3').rect(4, 5, 8, 1, '4').rect(4, 7, 8, 1, '2')         # copper hammer head
    c.rect(7, 8, 2, 6, '7').set(7, 8, '8')                                      # brass handle
    c.set(3, 4, 'a').set(12, 4, 'a')                                            # hooks
    c.frame(0, 0, 16, 16, 'w')
    return c.rows()


def write_bench():
    write_block('tinkers_bench_top', bench_top(), pal('copper'))
    write_block('tinkers_bench_side', bench_side(), pal('copper'))


# ---------------------------------------------------------------- tool and weapon modules (16x16 cards)
# Same card shape as the Exo modules (one module system), but with a notched steel frame: tool modules on a brass card,
# weapon modules on a crimson card. A glyph window, gold contacts, one pip per level.

MOD_BASE = {'k': '#1E1A1A', 'f': '#4D5558', 'F': '#8D9599', 'o': '#FFB21E', 'Y': '#FFE08A',
            'A': '#FFF4D8', 'a': '#FFB86A'}
TOOL_CARD = {'m': '#B98A2E', 'M': '#E8C46A', 'n': '#3A2A10'}
WEAPON_CARD = {'m': '#9A2A30', 'M': '#E0606A', 'n': '#2A0C10'}

# 6 wide, 8 tall glyphs; X = glyph, x = dim glyph
MOD_GLYPHS = {
    'torch_placer': ['...X..', '..XX..', '..XXX.', '..XX..', '..xx..', '..xx..', '..xx..', '..xx..'],
    'armor_pierce': ['..X...', '.XXX..', 'xxXxx.', 'xxXxx.', 'xxXxx.', '.xXx..', '..X...', '..X...'],
    'chain_lightning': ['...XX.', '..XX..', '.XXXX.', '...XX.', '..XX..', '.XX...', '.X....', '......'],
    'ricochet': ['......', 'X....X', 'X....X', '.X..X.', '.X..X.', '..XX..', 'xxxxxx', '......'],
    'lifesteal': ['.X..X.', 'XXXXXX', 'XXXXXX', '.XXXX.', '..XX..', '..x...', '..x...', '..x...'],
}
MOD_LEVELS = {'torch_placer': 1, 'armor_pierce': 3, 'chain_lightning': 3, 'ricochet': 2, 'lifesteal': 1}
MOD_WEAPON = {'armor_pierce', 'chain_lightning', 'ricochet', 'lifesteal'}


def gear_module(name, level=0):
    c = Canvas()
    c.rect(3, 1, 10, 14, 'm').bevel(3, 1, 10, 14, 'M', 'n').frame(2, 0, 12, 16, 'k')
    # notched steel corners: the gear modules' mark
    for x, y in ((3, 1), (12, 1), (3, 14), (12, 14)):
        c.set(x, y, 'F')
    c.set(2, 0, '.').set(13, 0, '.').set(2, 15, '.').set(13, 15, '.')
    c.rect(4, 2, 8, 9, 'k').rect(5, 3, 6, 8, 'n')
    for gy, row in enumerate(MOD_GLYPHS[name]):
        for gx, ch in enumerate(row):
            if ch == 'X':
                c.set(5 + gx, 3 + gy, 'A')
            elif ch == 'x':
                c.set(5 + gx, 3 + gy, 'a')
    for x in (4, 6, 8, 10):
        c.rect(x, 12, 1, 2, 'o')
    for i in range(level):
        c.set(5 + 2 * i, 11, 'A')
    return c.rows()


def module_files():
    """(file name, glyph, level pips) for every tool and weapon module item."""
    out = []
    for name, levels in MOD_LEVELS.items():
        if levels == 1:
            out.append((f'{name}_module', name, 0))
        else:
            for level in range(1, levels + 1):
                out.append((f'{name}_module' if level == 1 else f'{name}_module_{level}', name, level))
    return out


def write_modules():
    for fname, name, level in module_files():
        colors = WEAPON_CARD if name in MOD_WEAPON else TOOL_CARD
        write_item(fname, gear_module(name, level), {**MOD_BASE, **colors})


# ---------------------------------------------------------------- Rivet Gun projectile (entity texture)

def rivet_texture():
    """16x16 entity texture. Top half: the shaft along u (back left, tip right), steel with two grooves and a white-hot
    tip. Bottom-left quarter: the head, a steel cap with a dark rim and a bright centre."""
    rows = [['.'] * 16 for _ in range(16)]
    shaft = ['G', 'G', 'g', 'g', 'g', 's', 's', 'k']           # light top -> dark bottom
    for y in range(8):
        for x in range(16):
            ch = shaft[y]
            if x in (4, 9) and 1 <= y <= 6:
                ch = 's'                                           # grooves
            if x >= 12:
                ch = 'o' if x < 14 else ('O' if x == 14 else 'Y')  # glowing tip
                if y in (0, 7):
                    ch = 'o'
            rows[y][x] = ch
    for y in range(8, 16):
        for x in range(8):
            edge = x in (0, 7) or y in (8, 15)
            centre = 2 <= x <= 5 and 10 <= y <= 13
            rows[y][x] = 's' if edge else ('Y' if centre and 3 <= x <= 4 and 11 <= y <= 12 else ('G' if centre else 'g'))
    return [''.join(r) for r in rows]


def write_rivet():
    from pixelart import ASSETS, PALETTE, write_png
    pal_rivet = {**PALETTE, 'Y': '#FFFBEA', 'O': '#FFE08A', 'o': '#FFB21E'}
    write_png(ASSETS / 'textures/entity/rivet.png', rivet_texture(), pal_rivet)


def main():
    write_bench()
    write_modules()
    write_rivet()
    write_item('tinkers_hammer', hammer(), pal('copper'), handheld=True)
    write_item('felling_axe', axe(), pal('copper'), handheld=True)
    write_item('bore_drill', drill(1), pal('copper'), handheld=True)
    write_item('chainsaw', chainsaw(), pal('steel'), handheld=True)
    write_item('servo_drill', drill(2), pal('servo'), handheld=True)
    write_item('magma_drill', drill(3), pal('magma'), handheld=True)
    write_item('null_drill', drill(4), pal('null'), handheld=True)
    write_item('gearblade', sword(gear_guard=True), pal('copper'), handheld=True)
    write_item('shock_baton', baton(), pal('steel'), handheld=True)
    write_item('rivet_gun', rivet_gun(), pal('servo'), handheld=True)
    write_item('arc_blade', sword(glow_edge=True), pal('magma'), handheld=True)
    write_item('null_lance', lance(), pal('null'), handheld=True)
    for level, tier in ((1, 'steel'), (2, 'servo'), (3, 'magma'), (4, 'null')):
        write_item(f'tool_upgrade_kit_{level}', kit(level), pal(tier))


if __name__ == '__main__':
    main()
