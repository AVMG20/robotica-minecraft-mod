"""Drones module textures: Buddy Bot (Mk1 copper, Mk2 dark steel, Mk3 blackened netherite, Mk4 null), Courier and Hauler Drone.
Run: python3 scripts/textures/drones.py
Writes the 64x64 entity sheets in textures/entity, their *_glow.png (only the full bright pixels, drawn by a render layer),
buddy_bot_eye_<stance>.png (the Buddy Bot eye, tinted by stance) and the item icons (write_item adds missing item models).
The model sheets follow the box layout of the models in drones/client: texOffs(u, v), box w x h x d unfolds as
    top (u+d, v)  bottom (u+d+w, v)  west (u, v+d)  north/front (u+d, v+d)  east (u+d+w, v+d)  south (u+2d+w, v+d)
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, MATERIALS, Canvas, write_item, write_png  # noqa: E402

ENTITY = ASSETS / 'textures/entity'

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


# ---------------------------------------------------------------- Buddy Bot

# Chars: '01234' body (deep -> highlight), 'abcde' trim, 'mnoO' drill (dark -> highlight), 'ghi' tier glow (deep, mid,
# bright), 'k' near black, 'K' lens glass, 'x' cargo hold, 'W' teeth, 'w' white, 'G D E' loot (gold, diamond, emerald).
BUDDY = {
    1: {'body': MATERIALS['copper'], 'trim': MATERIALS['brass'],
        'drill': ('#33393C', '#5A6266', '#A9B1B4', '#E4EAEC'), 'glow': ('#145A66', '#2FB8CC', '#B5F6FB')},
    2: {'body': ('#1B2124', '#2A3034', '#3F484E', '#566067', '#7E8A90'), 'trim': MATERIALS['gold'],
        'drill': ('#3E4447', '#6B7376', '#B5BCBF', '#F0F4F5'), 'glow': ('#145A66', '#2FB8CC', '#B5F6FB')},
    3: {'body': ('#0E0B0E', '#1A1519', '#2A2328', '#40363C', '#5E5258'), 'trim': ('#1C1618', '#352C30', '#54484D', '#7A6C71', '#A8989C'),
        'drill': ('#1E1A1C', '#3A3236', '#5E5458', '#8E8286'), 'glow': ('#A82E0A', '#FF7A1A', '#FFD27A')},
    4: {'body': ('#0C0618', '#1A0E36', '#2C1A58', '#47307E', '#6E52A8'), 'trim': ('#2A1660', '#4A2C96', '#7650C8', '#A884F0', '#E6DAFF'),
        'drill': ('#145247', '#2E8F7F', '#5AD6BE', '#D8FBFF'), 'glow': ('#7A1E9E', '#E060FF', '#FFE0FF')},
}
# Eye glow by stance (aggressive, defense, passive): 'p' lens rim, 'q' pupil sides, 's' pupil, 'w' glint.
BUDDY_EYES = {
    'aggressive': ('#5A1008', '#E83A2A', '#FF8A70'),
    'defense': ('#0E4650', '#2FB8CC', '#9AF2FA'),
    'passive': ('#123E16', '#3EB03A', '#AEF08A'),
}


def buddy_pal(tier):
    t = BUDDY[tier]
    p = {'k': '#1E1A1A', 'K': '#0A0C10', 'x': '#120E10', 'W': '#F2EEDC', 'w': '#FFFFFF',
         'G': '#FFD84A', 'D': '#6FE8F0', 'E': '#3EC75A'}
    p.update(zip('01234', t['body']))
    p.update(zip('abcde', t['trim']))
    p.update(zip('mnoO', t['drill']))
    p.update(zip('ghi', t['glow']))
    return p


def eye_pal(stance):
    deep, mid, bright = BUDDY_EYES[stance]
    return {'p': deep, 'q': mid, 's': bright, 'w': '#FFFFFF'}


def stamp(c, face, rows):
    """Draws a hand made face: rows of chars sized like the face, '.' = transparent, ' ' = leave alone."""
    x, y, rw, rh = face
    for dy, row in enumerate(rows[:rh]):
        for dx, ch in enumerate(row[:rw]):
            if ch != ' ':
                c.set(x + dx, y + dy, ch)


def panel(c, face, ramp='01234', seed=0, top=None, bottom=None):
    """Side plate: mid fill with brushed grain, light top row, shadowed bottom row."""
    x, y, rw, rh = face
    c.rect(x, y, rw, rh, ramp[2])
    if rw >= 3 and rh >= 3:
        c.brushed(x, y + 1, rw, rh - 2, ramp, seed=seed, density=0.16)
    c.rect(x, y, rw, 1, top or ramp[3])
    c.rect(x, y + rh - 1, rw, 1, bottom or ramp[1])


def buddy_sheet(tier):
    """Returns (main, tier glow, eye glow) canvases of the Buddy Bot sheet (64x64, layout of BuddyBotModel)."""
    c = Canvas(64)
    e = Canvas(64)
    hot = tier >= 3                                # Mk3/Mk4: glowing seams, stripe, hub, drill tip and blade tips
    seam = 'h' if hot else '0'

    # body 8x6x8 at (0,0): a chest with a trim skirt and rivets
    f = rects(0, 0, 8, 6, 8)
    for i, name in enumerate(('north', 'south', 'west', 'east')):
        x, y, rw, rh = f[name]
        panel(c, f[name], seed=11 + i)
        c.set(x, y + 1, '3').set(x + rw - 1, y + 1, '1')            # rounded vertical edges
        c.rect(x, y + rh - 1, rw, 1, 'c')                           # skirt
        c.set(x, y + rh - 1, 'd').set(x + rw - 1, y + rh - 1, 'b')
        put(c, f[name], 1, 1, 'e')
        put(c, f[name], rw - 2, 1, 'e')
    for name in ('west', 'east'):                                   # vent slit above the shoulder
        block(c, f[name], 2, 1, 4, 1, seam)
        if hot:
            put(c, f[name], 3, 1, 'i')
    # back: hinge knuckles, a hatch with two charge lights
    stamp(c, f['south'], [
        ' dc  dc ',
        '        ',
        '  0001  ',
        '  0gh3  ',
        '  1333  ',
    ])
    # top: the open cargo hold, seen when the lid snaps open (bottom rows face the front)
    stamp(c, f['top'], [
        'cdddddcb',
        'dkkkkkkb',
        'dkxxxxxb',
        'dkxGxDxb',
        'dkxxGGxb',
        'dkxExxxb',
        'dkxxxxxa',
        'bbbbbbba',
    ])
    # bottom: plate with screws, the nozzle sits in the middle
    x, y, rw, rh = f['bottom']
    c.rect(x, y, rw, rh, '1')
    c.bevel(x, y, rw, rh, '2', '0')
    for dx, dy in ((1, 1), (6, 1), (1, 6), (6, 6)):
        put(c, f['bottom'], dx, dy, 'c')

    # lid 9x3x9 at (0,14): rimmed top with a racing stripe, buck teeth inside
    f = rects(0, 14, 9, 3, 9)
    stripe = 'h' if hot else 'd'
    x, y, rw, rh = f['top']
    c.rect(x, y, rw, rh, '3')
    c.brushed(x + 1, y + 1, rw - 2, rh - 2, '23344', seed=5, density=0.12)
    stamp(c, f['top'], [
        'cddd dddc',
        'd4     2b',
        'd       b',
        'd       b',
        'd       b',
        'd       b',
        'd       b',
        'd2     1b',
        'cbbb bbba',
    ])
    c.rect(x + 4, y, 1, rh, stripe)
    if hot:
        c.set(x + 4, y + 2, 'i').set(x + 4, y + 6, 'i')
    else:
        c.set(x + 4, y, 'e')
    for dx, dy in ((1, 1), (7, 1), (1, 7), (7, 7)):
        put(c, f['top'], dx, dy, 'e')
    stamp(c, f['bottom'], [
        'bbbbbbbbb',
        'bkkkkkkkb',
        'bxxxxxxxb',
        'bxxxxxxxb',
        'bxxxxxxxb',
        'bxxxxxxxb',
        'bxxxxxxxb',
        'bxxWxWxxb',
        'bbbWbWbbb',
    ])
    for name in ('north', 'west', 'east', 'south'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, 1, '3').rect(x, y + 1, rw, 1, '2').rect(x, y + 2, rw, 1, 'c')
        c.set(x, y + 2, 'd').set(x + rw - 1, y + 2, 'b')
    put(c, f['north'], 4, 2, 'e')                                   # latch between the brows
    put(c, f['north'], 4, 1, 'c')
    stamp(c, f['south'], ['', '', ' dc   dc '])

    # eye bezel 6x6x1 at (32,0): rounded trim ring round a dark lens
    f = rects(32, 0, 6, 6, 1)
    stamp(c, f['north'], [
        '.dccb.',
        'dkKKkb',
        'cKKKKb',
        'cKKKKa',
        'bkKKka',
        '.bbaa.',
    ])
    put(c, f['north'], 1, 0, 'e')
    stamp(c, f['top'], ['.dddd.'])
    stamp(c, f['bottom'], ['.bbbb.'])
    stamp(c, f['west'], ['.', 'd', 'c', 'c', 'c', '.'])
    stamp(c, f['east'], ['.', 'c', 'b', 'b', 'b', '.'])
    c.rect(*f['south'], 'k')
    stamp(e, f['north'], [
        '      ',
        '  pp  ',
        ' p  p ',
        ' p  p ',
        '  pp  ',
        '      ',
    ])
    # pupil 2x2x1 at (46,0): dark here, lit by the eye sheet
    f = rects(46, 0, 2, 2, 1)
    for face in f.values():
        c.rect(*face, 'K')
    stamp(e, f['north'], ['ws', 'ss'])
    for name in ('top', 'bottom', 'west', 'east'):
        e.rect(*f[name], 'q')
    # eyelid 4x4x1 at (46,3): a body coloured shutter with a dark lash line
    f = rects(46, 3, 4, 4, 1)
    stamp(c, f['north'], ['3443', '2332', '2222', 'kkkk'])
    for name in ('top', 'west', 'east', 'south'):
        c.rect(*f[name], '2')
    c.rect(*f['bottom'], 'k')
    # brows 3x1x1 at (52,0)
    f = rects(52, 0, 3, 1, 1)
    for name, face in f.items():
        c.rect(*face, '0' if name == 'top' else 'k')
    # mast 1x2x1 at (56,3), propeller hub 2x1x2 at (56,6)
    f = rects(56, 3, 1, 2, 1)
    for face in f.values():
        c.rect(*face, 'b')
    c.rect(*f['top'], 'c')
    f = rects(56, 6, 2, 1, 2)
    for face in f.values():
        c.rect(*face, 'c')
    stamp(c, f['top'], ['ih', 'hg'] if hot else ['ed', 'dc'])
    # propeller blade 10x1x2 at (36,20): two tone blades, glowing tips on Mk3/Mk4
    f = rects(36, 20, 10, 1, 2)
    tip = 'h' if hot else 'e'
    for name in ('top', 'bottom'):
        stamp(c, f[name], [
            '.' + 'dddc' + 'bb' + '3333',
            'cccc' + 'bb' + '222' + '.',
        ])
        put(c, f[name], 1, 0, tip)
        put(c, f[name], 8, 1, tip)
    for name in ('north', 'south', 'west', 'east'):
        c.rect(*f[name], 'b')
    # thruster nozzle 4x1x4 at (36,14): glowing jet underneath
    f = rects(36, 14, 4, 1, 4)
    for face in f.values():
        c.rect(*face, 'b')
    stamp(c, f['bottom'], ['abba', 'bihb', 'bhgb', 'abba'])
    stamp(c, f['top'], ['cccc', 'cbbc', 'cbbc', 'cccc'])

    # shoulder pods 3x3x3 at (0,26): rounded trim pods with a bolt on the outer face
    f = rects(0, 26, 3, 3, 3)
    for name, face in f.items():
        if name == 'top':
            stamp(c, face, ['cdc', 'ded', 'cdc'])
        elif name == 'bottom':
            stamp(c, face, ['aba', 'bab', 'aba'])
        else:
            stamp(c, face, ['bdb', 'cdc', 'aba'])
    for name in ('west', 'east'):
        stamp(c, f[name], ['bdb', 'dek', 'bkb'])
    # drill collar 3x3x2 at (12,26), bit 2x2x2 at (22,26), tip 1x1x2 at (30,26): spiral flutes
    f = rects(12, 26, 3, 3, 2)
    for name, face in f.items():
        c.rect(*face, 'n')
    stamp(c, f['north'], ['ooO', 'o n', 'nnm'])
    for name in ('top', 'west', 'east', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, rh, 'n')
        c.set(x, y, 'o')
    f = rects(22, 26, 2, 2, 2)
    for i, (name, face) in enumerate(f.items()):
        stamp(c, face, ['Om', 'mO'] if i % 2 else ['mO', 'Om'])
    f = rects(30, 26, 1, 1, 2)
    for face in f.values():
        c.rect(*face, 'h' if hot else 'o')
    c.rect(*f['north'], 'i' if hot else 'O')
    # blaster barrel 2x2x3 at (36,26) with a dark cooling ring, muzzle 3x3x1 at (46,26) with a glowing bore
    f = rects(36, 26, 2, 2, 3)
    for name, face in f.items():
        x, y, rw, rh = face
        c.rect(x, y, rw, rh, 'b')
        c.rect(x, y, rw, 1, 'c')
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y + 1, rw, 1, 'k')
    for name in ('west', 'east'):
        x, y, rw, rh = f[name]
        c.rect(x + 1, y, 1, rh, 'k')
    f = rects(46, 26, 3, 3, 1)
    for face in f.values():
        c.rect(*face, 'c')
    stamp(c, f['north'], ['dcc', 'cib', 'cba'])
    c.set(f['north'][0] + 1, f['north'][1] + 1, 'i')
    glow = c.mask('ghi')
    c.replace('hi', 'g')                    # dark under the glow, so the additive layer keeps its hue in daylight
    return c, glow, e


def buddy_icon(tier):
    """Front view: propeller on the lid, a unibrow, one big eye with a lit pupil, shoulder pods with a muzzle and a drill tip."""
    hot = 'h' if tier >= 3 else None

    def draw(c):
        c.draw(0, 0, [
            '.......ed.......',
            '...edddcb3332...',
            '.......cb.......',
            '..dddddddddddc..',
            '..d4333dd3332b..',
            '..cbbbkkkkbbba..',
            '...32kdccbk21...',
            '.dc32dKKKKb21cd.',
            '.ih32cKKwhb21On.',
            '.ba32cKKhha21ab.',
            '...32bKKKKa21...',
            '...322baab121...',
            '...dccccccccb...',
            '......bhhb......',
        ])
        if hot:                                                     # glowing stripe and blade tips on Mk3/Mk4
            c.rect(7, 3, 2, 2, hot).set(3, 1, hot).set(12, 1, hot)
    return draw


# ---------------------------------------------------------------- item icons

def icon(draw):
    c = Canvas(16)
    draw(c)
    c.outline('k')
    return c


# Item icons of the courier and hauler drones and the courier remote share the mod wide item look (see core.py): outline 'k',
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


COURIER_ICON1 = ipal('white', 'brass')
COURIER_ICON2 = ipal(DSTEEL, 'brass')


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


HAULER1 = {
    '1': '#F2C94C', '2': '#D9A62E', '3': '#B07F1E', '4': '#7A5512',      # safety yellow body
    'a': '#C4CBCE', 'b': '#8D9599', 'c': '#4D5558', 'k': '#1E1A1A',      # steel clamp
    'r': '#D4A73A', 'R': '#F2D27A',
    'y': '#2FB8CC', 'Y': '#5FE3F0', 'z': '#D8FBFF',                      # cyan sensor
}
HAULER2 = dict(HAULER1)
HAULER2.update({
    '1': '#7E8A90', '2': '#566067', '3': '#3F484E', '4': '#2A3034',      # dark steel body
    'a': '#FFE08A', 'b': '#F0B030', 'c': '#9A6A10',                      # gold clamp
})


def rotor(c, u, v, n):
    """Rotor n x 1 x n: transparent disc with a plus shaped blade and a darker hub."""
    f = box(c, u, v, n, 1, n, ('a', 'b', 'c', 'k'))
    m = n // 2 - 1
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, rh, '.')
        c.rect(x, y + m, rw, 2, 'b')
        c.rect(x + m, y, 2, rh, 'b')
        c.rect(x + m, y + m, 2, 2, 'c')
        c.rect(x, y + m, rw, 1, 'a')
        c.set(x, y + m, 'c').set(x + rw - 1, y + m + 1, 'c')


def hauler_sheet(tier):
    """Flat body with hazard stripes and status lights, a winch hub, a cable and claw, rotors on posts and a cyan sensor.
    Returns (main, glow, active glow): the active sheet lights the status lights and the hub ring while it carries."""
    c = Canvas(64)
    g = Canvas(64)
    ga = Canvas(64)
    # body 8x3x8 at (0,0): hazard stripes round the sides, rivets on top, a hatch underneath
    f = box(c, 0, 0, 8, 3, 8)
    for name in ('west', 'east', 'north', 'south'):
        x, y, rw, rh = f[name]
        for dx in range(rw):
            if (dx + (0 if tier == 1 else 1)) % 4 < 2:
                c.set(x + dx, y + 1, 'k')
    for dx, dy in ((1, 1), (6, 1), (1, 6), (6, 6)):
        put(c, f['top'], dx, dy, 'R')
    block(c, f['bottom'], 2, 2, 4, 4, 'c')
    for name, xs in (('west', (3, 4)), ('east', (3, 4)), ('south', (2, 5))):   # status lights, lit while carrying
        for dx in xs:
            put(c, f[name], dx, 1, 'y')
            put(ga, f[name], dx, 1, 'Y')
    # hub 4x2x4 at (0,14): steel drum with a dark band of light dots
    f = box(c, 0, 14, 4, 2, 4, ('a', 'b', 'c', 'k'))
    for name in ('west', 'east', 'north', 'south'):
        block(c, f[name], 0, 1, 4, 1, 'c')
        for dx in (1, 2):
            put(c, f[name], dx, 1, 'y')
            put(ga, f[name], dx, 1, 'Y' if dx == 1 else 'z')
    block(c, f['bottom'], 1, 1, 2, 2, 'k')
    # claw collar 3x1x3 at (16,14), cable 1x1x1 at (28,14)
    f = box(c, 16, 14, 3, 1, 3, ('a', 'b', 'c', 'k'))
    put(c, f['bottom'], 1, 1, 'k')
    for name in ('north', 'south', 'west', 'east'):
        put(c, f[name], 1, 0, 'y')
        put(ga, f[name], 1, 0, 'Y')
    f = rects(28, 14, 1, 1, 1)
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 'k' if name in ('north', 'south') else 'c')
    # fingers 1x5x1 at (32,0): trim with a dark hooked tip
    f = box(c, 32, 0, 1, 5, 1, ('a', 'b', 'c', 'k'))
    for name in ('west', 'east', 'north', 'south'):
        put(c, f[name], 0, 4, 'k')
        put(c, f[name], 0, 0, 'a')
    # posts 2x3x2 at (32,8)
    box(c, 32, 8, 2, 3, 2, ('a', 'b', 'c', 'k'))
    # sensor 3x2x1 at (40,0): cyan lens
    f = box(c, 40, 0, 3, 2, 1, ('4', 'c', 'c', 'k'))
    block(c, f['north'], 0, 0, 3, 2, 'k')
    block(c, f['north'], 0, 0, 3, 1, 'Y')
    put(c, f['north'], 1, 0, 'z')
    block(g, f['north'], 0, 0, 3, 1, 'Y')
    put(g, f['north'], 1, 0, 'z')
    # rotors 6x1x6 at (0,26) (Mk1) and 8x1x8 at (0,36) (Mk2)
    rotor(c, 0, 26, 6)
    rotor(c, 0, 36, 8)
    # Mk2 winch drums 2x2x3 at (36,14): trim drums with cable wound round them
    f = box(c, 36, 14, 2, 2, 3, ('a', 'b', 'c', 'k'))
    for name in ('top', 'bottom', 'north', 'south'):
        x, y, rw, rh = f[name]
        for yy in range(rh):
            for xx in range(rw):
                if (xx + yy) % 2 == 0:
                    c.set(x + xx, y + yy, 'k')
    # Mk2 antenna: rod 1x4x1 at (48,0), cyan tip 2x1x2 at (52,0)
    box(c, 48, 0, 1, 4, 1, ('b', 'c', 'c', 'k'))
    f = rects(52, 0, 2, 1, 2)
    for name, (x, y, rw, rh) in f.items():
        c.rect(x, y, rw, rh, 'Y')
        g.rect(x, y, rw, rh, 'Y')
        ga.rect(x, y, rw, rh, 'z')
    # the sensor stays lit in the active sheet too
    sx, sy, _, _ = rects(40, 0, 3, 2, 1)['north']
    ga.rect(sx, sy, 3, 1, 'Y').set(sx + 1, sy, 'z')
    return c, g, ga


HAULER_YELLOW = ('#5E4210', '#9C6E14', '#D9A62E', '#F2C94C', '#FFF0B0')
HAULER_ICON1 = ipal(HAULER_YELLOW, 'steel')
HAULER_ICON2 = ipal(DSTEEL, 'gold')


def hauler_icon(mk2):
    """Front view: two rotors on posts, a flat hull with hazard stripes and a cyan sensor, a winch hub and an open clamp."""
    def draw(c):
        for x0 in (1, 10):                                                    # rotor blades and posts
            c.rect(x0, 2, 5, 1, 'c').rect(x0, 2, 2, 1, 'd')
            c.rect(x0 + 2, 3, 1, 2, 'b')
        c.rect(2, 5, 12, 3, '3')                                              # hull
        c.auto_shade({'3': ('2', '4')})
        for x in range(3, 13, 3):                                             # hazard stripes
            c.set(x, 7, 'k')
        c.rect(6, 5, 4, 2, 'K').rect(7, 5, 2, 1, 'Y').set(7, 5, 'z')          # sensor
        c.rect(6, 8, 4, 2, 'c').rect(6, 8, 4, 1, 'd')                         # winch hub
        c.set(7, 9, 'y' if mk2 else 'b').set(8, 9, 'y' if mk2 else 'b')
        c.draw(3, 10, ['bccccccccb', 'c........c', 'c........c', '.d......d.'])   # open clamp
    return draw


def main():
    ENTITY.mkdir(parents=True, exist_ok=True)
    for pal, suffix in ((HAULER1, ''), (HAULER2, '_mk2')):
        c, g, ga = hauler_sheet(1 if not suffix else 2)
        write_png(ENTITY / f'hauler_drone{suffix}.png', c.rows(), pal, 64)
        if not suffix:
            write_png(ENTITY / 'hauler_drone_glow.png', g.rows(), pal, 64)
            write_png(ENTITY / 'hauler_drone_glow_active.png', ga.rows(), pal, 64)
    write_item('hauler_drone', icon(hauler_icon(False)).rows(), HAULER_ICON1)
    write_item('hauler_drone_mk2', icon(hauler_icon(True)).rows(), HAULER_ICON2)
    for tier in (1, 2, 3, 4):
        suffix = '' if tier == 1 else f'_mk{tier}'
        pal = buddy_pal(tier)
        c, g, e = buddy_sheet(tier)
        write_png(ENTITY / f'buddy_bot{suffix}.png', c.rows(), pal, 64)
        if tier != 2:                                               # Mk2 shares the Mk1 glow sheet
            write_png(ENTITY / f'buddy_bot{suffix}_glow.png', g.rows(), pal, 64)
        if tier == 1:
            for stance in BUDDY_EYES:
                write_png(ENTITY / f'buddy_bot_eye_{stance}.png', e.rows(), eye_pal(stance), 64)
        write_item(f'buddy_bot{suffix}', icon(buddy_icon(tier)).rows(), pal)

    for pal, suffix in ((COURIER1, ''), (COURIER2, '_mk2')):
        c, g = courier_sheet(1 if not suffix else 2)
        write_png(ENTITY / f'courier_drone{suffix}.png', c.rows(), pal, 64)
        if not suffix:
            write_png(ENTITY / 'courier_drone_glow.png', g.rows(), pal, 64)
    write_item('courier_drone', icon(courier_icon(False)).rows(), COURIER_ICON1)
    write_item('courier_drone_mk2', icon(courier_icon(True)).rows(), COURIER_ICON2)
    write_item('courier_remote', courier_remote_icon().rows(), COURIER_ICON1)
    print('drone textures written')


if __name__ == '__main__':
    main()
