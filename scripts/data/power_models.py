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


MACHINES = {
    # name: (top off, top on, glow on top when lit)
    'combustion_generator': ('combustion_generator_top', 'combustion_generator_top_on', None),
    'charger': ('charger_top', 'charger_top_on', 'charger_top_glow'),
}
for name, (top, top_on, top_glow) in MACHINES.items():
    for lit in (False, True):
        faces = {'north': f'{name}_front' + ('_on' if lit else ''), 'up': top_on if lit else top,
                 'down': 'power_machine_bottom', 'south': 'power_machine_side', 'west': 'power_machine_side',
                 'east': 'power_machine_side'}
        glow = None
        if lit:
            glow = {'north': f'{name}_front_glow'}
            if top_glow:
                glow['up'] = top_glow
        block_model(name + ('_on' if lit else ''), machine_model(faces, glow))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: n + ('_on' if c['lit'] else ''), [('lit', [False, True])])})
    item_model(name, f'robotica:block/{name}')
    loot(name)

# ---------- metal press: the processing machines' shared casing (scripts/data/machine_models.py) with a press bay 2 px
# deep in the front: back wall at z 2 and four walls, all from the metal_press_bay texture (the back wall in 4..11 x
# 3..12, the walls around it); lit, the animated bay plus its full-bright glow on the back wall and the floor.
def press_model(lit):
    faces = {'north': 'metal_press_front' + ('_on' if lit else ''), 'up': 'metal_press_top', 'down': 'metal_press_bottom',
             'south': 'metal_press_side', 'west': 'metal_press_side', 'east': 'metal_press_side'}
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


for lit in (False, True):
    block_model('metal_press' + ('_on' if lit else ''), press_model(lit))
blockstate('metal_press', {'variants': facing_variants(lambda c: 'metal_press' + ('_on' if c['lit'] else ''), [('lit', [False, True])])})
item_model('metal_press', 'robotica:block/metal_press')
loot('metal_press')

# ---------- wireless charger: the machine cube plus an emitter mast with a ball on top (full bright when lit) ----------
for lit in (False, True):
    name = 'wireless_charger' + ('_on' if lit else '')
    faces = {'north': 'wireless_charger_front' + ('_on' if lit else ''), 'up': 'wireless_charger_top',
             'down': 'power_machine_bottom', 'south': 'power_machine_side', 'west': 'power_machine_side',
             'east': 'power_machine_side'}
    model = machine_model(faces, {'north': 'wireless_charger_front_glow'} if lit else None)
    model['textures']['mast'] = tex('wireless_charger_mast')
    model['textures']['ball'] = tex('tesla_coil_tip')
    sides = ('north', 'south', 'west', 'east')
    model['elements'] += [
        {'from': [6, 16, 6], 'to': [10, 17, 10], 'faces': {d: {'texture': '#mast'} for d in sides + ('up',)}},
        {'from': [7.25, 17, 7.25], 'to': [8.75, 19.5, 8.75], 'faces': {d: {'texture': '#mast'} for d in sides}},
        {'from': [6.75, 19.5, 6.75], 'to': [9.25, 22, 9.25], 'shade': not lit,
         'faces': {d: dict({'texture': '#ball', 'uv': [5, 5, 11, 11]}, **({'neoforge_data': GLOW} if lit else {}))
                   for d in DIRS}},
    ]
    block_model(name, model)
blockstate('wireless_charger', {'variants': facing_variants(lambda c: 'wireless_charger' + ('_on' if c['lit'] else ''),
                                                            [('lit', [False, True])])})
item_model('wireless_charger', 'robotica:block/wireless_charger')
loot('wireless_charger')

# ---------- accumulators: the gauge shows the charge (block state `charge` 0-5, one lit cell per level) ----------
ACC_CELLS = 5
for tier in (1, 2, 3):
    name = f'accumulator_{tier}'
    faces = {'north': f'{name}_front', 'up': f'{name}_top', 'down': 'power_machine_bottom',
             'south': f'{name}_side', 'west': f'{name}_side', 'east': f'{name}_side'}
    for level in range(ACC_CELLS + 1):
        glow = None
        if level:
            side_glow = f'accumulator_side_glow_{level}'
            glow = {'north': f'accumulator_front_glow_{level}', 'south': side_glow, 'west': side_glow, 'east': side_glow}
        block_model(f'{name}_{level}', machine_model(faces, glow))
    blockstate(name, {'variants': facing_variants(lambda c, n=name: f"{n}_{c['charge']}",
                                                  [('charge', list(range(ACC_CELLS + 1)))])})
    # The item shows the stored charge too (item property robotica:charge = level / 5, see PowerClient).
    write(ASSETS / 'models/item' / f'{name}.json', {
        'parent': f'robotica:block/{name}_0',
        'overrides': [{'predicate': {'robotica:charge': level / ACC_CELLS}, 'model': f'robotica:block/{name}_{level}'}
                      for level in range(1, ACC_CELLS + 1)],
    })
    loot(name, copy_energy=True)

# ---------- solar panels: a 6 px slab, cells catch a faint glow ----------
for mk in (1, 2, 3, 4):
    name = f'solar_panel_mk{mk}'
    side_uv = [0, 0, 16, 6]
    block_model(name, {
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:cutout',
        'textures': {'top': tex(f'{name}_top'), 'side': tex(f'{name}_side'), 'bottom': tex('power_machine_bottom'),
                     'glow': tex(f'{name}_glow'), 'particle': tex(f'{name}_side')},
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
        }, {
            'from': [0, 0, 0], 'to': [16, 6, 16], 'shade': False,
            'faces': {'up': {'texture': '#glow', 'neoforge_data': {'block_light': 9, 'sky_light': 9, 'ambient_occlusion': False}}},
        }],
    })
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
