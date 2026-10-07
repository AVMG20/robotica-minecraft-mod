"""Industry module textures: ores, materials, alloys, fuel pellets, Assembler parts, the processing machines (Mk1-Mk4)
and the RTG. Run: python3 scripts/textures/industry.py
(writes textures/item/*.png with item models when missing, textures/block/*.png; block models come from
scripts/data/industry_data.py)

Items follow the core item style: one dark outline 'k', light from the top-left (Canvas.auto_shade), a highlight pixel
on the lit side. Chars: '12345' main material (deep -> highlight), 'abcde' steel, 'y Y z' glow (mid, bright, white).
Machines are dark slate boxes with a trim in their Mk colour (Mk1 copper, Mk2 ferrothorium, Mk3 pyrosteel,
Mk4 resonant alloy); what glows (molten metal, rotor, welding spark, the RTG core) sits on a full-bright overlay.
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, fbm, hash01, material, write_anim, write_block, write_item  # noqa: E402

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
# Chars: 'abcde' slate body, 'fghij' tier trim, 'k K' dark, 'r R' hazard, 'y Y z Z' glow, 'l L' leds off/on.

TIER_TRIM = {1: MATERIALS['copper'], 2: RAMPS['ferrothorium'], 3: RAMPS['pyrosteel'], 4: RAMPS['resonant']}
MACHINE_GLOW = {'alloy_smelter': 'amber', 'centrifuge': 'green', 'assembler': 'cyan', 'rtg': 'green'}


def mpal(tier=1, glow='amber'):
    p = {'k': '#121416', 'K': '#07080A', 'r': '#E8B530', 'R': '#2A2418', 'l': '#1C4A22', 'L': '#5CFF6A'}
    p.update(material('abcde', 'slate'))
    p.update(dict(zip('fghij', TIER_TRIM[tier])))
    g = GLOWS[glow]
    p.update({'Z': g[0], 'y': g[1], 'Y': g[2], 'z': g[3]})
    p.update(dict(zip('mnopq', MATERIALS['steel'])))
    return p


def shell(seed=0):
    """Slate panel in a 2 px tier-coloured frame with corner bolts."""
    c = Canvas()
    c.plate(0, 0, 16, 16, 'fghij', seed, brushed=False)
    c.rect(1, 1, 14, 14, 'h').rect(1, 14, 14, 1, 'g').rect(14, 1, 1, 14, 'g')
    c.plate(2, 2, 12, 12, 'abcde', seed + 2, density=0.25)
    c.rect(2, 2, 12, 1, 'a').rect(2, 2, 1, 12, 'a')
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.rect(x, y, 2, 2, 'i').set(x, y, 'j').set(x + 1, y + 1, 'g')
    return c


def side(tier):
    c = shell(10 + tier)
    c.recess(4, 4, 8, 8, 'abcde', fill='b')
    for y in range(5, 11, 2):
        c.rect(5, y, 6, 1, 'K').rect(5, y + 1, 6, 1, 'd')
    for i in range(tier):                                                # Mk pips
        c.set(3 + i * 3, 13, 'j').set(4 + i * 3, 13, 'i')
    return c


def bottom():
    c = Canvas()
    c.plate(0, 0, 16, 16, 'abcde', 40, density=0.25)
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):
        c.rect(x, y, 3, 3, 'a').set(x + 1, y + 1, 'k')
    return c


def smelter_front(tier, lit):
    """Crucible window with molten metal, a pouring spout above, two feed hoppers at the top corners."""
    c = shell(20 + tier)
    c.rect(3, 2, 3, 2, 'n').rect(10, 2, 3, 2, 'n').set(3, 2, 'p').set(10, 2, 'p')
    c.recess(4, 5, 8, 7, 'abcde', fill='K')
    if lit:
        c.vgradient(5, 8, 6, 3, 'yYZ')
    else:
        c.rect(5, 9, 6, 2, 'Z')
    c.rect(7, 4, 2, 2, 'm').set(7, 4, 'o')
    c.rect(4, 12, 8, 1, 'R').hazard(4, 12, 8, 1, 'r', 'R')
    c.set(12, 3, 'L' if lit else 'l')
    return c


def smelter_glow(frame):
    c = Canvas()
    for x in range(5, 11):
        h = 2 + int(1.5 + 1.5 * math.sin(frame * 1.3 + x * 1.7))
        for i in range(h):
            c.set(x, 10 - i, 'z' if i == 0 and (x + frame) % 3 == 0 else 'Y' if i < 2 else 'y')
    c.set(7 + frame % 2, 6 + frame % 3, 'Y')                             # dripping metal
    c.set(12, 3, 'L')
    return c


def centrifuge_front(tier, lit):
    """Round porthole with a three-arm rotor inside, a gauge strip below."""
    c = shell(30 + tier)
    c.disc(7.5, 7.0, 5.0, 'm')
    c.disc(7.5, 7.0, 4.2, 'K')
    c.ring(7.5, 7.0, 4.2, 5.0, 'o')
    for a in range(3):
        ang = a * 2 * math.pi / 3
        c.line(7, 7, round(7.5 + 3.3 * math.cos(ang)), round(7 + 3.3 * math.sin(ang)), 'c')
    c.disc(7.5, 7.0, 1.1, 'e')
    c.rect(4, 13, 8, 1, 'K')
    if lit:
        c.rect(4, 13, 5, 1, 'y')
    c.set(13, 3, 'L' if lit else 'l')
    return c


def centrifuge_glow(frame):
    c = Canvas()
    for a in range(3):
        ang = a * 2 * math.pi / 3 + frame * math.pi / 6
        for r in (1.6, 2.4, 3.2):
            c.set(round(7.5 + r * math.cos(ang)), round(7.0 + r * math.sin(ang)), 'Y' if r < 3 else 'y')
    c.set(7, 7, 'z')
    for i in range(1 + frame % 6):
        c.set(4 + i, 13, 'y')
    c.set(13, 3, 'L')
    return c


def assembler_front(tier, lit):
    """Work cell window: a gantry rail, a robot arm with a welding tip over a part on the bed."""
    c = shell(40 + tier)
    c.recess(3, 3, 10, 9, 'abcde', fill='K')
    c.rect(4, 4, 8, 1, 'n').set(4, 4, 'p')                               # gantry
    c.rect(8, 5, 1, 3, 'm').rect(7, 7, 3, 1, 'o').set(8, 8, 'c')         # arm and head
    c.rect(4, 10, 8, 1, 'c').rect(6, 9, 4, 1, 'h')                       # bed and part
    c.hazard(3, 12, 10, 1, 'r', 'R')
    c.set(13, 13, 'L' if lit else 'l')
    return c


def assembler_glow(frame):
    c = Canvas()
    x = 6 + (frame % 4)
    c.set(x, 8, 'z')
    for dx, dy in ((-1, 0), (1, 0), (0, -1)):
        if (frame + dx) % 2 == 0:
            c.set(x + dx, 8 + dy, 'Y')
    c.set(x - 1 + frame % 3, 9, 'y')
    c.set(13, 13, 'L')
    return c


def top(machine):
    c = shell(50 + len(machine))
    if machine == 'alloy_smelter':                                       # chimney grille
        c.disc(7.5, 7.5, 4.6, 'a')
        c.grille(4, 4, 8, 8, 'K', 'd')
    elif machine == 'centrifuge':                                        # round lid, hazard ring
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.4 <= d <= 5.6:
                    c.set(x, y, 'r' if int((math.atan2(y - 7.5, x - 7.5) + 3.2) * 2.6) % 2 else 'R')
        c.disc(7.5, 7.5, 4.0, 'c').ring(7.5, 7.5, 2.5, 3.2, 'b').disc(7.5, 7.5, 1.2, 'e')
    else:                                                                # parts hatch with a grid
        c.recess(3, 3, 10, 10, 'abcde', fill='b')
        for i in range(4, 12, 3):
            c.rect(i, 4, 1, 8, 'a').rect(4, i, 8, 1, 'a')
    return c


def rtg_front():
    """Shielded cask: a round viewport over the glowing pellet stack with a trefoil, cooling ribs on both sides."""
    c = shell(60)
    for x in (2, 3, 12, 13):
        for y in range(3, 13, 2):
            c.set(x, y, 'K')
    c.disc(7.5, 7.5, 3.8, 'm').disc(7.5, 7.5, 3.0, 'K')
    c.ring(7.5, 7.5, 3.0, 3.8, 'o')
    for a in range(3):                                                   # dim trefoil
        ang = a * 2 * math.pi / 3 - math.pi / 2
        c.set(round(7.5 + 1.8 * math.cos(ang)), round(7.5 + 1.8 * math.sin(ang)), 'Z')
    c.set(7, 7, 'Z')
    c.hazard(4, 13, 8, 1, 'r', 'R')
    return c


def rtg_front_glow():
    c = Canvas()
    for a in range(3):
        ang = a * 2 * math.pi / 3 - math.pi / 2
        for r in (1.2, 1.8, 2.4):
            c.set(round(7.5 + r * math.cos(ang)), round(7.5 + r * math.sin(ang)), 'y' if r > 2 else 'Y')
    c.set(7, 7, 'z').set(8, 8, 'Y')
    return c


def rtg_side():
    """Radiator fins: deep vertical ribs."""
    c = shell(61)
    for x in range(3, 13, 2):
        c.rect(x, 3, 1, 10, 'e').rect(x + 1, 3, 1, 10, 'K')
    return c


def rtg_top():
    c = shell(62)
    c.disc(7.5, 7.5, 4.6, 'b').disc(7.5, 7.5, 3.4, 'K')
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.set(x, y, 'Z')
    return c


def rtg_top_glow():
    c = Canvas()
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.set(x, y, 'y')
    c.disc(7.5, 7.5, 1.0, 'Y')
    return c


def blocks_machines():
    for tier in (1, 2, 3, 4):
        write_block(f'industry_side_mk{tier}', side(tier).rows(), mpal(tier))
    write_block('industry_bottom', bottom().rows(), mpal())
    fronts = {'alloy_smelter': smelter_front, 'centrifuge': centrifuge_front, 'assembler': assembler_front}
    glows = {'alloy_smelter': (smelter_glow, 8, 3), 'centrifuge': (centrifuge_glow, 12, 1), 'assembler': (assembler_glow, 8, 2)}
    for machine, front in fronts.items():
        g = MACHINE_GLOW[machine]
        for tier in (1, 2, 3, 4):
            write_block(f'{machine}_mk{tier}_front', front(tier, False).rows(), mpal(tier, g))
            write_block(f'{machine}_mk{tier}_front_on', front(tier, True).rows(), mpal(tier, g))
        fn, frames, ft = glows[machine]
        write_anim('block', f'{machine}_front_glow', [fn(i) for i in range(frames)], mpal(1, g), frametime=ft)
        write_block(f'{machine}_top', top(machine).rows(), mpal(2, g))
    p = mpal(2, 'green')
    write_block('rtg_front', rtg_front().rows(), p)
    write_block('rtg_front_glow', rtg_front_glow().rows(), p)
    write_block('rtg_side', rtg_side().rows(), p)
    write_block('rtg_top', rtg_top().rows(), p)
    write_block('rtg_top_glow', rtg_top_glow().rows(), p)


def main():
    items()
    blocks_ores()
    blocks_machines()
    print('industry textures written')


if __name__ == '__main__':
    main()
