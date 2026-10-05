"""Drones module textures: Mining Drone (copper body, steel drill) and Sentry Drone (steel orb, cyan eye), tier 1 and tier 2.
Run: python3 scripts/textures/drones.py
Writes textures/entity/{mining,sentry}_drone[_mk2].png (64x64 model sheets), the matching *_glow.png (only the lamp and eye
pixels, drawn full bright by a render layer) and the four item icons (models are written by scripts/data/drones_data.py).
The model sheets follow the box layout of the models in drones/client: texOffs(u, v), box w x h x d unfolds as
    top (u+d, v)  bottom (u+d+w, v)  west (u, v+d)  north/front (u+d, v+d)  east (u+d+w, v+d)  south (u+2d+w, v+d)
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, MATERIALS, Canvas, write_item, write_png  # noqa: E402

ENTITY = ASSETS / 'textures/entity'

# Tier palettes. 1-4: main ramp light to dark, a-c: trim ramp, k: near black, y/Y/z: cyan glow, l/L: lamp
MK1 = {
    '1': '#E8A060', '2': '#C87533', '3': '#A35E26', '4': '#7A431B',      # copper body
    'a': '#C4CBCE', 'b': '#8D9599', 'c': '#4D5558', 'k': '#1E1A1A',      # steel trim
    'r': '#D4A73A', 'R': '#F2D27A',                                      # brass rivets
    'y': '#2FB8CC', 'Y': '#5FE3F0', 'z': '#D8FBFF',                      # cyan
    'l': '#E8C15A', 'L': '#FFF3B0',
}
MK2 = dict(MK1)
MK2.update({
    '1': '#7E8A90', '2': '#566067', '3': '#3F484E', '4': '#2A3034',      # dark steel body
    'r': '#F0B030', 'R': '#FFE08A',                                      # gold trim
})

SENTRY1 = {
    '1': '#D8DEE0', '2': '#A9B3B7', '3': '#7C878C', '4': '#4D5558',      # steel shell
    'a': '#E8A060', 'b': '#C87533', 'c': '#8A4A22', 'k': '#1E1A1A',      # copper trim
    'r': '#D4A73A', 'R': '#F2D27A',
    'y': '#2FB8CC', 'Y': '#5FE3F0', 'z': '#D8FBFF',
    'l': '#E8C15A', 'L': '#FFF3B0',
}
SENTRY2 = dict(SENTRY1)
SENTRY2.update({
    '1': '#5A6870', '2': '#3F4A51', '3': '#2C353A', '4': '#1B2124',      # dark steel shell
    'a': '#F6C45A', 'b': '#E0A02E', 'c': '#8A5E14',                      # gold trim
})


def rects(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'east': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def box(c, u, v, w, h, d, ramp=('1', '2', '3', '4'), outline=None):
    """Fills the unfolded faces of a box: light top, mid sides, dark bottom, 1 px bevel on every face."""
    light, mid, dark, deep = ramp
    for name, (x, y, rw, rh) in rects(u, v, w, h, d).items():
        base = light if name == 'top' else deep if name == 'bottom' else mid
        c.rect(x, y, rw, rh, base)
        if rw >= 5 and rh >= 4 and name != 'bottom':           # brushed grain on the larger faces
            c.brushed(x + 1, y + 1, rw - 2, rh - 2, (dark, dark, base, light), seed=x * 7 + y, density=0.12)
        if rw >= 3 and rh >= 3:
            if name == 'top':
                c.bevel(x, y, rw, rh, light, mid)
            elif name == 'bottom':
                c.bevel(x, y, rw, rh, dark, deep)
            else:
                c.bevel(x, y, rw, rh, light, dark)
        elif rh >= 2:
            c.rect(x, y, rw, 1, light)
            c.rect(x, y + rh - 1, rw, 1, dark)
    return rects(u, v, w, h, d)


def put(c, face, dx, dy, ch):
    x, y, rw, rh = face
    if 0 <= dx < rw and 0 <= dy < rh:
        c.set(x + dx, y + dy, ch)


def block(c, face, dx, dy, w, h, ch):
    for yy in range(h):
        for xx in range(w):
            put(c, face, dx + xx, dy + yy, ch)


# ---------------------------------------------------------------- Mining Drone

def mining_sheet(tier):
    """Returns (main, glow) canvases of the Mining Drone sheet."""
    c = Canvas(64)
    g = Canvas(64)
    # body 8x6x8 at (0,0): copper (tier 1) or dark steel (tier 2)
    f = box(c, 0, 0, 8, 6, 8)
    # front: grille with cyan status strip
    block(c, f['north'], 1, 1, 6, 4, '4')
    for i in range(3):
        block(c, f['north'], 1, 1 + i + (i // 2), 6, 1, 'c')
    block(c, f['north'], 1, 4, 6, 1, 'y')
    # sides: bolted panel and battery window
    for side in ('west', 'east'):
        block(c, f[side], 2, 1, 4, 4, '3')
        block(c, f[side], 3, 2, 2, 2, 'y')
        put(c, f[side], 3, 2, 'z')
        put(c, f[side], 1, 1, 'r')
        put(c, f[side], 6, 4, 'r')
    # back: vent
    for i in range(4):
        block(c, f['south'], 1, 1 + i, 6, 1, 'c' if i % 2 == 0 else '4')
    # top and bottom studs
    for dx, dy in ((1, 1), (6, 1), (1, 6), (6, 6)):
        put(c, f['top'], dx, dy, 'r')
    # top cap 6x2x6 at (0,16): steel with a cyan dot
    f = box(c, 0, 16, 6, 2, 6, ('a', 'b', 'c', 'k'))
    block(c, f['top'], 2, 2, 2, 2, 'y')
    put(c, f['top'], 2, 2, 'z')
    # belly battery pack 6x2x6 at (0,26)
    f = box(c, 0, 26, 6, 2, 6, ('b', 'c', 'c', 'k'))
    block(c, f['bottom'], 1, 1, 4, 4, 'y')
    # headlamp 3x2x2 at (32,0): brass housing, glowing lens on the front
    f = box(c, 32, 0, 3, 2, 2, ('R', 'r', 'r', 'c'))
    block(c, f['north'], 0, 0, 3, 2, 'L')
    block(g, f['north'], 0, 0, 3, 2, 'L')
    # drill collar 5x5x2 at (32,8), bit 4x4x3 at (32,16), tip 2x2x3 at (32,24): steel with spiral stripes
    f = box(c, 32, 8, 5, 5, 2, ('a', 'b', 'c', 'k'))
    block(c, f['north'], 1, 1, 3, 3, 'c')
    f = box(c, 32, 16, 4, 4, 3, ('a', 'b', 'c', 'k'))
    for name in ('west', 'east', 'top', 'bottom'):
        x, y, rw, rh = f[name]
        for yy in range(rh):
            for xx in range(rw):
                if (xx + yy) % 3 == 0:
                    c.set(x + xx, y + yy, 'a')
                elif (xx + yy) % 3 == 1:
                    c.set(x + xx, y + yy, 'c')
    f = box(c, 32, 24, 2, 2, 3, ('a', 'b', 'c', 'k'))
    block(c, f['north'], 0, 0, 2, 2, 'k')
    # side pods 4x4x4 at (0,36): steel with a copper ring
    f = box(c, 0, 36, 4, 4, 4, ('a', 'b', 'c', 'k'))
    for side in ('west', 'east', 'north', 'south'):
        block(c, f[side], 0, 1, 4, 2, '2')
        block(c, f[side], 0, 1, 4, 1, '1')
    # rotor discs 6x1x6 at (16,36): a plus shaped blade, the rest of the faces stays transparent
    f = box(c, 16, 36, 6, 1, 6, ('a', 'b', 'c', 'k'))
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, rh, '.')
        c.rect(x, y + 2, rw, 2, 'b')
        c.rect(x + 2, y, 2, rh, 'b')
        c.rect(x + 2, y + 2, 2, 2, 'c')
        c.rect(x, y + 2, rw, 1, 'a')
        c.rect(x + 2, y, 1, rh, 'a')
    # tail fin 1x4x3 at (46,0)
    box(c, 46, 0, 1, 4, 3, ('a', 'b', 'c', 'k'))
    return c, g


# ---------------------------------------------------------------- Sentry Drone

def sentry_sheet(tier):
    c = Canvas(64)
    g = Canvas(64)
    # shell: three crossing boxes make a chamfered cube, all steel
    f = box(c, 0, 0, 8, 6, 6)          # wide
    block(c, f['top'], 1, 1, 6, 4, '2')
    for name in ('west', 'east'):
        put(c, f[name], 2, 2, 'a')
        put(c, f[name], 3, 3, 'b')
    f = box(c, 0, 12, 6, 8, 6)         # tall
    for name in ('west', 'east', 'north', 'south'):
        block(c, f[name], 2, 3, 2, 2, '3')
        put(c, f[name], 2, 3, 'a')
    f = box(c, 28, 0, 6, 6, 8)         # long, carries the eye
    block(c, f['west'], 3, 1, 3, 4, '3')
    block(c, f['east'], 2, 1, 3, 4, '3')
    block(c, f['west'], 4, 2, 1, 1, 'y')
    block(c, f['east'], 3, 2, 1, 1, 'y')
    # trim rivets on the long box front
    put(c, f['north'], 0, 0, 'R')
    put(c, f['north'], 5, 0, 'R')
    put(c, f['north'], 0, 5, 'R')
    put(c, f['north'], 5, 5, 'R')
    # eye visor 5x3x1 at (0,28): dark frame, cyan lens
    f = box(c, 0, 28, 5, 3, 1, ('4', 'c', 'c', 'k'))
    block(c, f['north'], 0, 0, 5, 3, 'k')
    block(c, f['north'], 1, 1, 3, 1, 'Y')
    put(c, f['north'], 2, 1, 'z')
    block(c, f['north'], 1, 1, 3, 1, 'Y')
    put(c, f['north'], 2, 1, 'z')
    block(g, f['north'], 1, 1, 3, 1, 'Y')
    put(g, f['north'], 2, 1, 'z')
    block(g, f['north'], 0, 1, 1, 1, 'y')
    block(g, f['north'], 4, 1, 1, 1, 'y')
    block(c, f['north'], 0, 1, 1, 1, 'y')
    block(c, f['north'], 4, 1, 1, 1, 'y')
    # barrel 2x2x6 at (16,28), muzzle ring 3x3x2 at (34,28)
    f = box(c, 16, 28, 2, 2, 6, ('a', 'b', 'c', 'k'))
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        for yy in range(0, rh, 2):
            c.rect(x, y + yy, rw, 1, 'c')
    f = box(c, 34, 28, 3, 3, 2, ('R', 'r', 'r', 'k'))
    block(c, f['north'], 0, 0, 3, 3, 'k')
    block(c, f['north'], 1, 1, 1, 1, 'z')
    block(g, f['north'], 1, 1, 1, 1, 'z')
    block(g, f['north'], 0, 1, 1, 1, 'y')
    block(g, f['north'], 2, 1, 1, 1, 'y')
    block(g, f['north'], 1, 0, 1, 1, 'y')
    block(g, f['north'], 1, 2, 1, 1, 'y')
    block(c, f['north'], 0, 1, 1, 1, 'y')
    block(c, f['north'], 2, 1, 1, 1, 'y')
    block(c, f['north'], 1, 0, 1, 1, 'y')
    block(c, f['north'], 1, 2, 1, 1, 'y')
    # rotor mast 1x2x1 at (44,14)
    box(c, 44, 14, 1, 2, 1, ('a', 'b', 'c', 'k'))
    # rotor blade 12x1x2 at (0,36): shared by both blades
    f = box(c, 0, 36, 12, 1, 2, ('a', 'b', 'c', 'k'))
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, rh, 'b')
        c.rect(x, y, rw, 1, 'a')
        block(c, f[name], 0, 0, 1, rh, 'k')
        block(c, f[name], rw - 1, 0, 1, rh, 'k')
    # thruster pods 2x2x3 at (0,40)
    f = box(c, 0, 40, 2, 2, 3, ('a', 'b', 'c', 'k'))
    block(c, f['south'], 0, 0, 2, 2, 'y')
    block(g, f['south'], 0, 0, 2, 2, 'y')
    return c, g


# ---------------------------------------------------------------- item icons

def icon(draw):
    c = Canvas(16)
    draw(c)
    c.outline('k')
    return c


def mining_icon(pal, body, light, dark):
    """Front three-quarter view: twin rotor pods, a stubby body with a lamp and window, the drill pointing down."""
    def draw(c):
        for x0 in (0, 10):                                   # rotor blur and pods
            c.rect(x0, 2, 6, 1, 'b').rect(x0 + 1, 2, 2, 1, 'a')
            c.set(x0 + 2, 3, 'c').set(x0 + 3, 3, 'c')
            c.rect(x0 + 1, 4, 4, 3, 'b')
        c.rect(4, 3, 8, 7, body)                             # body
        c.auto_shade({body: (dark, light), 'b': ('c', 'a')})
        c.rect(6, 4, 4, 1, 'r').set(7, 4, 'L').set(8, 4, 'l')    # headlamp
        c.rect(6, 6, 4, 2, 'k').rect(7, 6, 2, 1, 'y').set(7, 6, 'z')   # window
        c.set(5, 4, 'R')
        c.rect(5, 10, 6, 1, 'c').rect(6, 10, 4, 1, 'b')      # drill collar
        c.draw(6, 11, ['abca', '.ab.', '.ca.', '.a..'])       # spiral bit
    return draw


# Item icons of the sentry and courier drones and the courier remote share the mod wide item look (see core.py): outline 'k',
# light from the top-left, ramps from pixelart.MATERIALS. Chars: '12345' shell (deep -> highlight), 'abcde' trim,
# 'y Y z Z' cyan glow (mid, bright, white, deep), 'K' near black glass, 'o O' brass, 'A' amber lens.
DSTEEL = ('#2A3034', '#3F484E', '#566067', '#7E8A90', '#B5C0C5')    # Mk2 steel, same family as the Mk2 entity shells


def ipal(shell, trim):
    p = {'k': '#1E1A1A', 'K': '#0E0C0C'}
    p.update(zip('12345', MATERIALS[shell] if isinstance(shell, str) else shell))
    p.update(zip('abcde', MATERIALS[trim] if isinstance(trim, str) else trim))
    g = MATERIALS['cyan']
    p.update({'y': g[2], 'Y': g[3], 'z': g[4], 'Z': g[1]})
    p.update({'o': MATERIALS['brass'][2], 'O': MATERIALS['brass'][3], 'A': '#FFB21E', 'B': '#FFE08A'})
    return p


SENTRY_ICON1 = ipal('white', 'copper')
SENTRY_ICON2 = ipal(DSTEEL, 'steel')
COURIER_ICON1 = ipal('white', 'brass')
COURIER_ICON2 = ipal(DSTEEL, 'brass')


def shaded_ellipse(c, cx, cy, rx, ry, chars):
    """Lit ball, light from the top-left; `chars` run dark -> light."""
    for y in range(c.size):
        for x in range(c.size):
            nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = nx * nx + ny * ny
            if d <= 1.0:
                lum = -0.5 * nx - 0.6 * ny + 0.62 * (1 - d) ** 0.5
                i = 0 if lum < -0.05 else 1 if lum < 0.3 else 2 if lum < 0.62 else 3 if lum < 0.9 else 4
                c.set(x, y, chars[i])


def sentry_icon(mk2):
    """Floating orb: rotor on top, visor with a cyan eye, a trim band round the belly and a cannon on the right."""
    def draw(c):
        c.rect(3, 2, 10, 1, 'c').rect(3, 2, 4, 1, 'd').set(3, 2, 'e')         # rotor blade
        c.rect(7, 3, 2, 1, 'b').set(7, 3, 'c')                                # mast
        shaded_ellipse(c, 7.0, 9.0, 5.0, 5.0, '12345')
        for x in range(2, 12):                                                # belly band
            if c.get(x, 11) != '.':
                c.set(x, 11, 'y' if mk2 else 'c')
        c.rect(3, 6, 7, 4, 'K').rect(3, 6, 7, 1, '1').set(3, 6, '2')          # visor with a lit brow
        c.rect(4, 7, 5, 2, 'k').rect(4, 8, 5, 1, 'y').rect(5, 8, 3, 1, 'Y').set(5, 8, 'z')   # eye
        c.rect(11, 8, 4, 3, 'c').rect(11, 8, 4, 1, 'd').rect(11, 10, 4, 1, 'b')   # cannon barrel
        c.rect(14, 8, 1, 3, 'y' if mk2 else 'o').set(14, 8, 'z' if mk2 else 'O')  # muzzle
    return draw


COURIER1 = {
    '1': '#B7C6CE', '2': '#8FA4AF', '3': '#677B86', '4': '#44555E',      # blue steel body
    'a': '#F2D27A', 'b': '#D4A73A', 'c': '#8A6A1E', 'k': '#1E1A1A',      # brass trim
    'r': '#D4A73A', 'R': '#F2D27A',
    'y': '#FFB21E', 'Y': '#FFD76A', 'z': '#FFF3C4',                      # amber sensor
}
COURIER2 = dict(COURIER1)
COURIER2.update({
    '1': '#6B7B84', '2': '#4B5A62', '3': '#36424A', '4': '#232C31',
    'a': '#FFE08A', 'b': '#F0B030', 'c': '#9A6A10',
})


def courier_sheet(tier):
    c = Canvas(64)
    g = Canvas(64)
    # body 8x4x8 at (0,0)
    f = box(c, 0, 0, 8, 4, 8)
    for name in ('west', 'east'):
        block(c, f[name], 2, 1, 4, 2, '3')
        put(c, f[name], 3, 1, 'a')
    block(c, f['north'], 1, 1, 6, 2, '3')
    for dx, dy in ((1, 1), (6, 1), (1, 6), (6, 6)):
        put(c, f['top'], dx, dy, 'R')
    # cargo bay 6x3x6 at (0,14): amber stripes, a hatch on the bottom
    f = box(c, 0, 14, 6, 3, 6, ('b', 'c', 'c', 'k'))
    for name in ('west', 'east', 'north', 'south'):
        block(c, f[name], 0, 1, 6, 1, 'a')
    block(c, f['bottom'], 1, 1, 4, 4, 'k')
    block(c, f['bottom'], 2, 2, 2, 2, '4')
    # sensor 3x2x1 at (32,0): amber lens
    f = box(c, 32, 0, 3, 2, 1, ('4', 'c', 'c', 'k'))
    block(c, f['north'], 0, 0, 3, 2, 'k')
    block(c, f['north'], 0, 0, 3, 1, 'Y')
    put(c, f['north'], 1, 0, 'z')
    block(g, f['north'], 0, 0, 3, 1, 'Y')
    put(g, f['north'], 1, 0, 'z')
    # posts 2x3x2 at (32,8)
    box(c, 32, 8, 2, 3, 2, ('a', 'b', 'c', 'k'))
    # rotors 6x1x6 at (0,26): plus shaped blade
    f = box(c, 0, 26, 6, 1, 6, ('a', 'b', 'c', 'k'))
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, rh, '.')
        c.rect(x, y + 2, rw, 2, 'b')
        c.rect(x + 2, y, 2, rh, 'b')
        c.rect(x + 2, y + 2, 2, 2, 'c')
        c.rect(x, y + 2, rw, 1, 'a')
    return c, g


def courier_icon(mk2):
    """Front view: two rotors on short posts, a steel hull with an amber sensor and a brass cargo crate underneath."""
    def draw(c):
        for x0 in (1, 10):                                                    # rotor blades and posts
            c.rect(x0, 2, 5, 1, '2').rect(x0, 2, 2, 1, '3')
            c.rect(x0 + 2, 3, 1, 3, 'b')
        c.rect(3, 5, 10, 5, '3')                                              # hull
        c.auto_shade({'3': ('2', '4')})
        c.rect(6, 6, 4, 3, 'K').rect(7, 7, 2, 1, 'A').set(7, 7, 'B')          # sensor
        accent = 'y' if mk2 else 'o'
        c.set(4, 8, accent).set(11, 8, accent)                                # status lights
        c.rect(4, 10, 8, 4, 'c')                                              # cargo crate
        c.auto_shade({'c': ('b', 'd')})
        c.rect(7, 10, 2, 4, 'y' if mk2 else 'b').set(7, 10, 'Y' if mk2 else 'c')   # strap
    return draw


def courier_remote_icon():
    """Hand-held controller: steel body with a brass antenna, a cyan screen showing a drone blip and two buttons."""
    c = Canvas(16)
    c.rect(4, 4, 8, 11, '3')
    c.auto_shade({'3': ('2', '4')})
    c.rect(6, 1, 2, 3, 'c').set(6, 1, 'e').rect(6, 0, 2, 1, 'O').set(7, 0, 'o')   # antenna with brass tip
    c.recess(5, 5, 6, 4, '12345', fill='K')
    c.rect(6, 6, 4, 2, 'Z').rect(6, 6, 4, 1, 'y').set(8, 7, 'z')                    # screen
    for x, y in ((6, 10), (5, 11), (7, 11), (6, 12)):                                # d-pad
        c.set(x, y, 'c')
    c.set(6, 11, 'k').set(6, 10, 'e')
    c.rect(9, 10, 2, 2, 'A').set(9, 10, 'B').rect(9, 13, 2, 1, 'o')                 # buttons
    c.outline('k')
    return c


def main():
    ENTITY.mkdir(parents=True, exist_ok=True)
    for tier, pal_m, pal_s, suffix in ((1, MK1, SENTRY1, ''), (2, MK2, SENTRY2, '_mk2')):
        c, g = mining_sheet(tier)
        write_png(ENTITY / f'mining_drone{suffix}.png', c.rows(), pal_m, 64)
        if tier == 1:
            write_png(ENTITY / 'mining_drone_glow.png', g.rows(), pal_m, 64)
        c, g = sentry_sheet(tier)
        write_png(ENTITY / f'sentry_drone{suffix}.png', c.rows(), pal_s, 64)
        if tier == 1:
            write_png(ENTITY / 'sentry_drone_glow.png', g.rows(), pal_s, 64)

    for pal, suffix in ((COURIER1, ''), (COURIER2, '_mk2')):
        c, g = courier_sheet(1 if not suffix else 2)
        write_png(ENTITY / f'courier_drone{suffix}.png', c.rows(), pal, 64)
        if not suffix:
            write_png(ENTITY / 'courier_drone_glow.png', g.rows(), pal, 64)
    write_item('courier_drone', icon(courier_icon(False)).rows(), COURIER_ICON1)
    write_item('courier_drone_mk2', icon(courier_icon(True)).rows(), COURIER_ICON2)
    write_item('courier_remote', courier_remote_icon().rows(), COURIER_ICON1)
    write_item('mining_drone', icon(mining_icon(MK1, '2', '1', '3')).rows(), MK1)
    write_item('mining_drone_mk2', icon(mining_icon(MK2, '2', '1', '3')).rows(), MK2)
    write_item('sentry_drone', icon(sentry_icon(False)).rows(), SENTRY_ICON1)
    write_item('sentry_drone_mk2', icon(sentry_icon(True)).rows(), SENTRY_ICON2)
    print('drone textures written')


if __name__ == '__main__':
    main()
