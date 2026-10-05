"""Warp module textures: ender teal / purple tech look on dark plating.
Run: python3 scripts/textures/warp.py
Writes block textures (pad, Portal Projector parts, animated portal vortex and rim) and item sprites (rift upgrade, remotes, linking card).
Block models, blockstates, loot and recipes come from scripts/data/warp_data.py.
"""
import json
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, MATERIALS, PALETTE, Canvas, write_anim, write_block, write_item, write_png  # noqa: E402

P = dict(PALETTE)
P.update({
    'k': '#14161E',                                        # outline
    'd': '#2A2E3A', 'D': '#3C4254', 'm': '#565D78', 'M': '#7A83A3',  # dark plating
    'e': '#2E8F7F', 'E': '#5AD6BE', 'W': '#D8FBFF', 'T': '#145247',  # ender teal
    'p': '#6A3FB0', 'P': '#A884F0', 'q': '#2A1850', 'Q': '#E0CFFF',  # ender purple
})


P.update({'g': '#323746', 'G': '#262A36'})


def plate(c, x, y, w, h, seed=0):
    """Dark bevelled plate with light top/left and black bottom/right edge, faint brushed grain."""
    c.plate(x, y, w, h, 'kGdmM', seed, grain='Gg', density=0.25)


def rivets(c, ch='M', inset=2):
    for x, y in ((inset, inset), (15 - inset, inset), (inset, 15 - inset), (15 - inset, 15 - inset)):
        c.set(x, y, ch)


def ring(c, cx, cy, r_in, r_out, ch):
    c.ring(cx, cy, r_in, r_out, ch)


# ---- Warp Pad ----

def pad_top(rift):
    """Landing ring on dark plating: the ring channels are dim here, the overlay lights them."""
    dim = 'q' if rift else 'T'
    c = Canvas()
    plate(c, 0, 0, 16, 16, 1)
    c.rect(1, 1, 14, 14, 'd').frame(1, 1, 14, 14, 'k')
    c.rect(1, 1, 14, 1, 'D').rect(1, 1, 1, 14, 'D')
    ring(c, 7.5, 7.5, 4.9, 6.7, 'k')
    ring(c, 7.5, 7.5, 5.4, 6.3, dim)
    ring(c, 7.5, 7.5, 2.3, 3.7, 'D')
    ring(c, 7.5, 7.5, 2.8, 3.3, dim)
    c.disc(7.5, 7.5, 1.6, 'k')
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)):
        c.set(x, y, dim)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.rivet(x, y, 'kGdmM')
    return c.rows()


def pad_top_glow(rift, frame_no):
    """Pulsing ring: a bright arc chases around the outer ring, the core breathes."""
    glow, bright, dim = ('p', 'Q', 'P') if rift else ('e', 'W', 'E')
    c = Canvas()
    pts = [(x, y) for y in range(16) for x in range(16) if 5.4 <= math.hypot(x - 7.5, y - 7.5) <= 6.3]
    pts.sort(key=lambda p: math.atan2(p[1] - 7.5, p[0] - 7.5))
    for i, (x, y) in enumerate(pts):
        k = (i - frame_no * 2) % len(pts)
        c.set(x, y, bright if k < 3 else dim if k < 8 else glow)
    inner = [(x, y) for y in range(16) for x in range(16) if 2.8 <= math.hypot(x - 7.5, y - 7.5) <= 3.3]
    for x, y in inner:
        c.set(x, y, dim if frame_no % 8 < 4 else glow)
    c.disc(7.5, 7.5, 1.0, bright if frame_no % 8 < 4 else dim)
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)):
        c.set(x, y, dim)
    return c


def pad_side(rift):
    """Rows 0-7 show (the pad is 8 px tall): lit lip, glow channel (dim; the overlay lights it), base band with bolts."""
    dim = 'q' if rift else 'T'
    c = Canvas()
    plate(c, 0, 0, 16, 16, 2)
    c.rect(0, 0, 16, 1, 'M').rect(0, 1, 16, 1, 'm')
    c.rect(0, 2, 16, 3, 'k').rect(1, 3, 14, 1, dim)
    c.rect(0, 5, 16, 1, 'm').rect(0, 6, 16, 1, 'd').rect(0, 7, 16, 1, 'k')
    for x in (2, 7, 12):
        c.set(x, 6, 'M')
    return c.rows()


