"""Boss module textures: the Scrap Colossus (128x128 model sheet + glow layer), the Scrap Drone (32x32 + glow), the Forge
Tyrant (128x128 + glow), the Signal Flare and Ignition Charge icons, and the Colossus Altar and Forge Altar blocks (side,
top, bottom and their glow overlays).
Run: python3 scripts/textures/boss.py
The model sheets follow the box layout of boss/client/ScrapColossusModel, ScrapDroneModel and ForgeTyrantModel: texOffs(u, v), box w x h x d
unfolds as  top (u+d, v)  bottom (u+d+w, v)  west (u, v+d)  north/front (u+d, v+d)  east (u+d+w, v+d)  south (u+2d+w, v+d).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, MATERIALS, Canvas, fbm, hash01, write_block, write_item, write_png  # noqa: E402

ENTITY = ASSETS / 'textures/entity'

# 1-5 rusty copper (dark -> light), a-e verdigris, q/r rust browns, s-v dark steel, k black, glow: F orange, G yellow,
# H white hot, E red eye, D deep ember
PAL = {
    '1': '#3E200F', '2': '#7A3E1C', '3': '#A8562A', '4': '#CF7A45', '5': '#EBA878',
    'a': '#1F3F36', 'b': '#2F6050', 'c': '#4A8A70', 'd': '#6FB396', 'e': '#A6DCC4',
    'q': '#4A2A18', 'r': '#6A3A1E',
    's': '#1C2124', 't': '#2E3539', 'u': '#4A5358', 'v': '#6E787D', 'w': '#9AA4A8',
    'k': '#120E0C',
    'D': '#8A2A08', 'F': '#FF7A1E', 'G': '#FFC23A', 'H': '#FFF2B0', 'E': '#FF3B24',
    # stone for the altar
    'm': '#4F4F52', 'n': '#6C6C70', 'o': '#8A8A8E', 'p': '#A8A8AC',
    # flare
    'R': '#C9302A', 'S': '#FF6B5E', 'T': '#7A1712',
    # blackstone (dark -> light) and gold (dark -> light) for the Forge Tyrant
    '6': '#121014', '7': '#1F1B22', '8': '#2F2933', '9': '#443C48', '0': '#5E5462',
    'g': '#5C3A0A', 'h': '#9A6A12', 'i': '#D9A423', 'j': '#F7D25A', 'l': '#FFF0A8',
}
COPPER = ('1', '2', '3', '4', '5')
BLACK = ('6', '7', '8', '9', '0')
GOLD = ('g', 'h', 'i', 'j', 'l')
STEEL = ('s', 't', 'u', 'v', 'w')


def rects(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'east': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def weathered(c, x, y, w, h, ramp=COPPER, seed=0, verdigris=0.33, rust=0.12, bevel=True, dark=False):
    """Rusted metal panel: mid tone with brushed grain, verdigris blotches from noise, rust streaks running down."""
    r = ramp
    base = r[1] if dark else r[2]
    for yy in range(h):
        for xx in range(w):
            px, py = x + xx, y + yy
            n = hash01(px, py, seed)
            ch = base
            if n < 0.12:
                ch = r[1] if not dark else r[0]
            elif n > 0.9:
                ch = r[3]
            c.set(px, py, ch)
    for yy in range(h):                                           # verdigris blotches
        for xx in range(w):
            px, py = x + xx, y + yy
            f = fbm(px, py, seed + 31, wrap=128)
            if f > 1.0 - verdigris:
                c.set(px, py, 'c' if f > 1.0 - verdigris * 0.45 else 'b')
                if hash01(px, py, seed + 3) < 0.15:
                    c.set(px, py, 'd')
    for xx in range(w):                                           # rust streaks
        if hash01(x + xx, y, seed + 9) < rust:
            length = 2 + int(hash01(x + xx, y, seed + 10) * max(1, h - 2))
            start = int(hash01(x + xx, y, seed + 11) * max(1, h // 3))
            for yy in range(start, min(h, start + length)):
                c.set(x + xx, y + yy, 'r' if yy % 3 else 'q')
    if bevel and w >= 3 and h >= 3:
        for xx in range(w):
            c.set(x + xx, y, r[3])
            c.set(x + xx, y + h - 1, r[0])
        for yy in range(h):
            c.set(x, y + yy, r[3] if yy < h - 1 else r[0])
            c.set(x + w - 1, y + yy, r[1])


def box(c, u, v, w, h, d, ramp=COPPER, seed=0, **kw):
    f = rects(u, v, w, h, d)
    for i, (name, (x, y, rw, rh)) in enumerate(f.items()):
        weathered(c, x, y, rw, rh, ramp, seed + i * 13 + u * 3 + v, dark=(name == 'bottom'), **kw)
    return f


def put(c, face, dx, dy, ch):
    x, y, rw, rh = face
    if 0 <= dx < rw and 0 <= dy < rh:
        c.set(x + dx, y + dy, ch)


def fill(c, face, dx, dy, w, h, ch):
    for yy in range(h):
        for xx in range(w):
            put(c, face, dx + xx, dy + yy, ch)


def rivet(c, face, dx, dy):
    put(c, face, dx, dy, 'w')
    put(c, face, dx + 1, dy + 1, 's')


def band(c, face, dy, h=1, ramp=STEEL):
    x, y, rw, rh = face
    for yy in range(h):
        for xx in range(rw):
            put(c, face, xx, dy + yy, ramp[3] if yy == 0 else ramp[2] if hash01(x + xx, y + dy + yy, 5) > 0.2 else ramp[1])


# ---------------------------------------------------------------- Scrap Colossus

def colossus():
    c = Canvas(128)
    g = Canvas(128)
    # torso 22x20x14 at (0,0)
    f = box(c, 0, 0, 22, 20, 14, seed=1)
    front = f['north']
    for dx in range(22):                                          # collar band and belt band
        put(c, front, dx, 1, 'v')
        put(c, front, dx, 18, 'u')
    fill(c, front, 5, 5, 12, 10, 'k')                             # furnace cavity behind the hatch
    fill(c, front, 6, 6, 10, 8, 'D')
    for dx in (1, 3, 18, 20):
        for dy in (3, 9, 15):
            rivet(c, front, dx, dy)
    for side in ('west', 'east'):                                  # side plates with vents
        sf = f[side]
        for dy in range(6, 14, 2):
            fill(c, sf, 3, dy, 8, 1, 's')
            fill(c, sf, 3, dy + 1, 8, 1, 'u')
        rivet(c, sf, 1, 2)
        rivet(c, sf, 11, 2)
    back = f['south']
    fill(c, back, 3, 3, 16, 12, 't')                               # back hatch with a boiler gauge
    fill(c, back, 4, 4, 14, 10, 'u')
    for dy in range(5, 13, 2):
        fill(c, back, 5, dy, 12, 1, 's')
    fill(c, back, 9, 15, 4, 3, 'v')
    put(c, back, 10, 16, 'E')
    put(g, back, 10, 16, 'E')
    top = f['top']
    fill(c, top, 4, 4, 14, 6, 'u')                                # steel deck where the head sits
    # pelvis 16x5x10 at (0,34): dark steel
    f = box(c, 0, 34, 16, 5, 10, STEEL, seed=2, verdigris=0.1, rust=0.25)
    for dx in range(0, 16, 3):
        put(c, f['north'], dx, 2, 's')
    # legs 7x20x7 at (0,49): copper with a steel knee and a heavy dark foot
    f = box(c, 0, 49, 7, 20, 7, seed=3)
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 8, 3)
        band(c, f[name], 16, 4, ('s', 's', 't', 'u', 'v'))
        rivet(c, f[name], 1, 1)
        rivet(c, f[name], 4, 1)
    fill(c, f['bottom'], 0, 0, 7, 7, 's')
    # arms 7x18x7 at (28,49): copper with piston bands
    f = box(c, 28, 49, 7, 18, 7, seed=4)
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 6, 2)
        band(c, f[name], 12, 2)
        fill(c, f[name], 3, 8, 1, 4, 'w')                           # piston rod
    # fists 9x8x9 at (56,49): dark steel knuckles, copper rivets
    f = box(c, 56, 49, 9, 8, 9, STEEL, seed=5, verdigris=0.08, rust=0.3)
    for name in ('north', 'west', 'east', 'south'):
        for dx in (1, 3, 5, 7):
            put(c, f[name], dx, 5, '4')
            put(c, f[name], dx, 6, '2')
    fill(c, f['bottom'], 1, 1, 7, 7, 'k')
    for dx in (1, 3, 5, 7):
        fill(c, f['bottom'], dx, 1, 1, 7, 't')
    # shoulder pads 10x5x10 at (72,16): heavier verdigris and rivets
    f = box(c, 72, 16, 10, 5, 10, seed=6, verdigris=0.5)
    for name in ('north', 'south', 'west', 'east'):
        rivet(c, f[name], 1, 1)
        rivet(c, f[name], 7, 1)
    for dx, dy in ((1, 1), (7, 1), (1, 7), (7, 7), (4, 4)):
        rivet(c, f['top'], dx, dy)
    # head 9x7x9 at (72,0): visor band with two glowing eyes
    f = box(c, 72, 0, 9, 7, 9, seed=7)
    hf = f['north']
    fill(c, hf, 1, 2, 7, 3, 'k')
    fill(c, hf, 1, 2, 7, 1, 't')
    for dx in (2, 5):
        fill(c, hf, dx, 3, 2, 1, 'E')
        fill(g, hf, dx, 3, 2, 1, 'E')
        put(c, hf, dx, 3, 'G')
        put(g, hf, dx, 3, 'G')
    fill(c, hf, 3, 5, 3, 1, 'u')                                  # grille mouth
    put(c, hf, 4, 5, 's')
    rivet(c, f['top'], 3, 3)
    # smoke stacks 3x8x3 at (92,49): sooty steel, hollow top with an ember
    f = box(c, 92, 49, 3, 8, 3, STEEL, seed=8, verdigris=0.0, rust=0.4)
    fill(c, f['top'], 0, 0, 3, 3, 'k')
    put(c, f['top'], 1, 1, 'D')
    put(g, f['top'], 1, 1, 'F')
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 0, 1)
        fill(c, f[name], 0, 1, 3, 1, 'k')
    # hatch 10x8x1 at (0,76): copper door with glowing slits; inside (south) dark
    f = box(c, 0, 76, 10, 8, 1, seed=9, verdigris=0.4)
    hf = f['north']
    for dy in (2, 4, 6):
        fill(c, hf, 2, dy, 6, 1, 'F')
        fill(g, hf, 2, dy, 6, 1, 'F')
        put(c, hf, 4, dy, 'G')
        put(g, hf, 4, dy, 'G')
    rivet(c, hf, 0, 0)
    rivet(c, hf, 8, 0)
    put(c, hf, 9, 3, 'w')                                         # handle
    put(c, hf, 9, 4, 'w')
    fill(c, f['south'], 0, 0, 10, 8, 't')
    # furnace core 8x6x1 at (24,76): white hot centre, grate bars
    f = rects(24, 76, 8, 6, 1)
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 'F')
        g.rect(x, y, rw, rh, 'F')
    cf = f['north']
    for dy in range(6):
        for dx in range(8):
            dist = abs(dx - 3.5) / 4 + abs(dy - 2.5) / 3
            ch = 'H' if dist < 0.5 else 'G' if dist < 0.9 else 'F'
            put(c, cf, dx, dy, ch)
            put(g, cf, dx, dy, ch)
    for dx in (1, 4, 6):
        fill(c, cf, dx, 0, 1, 6, 'D')
        fill(g, cf, dx, 0, 1, 6, 'D')
    return c, g


# ---------------------------------------------------------------- Scrap Drone

def drone():
    c = Canvas(32)
    g = Canvas(32)
    f = box(c, 0, 0, 6, 4, 6, seed=21, verdigris=0.4)
    fill(c, f['north'], 1, 1, 4, 2, 'k')
    f = rects(0, 10, 3, 2, 1)                                     # eye
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 't')
    fill(c, f['north'], 0, 0, 3, 2, 'E')
    fill(g, f['north'], 0, 0, 3, 2, 'E')
    put(c, f['north'], 1, 0, 'G')
    put(g, f['north'], 1, 0, 'G')
    f = rects(0, 14, 12, 1, 1)                                    # rotor arm
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 'u')
    f = rects(16, 10, 1, 2, 1)                                    # stinger
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 'v')
    f = rects(0, 17, 4, 1, 4)                                     # rotor discs: a cross of blades, the rest see-through
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y + 1, rw, 2, 'v')
        c.rect(x + 1, y, 2, rh, 'v')
        c.set(x + 1, y + 1, 's')
    for name in ('north', 'south', 'west', 'east'):
        x, y, rw, rh = f[name]
        c.rect(x + 1, y, 2, rh, 'u')
    return c, g


# ---------------------------------------------------------------- Forge Tyrant

def molten(c, g, x, y, w, h, seed=0, cracks=0.1, bevel=True, dark=False):
    """Blackstone plate with glowing magma cracks (the cracks go on the glow layer too)."""
    weathered(c, x, y, w, h, BLACK, seed, verdigris=0.0, rust=0.0, bevel=bevel, dark=dark)
    for yy in range(h):
        for xx in range(w):
            px, py = x + xx, y + yy
            f = fbm(px, py, seed + 57, wrap=128)
            if abs(f - 0.5) < cracks * 0.25:
                ch = 'F' if abs(f - 0.5) < cracks * 0.1 else 'D'
                c.set(px, py, ch)
                g.set(px, py, ch)


def mbox(c, g, u, v, w, h, d, seed=0, cracks=0.1):
    f = rects(u, v, w, h, d)
    for i, (name, (x, y, rw, rh)) in enumerate(f.items()):
        molten(c, g, x, y, rw, rh, seed + i * 13 + u * 3 + v, cracks=cracks, dark=(name == 'bottom'))
    return f


def gold_rivet(c, face, dx, dy):
    put(c, face, dx, dy, 'j')
    put(c, face, dx + 1, dy + 1, 'g')


def tyrant():
    c = Canvas(128)
    g = Canvas(128)
    # body 24x20x18 at (0,0)
    f = mbox(c, g, 0, 0, 24, 20, 18, seed=101, cracks=0.12)
    front = f['north']
    band(c, front, 0, 2, GOLD)
    band(c, front, 18, 2, GOLD)
    fill(c, front, 5, 2, 14, 13, 'k')                             # furnace mouth behind the doors
    fill(c, front, 6, 3, 12, 11, 'D')
    fill(g, front, 6, 3, 12, 11, 'D')
    for dx in (1, 3, 20, 22):
        for dy in (4, 9, 14):
            gold_rivet(c, front, dx, dy)
    for side in ('west', 'east'):                                  # side grilles glowing from inside
        sf = f[side]
        band(c, sf, 0, 2, GOLD)
        band(c, sf, 18, 2, GOLD)
        for dy in range(5, 15, 2):
            fill(c, sf, 4, dy, 10, 1, 'k')
            fill(c, sf, 4, dy + 1, 10, 1, 'F')
            fill(g, sf, 4, dy + 1, 10, 1, 'F')
        gold_rivet(c, sf, 1, 3)
        gold_rivet(c, sf, 15, 3)
    back = f['south']
    band(c, back, 0, 2, GOLD)
    fill(c, back, 4, 4, 16, 12, 't')                               # riveted back plate with a pressure gauge
    fill(c, back, 5, 5, 14, 10, 'u')
    for dy in range(6, 14, 2):
        fill(c, back, 6, dy, 12, 1, 's')
    fill(c, back, 10, 16, 4, 3, 'i')
    put(c, back, 11, 17, 'E')
    put(g, back, 11, 17, 'E')
    fill(c, f['top'], 7, 9, 10, 9, 't')                            # deck under the chimney
    # chimney 8x12x8 at (86,16): sooty steel with gold bands, glowing mouth
    f = box(c, 86, 16, 8, 12, 8, STEEL, seed=102, verdigris=0.0, rust=0.3)
    fill(c, f['top'], 0, 0, 8, 8, 'k')
    fill(c, f['top'], 2, 2, 4, 4, 'D')
    fill(g, f['top'], 2, 2, 4, 4, 'F')
    put(g, f['top'], 3, 3, 'G')
    put(g, f['top'], 4, 4, 'G')
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 0, 2, GOLD)
        band(c, f[name], 7, 1, GOLD)
    # head 10x6x10 at (86,0): blackstone hood with a visor slit and two white-hot eyes
    f = mbox(c, g, 86, 0, 10, 6, 10, seed=103, cracks=0.06)
    hf = f['north']
    band(c, hf, 0, 1, GOLD)
    fill(c, hf, 1, 2, 8, 2, 'k')
    for dx in (2, 6):
        fill(c, hf, dx, 2, 2, 2, 'G')
        fill(g, hf, dx, 2, 2, 2, 'G')
        put(c, hf, dx, 2, 'H')
        put(g, hf, dx, 2, 'H')
    fill(c, hf, 3, 5, 4, 1, 'i')
    # legs 8x12x8 at (0,40): blackstone with a gold knee band; feet 10x4x10 at (32,40): dark steel
    f = mbox(c, g, 0, 40, 8, 12, 8, seed=104)
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 4, 2, GOLD)
        gold_rivet(c, f[name], 1, 1)
        gold_rivet(c, f[name], 5, 1)
    f = box(c, 32, 40, 10, 4, 10, STEEL, seed=105, verdigris=0.0, rust=0.2)
    for name in ('north', 'west', 'east', 'south'):
        band(c, f[name], 0, 1, GOLD)
    # arms 6x14x6 at (72,40): blackstone with gold piston bands
    f = mbox(c, g, 72, 40, 6, 14, 6, seed=106)
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 4, 1, GOLD)
        band(c, f[name], 10, 1, GOLD)
        fill(c, f[name], 2, 5, 1, 5, 'w')
    # crucible 9x7x9 at (0,62): dark steel pot with a gold rim, glowing spout and a red-hot bottom
    f = box(c, 0, 62, 9, 7, 9, STEEL, seed=107, verdigris=0.0, rust=0.15)
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 0, 1, GOLD)
        band(c, f[name], 6, 1, GOLD)
    for dy in range(1, 6):
        put(c, f['north'], 4, dy, 'F')
        put(g, f['north'], 4, dy, 'F')
    put(c, f['north'], 4, 5, 'G')
    put(g, f['north'], 4, 5, 'G')
    x0, y0, rw, rh = f['bottom']
    for yy in range(rh):
        for xx in range(rw):
            d = abs(xx - 4) + abs(yy - 4)
            ch = 'G' if d < 2 else 'F' if d < 4 else 'D'
            c.set(x0 + xx, y0 + yy, ch)
            g.set(x0 + xx, y0 + yy, ch)
    # hammer 10x8x10 at (36,62): heavy steel head with a gold rim and a blackstone striking face
    f = box(c, 36, 62, 10, 8, 10, STEEL, seed=108, verdigris=0.0, rust=0.2)
    for name in ('north', 'south', 'west', 'east'):
        band(c, f[name], 0, 1, GOLD)
        gold_rivet(c, f[name], 1, 3)
        gold_rivet(c, f[name], 7, 3)
    x0, y0, rw, rh = f['bottom']
    molten(c, g, x0, y0, rw, rh, seed=109, cracks=0.2)
    # shoulder pads 9x5x9 at (78,62): gold plate with rivets
    f = box(c, 78, 62, 9, 5, 9, GOLD, seed=110, verdigris=0.0, rust=0.05)
    for name in ('north', 'south', 'west', 'east'):
        put(c, f[name], 1, 1, 'l')
        put(c, f[name], 7, 1, 'l')
        fill(c, f[name], 0, 4, 9, 1, 'g')
    for dx, dy in ((1, 1), (7, 1), (1, 7), (7, 7), (4, 4)):
        put(c, f['top'], dx, dy, 'l')
    # doors 7x10x1 at (0,82): blackstone with gold frames and glowing grate slits
    f = rects(0, 82, 7, 10, 1)
    for name, (x, y, rw, rh) in f.items():
        molten(c, g, x, y, rw, rh, seed=111, cracks=0.0, bevel=rw >= 3 and rh >= 3)
    df = f['north']
    for dx in range(7):
        put(c, df, dx, 0, 'i')
        put(c, df, dx, 9, 'h')
    for dy in range(10):
        put(c, df, 0, dy, 'i')
    for dy in (2, 4, 6):
        fill(c, df, 2, dy, 4, 1, 'F')
        fill(g, df, 2, dy, 4, 1, 'F')
        put(c, df, 3, dy, 'G')
        put(g, df, 3, dy, 'G')
    put(c, df, 5, 8, 'j')
    fill(c, f['south'], 0, 0, 7, 10, 'D')
    fill(g, f['south'], 0, 0, 7, 10, 'D')
    # core 12x10x1 at (20,82): white-hot centre behind grate bars
    f = rects(20, 82, 12, 10, 1)
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 'F')
        g.rect(x, y, rw, rh, 'F')
    cf = f['north']
    for dy in range(10):
        for dx in range(12):
            dist = abs(dx - 5.5) / 6 + abs(dy - 4.5) / 5
            ch = 'H' if dist < 0.45 else 'G' if dist < 0.85 else 'F'
            put(c, cf, dx, dy, ch)
            put(g, cf, dx, dy, ch)
    for dx in (2, 5, 8):
        fill(c, cf, dx, 0, 1, 10, 'D')
        fill(g, cf, dx, 0, 1, 10, 'D')
    return c, g


# ---------------------------------------------------------------- items and blocks

def signal_flare():
    c = Canvas(16)
    for i in range(7):                                            # red tube running from bottom left to top right
        x, y = 3 + i, 12 - i
        c.set(x, y, 'R').set(x + 1, y, 'R').set(x, y - 1, 'S').set(x + 1, y + 1, 'T')
    for i in (1, 4):
        c.set(3 + i, 12 - i, '4').set(4 + i, 12 - i, '3')         # copper bands
    c.set(2, 13, '2').set(3, 13, '3').set(2, 12, '4').set(3, 14, '1')   # copper cap and fuse end
    c.outline('k')
    c.set(11, 4, 'G').set(12, 3, 'H').set(13, 2, 'G').set(12, 5, 'F').set(10, 3, 'F').set(14, 4, 'F')
    c.set(13, 5, 'D').set(11, 2, 'D')
    write_item('signal_flare', c.rows(), PAL)


def ignition_charge():
    c = Canvas(16)
    c.rect(5, 4, 6, 10, '8')                                      # blackstone canister
    for y in range(4, 14):
        c.set(5, y, '9').set(10, y, '7')
    for y in (5, 12):                                             # gold bands
        for x in range(5, 11):
            c.set(x, y, 'i' if x < 9 else 'h')
    c.rect(6, 7, 4, 4, 'D')                                       # magma window
    c.rect(7, 8, 2, 2, 'F')
    c.set(7, 8, 'G')
    c.set(7, 3, 'h').set(8, 3, 'i').set(8, 2, 'i')                # gold fuse
    c.outline('k')
    c.set(9, 1, 'G').set(8, 0, 'H').set(10, 0, 'F').set(10, 2, 'F').set(7, 1, 'D')
    write_item('ignition_charge', c.rows(), PAL)


def forge_altar():
    side = Canvas(16)
    glow_side = Canvas(16)
    molten(side, glow_side, 0, 4, 16, 8, seed=141, cracks=0.1)
    for y in range(12, 16):                                       # polished blackstone footing
        for x in range(16):
            side.set(x, y, '8' if hash01(x, y, 14) > 0.25 else '7')
    for x in range(16):
        side.set(x, 12, '9')
        side.set(x, 15, '6')
    for x in (3, 11):
        side.set(x, 13, '6').set(x, 14, '6')
    for y in range(0, 4):                                         # top rim: gold band with rivets
        for x in range(16):
            side.set(x, y, 'i' if y in (1, 2) else 'j' if y == 0 else 'h')
    for x in (1, 5, 10, 14):
        side.set(x, 1, 'l').set(x + 1, 2, 'g')
    side.rect(5, 6, 6, 5, 'k')                                    # furnace mouth with a grate
    side.rect(6, 7, 4, 3, 'D')
    glow_side.rect(6, 7, 4, 3, 'F')
    glow_side.set(7, 8, 'G').set(8, 8, 'H').set(7, 9, 'G').set(8, 9, 'G')
    for x in (6, 9):
        side.set(x, 7, 'g').set(x, 8, 'g').set(x, 9, 'g')
        glow_side.set(x, 7, '.').set(x, 8, '.').set(x, 9, '.')
    top = Canvas(16)
    glow_top = Canvas(16)
    weathered(top, 0, 0, 16, 16, BLACK, seed=143, verdigris=0.0, rust=0.0)
    for y in range(16):                                           # gold-rimmed crucible full of magma
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.2 <= d <= 6.4:
                top.set(x, y, 'i' if (x + y) % 3 else 'j')
            elif 4.4 <= d < 5.2:
                top.set(x, y, 'g')
            elif d < 4.4:
                ch = 'H' if d < 1.2 else 'G' if d < 2.6 else 'F' if hash01(x, y, 9) > 0.2 else 'D'
                top.set(x, y, ch)
                glow_top.set(x, y, ch)
    bottom = Canvas(16)
    for y in range(16):
        for x in range(16):
            bottom.set(x, y, '8' if hash01(x, y, 17) > 0.25 else '7')
        if y % 4 == 3:
            for x in range(16):
                bottom.set(x, y, '6')
    for y in range(16):
        bottom.set((y // 4 % 2) * 8 + 3, y, '6')
    write_block('forge_altar_side', side.rows(), PAL)
    write_block('forge_altar_side_glow', glow_side.rows(), PAL)
    write_block('forge_altar_top', top.rows(), PAL)
    write_block('forge_altar_top_glow', glow_top.rows(), PAL)
    write_block('forge_altar_bottom', bottom.rows(), PAL)


def altar():
    side = Canvas(16)
    glow_side = Canvas(16)
    weathered(side, 0, 4, 16, 8, seed=41, verdigris=0.4)
    for y in range(12, 16):                                       # stone brick footing
        for x in range(16):
            side.set(x, y, 'n' if hash01(x, y, 4) > 0.25 else 'm')
    for x in range(16):
        side.set(x, 12, 'o')
        side.set(x, 15, 'm')
    for x in (3, 11):
        side.set(x, 13, 'm').set(x, 14, 'm')
    for y in range(0, 4):                                         # top rim: steel band with rivets
        for x in range(16):
            side.set(x, y, 'u' if y in (1, 2) else 'v' if y == 0 else 't')
    for x in (1, 5, 10, 14):
        side.set(x, 1, 'w').set(x + 1, 2, 's')
    side.rect(5, 6, 6, 5, 'k')                                    # furnace mouth with a grate
    side.rect(6, 7, 4, 3, 'D')
    glow_side.rect(6, 7, 4, 3, 'F')
    glow_side.set(7, 8, 'G').set(8, 8, 'H').set(7, 9, 'G').set(8, 9, 'G')
    for x in (6, 9):
        side.set(x, 7, 's').set(x, 8, 's').set(x, 9, 's')
        glow_side.set(x, 7, '.').set(x, 8, '.').set(x, 9, '.')
    top = Canvas(16)
    glow_top = Canvas(16)
    weathered(top, 0, 0, 16, 16, seed=43, verdigris=0.35)
    for y in range(16):                                           # gear sigil
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            tooth = (x in (7, 8) or y in (7, 8)) and 4.5 <= d <= 6.6
            if 3.6 <= d <= 4.6 or tooth:
                top.set(x, y, 'k')
            if 2.6 <= d <= 3.5:
                top.set(x, y, 'u')
            if d < 2.5:
                top.set(x, y, 'D')
                glow_top.set(x, y, 'G' if d < 1.3 else 'F')
            if 3.7 <= d <= 4.4 or (tooth and d > 4.8):
                glow_top.set(x, y, 'D')
    bottom = Canvas(16)
    for y in range(16):
        for x in range(16):
            bottom.set(x, y, 'n' if hash01(x, y, 7) > 0.25 else 'm')
        if y % 4 == 3:
            for x in range(16):
                bottom.set(x, y, 'm')
    for y in range(16):
        bottom.set((y // 4 % 2) * 8 + 3, y, 'm')
    write_block('colossus_altar_side', side.rows(), PAL)
    write_block('colossus_altar_side_glow', glow_side.rows(), PAL)
    write_block('colossus_altar_top', top.rows(), PAL)
    write_block('colossus_altar_top_glow', glow_top.rows(), PAL)
    write_block('colossus_altar_bottom', bottom.rows(), PAL)


def main():
    ENTITY.mkdir(parents=True, exist_ok=True)
    c, g = colossus()
    write_png(ENTITY / 'scrap_colossus.png', c.rows(), PAL, 128)
    write_png(ENTITY / 'scrap_colossus_glow.png', g.rows(), PAL, 128)
    c, g = drone()
    write_png(ENTITY / 'scrap_drone.png', c.rows(), PAL, 32)
    write_png(ENTITY / 'scrap_drone_glow.png', g.rows(), PAL, 32)
    c, g = tyrant()
    write_png(ENTITY / 'forge_tyrant.png', c.rows(), PAL, 128)
    write_png(ENTITY / 'forge_tyrant_glow.png', g.rows(), PAL, 128)
    signal_flare()
    altar()
    ignition_charge()
    forge_altar()
    print('boss textures written')


if __name__ == '__main__':
    main()
