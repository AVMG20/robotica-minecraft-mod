"""Automation textures: robot body atlases (Stumpy, Sprout), shared robot parts, Mk tier accents, Excavator,
Supply Crate and the Mk farm kits.
Run: python3 scripts/textures/automation.py

<robot>_body.png is a 32x32 atlas at the same texel density as a 16x16 texture (the models address it with uv = texel / 2):
body faces, head faces, battery pack and the eye. The small part textures (automation_*) keep an 8 pixel period so the
default uvs of tiny elements (arms, tools, hip) still land on plating.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, Canvas, grain, material, write_anim, write_block, write_item, write_png  # noqa: E402

CU, BR, ST, DK, WD, GR, AC = '01234', '56789', 'abcde', 'fghij', 'uvwxy', 'lmnop', 'ABCDE'
BASE = {'k': '#16191B', 'V': '#0C1418', 'W': '#3A5560',
        'Y': '#7FEFF8', 'y': '#2FB8CC', 'z': '#F0FFFF', 'Z': '#16505A',
        'R': '#FF5A3A', 'r': '#7A1E14', 'T': '#FFB21E', 'H': '#2A2418'}
BASE.update(material(CU, 'copper'))
BASE.update(material(BR, 'brass'))
BASE.update(material(ST, 'steel'))
BASE.update(material(DK, 'dark'))
BASE.update(material(WD, 'wood'))
BASE.update(material(GR, 'green'))
BASE.update(grain('sS', 'steel'))
BASE.update(grain('tU', 'brass'))
BASE.update(grain('cC', 'copper'))
GRAINS = {CU: 'cC', BR: 'tU', ST: 'sS'}
TIERS = {1: 'copper', 2: 'iron', 3: 'gold', 4: 'teal'}


def pal(accent='copper', **extra):
    out = dict(BASE)
    out.update(material(AC, accent))
    out.update(extra)
    return out


def plate(c, x, y, w, h, r, seed=0, **kw):
    return c.plate(x, y, w, h, r, seed, grain=GRAINS.get(r), **kw)


# ---------------------------------------------------------------- robot atlas
# Region table (texels in the 32x32 atlas): name -> (u, v, w, h). Shared with scripts/data/automation_data.py.
ATLAS = {
    'body_front': (0, 0, 8, 6), 'body_back': (8, 0, 8, 6), 'body_side': (16, 0, 8, 6),
    'body_top': (24, 0, 8, 8), 'body_bottom': (24, 8, 8, 8),
    'head_front': (0, 6, 9, 6), 'head_back': (9, 6, 9, 6),
    'head_side': (0, 12, 7, 6), 'head_top': (7, 12, 9, 7), 'head_bottom': (16, 12, 9, 7),
    'pack_back': (0, 19, 6, 5), 'pack_side': (6, 19, 2, 5), 'pack_top': (8, 19, 6, 2),
    'eye': (14, 19, 2, 2),
    'tread_side': (0, 24, 10, 3), 'tread_top': (28, 16, 3, 10), 'tread_end': (13, 24, 3, 3),
}


def robot_atlas(kind):
    """kind 'stumpy' (copper lumberjack) or 'sprout' (brass gardener)."""
    r = CU if kind == 'stumpy' else BR
    c = Canvas(32)

    # body front: a riveted chest plate with a little status window
    u, v, w, h = ATLAS['body_front']
    plate(c, u, v, w, h, r, 1, density=0.25)
    if kind == 'stumpy':
        c.draw(u + 2, v + 1, ['.88.', '8776', '.65.'])                   # brass drive hub
        c.set(u + 1, v + 1, r[4]).set(u + 6, v + 1, r[4])
    else:
        c.draw(u + 2, v + 1, ['.mo.', 'mopm', '.mn.'])                   # leaf emblem
        c.set(u + 1, v + 1, r[4]).set(u + 6, v + 1, r[4])
    # body back (mostly behind the battery pack): vents
    u, v, w, h = ATLAS['body_back']
    plate(c, u, v, w, h, r, 2)
    c.vents(u + 1, v + 1, w - 2, 3, r, 2)
    # body sides: shoulder socket where the arm joins
    u, v, w, h = ATLAS['body_side']
    plate(c, u, v, w, h, r, 3)
    c.draw(u + 2, v + 0, ['.88.', '8776', '8765', '.65.'])
    c.set(u + 6, v + 4, r[4])
    # body top / bottom
    u, v, w, h = ATLAS['body_top']
    plate(c, u, v, w, h, r, 4)
    c.rivets(u, v, w, h, r)
    u, v, w, h = ATLAS['body_bottom']
    plate(c, u, v, w, h, DK, 5, brushed=False)

    # head front: big dark visor (the eyes are separate glowing elements in front of it) and cheek bolts
    u, v, w, h = ATLAS['head_front']
    plate(c, u, v, w, h, r, 6, density=0.15)
    c.rect(u + 1, v + 1, 7, 4, 'V').bevel(u + 1, v + 1, 7, 4, 'k', 'W')
    c.set(u + 2, v + 2, 'W')
    if kind == 'sprout':
        c.set(u + 1, v + 1, r[2]).set(u + 7, v + 1, r[2])                # rounder visor
        c.set(u + 1, v + 4, r[2]).set(u + 7, v + 4, r[2])
    c.set(u + 4, v + 5, r[1]).set(u + 3, v + 5, r[3])                    # chin seam
    # head back: two cooling slots and a serial plate
    u, v, w, h = ATLAS['head_back']
    plate(c, u, v, w, h, r, 7)
    c.vents(u + 2, v + 1, 5, 3, r, 2)
    c.rect(u + 3, v + 4, 3, 1, '6')
    # head side: round ear bolt with a highlight
    u, v, w, h = ATLAS['head_side']
    plate(c, u, v, w, h, r, 8)
    c.draw(u + 2, v + 1, ['.bb.', 'bdcb', 'bcca', '.aa.'])
    # head top: hatch seam and the antenna socket
    u, v, w, h = ATLAS['head_top']
    plate(c, u, v, w, h, r, 9)
    c.rect(u + 2, v + 1, 5, 1, r[1]).rect(u + 2, v + 1, 1, 5, r[1])
    c.rect(u + 3, v + 2, 4, 4, r[3]).rect(u + 3, v + 5, 4, 1, r[1])
    c.draw(u + 4, v + 3, ['76', '65'])
    u, v, w, h = ATLAS['head_bottom']
    plate(c, u, v, w, h, r, 10, brushed=False)

    # battery pack (back): dark case, copper terminals, a cyan charge strip
    u, v, w, h = ATLAS['pack_back']
    plate(c, u, v, w, h, DK, 11, brushed=False)
    c.draw(u + 1, v + 1, ['3..3'])                                       # terminals
    c.rect(u + 1, v + 3, 4, 1, 'Z').rect(u + 1, v + 3, 3, 1, 'y')
    u, v, w, h = ATLAS['pack_side']
    c.rect(u, v, w, h, 'g').rect(u, v, 1, h, 'h')
    u, v, w, h = ATLAS['pack_top']
    c.rect(u, v, w, h, 'h').rect(u, v, w, 1, 'i')

    # eye: glowing lens with a white catch light
    u, v, w, h = ATLAS['eye']
    c.draw(u, v, ['zY', 'Yy'])

    # treads: rubber track with lugs, road wheels on the sides
    u, v, w, h = ATLAS['tread_side']
    c.rect(u, v, w, h, 'g')
    c.rect(u, v, w, 1, 'h').rect(u, v + 2, w, 1, 'f')
    for x in range(u + 1, u + w - 1, 3):
        c.draw(x, v, ['ab', 'ba'])
    u, v, w, h = ATLAS['tread_top']
    for y in range(v, v + h):
        c.rect(u, y, w, 1, 'h' if (y - v) % 2 == 0 else 'f')
    u, v, w, h = ATLAS['tread_end']
    c.rect(u, v, w, h, 'g').rect(u, v, w, 1, 'h').set(u + 1, v + 1, 'b')
    return c


# ---------------------------------------------------------------- small part textures (8 px period)

def panels(r, rivets=True, slits=False, seed=0):
    """16x16 of four 8x8 plates: bevel light top/left, dark bottom/right, rivet top-left."""
    c = Canvas()
    for ox in (0, 8):
        for oy in (0, 8):
            plate(c, ox, oy, 8, 8, r, seed + ox + oy * 3, density=0.2)
            if rivets:
                c.rivet(ox + 2, oy + 2, r)
            if slits:
                c.vents(ox + 1, oy + 2, 6, 4, r, 2)
    return c


def wood():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for y in range(16):
        for x in range(16):
            n = (x * 5 + y * 13 + (x // 4) * 7) % 17
            if n < 2:
                c.set(x, y, 'v')
            elif n == 9:
                c.set(x, y, 'x')
    for x in range(0, 16, 4):
        c.rect(x, 0, 1, 16, 'v')
        c.rect(x + 1, 0, 1, 16, 'x')
    return c


def eye():
    """Kept for compatibility: the full glowing lens texture (the robots now use their atlas eye)."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'y').disc(7.5, 7.5, 6, 'Y').disc(5.5, 5.5, 2.2, 'z')
    return c


