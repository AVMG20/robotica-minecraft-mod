"""Industry module textures: ores, materials, alloys, fuel pellets, Assembler parts, the processing machines (Mk1-Mk4)
and the RTG. Run: python3 scripts/textures/industry.py
(writes textures/item/*.png with item models when missing, textures/block/*.png; block models come from
scripts/data/industry_data.py)

Items follow the core item style: one dark outline 'k', light from the top-left (Canvas.auto_shade), a highlight pixel
on the lit side. Chars: '12345' main material (deep -> highlight), 'abcde' steel, 'y Y z' glow (mid, bright, white).
The Alloy Smelter, Centrifuge and Assembler use the shared casing (machine_casing.py) with the tier metal (Mk1 copper,
Mk2 ferrothorium, Mk3 pyrosteel, Mk4 resonant alloy) and inset bays on their own model elements (crucible, drum, work
cell); what glows sits on full-bright overlays. The RTG uses the same casing in generator gunmetal.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import (MATERIALS, Canvas, fbm, hash01, material, write_anim, write_block, write_item,  # noqa: E402
                      write_still)
import machine_casing as casing  # noqa: E402

M, S = '12345', 'abcde'
SHADE = {'3': ('2', '4'), 'c': ('b', 'd')}

RAMPS = {
    'thorium': ('#22362A', '#3E5E48', '#6E9878', '#A8CDA8', '#E2F5DC'),
    'ferrothorium': ('#1E2626', '#3A4746', '#62736E', '#95A8A0', '#D2E2DA'),
    'pyrosteel': ('#3A0E08', '#6E2210', '#A8421C', '#E07A3A', '#FFC58A'),
    'resonant': ('#160A2E', '#36206A', '#5E46B0', '#8EA0F0', '#D8F4FF'),
    'pyrolite': ('#4A1004', '#8A2A08', '#D8561A', '#FF9A3A', '#FFE6A0'),
    'resonite': ('#24104A', '#46288A', '#6E4AC4', '#A084EC', '#8AF2E6'),
    'graphite': ('#121214', '#26262A', '#3C3C42', '#5A5A62', '#8A8A94'),
    'lead': ('#1C1E24', '#30343E', '#4C5260', '#757D8E', '#AEB6C6'),
    'thorium_ore': ('#465E4E', '#6A8670', '#90AC94', '#B6D0B4', '#E2F5DC'),   # ore pieces: lighter, sit on rock
}
GLOWS = {
    'green': ('#2E7A1E', '#7CE84A', '#D8FF9A', '#F8FFE8'),
    'amber': ('#8A3A06', '#FF9A1E', '#FFD86A', '#FFF8D8'),
    'cyan': ('#145A66', '#5FE3F0', '#B5F6FB', '#F2FFFF'),
    'dim': ('#2A302A', '#4E5A4E', '#6E7A6E', '#8E9A8E'),
    'violet': ('#3A1F6E', '#A884F0', '#E0D0FF', '#FFFFFF'),
}


def pal(main, glow='green', **extra):
    p = {'k': '#1A1C1C', 'K': '#0C0E0E'}
    p.update(dict(zip(M, RAMPS[main] if main in RAMPS else MATERIALS[main])))
    p.update(material(S, 'steel'))
    g = GLOWS[glow]
    p.update({'Z': g[0], 'y': g[1], 'Y': g[2], 'z': g[3]})
    p.update({'o': '#E8B530', 'O': '#FFE07A', 'p': '#C87533', 'P': '#E8A060'})
    p.update(extra)
    return p


def finish(c, shade=SHADE):
    c.auto_shade(shade)
    c.outline('k')
    return c.rows()


# =================================================================================================== items

def ingot():
    """Trapezoid bar seen at an angle: lit top face, a front face and a dark end."""
    c = Canvas()
    for y in range(5, 12):
        inset = max(0, 7 - y) if y < 7 else 0
        c.rect(2 + inset, y, 12 - inset, 1, '3')
    c.rect(4, 5, 9, 2, '4')                                              # top face
    c.rect(2, 10, 12, 1, '2').rect(13, 7, 1, 4, '2')
    c.line(5, 5, 7, 5, '5').set(4, 6, '5')
    c.auto_shade({'3': ('2', '3')})
    c.line(5, 8, 10, 8, '4')
    c.outline('k')
    return c.rows()


def raw_chunk():
    """Raw ore nugget: a few fused lumps, crevices between them, each lump lit on its top-left."""
    lumps = ((8.0, 9.0, 4.3), (11.4, 5.4, 2.3), (5.2, 5.6, 2.4), (3.6, 10.6, 2.0))
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = sorted((math.hypot(x - lx, y - ly) - r, i) for i, (lx, ly, r) in enumerate(lumps))
            if d[0][0] > 0.2:
                continue
            lx, ly, r = lumps[d[0][1]]
            crevice = d[1][0] - d[0][0] < 0.6 and -1.2 < d[1][0] < 0.4
            u = ((x - lx) + (y - ly)) / r
            c.set(x, y, '2' if crevice else '4' if u < -0.7 else '2' if u > 0.9 else '3')
    for lx, ly, r in lumps:
        c.set(round(lx - r * 0.45), round(ly - r * 0.45), '5')
    c.speckle(2, 2, 12, 12, '2', 0.08, 3, only='3')
    c.outline('k')
    return c.rows()


def dust():
    """Small heap of powder with a soft highlight and loose grains around it."""
    c = Canvas()
    c.shape(lambda x, y: y >= 6 and abs(x - 7.5) <= (y - 5) * 1.25 and y <= 13, lambda x, y: '3')
    c.speckle(2, 6, 12, 8, '24', 0.25, 9, only='3')
    c.auto_shade(SHADE)
    c.set(7, 7, '5').set(6, 8, '5').set(8, 8, '4')
    c.set(2, 13, '3').set(13, 12, '2').set(12, 14, '3')
    c.outline('k')
    return c.rows()


def plate():
    """Pressed sheet with thickness and a diagonal sheen (same shape as the core plates)."""
    c = Canvas()
    c.rect(1, 3, 14, 9, '3').rect(1, 12, 14, 1, '1')
    c.auto_shade(SHADE)
    c.rect(1, 12, 14, 1, '1').set(1, 12, '2')
    c.line(3, 10, 8, 5, '4').line(4, 10, 9, 5, '4').line(10, 9, 12, 7, '4')
    c.set(2, 4, '5').set(3, 4, '5')
    c.outline('k')
    return c.rows()


def shard():
    """Pyrolite shard: a long jagged crystal leaning right, a hot core line."""
    c = Canvas()
    for i in range(12):
        x, y = 3 + i * 0.75, 13 - i
        w = 2 if i in (0, 11) else 3 if i < 4 or i > 8 else 4
        c.rect(round(x), y, w, 1, '3')
    c.rect(9, 3, 2, 3, '3').rect(4, 10, 2, 3, '3')
    c.auto_shade(SHADE)
    c.line(5, 12, 10, 3, 'Y')
    c.set(6, 10, 'z').set(9, 5, 'z')
    c.outline('k')
    return c.rows()


def crystal():
    """Resonite cluster: three hexagonal prisms, the middle one tallest, cold glints."""
    c = Canvas()
    for cx, top, w in ((7, 1, 3), (3, 6, 2), (11, 4, 2)):
        c.rect(cx - w // 2, top + 1, w + 1, 14 - top, '3')
        c.set(cx, top, '4')
    c.auto_shade(SHADE)
    c.line(7, 3, 7, 12, '5').set(3, 8, 'Y').set(11, 6, 'Y').set(7, 2, 'z')
    c.rect(1, 14, 14, 1, '1')
    c.outline('k')
    return c.rows()


def pellet(glow=True, band='3'):
    """Fuel pellet: a short capsule, steel caps, a window over the glowing core."""
    c = Canvas()
    c.rect(4, 3, 8, 10, band)
    c.rect(5, 2, 6, 1, 'c').rect(5, 13, 6, 1, 'c').rect(4, 3, 8, 1, 'c').rect(4, 12, 8, 1, 'c')
    c.auto_shade(SHADE)
    c.rect(6, 5, 4, 6, 'K')
    if glow:
        c.rect(7, 6, 2, 4, 'y').set(7, 6, 'z').set(8, 7, 'Y').set(7, 8, 'Y')
    else:
        c.rect(7, 6, 2, 4, 'Z')
    c.set(5, 4, '5').set(5, 5, '4')
    c.outline('k')
    return c.rows()


def isotope():
    """Radiant isotope: a glass ampoule with a bright glowing bead and a steel cap."""
    c = Canvas()
    c.rect(6, 1, 4, 2, 'c')
    c.disc(7.5, 9.0, 4.6, '2')
    c.rect(6, 3, 4, 3, '2')
    c.auto_shade({'2': ('1', '3'), 'c': ('b', 'd')})
    c.disc(7.5, 9.0, 2.6, 'y')
    c.disc(7.0, 8.5, 1.4, 'Y').set(7, 8, 'z')
    c.set(5, 6, '4').set(4, 8, '4')
    c.outline('k')
    return c.rows()


def thermocouple():
    """Two wires of different metal (copper and thorium) twisted together, a junction bead, a steel plug."""
    c = Canvas()
    for i in range(10):
        y = 2 + i
        c.set(6 + (1 if (i // 2) % 2 else 0), y, 'p').set(8 - (1 if (i // 2) % 2 else 0), y, '3')
    c.disc(7.5, 2.0, 1.6, 'y').set(7, 1, 'Y')
    c.rect(4, 12, 8, 3, 'c').rect(5, 11, 6, 1, 'c')
    c.auto_shade({'c': ('b', 'd'), '3': ('2', '4'), 'p': ('p', 'P')})
    c.set(5, 13, 'K').set(10, 13, 'K')
    c.outline('k')
    return c.rows()


def sc_coil():
    """Superconductor coil: pyrosteel flanges, frosty cyan windings, a bright core."""
    c = Canvas()
    c.rect(2, 1, 12, 2, '3').rect(2, 13, 12, 2, '3')
    for x in range(3, 13):
        for y in range(3, 13):
            k = (x - 3) / 9
            c.set(x, y, 'z' if k < 0.15 else 'Y' if k < 0.45 else 'y' if k < 0.8 else 'Z')
    for y in range(4, 13, 2):
        c.rect(3, y, 10, 1, 'Z').set(3, y, 'y')
    c.auto_shade({'3': ('2', '4')})
    c.rect(7, 0, 2, 1, 'b').rect(7, 15, 2, 1, 'b')
    c.outline('k')
    return c.rows()


def lattice():
    """Resonant lattice: a diamond frame of alloy struts with glowing nodes at the joints."""
    c = Canvas()
    nodes = [(7.5, 1.5), (13.5, 7.5), (7.5, 13.5), (1.5, 7.5), (7.5, 7.5), (4.5, 4.5), (10.5, 4.5), (4.5, 10.5), (10.5, 10.5)]
    edges = [(0, 1), (1, 2), (2, 3), (3, 0), (5, 8), (6, 7), (0, 4), (4, 2), (3, 4), (4, 1)]
    for a, b in edges:
        (x0, y0), (x1, y1) = nodes[a], nodes[b]
        c.line(round(x0), round(y0), round(x1), round(y1), '3')
    c.auto_shade(SHADE)
    for i, (x, y) in enumerate(nodes):
        c.set(round(x), round(y), 'z' if i == 4 else 'Y')
    c.outline('k')
    return c.rows()


def items():
    write_item('raw_thorium', raw_chunk(), pal('thorium'))
    write_item('thorium_ingot', ingot(), pal('thorium'))
    write_item('thorium_dust', dust(), pal('thorium'))
    write_item('thorium_plate', plate(), pal('thorium'))
    write_item('graphite_dust', dust(), pal('graphite'))
    write_item('pyrolite_shard', shard(), pal('pyrolite', 'amber'))
    write_item('pyrolite_dust', dust(), pal('pyrolite', 'amber'))
    write_item('resonite_crystal', crystal(), pal('resonite', 'cyan'))
    write_item('resonite_dust', dust(), pal('resonite', 'cyan'))
    write_item('ferrothorium_ingot', ingot(), pal('ferrothorium'))
    write_item('ferrothorium_plate', plate(), pal('ferrothorium'))
    write_item('pyrosteel_ingot', ingot(), pal('pyrosteel'))
    write_item('pyrosteel_plate', plate(), pal('pyrosteel'))
    write_item('resonant_alloy_ingot', ingot(), pal('resonant'))
    write_item('resonant_alloy_plate', plate(), pal('resonant'))
    write_item('thermocouple', thermocouple(), pal('thorium', 'amber'))
    write_item('superconductor_coil', sc_coil(), pal('pyrosteel', 'cyan'))
    write_item('resonant_lattice', lattice(), pal('resonant', 'violet'))
    write_item('thorium_fuel_pellet', pellet(), pal('thorium', 'green'))
    write_item('enriched_fuel_pellet', pellet(), pal('pyrosteel', 'amber'))
    write_item('depleted_fuel_pellet', pellet(glow=False), pal('lead', 'dim'))
    write_item('fusion_fuel_pellet', pellet(), pal('resonant', 'cyan'))
    write_item('radiant_isotope', isotope(), pal('thorium', 'green', **{'1': '#2A3A30', '2': '#5E7A66', '3': '#9AB8A2', '4': '#D8F0DC'}))


# =================================================================================================== ore and storage blocks

# Ores are two-layer models (scripts/data/industry_data.py): the vanilla host texture underneath, our cutout overlay
# with only the ore pieces on top, so they match vanilla rock and resource packs. Pyrolite and resonite add a small
# full-bright layer with the core pixels.
#
# A piece is a little sprite: 'X' body (auto-shaded from its outline: lit top-left, dark bottom-right), '5' highlight,
# '*' glowing core, 'T' glowing accent tip, '6' accent. Around it the overlay adds a shadow 'k' below/right and a rim
# 'm' above/left, both in host-rock colours.

HOST_EDGE = {   # (shadow, rim) per host rock
    'stone': ('#58585A', '#686868'),
    'deepslate': ('#232329', '#2F2F37'),
    'netherrack': ('#2E0C0C', '#411616'),
    'end_stone': ('#8E875E', '#B0A877'),
}

THORIUM_PIECES = {   # metallic chunks
    'a': ('.5X.', '5X2X', 'XXXX', '.XX.'),
    'b': ('5X.', 'X2X', '.XX'),
    'c': ('.5X', 'X2X', 'XX.'),
    'd': ('5XX', 'XX.'),
    'e': ('.5X.', 'X42X', 'XXX.'),
    'f': ('5X', 'XX'),
}
PYROLITE_PIECES = {   # shards with a hot core
    'a': ('..X5', '.X*X', 'X*X.', 'XX..'),
    'b': ('5X..', 'X*X.', '.X*X', '..XX'),
    'c': ('...5', '..XX', '.*X.', 'XX..', 'X...'),
    'd': ('.5', '*X', 'X.'),
}
RESONITE_PIECES = {   # crystal prisms with teal tips
    'a': ('.T..', '.6.T', 'TX.6', 'XXXX', '.XX.'),
    'b': ('T.', '6T', 'XX'),
    'c': ('..T', 'T.6', '6XX', 'XXX'),
    'd': ('.T.', 'T6.', 'XXX'),
}

ORE_LAYOUTS = {   # name: (host, pieces, ((piece, x, y), ...))
    'thorium_ore': ('stone', THORIUM_PIECES, (('a', 1, 1), ('d', 9, 1), ('e', 10, 6), ('b', 4, 8), ('f', 1, 13), ('c', 11, 12))),
    'deepslate_thorium_ore': ('deepslate', THORIUM_PIECES, (('b', 2, 2), ('a', 9, 1), ('f', 7, 7), ('e', 1, 10), ('c', 11, 10), ('d', 6, 13))),
    'pyrolite_ore': ('netherrack', PYROLITE_PIECES, (('a', 1, 1), ('b', 10, 1), ('c', 6, 6), ('d', 13, 7), ('d', 1, 11), ('b', 10, 11))),
    'resonite_ore': ('end_stone', RESONITE_PIECES, (('a', 1, 1), ('b', 10, 2), ('d', 6, 7), ('c', 1, 11), ('a', 11, 9))),
}


def ore_overlay(name):
    """Returns (overlay, glow) canvases for an ore."""
    _, pieces, layout = ORE_LAYOUTS[name]
    body = {}
    for key, ox, oy in layout:
        for y, row in enumerate(pieces[key]):
            for x, ch in enumerate(row):
                if ch != '.':
                    body[(ox + x, oy + y)] = ch
    c, g = Canvas(), Canvas()
    for (x, y), ch in body.items():
        if ch == 'X':
            score = sum((x + dx, y + dy) not in body for dx, dy in ((0, -1), (-1, 0))) \
                - sum((x + dx, y + dy) not in body for dx, dy in ((0, 1), (1, 0)))
            ch = '4' if score > 0 else '2' if score < 0 else '3'
            if score < 0 and (x + 1, y + 1) not in body and (x - 1, y) in body and (x, y - 1) in body:
                ch = '1'
        c.set(x, y, ch)
        if ch in '*T':
            g.set(x, y, ch)
    for y in range(16):
        for x in range(16):
            if (x, y) in body:
                continue
            if (x, y - 1) in body or (x - 1, y) in body:
                c.set(x, y, 'k')
            elif (x, y + 1) in body or (x + 1, y) in body:
                c.set(x, y, 'm')
    return c, g


ORE_RAMPS = {   # name -> (ramp, (core/tip colour, accent), glow colour)
    'thorium_ore': ('thorium_ore', ('#E2F5DC', '#A8CDA8'), None),
    'deepslate_thorium_ore': ('thorium_ore', ('#E2F5DC', '#A8CDA8'), None),
    'pyrolite_ore': ('pyrolite', ('#FFE6A0', '#FF9A3A'), '#FFD070'),
    'resonite_ore': ('resonite', ('#9AF4EA', '#4FC4C8'), '#8AE8E0'),
}


def ore_pal(name):
    host = ORE_LAYOUTS[name][0]
    ramp, (core, accent), glow = ORE_RAMPS[name]
    p = dict(zip(M, RAMPS[ramp]))
    p.update({'k': HOST_EDGE[host][0], 'm': HOST_EDGE[host][1], '*': core, 'T': core, '6': accent})
    return p, {'*': glow or core, 'T': glow or core}

def storage_block(main, kind, glow=False):
    """Metal or crystal block: bevelled tiles with seams; crystal blocks get facets and glowing seams."""
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    g = Canvas()
    if kind == 'metal':
        c.rect(0, 0, 16, 1, '4').rect(0, 0, 1, 16, '4').rect(0, 15, 16, 1, '2').rect(15, 0, 1, 16, '2')
        c.rect(1, 7, 14, 1, '2').rect(1, 8, 14, 1, '4').rect(7, 1, 1, 6, '2').rect(8, 1, 1, 6, '4')
        c.rect(4, 9, 1, 6, '2').rect(5, 9, 1, 6, '4').rect(11, 9, 1, 6, '2').rect(12, 9, 1, 6, '4')
        c.speckle(1, 1, 14, 14, '24', 0.06, 7, only='3')
        c.set(1, 1, '5').set(9, 1, '5')
    elif kind == 'raw':
        for y in range(16):
            for x in range(16):
                n = fbm(x, y, 21)
                c.set(x, y, '12345'[max(0, min(4, int(n * 5.5 - 0.4)))])
    else:
        for y in range(16):
            for x in range(16):
                a = (x + y) % 8
                b = (x - y) % 8
                c.set(x, y, '4' if a == 0 else '2' if b == 0 else '3' if (x // 4 + y // 4) % 2 else '4' if hash01(x, y, 2) < 0.1 else '3')
                if a == 0 and glow:
                    g.set(x, y, 'y' if (x + y) % 16 else 'Y')
        c.rect(0, 0, 16, 1, '5').rect(0, 0, 1, 16, '5').rect(0, 15, 16, 1, '1').rect(15, 0, 1, 16, '1')
    return c, g


def blocks_ores():
    for name in ORE_LAYOUTS:
        c, g = ore_overlay(name)
        p, gp = ore_pal(name)
        write_block(name, c.rows(), p)
        if ORE_RAMPS[name][2]:
            write_block(name + '_glow', g.rows(), gp)

    c, _ = storage_block('thorium', 'raw')
    write_block('raw_thorium_block', c.rows(), pal('thorium'))
    c, _ = storage_block('thorium', 'metal')
    write_block('thorium_block', c.rows(), pal('thorium'))
    c, g = storage_block('pyrolite', 'crystal', True)
    write_block('pyrolite_block', c.rows(), pal('pyrolite', 'amber'))
    write_block('pyrolite_block_glow', g.rows(), pal('pyrolite', 'amber'))
    c, g = storage_block('resonite', 'crystal', True)
    write_block('resonite_block', c.rows(), pal('resonite', 'cyan', **{'5': '#C4B0F6'}))
    write_block('resonite_block_glow', g.rows(), pal('resonite', 'cyan'))


# =================================================================================================== machines

# Alloy Smelter: a bay 3 px deep holding a crucible (a real model part) wrapped in a cyan heating hoop; molten alloy
# shows on its top and pours from a spout into a mould on the bay floor. Two textures shared by every tier: the
# chamber (back wall, bay walls in its border) and the parts atlas (SMELTER_PARTS); while working both are animated
# and full-bright. Top: a feed hopper with a cyan ring round its throat.

SMELTER_HEAT = {
    'm': '#2A0C08', 'n': '#7A2208', 'o': '#D4500E', 'p': '#FF9426', 'q': '#FFD070', 'y': '#FFF6D8',  # molten
    'r': '#2E2C30', 's': '#4A474D', 't': '#6E6A70',                                                  # cooled alloy
}
SMELTER_BAY = (3, 3, 10, 10)                    # x, y, w, h of the bay on the front (corners stay casing)
SMELTER_FRAMES, SMELTER_FRAMETIME = 16, 2
# Parts atlas regions (x, y, w, h); scripts/data/industry_data.py maps the model faces onto them
SMELTER_PARTS = {
    'crucible_front': (0, 0, 6, 5), 'crucible_left': (6, 0, 2, 5), 'crucible_right': (8, 0, 2, 5),
    'crucible_top': (0, 6, 6, 2), 'spout': (10, 0, 1, 1), 'spout_top': (10, 1, 1, 1), 'stream': (12, 0, 1, 5),
    'mould_front': (0, 9, 4, 1), 'mould_top': (0, 10, 4, 2), 'mould_side': (4, 9, 2, 1),
}
HOOP_ROW = 2                                    # crucible row with the heating hoop


def smelter_pal(tier=1):
    return casing.casing_palette(casing.INDUSTRY_TIERS[tier], **SMELTER_HEAT)


def smelter_chamber(lit, t=0):
    """Back wall in the bay area; bay walls in the border: cols 0-2 left, 13-15 right, rows 0-2 floor, 13-15 roof."""
    x0, y0, w, h = SMELTER_BAY
    c = Canvas()
    c.rect(x0, y0, w, h, 'm' if lit else 'a')
    if lit:                                                               # heat glow behind the crucible mouth
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                d = math.hypot((x - 6.5) / 1.7, y - 6.0)
                if d < 3.0:
                    c.set(x, y, 'o' if d < 1.8 else 'n')
    else:
        for x in range(x0 + 1, x0 + w, 3):
            c.rect(x, y0, 1, h, 'b')
    for x, d in ((3, 0), (10, 0), (11, 1), (12, 2)):                       # emitters feeding the hoop, pulses flow in
        c.set(x, 8, ('w' if (t // 2 + d) % 4 == 0 else 'v') if lit else 'u')
    c.set(3, 7, 'b').set(3, 9, 'b').set(12, 7, 'b').set(12, 9, 'b')
    if lit:
        c.rect(0, y0, 3, h, 'm').rect(13, y0, 3, h, 'm').set(2, 7, 'n').set(13, 7, 'n')
        c.rect(x0, 0, w, 3, 'm').rect(x0 + 1, 2, w - 2, 1, 'n')
        c.rect(x0, 13, w, 3, 'm')
    else:
        c.rect(0, y0, 3, h, 'b').rect(13, y0, 3, h, 'b').rect(0, y0, 1, h, 'c').rect(15, y0, 1, h, 'c')
        c.rect(x0, 0, w, 3, 'b').rect(x0, 0, w, 1, 'c')
        c.rect(x0, 13, w, 3, 'a')
    return c


def hoop_spark(t):
    """Where the hoop's spark is on its way round: ('left', u) / ('front', u) / ('right', u), or None while behind."""
    path = [('left', 0), ('left', 1)] + [('front', u) for u in range(6)] + [('right', 0), ('right', 1)]
    k = t * 16 // SMELTER_FRAMES                                          # once round per loop
    return path[k] if k < len(path) else None


