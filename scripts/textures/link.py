"""Quantum Link textures: a dark flat-topped block with tier coloured light (Mk1 teal on copper, Mk2 magma on gold, Mk3
null purple on steel), dark windows and a top core the block entity renderer fills with the network colour (tinting the
white quantum_core texture).
Run: python3 scripts/textures/link.py
Block models, blockstates, loot and recipes come from scripts/data/link_data.py.

The side and front textures are one profile of the whole block: row r covers block height pixels [15 - r, 16 - r], so
every element of the model reads its own rows (uv v = 16 - top .. 16 - bottom):
rows 13-15 base (y 0-3), rows 3-12 body (y 3-13), rows 0-2 cap (y 13-16). The window on each body face is x 7-8,
rows 6-9 (y 6-10); the top core is x 5-10, z 5-10.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, write_block  # noqa: E402

# Per tier: light ramp ('12345' deep -> highlight) and trim metal ('6789' dark -> highlight).
TIERS = {
    'mk1': ('teal', 'copper'),
    'mk2': ('magma', 'gold'),
    'mk3': ('null', 'steel'),
}
PIPS = {'mk1': (7,), 'mk2': (5, 9), 'mk3': (4, 7, 10)}


def pal(tier):
    light, trim = TIERS[tier]
    p = {'k': '#14161E', 'G': '#20242E', 'd': '#2A2E3A', 'D': '#3C4254', 'm': '#565D78', 'M': '#7A83A3'}
    p.update(zip('12345', MATERIALS[light]))
    t = MATERIALS[trim]
    p.update({'6': t[0], '7': t[1], '8': t[2], '9': t[3]})
    return p


def plate(c, x, y, w, h, seed=0):
    c.plate(x, y, w, h, 'kGdmM', seed, grain='Gd', density=0.2)


def profile(tier, front):
    c = Canvas()
    plate(c, 0, 0, 16, 16, 3 if front else 1)
    # cap, y 13-16: trim metal band with a dark lip under it
    c.rect(0, 0, 16, 1, '8').rect(0, 1, 16, 1, '9').rect(0, 2, 16, 1, '7')
    c.set(0, 0, '7').set(15, 0, '7').set(0, 1, '8').set(15, 1, '8').set(0, 2, '6').set(15, 2, '6')
    for x in (3, 8, 12):
        c.set(x, 2, '8')
    # body, y 3-13: dark panel, shadow under the cap, dark foot
    c.rect(0, 3, 16, 10, 'd').rect(0, 3, 16, 1, 'k').rect(0, 4, 16, 1, 'D').rect(0, 12, 16, 1, 'G')
    # window frame (the renderer fills x 7-8, rows 6-9 with the network colour)
    c.rect(6, 5, 4, 6, 'k').rect(7, 6, 2, 4, 'G')
    c.set(6, 5, 'G').set(9, 5, 'G').set(6, 10, 'G').set(9, 10, 'G')
    if front:
        # Mk pips under the window, braces beside it
        for x in PIPS[tier]:
            c.rect(x, 11, 2, 1, 'k')
        for x in (3, 12):
            c.rect(x, 6, 1, 5, 'G').set(x, 6, 'k').set(x, 10, 'm')
    else:
        # vents beside the window
        for y in (6, 8):
            c.rect(3, y, 2, 1, 'k').rect(3, y + 1, 2, 1, 'm')
            c.rect(11, y, 2, 1, 'k').rect(11, y + 1, 2, 1, 'm')
        c.rect(7, 11, 2, 1, 'k')
    # corner pillars show at x 1-2 and 13-14 of the side faces
    # base, y 0-3: trim band, bolts, dark foot
    c.rect(0, 13, 16, 1, '8').rect(0, 14, 16, 1, 'D').rect(0, 15, 16, 1, 'k')
    for x in (2, 5, 10, 13):
        c.set(x, 14, 'M')
    return c.rows()


def profile_glow(tier, front):
    """Tier light: the Mk pips on the front, one pip on the other sides, a seam under the cap."""
    c = Canvas()
    if front:
        for x in PIPS[tier]:
            c.rect(x, 11, 2, 1, '4')
    else:
        c.rect(7, 11, 2, 1, '3').set(7, 11, '4')
    for x in (1, 14):
        c.set(x, 4, '2')
    return c.rows()


def top(tier):
    """Flat cap top: plating, tier metal corners, a dark socket around the core and a light ring around that."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, 5)
    c.frame(0, 0, 16, 16, '9')
    c.set(0, 0, '8').set(15, 15, '6')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rect(x, y, 2, 2, '8').set(x, y, '9').set(x + 1, y + 1, '6')
    c.frame(3, 3, 10, 10, 'G')
    c.frame(4, 4, 8, 8, 'k')
    c.rect(5, 5, 6, 6, 'G')
    return c.rows()


def top_glow(tier):
    """Full-bright light ring of the cap top (the rest is clear)."""
    c = Canvas()
    c.frame(3, 3, 10, 10, '2')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, '4')
    for i in (6, 9):
        c.set(i, 3, '3').set(i, 12, '3').set(3, i, '3').set(12, i, '3')
    return c.rows()


def trim(tier):
    """Corner pillars: vertical brushed tier metal with a dark seam."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '8')
    for x in range(16):
        for y in range(16):
            if (x * 7 + y * 3) % 11 == 0:
                c.set(x, y, '7')
    c.rect(0, 0, 4, 16, '9').rect(12, 0, 4, 16, '7').rect(7, 0, 2, 16, '6')
    return c.rows()


def bottom():
    p = pal('mk1')
    c = Canvas()
    plate(c, 0, 0, 16, 16, 7)
    c.rect(3, 3, 10, 10, 'D').frame(3, 3, 10, 10, 'k')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, 'M')
    return c.rows(), p


CORE = {'a': '#FFFFFF', 'b': '#F0F4F8', 'c': '#DCE3EA', 'e': '#C6D0DA'}


def core():
    """White core the renderer tints in the network colour: bright middle, darker rim."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'e')
    c.rect(2, 2, 12, 12, 'c')
    c.rect(4, 4, 8, 8, 'b')
    c.rect(6, 6, 4, 4, 'a')
    return c.rows()


def item_glow():
    """The item's core and windows (the renderer draws them in the world): pale cyan, rest clear.
    Returns the top core and the side window, each as rows of the face they go on."""
    p = dict(zip('12345', MATERIALS['cyan']))
    top = Canvas()
    top.rect(5, 5, 6, 6, '3').rect(6, 6, 4, 4, '4').rect(7, 7, 2, 2, '5')
    side = Canvas()
    side.rect(7, 6, 2, 4, '4').rect(7, 7, 2, 2, '5')
    return top.rows(), side.rows(), p


def main():
    for tier in TIERS:
        p = pal(tier)
        name = f'quantum_link_{tier}'
        write_block(f'{name}_side', profile(tier, False), p)
        write_block(f'{name}_front', profile(tier, True), p)
        write_block(f'{name}_side_glow', profile_glow(tier, False), p)
        write_block(f'{name}_front_glow', profile_glow(tier, True), p)
        write_block(f'{name}_top', top(tier), p)
        write_block(f'{name}_top_glow', top_glow(tier), p)
        write_block(f'{name}_trim', trim(tier), p)
    rows, p = bottom()
    write_block('quantum_link_bottom', rows, p)
    write_block('quantum_core', core(), CORE)
    top_rows, side_rows, p = item_glow()
    write_block('quantum_link_item_core', top_rows, p)
    write_block('quantum_link_item_window', side_rows, p)
    print('link textures written')


if __name__ == '__main__':
    main()