def saw():
    """Circular saw blade drawn at the 8x8 size it shows on the robot (uv 0-8), tiled 2x2: hooked teeth, polished
    ring and a brass hub, transparent between the teeth."""
    t = Canvas(8)
    teeth = 8

    def inside(x, y):
        dx, dy = x - 3.5, y - 3.5
        r = math.hypot(dx, dy)
        if r <= 2.9:
            return True
        ang = (math.degrees(math.atan2(dy, dx)) + 360) % (360 / teeth)
        return r <= 4.2 and ang < (360 / teeth) * 0.55

    t.shape(inside, lambda x, y: 'd' if x + y < 6 else 'c' if x + y < 9 else 'b')
    t.edge('a')
    t.draw(3, 3, ['97', '75'])
    t.set(2, 2, 'e').set(5, 5, 'a')
    c = Canvas()
    for ox in (0, 8):
        for oy in (0, 8):
            c.paste(t, ox, oy)
    return c


def leaf():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'n')
    for y in range(16):
        for x in range(16):
            k = (x * 3 + y * 5) % 7
            if k == 0:
                c.set(x, y, 'o')
            elif k == 4:
                c.set(x, y, 'm')
    for x, y in ((2, 2), (9, 1), (5, 6), (12, 7), (1, 10), (8, 11), (13, 13), (4, 14)):
        c.set(x, y, 'p')
    return c


