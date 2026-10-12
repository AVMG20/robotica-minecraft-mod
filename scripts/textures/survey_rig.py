"""Survey Rig textures: a scanner dish on a tower on a plinth. Run: python3 scripts/textures/survey_rig.py

Industrial grey body (the Excavator's), tier metal brackets and dish rim (machine_casing.PROCESSING_TIERS: copper,
gold, magma, null), amber work lights (the plinth's light strip and the dish's feed lamp, at standby while idle), in
the casing's palette chars. The tower front carries a depth display (strata with ore blips) in a bezel.
Working: cyan scan rings ripple out across the dish from the feed, which flashes amber as it sends each ping; a scan
bar sweeps across the depth display and its ore blips flare as it passes; a light wave runs round the plinth strip
and cyan energy climbs the tower sides. Every working loop is 32 frames x 2 ticks.
The model is scripts/data/automation_survey_rig.py; faces sample the default uv (element position) unless noted.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, write_anim, write_block  # noqa: E402
import machine_casing as casing  # noqa: E402

SR_GREY = ('#2A2C2F', '#43464A', '#62666B', '#8A8F95', '#B9BEC3')        # industrial grey (as the Excavator)
SR_EXTRA = dict(zip('abcde', SR_GREY))
SR_EXTRA.update(zip('12345', MATERIALS['steel']))                         # dish and mast steel
SR_EXTRA.update({
    'q': '#244E58', 'Q': '#2F6672', 'y': '#2A8696',                         # display glass, dim scan
    '6': '#4A2E08', '7': '#8A5410', '8': '#B8761A',                        # amber: dark, standby
    'A': '#D88A12', 'B': '#FFB21E', 'C': '#FFE08A', 'D': '#FFFBE6',        # amber lit
    'o': '#7A6018', 'O': '#FFD23A',                                        # gold blip dim / lit
    'r': '#6A1E18', 'R': '#FF4A3A',                                        # redstone
    'n': '#1A6A40', 'N': '#4CFF8A',                                        # emerald
    's': '#2E7280', 'S': '#B8FCFF',                                        # diamond
})
SR_FRAMES, SR_FRAMETIME = 32, 2
SR_DISH_R = 5.0                                                           # dish radius (centre 7.5, 7.5)
SR_GLASS = (4, 7, 8, 3)                                                   # display glass x, y, w, h (front texture)
SR_BLIPS = ((5, 8, 'o', 'O'), (7, 9, 'r', 'R'), (9, 7, 's', 'S'), (10, 9, 'n', 'N'))


def pal(tier=1):
    return casing.casing_palette(casing.PROCESSING_TIERS[tier], **SR_EXTRA)


def ease(t):
    return t * t * (3 - 2 * t)


def brackets(c, x0, y0, x1, y1, n=3):
    """Tier metal L brackets on the four corners of the box x0..x1, y0..y1 (inclusive), light from the top left."""
    for x, sx in ((x0, 1), (x1, -1)):
        for y, sy in ((y0, 1), (y1, -1)):
            for i in range(n):
                c.set(x + sx * i, y, 'h').set(x, y + sy * i, 'h')
            c.set(x + sx * (n - 1), y, 'g').set(x, y + sy * (n - 1), 'g')
    c.set(x0, y0, 'j').set(x0 + 1, y0, 'i').set(x0, y0 + 1, 'i').set(x1, y1, 'f')
    return c


# ---------------------------------------------------------------- plinth

def base_side(tier):
    """Plinth sides (rows 12-15): the amber work light strip in a recessed band, tier brackets, Mk marks."""
    c = Canvas()
    c.rect(0, 12, 16, 4, 'c')
    c.rect(0, 12, 16, 1, 'e').rect(0, 15, 16, 1, 'a')
    c.rect(3, 13, 10, 2, 'b').rect(3, 13, 10, 1, 'a')
    c.rect(4, 13, 8, 1, '7').set(4, 13, '6').set(11, 13, '6')
    brackets(c, 0, 12, 15, 15, 3)
    for i in range(tier):
        c.set(5 + i * 2, 14, 'i')
    return c


def base_glow(frame=None):
    """The plinth strip: standby amber while idle; working, a bright wave runs along it."""
    c = Canvas()
    if frame is None:
        return c.rect(4, 13, 8, 1, '8').set(4, 13, '7').set(11, 13, '7')
    head = 4 + 8 * frame / SR_FRAMES
    for x in range(4, 12):
        d = min((x - head) % 8, (head - x) % 8)
        c.set(x, 13, 'D' if d < 0.75 else 'C' if d < 1.75 else 'B')
    return c


def base_top(tier):
    """Plinth top: the rim round the tower (2..13) with tier metal corner plates."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(0, 0, 16, 16, 'e', 'a')
    c.rect(2, 2, 12, 12, 'a')
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.rect(x, y, 2, 2, 'h')
    c.set(0, 0, 'j').set(15, 15, 'f')
    return c