def smelter_parts(lit, t=0):
    """The crucible, spout, stream and mould faces (see SMELTER_PARTS)."""
    c = Canvas()
    R = SMELTER_PARTS

    def body(name):
        x, y, w, h = R[name]
        face = name.split('_')[1]
        c.rect(x, y, w, h, 'c').rect(x, y, w, 1, 'e' if face == 'front' else 'd')
        c.rect(x, y + 1, w, 1, 'd').rect(x, y + h - 1, w, 1, 'b')
        if face == 'front':
            c.set(x, y + h - 1, '.').set(x + w - 1, y + h - 1, '.')
            c.rect(x + w - 1, y + 1, 1, h - 2, 'b').set(x, y + 1, 'e')
        spark = hoop_spark(t) if lit else None
        for u in range(w):
            if not lit:
                ch = 'u'
            elif spark and spark[0] == face and spark[1] == u:
                ch = 'x'
            elif spark and spark[0] == face and abs(spark[1] - u) == 1:
                ch = 'w'
            else:
                ch = 'v'
            c.set(x + u, y + HOOP_ROW, ch)
    for name in ('crucible_front', 'crucible_left', 'crucible_right'):
        body(name)
    x, y, w, h = R['crucible_top']
    if lit:
        glint = 1 + round(1.5 + 1.5 * math.sin(t / SMELTER_FRAMES * 2 * math.pi))
        c.rect(x, y, w, h, 'q').rect(x, y + 1, w, 1, 'p').set(x + glint, y, 'y').set(x + 5 - glint, y + 1, 'q')
    else:
        c.rect(x, y, w, h, 's').rect(x, y + 1, w, 1, 'r')
    c.rect(x, y, 1, h, 'e').rect(x + w - 1, y, 1, h, 'd')                # rim ends
    x, y, _, _ = R['spout']
    c.set(x, y, 'd')
    x, y, _, _ = R['spout_top']
    c.set(x, y, 'p' if lit else 's')
    x, y, w, h = R['stream']
    for v in range(h):                                                    # blobs sliding down
        c.set(x, y + v, 'y' if (v - t) % 4 == 0 else 'q')
    x, y, w, h = R['mould_front']
    c.rect(x, y, w, h, 'd')
    x, y, w, h = R['mould_top']
    c.rect(x, y, w, h, 'c')
    c.rect(x + 1, y, w - 2, h, 'p' if lit else 'r')
    if lit:                                                               # splash where each blob lands
        c.rect(x + 1, y, w - 2, 1, 'q').set(x + 1, y, 'y' if t % 4 < 2 else 'q')
    x, y, w, h = R['mould_side']
    c.rect(x, y, w, h, 'b')
    return c


