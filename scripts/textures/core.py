"""Core textures: balance-ladder parts, cells, boss cores, upgrade cards. Run: python3 scripts/textures/core.py"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, diamond, write_item  # noqa: E402

# Material palettes: m = mid, M = light, n = dark
COPPER = {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22'}
IRON = {'m': '#A9B1B4', 'M': '#E2E7E9', 'n': '#6B7376'}
GOLD = {'m': '#E0B232', 'M': '#FFE58A', 'n': '#9C7412'}
OBSIDIAN = {'m': '#3B2E5A', 'M': '#6C58A0', 'n': '#1E1630'}
BLAZE = {'m': '#C9561B', 'M': '#FFB347', 'n': '#7A2A0A'}
NULL = {'m': '#2A6F68', 'M': '#62E0CC', 'n': '#123B37'}


def pal(*dicts):
    out = dict(PALETTE)
    for d in dicts:
        out.update(d)
    return out


def gear(c_light='M', c_mid='m', c_dark='n', teeth=8):
    c = Canvas()

    def inside(x, y):
        dx, dy = x - 7.5, y - 7.5
        r = math.hypot(dx, dy)
        if r <= 1.6:
            return False
        if r <= 5.4:
            return True
        ang = (math.degrees(math.atan2(dy, dx)) + 360) % (360 / teeth)
        return r <= 7.4 and ang < (360 / teeth) * 0.5

    def fill(x, y):
        if x + y < 13:
            return c_light
        if x + y > 17:
            return c_dark
        return c_mid

    c.shape(inside, fill).edge('k')
    return c.rows()


def plate():
    c = Canvas()
    c.rect(2, 3, 12, 10, 'm').bevel(2, 3, 12, 10, 'M', 'n').frame(1, 2, 14, 12, 'k')
    c.set(4, 5, 'M').set(5, 5, 'M').set(4, 6, 'M')
    return c.rows()


def casing():
    c = Canvas()
    c.rect(1, 1, 14, 14, 'm').bevel(1, 1, 14, 14, 'M', 'n').frame(0, 0, 16, 16, 'k')
    c.rect(4, 4, 8, 8, 'n').rect(5, 5, 6, 6, 'm')
    for y in (6, 8, 10):
        c.rect(5, y, 6, 1, 'n')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, 'Y')
    return c.rows()


def circuit():
    c = Canvas()
    c.rect(1, 2, 14, 12, 'm').frame(0, 1, 16, 14, 'k')
    for x in (3, 6, 9, 12):
        c.rect(x, 3, 1, 3, 'o')
    c.rect(3, 6, 10, 1, 'o')
    c.rect(5, 8, 6, 4, 'k').rect(6, 9, 4, 2, 's').set(6, 9, 'G')
    c.rect(2, 12, 2, 1, 'o').rect(12, 10, 2, 1, 'o').rect(12, 10, 1, 3, 'o')
    return c.rows()


def motor():
    c = Canvas()
    c.rect(3, 3, 10, 10, 'c').frame(2, 2, 12, 12, 'k')
    for y in (4, 6, 8, 10):
        c.rect(3, y, 10, 1, 'C')
    c.rect(3, 3, 1, 10, 'M').rect(12, 3, 1, 10, 'n')
    c.rect(0, 7, 2, 2, 'G').set(0, 6, 'k').set(1, 6, 'k').set(0, 9, 'k').set(1, 9, 'k')
    c.rect(14, 7, 2, 2, 'g')
    return c.rows()


def coil():
    c = Canvas()
    c.rect(4, 1, 8, 2, 'g').frame(3, 0, 10, 4, 'k')
    c.rect(4, 13, 8, 2, 'g').frame(3, 12, 10, 4, 'k')
    c.rect(3, 4, 10, 8, 'c').frame(2, 3, 12, 10, 'k')
    for y in range(4, 12):
        for x in range(3, 13):
            if (x + y) % 2 == 0:
                c.set(x, y, 'C')
    return c.rows()


def chassis():
    c = Canvas()
    c.rect(1, 1, 14, 14, 'W').frame(0, 0, 16, 16, 'k').rect(3, 3, 10, 10, '.').frame(2, 2, 12, 12, 'w')
    for i in range(3, 13):
        c.set(i, i, 'w')
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.rect(x, y, 2, 2, 'c')
    return c.rows()


def mechanism():
    c = Canvas()
    big = gear()
    for y, row in enumerate(big):
        for x, ch in enumerate(row):
            if ch != '.':
                c.set(x, y, ch)
    c.rect(6, 6, 4, 4, 'b').frame(6, 6, 4, 4, 'k').set(7, 7, 'B')
    return c.rows()


def mainspring():
    return ["................", "....kkkkkkk.....", "...kbbbbbbbk....", "..kbkkkkkkkbk...",
            ".kbk.......kbk..", ".kbk.kkkkk.kbk..", ".kbk.kbbbk.kbk..", ".kbk.kbkbk.kbk..",
            ".kbk.kbkbk.kbk..", ".kbk...kbk.kbk..", ".kbkkkkkbk.kbk..", ".kbbbbbbbk.kbk..",
            "..kkkkkkk..kbk..", "...........kbkk.", "...........kBbk.", "............kk.."]


def cell():
    c = Canvas()
    c.rect(6, 1, 4, 2, 'G').frame(5, 0, 6, 3, 'k')
    c.rect(4, 3, 8, 12, 'm').frame(3, 2, 10, 14, 'k').rect(4, 3, 1, 12, 'M').rect(11, 3, 1, 12, 'n')
    c.rect(6, 5, 4, 8, 'k').rect(7, 6, 2, 6, 'y').set(7, 6, 'Y')
    return c.rows()


def card(level, pips_char='y'):
    c = Canvas()
    c.rect(2, 1, 12, 13, 'G').frame(1, 0, 14, 15, 'k').rect(13, 1, 1, 13, 'g').rect(2, 13, 12, 1, 'g')
    c.rect(4, 2, 8, 6, 'm').frame(3, 1, 10, 8, 'k').rect(5, 3, 6, 4, 'M')
    for i in range(level):
        c.set(4 + i * 2, 10, pips_char)
    for x in range(3, 13, 2):
        c.set(x, 15, 'o')
    return c.rows()


CARD_COLORS = {
    'speed': {'m': '#C9302A', 'M': '#FF6B5E'},
    'range': {'m': '#2468B8', 'M': '#5FA4F0'},
    'efficiency': {'m': '#3E8E3A', 'M': '#7FD068'},
    'fortune': {'m': '#2A4FB0', 'M': '#7FA0FF'},
    'silk': {'m': '#B8B8C8', 'M': '#FFFFFF'},
    'growth': {'m': '#5C8F2A', 'M': '#B8E07A'},
    'void': {'m': '#2A1F3A', 'M': '#5A4A7A'},
}
CARD_LEVELS = {'speed': range(1, 5), 'range': range(1, 5), 'efficiency': range(1, 5), 'fortune': range(2, 5),
               'silk': [1], 'growth': range(1, 5), 'void': [1]}


def main():
    write_item('copper_gear', gear(), pal(COPPER))
    write_item('clockwork_mechanism', mechanism(), pal(COPPER))
    write_item('wooden_chassis', chassis(), pal())
    write_item('mainspring', mainspring(), pal())
    write_item('iron_plate', plate(), pal(IRON))
    write_item('copper_plate', plate(), pal(COPPER))
    write_item('gold_plate', plate(), pal(GOLD))
    write_item('copper_coil', coil(), pal())
    write_item('iron_casing', casing(), pal(IRON))
    write_item('reinforced_casing', casing(), pal(OBSIDIAN))
    write_item('blazing_casing', casing(), pal(BLAZE))
    write_item('null_casing', casing(), pal(NULL))
    write_item('basic_circuit', circuit(), pal({'m': '#2F7A3A'}))
    write_item('advanced_circuit', circuit(), pal({'m': '#2A4FB0'}))
    write_item('quantum_circuit', circuit(), pal({'m': '#8A2A1A'}))
    write_item('null_circuit', circuit(), pal({'m': '#3A1F6E'}))
    write_item('electric_motor', motor(), pal())
    write_item('servo_actuator', motor(), pal({'c': '#6E777A', 'C': '#9AA3A6', 'M': '#C4CBCE', 'n': '#3E4548'}))
    write_item('plasma_actuator', motor(), pal({'c': '#C23A12', 'C': '#FF7A2E', 'M': '#FFE08A', 'n': '#6E1A06'}))
    write_item('copper_cell', cell(), pal(COPPER))
    write_item('redstone_cell', cell(), pal({'m': '#B8261F', 'M': '#F0574A', 'n': '#6E120D', 'y': '#FFD27A'}))
    write_item('ender_cell', cell(), pal({'m': '#1F7A6C', 'M': '#4FD0B6', 'n': '#0F4239', 'y': '#D6FFF5'}))
    write_item('servo_core', diamond(), pal({'m': '#C87533', 'M': '#F0A866', 'n': '#7A3E18', 'Y': '#FFE7C2'}))
    write_item('magma_core', diamond(), pal({'m': '#C23A12', 'M': '#FF7A2E', 'n': '#6E1A06', 'Y': '#FFE08A'}))
    write_item('antigrav_core', diamond(), pal({'m': '#6A3FB0', 'M': '#A884F0', 'n': '#3A1F6E', 'Y': '#E9DDFF'}))
    for kind, levels in CARD_LEVELS.items():
        for level in levels:
            write_item(f'upgrade_{kind}_{level}', card(level), pal(CARD_COLORS[kind]))


if __name__ == '__main__':
    main()