def pad_side_glow(rift):
    c = Canvas()
    c.rect(1, 3, 14, 1, 'P' if rift else 'E')
    c.set(4, 3, 'Q' if rift else 'W').set(11, 3, 'Q' if rift else 'W')
    return c


def pad_base():
    c = Canvas()
    plate(c, 0, 0, 16, 16, 3)
    c.rect(3, 3, 10, 10, 'D').frame(3, 3, 10, 10, 'k')
    rivets(c)
    return c.rows()


def rift_stud():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'p').bevel(0, 0, 16, 16, 'P', 'q')
    c.rect(5, 5, 6, 6, 'Q').frame(4, 4, 8, 8, 'P')
    return c.rows()


# ---- Portal Projector (registry id gate_controller) ----
# The side texture is one profile of the whole body: row r covers block height pixels [15 - r, 16 - r], so every
# element of the model reads its own rows (uv v = 16 - top .. 16 - bottom).

def projector_side():
    c = Canvas()
    plate(c, 0, 0, 16, 16, 4)
    c.rect(0, 13, 16, 3, 'D').rect(0, 13, 16, 1, 'm').rect(0, 15, 16, 1, 'k')   # base plate, y 0-2
    for x in (1, 6, 10, 14):
        c.set(x, 14, 'M')
    c.rect(0, 12, 16, 1, 'k')                                   # groove between base and neck, y 3
    c.rect(0, 8, 16, 4, 'd').rect(0, 8, 16, 1, 'D')             # neck, y 4-7
    c.rect(0, 9, 16, 2, 'k').rect(1, 9, 14, 1, 'e').rect(1, 10, 14, 1, 'T')   # glow groove, y 5-6
    for x in (3, 8, 13):
        c.set(x, 9, 'E')
    c.rect(0, 7, 16, 1, 'k')                                    # groove between neck and head, y 8
    c.rect(0, 5, 16, 2, 'D').rect(0, 5, 16, 1, 'm')             # head, y 9-10
    c.rect(0, 4, 16, 1, 'M')                                    # head lip, y 11
    for x in (2, 7, 12):
        c.set(x, 6, 'k').set(x + 1, 6, 'm')
    return c.rows()


def projector_side_glow():
    """The neck's glow groove, lit while the portal is open."""
    c = Canvas()
    c.rect(1, 9, 14, 1, 'E').rect(1, 10, 14, 1, 'e')
    for x in (3, 8, 13):
        c.set(x, 9, 'W')
    return c


def projector_top():
    c = Canvas()
    plate(c, 0, 0, 16, 16, 5)
    c.frame(2, 2, 12, 12, 'k')
    ring(c, 7.5, 7.5, 4.4, 5.6, 'D')
    ring(c, 7.5, 7.5, 4.4, 4.9, 'k')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.rivet(x, y, 'kGdmM')
    return c.rows()


def projector_lens(on):
    """Lens disc seen from above (and its 1 pixel rim): bright teal rings when lit, dim purple glass when idle."""
    c = Canvas()
    if on:
        c.rect(0, 0, 16, 16, 'E')
        ring(c, 7.5, 7.5, 0, 6.8, 'E')
        ring(c, 7.5, 7.5, 0, 4.6, 'W')
        ring(c, 7.5, 7.5, 0, 2.2, 'W')
        ring(c, 7.5, 7.5, 3.2, 4.2, 'Y')
    else:
        c.rect(0, 0, 16, 16, 'q')
        ring(c, 7.5, 7.5, 3.2, 6.8, 'p')
        ring(c, 7.5, 7.5, 0, 2.6, 'P')
    return c.rows()


