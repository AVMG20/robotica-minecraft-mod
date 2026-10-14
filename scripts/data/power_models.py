"""Writes blockstates, block/item models and loot tables of the power module.
Run: python3 scripts/data/power_models.py   (overwrites files named after power blocks only)"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from machine_models import machine_model as casing_model  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def block_model(name, obj):
    write(ASSETS / 'models/block' / f'{name}.json', obj)


def item_model(name, parent):
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': parent})


def blockstate(name, obj):
    write(ASSETS / 'blockstates' / f'{name}.json', obj)


def loot(name, copy_energy=False):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}'}
    if copy_energy:
        entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['robotica:energy']}]
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def facing_variants(model_for, extra=()):
    """model_for(extra_values dict) -> model name; yields every facing x extra combination."""
    variants = {}
    combos = [{}]
    for prop, values in extra:
        combos = [dict(c, **{prop: v}) for c in combos for v in values]
    for combo in combos:
        for facing, y in FACING_Y.items():
            key = ','.join([f'facing={facing}'] + [f'{k}={str(v).lower()}' for k, v in combo.items()])
            entry = {'model': f'robotica:block/{model_for(combo)}'}
            if y:
                entry['y'] = y
            variants[key] = entry
    return variants


# ---------- orientable machines (generator, press, charger) ----------
# One cube with per-machine top, shared sides and bottom, plus a coplanar full-bright overlay for the glowing parts
# when lit (fire, die seam, coil). Front faces north; the blockstate turns it.
GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')


def machine_model(textures, glow=None):
    """textures: face -> texture name; glow: face -> overlay texture name (drawn full bright)."""
    tex_vars = {f: tex(t) for f, t in textures.items()}
    tex_vars['particle'] = tex(textures['south'])
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16],
                 'faces': {d: {'texture': '#' + d, 'cullface': d} for d in DIRS}}]
    if glow:
        for f, t in glow.items():
            tex_vars['glow_' + f] = tex(t)
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {d: {'texture': '#glow_' + d, 'cullface': d, 'neoforge_data': GLOW} for d in glow}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': tex_vars, 'elements': elements}


# ---------- combustion generator: the shared casing in the generators' gunmetal with a fire window 2 px deep in the
# front (GEN_BAY in scripts/textures/power.py: x 3..12, y 3..10 of the front texture); the firebox faces come from one
# chamber texture (GEN_PARTS there), full-bright while working; idle, a glass pane in front shows its glare.
def generator_model(lit):
    faces = {'north': 'combustion_generator_front' + ('_on' if lit else ''), 'up': 'combustion_generator_top',
             'down': 'combustion_generator_bottom', 'south': 'combustion_generator_side',
             'west': 'combustion_generator_side', 'east': 'combustion_generator_side'}
    x0, x1, y0, y1, z = 3, 13, 5, 13, 2                                   # bay in model space

    def face(d, uv):
        return {'from': [x0 if d != 'west' else x1, y0 if d != 'down' else y1, 0 if d != 'north' else z],
                'to': [x1 if d != 'east' else x0, y1 if d != 'up' else y0, z],
                'faces': {d: {'texture': '#chamber', 'uv': uv}}, 'glow': lit}
    bay = [face('north', [3, 3, 13, 11]), face('east', [14, 3, 16, 11]), face('west', [0, 3, 2, 11]),
           face('up', [13, 0, 3, 2]), face('down', [13, 14, 3, 16])]
    textures = {'chamber': 'combustion_generator_chamber' + ('_on' if lit else '')}
    if not lit:                                                           # the fire outshines the glass glare
        bay.append({'from': [x0, y0, 0.5], 'to': [x1, y1, 0.5],
                    'faces': {'north': {'texture': '#glass', 'uv': [3, 3, 13, 11]}}})
        textures['glass'] = 'combustion_generator_glass'
    glow = {'north': 'combustion_generator_front_glow', 'up': 'combustion_generator_top_glow'} if lit else None
    return casing_model(faces, glow, bay, textures, lit=lit)


# ---------- charger: the shared casing in light steel with a charging dock 2 px deep in the front (dock texture layout in
# scripts/textures/power.py): back wall at z 2, four walls, and the power cell standing 1.5 px proud of the back wall.
# Working, a full-bright glow on the back wall and the cell. Mk2-Mk4 (charger_mk2 ...) swap in their tier textures.
def tier_name(base, tier):
    return base if tier == 1 else f'{base}_mk{tier}'


def charger_model(lit, tier=1):
    n = tier_name('charger', tier)
    faces = {'north': f'{n}_front' + ('_on' if lit else ''), 'up': f'{n}_top', 'down': 'charger_bottom',
             'south': f'{n}_side', 'west': f'{n}_side', 'east': f'{n}_side'}
    d = '#dock'
    elements = [
        {'from': [3, 4, 2], 'to': [13, 13, 3], 'faces': {'north': {'texture': d, 'uv': [3, 3, 13, 12]}}},
        {'from': [3, 13, 0], 'to': [13, 14, 2], 'faces': {'down': {'texture': d, 'uv': [13, 1, 3, 2]}}},
        {'from': [3, 3, 0], 'to': [13, 4, 2], 'faces': {'up': {'texture': d, 'uv': [13, 14, 3, 15]}}},
        {'from': [13, 4, 0], 'to': [14, 13, 2], 'faces': {'west': {'texture': d, 'uv': [1, 3, 2, 12]}}},
        {'from': [2, 4, 0], 'to': [3, 13, 2], 'faces': {'east': {'texture': d, 'uv': [14, 3, 15, 12]}}},
        {'from': [5, 5, 0.5], 'to': [11, 12, 2], 'faces': {'north': {'texture': d, 'uv': [5, 4, 11, 11]},
                                                        'up': {'texture': d, 'uv': [5, 12, 11, 13]},
                                                        'east': {'texture': d, 'uv': [2, 4, 3, 11]},
                                                        'west': {'texture': d, 'uv': [2, 4, 3, 11]}}},
    ]
    textures = {'dock': f'{n}_dock'}
    if lit:
        textures['dock_glow'] = 'charger_dock_glow'
        elements += [
            {'from': [3, 4, 1.99], 'to': [13, 13, 2], 'glow': True,
             'faces': {'north': {'texture': '#dock_glow', 'uv': [3, 3, 13, 12]}}},
            {'from': [5, 5, 0.49], 'to': [11, 12, 0.5], 'glow': True,
             'faces': {'north': {'texture': '#dock_glow', 'uv': [5, 4, 11, 11]}}},
        ]
    glow = {'north': 'charger_front_glow', 'up': 'charger_top_glow'} if lit else None
    return casing_model(faces, glow, elements, textures, lit=lit)


ORIENTABLE = [('combustion_generator', generator_model)]
ORIENTABLE += [(tier_name('charger', t), lambda lit, t=t: charger_model(lit, t)) for t in (1, 2, 3, 4)]
for name, model_fn in ORIENTABLE:
    for lit in (False, True):
        block_model(name + ('_on' if lit else ''), model_fn(lit))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n + ('_on' if c['lit'] else ''), [('lit', [False, True])])})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- metal press: the processing machines' shared casing (scripts/data/machine_models.py) with a press bay 2 px
# deep in the front: back wall at z 2 and four walls, all from the metal_press_bay texture (the back wall in 4..11 x
# 3..12, the walls around it); lit, the animated bay plus its full-bright glow on the back wall and the floor.
# Mk2-Mk4 (metal_press_mk2 ...) swap in their tier textures.
def press_model(lit, tier=1):
    n = tier_name('metal_press', tier)
    faces = {'north': f'{n}_front' + ('_on' if lit else ''), 'up': f'{n}_top', 'down': 'metal_press_bottom',
             'south': f'{n}_side', 'west': f'{n}_side', 'east': f'{n}_side'}
    bay = '#bay'
    elements = [
        {'from': [4, 3, 2], 'to': [12, 13, 3], 'faces': {'north': {'texture': bay, 'uv': [4, 3, 12, 13]}}},
        {'from': [4, 13, 0], 'to': [12, 14, 2], 'faces': {'down': {'texture': bay, 'uv': [12, 0, 4, 2]}}},
        {'from': [4, 2, 0], 'to': [12, 3, 2], 'faces': {'up': {'texture': bay, 'uv': [12, 14, 4, 16]}}},
        {'from': [12, 3, 0], 'to': [13, 13, 2], 'faces': {'west': {'texture': bay, 'uv': [0, 3, 2, 13]}}},
        {'from': [3, 3, 0], 'to': [4, 13, 2], 'faces': {'east': {'texture': bay, 'uv': [14, 3, 16, 13]}}},
    ]
    textures = {'bay': 'metal_press_bay_on' if lit else 'metal_press_bay'}
    if lit:
        textures['bay_glow'] = 'metal_press_bay_glow'
        elements += [
            {'from': [4, 3, 1.99], 'to': [12, 13, 2], 'glow': True,
             'faces': {'north': {'texture': '#bay_glow', 'uv': [4, 3, 12, 13]}}},
            {'from': [4, 2, 0], 'to': [12, 3.01, 2], 'glow': True,
             'faces': {'up': {'texture': '#bay_glow', 'uv': [12, 14, 4, 16]}}},
        ]
    glow = {'north': 'metal_press_front_glow', 'up': 'metal_press_top_glow'} if lit else None
    return casing_model(faces, glow, elements, textures, lit=lit)


for t in (1, 2, 3, 4):
    name = tier_name('metal_press', t)
    for lit in (False, True):
        block_model(name + ('_on' if lit else ''), press_model(lit, t))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n + ('_on' if c['lit'] else ''), [('lit', [False, True])])})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- wireless charger: the shared casing in light steel plus an emitter mast on top: a collar, a rod through a
# halo ring and a violet orb (full bright when lit). Mast parts come from one texture (wireless_mast in
# scripts/textures/power.py says where). Mk2-Mk4 swap in their tier textures. The block state `face` turns the model so the
# mast points up (floor), down (ceiling) or out of a wall towards `facing` (the front then faces down).
def wireless_model(lit, tier=1):
    on = '_on' if lit else ''
    n = tier_name('wireless_charger', tier)
    faces = {'north': f'{n}_front' + on, 'up': f'{n}_top' + on, 'down': 'wireless_charger_bottom',
             'south': f'{n}_side', 'west': f'{n}_side', 'east': f'{n}_side'}
    sides = ('north', 'south', 'west', 'east')
    elements = [
        {'from': [6, 16, 6], 'to': [10, 17, 10],
         'faces': dict({d: {'texture': '#mast', 'uv': [0, 4, 4, 5]} for d in sides}, up={'texture': '#mast', 'uv': [0, 0, 4, 4]})},
        {'from': [7.25, 17, 7.25], 'to': [8.75, 19.5, 8.75],
         'faces': {d: {'texture': '#mast', 'uv': [12, 2, 14, 5]} for d in sides}},
        {'from': [5, 18, 5], 'to': [11, 18, 11], 'glow': lit,
         'faces': {d: {'texture': '#mast', 'uv': [5, 5, 11, 11]} for d in ('up', 'down')}},
        {'from': [6.75, 19.5, 6.75], 'to': [9.25, 22, 9.25], 'glow': lit,
         'faces': {d: {'texture': '#orb', 'uv': [5, 5, 11, 11]} for d in DIRS}},
    ]
    textures = {'mast': 'wireless_charger_mast' + on, 'orb': 'wireless_charger_orb' + on}
    glow = {'north': 'wireless_charger_front_glow', 'up': 'wireless_charger_top_glow'} if lit else None
    return casing_model(faces, glow, elements, textures, lit=lit)


def attached_variants(name):
    """Variants for face (floor / ceiling / wall) x facing x lit: x turns the mast, y the front."""
    variants = {}
    for face, x in (('floor', 0), ('ceiling', 180), ('wall', 90)):
        for facing, y in FACING_Y.items():
            if face == 'ceiling':
                y = (y + 180) % 360                                          # x 180 turns the front to the south
            for lit in (False, True):
                entry = {'model': f'robotica:block/{name}' + ('_on' if lit else '')}
                if x:
                    entry['x'] = x
                if y:
                    entry['y'] = y
                variants[f'face={face},facing={facing},lit={str(lit).lower()}'] = entry
    return variants


for tier in (1, 2, 3, 4):
    name = tier_name('wireless_charger', tier)
    for lit in (False, True):
        block_model(name + ('_on' if lit else ''), wireless_model(lit, tier))
    blockstate(name, {'variants': attached_variants(name)})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- accumulators: the gauge shows the charge (block state `charge` 0-5, one lit cell per level) ----------
ACC_CELLS = 5


def accumulator_model(tier, level):
    """The casing cube in light steel; charged, the cells on the front and sides fill on full-bright overlays and the
    cell heads on top light up."""
    name = f'accumulator_{tier}'
    faces = {'north': f'{name}_front', 'up': f'{name}_top', 'down': 'accumulator_bottom',
             'south': f'{name}_side', 'west': f'{name}_side', 'east': f'{name}_side'}
    glow = None
    if level:
        side_glow = f'accumulator_side_glow_{level}'
        glow = {'north': f'accumulator_front_glow_{level}', 'up': f'accumulator_top_glow_{level}',
                'south': side_glow, 'west': side_glow, 'east': side_glow}
    return casing_model(faces, glow)


for tier in (1, 2, 3):
    name = f'accumulator_{tier}'
    for level in range(ACC_CELLS + 1):
        block_model(f'{name}_{level}', accumulator_model(tier, level))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: f"{n}_{c['charge']}",
                                                  [('charge', list(range(ACC_CELLS + 1)))])})
    # The item shows the stored charge too (item property robotica:charge = level / 5, see PowerClient).
    write(ASSETS / 'models/item' / f'{name}.json', {
        'parent': f'robotica:block/{name}_0',
        'overrides': [{'predicate': {'robotica:charge': level / ACC_CELLS}, 'model': f'robotica:block/{name}_{level}'}
                      for level in range(1, ACC_CELLS + 1)],
    })
    loot(name, copy_energy=True)

# ---------- solar panels: a 6 px slab in the generators' gunmetal; the animated top carries the cells and their glint
def solar_model(mk):
    name = f'solar_panel_mk{mk}'
    side_uv = [0, 0, 16, 6]
    return {
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:cutout',
        'textures': {'top': tex(f'{name}_top'), 'side': tex(f'{name}_side'), 'bottom': tex('solar_panel_bottom'),
                     'particle': tex(f'{name}_side')},
        'elements': [{
            'from': [0, 0, 0], 'to': [16, 6, 16],
            'faces': {
                'down': {'texture': '#bottom', 'cullface': 'down'},
                'up': {'texture': '#top'},
                'north': {'texture': '#side', 'uv': side_uv, 'cullface': 'north'},
                'south': {'texture': '#side', 'uv': side_uv, 'cullface': 'south'},
                'west': {'texture': '#side', 'uv': side_uv, 'cullface': 'west'},
                'east': {'texture': '#side', 'uv': side_uv, 'cullface': 'east'},
            },
        }],
    }


for mk in (1, 2, 3, 4):
    name = f'solar_panel_mk{mk}'
    block_model(name, solar_model(mk))
    blockstate(name, {'variants': {'': {'model': f'robotica:block/{name}'}}})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- tesla coils ----------
# Every Tesla block is a low machine platform of the tier metal (I copper, II iron, III gold, IV magma, V null) with a
# glowing lens; TeslaCoilRenderer draws the energy crystal floating over it (and the Tesla Coil's orbiting shards),
# also in the item (TeslaItemRenderer). Each layer is a square with stepped-in corners; their tops share one texture
# drawn pixel for pixel from above (scripts/textures/power.py, same layers). FACING is the direction it points (away
# from the block it sits on); the blockstate turns the up model like a piston.
TESLA_ROT = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180},
             'west': {'x': 90, 'y': 270}, 'east': {'x': 90, 'y': 90}}
SIDES = ('north', 'south', 'west', 'east')


def face(texture, uv, glow=False):
    return dict({'texture': texture, 'uv': [round(v, 4) for v in uv]}, **({'neoforge_data': GLOW} if glow else {}))


def box(frm, to, top, side=None, row=0, glow=False, faces=SIDES, bottom=False):
    """A box whose top takes `top` at its own coordinates and whose sides take row `row` of `side` (one texture row
    per pixel of height). Glowing boxes are full bright and take `top` everywhere."""
    side = side or top
    out = {'up': face(top, [frm[0], frm[2], to[0], to[2]], glow)}
    if bottom:
        out['down'] = dict(face(side, [frm[0], 16 - to[2], to[0], 16 - frm[2]]), cullface='down')
    for d in faces:
        a, b = (frm[0], to[0]) if d in ('north', 'south') else (frm[2], to[2])
        out[d] = face(side, [a, 0, b, 16] if glow else [a, row, b, row + to[1] - frm[1]], glow)
    el = {'from': frm, 'to': to, 'faces': out}
    if glow:
        el['shade'] = False
    return el


def square(half, y0, y1, top, side=None, row=0, glow=False, bottom=False):
    return box([8 - half, y0, 8 - half], [8 + half, y1, 8 + half], top, side, row, glow, bottom=bottom)


def ring(outer, inner, y0, y1, top, side=None, row=0, glow=False):
    """A square ring: north and south bars the full width, west and east bars between them."""
    lo, hi, il, ih = 8 - outer, 8 + outer, 8 - inner, 8 + inner
    return [box([lo, y0, lo], [hi, y1, il], top, side, row, glow),
            box([lo, y0, ih], [hi, y1, hi], top, side, row, glow),
            box([lo, y0, il], [il, y1, ih], top, side, row, glow, faces=('west', 'east')),
            box([ih, y0, il], [hi, y1, ih], top, side, row, glow, faces=('west', 'east'))]


def tesla_model(name, tier, kind, elements, item_display):
    block_model(name, {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'top': tex(f'tesla_{kind}_{tier}_top'), 'side': tex(f'tesla_side_{tier}'),
                     'glow': tex(f'tesla_glow_{tier}'), 'particle': tex(f'tesla_side_{tier}')},
        'elements': elements,
    })
    blockstate(name, {'variants': {f'facing={f}': dict({'model': f'robotica:block/{name}'}, **r) for f, r in TESLA_ROT.items()}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}', 'display': item_display})
    loot(name)


def tesla_platform(base, seam, housing, well, y_housing, top_housing):
    """Base plate, the glowing seam under the housing, the housing ring and the lens at the bottom of its well. Sizes
    match TESLA_PARTS in scripts/textures/power.py."""
    return [square(base, 0, 1, '#top', '#side', row=0, bottom=True),
            *ring(seam, housing, 1, y_housing, '#glow', glow=True),
            *ring(housing, well, 1, top_housing, '#top', '#side', row=1),
            square(well, 1, 1.4, '#glow', glow=True)]


# Coil: 2.6 high; the crystal floats over the well up to about 14.6. Light slits sit on the base at each edge. In the
# slot it is lifted and scaled so platform and crystal fill it.
for tier in range(1, 6):
    slits = [box([7, 1, 2.5], [9, 1.15, 3], '#glow', glow=True, faces=SIDES), box([7, 1, 13], [9, 1.15, 13.5], '#glow', glow=True, faces=SIDES),
             box([2.5, 1, 7], [3, 1.15, 9], '#glow', glow=True, faces=SIDES), box([13, 1, 7], [13.5, 1.15, 9], '#glow', glow=True, faces=SIDES)]
    tesla_model(f'tesla_coil_{tier}', tier, 'coil', [*tesla_platform(6, 4.4, 4, 2, 1.25, 2.6), *slits], {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 2.2, 0], 'scale': [0.85, 0.85, 0.85]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.7, 0.7, 0.7]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.45, 0.45, 0.45]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 1, 0], 'scale': [0.5, 0.5, 0.5]},
    })

# Emitter: 2.2 high under a slim crystal up to about 10.
for tier in range(1, 6):
    tesla_model(f'tesla_emitter_{tier}', tier, 'emitter', tesla_platform(4, 3.3, 3, 1.5, 1.2, 2.2), {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 4.5, 0], 'scale': [1.15, 1.15, 1.15]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 3, 1], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [0.6, 0.6, 0.6]},
    })

# ---------- winding crank ----------
crank_tex = {'base': tex('winding_crank_base'), 'metal': tex('winding_crank_metal'), 'spring': tex('winding_crank_spring'),
             'particle': tex('winding_crank_base')}


def all_faces(texture):
    return {d: {'texture': texture} for d in ('down', 'up', 'north', 'south', 'west', 'east')}


crank_elements = [
    {'from': [2, 0, 2], 'to': [14, 4, 14], 'faces': all_faces('#base')},            # base plate
    {'from': [7, 4, 7], 'to': [9, 12, 9], 'faces': all_faces('#metal')},             # shaft
    {'from': [7, 11, 2], 'to': [9, 13, 9], 'faces': all_faces('#metal')},            # arm (points north)
    {'from': [7, 7, 2], 'to': [9, 11, 4], 'faces': all_faces('#base')},              # grip
]
block_model('winding_crank', {'parent': 'minecraft:block/block', 'textures': crank_tex, 'elements': crank_elements})
block_model('winding_crank_loaded', {'parent': 'minecraft:block/block', 'textures': crank_tex, 'elements': [crank_elements[0], {
    'from': [3, 4, 3], 'to': [13, 8, 13],
    'faces': dict(all_faces('#metal'), up={'texture': '#spring'}),
}] + crank_elements[1:]})
variants = {}
for spring in (False, True):
    for rotation in range(4):
        entry = {'model': 'robotica:block/winding_crank' + ('_loaded' if spring else '')}
        if rotation:
            entry['y'] = 90 * rotation
        variants[f'rotation={rotation},spring={str(spring).lower()}'] = entry
blockstate('winding_crank', {'variants': variants})
item_model('winding_crank', 'robotica:block/winding_crank')
loot('winding_crank')
