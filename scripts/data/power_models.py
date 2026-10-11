"""Writes blockstates, block/item models and loot tables of the power module.
Run: python3 scripts/data/power_models.py   (overwrites files named after power blocks only)"""
import json
import pathlib

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
    'metal_press': ('metal_press_top', 'metal_press_top', None),
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
# Every Tesla block is a glowing energy crystal on a platform of the tier metal (I copper, II iron, III gold, IV magma,
# V null); the crystal colour shows the tier too. Tesla Emitter: a faceted crystal with a pointed top on a small plate.
# Tesla Coil: a wide stepped platform with a glowing seam, four prongs with lit tips and a bigger crystal floating over
# a socket; TeslaCoilRenderer adds the shards that circle it. FACING is the direction it points (away from the block it
# sits on); the blockstate turns the up model like a piston.
TESLA_ROT = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180},
             'west': {'x': 90, 'y': 270}, 'east': {'x': 90, 'y': 90}}
SIDES = ('north', 'south', 'west', 'east')


def cuboid(frm, to, texture, glow=False, turn=False, full_uv=False, uv=None, faces=SIDES + ('up', 'down')):
    """A box with pixel-exact UVs taken from its own coordinates. full_uv: the whole texture on every face; uv: this
    uv on every face. turn: 45 degrees round the block's vertical centre line."""
    out = {}
    for d in faces:
        if uv:
            u = list(uv)
        elif full_uv:
            u = [0, 0, 16, 16]
        elif d in ('up', 'down'):
            u = [frm[0], frm[2], to[0], to[2]]
        elif d in ('north', 'south'):
            u = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
        else:
            u = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        out[d] = dict({'texture': texture, 'uv': u}, **({'neoforge_data': GLOW} if glow else {}))
    el = {'from': frm, 'to': to, 'faces': out}
    if glow:
        el['shade'] = False
    if turn:
        el['rotation'] = {'angle': 45, 'axis': 'y', 'origin': [8, frm[1], 8]}
    return el


def platform(lo, hi, y0, y1):
    """Tier platform: the whole plate texture on top and bottom, its lit frame band round the sides."""
    el = cuboid([lo, y0, lo], [hi, y1, hi], '#plate', faces=('up', 'down'), full_uv=True)
    for d in SIDES:
        el['faces'][d] = {'texture': '#plate', 'uv': [lo, 0, hi, y1 - y0]}
    return el


def gem(w, y0, y1, turn=False, faces=SIDES + ('up',), rim=True):
    """One full-bright crystal layer w wide on the centre line. rim: the whole texture (with its coloured rim) per face."""
    return cuboid([8 - w / 2, y0, 8 - w / 2], [8 + w / 2, y1, 8 + w / 2], '#crystal', glow=True, turn=turn, full_uv=rim,
                  faces=faces)


def spike(y, w, h):
    """Pointed roof from y: turned layers that shrink from w to a sliver over h pixels."""
    steps = max(2, round(h / 1.0))
    return [gem(w * (1 - i / steps) + 0.4 * (i == steps - 1), y + h * i / steps, y + h * (i + 1) / steps, turn=True, rim=False)
            for i in range(steps)]


def tesla_model(name, tier, elements):
    elements[0]['faces']['down']['cullface'] = 'down'
    block_model(name, {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'plate': tex(f'tesla_plate_{tier}'), 'frame': tex('tesla_frame'), 'crystal': tex(f'tesla_crystal_{tier}'),
                     'particle': tex(f'tesla_plate_{tier}')},
        'elements': elements,
    })
    blockstate(name, {'variants': {f'facing={f}': dict({'model': f'robotica:block/{name}'}, **r) for f, r in TESLA_ROT.items()}})
    loot(name)


# Coil: 10 high. The crystal floats from 2.9 to 10 over a socket (TeslaCoilBlock.TIP 7.5); prongs at 4..5 and 11..12.
for tier in range(1, 6):
    name = f'tesla_coil_{tier}'
    prongs = []
    for x in (4, 11):
        for z in (4, 11):
            prongs.append(cuboid([x, 2, z], [x + 1, 4.5, z + 1], '#plate', uv=[0, 0, 1, 2.5], faces=SIDES))
            prongs.append(cuboid([x, 4.5, z], [x + 1, 5.25, z + 1], '#crystal', glow=True, full_uv=True, faces=SIDES + ('up',)))
    tesla_model(name, tier, [
        platform(2, 14, 0, 1),
        cuboid([3, 1, 3], [13, 1.25, 13], '#crystal', glow=True, full_uv=True, faces=SIDES + ('up',)),   # glowing seam
        cuboid([3.5, 1, 3.5], [12.5, 2, 12.5], '#frame', faces=SIDES + ('up',)),
        platform(6.5, 9.5, 2, 2.5),                                                                     # socket
        *prongs,
        gem(1.5, 2.9, 3.4, turn=True, faces=SIDES + ('down',), rim=False),                               # lower point
        gem(3, 3.4, 3.9, turn=True, faces=SIDES + ('down',), rim=False),
        gem(4, 3.9, 7.5, turn=True),
        gem(3, 3.9, 7.25),
        *spike(7.5, 3, 2.5),
    ])
    # 10 high and 12 wide: shown a little bigger than a block in the slot, lifted so it sits in the middle.
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}', 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 2.5, 0], 'scale': [0.8, 0.8, 0.8]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 1, 0], 'scale': [0.7, 0.7, 0.7]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.45, 0.45, 0.45]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    }})

# Emitter: a faceted crystal (a turned square round a lower square, so their tops do not fight) with a pointed roof,
# 10.5 high on a small tier plate. Arcs start at 8 (TeslaEmitterBlock.EMITTER_TIP).
for tier in range(1, 6):
    name = f'tesla_emitter_{tier}'
    tesla_model(name, tier, [
        platform(4.5, 11.5, 0, 1),
        gem(4.5, 1, 6, turn=True),
        gem(3.5, 1, 5.75),
        *spike(6, 3.5, 4.5),
    ])
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}', 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.95, 0.95, 0.95]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.8, 0.8, 0.8]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 3, 1], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 3, 0], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 3, 0], 'scale': [0.6, 0.6, 0.6]},
    }})

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