def smelter_front(tier, lit):
    """The bay with tier metal corners; the bottom right one is the status lamp."""
    x0, y0, w, h = SMELTER_BAY
    c = casing.shell(tier)
    c.rect(x0, y0, w, h, '.')
    c.set(3, 3, 'i').set(12, 3, 'h').set(3, 12, 'h').set(12, 12, 'L' if lit else 'l')    # tier metal corners
    return c


def smelter_glow():
    """Front overlay: the status lamp."""
    c = Canvas()
    c.set(12, 12, 'L')
    return c


def smelter_top(tier):
    """Feed hopper: a stepped funnel down to a dark throat with a ring round it."""
    c = casing.shell(tier)
    c.recess(4, 4, 8, 8, 'abcde', fill='b')
    c.recess(5, 5, 6, 6, 'abcde', fill='a')
    c.ring(7.5, 7.5, 1.6, 2.6, 'u')
    c.disc(7.5, 7.5, 1.5, 'K')
    return c


def smelter_top_glow(t):
    """The throat ring lit, a spark running round it."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 1.6 <= d <= 2.6:
                a = (math.atan2(y - 7.5, x - 7.5) / (2 * math.pi) - t / SMELTER_FRAMES) % 1
                c.set(x, y, 'x' if a < 0.1 else 'w' if a < 0.3 else 'v')
    return c


# Centrifuge: a drum spinning behind a round porthole. The drum sits 2 px deep on its own element
# (scripts/data/industry_data.py): separated layers of material in rings round the hub and a glass track where green
# light chases round while it spins. Chars on top of the casing: 'n o p q r' green glow (deep -> white core),
# 's t y' material layers unlit (heavy outer -> light inner), 'S T Y' the same layers lit.

CF_HOLE, CF_RIM = 5.0, 6.1                                               # porthole and bezel radius
CF_TRACK, CF_HEAVY, CF_LIGHT = 3.8, 2.0, 1.4                             # drum rings: track, heavy, light layer
CF_LAYERS = 2.9                                                          # outer edge of the material, a dark gap to the track
CF_ARMS, CF_FRAMES, CF_FRAMETIME = 4, 8, 2                               # 4 light arms, one track pixel per frame
CF_TAIL = 'rqpo'                                                      # an arm from its head back


def cf_pal(tier=1):
    return casing.casing_palette(casing.INDUSTRY_TIERS[tier], n='#123A22', o='#1F7A3A', p='#45D35C', q='#B2FF8A',
                                 r='#F2FFE8', s='#1A2620', t='#22342A', y='#2E4636', S='#1E5C30', T='#3C9C48',
                                 Y='#9AEC7A')


def cf_dist(x, y):
    return math.hypot(x - 7.5, y - 7.5)


# The track: a one pixel ring of 32 pixels, one per 1/32 turn, in clockwise order
CF_RING = sorted(((x, y) for y in range(16) for x in range(16) if CF_TRACK <= cf_dist(x, y) < CF_HOLE - 0.1),
                 key=lambda p: math.atan2(p[1] - 7.5, p[0] - 7.5))


def cf_clamps(c):
    """Tier metal clamps holding the bezel at the four sides."""
    for x, y, w, h in ((7, 2, 2, 1), (7, 13, 2, 1), (2, 7, 1, 2), (13, 7, 1, 2)):
        c.rect(x, y, w, h, 'h')
    c.set(7, 2, 'i').set(2, 7, 'i')


def centrifuge_front(tier, lit):
    """Casing with a round porthole (transparent: the drum shows through) in a bevelled bezel held by tier clamps."""
    c = casing.shell(tier)
    for y in range(16):
        for x in range(16):
            d = cf_dist(x, y)
            lean = (x - 7.5) + (y - 7.5)                                 # < 0 towards the light (top-left)
            if d < CF_HOLE:
                c.set(x, y, '.')
            elif d < CF_RIM:
                if d < CF_HOLE + 0.55:                                   # inner lip: shadowed at the top-left
                    c.set(x, y, 'a' if lean < -1 else 'd' if lean > 1 else 'b')
                else:
                    c.set(x, y, 'e' if lean < -2 else 'b' if lean > 2 else 'd')
    cf_clamps(c)
    c.set(12, 3, 'L' if lit else 'l')
    return c


def centrifuge_drum():
    """The drum face behind the porthole: hub, light and heavy layer, glass track, dark bore round it."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = cf_dist(x, y)
            if d < CF_LIGHT:
                c.set(x, y, 'e' if (x, y) == (7, 7) else 'd')
            elif d < CF_HEAVY:
                c.set(x, y, 'y')
            elif d < CF_LAYERS:
                c.set(x, y, 't')
            elif d < CF_TRACK:
                c.set(x, y, 'K')
            elif d < CF_HOLE - 0.1:
                c.set(x, y, 'u')
            else:
                c.set(x, y, 'K' if d < CF_RIM else 'a')
    return c


