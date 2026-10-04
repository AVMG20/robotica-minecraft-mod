"""Gear textures: tools, weapons, upgrade kits. Run: python3 scripts/textures/gear.py
Tools are handheld item models. Tier look: copper (Age 0) -> steel/cyan (1-2) -> magma red (3) -> null purple/teal (4)."""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_item  # noqa: E402

# Tier palettes: m = body, M = light, n = dark, a = accent, A = glowing accent
COPPER = {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22', 'a': '#5FE3F0', 'A': '#D8FBFF'}
STEEL = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#4D5558', 'a': '#5FE3F0', 'A': '#D8FBFF'}
SERVO = {'m': '#2A8FA6', 'M': '#7FE3F5', 'n': '#14566A', 'a': '#FFB21E', 'A': '#FFE08A'}
MAGMA = {'m': '#C23A12', 'M': '#FF7A2E', 'n': '#6E1A06', 'a': '#FFE08A', 'A': '#FFFFFF'}
NULL = {'m': '#5A33A0', 'M': '#A884F0', 'n': '#2A1560', 'a': '#5AD6BE', 'A': '#D6FFF5'}


def pal(*dicts):
    out = dict(PALETTE)
    for d in dicts:
        out.update(d)
    return out


def line(c, x0, y0, x1, y1, ch, thick=1):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        c.set(x, y, ch)
        if thick >= 2:
            c.set(x + 1, y, ch)
        if thick >= 3:
            c.set(x, y + 1, ch)


def handle(c, x0=2, y0=13, x1=9, y1=6, dark='w', light='W'):
    line(c, x0, y0, x1, y1, dark, 2)
    line(c, x0, y0 - 1, x1 - 1, y1, light, 1)


def hammer(p):
    c = Canvas()
    handle(c, 2, 14, 10, 6)
    # head across the end of the handle
    line(c, 7, 2, 13, 8, 'm', 3)
    line(c, 7, 1, 12, 6, 'M', 1)
    line(c, 8, 4, 13, 9, 'n', 1)
    c.rect(10, 4, 2, 2, 'a')
    c.outline('k')
    return c.rows()


def stamp(c, x0, y0, x1, y1, ch, size):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        c.rect(x, y, size, size, ch)


def axe(p):
    c = Canvas()
    handle(c, 2, 14, 10, 6)
    # wedge shaped blade: wide cutting edge on the right, tapering into the handle top
    for y in range(1, 9):
        for x in range(7, 15):
            reach = 14 - abs(y - 4.5) * 1.1
            if x <= reach and x >= 7 + abs(y - 4.5) * 0.4 and abs(y - 4.5) <= 3.6:
                c.set(x, y, 'm')
    for x in range(8, 14):
        c.set(x, 1 if c.get(x, 1) != '.' else 2, 'M')
    c.set(13, 4, 'M').set(14, 4, 'G').set(14, 5, 'G')
    for x in range(8, 13):
        c.set(x, 8 if c.get(x, 8) != '.' else 7, 'n')
    c.set(9, 4, 'a')
    c.outline('k')
    return c.rows()


def drill(p):
    c = Canvas()
    handle(c, 2, 14, 7, 9, 'n', 'm')
    # motor housing: a fat block along the diagonal
    stamp(c, 6, 10, 9, 7, 'm', 4)
    stamp(c, 6, 10, 9, 7, 'M', 1)
    line(c, 6, 13, 12, 7, 'n', 1)
    c.set(8, 10, 'a').set(9, 9, 'A').set(7, 11, 'a')
    # chuck and bit
    stamp(c, 12, 5, 13, 4, 'G', 2)
    stamp(c, 13, 4, 14, 2, 'g', 2)
    c.set(14, 1, 'G').set(15, 0, 'Y')
    c.outline('k')
    return c.rows()


def chainsaw(p):
    c = Canvas()
    # body with handle
    c.rect(2, 8, 6, 5, 'm').bevel(2, 8, 6, 5, 'M', 'n')
    c.rect(3, 6, 4, 2, 'n')
    c.set(4, 10, 'a').set(5, 10, 'A')
    # guide bar
    c.rect(8, 9, 7, 3, 'g')
    c.rect(8, 9, 7, 1, 'G')
    for x in range(8, 15, 2):
        c.set(x, 12, 's')
        c.set(x + 1, 8, 'G') if x + 1 < 15 else None
    c.set(14, 10, 'g')
    c.outline('k')
    return c.rows()


def sword(p, blade_hi='G', blade='g', blade_lo='s'):
    c = Canvas()
    line(c, 3, 13, 5, 11, 'w', 2)          # grip
    line(c, 4, 10, 8, 14, 'm', 1)          # guard across the blade
    line(c, 3, 11, 7, 15, 'n', 1)
    line(c, 5, 10, 13, 2, blade, 2)        # blade
    line(c, 5, 9, 13, 1, blade_hi, 1)
    line(c, 6, 11, 14, 3, blade_lo, 1)
    c.set(2, 14, 'M').set(1, 15, 'a')
    c.outline('k')
    return c.rows()


def gearblade(p):
    c = Canvas()
    rows = sword(p, 'C', 'c', 'd')
    c = Canvas()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                c.set(x, y, ch)
    # a little gear in the guard
    c.rect(6, 12, 2, 2, 'B').set(6, 12, 'b')
    return c.rows()


def baton(p):
    c = Canvas()
    line(c, 2, 14, 10, 6, 'n', 2)
    line(c, 2, 13, 9, 6, 'm', 1)
    line(c, 10, 6, 13, 3, 'g', 3)
    line(c, 11, 5, 14, 2, 'G', 1)
    c.set(14, 1, 'A').set(13, 1, 'a').set(15, 2, 'a').set(15, 0, 'a')
    c.set(5, 11, 'a').set(7, 9, 'a')
    c.outline('k')
    return c.rows()


def rivet_gun(p):
    c = Canvas()
    c.rect(2, 5, 11, 4, 'm').bevel(2, 5, 11, 4, 'M', 'n')      # barrel housing
    c.rect(13, 6, 2, 2, 'G')                                     # muzzle
    c.rect(2, 9, 4, 5, 'n').rect(3, 9, 2, 5, 'm')                # grip
    c.rect(6, 9, 2, 2, 'g')                                      # trigger guard
    c.rect(5, 3, 5, 2, 'g').set(6, 3, 'G')                       # top rail
    c.rect(8, 6, 3, 2, 'a').set(9, 6, 'A')                       # energy window
    c.outline('k')
    return c.rows()


def lance(p):
    c = Canvas()
    line(c, 1, 14, 11, 4, 'n', 2)
    line(c, 1, 13, 10, 4, 'm', 1)
    # long diamond head
    line(c, 10, 5, 14, 1, 'M', 2)
    c.set(15, 0, 'A').set(14, 1, 'A').set(13, 2, 'a')
    line(c, 9, 7, 12, 4, 'a', 1)
    c.set(5, 10, 'a').set(3, 12, 'a')
    c.outline('k')
    return c.rows()


def kit(level, p):
    c = Canvas()
    c.rect(2, 1, 12, 14, 'G').frame(1, 0, 14, 16, 'k').rect(13, 1, 1, 14, 'g').rect(2, 14, 12, 1, 'g')
    # wrench-and-gear emblem in the tier colour
    c.rect(5, 3, 6, 6, 'm').frame(4, 2, 8, 8, 'k').rect(6, 4, 4, 4, 'M').rect(7, 5, 2, 2, 'n')
    for i in range(level):
        c.set(4 + i * 2, 12, 'a')
    return c.rows()


def main():
    write_item('tinkers_hammer', hammer(None), pal(COPPER), handheld=True)
    write_item('felling_axe', axe(None), pal(COPPER), handheld=True)
    write_item('bore_drill', drill(None), pal(STEEL, {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22'}), handheld=True)
    write_item('chainsaw', chainsaw(None), pal(STEEL), handheld=True)
    write_item('servo_drill', drill(None), pal(SERVO), handheld=True)
    write_item('magma_drill', drill(None), pal(MAGMA), handheld=True)
    write_item('null_drill', drill(None), pal(NULL), handheld=True)
    write_item('gearblade', gearblade(None), pal(COPPER), handheld=True)
    write_item('shock_baton', baton(None), pal(STEEL), handheld=True)
    write_item('rivet_gun', rivet_gun(None), pal(SERVO), handheld=True)
    write_item('arc_blade', sword(None, 'M', 'm', 'n'), pal(MAGMA), handheld=True)
    write_item('null_lance', lance(None), pal(NULL), handheld=True)
    for level, colors in ((1, STEEL), (2, SERVO), (3, MAGMA), (4, NULL)):
        write_item(f'tool_upgrade_kit_{level}', kit(level, None), pal(colors))


if __name__ == '__main__':
    main()
