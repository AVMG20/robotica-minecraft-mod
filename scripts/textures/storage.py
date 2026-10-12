"""Storage module textures: the Storage Terminal (a light steel console in the shared machine casing with a big screen)
and the three Storage Expansion modules.
Run: python3 scripts/textures/storage.py   (writes textures/block/storage_terminal_*.png and textures/item/storage_expansion_mk*.png;
models, blockstates, loot and recipes are written by scripts/data/storage_data.py)

The terminal's screen sits 1 px deep in the front on its own model element (scripts/data/storage_data.py): cyan tinted
glass while unpowered; powered, a full-bright display of the terminal's item grid under a search bar, browsing down
and back up a row at a time with its scrollbar following.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import Canvas, material, write_anim, write_block, write_item  # noqa: E402
import machine_casing as casing  # noqa: E402

ST, DK = 'abcde', 'fghij'
P = {'k': '#16191B', 'K': '#0B0D0E'}
P.update(material(ST, 'steel'))
P.update(material(DK, 'dark'))
P.update({'o': '#E8B530', 'O': '#FFE07A'})                            # gold contacts


# ---------------------------------------------------------------- block
# Chars on top of the casing: 'n' screen backdrop, 'o' scrollbar track, 'p' dim cyan, 'W' white, 'Q' unpowered glass,
# 'G' its glare, 'S' screen glass seen edge-on (the bezel walls).

TERMINAL_BODY = ('#4A535B', '#6F7A83', '#9AA5AE', '#C4CDD4', '#EBF0F3')   # light steel
SCREEN = (3, 3, 10, 8)                       # x, y, w, h of the screen on the front; its bezel walls sit round it
GRID_TOP, PITCH = 2, 3                       # screen rows above the item grid (a margin, the search bar); rows every 3 px
SCROLL = (0, 0, 1, 2, 3, 3, 3, 3)            # px scrolled in each frame of one row step: eased, then held
FRAMES, FRAMETIME = 32, 2                    # down two rows and back up: 64 ticks
# Item icons (2x2: top-left, top-right, bottom-left, bottom-right) in rows of three, the list the screen browses
ICONS = (('xwwv', 'wvvp', 'pwwx'), ('vxvw', 'xwpv', 'wwvv'), ('pvxw', 'wxvp', 'vwwx'), ('wwpv', 'pxwv', 'xvvw'))


def terminal_pal():
    a, b, c, d, e = TERMINAL_BODY
    return casing.casing_palette(casing.INDUSTRY_TIERS[1], a=a, b=b, c=c, d=d, e=e, n='#0B3341', o='#0F3846',
                                 p='#1D7F94', W='#FFFFFF', Q='#2A6472', G='#74B6C4', S='#0E2A33')


def scroll(frame):
    """Grid offset in px: two row steps down, then two back up."""
    step, t = divmod(frame, len(SCROLL))
    s = SCROLL[t]
    return (0, PITCH, 2 * PITCH, PITCH)[step] + (s if step < 2 else -s)


def terminal_screen(frame=None):
    """Screen face in SCREEN, its bezel walls round it (1 px: cols 2 and 13, rows 2 and 11). No frame: unpowered."""
    x0, y0, w, h = SCREEN
    c = Canvas()
    c.rect(x0 - 1, y0, 1, h, 'S').rect(x0 + w, y0, 1, h, 'S').rect(x0, y0 - 1, w, 1, 'S').rect(x0, y0 + h, w, 1, 'S')
    if frame is None:                                                    # tinted glass with a soft glare
        c.rect(x0, y0, w, h, 'Q').rect(x0, y0 + h - 1, w, 1, 'u').rect(x0 + w - 1, y0, 1, h, 'u')
        for i in (0, 1, 3):                                              # glare bands
            c.line(x0 + 2 + i, y0 + h - 2, x0 + 7 + i, y0 + 1 - 2 + 2, 'G')
        c.set(x0 + 1, y0 + 1, 'W')
        return c
    c.rect(x0, y0, w, h, 'n')
    c.rect(x0, y0 + 1, 8, 1, 'W').rect(x0 + 1, y0 + 1, 3, 1, 'x')        # search bar, its text and search icon
    c.set(x0 + 9, y0 + 1, 'w')
    off = scroll(frame)
    top = y0 + GRID_TOP
    for y in range(top, y0 + h):                                         # the grid, clipped to the viewport
        r, v = divmod(y - top + off, PITCH)
        if v == 2:
            continue
        for col, x in enumerate((0, 3, 6)):
            icon = ICONS[r % len(ICONS)][col]
            c.set(x0 + x, y, icon[2 * v]).set(x0 + x + 1, y, icon[2 * v + 1])
    track = h - GRID_TOP                                                 # scrollbar: thumb follows the list
    c.rect(x0 + 9, top, 1, track, 'o')
    t = top + round(off * (track - 3) / (2 * PITCH))
    c.rect(x0 + 9, t, 1, 3, 'w').set(x0 + 9, t, 'x')
    return c


def terminal_front(lit):
    """Casing with the screen opening (transparent), an item port below it and the status lamp."""
    x0, y0, w, h = SCREEN
    c = casing.shell(1)
    c.rect(x0, y0, w, h, '.')
    c.rect(4, 12, 6, 1, 'a').rect(4, 11, 6, 1, 'b').set(3, 12, 'b').set(10, 12, 'e')   # item port slit
    c.set(12, 12, 'L' if lit else 'l')
    return c


def terminal_front_glow():
    c = Canvas()
    c.set(12, 12, 'L')
    return c


def terminal_top():
    """Four drive bays in a recess, each with an activity light at its end."""
    c = casing.shell(1)
    c.recess(3, 3, 10, 10, 'abcde', fill='a')
    for y in (4, 6, 8, 10):
        c.rect(4, y, 6, 1, 'd').set(4, y, 'e').rect(10, y, 2, 1, 'Q')
    return c


def terminal_top_glow(frame):
    """Activity lights: a pulse stepping down the four bays and back, in step with the screen's browsing."""
    c = Canvas()
    head = (0, 1, 2, 3, 3, 2, 1, 0)[frame * 8 // FRAMES]
    for i, y in enumerate((4, 6, 8, 10)):
        c.rect(10, y, 2, 1, 'x' if i == head else 'w' if abs(i - head) == 1 else 'v')
    return c


def write_terminal():
    p = terminal_pal()
    write_block('storage_terminal_front', terminal_front(False).rows(), p)
    write_block('storage_terminal_front_on', terminal_front(True).rows(), p)
    write_block('storage_terminal_front_glow', terminal_front_glow().rows(), p)
    write_block('storage_terminal_side', casing.side(1).rows(), p)
    write_block('storage_terminal_bottom', casing.bottom().rows(), p)
    write_block('storage_terminal_top', terminal_top().rows(), p)
    write_block('storage_terminal_screen', terminal_screen().rows(), p)
    write_anim('block', 'storage_terminal_screen_on', [terminal_screen(f) for f in range(FRAMES)], p,
               frametime=FRAMETIME)
    write_anim('block', 'storage_terminal_top_glow', [terminal_top_glow(f) for f in range(FRAMES)], p,
               frametime=FRAMETIME, interpolate=True)


# ---------------------------------------------------------------- items

TIER = {1: 'copper', 2: 'cyan', 3: 'magma'}
T = 'ABCDE'


def expansion(mk):
    """A memory module card: slate board, tier coloured stripe, steel chip, gold contacts, one dot per Mk."""
    c = Canvas()
    c.rect(2, 1, 12, 14, 'k')
    c.rect(3, 2, 10, 12, DK[2])
    c.bevel(3, 2, 10, 12, DK[3], DK[1])
    c.rect(4, 3, 8, 2, T[2])
    c.rect(4, 3, 8, 1, T[3])
    c.rect(4, 4, 8, 1, T[1])
    c.rect(6, 6, 4, 4, ST[2])                        # chip
    c.bevel(6, 6, 4, 4, ST[4], ST[1])
    c.set(7, 7, ST[0]).set(8, 7, ST[0])
    for x in (5, 10):                                # chip pins
        for y in (6, 8):
            c.set(x, y, ST[3])
    for i in range(mk):                              # tier dots
        c.set(5 + i * 2, 11, T[4]).set(5 + i * 2, 12, T[2])
    for x in range(4, 12, 2):                        # gold contacts
        c.set(x, 13, 'o').set(x + 1, 13, 'O')
    return c


def main():
    write_terminal()
    for mk, name in TIER.items():
        pal = dict(P)
        pal.update(material(T, name))
        write_item(f'storage_expansion_mk{mk}', expansion(mk).rows(), pal)
    print('storage textures written')


if __name__ == '__main__':
    main()