def centrifuge_glow(frame, layers=True):
    """Four arms of green light chasing round the track one pixel per frame, each a bright head with a fading tail
    (reads as a blur), and the glowing layers of material: heavy outside, light inside."""
    c = Canvas()
    if layers:
        for y in range(16):
            for x in range(16):
                d = cf_dist(x, y)
                if CF_LIGHT <= d < CF_LAYERS:
                    c.set(x, y, 'Y' if d < CF_HEAVY else 'S')
    arm = len(CF_RING) // CF_ARMS
    for i, (x, y) in enumerate(CF_RING):
        behind = (frame - i) % arm
        c.set(x, y, CF_TAIL[behind] if behind < len(CF_TAIL) else 'n')
    return c


def centrifuge_top(tier):
    """Round lid over the drum: a bevelled rim, the glass track (lit by centrifuge_top_glow) and a vented hub cap."""
    c = casing.shell(tier)
    for y in range(16):
        for x in range(16):
            d = cf_dist(x, y)
            lean = (x - 7.5) + (y - 7.5)
            if d < CF_LIGHT + 0.9:
                c.set(x, y, 'e' if lean < -1 else 'b' if lean > 1 else 'd')
            elif d < CF_TRACK:
                c.set(x, y, 'a')
            elif d < CF_HOLE - 0.1:
                c.set(x, y, 'u')
            elif d < CF_RIM:
                c.set(x, y, 'e' if lean < -2 else 'b' if lean > 2 else 'd')
    cf_clamps(c)
    return c


