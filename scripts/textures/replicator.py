"""Replicator module textures: eerie bio-tech look, dark steel with teal/green glow.
Run: python3 scripts/textures/replicator.py   (writes textures/block/* and textures/item/essence_vial*.png;
models, blockstates and loot tables come from scripts/data/replicator_models.py)"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_anim, write_block, write_item  # noqa: E402

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


DS = 'nzaAm'          # dark steel ramp: deep, shadow, mid, light, highlight
P.update({'s': '#2C343A', 'S': '#1E252A'})   # subtle grain


def plate(light='A', dark='z', base='a', seed=0):
    c = Canvas()
    c.plate(0, 0, 16, 16, DS, seed, grain='Ss', density=0.3)
    return c


def rivets(c, ch='m'):
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.rivet(x, y, DS)
    return c


TRACE = [(1, 7), (2, 7), (3, 7), (4, 7), (4, 6), (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
         (10, 6), (10, 7), (11, 7), (12, 7), (13, 7), (14, 7), (7, 6), (7, 7), (7, 8), (7, 9), (7, 10), (6, 10),
         (5, 10), (8, 10), (9, 10), (10, 10), (10, 11), (10, 12), (5, 11), (5, 12)]
NODES = [(7, 8), (10, 12), (5, 12), (1, 7), (14, 7)]


def frame():
    """Dark machined plate, bevelled, riveted, with a recessed circuit groove (the trace glows on the overlay)."""
    c = plate(seed=1)
    rivets(c)
    for x, y in TRACE:
        c.set(x, y, 'q')
    for x, y in NODES:
        c.set(x, y, 'e')
    c.rect(0, 7, 1, 2, 'q').rect(15, 7, 1, 2, 'q')
    return c.rows()


def frame_glow():
    c = Canvas()
    for x, y in TRACE:
        c.set(x, y, 'e')
    for x, y in NODES:
        c.set(x, y, 'E')
    c.set(7, 8, 'F')
    c.rect(0, 7, 1, 2, 'e').rect(15, 7, 1, 2, 'e')
    return c


def glass(joined=0):
    """Translucent teal pane in a two pixel dark steel frame with streaked glints. Connected glass: `joined` has a bit
    per side that joins a neighbour (1 top, 2 right, 4 bottom, 8 left) and drops its frame there."""
    t, r, b, lf = (not joined & 1, not joined & 2, not joined & 4, not joined & 8)
    steel = Canvas()
    steel.plate(0, 0, 16, 16, DS, 3, brushed=False)
    c = Canvas()
    c.rect(0, 0, 16, 16, 't')
    x0, x1 = (1 if lf else 0), (14 if r else 15)
    y0, y1 = (1 if t else 0), (14 if b else 15)
    for side, on in (('t', t), ('b', b), ('l', lf), ('r', r)):
        if not on:
            continue
        for i in range(16):
            x, y = {'t': (i, 0), 'b': (i, 15), 'l': (0, i), 'r': (15, i)}[side]
            c.set(x, y, steel.get(x, y))
        if side in 'tb':
            c.rect(x0, 1 if side == 't' else 14, x1 - x0 + 1, 1, 'z')
        else:
            c.rect(1 if side == 'l' else 14, y0, 1, y1 - y0 + 1, 'z')
    if joined:      # part of a pane: fewer glints, so a wall of them reads calm
        c.line(3, 6, 6, 3, 'T')
        c.line(9, 13, 12, 10, 'T')
    else:
        c.line(3, 9, 9, 3, 'T').line(4, 9, 9, 4, 'T')
        c.line(10, 13, 13, 10, 'T')
    for x, y, s1, s2 in ((0, 0, t, lf), (15, 0, t, r), (0, 15, b, lf), (15, 15, b, r)):
        if s1 and s2:
            c.set(x, y, 'm')
    return c.rows()


# ---- formed look: the controller sets frame=wall|x|y|z|corner on its frames (see scripts/data/replicator_models.py)

def frame_beam():
    """Edge beam, running top to bottom and seamless along it: bevelled rails either side of a sunken conduit (its
    light is the overlay)."""
    c = Canvas()
    c.plate(0, 0, 16, 16, DS, 7, bevel=False, vertical=True, grain='Ss', density=0.3)
    for x, ch in ((0, 'n'), (1, 'm'), (2, 'A'), (13, 'z'), (14, 'n'), (15, 'n')):
        c.rect(x, 0, 1, 16, ch)
    for y in (3, 11):
        c.rivet(3, y, DS).rivet(11, y, DS)
    c.rect(5, 0, 6, 16, 'z')
    c.rect(5, 0, 1, 16, 'n').rect(10, 0, 1, 16, 'A')
    c.rect(6, 0, 4, 16, 'n').rect(7, 0, 2, 16, 'q')
    for y in (6, 14):
        c.rect(6, y, 4, 1, 'z')
    return c


def frame_beam_glow():
    """Thin teal conduit line along the beam, a little brighter halfway between the clamps."""
    c = Canvas()
    c.rect(7, 0, 2, 16, 'e')
    for y in (2, 10):
        c.set(7, y, 'E').set(8, y, 'E')
    for y in (6, 14):
        c.rect(7, y, 2, 1, '.')
    return c


def frame_corner():
    """Corner cap where three beams meet: a heavy bevelled plate, four bolts and a sunken teal lens."""
    c = Canvas()
    c.plate(0, 0, 16, 16, DS, 8, grain='Ss', density=0.25)
    c.frame(0, 0, 16, 16, 'n')
    c.bevel(1, 1, 14, 14, 'm', 'z')
    c.recess(4, 4, 8, 8, DS, fill='z')
    c.disc(7.5, 7.5, 2.6, 'q')
    c.disc(7.5, 7.5, 1.5, 'e')
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, DS)
    return c


def frame_corner_glow():
    c = Canvas()
    c.ring(7.5, 7.5, 1.6, 2.6, 'e')
    c.rect(7, 7, 2, 2, 'E').set(7, 7, 'F')
    return c


def panel():
    """Formed wall plating between the beams: one brushed surface with two ribs and a capped port in the middle."""
    c = Canvas()
    c.plate(0, 0, 16, 16, DS, 9, bevel=False, grain='Ss', density=0.22)
    for y in (3, 11):
        c.rect(0, y, 16, 1, 'z').rect(0, y + 1, 16, 1, 'A')
    c.disc(7.5, 7.5, 3.4, 'z')
    c.disc(7.5, 7.5, 2.6, 'a')
    c.ring(7.5, 7.5, 1.0, 1.8, 'q')
    c.set(6, 6, 'A')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.rivet(x, y, DS)
    return c


def controller_side():
    c = plate(seed=4)
    rivets(c)
    c.recess(3, 5, 10, 6, DS, fill='n')
    for x in range(4, 12, 2):                                     # coolant tubes
        c.rect(x, 6, 1, 4, 'e').set(x, 6, 'E')
    return c.rows()


def controller_top():
    c = plate(seed=5)
    rivets(c)
    c.disc(7.5, 7.5, 4.6, 'z')
    c.disc(7.5, 7.5, 3.8, 'q')
    c.ring(7.5, 7.5, 2.2, 3.0, 'e')
    c.disc(7.5, 7.5, 1.2, 'E')
    c.set(5, 4, 'A').set(4, 5, 'A')
    return c.rows()


def screen(c):
    c.screen(2, 2, 12, 12, DS, glass='n')
    return c


def controller_front(state):
    """Base face: the screen with a dim picture; the bright picture lives on the matching overlay."""
    c = plate(seed=6)
    rivets(c)
    screen(c)
    if state == 'off':
        c.rect(7, 7, 2, 2, 'd')
    elif state == 'formed':
        c.ring(7.5, 7.5, 3.0, 4.0, 'q')
        c.rect(7, 7, 2, 2, 'e')
    else:
        for x, y in helix(0):
            c.set(x, y, 'q')
    return c.rows()


def helix(phase):
    pts = []
    for y in range(3, 13):
        a = math.sin((y - 3) * 0.8 + phase)
        pts.append((int(round(7.5 + 3.0 * a)), y))
        pts.append((int(round(7.5 - 3.0 * a)), y))
    return pts


def controller_glow(state, frame_no):
    c = Canvas()
    if state == 'off':
        on = frame_no % 4 < 2                                      # slow red blink
        c.rect(7, 7, 2, 2, 'D' if on else 'd')
        if on:
            c.set(7, 7, 'D').set(6, 7, 'd').set(9, 8, 'd').set(7, 6, 'd').set(8, 9, 'd')
    elif state == 'formed':
        pts = [(x, y) for y in range(16) for x in range(16) if 3.0 <= math.hypot(x - 7.5, y - 7.5) <= 4.0]
        pts.sort(key=lambda p: math.atan2(p[1] - 7.5, p[0] - 7.5))
        for i, (x, y) in enumerate(pts):
            k = (i - frame_no * 3) % len(pts)
            c.set(x, y, 'F' if k < 2 else 'E' if k < 6 else 'e')
        c.rect(7, 7, 2, 2, 'E').set(7, 7, 'F')
    else:
        phase = frame_no * 2 * math.pi / 8
        for y in range(3, 13):
            a = math.sin((y - 3) * 0.8 + phase)
            x1, x2 = int(round(7.5 + 3.0 * a)), int(round(7.5 - 3.0 * a))
            front = math.cos((y - 3) * 0.8 + phase) > 0
            c.set(x1, y, 'G' if front else 'g').set(x2, y, 'E' if not front else 'e')
            if y % 2 == 0:
                for x in range(min(x1, x2) + 1, max(x1, x2)):
                    c.set(x, y, 'e')
        c.set(3, 12, 'G').set(12, 3, 'G')
    return c


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
    write_block('replicator_frame_glow', frame_glow().rows(), P)
    for joined in range(16):
        write_block(f'replicator_glass/{joined}', glass(joined), P)
    write_block('replicator_frame_beam', frame_beam().rows(), P)
    write_block('replicator_frame_beam_glow', frame_beam_glow().rows(), P)
    write_block('replicator_frame_corner', frame_corner().rows(), P)
    write_block('replicator_frame_corner_glow', frame_corner_glow().rows(), P)
    write_block('replicator_panel', panel().rows(), P)
    write_block('replicator_controller_side', controller_side(), P)
    write_block('replicator_controller_top', controller_top(), P)
    for state in ('off', 'formed', 'on'):
        write_block(f'replicator_controller_front_{state}', controller_front(state), P)
        n = {'off': 4, 'formed': 12, 'on': 8}[state]
        write_anim('block', f'replicator_controller_front_{state}_glow', [controller_glow(state, i) for i in range(n)], P,
                   frametime={'off': 8, 'formed': 2, 'on': 3}[state])
    write_item('essence_vial', vial(0), P, model=False)
    write_item('essence_vial_partial', vial(4), P, model=False)
    write_item('essence_vial_full', vial(9), P, model=False)


if __name__ == '__main__':
    main()
