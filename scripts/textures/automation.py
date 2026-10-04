"""Automation textures: robot panels (Stumpy, Sprout), Excavator parts, Supply Crate, Mk kits.
Run: python3 scripts/textures/automation.py

Block textures are 8-pixel-period panel patterns so the default model UVs of small elements still read as
plating (the models do not set UVs, vanilla derives them from the element position).
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_block, write_item  # noqa: E402

COPPER = {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22', 'r': '#F2D27A'}
BRASS = {'m': '#D4A73A', 'M': '#F2D27A', 'n': '#9C7A22', 'r': '#FFF1B8'}
STEEL = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#5A6266', 'r': '#E8EDEF'}
DARK = {'m': '#3B4144', 'M': '#5A6266', 'n': '#23282A', 'r': '#7C868A'}
IRON = {'m': '#A9B1B4', 'M': '#E2E7E9', 'n': '#6B7376', 'r': '#FFFFFF'}
GOLD = {'m': '#E0B232', 'M': '#FFE58A', 'n': '#9C7412', 'r': '#FFFFFF'}
TEAL = {'m': '#2E8F7F', 'M': '#5AD6BE', 'n': '#145247', 'r': '#D8FBFF'}


def pal(*dicts):
    out = dict(PALETTE)
    for d in dicts:
        out.update(d)
    return out


def panels(rivets=True, slits=False):
    """16x16 of four 8x8 plates: light top/left, dark bottom/right edge, rivet in the middle."""
    c = Canvas()
    for ox in (0, 8):
        for oy in (0, 8):
            c.rect(ox, oy, 8, 8, 'm').bevel(ox, oy, 8, 8, 'M', 'n')
            if rivets:
                c.set(ox + 3, oy + 3, 'r').set(ox + 4, oy + 4, 'n')
            if slits:
                for y in (oy + 2, oy + 4, oy + 6):
                    c.rect(ox + 1, y, 6, 1, 'n')
    return c.rows()


def wood():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for y in range(0, 16, 4):
        c.rect(0, y, 16, 1, 'k')
        c.rect(0, y + 1, 16, 1, 'W')
    for i, (x, y) in enumerate(((3, 2), (10, 6), (6, 10), (13, 14), (2, 13))):
        c.set(x, y, 'k').set(x + 1, y, 'k')
    return c.rows()


def eye():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'y')
    c.rect(3, 3, 10, 10, 'Y')
    c.frame(0, 0, 16, 16, 'y')
    return c.rows()


def saw():
    """Circular saw blade seen from the side, 16x16 with transparent corners."""
    c = Canvas()
    teeth = 12

    def inside(x, y):
        dx, dy = x - 7.5, y - 7.5
        r = math.hypot(dx, dy)
        if r <= 4.8:
            return True
        ang = (math.degrees(math.atan2(dy, dx)) + 360) % (360 / teeth)
        return r <= 7.6 and ang < (360 / teeth) * 0.55

    def fill(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        if r <= 1.5:
            return 'b'
        if r <= 2.6:
            return 's'
        return 'G' if x + y < 14 else 'g'

    c.shape(inside, fill).edge('k')
    c.set(7, 7, 'B').set(8, 7, 'B').set(7, 8, 'b').set(8, 8, 'b')
    return c.rows()


def leaf():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'l')
    for x, y in ((2, 2), (9, 1), (5, 6), (12, 7), (1, 10), (8, 11), (13, 13), (4, 14)):
        c.set(x, y, 'L').set(x + 1, y, 'L').set(x, y + 1, 'L')
    return c.rows()


def drill():
    """Spiral stripes for the drill cones."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            band = (x + y) % 6
            c.set(x, y, 'M' if band < 2 else ('m' if band < 4 else 'n'))
    return c.rows()


def light():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'o').rect(2, 2, 12, 12, 'O').rect(5, 5, 6, 6, 'B')
    return c.rows()


def crate_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for y in range(0, 16, 5):
        c.rect(0, y, 16, 1, 'k')
        c.rect(0, y + 1, 16, 1, 'W')
    c.set(5, 3, 'k').set(11, 8, 'k').set(4, 13, 'k')
    # copper straps and corner brackets
    c.rect(0, 0, 3, 16, 'c').rect(13, 0, 3, 16, 'c')
    c.rect(0, 0, 1, 16, 'C').rect(13, 0, 1, 16, 'C')
    c.rect(2, 0, 1, 16, 'd').rect(15, 0, 1, 16, 'd')
    for y in (2, 7, 13):
        c.set(1, y, 'B').set(14, y, 'B')
    c.rect(3, 0, 10, 1, 'k').rect(3, 15, 10, 1, 'k')
    return c.rows()


def crate_top():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'W')
    for x in range(0, 16, 5):
        c.rect(x, 0, 1, 16, 'k')
        c.rect(x + 1, 0, 1, 16, 'w')
    c.frame(0, 0, 16, 16, 'c').frame(1, 1, 14, 14, 'd')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, 'B')
    return c.rows()


def kit(tier_palette, pips):
    """Mk kit: a small toolbox with a coloured lid plate and one pip per tier above 1."""
    c = Canvas()
    c.rect(2, 5, 12, 9, 'w').frame(2, 5, 12, 9, 'k')
    c.rect(3, 6, 10, 1, 'W').rect(3, 12, 10, 1, 'd')
    c.rect(2, 7, 12, 1, 'k')
    c.rect(3, 3, 10, 2, 'm').frame(3, 3, 10, 2, 'k')
    c.rect(5, 1, 6, 2, 'g').frame(5, 1, 6, 2, 'k').rect(7, 2, 2, 1, 'k')
    c.rect(7, 8, 2, 3, 'b').frame(7, 8, 2, 3, 'k')
    for i in range(pips):
        c.set(4 + i * 3, 13, 'Y').set(5 + i * 3, 13, 'y')
    c.rect(3, 4, 10, 1, 'M')
    return c.rows()


def main():
    write_block('automation_copper', panels(), pal(COPPER))
    write_block('automation_brass', panels(), pal(BRASS))
    write_block('automation_steel', panels(), pal(STEEL))
    write_block('automation_dark', panels(rivets=False, slits=True), pal(DARK))
    write_block('automation_wood', wood(), pal())
    write_block('automation_mk1', panels(), pal(COPPER))
    write_block('automation_mk2', panels(), pal(IRON))
    write_block('automation_mk3', panels(), pal(GOLD))
    write_block('automation_mk4', panels(), pal(TEAL))
    write_block('automation_eye', eye(), pal())
    write_block('automation_saw', saw(), pal())
    write_block('automation_leaf', leaf(), pal())
    write_block('excavator_drill', drill(), pal(STEEL))
    write_block('excavator_light', light(), pal())
    write_block('supply_crate_side', crate_side(), pal())
    write_block('supply_crate_top', crate_top(), pal())
    write_item('farm_kit_mk2', kit(IRON, 1), pal(IRON))
    write_item('farm_kit_mk3', kit(GOLD, 2), pal(GOLD))
    write_item('farm_kit_mk4', kit(TEAL, 3), pal(TEAL))


if __name__ == '__main__':
    main()