def centrifuge_top_glow(frame):
    """The lid's track with the same chasing arms as the porthole."""
    return centrifuge_glow(frame, layers=False)


# Assembler: a robot work cell. The front has an open bay 2 px deep (walls and back on their own elements, see
# scripts/data/industry_data.py). A gantry carriage runs along the back, lowers its torch onto the part, welds a seam
# from left to right with a cyan-white spark, lifts and travels back while the seam cools. Chars on top of the
# casing: 'p q' part steel (body, lit edge), 'y s' a cooling seam (warm cyan, cool cyan); the spark uses 'v w x'.

AS_FRAMES, AS_FRAMETIME = 32, 2                                          # one weld cycle: 64 ticks
AS_LEFT, AS_RIGHT, AS_PARK = 5, 10, 7                                    # torch x: seam ends, parked while idle
AS_PHASE = 8                                                             # the loop starts mid-weld
AS_SEAM = 'xxwwvvvvyyyyssssss'                                           # a seam pixel cooling, one char per frame


def as_pal(tier=1):
    return casing.casing_palette(casing.INDUSTRY_TIERS[tier], p='#56626C', q='#9AA8B2', y='#2C8FA2', s='#1C4C58')


def as_torch(frame):
    """(torch x, head row, welding) of the weld cycle: lower, weld left to right, lift, ease back, rest."""
    if frame < 2:
        return AS_LEFT, 6 + frame, False
    if frame < 14:
        return AS_LEFT + (frame - 2) // 2, 8, True
    if frame < 16:
        return AS_RIGHT, 7 - (frame - 14), False
    if frame < 28:
        t = (frame - 16) / 11
        t = t * t * (3 - 2 * t)
        return round(AS_RIGHT - (AS_RIGHT - AS_LEFT) * t), 6, False
    return AS_LEFT, 6, False