def accent():
    """Tier accent: rows 0-5 the antenna bulb (glossy), rows 6-7 the belt with a buckle in the middle."""
    c = Canvas()
    c.rect(0, 0, 16, 6, 'C').rect(0, 0, 16, 1, 'D').rect(0, 5, 16, 1, 'B')
    c.draw(6, 1, ['DE', 'DD', 'CC'])
    c.draw(7, 2, ['E'])
    c.rect(0, 6, 16, 2, 'C').rect(0, 6, 16, 1, 'D').rect(0, 7, 16, 1, 'B')
    for x in range(1, 16, 3):
        c.set(x, 6, 'E')
    c.rect(7, 6, 2, 2, '8').set(7, 6, '9').set(8, 7, '5')               # buckle
    c.rect(0, 8, 16, 8, 'C')
    plate(c, 0, 8, 16, 8, AC, 3, brushed=False)
    return c


def drill():
    """Spiral flutes for the drill cones."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            band = (x + y * 2) % 6
            c.set(x, y, 'e' if band == 0 else 'd' if band < 2 else 'c' if band < 4 else 'b' if band < 5 else 'a')
    return c


def drill_spin(frame_no):
    """Working drill: the flutes shift one step a frame, so the cones seem to turn."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            band = (x + y * 2 + frame_no) % 6
            c.set(x, y, 'e' if band == 0 else 'd' if band < 2 else 'c' if band < 4 else 'b' if band < 5 else 'a')
    return c


def light():
    """Excavator work lamp (the model samples texels 5-8 x 5-7 and draws them full bright)."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'T')
    c.draw(5, 5, ['zY9', '9TT'])
    c.frame(4, 4, 5, 4, '6')
    return c


def light_off():
    """Idle lamp: dark amber, no glare."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '6')
    c.draw(5, 5, ['876', '766'])
    c.frame(4, 4, 5, 4, '5')
    return c


def light_on(frame_no):
    """Working lamp: a slow amber blink with a moving glare."""
    c = light()
    if frame_no % 4 in (2, 3):
        c.draw(5, 5, ['YzT', 'TzT'] if frame_no % 4 == 2 else ['TYz', 'TTz'])
    return c


def excavator_side():
    """Steel housing: the box face samples rows 3-10, cols 1-14 (vents and lamps are separate elements)."""
    c = Canvas()
    plate(c, 0, 0, 16, 16, ST, 21, density=0.25)
    c.rect(1, 3, 14, 1, 'e').rect(1, 10, 14, 1, 'a')
    c.hazard(1, 8, 14, 2, 'T', 'H')
    for x in (2, 13):
        c.rivet(x, 4, ST).rivet(x, 6, ST)
    return c


