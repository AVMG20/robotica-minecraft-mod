"""Core textures: balance-ladder parts, cells, boss cores, upgrade cards. Run: python3 scripts/textures/core.py

Item style (shared with gear, drones, warp and codex icons): one dark outline 'k', light from the top-left (lit top/left
rim, shaded bottom/right rim via Canvas.auto_shade), a highlight pixel or glint on the lit side, materials from
pixelart.MATERIALS. Chars: '12345' main material (deep -> highlight), 'abcde' steel, '6789' brass (dark -> light),
'y Y z' glow (mid, bright, white).
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, material, write_item  # noqa: E402

M, S = '12345', 'abcde'
SHADE = {'3': ('2', '4'), 'c': ('b', 'd'), '7': ('6', '8')}


def pal(main='copper', glow='cyan', **extra):
    p = {'k': '#1E1A1A', 'K': '#0E0C0C'}
    p.update(material(M, main))
    p.update(material(S, 'steel'))
    b = MATERIALS['brass']
    p.update({'6': b[1], '7': b[2], '8': b[3], '9': b[4]})
    g = MATERIALS[glow]
    p.update({'y': g[2], 'Y': g[3], 'z': g[4], 'Z': g[1]})
    p.update({'w': '#5E3E22', 'W': '#8B5E34', 'v': '#B98550'})          # wood
    p.update({'o': '#E8B530', 'O': '#FFE07A'})                          # gold traces
    p.update(extra)
    return p


def finish(c, shade=SHADE):
    c.auto_shade(shade)
    c.outline('k')
    return c.rows()


# ---------------------------------------------------------------- mechanical parts

def gear_shape(c, cx, cy, r_body, r_tip, teeth, hole, chars='3', phase=0.0):
    def inside(x, y):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r <= hole:
            return False
        if r <= r_body:
            return True
        ang = (math.degrees(math.atan2(dy, dx)) + phase + 360) % (360 / teeth)
        return r <= r_tip and ang < (360 / teeth) * 0.5
    c.shape(inside, lambda x, y: chars)


def gear():
    c = Canvas()
    gear_shape(c, 7.5, 7.5, 5.3, 7.4, 8, 1.6)
    c.ring(7.5, 7.5, 2.6, 3.2, '2')                                     # hub groove
    c.set(5, 4, '5').set(4, 5, '5')
    return finish(c)


def mechanism():
    """A big copper gear meshing with a small brass one, a steel arbor in each."""
    c = Canvas()
    gear_shape(c, 6.0, 6.5, 4.0, 5.6, 8, 0.0)
    gear_shape(c, 11.5, 11.5, 2.4, 3.6, 6, 0.0, chars='7', phase=15)
    c.auto_shade(SHADE)
    c.ring(6.0, 6.5, 1.8, 2.5, '2')
    c.disc(6.0, 6.5, 1.0, 'c').set(6, 6, 'e')
    c.disc(11.5, 11.5, 0.8, 'c')
    c.set(4, 3, '5').set(3, 4, '5').set(10, 9, '9')
    c.outline('k')
    return c.rows()


def chassis():
    """Wooden frame with a cross brace and copper corner brackets."""
    c = Canvas()
    c.rect(1, 1, 14, 14, 'W').rect(4, 4, 8, 8, '.')
    c.line(4, 11, 11, 4, 'W').line(4, 12, 12, 4, 'w')
    c.rect(1, 1, 14, 1, 'v').rect(1, 1, 1, 14, 'v').rect(1, 14, 14, 1, 'w').rect(14, 1, 1, 14, 'w')
    c.rect(3, 4, 1, 8, 'w').rect(4, 3, 8, 1, 'w')
    for x, y in ((1, 1), (12, 1), (1, 12), (12, 12)):
        c.rect(x, y, 3, 3, '3').set(x, y, '4').set(x + 1, y + 1, '5').set(x + 2, y + 2, '2')
    c.outline('k')
    return c.rows()


def mainspring():
    """Flat brass spring wound into a spiral, the free end hooked out."""
    c = Canvas()
    for i in range(900):
        t = i / 900 * 3.2 * 2 * math.pi
        r = 1.0 + t * 0.30
        x, y = 7.5 + r * math.cos(t), 7.5 + r * math.sin(t)
        if r < 6.6:
            c.set(round(x), round(y), '7')
    c.line(13, 8, 14, 13, '7').set(14, 14, '8')
    c.auto_shade(SHADE)
    c.set(7, 7, 'c').set(8, 8, 'b')
    c.outline('k')
    return c.rows()


def plate():
    """Pressed sheet with thickness (darker bottom edge) and a diagonal sheen."""
    c = Canvas()
    c.rect(1, 3, 14, 9, '3').rect(1, 12, 14, 1, '1')
    c.auto_shade(SHADE)
    c.rect(1, 12, 14, 1, '1').set(1, 12, '2')
    c.line(3, 10, 8, 5, '4').line(4, 10, 9, 5, '4').line(10, 9, 12, 7, '4')
    c.set(2, 4, '5').set(3, 4, '5')
    c.outline('k')
    return c.rows()


def coil():
    """Spool: steel flanges, copper winding shaded like a cylinder (light left, dark right)."""
    c = Canvas()
    c.rect(3, 1, 10, 2, 'c').rect(3, 13, 10, 2, 'c')
    for x in range(4, 12):
        for y in range(3, 13):
            k = (x - 4) / 7
            c.set(x, y, '5' if k < 0.15 else '4' if k < 0.4 else '3' if k < 0.75 else '2')
    for y in range(4, 13, 2):
        c.rect(4, y, 8, 1, '1').set(4, y, '2')
    c.auto_shade({'c': ('b', 'd')})
    c.rect(7, 0, 2, 1, 'b').rect(7, 15, 2, 1, 'b')                       # axle ends
    c.outline('k')
    return c.rows()


def casing(emblem):
    """Bevelled frame plate with corner rivets; the centre carries the tier emblem."""
    c = Canvas()
    c.rect(1, 1, 14, 14, '3')
    c.auto_shade(SHADE)
    c.set(1, 1, '5')
    c.recess(4, 4, 8, 8, M, fill='2')
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.set(x, y, '5').set(x + 1, y + 1, '1')
    if emblem == 'vent':
        for y in (6, 8, 10):
            c.rect(5, y, 6, 1, '1').rect(5, y - 1, 6, 1, '4')
    elif emblem == 'crystal':
        c.draw(6, 5, ['.Yz.', 'Yyyy', 'yyZZ', '.ZZ.'])
        c.rect(5, 9, 6, 2, '1')
    elif emblem == 'fire':
        c.rect(5, 5, 6, 6, 'K')
        c.draw(5, 6, ['.y..y.', 'yYyyYy', 'YzYYzY', 'yyyyyy'])
        for x in (6, 8, 10):
            c.rect(x, 5, 1, 6, '2')
    elif emblem == 'rune':
        c.rect(5, 5, 6, 6, 'K')
        c.draw(5, 5, ['..yy..', '.y..y.', 'yYzzYy', 'yYzzYy', '.y..y.', '..yy..'])
    c.frame(0, 0, 16, 16, 'k')
    return c.rows()


def circuit(board):
    """PCB: bevelled board, gold traces and pads, a black chip with pins, an edge connector."""
    c = Canvas()
    c.rect(1, 2, 14, 12, '3')
    c.auto_shade(SHADE)
    for x in (3, 6, 9, 12):
        c.rect(x, 3, 1, 3, 'o')
    c.rect(3, 6, 10, 1, 'o').set(3, 6, 'O')
    c.rect(2, 10, 2, 1, 'o').rect(12, 9, 2, 1, 'o').rect(13, 9, 1, 3, 'o')
    c.rect(5, 8, 6, 4, 'K').rect(5, 8, 6, 1, 'a').set(5, 8, 'c')        # chip
    for x in (5, 7, 9):
        c.set(x, 12, 'b').set(x + 1, 7, 'b')
    c.set(9, 10, board)
    for x in range(3, 13, 2):
        c.set(x, 14, 'o')                                                # edge contacts
    c.set(2, 3, '5')
    c.outline('k')
    return c.rows()


def motor(tier):
    """Motor can with cooling fins, a steel shaft out the right, a status window on tier 2+."""
    c = Canvas()
    c.rect(2, 3, 10, 10, '3')
    for x in range(3, 12, 2):
        c.rect(x, 3, 1, 10, '2')
    c.auto_shade(SHADE)
    c.rect(2, 3, 2, 10, 'c').rect(2, 3, 2, 1, 'd').rect(2, 12, 2, 1, 'b')   # end bell
    c.rect(12, 6, 3, 4, 'c').rect(12, 6, 3, 1, 'd').rect(12, 9, 3, 1, 'b')  # shaft collar
    c.rect(15, 7, 1, 2, 'e')
    c.rect(0, 7, 2, 2, '7')                                              # terminal
    if tier >= 2:
        c.rect(6, 6, 3, 4, 'K').rect(7, 7, 1, 2, 'y').set(7, 7, 'Y')
    c.set(5, 4, '5')
    c.outline('k')
    return c.rows()


def cell():
    """Battery cell: steel cap, material sleeve with a label band, glowing charge window and level ticks."""
    c = Canvas()
    c.rect(6, 1, 4, 2, 'c')
    c.rect(4, 3, 8, 12, '3')
    c.auto_shade(SHADE)
    c.rect(6, 1, 4, 1, 'd').set(9, 2, 'b')
    c.rect(4, 4, 8, 1, '2').rect(4, 12, 8, 1, '2')                      # band seams
    c.rect(6, 5, 4, 7, 'K').rect(7, 6, 2, 5, 'y').rect(7, 6, 1, 5, 'Y').set(7, 6, 'z')
    for y in (7, 9):
        c.set(10, y, '1')
    c.set(5, 5, '5').set(5, 6, '5')
    c.outline('k')
    return c.rows()


def core():
    """Boss core: faceted octahedron with four facet tones and a bright heart."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = abs(dx) + abs(dy)
            if d > 7:
                continue
            if d <= 2:
                ch = 'z' if d <= 1 else 'Y'
            elif dx < 0 and dy < 0:
                ch = '4'
            elif dx >= 0 and dy < 0:
                ch = '3'
            elif dx < 0 and dy >= 0:
                ch = '3' if abs(dx) > abs(dy) else '2'
            else:
                ch = '2' if dx > dy else '1'
            c.set(x, y, ch)
    c.line(2, 7, 7, 2, '5').set(6, 4, '5')
    c.edge('k')
    return c.rows()