def assembler_front(tier, lit):
    """Casing with the open work cell bay (transparent) above a lip with the status lamp."""
    c = casing.shell(tier)
    c.rect(3, 3, 10, 9, '.')
    c.rect(3, 12, 10, 1, 'd')
    c.set(12, 12, 'L' if lit else 'l')
    return c


def assembler_bay(frame=None):
    """Back wall of the bay: the gantry rail, the carriage, arm and torch, the part on its bed. No frame: parked."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'c')
    c.rect(3, 3, 10, 1, 'a')
    for x in range(4, 12, 3):                                            # back wall panel seams
        c.rect(x, 6, 1, 4, 'b')
    c.rect(3, 4, 10, 1, 'd').rect(3, 5, 10, 1, 'a')                      # gantry rail
    c.rect(3, 11, 10, 1, 'a')                                            # bed
    c.rect(AS_LEFT, 10, AS_RIGHT - AS_LEFT + 1, 1, 'q')                  # the part
    c.set(AS_LEFT - 1, 10, 'p').set(AS_RIGHT + 1, 10, 'p')
    x, head, _ = as_torch(frame) if frame is not None else (AS_PARK, 6, False)
    c.rect(x - 1, 4, 3, 2, 'q').set(x + 1, 5, 'p').set(x - 1, 4, 'e')   # carriage on the rail
    c.rect(x, 6, 1, head - 6, 'e')                                       # telescoping arm
    c.rect(x - 1, head, 3, 1, 'q').set(x + 1, head, 'p')                 # torch head
    c.set(x, head + 1, 'K')                                              # nozzle
    return c


def assembler_bay_glow(frame):
    """The spark at the nozzle flaring as it reaches each pixel, the seam cooling behind it, the carriage light."""
    c = Canvas()
    x, head, welding = as_torch(frame)
    for sx in range(AS_LEFT, AS_RIGHT + 1):                              # frame each seam pixel was welded
        age = (frame - (2 + (sx - AS_LEFT) * 2)) % AS_FRAMES
        if age < len(AS_SEAM):
            c.set(sx, 10, AS_SEAM[age])
    if welding:
        c.set(x, head + 1, 'x').set(x, 10, 'x')
        flare = 'w' if frame % 2 == 0 else 'v'
        c.set(x - 1, head + 1, flare).set(x + 1, head + 1, flare)
    c.set(x, 4, 'w' if welding else 'v')
    return c


def assembler_front_glow():
    """The status lamp, lit full-bright."""
    c = Canvas()
    c.set(12, 12, 'L')
    return c


def assembler_bay_wall():
    """Bay walls and floor: rows 0-8 the side wall panel, rows 9-15 the floor (lit front edge first)."""
    c = Canvas()
    c.rect(0, 0, 16, 9, 'b')
    c.rect(0, 0, 16, 1, 'a').rect(0, 4, 16, 1, 'a')
    c.rect(0, 9, 16, 7, 'a')
    c.rect(0, 9, 16, 1, 'c')
    return c


def assembler_top(tier):
    """Parts hatch: two doors over the cell, a cyan seam between them, feed lights at the corners."""
    c = casing.shell(tier)
    c.recess(4, 4, 8, 8, 'abcde', fill='c')
    c.rect(7, 5, 2, 6, 'u')
    c.rect(5, 6, 1, 4, 'b').rect(10, 6, 1, 4, 'b')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'u')
    return c


def assembler_top_glow():
    c = Canvas()
    c.rect(7, 5, 2, 6, 'v').rect(7, 7, 2, 2, 'w')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'w')
    return c


# RTG: a shielded radioisotope core. A window 2 px deep in the gunmetal casing (model: industry_data.rtg_model) shows
# a column of three yellow-green thorium pellets held between steel clamps from floor to roof; deep radiator fins on
# the sides and a finned heat sink round a core port on top. Idle the pellets are cold yellow-green ceramic; working
# they glow toxic yellow-green and breathe slowly, the halo in the window and the top port breathing with them, and
# each breath sends a ripple out across the heat sink. Chars on top of the casing: '0'-'9' toxic glow (deep -> white
# core, enough steps for a smooth breath), 's t y' cold pellet (shadow, body, highlight), 'S T' clamp steel.

RTG_BODY = ('#1E2328', '#323A42', '#4C565F', '#6C7882', '#97A3AD')      # generator gunmetal
RTG_GLOW = ('#121A04', '#1E2E06', '#34500A', '#4E760C', '#6A9A0E', '#88BC14', '#A8DC1C', '#C6F238', '#E2FF6A',
            '#FAFFD0')
RTG_FRAMES, RTG_FRAMETIME = 32, 2                                        # one breath: 64 ticks
RTG_WINDOW = (4, 3, 8, 10)                                               # x, y, w, h of the window on the front
# Core atlas regions (x, y, w, h); industry_data.rtg_model maps the model faces onto them. The column's sides use its
# front's outer columns.
RTG_PARTS = {'back': (0, 0, 8, 10), 'wall_left': (8, 0, 2, 10), 'wall_right': (10, 0, 2, 10),
             'column': (12, 0, 4, 10), 'roof': (0, 10, 8, 2), 'floor': (0, 12, 8, 2)}
RTG_CLAMPS = (0, 3, 6, 9)                                                # column rows with a clamp, pellets between


def rtg_pal():
    a, b, c, d, e = RTG_BODY
    return casing.casing_palette(casing.INDUSTRY_TIERS[1], a=a, b=b, c=c, d=d, e=e,
                                 s='#7E9E20', t='#B4D838', y='#E2F870', S='#8A96A0', T='#C2CCD4',
                                 **{str(i): col for i, col in enumerate(RTG_GLOW)})


def rtg_breath(t):
    """1 (peak, at frame 0) .. 0 (dim) and back over the loop, a smooth cosine."""
    return 0.5 + 0.5 * math.cos(t / RTG_FRAMES * 2 * math.pi)


def rtg_level(level):
    """The toxic glow char of a brightness level 0..9."""
    return str(max(0, min(9, round(level))))


def rtg_core(lit, t=0):
    """The window's faces: back wall, side walls, roof, floor and the pellet column (RTG_PARTS)."""
    R = RTG_PARTS
    c = Canvas()
    b = rtg_breath(t)
    x, y, w, h = R['back']
    if lit:                                                              # the column's halo, brightest next to it
        for xx in range(w):
            c.rect(x + xx, y, 1, h, rtg_level(3 + 3 * b if xx in (1, w - 2) else 2 + 2 * b))
    else:                                                                # steel back plate with clamp rails
        c.rect(x, y, w, h, 'd').rect(x, y, 1, h, 'c').rect(x + w - 1, y, 1, h, 'e')
        for yy in RTG_CLAMPS:
            c.rect(x + 1, y + yy, w - 2, 1, 'c')
    for name, front in (('wall_left', 0), ('wall_right', 1)):            # side walls: the front edge catches light
        x, y, w, h = R[name]
        if lit:
            c.rect(x, y, w, h, rtg_level(1 + 2 * b)).rect(x + front, y, 1, h, rtg_level(2 + 2 * b))
        else:
            c.rect(x, y, w, h, 'b').rect(x + front, y, 1, h, 'c')
    x, y, w, h = R['roof']
    c.rect(x, y, w, h, rtg_level(1 + b) if lit else 'a').rect(x, y + 1, w, 1, rtg_level(1 + 2 * b) if lit else 'b')
    x, y, w, h = R['floor']
    c.rect(x, y, w, h, rtg_level(2 + 2 * b) if lit else 'c').rect(x, y, w, 1, rtg_level(3 + 2 * b) if lit else 'd')
    x, y, w, h = R['column']                                             # pellets: a cylinder lit from the left
    if lit:
        c.rect(x, y, w, h, rtg_level(5 + 3 * b)).rect(x + 1, y, 1, h, rtg_level(6 + 3 * b)).rect(x + w - 1, y, 1, h, rtg_level(4 + 2 * b))
    else:
        c.rect(x, y, w, h, 't').rect(x + 1, y, 1, h, 'y').rect(x + w - 1, y, 1, h, 's')
    for yy in RTG_CLAMPS:                                                # clamps: steel, dark graphite while it glows
        c.rect(x, y + yy, w, 1, 'b' if lit else 'S').set(x, y + yy, 'c' if lit else 'T')
        c.set(x + w - 1, y + yy, 'a' if lit else 'd')
    return c


