"""Exo-Frame worn armor: the extra model parts and the 64x64 armor textures with their glow pass.
Run: python3 scripts/textures/exo_armor.py

The suit is the vanilla armor model (same boxes and UVs in the top half of the texture) plus the parts in EXTRAS:
ear pods, a chest core, pauldrons, knee pads, toe caps and, per mark, crests, fins, heel thrusters, shin guards and
blades. ExoArmorModel (Java) builds the same boxes: keep the two in step.
Mk1 = copper plates and brass trim, Mk2 = white steel and cyan, Mk3 = black netherite plates and magma, Mk4 = null
violet and lilac. Every mark has energy conduits that glow (cyan, cyan, amber, teal).

Writes textures/models/armor/exo_mk{1-4}_layer_{1,2}.png (layer 1: helmet, chestplate, boots; layer 2: leggings) and
exo_mk{1-4}_layer_{1,2}_glow.png: the conduits only, drawn full bright on top by ExoArmorGlowLayer while the piece has
energy. In the base texture the conduits are dark glass, so an empty suit reads as powered down.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, Canvas, png_bytes, write_item  # noqa: E402

OUT = ASSETS / 'textures/models/armor'

# Palette per mark. 0-4 plate ramp (dark -> light), u U undersuit, t T Q trim (dark, mid, light), k seam,
# g dark conduit glass (under the glow), v visor glass; glow pass: e glow, E bright glow, W white-hot core.
MARKS = {
    1: {'0': '#3E1F0E', '1': '#83441E', '2': '#C27234', '3': '#E79E5C', '4': '#FFCB94',
        'u': '#26211F', 'U': '#3A3330', 't': '#806014', 'T': '#D9AE36', 'Q': '#FFE08A', 'k': '#161212',
        'g': '#0F3A44', 'v': '#0B1A20', 'e': '#2FB8CC', 'E': '#7FEEFA', 'W': '#E6FFFF'},
    2: {'0': '#4A5256', '1': '#959FA4', '2': '#C9D0D3', '3': '#E6EAEC', '4': '#FFFFFF',
        'u': '#1B2026', 'U': '#2B323A', 't': '#145A6E', 'T': '#2394B0', 'Q': '#6FD7EE', 'k': '#101418',
        'g': '#0F3A44', 'v': '#0A161C', 'e': '#2FB8CC', 'E': '#7FEEFA', 'W': '#E6FFFF'},
    3: {'0': '#0C0D0F', '1': '#1A1C1F', '2': '#2B2E33', '3': '#42464C', '4': '#62676D',
        'u': '#0A0A0B', 'U': '#141517', 't': '#7A2208', 'T': '#C9461A', 'Q': '#FF8A3C', 'k': '#060606',
        'g': '#3A1606', 'v': '#120804', 'e': '#E0601A', 'E': '#FFB040', 'W': '#FFF0B8'},
    4: {'0': '#0E0820', '1': '#21134A', '2': '#382374', '3': '#5838A8', '4': '#8C6FDA',
        'u': '#08050F', 'U': '#130C24', 't': '#4A2C8C', 'T': '#8A64E0', 'Q': '#C7B2FF', 'k': '#05030A',
        'g': '#0B3530', 'v': '#06040C', 'e': '#2EC4A4', 'E': '#7CF5D8', 'W': '#E4FFF6'},
}
GLOW = 'eEW'
# Grid chars that depend on the mark: '#' a glow dot from Mk2 on (a seam before), '@' white-hot on Mk3+ (bright
# before), '%' a glow dot on Mk4 only (plate before), '!' trim light on Mk3+ (trim before).
MARK_CHARS = {
    '#': lambda mk: 'e' if mk >= 2 else 'k',
    '@': lambda mk: 'W' if mk >= 3 else 'E',
    '%': lambda mk: 'e' if mk >= 4 else '2',
    '!': lambda mk: 'Q' if mk >= 3 else 'T',
}


class Face:
    """One face of a box being painted: texel x, y in a face of w x h. For the side faces fz is the texel's distance
    from the front edge (0 = front column); for top and bottom it is the distance from the front row."""

    def __init__(self, name, x, y, w, h):
        self.name, self.x, self.y, self.w, self.h = name, x, y, w, h
        if name == 'right':
            self.fz = w - 1 - x
        elif name == 'left':
            self.fz = x
        elif name in ('top', 'bottom'):
            self.fz = h - 1 - y
        else:
            self.fz = None

    @property
    def side(self):
        return self.name in ('left', 'right')


def grids(spec, fill=None):
    """Painter from pixel grids per face: 'front', 'back', 'top', 'bottom', and 'side' (both sides, drawn with the
    front on the right) or 'right'/'left' (outer and inner side of a right limb). A top grid has the front row last.
    Faces without a grid use fill (a char or None for transparent)."""
    def paint(f, mk):
        g = spec.get(f.name)
        x = f.x
        if g is None and f.side and 'side' in spec:
            g = spec['side']
            x = f.w - 1 - f.fz
        if g is None:
            return fill
        ch = g[f.y][x] if f.y < len(g) and x < len(g[f.y]) else fill
        if ch in MARK_CHARS:
            ch = MARK_CHARS[ch](mk)
        return None if ch == '.' else ch
    paint.spec = spec
    return paint


def by_mark(**painters):
    """Painter that picks a painter by mark: by_mark(m1=..., m3=...) falls back to the nearest lower mark."""
    def paint(f, mk):
        for m in range(mk, 0, -1):
            p = painters.get(f'm{m}')
            if p:
                return p(f, mk)
    return paint


class Skin:
    W, H = 64, 64

    def __init__(self):
        self.px = [['.'] * self.W for _ in range(self.H)]
        self.used = [[False] * self.W for _ in range(self.H)]

    def box(self, u, v, w, h, d, painter, mk):
        regions = (('top', u + d, v, w, d), ('bottom', u + d + w, v, w, d), ('right', u, v + d, d, h),
                   ('front', u + d, v + d, w, h), ('left', u + d + w, v + d, d, h), ('back', u + 2 * d + w, v + d, w, h))
        for name, x0, y0, fw, fh in regions:
            for y in range(fh):
                for x in range(fw):
                    X, Y = x0 + x, y0 + y
                    if not (0 <= X < self.W and 0 <= Y < self.H):
                        raise ValueError(f'box at {u},{v} leaves the texture')
                    if self.used[Y][X]:
                        raise ValueError(f'box at {u},{v} overlaps another box at {X},{Y}')
                    self.used[Y][X] = True
                    self.px[Y][X] = painter(Face(name, x, y, fw, fh), mk) or '.'

    def rows(self, glow):
        if glow:
            return [''.join(c if c in GLOW else '.' for c in r) for r in self.px]
        return [''.join('g' if c in GLOW else c for c in r) for r in self.px]


# ------------------------------------------------------------------ base pieces (vanilla boxes and UVs)

HELMET_FRONT1 = [
    '333QQ333',
    '222TT222',
    '1kkkkkk1',
    'kEEEEEEk',
    '2kkkkkk2',
    '22222222',
    '2k1kk1k2',
    '11111111']

HELMET = grids({
    'front': [
        '333QQ333',
        '222TT222',
        '1kkkkkk1',
        'kEEEEEEk',
        '2kk@@kk2',
        '21kEEk12',
        '21keek12',
        '11kkkk11'],
    'side': [
        '33333333',
        '22222222',
        '222221kk',
        '22222kEE',
        '222222k1',
        '22222212',
        '1k1k2212',
        '11111111'],
    'back': [
        '33333333',
        '222TT222',
        '222##222',
        '222##222',
        '222TT222',
        '1k1TT1k1',
        '22222222',
        '11111111'],
    'top': [
        '322TT223',
        '322TT223',
        '322TT223',
        '322!!223',
        '322!!223',
        '322TT223',
        '333TT333',
        '333QQ333'],
}, fill='u')
HELMET = by_mark(m1=grids({**HELMET.spec, 'front': HELMET_FRONT1}, fill='u'), m2=HELMET)

CHEST = grids({
    'front': [
        '33333333',
        '3ee22ee3',
        '21k22k12',
        '21k22k12',
        '21k22k12',
        '11kkkk11',
        'U22kk22U',
        'U11##11U',
        'U22kk22U',
        'TTTTTTTT',
        'ttTEETtt',
        'tttttttt'],
    'side': [
        '3333',
        '2222',
        '222#',
        '2222',
        '1111',
        'kkkk',
        '2222',
        '1111',
        '2222',
        'TTTT',
        'tttt',
        'tttt'],
    'back': [
        '33333333',
        '222kk222',
        '22k##k22',
        '222kk222',
        '22k##k22',
        '111kk111',
        'U22kk22U',
        'U11kk11U',
        'U22kk22U',
        'TTTTTTTT',
        'tttttttt',
        'tttttttt'],
    'top': [
        '22222222',
        '33333333',
        '33333333',
        '33333333'],
}, fill='u')

ARM = grids({
    'right': [  # outside, front on the right
        '2222',
        '2222',
        '2222',
        '2e22',
        '2e21',
        '2e21',
        'uuuu',
        'TTTT',
        '2222',
        '2#22',
        '1111',
        'tttt'],
    'front': [
        '3222',
        '3222',
        '3222',
        '3221',
        '3221',
        '3221',
        'uuuu',
        'TTTT',
        '3222',
        '3222',
        '1111',
        'tttt'],
    'left': [  # inside, facing the body
        '1111',
        '1111',
        '1111',
        '1111',
        '1111',
        '1111',
        'uuuu',
        'tttt',
        '1111',
        '1111',
        '1111',
        'tttt'],
    'back': [
        '2222',
        '2222',
        '2222',
        '2221',
        '2kk1',
        '2221',
        'uuuu',
        'TTTT',
        '2222',
        '2222',
        '1111',
        'tttt'],
    'top': ['2222'] * 4,
}, fill='u')

BOOT = grids({
    'front': ['....'] * 6 + ['TTTT', '3222', '3222', '3222', '1111', 'kkkk'],
    'right': ['....'] * 6 + ['TTTT', '2222', '2ee2', '2222', '1111', 'kkkk'],
    'left': ['....'] * 6 + ['tttt', '1111', '1111', '1111', '1111', 'kkkk'],
    'back': ['....'] * 6 + ['TTTT', '2222', '2222', '2222', '1111', 'kkkk'],
    'top': ['....'] * 4,
}, fill='k')

BELT = grids({
    'front': ['........'] * 8 + ['tttttttt', 'TTTEETTT', '21k22k12', '11111111'],
    'side': ['....'] * 8 + ['tttt', 'TTTT', '2222', '1111'],
    'back': ['........'] * 8 + ['tttttttt', 'TTTTTTTT', '22222222', '11111111'],
    'top': ['........'] * 4,
}, fill='u')

LEGS = grids({
    'right': [  # outside
        'TTTT',
        '2e22',
        '2e22',
        '2e21',
        '2221',
        'uuuu',
        'U22U',
        'U22U',
        'U22U',
        'U11U',
        'U11U',
        'U11U'],
    'front': [
        'TTTT',
        '3222',
        '3222',
        '3221',
        '1111',
        'uuuu',
        '2222',
        '2222',
        '2222',
        '1111',
        '1111',
        '1111'],
    'left': [
        'tttt',
        '1111',
        '1111',
        '1111',
        '1111',
        'uuuu',
        'UUUU',
        'UUUU',
        'UUUU',
        'UUUU',
        'UUUU',
        'UUUU'],
    'back': [
        'TTTT',
        '2222',
        '2222',
        '2222',
        '1111',
        'uuuu',
        '2222',
        '2222',
        '1111',
        '1111',
        '1111',
        '1111'],
    'top': ['1111'] * 4,
}, fill='u')


# ------------------------------------------------------------------ extra parts

POD = grids({
    'right': ['kTTk', 'TEET', 'T@ET', 'kttk'],
    'front': ['T', 'T', 'T', 't'],
    'back': ['T', 'T', 'T', 't'],
    'top': ['Q'] * 4,
}, fill='t')

CORE = grids({
    'front': ['tTTt', 'TW@T', 'TEET', 'tTTt'],
    'side': ['T', 'T', 'T', 't'],
    'top': ['QQQQ'],
}, fill='t')

CREST = grids({'top': ['Q'] * 7, 'side': ['TTTTTTT', 'teeeeet'], 'front': ['T', 't'], 'back': ['T', 't']}, fill='t')
RIDGE = grids({'top': ['Q'] * 6, 'side': ['TTTTTT'], 'front': ['T'], 'back': ['t']}, fill='t')
FIN = grids({'top': ['Q'] * 5, 'side': ['kekek'], 'front': ['T'], 'back': ['t']}, fill='t')
ANTENNA = grids({'top': ['W'], 'side': ['E', 'T', 'T', 't'], 'front': ['E', 'T', 'T', 't'], 'back': ['E', 'T', 'T', 't']},
                fill='t')

# Pauldron on the right arm: x runs from the outer edge (0) in, the top grid has the front row last.
PAULDRON3 = grids({
    'top': ['QT3332', 'QT3322', 'QT3322', 'QT3322', 'QT3322', 'QT3322', 'QT3332'],
    'right': ['3333333', '22eee22', 'TTTTTTT'],
    'front': ['Q33331', 'T22221', 'TTTTTt'],
    'back': ['T1111t', 'T1111t', 'tttttt'],
    'left': ['1111111', '1111111', 'ttttttt'],
}, fill='u')
PAULDRON4 = grids({
    'top': ['QT3332', 'QT3322', 'QT3322', 'QT3322', 'QT3322', 'QT3322', 'QT3332'],
    'right': ['3333333', '2222222', '2keeek2', 'TTTTTTT'],
    'front': ['Q33331', 'T22221', 'T22221', 'TTTTTt'],
    'back': ['T1111t', 'T1111t', 'T1111t', 'tttttt'],
    'left': ['1111111', '1111111', '1111111', 'ttttttt'],
}, fill='u')
PAULDRON = by_mark(m1=PAULDRON3, m2=PAULDRON4)
HEATSINK = grids({'top': ['Q'] * 5, 'side': ['ekeke'], 'front': ['T'], 'back': ['t']}, fill='t')
BLADE = grids({
    'top': ['Q'] * 6,
    'side': ['TTTTTT', '2eeee2', '211112'],
    'front': ['T', '2', '1'],
    'back': ['T', '1', 't'],
}, fill='t')

KNEE = grids({'front': ['QTTQ', 'T%%T', 'tttt'], 'top': ['QQQQ'], 'side': ['T', 'T', 't']}, fill='t')
TOE = grids({'front': ['33333', '2#2#2', 'kkkkk'], 'top': ['33333'], 'side': ['3', '1', 'k']}, fill='k')
HEEL = grids({'back': ['tkt', 'kEk', 'tkt'], 'top': ['TTT'], 'side': ['T', 't', 't']}, fill='t')
SHIN = grids({'front': ['Q!!Q', 'T22T', 'T22T', 'teet'], 'top': ['QQQQ'], 'side': ['T', 'T', 'T', 't']}, fill='t')
THIGH = grids({'right': ['TTTT', '2EE2', '2ee2', 'tttt'], 'top': ['QQQQ'], 'front': ['T', '2', '2', 't'],
               'back': ['T', '2', '2', 't']}, fill='t')


# ------------------------------------------------------------------ the model

# (bone, u, v, x, y, z, w, h, d, marks, painter, pair). Coordinates are in the bone's space like ModelPart boxes.
# right_arm and right_leg parts are mirrored onto the left limb; head parts with pair=True get a mirrored twin.
# 'outer' parts go on the layer 1 model (helmet, chestplate, boots), 'inner' parts on layer 2 (leggings).
ALL = (1, 2, 3, 4)
EXTRAS = {
    'outer': [
        # helmet
        ('head', 32, 0, -6.0, -6.0, -1.0, 1, 4, 4, ALL, POD, True),
        ('head', 32, 8, -0.5, -10.0, -3.0, 1, 1, 6, (2,), RIDGE, False),
        ('head', 46, 0, -3.5, -10.0, -2.0, 1, 1, 5, (3,), FIN, True),
        ('head', 46, 0, -0.5, -11.0, -4.0, 1, 2, 7, (4,), CREST, False),
        ('head', 50, 48, -6.0, -10.0, 1.0, 1, 4, 1, (4,), ANTENNA, False),
        # chestplate
        ('body', 40, 48, -2.0, 1.0, -4.0, 4, 4, 1, ALL, CORE, False),
        ('right_arm', 0, 32, -5.0, -4.0, -3.5, 6, 3, 7, (1,), PAULDRON, False),
        ('right_arm', 0, 32, -5.0, -4.0, -3.5, 6, 4, 7, (2, 3, 4), PAULDRON, False),
        ('right_arm', 28, 32, -4.0, -5.0, -2.5, 1, 1, 5, (3,), HEATSINK, False),
        ('right_arm', 28, 32, -2.0, -5.0, -2.5, 1, 1, 5, (3,), HEATSINK, False),
        ('right_arm', 28, 32, -5.5, -6.0, -3.0, 1, 3, 6, (4,), BLADE, False),
        # boots
        ('right_leg', 28, 44, -2.5, 10.0, -4.0, 5, 3, 1, ALL, TOE, False),
        ('right_leg', 40, 44, -1.5, 9.0, 3.0, 3, 3, 1, (2, 3, 4), HEEL, False),
        ('right_leg', 28, 48, -2.0, 5.0, -4.0, 4, 4, 1, (3, 4), SHIN, False),
    ],
    'inner': [
        ('right_leg', 0, 32, -2.0, 3.0, -3.5, 4, 3, 1, ALL, KNEE, False),
        ('right_leg', 12, 32, -3.5, 0.5, -2.0, 1, 4, 4, (4,), THIGH, False),
    ],
}


def extras(layer, mk):
    """(bone, u, v, x, y, z, w, h, d, mirror) of the extra parts on one layer for a mark, left side included."""
    out = []
    for bone, u, v, x, y, z, w, h, d, marks, _, pair in EXTRAS[layer]:
        if mk not in marks:
            continue
        out.append((bone, u, v, x, y, z, w, h, d, False))
        if bone in ('right_arm', 'right_leg'):
            out.append((bone.replace('right', 'left'), u, v, -(x + w), y, z, w, h, d, True))
        elif pair:
            out.append((bone, u, v, -(x + w), y, z, w, h, d, True))
    return out


def armor_model(mk, inner):
    """Boxes per bone, (u, v, x, y, z, w, h, d, grow, mirror), for previews."""
    g = 0.5 if inner else 1.0
    bones = {
        'head': [(0, 0, -4, -8, -4, 8, 8, 8, g, False)],
        'body': [(16, 16, -4, 0, -2, 8, 12, 4, g, False)],
        'right_arm': [(40, 16, -3, -2, -2, 4, 12, 4, g, False)],
        'left_arm': [(40, 16, -1, -2, -2, 4, 12, 4, g, True)],
        'right_leg': [(0, 16, -2, 0, -2, 4, 12, 4, g, False)],
        'left_leg': [(0, 16, -2, 0, -2, 4, 12, 4, g, True)],
    }
    for bone, u, v, x, y, z, w, h, d, mirror in extras('inner' if inner else 'outer', mk):
        bones[bone].append((u, v, x, y, z, w, h, d, 0.0, mirror))
    return bones


def layer(mk, inner):
    s = Skin()
    if inner:
        s.box(16, 16, 8, 12, 4, BELT, mk)
        s.box(0, 16, 4, 12, 4, LEGS, mk)
    else:
        s.box(0, 0, 8, 8, 8, HELMET, mk)
        s.box(16, 16, 8, 12, 4, CHEST, mk)
        s.box(40, 16, 4, 12, 4, ARM, mk)
        s.box(0, 16, 4, 12, 4, BOOT, mk)
    seen = set()
    for bone, u, v, x, y, z, w, h, d, marks, painter, _ in EXTRAS['inner' if inner else 'outer']:
        if mk in marks and (u, v) not in seen:
            seen.add((u, v))
            s.box(u, v, w, h, d, painter, mk)
    return s


# ------------------------------------------------------------------ item icons (16x16)

def icon_helmet(mk):
    c = Canvas()
    c.rect(4, 1, 8, 1, '3').rect(3, 2, 10, 3, '2').rect(2, 4, 12, 9, '2')
    c.rect(3, 2, 1, 3, '3').rect(2, 4, 1, 9, '3').rect(13, 4, 1, 9, '1').rect(12, 2, 1, 3, '1')
    c.rect(7, 1, 2, 4, 'T').rect(7, 1, 2, 1, 'Q')
    c.rect(2, 6, 12, 1, 'k').rect(3, 7, 10, 1, 'E').rect(2, 8, 12, 1, 'k')
    if mk >= 2:
        c.rect(7, 8, 2, 3, 'e').set(7, 8, 'E').set(8, 8, 'E').rect(6, 8, 1, 4, 'k').rect(9, 8, 1, 4, 'k')
    else:
        c.rect(4, 10, 8, 1, 'k').set(5, 10, '1').set(7, 10, '1').set(9, 10, '1')
    c.rect(6, 12, 4, 1, 'k').rect(2, 12, 4, 1, '1').rect(10, 12, 4, 1, '1')
    c.rect(1, 6, 1, 4, 'T').rect(14, 6, 1, 4, 'T').set(1, 7, 'E').set(14, 7, 'E')
    c.rect(6, 13, 4, 2, '.')
    if mk == 4:
        c.rect(1, 2, 1, 4, 'T').set(1, 1, 'W')
    if mk == 3:
        c.set(5, 1, 'Q').set(10, 1, 'Q')
    c.outline('k')
    return c.rows()


def icon_chestplate(mk):
    c = Canvas()
    c.rect(3, 3, 10, 11, '2').rect(3, 3, 1, 11, '3').rect(12, 3, 1, 11, '1')
    c.rect(6, 2, 4, 2, '.').rect(5, 3, 6, 1, 'k')
    # pauldrons
    for x0 in (0, 11):
        c.rect(x0, 2, 5, 4, '3' if x0 == 0 else '2').rect(x0, 5, 5, 1, 'T').rect(x0, 2, 5, 1, 'Q' if x0 == 0 else '3')
        c.rect(x0 + 1, 6, 3, 3, '2' if x0 == 0 else '1')
        c.rect(x0 + 1, 4, 3, 1, 'e' if mk >= 2 else 'k')
    # core and conduits
    c.rect(6, 5, 4, 4, 'T').rect(7, 6, 2, 2, 'E').set(7, 6, 'W').set(6, 5, 't').set(9, 5, 't').set(6, 8, 't').set(9, 8, 't')
    c.rect(4, 5, 1, 4, 'e').rect(11, 5, 1, 4, 'e')
    c.rect(4, 9, 8, 1, 'k').rect(7, 10, 2, 2, 'e' if mk >= 2 else 'k')
    c.rect(3, 12, 10, 1, 'T').rect(3, 13, 10, 1, 't').rect(7, 12, 2, 1, 'E')
    c.outline('k')
    return c.rows()


def icon_leggings(mk):
    c = Canvas()
    c.rect(3, 1, 10, 4, '2').rect(3, 5, 4, 9, '2').rect(9, 5, 4, 9, '2')
    c.rect(3, 1, 10, 1, 'T').rect(3, 2, 10, 1, 't').rect(7, 1, 2, 1, 'E')
    c.rect(3, 3, 1, 11, '3').rect(9, 5, 1, 9, '3').rect(6, 5, 1, 9, '1').rect(12, 3, 1, 11, '1')
    c.rect(7, 4, 2, 2, '.')
    c.rect(3, 13, 4, 1, '1').rect(9, 13, 4, 1, '1')
    # conduits down the outer thighs, knee pads
    c.rect(4, 4, 1, 4, 'e').rect(11, 4, 1, 4, 'e')
    for x0 in (3, 9):
        c.rect(x0, 9, 4, 2, 'T').rect(x0, 9, 4, 1, 'Q').rect(x0 + 1, 10, 2, 1, 'e' if mk >= 4 else 't')
    c.outline('k')
    return c.rows()


def icon_boots(mk):
    c = Canvas()
    for x0 in (1, 9):
        c.rect(x0 + 1, 3, 4, 7, '2').rect(x0, 9, 6, 4, '2')
        c.rect(x0 + 1, 3, 4, 1, 'T').rect(x0 + 1, 4, 1, 5, '3').rect(x0 + 4, 4, 1, 5, '1')
        c.rect(x0, 9, 6, 1, '3').rect(x0, 12, 6, 1, 'k').rect(x0 + 5, 10, 1, 2, '1')
        c.rect(x0 + 2, 6, 2, 1, 'e')
        if mk >= 3:
            c.rect(x0 + 2, 5, 2, 3, 'Q').rect(x0 + 2, 7, 2, 1, 'e')
        c.set(x0 + 1, 11, 'e' if mk >= 2 else '1').set(x0 + 3, 11, 'e' if mk >= 2 else '1')
    c.outline('k')
    return c.rows()


ICONS = {'helmet': icon_helmet, 'chestplate': icon_chestplate, 'leggings': icon_leggings, 'boots': icon_boots}


def write(path, rows, palette):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png_bytes(rows, palette))


def main(out=OUT):
    out = pathlib.Path(out)
    for mk, colors in MARKS.items():
        if out == OUT:
            for slot, fn in ICONS.items():
                write_item(f'exo_{slot}_mk{mk}', fn(mk), colors)
        for n, inner in ((1, False), (2, True)):
            s = layer(mk, inner)
            write(out / f'exo_mk{mk}_layer_{n}.png', s.rows(False), colors)
            write(out / f'exo_mk{mk}_layer_{n}_glow.png', s.rows(True), colors)


if __name__ == '__main__':
    main(*sys.argv[1:])