def bottom():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b').bevel(0, 0, 16, 16, 'c', 'a')
    c.rect(4, 4, 8, 8, 'a').bevel(4, 4, 8, 8, 'K', 'c')
    return c


# ---------------------------------------------------------------- tower

def tower_side(tier):
    """Tower sides (cols 2-13, rows 5-11): an energy channel between cooling slats, tier brackets."""
    c = Canvas()
    c.rect(2, 5, 12, 7, 'c')
    c.rect(2, 5, 12, 1, 'e').rect(2, 11, 12, 1, 'a')
    for y in (7, 9):
        c.rect(4, y, 2, 1, 'a').rect(10, y, 2, 1, 'a')
        c.rect(4, y + 1, 2, 1, 'd').rect(10, y + 1, 2, 1, 'd')
    c.rect(6, 6, 4, 5, 'b').rect(6, 6, 1, 5, 'a').rect(9, 6, 1, 5, 'd')
    c.rect(7, 6, 2, 5, 'u')
    return brackets(c, 2, 5, 13, 11, 3)


def channel_glow(frame):
    """Side channels lit cyan, a bright pulse climbing them twice per loop."""
    c = Canvas()
    head = 10 - (frame * 12 // SR_FRAMES) % 6
    for y in range(6, 11):
        d = (y - head) % 6
        c.rect(7, y, 2, 1, 'x' if d == 0 else 'w' if d in (1, 5) else 'v')
    return c


def tower_front(tier):
    """Tower front behind the display (cols 2-13, rows 5-11)."""
    c = Canvas()
    c.rect(2, 5, 12, 7, 'b')
    c.rect(2, 5, 12, 1, 'e').rect(2, 11, 12, 1, 'a')
    return brackets(c, 2, 5, 13, 11, 3)


def tower_top(tier):
    """Tower top round the mast (2..13): a grey deck with vents."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.bevel(2, 2, 12, 12, 'e', 'a')
    for x in (4, 10):
        for y in range(4, 12, 2):
            c.rect(x, y, 2, 1, 'a')
    return brackets(c, 2, 2, 13, 13, 2)


# ---------------------------------------------------------------- depth display

def screen(lit):
    """The display (front cols 3-12, rows 6-10): a bezel round tinted glass with strata and dim ore blips, the status
    lamp in the lower bezel; everything else is bezel grey (the sides of the 1 px deep display)."""
    x, y, w, h = SR_GLASS
    c = Canvas()
    c.rect(0, 0, 16, 16, 'b')
    c.rect(3, 6, 10, 5, 'c').rect(3, 6, 10, 1, 'e').rect(3, 10, 10, 1, 'a').rect(12, 6, 1, 5, 'b')
    for row in range(h):
        c.rect(x, y + row, w, 1, 'Q' if row % 2 == 0 else 'q')
    for bx, by, dim, _ in SR_BLIPS:
        c.set(bx, by, dim)
    c.set(11, 10, 'L' if lit else 'l')
    return c


def screen_glow(frame):
    """A scan bar sweeps across the glass (eased, then rests while the blips fade), each blip flaring as it passes."""
    x, y, w, h = SR_GLASS

    def bar(t):
        return x - 0.5 + (w + 0.5) * ease(min(1.0, t / 0.7))

    c = Canvas()
    t = frame / SR_FRAMES
    pos = bar(t)
    for col in range(x, x + w):
        d = pos - col
        ch = 'w' if -0.5 <= d < 0.5 else 'v' if 0.5 <= d < 1.5 else 'y' if 1.5 <= d < 3 else None
        if ch:
            c.rect(col, y, 1, h, ch)
    for bx, by, dim, lit in SR_BLIPS:
        hit = next(i / SR_FRAMES for i in range(SR_FRAMES) if bar(i / SR_FRAMES) >= bx)
        age = (t - hit) % 1
        c.set(bx, by, 'x' if age < 0.07 else lit if age < 0.5 else dim)
    c.set(11, 10, 'L')
    return c


# ---------------------------------------------------------------- dish

def dish_dist(x, y):
    return math.hypot(x - 7.5, y - 7.5)


def dish(tier):
    """Dish face (a disc in cols/rows 3-12, transparent round it): a tier metal rim, the concave steel bowl lit on
    its far side, the feed mount."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = dish_dist(x, y)
            lean = (x - 7.5) + (y - 7.5)
            if d >= SR_DISH_R:
                continue
            if d >= SR_DISH_R - 0.85:
                c.set(x, y, 'i' if lean < -2 else 'g' if lean > 2 else 'h')
            elif d >= 3.4:
                c.set(x, y, '4' if lean > 0.5 else '3')
            else:
                c.set(x, y, '3' if lean > 0.5 else '2')
    c.rect(7, 7, 2, 2, '1')
    return c


def dish_back():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if dish_dist(x, y) < SR_DISH_R:
                c.set(x, y, 'b' if dish_dist(x, y) > 2 else 'c')
    return c


def dish_glow(frame):
    """Two cyan rings rippling out from the feed across the bowl, fading as they reach the rim."""
    c = Canvas()
    for k in range(2):
        r = 1.0 + ((frame / SR_FRAMES + k / 2) % 1) * (SR_DISH_R - 1.4)
        for y in range(16):
            for x in range(16):
                d = dish_dist(x, y)
                if abs(d - r) < 0.55 and d < SR_DISH_R - 0.85:
                    c.set(x, y, 'x' if r < 2 else 'w' if r < 3 else 'v' if r < 3.8 else 'y')
    return c


def feed(frame=None):
    """Feed lamp (the dish's emitter, sides sample 0..2 x 0..1.5): standby amber while idle; working it flashes as
    each ring leaves it and dims between pings."""
    c = Canvas()
    if frame is None:
        return c.rect(0, 0, 16, 16, '7').rect(0, 0, 2, 1, '8')
    ph = (frame / SR_FRAMES * 2) % 1
    lv = 'DCBA'[min(3, int(ph * 5))]
    return c.rect(0, 0, 16, 16, lv).rect(0, 0, 2, 1, 'D' if lv in 'DC' else 'C')


def mast():
    """Mast steel, lit on its left."""
    c = Canvas()
    for x in range(16):
        c.rect(x, 0, 1, 16, '4' if x % 3 == 0 else '3' if x % 3 == 1 else '2')
    return c


def main():
    for tier in (1, 2, 3, 4):
        p = pal(tier)
        write_block(f'survey_rig_mk{tier}_base_side', base_side(tier).rows(), p)
        write_block(f'survey_rig_mk{tier}_base_top', base_top(tier).rows(), p)
        write_block(f'survey_rig_mk{tier}_side', tower_side(tier).rows(), p)
        write_block(f'survey_rig_mk{tier}_front', tower_front(tier).rows(), p)
        write_block(f'survey_rig_mk{tier}_top', tower_top(tier).rows(), p)
        write_block(f'survey_rig_mk{tier}_dish', dish(tier).rows(), p)
    p = pal()
    stills = {'bottom': bottom(), 'screen': screen(False), 'screen_on': screen(True), 'dish_back': dish_back(),
              'feed': feed(), 'mast': mast(), 'base_glow': base_glow()}
    for name, c in stills.items():
        write_block(f'survey_rig_{name}', c.rows(), p)
    frames = range(SR_FRAMES)
    for name, fn in (('base_glow_on', base_glow), ('channel_glow', channel_glow), ('screen_glow', screen_glow),
                     ('dish_glow', dish_glow), ('feed_on', feed)):
        write_anim('block', f'survey_rig_{name}', [fn(i) for i in frames], p, frametime=SR_FRAMETIME,
                   interpolate=name != 'channel_glow')


if __name__ == '__main__':
    main()
