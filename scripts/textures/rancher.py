"""Rancher textures (automation module): the 64x64 model sheets of Mk1 and Mk2, the glow sheet (eyes only) and both item icons.
Run: python3 scripts/textures/rancher.py
Writes textures/entity/rancher.png, rancher_mk2.png, rancher_glow.png (eyes), rancher_light.png (status lamps, white: the renderer
tints them by status) and textures/item/rancher[_mk2].png (+ item models when missing).
The sheet follows automation/client/RancherModel: texOffs(u, v), box w x h x d unfolds as
    top (u+d, v)  bottom (u+d+w, v)  west (u, v+d)  north/front (u+d, v+d)  east (u+d+w, v+d)  south (u+2d+w, v+d)
Mk1: steel head, blue overall body, copper trim, straw hat. Mk2: dark steel, green body, gold trim, hat with a red band, and its
own parts (twin tanks, antenna, badge) on the same sheet.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, Canvas, write_item, write_png  # noqa: E402

ENTITY = ASSETS / 'textures/entity'

# 1-4 head/limb steel (light -> dark), 5-8 body paint, a-c trim, h/H/j straw, d hat band, k near black, y/Y/z cyan eyes
MK1 = {
    '1': '#D8DEE0', '2': '#A9B3B7', '3': '#7C878C', '4': '#4D5558',
    '5': '#7FA8E0', '6': '#4A78B8', '7': '#345A8E', '8': '#223C62',
    'a': '#E8A060', 'b': '#C87533', 'c': '#8A4A22',
    'h': '#F2D27A', 'H': '#D4A73A', 'j': '#9C7A22', 'd': '#8A4A22',
    'k': '#1E1A1A', 'y': '#2FB8CC', 'Y': '#5FE3F0', 'z': '#D8FBFF',
    'g': '#3A4A3E', 'W': '#FFFFFF',
}
MK2 = dict(MK1)
MK2.update({
    '1': '#7E8A90', '2': '#566067', '3': '#3F484E', '4': '#2A3034',
    '5': '#7FD068', '6': '#3E9E3A', '7': '#1E6B2E', '8': '#0E3A1A',
    'a': '#FFE07A', 'b': '#E8B530', 'c': '#B07E12',
    'd': '#C9302A',
})


def rects(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'east': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def box(c, u, v, w, h, d, ramp):
    """Fills the unfolded faces: light top, mid sides, dark bottom, 1 px bevel on faces of 3 px and more."""
    light, mid, dark, deep = ramp
    faces = rects(u, v, w, h, d)
    for name, (x, y, rw, rh) in faces.items():
        base = light if name == 'top' else deep if name == 'bottom' else mid
        c.rect(x, y, rw, rh, base)
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
    return faces


def put(c, face, dx, dy, ch):
    x, y, rw, rh = face
    if 0 <= dx < rw and 0 <= dy < rh:
        c.set(x + dx, y + dy, ch)


def block(c, face, dx, dy, w, h, ch):
    for yy in range(h):
        for xx in range(w):
            put(c, face, dx + xx, dy + yy, ch)


def straw(c, face, seed):
    """Woven straw: alternating light and dark rows with a few offset stitches."""
    x, y, rw, rh = face
    for yy in range(rh):
        for xx in range(rw):
            ch = 'h' if (yy + (xx + seed) // 2) % 2 == 0 else 'H'
            c.set(x + xx, y + yy, ch)


def sheet():
    """Returns (main, eye glow, lamp light) canvases."""
    c = Canvas(64)
    g = Canvas(64)
    lt = Canvas(64)
    # head 6x5x6 at (0,0): steel, a visor with two round cyan eyes, a little speaker grille
    f = box(c, 0, 0, 6, 5, 6, ('1', '2', '3', '4'))
    block(c, f['north'], 0, 1, 6, 2, 'k')
    for dx in (1, 4):
        put(c, f['north'], dx, 1, 'Y')
        put(c, f['north'], dx, 2, 'y')
        put(g, f['north'], dx, 1, 'Y')
        put(g, f['north'], dx, 2, 'y')
    put(c, f['north'], 1, 1, 'z')
    put(g, f['north'], 1, 1, 'z')
    for dx in (2, 3):
        put(c, f['north'], dx, 4, '4')
    for name in ('west', 'east'):
        block(c, f[name], 2, 1, 2, 2, 'b')
        put(c, f[name], 2, 1, 'a')
    # hat crown 6x2x6 at (24,0): straw with a band
    f = rects(24, 0, 6, 2, 6)
    for name, face in f.items():
        straw(c, face, len(name))
    for name in ('west', 'east', 'north', 'south'):
        block(c, f[name], 0, 1, 6, 1, 'd')
    # brim 10x1x10 at (0,44): straw, darker rim
    f = rects(0, 44, 10, 1, 10)
    for name, face in f.items():
        straw(c, face, len(name) + 1)
    for name in ('top', 'bottom'):
        x, y, rw, rh = f[name]
        c.frame(x, y, rw, rh, 'j')
    # body 8x7x5 at (0,12): painted overalls with a copper bib plate, two buttons and a belt
    f = box(c, 0, 12, 8, 7, 5, ('5', '6', '7', '8'))
    block(c, f['north'], 2, 1, 4, 3, 'b')
    block(c, f['north'], 3, 2, 2, 1, 'a')
    put(c, f['north'], 1, 1, 'a')
    put(c, f['north'], 6, 1, 'a')
    for name in ('north', 'south', 'west', 'east'):
        x, y, rw, rh = f[name]
        c.rect(x, y + 5, rw, 1, 'c')
    put(c, f['north'], 3, 5, 'a')
    put(c, f['north'], 4, 5, 'a')
    block(c, f['south'], 1, 1, 6, 3, '7')
    # arms 2x7x2 at (40,12) and (48,12): steel with a copper elbow ring, dark grabber hands
    for u in (40, 48):
        f = box(c, u, 12, 2, 7, 2, ('1', '2', '3', '4'))
        for name in ('north', 'south', 'west', 'east'):
            x, y, rw, rh = f[name]
            c.rect(x, y + 3, rw, 1, 'b')
            c.rect(x, y + 5, rw, 2, '4')
        block(c, f['bottom'], 0, 0, 2, 2, 'k')
    # legs 3x6x3 at (0,24) and (12,24): painted thighs, steel shins, dark boots
    for u in (0, 12):
        f = box(c, u, 24, 3, 6, 3, ('5', '6', '7', '8'))
        for name in ('north', 'south', 'west', 'east'):
            x, y, rw, rh = f[name]
            c.rect(x, y + 2, rw, 2, '2')
            c.rect(x, y + 4, rw, 2, '4')
            c.rect(x, y + 5, rw, 1, 'k')
    # feed tank 6x5x2 at (28,24): copper tank with a glass strip showing grain
    f = box(c, 28, 24, 6, 5, 2, ('a', 'b', 'c', 'c'))
    block(c, f['south'], 1, 1, 4, 3, 'k')
    block(c, f['south'], 2, 2, 2, 2, 'H')
    put(c, f['south'], 2, 2, 'h')
    # status lamp 2x1x1 at (48,6) on the chest: copper housing, dark glass, white in the light sheet
    f = box(c, 48, 6, 2, 1, 1, ('a', 'b', 'c', 'c'))
    for name in ('north', 'top', 'west', 'east'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, rh, 'g')
        lt.rect(x, y, rw, rh, 'W')
    # eyelid 6x2x1 at (26,16): steel, darker lower edge
    f = box(c, 26, 16, 6, 2, 1, ('1', '2', '3', '4'))
    x, y, rw, rh = f['north']
    c.rect(x, y, rw, 1, '2').rect(x, y + 1, rw, 1, '3')
    # Mk2 twin tanks 3x6x3 at (44,24): trim tanks with a glass strip of grain
    f = box(c, 44, 24, 3, 6, 3, ('a', 'b', 'c', 'c'))
    for name in ('south', 'west', 'east'):
        block(c, f[name], 1, 1, 1, 4, 'k')
        block(c, f[name], 1, 3, 1, 2, 'H')
    for name in ('north', 'south', 'west', 'east'):
        x, y, rw, rh = f[name]
        c.rect(x, y, rw, 1, 'a')
    # Mk2 antenna: rod 1x4x1 at (48,0), lamp bulb 2x2x2 at (52,0)
    box(c, 48, 0, 1, 4, 1, ('2', '3', '4', '4'))
    for name, (x, y, rw, rh) in rects(52, 0, 2, 2, 2).items():
        c.rect(x, y, rw, rh, 'g')
        lt.rect(x, y, rw, rh, 'W')
    # Mk2 badge 2x2x1 at (26,12): trim star plate
    f = box(c, 26, 12, 2, 2, 1, ('a', 'b', 'c', 'c'))
    x, y, rw, rh = f['north']
    c.set(x, y, 'a').set(x + 1, y, 'b').set(x, y + 1, 'b').set(x + 1, y + 1, 'c')
    return c, g, lt


def icon(mk2):
    """Front view: straw hat, steel head with cyan eyes, painted body with a bib plate, arms and legs."""
    c = Canvas(16)
    c.rect(2, 3, 12, 1, 'H').rect(3, 3, 10, 1, 'h')       # brim
    c.rect(5, 1, 6, 2, 'h').rect(5, 2, 6, 1, 'd')         # crown and band
    c.set(6, 1, 'H').set(9, 1, 'H')
    c.rect(5, 4, 6, 4, '2').rect(5, 4, 6, 1, '1').rect(5, 7, 6, 1, '3')   # head
    c.rect(5, 5, 6, 1, 'k').set(6, 5, 'Y').set(9, 5, 'Y').set(7, 6, '4').set(8, 6, '4')
    c.rect(5, 8, 6, 4, '6').rect(5, 8, 6, 1, '5')                          # body
    c.rect(6, 8, 4, 2, 'b').rect(7, 9, 2, 1, 'a')                          # bib plate
    c.rect(5, 11, 6, 1, 'c')                                               # belt
    c.rect(2, 8, 2, 4, '2').rect(2, 8, 2, 1, '1').rect(2, 11, 2, 1, '4')   # arms
    c.rect(12, 8, 2, 4, '2').rect(12, 8, 2, 1, '1').rect(12, 11, 2, 1, '4')
    c.rect(5, 12, 2, 3, '3').rect(9, 12, 2, 3, '3').rect(5, 14, 2, 1, 'k').rect(9, 14, 2, 1, 'k')   # legs
    if mk2:
        c.set(2, 9, 'a').set(13, 9, 'a')                                   # gold elbow studs
    c.outline('k')
    return c


def main():
    ENTITY.mkdir(parents=True, exist_ok=True)
    c, g, lt = sheet()
    write_png(ENTITY / 'rancher.png', c.rows(), MK1, 64)
    write_png(ENTITY / 'rancher_mk2.png', c.rows(), MK2, 64)
    write_png(ENTITY / 'rancher_glow.png', g.rows(), MK1, 64)
    write_png(ENTITY / 'rancher_light.png', lt.rows(), MK1, 64)
    write_item('rancher', icon(False).rows(), MK1)
    write_item('rancher_mk2', icon(True).rows(), MK2)
    print('rancher textures written')


if __name__ == '__main__':
    main()
