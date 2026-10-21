"""Quantum Link textures: an open containment chamber. A dark armoured base and flat cap in tier metal (Mk1 copper,
Mk2 gold, Mk3 steel), four corner pillars with tier coloured light rails (Mk1 teal, Mk2 magma, Mk3 null purple), and
emitter pads on the chamber floor and ceiling. The block entity renderer draws the quantum core floating in the chamber
and the socket in the cap top in the network colour (tinting the white quantum_core texture).
Run: python3 scripts/textures/link.py
Block models, blockstates, loot and recipes come from scripts/data/link_data.py.

The shell textures are one profile of the block: row r covers block height pixels [15 - r, 16 - r]; the model reads
rows 0-2 for the cap (y 13-16) and rows 13-15 for the base (y 0-3). Pillar faces are 3 px wide (columns 0-2) and read
rows 3-12 (y 3-13). The top core socket is x 5-10, z 5-10.
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
PIPS = {'mk1': (7,), 'mk2': (5, 9), 'mk3': (3, 7, 11)}


def pal(tier):
    light, trim = TIERS[tier]
    p = {'k': '#0E1016', 'G': '#181B24', 'd': '#232733', 'D': '#323848', 'm': '#4A5268', 'M': '#6E7894'}
    p.update(zip('12345', MATERIALS[light]))
    t = MATERIALS[trim]
    p.update({'6': t[0], '7': t[1], '8': t[2], '9': t[3]})
    return p


def armour(c, x, y, w, h, seed=0):
    c.plate(x, y, w, h, 'kGdmM', seed, grain='Gd', density=0.15)


def shell(tier, front):
    """Cap band (rows 0-2) and base band (rows 13-15): tier metal edges around a dark armour band with light seams."""
    c = Canvas()
    armour(c, 0, 0, 16, 16, 3 if front else 1)
    # cap y 13-16: metal top edge, dark band with a seam, metal lip
    c.rect(0, 0, 16, 1, '9').rect(0, 1, 16, 1, 'd').rect(0, 2, 16, 1, '7')
    c.set(0, 0, '8').set(15, 0, '8').set(0, 2, '6').set(15, 2, '6')
    c.rect(4, 1, 8, 1, 'k')
    for x in (1, 14):
        c.set(x, 1, 'D')
    # base y 0-3: metal lip, armour band with the seam and the Mk pips (front), dark foot
    c.rect(0, 13, 16, 1, '8').rect(0, 14, 16, 1, 'D').rect(0, 15, 16, 1, 'k')
    c.set(0, 13, '7').set(15, 13, '6')
    c.rect(4, 14, 8, 1, 'k')
    if front:
        for x in PIPS[tier]:
            c.rect(x, 14, 2, 1, 'G')
    for x in (1, 14):
        c.set(x, 14, 'M')
    return c.rows()


def shell_glow(tier, front):
    """Full-bright seams of the cap and base bands, and the Mk pips on the front."""
    c = Canvas()
    c.rect(5, 1, 6, 1, '2').set(7, 1, '3').set(8, 1, '3')
    if front:
        for x in PIPS[tier]:
            c.rect(x, 14, 2, 1, '4')
    else:
        c.rect(5, 14, 6, 1, '2').set(7, 14, '3').set(8, 14, '3')
    return c.rows()


def pillar(tier):
    """Corner pillar face (columns 0-2, rows 3-12): armour plate between tier metal end caps, a vent and two bolts."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd')
    c.rect(0, 3, 3, 10, 'D').set(0, 3, 'M').set(2, 12, 'k')
    c.rect(0, 3, 3, 1, '8').set(2, 3, '6').rect(0, 12, 3, 1, '7').set(0, 12, '9')
    c.rect(1, 5, 1, 4, 'k').set(1, 9, 'm')
    c.set(0, 10, 'm').set(2, 10, 'G')
    return c.rows()


def pillar_glow(tier):
    """Light rail on the pillar faces that edge the chamber windows: a bright line with a dark break."""
    c = Canvas()
    c.rect(1, 4, 1, 8, '3')
    c.set(1, 4, '4').set(1, 11, '4').set(1, 7, '5').set(1, 8, '2')
    return c.rows()


