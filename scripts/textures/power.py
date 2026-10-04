"""Power module block textures (copper/brass industrial look, same palette as core.py).
Run: python3 scripts/textures/power.py   (writes textures/block/*.png only; models are written by scripts/data/power_models.py)"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_block  # noqa: E402

P = dict(PALETTE)
P.update({
    'u': '#23408F', 'U': '#4F86D8', 'v': '#142458',  # solar cell blue
    'q': '#2B2F31',  # near black iron
    'f': '#FF8A1E', 'F': '#FFD24A',  # flame
    'x': '#6A4A2A',  # dark wood
    'a': '#E6EEF0',  # frost highlight
})

ACCENT = {1: ('c', 'C', 'd'), 2: ('g', 'G', 's'), 3: ('o', 'O', 'b')}  # copper, steel, gold per accumulator tier


def rivets(c, ch='B', inset=2):
    for x, y in ((inset, inset), (15 - inset, inset), (inset, 15 - inset), (15 - inset, 15 - inset)):
        c.set(x, y, ch)


def panel(base='c', light='C', dark='d'):
    """Copper machine panel with bevel and a seam."""
    c = Canvas()
    c.rect(0, 0, 16, 16, base).bevel(0, 0, 16, 16, light, dark)
    c.rect(1, 7, 14, 1, dark).rect(1, 8, 14, 1, light)
    rivets(c)
    return c


def side():
    return panel().rows()


def top():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'g').bevel(0, 0, 16, 16, 'G', 's')
    c.rect(3, 3, 10, 10, 's').rect(4, 4, 8, 8, 'g').bevel(4, 4, 8, 8, 's', 'G')
    for i in (6, 9):
        c.rect(5, i, 6, 1, 's')
    rivets(c, 'B')
    return c.rows()


def generator_front(lit):
    c = panel()
    c.rect(3, 3, 10, 2, 'k')  # vent slits
    for x in range(4, 12, 2):
        c.set(x, 3, 's').set(x, 4, 's')
    c.rect(3, 6, 10, 8, 'k')
    c.rect(4, 7, 8, 6, 'q')
    if lit:
        c.rect(4, 10, 8, 3, 'f').rect(5, 9, 6, 2, 'F').rect(6, 8, 4, 2, 'F').rect(7, 11, 2, 2, 'O')
        c.set(5, 11, 'R').set(10, 11, 'R').set(4, 12, 'r').set(11, 12, 'r')
    else:
        for x in range(4, 12, 2):
            c.rect(x, 7, 1, 6, 's')
        c.rect(4, 12, 8, 1, 'd')
    return c.rows()


def press_front(lit):
    c = panel()
    c.rect(3, 3, 10, 3, 'G').bevel(3, 3, 10, 3, 'a', 's')  # upper jaw
    c.rect(3, 6, 10, 4, 'k')
    c.rect(3, 10, 10, 3, 'g').bevel(3, 10, 10, 3, 'G', 's')  # lower jaw
    c.rect(5, 7, 6, 2, 'q')
    if lit:
        c.rect(6, 7, 4, 2, 'y').set(7, 7, 'Y').set(8, 7, 'Y')
    c.set(2, 13, 'y' if lit else 's').set(13, 13, 'r' if not lit else 'y')
    return c.rows()


def charger_front(lit):
    c = panel()
    c.rect(4, 3, 8, 5, 'k').rect(5, 4, 6, 3, 'q')  # item slot
    c.rect(5, 6, 6, 1, 's')
    bolt = ((9, 9), (8, 10), (7, 11), (8, 11), (9, 11), (8, 12), (7, 13))
    for x, y in bolt:
        c.set(x, y, 'Y' if lit else 'y')
    if lit:
        c.rect(3, 9, 1, 5, 'y').rect(12, 9, 1, 5, 'y')
    else:
        c.rect(3, 9, 1, 5, 's').rect(12, 9, 1, 5, 's')
    return c.rows()


def solar_top(mk):
    cell_hi = 'U' if mk == 1 else 'y'
    frame, frame_hi, frame_lo = ('c', 'C', 'd') if mk == 1 else ('o', 'O', 'b')
    c = Canvas()
    c.rect(0, 0, 16, 16, frame).bevel(0, 0, 16, 16, frame_hi, frame_lo)
    for cx in (1, 8):
        for cy in (1, 8):
            c.rect(cx, cy, 7, 7, 'u' if mk == 1 else 'v')
            c.bevel(cx, cy, 7, 7, cell_hi, 'v')
            for i in range(1, 6, 2):
                c.rect(cx + i, cy + 1, 1, 5, 'v' if mk == 1 else 'u')
            c.set(cx + 1, cy + 1, 'Y' if mk == 2 else 'U')
    if mk == 2:
        for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
            c.set(x, y, 'B')
    return c.rows()


def solar_side(mk):
    base, light, dark = ('c', 'C', 'd') if mk == 1 else ('o', 'O', 'b')
    c = Canvas()
    c.rect(0, 0, 16, 16, base).bevel(0, 0, 16, 16, light, dark)
    c.rect(1, 0, 14, 2, light).rect(1, 2, 14, 1, dark)  # frame lip on the upper part (blocks are 6px high)
    for x in range(3, 14, 3):
        c.rect(x, 4, 1, 2, 'k')
    return c.rows()


def accumulator_side(tier):
    base, light, dark = ACCENT[tier]
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c').bevel(0, 0, 16, 16, 'C', 'd')
    c.rect(0, 2, 16, 3, base).rect(0, 2, 16, 1, light).rect(0, 4, 16, 1, dark)
    c.rect(0, 11, 16, 3, base).rect(0, 11, 16, 1, light).rect(0, 13, 16, 1, dark)
    c.rect(3, 6, 10, 4, 'k').rect(4, 7, 8, 2, 'q')
    for i, x in enumerate((4, 6, 8, 10)):
        c.rect(x, 7, 1, 2, 'y' if i < tier + 1 else 's')
    rivets(c, 'B', 1)
    return c.rows()


def accumulator_front(tier):
    base, light, dark = ACCENT[tier]
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c').bevel(0, 0, 16, 16, 'C', 'd')
    c.rect(0, 2, 16, 1, light).rect(0, 4, 16, 1, dark).rect(0, 11, 16, 1, light).rect(0, 13, 16, 1, dark)
    c.rect(4, 4, 8, 8, 'k').rect(5, 5, 6, 6, 'q').rect(6, 6, 4, 4, base).rect(7, 7, 2, 2, 'Y')
    c.set(6, 6, light).set(9, 9, dark)
    c.rect(2, 6, 1, 4, 'y').rect(13, 6, 1, 4, 'y')
    return c.rows()


def accumulator_top(tier):
    base, light, dark = ACCENT[tier]
    c = Canvas()
    c.rect(0, 0, 16, 16, base).bevel(0, 0, 16, 16, light, dark)
    c.rect(3, 3, 10, 10, dark).rect(4, 4, 8, 8, base).bevel(4, 4, 8, 8, dark, light)
    c.rect(6, 6, 4, 4, 'k').rect(7, 7, 2, 2, 'y')
    rivets(c, 'B')
    return c.rows()


def conduit(base, light, dark):
    """Striped cable, reads well on the 4x4 arm faces whichever part of the texture they sample."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, light if (x + y) % 8 < 2 else base)
    for x in range(16):
        c.set(x, 0, dark).set(x, 8, dark)
        c.set(x, 4, 'k' if x % 4 == 0 else dark).set(x, 12, 'k' if x % 4 == 0 else dark)
    return c.rows()


