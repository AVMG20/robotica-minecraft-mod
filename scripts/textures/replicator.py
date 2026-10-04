"""Replicator module textures: eerie bio-tech look, dark steel with teal/green glow.
Run: python3 scripts/textures/replicator.py   (writes textures/block/* and textures/item/essence_vial*.png;
models, blockstates and loot tables come from scripts/data/replicator_models.py)"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_block, write_item  # noqa: E402

P = dict(PALETTE)
P.update({
    'a': '#252C31', 'A': '#3F4B53', 'z': '#10151A',  # dark steel: base, light edge, shadow
    'm': '#566872',  # rivet / worn metal
    'n': '#0A0E10',  # screen black
    'e': '#126B5E', 'E': '#2FE0C0', 'F': '#9CFFEA',  # teal glow: dim, bright, white-hot
    'g': '#14502A', 'G': '#58F07A',  # bio green: dim, bright
    'd': '#7A1E1A', 'D': '#E8493A',  # alarm red: dim, bright
    't': '#5FE8D030', 'T': '#BFFFF2A0',  # glass tint and glint (translucent)
    'h': '#A9CDD4', 'H': '#E6FAFC',  # vial glass
    'q': '#0E3B36',  # deep teal shadow
})


def plate(light='A', dark='z', base='a'):
    c = Canvas()
    c.rect(0, 0, 16, 16, base).bevel(0, 0, 16, 16, light, dark)
    return c


def rivets(c, ch='m'):
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, ch)
    return c


def frame():
    """Dark steel plate with a recessed panel and a faint teal circuit trace."""
    c = plate()
    rivets(c)
    c.rect(4, 4, 8, 8, 'z').rect(5, 5, 6, 6, 'a').bevel(5, 5, 6, 6, 'z', 'A')
    # trace: a small spiral of teal lines with one bright node
    for x in range(6, 10):
        c.set(x, 6, 'e')
    for y in range(6, 10):
        c.set(9, y, 'e')
    for x in range(7, 10):
        c.set(x, 9, 'e')
    c.set(7, 8, 'e').set(7, 7, 'E')
    c.set(0, 7, 'e').set(0, 8, 'e').set(15, 7, 'e').set(15, 8, 'e')
    return c.rows()


def glass():
    """Translucent teal pane in a thin dark steel frame with a diagonal glint."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 't')
    c.frame(0, 0, 16, 16, 'a').frame(1, 1, 14, 14, 'z')
    c.bevel(0, 0, 16, 16, 'A', 'z')
    for i in range(3, 8):
        c.set(i + 1, 12 - i, 'T')
    c.set(9, 4, 'T').set(10, 3, 'T')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, 'm')
    return c.rows()


def controller_side():
    c = plate()
    rivets(c)
    c.rect(2, 7, 12, 2, 'z')
    for x in range(3, 13, 3):
        c.set(x, 7, 'e')
    return c.rows()


def controller_top():
    c = plate()
    rivets(c)
    c.rect(4, 4, 8, 8, 'z').rect(5, 5, 6, 6, 'q').bevel(5, 5, 6, 6, 'z', 'e')
    c.set(7, 7, 'E').set(8, 8, 'E').set(8, 7, 'e').set(7, 8, 'e')
    return c.rows()


def screen(c):
    c.rect(2, 2, 12, 12, 'z').rect(3, 3, 10, 10, 'n').bevel(2, 2, 12, 12, 'z', 'A')
    return c


def controller_front(state):
    """state: off (alarm red eye), formed (teal idle ring) or on (bright DNA helix)."""
    c = plate()
    rivets(c)
    screen(c)
    if state == 'off':
        c.rect(7, 7, 2, 2, 'd').set(7, 7, 'D')
        c.set(4, 12, 'd').set(11, 12, 'd')
    elif state == 'formed':
        # idle ring and a dim core
        for x, y in ((6, 4), (7, 4), (8, 4), (9, 4), (6, 11), (7, 11), (8, 11), (9, 11),
                     (4, 6), (4, 7), (4, 8), (4, 9), (11, 6), (11, 7), (11, 8), (11, 9),
                     (5, 5), (10, 5), (5, 10), (10, 10)):
            c.set(x, y, 'e')
        c.rect(7, 7, 2, 2, 'E')
        c.set(7, 7, 'F')
    else:
        # two intertwined strands (helix), bright green and teal, with rungs
        import math
        for y in range(4, 12):
            a = math.sin((y - 4) * 0.85)
            x1 = int(round(7.5 + 3.0 * a))
            x2 = int(round(7.5 - 3.0 * a))
            c.set(x1, y, 'G').set(x2, y, 'E')
            if y % 2 == 0:
                lo, hi = min(x1, x2) + 1, max(x1, x2)
                for x in range(lo, hi):
                    c.set(x, y, 'g')
        c.set(7, 3, 'F').set(8, 12, 'F')
    return c.rows()


# ---- Essence Vial ----

def vial(fill_rows):
    """Glass bottle. fill_rows = number of liquid rows (0 empty, 9 full); the liquid glows teal/green."""
    c = Canvas()

    def inside(x, y):
        if 6 <= x <= 9 and 2 <= y <= 4:  # neck
            return True
        if 4 <= x <= 11 and y == 5:  # shoulder
            return True
        if 3 <= x <= 12 and 6 <= y <= 14:
            return not ((x in (3, 12)) and y in (6, 14))
        return False

    c.shape(inside, lambda x, y: 'h')
    c.rect(6, 1, 4, 2, 'w')  # cork
    c.set(6, 1, 'W').set(7, 1, 'W')
    # glass highlights
    c.set(4, 7, 'H').set(4, 8, 'H').set(4, 9, 'H').set(7, 3, 'H')
    # liquid
    top = 14 - fill_rows
    for y in range(max(top, 6), 14):
        for x in range(4, 12):
            if c.get(x, y) == '.':
                continue
            c.set(x, y, 'E' if (x + y) % 5 else 'G')
    if fill_rows:
        for x in range(4, 12):
            if c.get(x, top) != '.':
                c.set(x, top, 'F')
        c.set(5, 12, 'g').set(10, 13, 'q').set(7, 11, 'e')
        if fill_rows >= 9:
            c.set(6, 8, 'F').set(9, 10, 'F').set(8, 7, 'F')
    c.edge('k')
    # the cork keeps its own outline colour from edge(); restore a lighter top for readability
    return c.rows()


def main():
    write_block('replicator_frame', frame(), P)
    write_block('replicator_glass', glass(), P)
    write_block('replicator_controller_side', controller_side(), P)
    write_block('replicator_controller_top', controller_top(), P)
    for state in ('off', 'formed', 'on'):
        write_block(f'replicator_controller_front_{state}', controller_front(state), P)
    write_item('essence_vial', vial(0), P, model=False)
    write_item('essence_vial_partial', vial(4), P, model=False)
    write_item('essence_vial_full', vial(9), P, model=False)


if __name__ == '__main__':
    main()
