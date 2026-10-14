"""Exo-Frame textures: module icons, the Glider wings and the Jet Assist pods.
Run: python3 scripts/textures/exo.py (the armor itself, icons and worn look: scripts/textures/exo_armor.py)

Mk1 = copper plating with brass trim and cyan lights, Mk2 = steel plating with cyan trim and lights, Mk3 = dark netherite
plating with magma trim and amber lights, Mk4 = null-violet plating with lilac trim and teal lights.
The Glider wings (Elytra model UVs) go to textures/entity/exo_glider.png.
Jet Assist thruster pods (a player render layer, 64x32): exo_jetpack_mk{1-4}.png in the mark's colors and
exo_jetpack_glow.png, the full bright pass drawn while the pack fires (hot nozzles, heat strips).
Module cards are coloured by the piece they fit (any-piece modules violet); leveled modules show one gold pip per level.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, PALETTE, Canvas, write_item, write_png  # noqa: E402

# Plating palettes: m mid, M light, n dark, T trim, t dark trim, a light (cyan), A bright light
MK1 = {'m': '#C87533', 'M': '#E8A060', 'n': '#8A4A22', 'T': '#D4A73A', 't': '#8F6D1C', 'a': '#5FE3F0', 'A': '#D8FBFF'}
MK2 = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#4D5558', 'T': '#2A8FA6', 't': '#14566A', 'a': '#5FE3F0', 'A': '#D8FBFF'}
MK3 = {'m': '#3B3533', 'M': '#5E5552', 'n': '#1F1B1A', 'T': '#FF7A2E', 't': '#C23A12', 'a': '#FFB21E', 'A': '#FFE08A'}
MK4 = {'m': '#2A1F45', 'M': '#4A3A75', 'n': '#140A2C', 'T': '#A884F0', 't': '#5A33A0', 'a': '#5AD6BE', 'A': '#E6DAFF'}
MARKS = {1: MK1, 2: MK2, 3: MK3, 4: MK4}


def pal(*dicts):
    out = dict(PALETTE)
    for d in dicts:
        out.update(d)
    return out


# ------------------------------------------------------------------ module icons (16x16 circuit cards)

# m card, M card light, n card dark / glyph window, A glyph, a dim glyph
GLYPH = {'A': '#D8FBFF', 'a': '#5FE3F0'}
HEAD = {'m': '#2A8FA6', 'M': '#7FE3F5', 'n': '#14566A', **GLYPH}
CHEST = {'m': '#C87533', 'M': '#E8A060', 'n': '#5A2E14', **GLYPH}
LEGS = {'m': '#D4A73A', 'M': '#F2D27A', 'n': '#5E4512', **GLYPH}
FEET = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#33393C', **GLYPH}
ANY = {'m': '#6A3FB0', 'M': '#A884F0', 'n': '#2A1560', **GLYPH}

# 6 wide, 8 tall glyphs; X = glyph, x = dim glyph
GLYPHS = {
    'night_vision': ['......', '.XXXX.', 'X.XX.X', 'X.XX.X', '.XXXX.', '......', '......', '......'],
    'rebreather': ['..X...', '.X.X..', '..X..X', '.....X.', 'X..X..', '.X.X..', '..X...', '......'],
    'robot_hud': ['XXXXXX', 'X....X', 'X.XX.X', 'X.X..X', 'X....X', 'XXXXXX', '..XX..', '.XXXX.'],
    'jet_assist': ['.X..X.', 'XXXXXX', 'XX..XX', 'XX..XX', 'XX..XX', '.X..X.', '.x..x.', 'x.xx.x'],
    'flight': ['X....X', 'XX..XX', 'XXXXXX', '.XXXX.', '..XX..', '..XX..', '......', '......'],
    'kinetic_shield': ['XXXXXX', 'XXXXXX', 'XXXXXX', 'XXXXXX', '.XXXX.', '.XXXX.', '..XX..', '..XX..'],
    'servo_stride_1': ['......', 'X.....', '.X....', '..X...', '.X....', 'X.....', '......', '......'],
    'servo_stride_2': ['......', 'X.X...', '.X.X..', '..X.X.', '.X.X..', 'X.X...', '......', '......'],
    'servo_stride_3': ['......', 'X.X.X.', '.X.X.X', '..X.X.', '.X.X.X', 'X.X.X.', '......', '......'],
    'step_assist': ['....XX', '....XX', '..XXXX', '..XXXX', 'XXXXXX', 'XXXXXX', '......', '......'],
    'spring_heels': ['.XXXX.', 'X....X', '.XXXX.', 'X....X', '.XXXX.', 'X....X', '.XXXX.', '......'],
    'fall_dampener': ['..XX..', '..XX..', '..XX..', 'XXXXXX', '.XXXX.', '..XX..', 'XXXXXX', 'xxxxxx'],
    'magnet': ['XX..XX', 'XX..XX', 'XX..XX', 'XX..XX', 'XXXXXX', '.XXXX.', '......', '......'],
    'auto_feeder': ['...X..', '..X...', '.XXXX.', 'XXXXXX', 'XXXXXX', 'XXXXXX', '.XXXX.', '......'],
    'solar_weave': ['..X...', 'X.X.X.', '.XXX..', 'XXXXX.', '.XXX..', 'X.X.X.', '..X...', '......'],
    'sonar_pulse': ['.xxxx.', 'x....x', 'x.XX.x', 'x.XX.x', 'x....x', '.xxxx.', '......', '......'],
    'med_injector': ['..XX..', '..XX..', 'XXXXXX', 'XXXXXX', '..XX..', '..XX..', '......', '......'],
    'hazard_seal': ['..X...', '.XXX..', '.XXX..', 'XXXXX.', 'XXXXX.', '.XXX..', '......', 'xxxxxx'],
    'kinetic_generator': ['...XX.', '..XX..', '.XX...', 'XXXXX.', '..XX..', '.XX...', '.X....', '......'],
    'dash_thrusters': ['......', 'x..X..', '...XX.', 'xXXXXX', '...XX.', 'x..X..', '......', '......'],
    'hydro_fins': ['......', 'X.....', 'XX....', 'XXX...', 'XXXX..', 'XXXXXX', '......', 'x.x.x.'],
    'capacitor_plating': ['..XX..', 'XXXXXX', 'X....X', 'XXXXXX', 'XXXXXX', 'XXXXXX', 'XXXXXX', '......'],
    'power_regulator': ['.XXXX.', 'X....X', 'X..X.X', 'X.X..X', 'X....X', '.XXXX.', '......', '......'],
    'glider': ['......', 'X....X', 'XX..XX', 'XXXXXX', 'xXXXXx', '..XX..', '......', '......'],
    'gyro_stabilizer': ['..XX..', '.X..X.', 'XxXXxX', 'XxXXxX', '.X..X.', '..XX..', '......', 'xxxxxx'],
    'anchor_servos': ['..XX..', '..XX..', 'XXXXXX', '..XX..', '..XX..', 'X.XX.X', '.XXXX.', '......'],
    'cryo_treads': ['..X...', 'X.X.X.', '.XXX..', 'XXXXX.', '.XXX..', 'X.X.X.', '..X...', 'xxxxxx'],
    'reach': ['......', '...X..', '....X.', 'xXXXXX', '....X.', '...X..', '......', 'x.x.x.'],
    'heat_shield': ['XXXXXX', 'X..x.X', 'X.xx.X', 'XxxxxX', 'XxxxxX', '.X..X.', '..XX..', '......'],
}


def module(name, colors, level=0):
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
    # level pips
    for i in range(level):
        c.set(5 + 2 * i, 11, 'Y')
    return c.rows()


MODULE_COLORS = {
    'night_vision': HEAD, 'rebreather': HEAD, 'robot_hud': HEAD, 'auto_feeder': HEAD, 'solar_weave': HEAD, 'sonar_pulse': HEAD,
    'jet_assist': CHEST, 'flight': CHEST, 'kinetic_shield': CHEST, 'med_injector': CHEST, 'hazard_seal': CHEST,
    'glider': CHEST,
    'servo_stride_1': LEGS, 'servo_stride_2': LEGS, 'servo_stride_3': LEGS, 'kinetic_generator': LEGS, 'dash_thrusters': LEGS,
    'gyro_stabilizer': LEGS, 'anchor_servos': LEGS,
    'step_assist': FEET, 'spring_heels': FEET, 'fall_dampener': FEET, 'hydro_fins': FEET, 'cryo_treads': FEET,
    'capacitor_plating': ANY, 'power_regulator': ANY, 'reach': ANY, 'heat_shield': ANY, 'magnet': ANY,
}
# Modules with levels: one file per level (level I keeps the plain name). Servo Stride has its own glyph per level.
LEVELS = {name: 3 for name in ('night_vision', 'sonar_pulse', 'jet_assist', 'kinetic_shield', 'med_injector', 'spring_heels',
                               'magnet', 'capacitor_plating', 'power_regulator', 'reach', 'anchor_servos')}
LEVELS['heat_shield'] = 2


def module_files():
    """(file name, glyph, level pips) for every module item."""
    out = []
    for name in MODULE_COLORS:
        if name.startswith('servo_stride_'):
            level = int(name[-1])
            out.append((f'servo_stride_module_{level}', name, level))
        elif name in LEVELS:
            for level in range(1, LEVELS[name] + 1):
                out.append((f'{name}_module' if level == 1 else f'{name}_module_{level}', name, level))
        else:
            out.append((f'{name}_module', name, 0))
    return out


# ------------------------------------------------------------------ 64x32 model textures (glider, jetpack)

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


# ------------------------------------------------------------------ glider wings (64x32, Elytra model UVs)

def glider_wings():
    """One wing box at u=22, v=0 (10 wide, 20 tall, 2 deep), used mirrored for both wings: a steel panel wing with
    dark ribs and cyan light seams, tapering to a tip at the bottom."""
    t = Layer()

    def paint(name, lx, ly, fw, fh):
        if name not in ('front', 'back'):
            return 'n'
        # taper: full width for the first 12 rows, then narrower toward the tip
        inset = max(0, ly - 11)
        if lx < inset // 2 or lx >= fw - (inset + 1) // 2:
            return '.'
        if lx == inset // 2 or lx == fw - 1 - (inset + 1) // 2 or ly == 0:
            return 'n'
        if ly == 1:
            return 'T'
        if ly in (6, 12):
            return 'a'
        if lx in (3, 6):
            return 't'
        return 'M' if lx < 3 else 'm'

    t.box(22, 0, 10, 20, 2, paint)
    return t.rows()


# ------------------------------------------------------------------ Jet Assist pods (64x32, see ExoJetpackLayer)

# Glow pass colors: Y white-hot core, o orange, r dull red heat, h heat strip.
JET_GLOW = {'Y': '#FFF4C0', 'o': '#FF9A2E', 'r': '#B8401A', 'h': '#FFB060'}


def jet_bracket(face, lx, ly, fw, fh, glow=False):
    if glow:
        return '.'
    if face in ('front', 'back') and lx in (0, fw - 1) and ly in (0, fh - 1):
        return 'k'
    if face == 'back' and ly == 1 and 1 < lx < fw - 2:
        return 'T'
    return plate(face, lx, ly, fw, fh)


def jet_pod(face, lx, ly, fw, fh, glow=False):
    strip = face == 'back' and lx == 1 and 2 <= ly <= 5
    if glow:
        if strip:
            return 'h'
        if face in ('left', 'right', 'back', 'front') and ly == fh - 1:
            return 'r'
        return '.'
    if face == 'top':
        return 'M'
    if face == 'bottom':
        return 'k'
    if strip:
        return 'a' if ly > 2 else 'A'
    if ly == 1:
        return 'T'
    if ly == fh - 2:
        return 't'
    if face in ('left', 'right') and lx == 1 and ly in (3, 5):
        return 'k'
    return plate(face, lx, ly, fw, fh)


def jet_cap(face, lx, ly, fw, fh, glow=False):
    if glow:
        return '.'
    return 'M' if face == 'top' else 'T'


def jet_throat(face, lx, ly, fw, fh, glow=False):
    if glow:
        return 'o' if face == 'bottom' or ly == fh - 1 else 'r'
    if face == 'bottom':
        return 'k'
    return 'k' if ly == fh - 1 else ('n' if lx == fw - 1 else 'm')


def jet_bell(face, lx, ly, fw, fh, glow=False):
    if face == 'bottom':
        if glow:
            return 'Y' if (lx, ly) == (1, 1) else 'o'
        return 'k' if (lx, ly) == (1, 1) else 'n'
    if glow:
        return 'r'
    if face == 'top':
        return 'n'
    return 't' if lx != 0 else 'T'


JET_BOXES = ((0, 0, 6, 4, 1, jet_bracket), (0, 6, 3, 8, 3, jet_pod), (16, 0, 2, 1, 2, jet_cap),
             (24, 0, 2, 2, 2, jet_throat), (16, 6, 3, 1, 3, jet_bell))


def jetpack(glow=False):
    t = Layer()
    for u, v, w, h, d, fn in JET_BOXES:
        t.box(u, v, w, h, d, lambda face, lx, ly, fw, fh, fn=fn: fn(face, lx, ly, fw, fh, glow))
    return t.rows()


# ------------------------------------------------------------------ main

def main():
    for mk, colors in MARKS.items():
        p = pal(colors)
        write_png(ASSETS / f'textures/models/armor/exo_jetpack_mk{mk}.png', jetpack(), p, size=None)
    write_png(ASSETS / 'textures/models/armor/exo_jetpack_glow.png', jetpack(glow=True), pal(JET_GLOW), size=None)
    for fname, name, level in module_files():
        write_item(fname, module(name, MODULE_COLORS[name], level), pal(MODULE_COLORS[name]))
    write_png(ASSETS / 'textures/entity/exo_glider.png', glider_wings(), pal(MK2), size=None)


if __name__ == '__main__':
    main()