def crank_base():
    """Dark wood planks with copper corner plates."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for y in (0, 5, 10, 15):
        c.rect(0, y, 16, 1, 'x')
    for y in range(16):
        if y % 5 != 0:
            c.set((y * 7) % 16, y, 'W')
            c.set((y * 7 + 8) % 16, y, 'x')
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):
        c.rect(x, y, 3, 3, 'c').bevel(x, y, 3, 3, 'C', 'd').set(x + 1, y + 1, 'B')
    return c.rows()


def crank_metal():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b').bevel(0, 0, 16, 16, 'B', 'd')
    for y in range(2, 14, 3):
        c.rect(1, y, 14, 1, 'd')
    for y in range(3, 14, 3):
        c.rect(1, y, 14, 1, 'B')
    return c.rows()


def crank_spring():
    """Top view of a wound spring drum."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd').bevel(0, 0, 16, 16, 'b', 'k')
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = (dx * dx + dy * dy) ** 0.5
            if r > 7.3:
                continue
            ring = int(r + (0.5 if dx > 0 else 0)) % 2
            c.set(x, y, 'B' if ring == 0 else 'b')
    c.rect(7, 7, 2, 2, 'k')
    return c.rows()


def main():
    write_block('power_machine_side', side(), P)
    write_block('power_machine_top', top(), P)
    write_block('combustion_generator_front', generator_front(False), P)
    write_block('combustion_generator_front_on', generator_front(True), P)
    write_block('metal_press_front', press_front(False), P)
    write_block('metal_press_front_on', press_front(True), P)
    write_block('charger_front', charger_front(False), P)
    write_block('charger_front_on', charger_front(True), P)
    for mk in (1, 2):
        write_block(f'solar_panel_mk{mk}_top', solar_top(mk), P)
        write_block(f'solar_panel_mk{mk}_side', solar_side(mk), P)
    for tier in (1, 2, 3):
        write_block(f'accumulator_{tier}_side', accumulator_side(tier), P)
        write_block(f'accumulator_{tier}_front', accumulator_front(tier), P)
        write_block(f'accumulator_{tier}_top', accumulator_top(tier), P)
    write_block('copper_conduit', conduit('c', 'C', 'd'), P)
    write_block('gold_conduit', conduit('o', 'O', 'b'), P)
    write_block('winding_crank_base', crank_base(), P)
    write_block('winding_crank_metal', crank_metal(), P)
    write_block('winding_crank_spring', crank_spring(), P)


if __name__ == '__main__':
    main()