# 7x3 glyph per card kind, drawn in the card colour under the window
CARD_GLYPHS = {
    'speed': ['y..y...', '.y..y..', 'y..y...'],
    'range': ['.y...y.', 'yyyyyyy', '.y...y.'],
    'efficiency': ['..yyy..', '.y.y.y.', '..yyy..'],
    'fortune': ['...y...', '..yyy..', '...y...'],
    'silk': ['yyyyyyy', '.......', 'yyyyyyy'],
    'growth': ['...y...', '.yyyyy.', '...y...'],
    'void': ['y.....y', '..y.y..', 'y.....y'],
    'pickup': ['y.....y', 'y.....y', '.yyyyy.'],
}


def card(kind):
    """Upgrade card: white steel card with a clipped corner, coloured window with glint, glyph, gold contacts."""
    c = Canvas()
    c.rect(2, 1, 12, 14, 'd')
    c.set(13, 1, '.').set(12, 1, '.').set(13, 2, '.')                    # clipped corner
    c.auto_shade({'d': ('c', 'e')})
    c.recess(4, 3, 8, 6, 'Kbcde', fill='3')
    c.rect(5, 4, 6, 4, '3').rect(5, 4, 6, 1, '4').set(5, 4, '5').rect(5, 7, 6, 1, '2')
    for dy, row in enumerate(CARD_GLYPHS[kind]):
        for dx, ch in enumerate(row):
            if ch == 'y':
                c.set(4 + dx, 10 + dy, '2')
    for x in range(3, 13, 2):
        c.set(x, 14, 'o')
    c.outline('k')
    return c.rows()


