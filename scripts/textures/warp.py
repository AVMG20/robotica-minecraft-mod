"""Warp module textures: ender teal / purple tech look on dark plating.
Run: python3 scripts/textures/warp.py
Writes block textures (pad, gate frame, controller, animated gate portal) and item sprites (rift upgrade, remotes, linking card).
Block models, blockstates, loot and recipes come from scripts/data/warp_data.py.
"""
import json
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, PALETTE, Canvas, write_block, write_item, write_png  # noqa: E402

P = dict(PALETTE)
P.update({
    'k': '#14161E',                                        # outline
    'd': '#2A2E3A', 'D': '#3C4254', 'm': '#565D78', 'M': '#7A83A3',  # dark plating
    'e': '#2E8F7F', 'E': '#5AD6BE', 'W': '#D8FBFF', 'T': '#145247',  # ender teal
    'p': '#6A3FB0', 'P': '#A884F0', 'q': '#2A1850', 'Q': '#E0CFFF',  # ender purple
})


def plate(c, x, y, w, h):
    """Dark bevelled plate with light top/left and black bottom/right edge."""
    c.rect(x, y, w, h, 'd').bevel(x, y, w, h, 'm', 'k')


def rivets(c, ch='M', inset=2):
    for x, y in ((inset, inset), (15 - inset, inset), (inset, 15 - inset), (15 - inset, 15 - inset)):
        c.set(x, y, ch)


def ring(c, cx, cy, r_in, r_out, ch):
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - cx, y - cy)
            if r_in <= r <= r_out:
                c.set(x, y, ch)


# ---- Warp Pad ----

def pad_top(rift):
    glow, bright, dim = ('P', 'Q', 'q') if rift else ('E', 'W', 'T')
    c = Canvas()
    plate(c, 0, 0, 16, 16)
    c.frame(1, 1, 14, 14, 'D')
    ring(c, 7.5, 7.5, 5.4, 6.6, glow)
    ring(c, 7.5, 7.5, 3.2, 4.0, dim)
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)):
        c.set(x, y, glow)
    c.rect(6, 6, 4, 4, bright)
    c.rect(7, 7, 2, 2, 'W' if not rift else 'Q')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, glow)
    return c.rows()


def pad_side(rift):
    """Rows 0-2: base band (shown at the bottom of the block), rows 3-7: upper band with a glow strip."""
    glow = 'P' if rift else 'E'
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd')
    c.rect(0, 0, 16, 3, 'D').rect(0, 2, 16, 1, 'k')
    for x in (2, 6, 10, 14):
        c.set(x, 1, 'M')
    c.rect(0, 3, 16, 5, 'd').rect(0, 3, 16, 1, 'm')
    c.rect(1, 5, 14, 2, 'k').rect(2, 5, 12, 1, glow).rect(2, 6, 12, 1, 'T' if not rift else 'q')
    return c.rows()


def pad_base():
    c = Canvas()
    plate(c, 0, 0, 16, 16)
    c.rect(3, 3, 10, 10, 'D').frame(3, 3, 10, 10, 'k')
    rivets(c)
    return c.rows()


def rift_stud():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'p').bevel(0, 0, 16, 16, 'P', 'q')
    c.rect(5, 5, 6, 6, 'Q').frame(4, 4, 8, 8, 'P')
    return c.rows()


# ---- Gate ----

def frame_tex():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd').bevel(0, 0, 16, 16, 'm', 'k')
    c.rect(2, 2, 12, 12, 'D').frame(2, 2, 12, 12, 'k')
    c.rect(3, 7, 10, 2, 'k').rect(4, 7, 8, 1, 'p').rect(4, 8, 8, 1, 'q')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'M')
    c.set(7, 7, 'P').set(8, 7, 'P')
    return c.rows()


def controller_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd').bevel(0, 0, 16, 16, 'm', 'k')
    c.rect(2, 2, 12, 12, 'D').frame(2, 2, 12, 12, 'k')
    for y in (4, 6, 9, 11):
        c.rect(4, y, 8, 1, 'k')
    c.rect(4, 7, 8, 1, 'e')
    return c.rows()


def controller_top():
    c = Canvas()
    plate(c, 0, 0, 16, 16)
    ring(c, 7.5, 7.5, 3.0, 4.2, 'p')
    c.rect(6, 6, 4, 4, 'k').rect(7, 7, 2, 2, 'e')
    rivets(c)
    return c.rows()


def controller_front(on):
    c = Canvas()
    c.rect(0, 0, 16, 16, 'd').bevel(0, 0, 16, 16, 'm', 'k')
    c.rect(2, 2, 12, 12, 'k')
    if on:
        c.rect(3, 3, 10, 10, 'e')
        ring(c, 7.5, 7.5, 2.0, 4.6, 'E')
        c.rect(6, 6, 4, 4, 'W')
        c.frame(3, 3, 10, 10, 'T')
    else:
        c.rect(3, 3, 10, 10, 'q')
        ring(c, 7.5, 7.5, 2.0, 4.6, 'p')
        c.rect(6, 6, 4, 4, 'k').rect(7, 7, 2, 2, 'm')
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, 'M')
    return c.rows()