def rtg_front(lit):
    """Gunmetal casing with the window (transparent: the core shows through) between two shield lips."""
    x0, y0, w, h = RTG_WINDOW
    c = casing.shell(1)
    c.rect(x0, y0, w, h, '.')
    c.rect(3, 3, 1, 10, 'd').rect(12, 3, 1, 10, 'b').set(3, 3, 'e')      # shield lips, lit from the top-left
    c.set(12, 12, 'L' if lit else 'l')
    return c


def rtg_front_glow(t):
    """Front overlay: the lips catch the breathing glow; the status lamp."""
    c = Canvas()
    b = rtg_breath(t)
    c.rect(3, 3, 1, 10, rtg_level(3 + 3 * b)).rect(12, 3, 1, 9, rtg_level(2 + 2 * b))
    c.set(12, 12, 'L')
    return c


def rtg_side():
    """Radiator: the shared energy channel between two banks of deep vertical fins."""
    c = casing.shell(1)
    for x in (3, 5, 10, 12):                                             # fins: lit face, dark gap behind
        c.rect(x, 3, 1, 10, 'd').set(x, 3, 'e')
    for x in (4, 11):
        c.rect(x, 3, 1, 10, 'K')
    c.rect(3, 12, 3, 1, 'b').rect(10, 12, 3, 1, 'b')
    c.rect(6, 2, 4, 12, 'b')                                             # channel housing, as casing.side
    c.rect(6, 2, 1, 12, 'a').rect(9, 2, 1, 12, 'd')
    c.rect(7, 2, 2, 12, 'u')
    c.rect(7, 2, 2, 1, 'k').rect(7, 13, 2, 1, 'k')
    return c