def top(tier):
    """Flat cap top: armour plate, tier metal chamfered corners, etched channels to a dark socket ring around the core."""
    c = Canvas()
    armour(c, 0, 0, 16, 16, 5)
    c.frame(0, 0, 16, 16, '8')
    c.set(0, 0, '9').set(15, 15, '6').set(15, 0, '7').set(0, 15, '7')
    for x, y in ((1, 1), (12, 1), (1, 12), (12, 12)):
        c.rect(x, y, 3, 3, '7').set(x + (2 if x == 1 else 0), y + (2 if y == 1 else 0), 'D')
    # channels from the edges to the socket
    for i in (7, 8):
        c.rect(i, 1, 1, 2, 'k').rect(i, 13, 1, 2, 'k').rect(1, i, 2, 1, 'k').rect(13, i, 2, 1, 'k')
    c.frame(3, 3, 10, 10, 'G')
    c.frame(4, 4, 8, 8, 'k')
    c.rect(5, 5, 6, 6, 'G')
    return c.rows()


def top_glow(tier):
    """Full-bright light in the channels and the ring around the socket."""
    c = Canvas()
    c.frame(3, 3, 10, 10, '2')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, '4')
    for i in (7, 8):
        c.set(i, 1, '3').set(i, 14, '3').set(1, i, '3').set(14, i, '3')
        c.set(i, 3, '4').set(i, 12, '4').set(3, i, '4').set(12, i, '4')
    return c.rows()


def pad(tier):
    """Emitter pad on the chamber floor and ceiling: dark plate with rings."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'G')
    c.ring(7.5, 7.5, 5.0, 6.4, 'D')
    c.ring(7.5, 7.5, 3.2, 4.2, 'k')
    c.disc(7.5, 7.5, 2.2, 'd')
    return c.rows()


def pad_glow(tier):
    """Full-bright rings of the pad."""
    c = Canvas()
    c.ring(7.5, 7.5, 5.4, 6.2, '2')
    c.ring(7.5, 7.5, 1.4, 2.2, '4')
    for x, y in ((7, 1), (8, 14), (1, 8), (14, 7)):
        c.set(x, y, '3')
    return c.rows()


def bottom():
    p = pal('mk1')
    c = Canvas()
    armour(c, 0, 0, 16, 16, 7)
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
    """The item's top core and floating crystal (the renderer draws both in the world): pale cyan."""
    p = dict(zip('12345', MATERIALS['cyan']))
    top = Canvas()
    top.rect(5, 5, 6, 6, '3').rect(6, 6, 4, 4, '4').rect(7, 7, 2, 2, '5')
    crystal = Canvas()
    for y in range(16):
        crystal.rect(0, y, 16, 1, '5432'[min(3, abs(y - 7) // 2)])
    crystal.rect(4, 2, 2, 3, '5')
    return top.rows(), crystal.rows(), p


def main():
    for tier in TIERS:
        p = pal(tier)
        name = f'quantum_link_{tier}'
        write_block(f'{name}_shell', shell(tier, False), p)
        write_block(f'{name}_shell_front', shell(tier, True), p)
        write_block(f'{name}_shell_glow', shell_glow(tier, False), p)
        write_block(f'{name}_shell_front_glow', shell_glow(tier, True), p)
        write_block(f'{name}_pillar', pillar(tier), p)
        write_block(f'{name}_pillar_glow', pillar_glow(tier), p)
        write_block(f'{name}_top', top(tier), p)
        write_block(f'{name}_top_glow', top_glow(tier), p)
        write_block(f'{name}_pad', pad(tier), p)
        write_block(f'{name}_pad_glow', pad_glow(tier), p)
    rows, p = bottom()
    write_block('quantum_link_bottom', rows, p)
    write_block('quantum_core', core(), CORE)
    top_rows, crystal_rows, p = item_glow()
    write_block('quantum_link_item_core', top_rows, p)
    write_block('quantum_link_item_crystal', crystal_rows, p)
    print('link textures written')


if __name__ == '__main__':
    main()
