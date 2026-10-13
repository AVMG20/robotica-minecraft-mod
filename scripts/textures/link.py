"""Quantum Link textures: a dark pedestal with tier coloured light (Mk1 teal on copper, Mk2 magma on gold, Mk3 null purple
on steel) and the white crystal the block entity renderer tints in the network colour.
Run: python3 scripts/textures/link.py
Block models, blockstates, loot and recipes come from scripts/data/link_data.py.

The side and front textures are one profile of the whole pedestal: row r covers block height pixels [15 - r, 16 - r], so
every element of the model reads its own rows (uv v = 16 - top .. 16 - bottom):
rows 13-15 base (y 0-3), rows 5-12 body (y 3-11), rows 3-4 cap (y 11-13), row 2 emitter (y 13-14).
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
    # emitter rim, y 13-14 (only x 5-10 shows)
    c.rect(5, 2, 6, 1, '2').set(5, 2, 'k').set(10, 2, 'k').set(7, 2, '3').set(8, 2, '3')
    # cap, y 11-13: trim metal lip
    c.rect(0, 3, 16, 1, '9').rect(0, 4, 16, 1, '7')
    c.set(0, 3, '8').set(15, 3, '8').set(0, 4, '6').set(15, 4, '6')
    for x in (3, 8, 12):
        c.set(x, 4, '8')
    # body, y 3-11: dark panel, shadow under the cap, dark foot
    c.rect(0, 5, 16, 8, 'd').rect(0, 5, 16, 1, 'k').rect(0, 12, 16, 1, 'G')
    c.rect(0, 6, 16, 1, 'D')
    if front:
        # lens ring with the Mk pips under it
        c.rect(6, 6, 4, 4, 'k')
        c.rect(7, 7, 2, 2, '2')
        c.set(6, 6, 'd').set(9, 6, 'd').set(6, 9, 'd').set(9, 9, 'd')
        for x in PIPS[tier]:
            c.rect(x, 11, 2, 1, 'k')
        for x in (2, 13):
            c.rect(x, 7, 1, 4, 'G').set(x, 7, 'k')
    else:
        # light slit with vents beside it
        c.rect(6, 6, 4, 6, 'k').rect(7, 7, 2, 4, '1')
        for y in (7, 9):
            c.rect(3, y, 2, 1, 'k').rect(3, y + 1, 2, 1, 'm')
            c.rect(11, y, 2, 1, 'k').rect(11, y + 1, 2, 1, 'm')
    # corner pillars show at x 1-2 and 13-14 of the side faces
    # base, y 0-3: trim band, bolts, dark foot
    c.rect(0, 13, 16, 1, '8').rect(0, 14, 16, 1, 'D').rect(0, 15, 16, 1, 'k')
    for x in (2, 5, 10, 13):
        c.set(x, 14, 'M')
    return c.rows()


def profile_glow(tier, front):
    c = Canvas()
    if front:
        c.rect(7, 7, 2, 2, '4').set(7, 7, '5')
        for x in PIPS[tier]:
            c.rect(x, 11, 2, 1, '4')
    else:
        c.rect(7, 7, 2, 4, '3').rect(7, 8, 2, 2, '4').set(7, 8, '5')
    c.set(7, 2, '4').set(8, 2, '4')
    return c.rows()


def top(tier):
    """Cap top: plating with a light ring around the emitter and tier metal corners."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, 5)
    c.frame(1, 1, 14, 14, 'k')
    c.ring(7.5, 7.5, 3.6, 5.2, 'k')
    c.ring(7.5, 7.5, 4.1, 4.7, '2')
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rect(x, y, 2, 2, '8').set(x, y, '9').set(x + 1, y + 1, '6')
    return c.rows()


def emitter(tier):
    """Emitter disc seen from above (full bright): bright core, rings."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    c.ring(7.5, 7.5, 5.5, 8.0, '2')
    c.ring(7.5, 7.5, 3.0, 4.2, '4')
    c.disc(7.5, 7.5, 1.8, '5')
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


CRYSTAL = {'a': '#FFFFFF', 'b': '#F0F4F8', 'c': '#DCE3EA', 'e': '#C6D0DA', 'f': '#AEBAC6'}


def crystal():
    """White facet the renderer tints: light at the apex (top middle), darker towards the base, two glints."""
    c = Canvas()
    for y in range(16):
        c.rect(0, y, 16, 1, 'abcef'[min(4, y // 3)])
    for i in range(6):
        c.set(7 - i // 2, 2 + i, 'a').set(8 + i // 3, 3 + i, 'b')
    return c.rows()


def crystal_item():
    """The item's small crystal: pale cyan."""
    p = dict(zip('12345', MATERIALS['cyan']))
    c = Canvas()
    for y in range(16):
        c.rect(0, y, 16, 1, '5432'[min(3, y // 4)])
    c.rect(2, 2, 3, 3, '5')
    return c.rows(), p


def main():
    for tier in TIERS:
        p = pal(tier)
        name = f'quantum_link_{tier}'
        write_block(f'{name}_side', profile(tier, False), p)
        write_block(f'{name}_front', profile(tier, True), p)
        write_block(f'{name}_side_glow', profile_glow(tier, False), p)
        write_block(f'{name}_front_glow', profile_glow(tier, True), p)
        write_block(f'{name}_top', top(tier), p)
        write_block(f'{name}_emitter', emitter(tier), p)
        write_block(f'{name}_trim', trim(tier), p)
    rows, p = bottom()
    write_block('quantum_link_bottom', rows, p)
    write_block('quantum_crystal', crystal(), CRYSTAL)
    rows, p = crystal_item()
    write_block('quantum_crystal_item', rows, p)
    print('link textures written')


if __name__ == '__main__':
    main()