def rtg_top():
    """Heat sink: rows of fins round a core port of yellow-green glass."""
    c = casing.shell(1)
    c.recess(3, 3, 10, 10, 'abcde', fill='b')
    for y in range(4, 12, 2):
        c.rect(4, y, 8, 1, 'd').rect(4, y + 1, 8, 1, 'a').set(4, y, 'e')
    c.rect(5, 5, 6, 6, 'c').bevel(5, 5, 6, 6, 'e', 'a')                  # port collar
    c.rect(6, 6, 4, 4, 't').rect(6, 6, 4, 1, 'y').rect(6, 6, 1, 4, 'y').rect(9, 7, 1, 3, 's').rect(7, 9, 3, 1, 's')
    return c


def rtg_top_glow(t):
    """The core port breathing with the pellets; from each peak a ripple runs out along the fin gaps and fades."""
    c = Canvas()
    b = rtg_breath(t)
    c.rect(6, 6, 4, 4, rtg_level(4 + 3 * b)).rect(7, 7, 2, 2, rtg_level(5 + 4 * b))
    r = t / 3.0                                                          # px travelled since the peak
    for y in range(5, 12, 2):
        for x in range(4, 12):
            if 5 <= x <= 10 and 5 <= y <= 10:
                continue
            d = max(abs(x - 7.5), abs(y - 7.5)) - 2.5
            v = max(0.0, 1 - abs(r - d) / 1.2) * max(0.0, 1 - d / 3.5)
            if v > 0.12:
                c.set(x, y, rtg_level(1 + 6 * v))
    return c


def write_rtg():
    p = rtg_pal()
    frames = range(RTG_FRAMES)
    write_block('rtg_front', rtg_front(False).rows(), p)
    write_block('rtg_front_on', rtg_front(True).rows(), p)
    write_block('rtg_side', rtg_side().rows(), p)
    write_block('rtg_bottom', casing.bottom().rows(), p)
    write_block('rtg_top', rtg_top().rows(), p)
    write_block('rtg_core', rtg_core(False).rows(), p)
    write_anim('block', 'rtg_core_on', [rtg_core(True, t) for t in frames], p, frametime=RTG_FRAMETIME,
               interpolate=True)
    write_anim('block', 'rtg_front_glow', [rtg_front_glow(t) for t in frames], p, frametime=RTG_FRAMETIME,
               interpolate=True)
    write_anim('block', 'rtg_top_glow', [rtg_top_glow(t) for t in frames], p, frametime=RTG_FRAMETIME,
               interpolate=True)


def write_smelter():
    frames = range(SMELTER_FRAMES)
    for tier in (1, 2, 3, 4):
        p = smelter_pal(tier)
        write_block(f'alloy_smelter_mk{tier}_front', smelter_front(tier, False).rows(), p)
        write_block(f'alloy_smelter_mk{tier}_front_on', smelter_front(tier, True).rows(), p)
        write_block(f'alloy_smelter_mk{tier}_top', smelter_top(tier).rows(), p)
    p = smelter_pal()
    write_block('alloy_smelter_chamber', smelter_chamber(False).rows(), p)
    write_block('alloy_smelter_parts', smelter_parts(False).rows(), p)
    write_anim('block', 'alloy_smelter_parts_on', [smelter_parts(True, t) for t in frames], p,
               frametime=SMELTER_FRAMETIME, interpolate=True)
    write_anim('block', 'alloy_smelter_chamber_on', [smelter_chamber(True, t) for t in frames], p,
               frametime=SMELTER_FRAMETIME, interpolate=True)
    write_still('block', 'alloy_smelter_front_glow', smelter_glow(), p)
    write_anim('block', 'alloy_smelter_top_glow', [smelter_top_glow(t) for t in frames], p,
               frametime=SMELTER_FRAMETIME, interpolate=True)


def write_centrifuge():
    for tier in (1, 2, 3, 4):
        p = cf_pal(tier)
        write_block(f'centrifuge_mk{tier}_front', centrifuge_front(tier, False).rows(), p)
        write_block(f'centrifuge_mk{tier}_front_on', centrifuge_front(tier, True).rows(), p)
        write_block(f'centrifuge_mk{tier}_top', centrifuge_top(tier).rows(), p)
    p = cf_pal()
    write_block('centrifuge_drum', centrifuge_drum().rows(), p)
    write_anim('block', 'centrifuge_front_glow', [centrifuge_glow(i) for i in range(CF_FRAMES)], p,
               frametime=CF_FRAMETIME, interpolate=True)
    write_anim('block', 'centrifuge_top_glow', [centrifuge_top_glow(i) for i in range(CF_FRAMES)], p,
               frametime=CF_FRAMETIME, interpolate=True)


def write_assembler():
    for tier in (1, 2, 3, 4):
        p = as_pal(tier)
        write_block(f'assembler_mk{tier}_front', assembler_front(tier, False).rows(), p)
        write_block(f'assembler_mk{tier}_front_on', assembler_front(tier, True).rows(), p)
        write_block(f'assembler_mk{tier}_top', assembler_top(tier).rows(), p)
    p = as_pal()
    write_block('assembler_bay', assembler_bay().rows(), p)
    write_block('assembler_bay_wall', assembler_bay_wall().rows(), p)
    cycle = [(i + AS_PHASE) % AS_FRAMES for i in range(AS_FRAMES)]
    write_anim('block', 'assembler_bay_on', [assembler_bay(i) for i in cycle], p, frametime=AS_FRAMETIME)
    write_anim('block', 'assembler_bay_glow', [assembler_bay_glow(i) for i in cycle], p,
               frametime=AS_FRAMETIME)
    write_block('assembler_front_glow', assembler_front_glow().rows(), p)
    write_block('assembler_top_glow', assembler_top_glow().rows(), p)


def blocks_machines():
    for tier in (1, 2, 3, 4):
        write_block(f'industry_side_mk{tier}', casing.side(tier).rows(), casing.casing_palette(casing.INDUSTRY_TIERS[tier]))
    write_block('industry_bottom', casing.bottom().rows(), casing.casing_palette(casing.INDUSTRY_TIERS[1]))
    casing.write_side_glow()
    write_smelter()
    write_centrifuge()
    write_assembler()
    write_rtg()


def main():
    items()
    blocks_ores()
    blocks_machines()
    print('industry textures written')


if __name__ == '__main__':
    main()
