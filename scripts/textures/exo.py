"""Exo-Frame textures: armor item icons, module icons and the humanoid armor layers (64x32).
Run: python3 scripts/textures/exo.py

Mk1 = copper plating with brass trim and cyan lights, Mk2 = steel plating with cyan trim and lights.
Armor layers go to textures/models/armor/exo_mk{1,2}_layer_{1,2}.png (layer 1: helmet, chestplate, boots; layer 2: leggings).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, PALETTE, Canvas, write_item, write_png  # noqa: E402

# Plating palettes: m mid, M light, n dark, T trim, t dark trim, a light (cyan), A bright light
MK1 = {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22', 'T': '#D4A73A', 't': '#8F6D1C', 'a': '#5FE3F0', 'A': '#D8FBFF'}
MK2 = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#4D5558', 'T': '#2A8FA6', 't': '#14566A', 'a': '#5FE3F0', 'A': '#D8FBFF'}


def pal(*dicts):
    out = dict(PALETTE)
    for d in dicts:
        out.update(d)
    return out


# ------------------------------------------------------------------ armor icons (16x16)

def helmet():
    c = Canvas()
    c.rect(4, 2, 8, 2, 'm').rect(3, 4, 10, 6, 'm').rect(3, 10, 3, 3, 'm').rect(10, 10, 3, 3, 'm')
    c.rect(4, 2, 8, 1, 'M').rect(3, 4, 1, 8, 'M').rect(12, 5, 1, 8, 'n').rect(3, 12, 3, 1, 'n').rect(10, 12, 3, 1, 'n')
    c.rect(7, 1, 2, 3, 'T').set(7, 1, 'T')
    c.rect(4, 6, 8, 3, 'k').rect(5, 7, 6, 1, 'a').set(5, 7, 'A').set(6, 7, 'A')
    c.rect(4, 5, 8, 1, 't')
    c.set(3, 8, 'T').set(12, 8, 'T')
    c.outline('k')
    return c.rows()


def chestplate():
    c = Canvas()
    c.rect(1, 2, 4, 5, 'm').rect(11, 2, 4, 5, 'm')
    c.rect(3, 3, 10, 11, 'm')
    c.rect(6, 2, 4, 2, '.')
    c.rect(1, 2, 4, 1, 'M').rect(11, 2, 4, 1, 'M').rect(3, 4, 1, 10, 'M').rect(12, 4, 1, 10, 'n')
    c.rect(3, 13, 10, 1, 'n').rect(1, 6, 4, 1, 'n').rect(11, 6, 4, 1, 'n')
    c.rect(6, 4, 4, 1, 'T').rect(3, 11, 10, 1, 'T')
    c.rect(6, 6, 4, 4, 'k').rect(7, 7, 2, 2, 'a').set(7, 7, 'A')
    c.set(1, 3, 'T').set(14, 3, 'T')
    c.outline('k')
    return c.rows()


def leggings():
    c = Canvas()
    c.rect(3, 1, 10, 4, 'm').rect(3, 5, 4, 9, 'm').rect(9, 5, 4, 9, 'm')
    c.rect(3, 1, 10, 1, 'M').rect(3, 5, 1, 9, 'M').rect(9, 5, 1, 9, 'M').rect(6, 5, 1, 9, 'n').rect(12, 5, 1, 9, 'n')
    c.rect(3, 13, 4, 1, 'n').rect(9, 13, 4, 1, 'n').rect(3, 4, 10, 1, 'n')
    c.rect(3, 3, 10, 1, 'T').rect(7, 2, 2, 2, 'k').set(7, 3, 'a')
    c.rect(4, 8, 2, 2, 'T').rect(10, 8, 2, 2, 'T').set(4, 8, 'B').set(10, 8, 'B')
    c.outline('k')
    return c.rows()


def boots():
    c = Canvas()
    for x0 in (1, 9):
        c.rect(x0 + 1, 3, 4, 6, 'm').rect(x0, 9, 6, 4, 'm')
        c.rect(x0 + 1, 3, 1, 6, 'M').rect(x0, 9, 6, 1, 'M').rect(x0 + 4, 4, 1, 5, 'n').rect(x0, 12, 6, 1, 'n')
        c.rect(x0 + 1, 3, 4, 1, 'T').rect(x0, 11, 6, 1, 't')
        c.set(x0 + 2, 6, 'a')
    c.outline('k')
    return c.rows()


ICONS = {'helmet': helmet, 'chestplate': chestplate, 'leggings': leggings, 'boots': boots}


# ------------------------------------------------------------------ module icons (16x16 circuit cards)

HEAD = {'m': '#2A8FA6', 'M': '#7FE3F5', 'n': '#14566A'}
CHEST = {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22'}
LEGS = {'m': '#D4A73A', 'M': '#F2D27A', 'n': '#8F6D1C'}
FEET = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#4D5558'}

# 6 wide, 8 tall glyphs; X = glyph, x = dim glyph
GLYPHS = {
    'night_vision': ['......', '.XXXX.', 'X.XX.X', 'X.XX.X', '.XXXX.', '......', '......', '......'],
    'rebreather': ['..X...', '.X.X..', '..X..X', '.....X.', 'X..X..', '.X.X..', '..X...', '......'],
    'robot_hud': ['XXXXXX', 'X....X', 'X.XX.X', 'X.X..X', 'X....X', 'XXXXXX', '..XX..', '.XXXX.'],
    'jet_assist': ['..XX..', '.XXXX.', 'XXXXXX', '..XX..', '..XX..', '.x..x.', '.x..x.', '..xx..'],
    'flight': ['X....X', 'XX..XX', 'XXXXXX', '.XXXX.', '..XX..', '..XX..', '......', '......'],
    'kinetic_shield': ['XXXXXX', 'XXXXXX', 'XXXXXX', 'XXXXXX', '.XXXX.', '.XXXX.', '..XX..', '..XX..'],
    'servo_stride_1': ['......', 'X.....', '.X....', '..X...', '.X....', 'X.....', '......', '......'],
    'servo_stride_2': ['......', 'X.X...', '.X.X..', '..X.X.', '.X.X..', 'X.X...', '......', '......'],
    'servo_stride_3': ['......', 'X.X.X.', '.X.X.X', '..X.X.', '.X.X.X', 'X.X.X.', '......', '......'],
    'step_assist': ['....XX', '....XX', '..XXXX', '..XXXX', 'XXXXXX', 'XXXXXX', '......', '......'],
    'spring_heels': ['.XXXX.', 'X....X', '.XXXX.', 'X....X', '.XXXX.', 'X....X', '.XXXX.', '......'],
    'fall_dampener': ['..XX..', '..XX..', '..XX..', 'XXXXXX', '.XXXX.', '..XX..', 'XXXXXX', 'xxxxxx'],
    'magnet': ['XX..XX', 'XX..XX', 'XX..XX', 'XX..XX', 'XXXXXX', '.XXXX.', '......', '......'],
}


def module(name, colors):
    c = Canvas()
    c.rect(3, 1, 10, 14, 'm').bevel(3, 1, 10, 14, 'M', 'n').frame(2, 0, 12, 16, 'k')
    # glyph window
    c.rect(4, 2, 8, 9, 'k').rect(5, 3, 6, 8, 'n')
    for gy, row in enumerate(GLYPHS[name]):
        for gx, ch in enumerate(row):
            if ch == 'X':
                c.set(5 + gx, 3 + gy, 'A')
            elif ch == 'x':
                c.set(5 + gx, 3 + gy, 'a')
    # gold contacts
    for x in (4, 6, 8, 10):
        c.rect(x, 12, 1, 2, 'o')
    return c.rows()


MODULE_COLORS = {
    'night_vision': HEAD, 'rebreather': HEAD, 'robot_hud': HEAD,
    'jet_assist': CHEST, 'flight': CHEST, 'kinetic_shield': CHEST,
    'servo_stride_1': LEGS, 'servo_stride_2': LEGS, 'servo_stride_3': LEGS, 'step_assist': LEGS,
    'spring_heels': FEET, 'fall_dampener': FEET, 'magnet': FEET,
}
MODULE_FILES = {
    'night_vision': 'night_vision_module', 'rebreather': 'rebreather_module', 'robot_hud': 'robot_hud_module',
    'jet_assist': 'jet_assist_module', 'flight': 'flight_module', 'kinetic_shield': 'kinetic_shield_module',
    'servo_stride_1': 'servo_stride_module_1', 'servo_stride_2': 'servo_stride_module_2', 'servo_stride_3': 'servo_stride_module_3',
    'step_assist': 'step_assist_module', 'spring_heels': 'spring_heels_module', 'fall_dampener': 'fall_dampener_module',
    'magnet': 'magnet_module',
}


# ------------------------------------------------------------------ armor layers (64x32)

class Layer:
    """64x32 humanoid armor texture. box() lays out the six faces of a cuboid like the vanilla model UVs."""

    W, H = 64, 32

    def __init__(self):
        self.px = [['.'] * self.W for _ in range(self.H)]

    def put(self, x, y, ch):
        if 0 <= x < self.W and 0 <= y < self.H and ch != '.':
            self.px[y][x] = ch

    def face(self, x0, y0, w, h, painter, name):
        for ly in range(h):
            for lx in range(w):
                self.put(x0 + lx, y0 + ly, painter(name, lx, ly, w, h))

    def box(self, u, v, w, h, d, painter):
        self.face(u + d, v, w, d, painter, 'top')
        self.face(u + d + w, v, w, d, painter, 'bottom')
        self.face(u, v + d, d, h, painter, 'right')
        self.face(u + d, v + d, w, h, painter, 'front')
        self.face(u + d + w, v + d, d, h, painter, 'left')
        self.face(u + 2 * d + w, v + d, w, h, painter, 'back')

    def rows(self):
        return [''.join(r) for r in self.px]


def plate(face, lx, ly, fw, fh):
    """Base plating: light top/left edges, dark bottom/right edges."""
    if face == 'top':
        return 'M'
    if face == 'bottom':
        return 'n'
    if ly == 0 or lx == 0:
        return 'M'
    if ly == fh - 1 or lx == fw - 1:
        return 'n'
    return 'm'


def helmet_paint(face, lx, ly, fw, fh):
    ch = plate(face, lx, ly, fw, fh)
    if face == 'top' and lx in (fw // 2 - 1, fw // 2):
        return 'T'
    if face in ('front',):
        if ly == 3 and 1 <= lx <= fw - 2:
            return 'k'
        if ly == 4 and 1 <= lx <= fw - 2:
            return 'k'
        if ly == 4 and 2 <= lx <= fw - 3:
            return 'a'
        if ly == 2:
            return 't'
        if ly == fh - 1 and lx in (0, fw - 1):
            return 'T'
    if face in ('left', 'right') and ly == 4 and lx in (2, 3):
        return 'T'
    if face == 'back' and ly in (3, 5) and 1 <= lx <= fw - 2:
        return 't'
    return ch


def chest_paint(face, lx, ly, fw, fh):
    ch = plate(face, lx, ly, fw, fh)
    if face == 'front':
        if lx in (3, 4) and ly in (3, 4, 5):
            return 'A' if (lx, ly) == (3, 3) else 'a'
        if ly == 2 and 1 <= lx <= fw - 2:
            return 'T'
        if ly == 8 and 0 < lx < fw - 1:
            return 'T'
        if lx in (2, 5) and ly in (3, 4, 5):
            return 'k'
    if face == 'back' and lx in (3, 4) and 1 <= ly <= fh - 2:
        return 't'
    if face == 'back' and ly == 8 and 0 < lx < fw - 1:
        return 'T'
    return ch


def arm_paint(face, lx, ly, fw, fh):
    if face in ('top', 'bottom'):
        return 'T' if face == 'top' else 'n'
    if ly < 4:  # shoulder pad
        if ly == 3:
            return 't'
        if lx in (0, fw - 1):
            return 'T'
        return 'T'
    if ly == 7 and 0 < lx < fw - 1:
        return 'a' if lx == 1 else 't'
    return plate(face, lx, ly, fw, fh)


def boots_paint(face, lx, ly, fw, fh):
    if face == 'top':
        return '.'
    if face == 'bottom':
        return 'n'
    if ly < 6:
        return '.'
    if ly == 6:
        return 'T'
    if ly == 7 and face == 'front' and lx in (1, 2):
        return 'a'
    if ly == fh - 1:
        return 'k'
    return plate(face, lx, ly, fw, fh)


def belt_paint(face, lx, ly, fw, fh):
    if face == 'top':
        return '.'
    if face == 'bottom':
        return 'n'
    if ly < 7:
        return '.'
    if ly == 7:
        return 'M'
    if ly == 8:
        return 'T'
    if ly == 9 and face == 'front' and lx in (fw // 2 - 1, fw // 2):
        return 'a'
    if ly == 11:
        return 'n'
    return plate(face, lx, ly, fw, fh) if ly != 9 else 'm'


def legs_paint(face, lx, ly, fw, fh):
    if face == 'top':
        return 'n'
    if face == 'bottom':
        return 'n'
    if ly in (5, 6) and face in ('front', 'left', 'right', 'back'):
        return 'T' if ly == 5 else 't'
    if ly == 6 and face == 'front' and lx in (1, 2):
        return 'a'
    return plate(face, lx, ly, fw, fh)


def layer1():
    t = Layer()
    t.box(0, 0, 8, 8, 8, helmet_paint)
    t.box(16, 16, 8, 12, 4, chest_paint)
    t.box(40, 16, 4, 12, 4, arm_paint)
    t.box(0, 16, 4, 12, 4, boots_paint)
    return t.rows()


def layer2():
    t = Layer()
    t.box(16, 16, 8, 12, 4, belt_paint)
    t.box(0, 16, 4, 12, 4, legs_paint)
    return t.rows()


# ------------------------------------------------------------------ main

def main():
    for mk, colors in ((1, MK1), (2, MK2)):
        p = pal(colors)
        for slot, fn in ICONS.items():
            write_item(f'exo_{slot}_mk{mk}', fn(), p)
        write_png(ASSETS / f'textures/models/armor/exo_mk{mk}_layer_1.png', layer1(), p, size=None)
        write_png(ASSETS / f'textures/models/armor/exo_mk{mk}_layer_2.png', layer2(), p, size=None)
    for name, fname in MODULE_FILES.items():
        write_item(fname, module(name, MODULE_COLORS[name]), pal(MODULE_COLORS[name]))


if __name__ == '__main__':
    main()