def projector_fin(on):
    """Front prow: dark plate with a vertical emitter slit."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd').bevel(0, 0, 16, 16, 'm', 'k')
    c.rect(6, 2, 4, 12, 'k')
    c.rect(7, 3, 2, 10, 'W' if on else 'q')
    if on:
        c.rect(7, 4, 2, 8, 'Y')
    return c.rows()


# ---- Animated portal (32x32 frames) ----

FRAMES = 24
VORTEX_PALETTE = {
    '0': '#1A0B3AB8', '1': '#2A1458BE', '2': '#3F2090C4', '3': '#5A2FA8C8', '4': '#4A55B8CC', '5': '#2E7FB8D0',
    '6': '#2EA6B4D4', '7': '#2EC9B0D8', '8': '#5AE0C6E0', '9': '#A0F4E4E8', 'a': '#D8FBFFEE', 'b': '#FFFFFFF4',
}
BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def vortex_frames():
    """Two counter turning spiral systems around a bright core. A full loop turns every term by a whole period, so it is seamless."""
    rows = []
    size = 32
    for f in range(FRAMES):
        phase = 2 * math.pi * f / FRAMES
        for y in range(size):
            row = ''
            for x in range(size):
                dx, dy = (x - 15.5) / 16.0, (y - 15.5) / 16.0
                r = math.hypot(dx, dy)
                theta = math.atan2(dy, dx)
                arms = 0.5 + 0.5 * math.sin(3 * theta + 9.0 * r - phase)
                swirl = 0.5 + 0.5 * math.sin(2 * theta - 5.0 * r + 2 * phase + 1.3)
                core = max(0.0, 1.0 - r * 1.6) ** 1.5
                glow = 0.45 * arms + 0.30 * swirl + 0.55 * core
                glow *= 0.78 + 0.22 * (1.0 - r)
                glow += (BAYER[y % 4][x % 4] / 16.0 - 0.5) * 0.07
                level = int(min(11, max(0, glow * 12)))
                row += '0123456789ab'[level]
            rows.append(row)
    return rows


def write_portal():
    base = ASSETS / 'textures/block'
    write_png(base / 'portal_vortex.png', vortex_frames(), VORTEX_PALETTE, size=None)
    (base / 'portal_vortex.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 2, 'interpolate': True}}, indent=2) + '\n')
    rim = Canvas()
    rim.rect(0, 0, 16, 16, 'W')
    for x, y in ((3, 4), (9, 7), (12, 11), (5, 12)):
        rim.set(x, y, 'Y')
    write_png(base / 'portal_rim.png', rim.rows(), P)


# ---- Items ----
# Item icons follow the mod wide item look (see core.py): outline 'k' #1E1A1A, light from the top-left, ramps from
# pixelart.MATERIALS. Chars: '12345' null purple (deep -> highlight), 'abcde' steel, 'stuvw' teal, '6789' brass,
# 'K' near black glass.

def item_pal():
    p = {'k': '#1E1A1A', 'K': '#0E0C0C'}
    p.update(zip('12345', MATERIALS['null']))
    p.update(zip('abcde', MATERIALS['steel']))
    p.update(zip('stuvw', MATERIALS['teal']))
    b = MATERIALS['brass']
    p.update({'6': b[1], '7': b[2], '8': b[3], '9': b[4]})
    return p


IP = item_pal()


def rift_upgrade():
    """Upgrade card (same shape as the core upgrade cards): steel card, a portal ring in the window, null contacts."""
    c = Canvas()
    c.rect(2, 1, 12, 14, 'd')
    c.set(13, 1, '.').set(12, 1, '.').set(13, 2, '.')                    # clipped corner
    c.auto_shade({'d': ('c', 'e')})
    c.recess(4, 3, 8, 6, 'Kbcde', fill='1')
    c.rect(5, 4, 6, 4, '1').rect(5, 4, 6, 1, '2').set(5, 4, '3')
    for dx, row in enumerate(['..vv..', '.v44u.', '.v45u.', '..uu..']):    # portal oval
        for x, ch in enumerate(row):
            if ch != '.':
                c.set(5 + x, 4 + dx, ch)
    for dy, row in enumerate(['.3...3.', '3.3.3.3', '...3...']):
        for dx, ch in enumerate(row):
            if ch == '3':
                c.set(4 + dx, 10 + dy, '3')
    for x in range(3, 13, 2):
        c.set(x, 14, '7')
    c.outline('k')
    return c.rows()


def remote(rift):
    """Hand-held transmitter: steel body, recessed screen with a pad ring (recall, teal) or a ring split by a rift
    slit (rift, purple), d-pad, buttons and an antenna with a glowing tip and signal arcs."""
    glow, bright, dim, deep = ('w', 'v', 'u', 's') if not rift else ('5', '4', '3', '1')
    c = Canvas()
    c.rect(2, 6, 11, 9, 'c')
    c.auto_shade({'c': ('b', 'd')})
    c.recess(3, 7, 9, 5, 'Kbcde', fill=deep)
    glyph = ['.44544.', '4..5..4', '.44544.'] if rift else ['.44444.', '4.555.4', '.44444.']
    for dy, row in enumerate(glyph):
        for dx, ch in enumerate(row):
            if ch != '.':
                c.set(4 + dx, 8 + dy, glow if ch == '5' else bright)
    for x, y in ((4, 12), (3, 13), (5, 13), (4, 14)):                    # d-pad
        c.set(x, y, 'b')
    c.set(4, 13, 'k').set(4, 12, 'e')
    c.set(8, 13, bright).set(9, 13, glow).set(10, 13, dim)               # buttons
    for x, y in ((11, 5), (12, 4)):                                      # antenna
        c.set(x, y, 'd')
    c.set(13, 2, glow).set(13, 3, bright).set(14, 2, bright).set(14, 3, dim)    # tip
    c.outline('k')
    return c.rows()


def linking_card():
    """Key card: null purple body with a dark stripe, a chain link (teal and white loops) and a brass contact chip."""
    c = Canvas()
    c.rect(1, 3, 14, 10, '3')
    c.auto_shade({'3': ('2', '4')})
    c.rect(2, 4, 12, 2, '1').rect(2, 4, 12, 1, 'K').rect(2, 5, 12, 1, '2')    # magnetic stripe
    c.set(2, 6, '4')
    for x in range(5, 9):                                               # left loop, teal
        c.set(x, 7, 'v').set(x, 10, 'u')
    c.set(4, 8, 'v').set(4, 9, 'v').set(8, 8, 'u').set(8, 9, 'u')
    for x in range(8, 12):                                              # right loop, steel
        c.set(x, 7, 'e').set(x, 10, 'c')
    c.set(7, 8, 'd').set(7, 9, 'c').set(12, 8, 'd').set(12, 9, 'c')
    c.set(8, 7, 'v').set(7, 10, 'c')                                    # weave
    c.rect(2, 11, 3, 1, '7').set(2, 11, '8').rect(11, 11, 3, 1, '2')    # chip, serial bar
    c.outline('k')
    return c.rows()


def main():
    write_block('warp_pad_top', pad_top(False), P)
    write_block('warp_pad_rift_top', pad_top(True), P)
    write_block('warp_pad_side', pad_side(False), P)
    write_block('warp_pad_rift_side', pad_side(True), P)
    for rift, name in ((False, 'warp_pad'), (True, 'warp_pad_rift')):
        write_anim('block', f'{name}_top_glow', [pad_top_glow(rift, i) for i in range(16)], P, frametime=2)
        write_block(f'{name}_side_glow', pad_side_glow(rift).rows(), P)
    write_block('warp_pad_base', pad_base(), P)
    write_block('warp_pad_rift_stud', rift_stud(), P)
    write_block('projector_side', projector_side(), P)
    write_block('projector_top', projector_top(), P)
    write_block('projector_side_glow', projector_side_glow().rows(), P)
    write_block('projector_lens', projector_lens(False), P)
    write_block('projector_lens_on', projector_lens(True), P)
    write_block('projector_fin', projector_fin(False), P)
    write_block('projector_fin_on', projector_fin(True), P)
    write_portal()
    write_item('rift_upgrade', rift_upgrade(), IP)
    write_item('recall_remote', remote(False), IP)
    write_item('rift_remote', remote(True), IP)
    write_item('linking_card', linking_card(), IP)
    print('warp textures written')


if __name__ == '__main__':
    main()