# Supply Crate Mk1-Mk4: the processing machines' graphite casing (bevelled frame around a sunk panel, chamfered corners
# with tier metal L brackets) as a crate with a lid seam, a latch and cyan accents. Body DK, tier metal AC (pal(TIERS)).
# The cyan pixels (q, Q, Y) are copied onto a full-bright *_glow overlay, so the accents stay lit at night.
CRATE_CYAN = {'q': '#2FB8CC', 'Q': '#8CF4FF'}
# Storage gets a lighter gunmetal body than the dark machine casing, so crates stand apart from machines.
CRATE_BODY = dict(zip(DK, ('#23282E', '#3C434B', '#5D666F', '#86909A', '#B4BDC5')))


def crate_pal(tier=1):
    return pal(TIERS[tier], **CRATE_CYAN, **CRATE_BODY)


def glow_only(c, keep='qQY'):
    out = Canvas()
    for y in range(16):
        for x in range(16):
            if c.get(x, y) in keep:
                out.set(x, y, c.get(x, y))
    return out


def crate_shell(tier):
    """Casing face. Higher Mks carry more metal: Mk3 runs the tier metal down the vertical edges, Mk4 all around."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'h')
    c.rect(1, 1, 14, 14, 'g')
    c.bevel(0, 0, 16, 16, 'i', 'f')                                      # outer edge
    c.bevel(2, 2, 12, 12, 'f', 'i')                                      # panel sunk into the frame
    c.rect(3, 3, 10, 10, 'h')
    if tier >= 3:
        c.rect(0, 4, 2, 8, 'C').rect(14, 4, 2, 8, 'C').rect(0, 4, 1, 8, 'D').rect(15, 4, 1, 8, 'B')
    if tier >= 4:
        c.rect(4, 0, 8, 2, 'C').rect(4, 14, 8, 2, 'C').rect(4, 0, 8, 1, 'D').rect(4, 15, 8, 1, 'B')
    for x, y, sx, sy in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for i in range(5):                                               # 2 px L bracket in the tier metal
            for j in range(2):
                c.set(x + sx * i, y + sy * j, 'C').set(x + sx * j, y + sy * i, 'C')
        if tier < 4:
            c.set(x + sx * 4, y, 'B').set(x + sx * 4, y + sy, 'A')
        if tier < 3:
            c.set(x, y + sy * 4, 'B').set(x + sx, y + sy * 4, 'A')
        c.set(x + sx, y + sy, 'B')
        c.set(x, y, 'V')                                                 # chamfer
    for i in range(1, 4):                                                # light from the top-left
        c.set(i, 0, 'E').set(0, i, 'E')
    return c


def crate_seam(c, y=4):
    """Lid seam across the panel: a dark gap with a cyan strip in it, the lid's lit edge above."""
    c.rect(3, y - 1, 10, 1, 'i')
    c.rect(2, y, 12, 1, 'k')
    c.rect(4, y, 8, 1, 'q').set(3, y, 'Z').set(12, y, 'Z')
    return c


def crate_boards(c, y0=5):
    """Graphite boards below the lid: a dark joint with a lit edge every 3 rows, like a cargo crate."""
    for y in range(y0, 13):
        if (y - y0) % 3 == 0:
            c.rect(3, y, 10, 1, 'g')
        elif (y - y0) % 3 == 1:
            c.rect(3, y, 10, 1, 'i')
    return c


def crate_front(tier):
    """Front: lid seam, boards, a tier metal latch with a cyan lock light and a plate with one lit pip per Mk."""
    c = crate_boards(crate_seam(crate_shell(tier)))
    c.rect(6, 2, 4, 6, 'C').bevel(6, 2, 4, 6, 'D', 'A')                 # latch hasp over the seam
    c.rect(7, 3, 2, 1, 'B')
    c.set(7, 5, 'Y').set(8, 5, 'q')                                      # lock light
    c.rect(7, 6, 2, 1, 'B')
    c.rect(4, 10, 8, 3, 'f').bevel(4, 10, 8, 3, 'k', 'i')               # Mk plate
    for i in range(4):
        c.set(5 + i * 2, 11, 'Y' if i < tier else 'Z')
    return c


def crate_side(tier):
    """Side: lid seam, boards and the crate's diagonal brace in the tier metal."""
    c = crate_boards(crate_seam(crate_shell(tier)))
    lit, mid, dark = 'D', 'C', 'A'
    for i in range(9):                                                   # 2 px brace, bottom-left to top-right
        x, y = 3 + i, 12 - (i * 7) // 8
        c.set(x, y, mid).set(x, y - 1, lit)
        if y + 1 <= 12:
            c.set(x, y + 1, dark)
    c.set(12, 6, mid).set(12, 5, lit)
    return c