# ---- Animated portal ----

FRAMES = 16
PORTAL_PALETTE = {
    '0': '#2A1850B4', '1': '#4A2C88BC', '2': '#6A3FB0C4', '3': '#3A7FB8CC',
    '4': '#2E8F7FD0', '5': '#5AD6BED8', '6': '#A884F0E0', '7': '#D8FBFFEA',
}


def portal_frames():
    """Swirl made of three spiral arms; phase over the strip is a whole period so the loop is seamless."""
    rows = []
    for f in range(FRAMES):
        phase = 2 * math.pi * f / FRAMES
        for y in range(16):
            row = ''
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                theta = math.atan2(dy, dx)
                v = 0.5 + 0.5 * math.sin(3 * theta + 0.55 * r - phase)
                w = 0.5 + 0.5 * math.sin(2 * theta - 0.35 * r + phase + 1.3)
                s = 0.62 * v + 0.38 * w
                level = int(min(7, max(0, s * 8)))
                row += str(level)
            rows.append(row)
    return rows


def write_portal():
    rows = portal_frames()
    path = ASSETS / 'textures/block/gate_portal.png'
    write_png(path, rows, PORTAL_PALETTE, size=None)
    (path.parent / 'gate_portal.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 2, 'interpolate': True}}, indent=2) + '\n')


# ---- Items ----

def rift_upgrade():
    """Purple crystal ring chip on a dark card."""
    c = Canvas()
    c.rect(1, 3, 14, 10, 'd').bevel(1, 3, 14, 10, 'm', 'k')
    c.frame(1, 3, 14, 10, 'k')
    ring(c, 7.5, 7.5, 2.6, 4.2, 'P')
    ring(c, 7.5, 7.5, 1.0, 2.2, 'p')
    c.rect(7, 7, 2, 2, 'Q')
    for x, y in ((3, 5), (12, 5), (3, 10), (12, 10)):
        c.set(x, y, 'E')
    c.rect(1, 13, 14, 1, 'q')
    return c.rows()


def remote(rift):
    glow, bright, dim = ('P', 'Q', 'q') if rift else ('E', 'W', 'T')
    c = Canvas()
    c.rect(5, 5, 6, 10, 'd').bevel(5, 5, 6, 10, 'm', 'k')
    c.frame(5, 5, 6, 10, 'k')
    c.rect(6, 6, 4, 4, 'k').rect(7, 7, 2, 2, glow).set(7, 7, bright)  # screen
    c.set(6, 12, glow).set(9, 12, 'M').set(6, 13, 'M').set(9, 13, glow)  # buttons
    c.rect(7, 2, 2, 3, 'm').frame(7, 2, 2, 3, 'k')  # antenna
    c.set(7, 1, glow).set(8, 1, glow).set(7, 0, bright).set(8, 0, bright)
    if rift:
        c.set(4, 8, 'P').set(11, 8, 'P').set(4, 9, 'p').set(11, 9, 'p')
    return c.rows()


def linking_card():
    c = Canvas()
    c.rect(1, 3, 14, 10, 'D').bevel(1, 3, 14, 10, 'M', 'k')
    c.frame(1, 3, 14, 10, 'k')
    c.rect(2, 4, 12, 1, 'p')
    for cx in (4, 11):
        c.rect(cx - 1, 7, 3, 3, 'k').rect(cx, 8, 1, 1, 'E')
    c.rect(6, 8, 4, 1, 'e')
    c.set(5, 8, 'e').set(10, 8, 'e')
    c.rect(2, 11, 8, 1, 'm')
    return c.rows()


def main():
    write_block('warp_pad_top', pad_top(False), P)
    write_block('warp_pad_rift_top', pad_top(True), P)
    write_block('warp_pad_side', pad_side(False), P)
    write_block('warp_pad_rift_side', pad_side(True), P)
    write_block('warp_pad_base', pad_base(), P)
    write_block('warp_pad_rift_stud', rift_stud(), P)
    write_block('gate_frame', frame_tex(), P)
    write_block('gate_controller_side', controller_side(), P)
    write_block('gate_controller_top', controller_top(), P)
    write_block('gate_controller_front', controller_front(False), P)
    write_block('gate_controller_front_on', controller_front(True), P)
    write_portal()
    write_item('rift_upgrade', rift_upgrade(), P)
    write_item('recall_remote', remote(False), P)
    write_item('rift_remote', remote(True), P)
    write_item('linking_card', linking_card(), P)
    print('warp textures written')


if __name__ == '__main__':
    main()