CARD_COLORS = {
    'speed': 'red', 'range': 'cyan', 'efficiency': 'green', 'fortune': 'gold', 'silk': 'white', 'growth': 'green',
    'void': 'purple', 'pickup': 'teal',
}


def main():
    write_item('copper_gear', gear(), pal())
    write_item('clockwork_mechanism', mechanism(), pal())
    write_item('wooden_chassis', chassis(), pal())
    write_item('mainspring', mainspring(), pal())
    write_item('iron_plate', plate(), pal('iron'))
    write_item('copper_plate', plate(), pal('copper'))
    write_item('gold_plate', plate(), pal('gold'))
    write_item('copper_coil', coil(), pal())
    write_item('iron_casing', casing('vent'), pal('iron'))
    write_item('reinforced_casing', casing('crystal'), pal('purple', 'purple'))
    write_item('blazing_casing', casing('fire'), pal('magma', 'amber'))
    write_item('null_casing', casing('rune'), pal('teal', 'teal'))
    write_item('basic_circuit', circuit('y'), pal('green'))
    write_item('advanced_circuit', circuit('y'), pal('cyan', 'cyan', **{'1': '#0E2A5A', '2': '#1A4490', '3': '#2A62C0', '4': '#5A92E8', '5': '#B8D8FF'}))
    write_item('quantum_circuit', circuit('Y'), pal('red', 'amber'))
    write_item('null_circuit', circuit('Y'), pal('null', 'teal'))
    write_item('electric_motor', motor(1), pal('copper'))
    write_item('servo_actuator', motor(2), pal('steel'))
    write_item('plasma_actuator', motor(3), pal('magma', 'amber'))
    write_item('copper_cell', cell(), pal('copper'))
    write_item('redstone_cell', cell(), pal('red', 'amber'))
    write_item('ender_cell', cell(), pal('teal', 'teal'))
    write_item('servo_core', core(), pal('copper', 'amber'))
    write_item('magma_core', core(), pal('magma', 'amber'))
    write_item('antigrav_core', core(), pal('null', 'purple'))
    for kind, mat in CARD_COLORS.items():
        extra = {'5': '#FFFFFF'} if mat == 'white' else {}
        write_item(f'upgrade_{kind}', card(kind), pal(mat, **extra))


if __name__ == '__main__':
    main()