def crate_top(tier):
    """Lid: boards across it under a tier metal cross strap with a cyan core where the straps meet."""
    c = crate_shell(tier)
    for x in range(3, 13):
        if x % 3 == 0:
            c.rect(x, 3, 1, 10, 'g')
        elif x % 3 == 1:
            c.rect(x, 3, 1, 10, 'i')
    c.rect(3, 7, 10, 2, 'C').rect(3, 7, 10, 1, 'D')                      # straps
    c.rect(7, 3, 2, 10, 'C').rect(7, 3, 1, 10, 'D')
    c.rect(6, 6, 4, 4, 'B').bevel(6, 6, 4, 4, 'D', 'A')                  # hub
    c.rect(7, 7, 2, 2, 'q').set(7, 7, 'Q')                               # cyan core
    return c


def crate_bottom():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'g')
    c.bevel(0, 0, 16, 16, 'h', 'f')
    c.rect(3, 3, 10, 10, 'f').bevel(3, 3, 10, 10, 'k', 'h')
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rect(x, y, 2, 2, 'f').set(x, y, 'h')
    return c


def kit(tier):
    """Mk kit: a tier coloured toolbox, latch, handle and one cyan pip per tier above 1."""
    c = Canvas()
    c.rect(1, 6, 14, 8, 'C')                                             # box
    c.rect(1, 6, 14, 1, 'D').rect(1, 13, 14, 1, 'B').rect(1, 6, 1, 8, 'D').rect(14, 6, 1, 8, 'B')
    c.rect(1, 4, 14, 2, 'B').rect(1, 4, 14, 1, 'E')                      # lid
    c.rect(5, 1, 6, 1, 'b').rect(5, 2, 1, 2, 'b').rect(10, 2, 1, 2, 'b').set(5, 1, 'd')   # handle
    c.rect(1, 8, 14, 1, 'A')                                             # lid seam
    c.draw(7, 7, ['87', '65'])                                           # latch
    for i in range(tier - 1):
        c.set(3 + i * 2, 11, 'Y')
    c.draw(11, 10, ['.o', 'on'])                                         # little sprout decal
    c.outline('k')
    return c


def main():
    for kind in ('stumpy', 'sprout'):
        write_png(ASSETS / 'textures/block' / f'{kind}_body.png', robot_atlas(kind).rows(), pal(), size=32)
    write_block('automation_copper', panels(CU).rows(), pal())
    write_block('automation_brass', panels(BR, seed=4).rows(), pal())
    write_block('automation_steel', panels(ST, seed=8).rows(), pal())
    write_block('automation_dark', panels(DK, rivets=False, slits=True).rows(), pal())
    write_block('automation_wood', wood().rows(), pal())
    for tier, mat in TIERS.items():
        write_block(f'automation_mk{tier}', accent().rows(), pal(mat))
    write_block('automation_eye', eye().rows(), pal())
    write_block('automation_saw', saw().rows(), pal())
    write_block('automation_leaf', leaf().rows(), pal())
    write_block('excavator_drill', drill().rows(), pal())
    write_block('excavator_light', light_off().rows(), pal())
    write_anim('block', 'excavator_drill_on', [drill_spin(i) for i in range(6)], pal(), frametime=1)
    write_anim('block', 'excavator_light_on', [light_on(i) for i in range(4)], pal(), frametime=5)
    write_block('excavator_side', excavator_side().rows(), pal())
    for tier, mat in TIERS.items():
        name = 'supply_crate' if tier == 1 else f'supply_crate_mk{tier}'
        for face, draw in (('front', crate_front), ('side', crate_side), ('top', crate_top)):
            c = draw(tier)
            write_block(f'{name}_{face}', c.rows(), crate_pal(tier))
            if face == 'front' or tier == 1:                             # side and top glow are the same for every Mk
                write_block(f'{name}_{face}_glow' if face == 'front' else f'supply_crate_{face}_glow', glow_only(c).rows(),
                            crate_pal(tier))
    write_block('supply_crate_bottom', crate_bottom().rows(), crate_pal())
    for tier in (2, 3, 4):
        write_item(f'farm_kit_mk{tier}', kit(tier).rows(), pal(TIERS[tier]))


if __name__ == '__main__':
    main()
