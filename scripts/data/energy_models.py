"""Writes blockstates, block/item models, loot tables and the pickaxe tag of the energy module.
Run: python3 scripts/data/energy_models.py   (overwrites files named after energy blocks only)"""
import json
import pathlib

import connected_glass

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'
FRAG = ROOT / 'src/main/fragments/energy/data/minecraft/tags/block'

GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')
SIDES = ('north', 'south', 'west', 'east')
BLOCKS = []


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def loot(name, copy=None):
    entry = {'type': 'minecraft:item', 'name': f'robotica:{name}'}
    if copy:
        entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': copy}]
    pool = {'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry]}
    # A block that carries stored energy always drops, so an explosion never deletes the energy with it.
    if not copy:
        pool['conditions'] = [{'condition': 'minecraft:survives_explosion'}]
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [pool],
        'random_sequence': f'robotica:blocks/{name}',
    })


def glow_cube(faces, glow, render_type='minecraft:cutout'):
    """Cube with face -> texture and a coplanar full-bright overlay (face -> texture)."""
    textures = {f'f_{d}': tex(t) for d, t in faces.items()}
    textures.update({f'g_{d}': tex(t) for d, t in glow.items()})
    textures['particle'] = tex(faces['north'])
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: {'texture': f'#f_{d}', 'cullface': d} for d in faces}}]
    if glow:
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {d: {'texture': f'#g_{d}', 'cullface': d, 'neoforge_data': GLOW} for d in glow}})
    return {'parent': 'minecraft:block/block', 'render_type': render_type, 'textures': textures, 'elements': elements}


def simple(name, model, item_model=None):
    """One model for every state."""
    write(ASSETS / 'models/block' / f'{name}.json', model)
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{name}'}}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': item_model or f'robotica:block/{name}'})
    loot(name)
    BLOCKS.append(name)


def cube_all(name, texture=None, render_type=None):
    model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(texture or name)}}
    if render_type:
        model['render_type'] = render_type
    simple(name, model)


def column(name, side, end, glow_side=None, glow_end=None):
    faces = {d: side for d in SIDES} | {'up': end, 'down': end}
    glow = {}
    if glow_side:
        glow.update({d: glow_side for d in SIDES})
    if glow_end:
        glow.update({'up': glow_end, 'down': glow_end})
    simple(name, glow_cube(faces, glow))


def default_uv(d, a, b):
    """The UV the game derives from an element's position (BlockElement.uvsByFace)."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {'down': [x1, 16 - z2, x2, 16 - z1], 'up': [x1, z1, x2, z2], 'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1],
            'south': [x1, 16 - y2, x2, 16 - y1], 'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1]}[d]


def fit_uv(lo, hi):
    """A UV span moved back inside 0..16 (squeezed when longer), so a face past the block edge never samples the
    neighbouring sprite in the atlas."""
    if 0 <= lo and hi <= 16:
        return lo, hi
    span = hi - lo
    if span >= 16:
        return 0, 16
    lo %= 16
    return (lo, lo + span) if lo + span <= 16 else (16 - span, 16)


def box(frm, to, faces, shade=True, glow=False):
    """Element with face -> texture variable; faces touching the block edge cull against that side. Faces of an
    element reaching past the block get an explicit UV inside the texture."""
    edge = {'down': frm[1] == 0, 'up': to[1] == 16, 'north': frm[2] == 0, 'south': to[2] == 16,
            'west': frm[0] == 0, 'east': to[0] == 16}
    out = {}
    for d, t in faces.items():
        face = {'texture': t}
        u0, v0, u1, v1 = default_uv(d, frm, to)
        if min(u0, v0) < 0 or max(u1, v1) > 16:
            u0, u1 = fit_uv(u0, u1)
            v0, v1 = fit_uv(v0, v1)
            face['uv'] = [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]
        if edge[d]:
            face['cullface'] = d
        if glow:
            face['neoforge_data'] = GLOW
        out[d] = face
    element = {'from': frm, 'to': to, 'faces': out}
    if not shade:
        element['shade'] = False
    return element


def all_faces(t):
    return {d: t for d in DIRS}


# ---------- casings, glass, coolant ----------
# Formed look: the controller sets frame=wall|x|y|z|corner on its casings (FramedPartBlock) and formed=true on its
# glass, so a finished structure reads as one machine: beams along the edges, corner caps, plain wall panels and
# windows joined into the frame. Glass is connected (connected_glass.py). Previewed by scripts/scene_preview.py.
for prefix in ('reactor', 'bank'):
    name = f'{prefix}_casing'
    write(ASSETS / 'models/block' / f'{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(name)}})
    write(ASSETS / 'models/block' / f'{name}_wall.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(f'{prefix}_panel')}})
    beam_glow = {d: f'{prefix}_frame_glow' for d in SIDES} if prefix == 'bank' else {}
    beam = glow_cube({d: f'{prefix}_frame' for d in SIDES} | {'up': f'{prefix}_frame_corner', 'down': f'{prefix}_frame_corner'}, beam_glow)
    write(ASSETS / 'models/block' / f'{name}_beam.json', beam)
    write(ASSETS / 'models/block' / f'{name}_corner.json',
          glow_cube({d: f'{prefix}_frame_corner' for d in DIRS}, {d: f'{prefix}_frame_corner_glow' for d in DIRS}))
    m = f'robotica:block/{name}'
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {
        'frame=none': {'model': m}, 'frame=wall': {'model': m + '_wall'}, 'frame=corner': {'model': m + '_corner'},
        'frame=y': {'model': m + '_beam'}, 'frame=z': {'model': m + '_beam', 'x': 90},
        'frame=x': {'model': m + '_beam', 'x': 90, 'y': 90}}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': m})
    loot(name)
    BLOCKS.append(name)

    name = f'{prefix}_glass'
    connected_glass.write_glass(write, ASSETS, name)
    loot(name)
    BLOCKS.append(name)
cube_all('cryo_coolant')
cube_all('reactor_access_port')

# ---------- columns: amplifiers, damper, capacitors, coils ----------
for n in ('copper', 'redstone', 'ender', 'resonant'):
    column(f'capacitor_{n}', f'capacitor_{n}', f'capacitor_{n}_top', 'capacitor_glow')
for n in ('basic', 'advanced', 'elite'):
    column(f'transfer_coil_{n}', f'transfer_coil_{n}', f'transfer_coil_{n}_top', f'transfer_coil_{n}_glow',
           f'transfer_coil_{n}_top_glow')

# ---------- ports ----------
simple('reactor_power_port', glow_cube({d: 'reactor_power_port' for d in DIRS}, {d: 'reactor_power_port_glow' for d in DIRS}))

for mode in ('input', 'output'):
    write(ASSETS / 'models/block' / f'bank_port_{mode}.json',
          glow_cube({d: f'bank_port_{mode}' for d in DIRS}, {d: f'bank_port_{mode}_glow' for d in DIRS}))
write(ASSETS / 'blockstates/bank_port.json', {'variants': {
    'output=false': {'model': 'robotica:block/bank_port_input'},
    'output=true': {'model': 'robotica:block/bank_port_output'}}})
write(ASSETS / 'models/item/bank_port.json', {'parent': 'robotica:block/bank_port_input'})
loot('bank_port')
BLOCKS.append('bank_port')

# ---------- controllers: screen front (off / formed / working), casing elsewhere, four facings ----------
# The bank's and the reactor's screens are dark while off (dark_off: no overlay); working, the overlay is animated.
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
CONTROLLERS = {
    'reactor_controller': ('reactor', {'dark_off': True}),
    'bank_controller': ('bank', {'dark_off': True}),
    # Spire Base: copper winding band on the sides, the terminal plate on top.
    'spire_base': ('spire', {'faces': {'south': 'spire_base', 'west': 'spire_base', 'east': 'spire_base',
                                       'up': 'spire_base_top'},
                             'glow': {'south': 'spire_base_glow', 'west': 'spire_base_glow', 'east': 'spire_base_glow'}}),
}
for name, (prefix, extra) in CONTROLLERS.items():
    for state in ('off', 'formed', 'on'):
        faces = {d: f'{prefix}_casing' for d in DIRS} | extra.get('faces', {}) | {'north': f'{prefix}_controller_{state}'}
        glow = dict(extra.get('glow', {}))
        if state != 'off' or not extra.get('dark_off'):
            glow |= {'north': f'{prefix}_controller_{state}_glow'}
        model = glow_cube(faces, glow)
        model['textures']['particle'] = tex(f'{prefix}_casing')
        if name == 'spire_base' and state == 'formed':          # the item keeps the plain block
            write(ASSETS / 'models/item/spire_base.json', json.loads(json.dumps(model)))
        if name == 'spire_base' and state != 'off':
            # formed: four stepped corner feet brace the column (past the block, like the crown's toroid)
            model['textures']['feet'] = tex('spire_rail')
            for x, z in ((-3, -3), (13, -3), (-3, 13), (13, 13)):
                for y0, y1, grow in ((0, 4, 0), (4, 9, 1.5)):
                    a = [x + grow if x < 0 else x, y0, z + grow if z < 0 else z]
                    b = [x + 6 - (grow if x > 0 else 0), y1, z + 6 - (grow if z > 0 else 0)]
                    model['elements'].append(box(a, b, all_faces('#feet')))
        write(ASSETS / 'models/block' / f'{name}_{state}.json', model)
    variants = {}
    for facing, y in FACING_Y.items():
        for formed in (False, True):
            for lit in (False, True):
                state = 'on' if formed and lit else 'formed' if formed else 'off'
                entry = {'model': f'robotica:block/{name}_{state}'}
                if y:
                    entry['y'] = y
                variants[f'facing={facing},formed={str(formed).lower()},lit={str(lit).lower()}'] = entry
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': variants})
    if name != 'spire_base':
        write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}_formed'})
    loot(name, ['robotica:bank_energy'] if prefix == 'bank' else None)
    BLOCKS.append(name)

# ---------- Core Reactor modulators: open cages, so the core and its beams show through a packed chamber ----------
def cage_model(name, plate_side, plate_top, inner_tex, frm, to, glow):
    """Two plates on four corner posts around a floating inner block; a glowing inner block also lights the plates'
    outer faces (<plate_top>_glow)."""
    plate = {'down': '#top', 'up': '#top', 'north': '#side', 'south': '#side', 'west': '#side', 'east': '#side'}
    elements = [box([0, 0, 0], [16, 2, 16], plate), box([0, 14, 0], [16, 16, 16], plate)]
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append(box([x, 2, z], [x + 2, 14, z + 2], all_faces('#side')))
    elements.append(box(frm, to, all_faces('#inner'), shade=not glow, glow=glow))
    textures = {'side': tex(plate_side), 'top': tex(plate_top), 'inner': tex(inner_tex), 'particle': tex(plate_side)}
    if glow:
        textures['top_glow'] = tex(f'{plate_top}_glow')
        elements.append(box([0, 0, 0], [16, 2, 16], {'down': '#top_glow'}, shade=False, glow=True))
        elements.append(box([0, 14, 0], [16, 16, 16], {'up': '#top_glow'}, shade=False, glow=True))
    simple(name, {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures,
                  'elements': elements})


for n in ('flux', 'pyro', 'resonant'):
    cage_model(f'{n}_amplifier', f'{n}_amplifier', f'{n}_amplifier_top', f'{n}_amplifier_crystal', [5, 4, 5], [11, 12, 11], True)
cage_model('graphite_damper', 'graphite_damper', 'graphite_damper_top', 'graphite_damper', [4, 2, 4], [12, 14, 12], False)

# ---------- Spire Crown: copper-wound neck, steel toroid, discharge sphere that lights up ----------
# Formed, the crown grows a wide toroid (16 segments, radius 20 px, past the block like a real Tesla coil's top load)
# on four spokes; the hit box stays the small crown (SpireCrownBlock.SHAPE).
def toroid(radius, y0, y1, thick, tex_var):
    """Ring of 16 straight segments around the block centre; element rotations only come in 22.5 degree steps, so
    each segment is built along x or z and turned by -45..45 to lie along the ring."""
    import math
    length = 2 * math.pi * radius / 16 * 1.06
    out = []
    for k in range(16):
        phi = math.radians(k * 22.5)
        cx, cz = 8 + radius * math.cos(phi), 8 + radius * math.sin(phi)
        theta = (k * 22.5 + 90) % 180                     # tangent direction, mod 180
        for along_x in (True, False):
            # a box along x turned by a points at -a; along z it points at 90 - a (model_preview / FaceBakery sense)
            a = ((-theta if along_x else 90 - theta) + 90) % 180 - 90
            if a in (-45, -22.5, 0, 22.5, 45):
                break
        hx, hz = (length / 2, thick / 2) if along_x else (thick / 2, length / 2)
        el = box([round(cx - hx, 3), y0, round(cz - hz, 3)], [round(cx + hx, 3), y1, round(cz + hz, 3)],
                 {d: tex_var for d in DIRS})
        for f in el['faces'].values():
            f.pop('cullface', None)
            f['uv'] = [0, 0, 16, 16] if f is not None else None
        if a:
            el['rotation'] = {'angle': a, 'axis': 'y', 'origin': [round(cx, 3), (y0 + y1) / 2, round(cz, 3)]}
        out.append(el)
    return out


for formed in (False, True):
    for lit in (False, True):
        core = 'spire_crown_core_lit' if lit else 'spire_crown_core'
        ring = all_faces('#ring')
        if formed:
            elements = [box([6, 0, 6], [10, 8, 10], all_faces('#coil')),
                        box([4, 8, 4], [12, 16, 12], all_faces('#core'), shade=not lit, glow=lit),
                        box([7, 16, 7], [9, 18, 9], all_faces('#ring'))]
            elements += toroid(20, 7, 12, 5, '#ring') + toroid(12.5, 9, 11, 2, '#ring')
            for x0, z0, x1, z1 in ((-12, 7, 4, 9), (12, 7, 28, 9), (7, -12, 9, 4), (7, 12, 9, 28)):   # spokes
                elements.append(box([x0, 9, z0], [x1, 10.5, z1], all_faces('#ring')))
            for el in elements:
                for f in el['faces'].values():
                    f.pop('cullface', None)
        else:
            elements = [
                box([6, 0, 6], [10, 7, 10], all_faces('#coil')),
                box([1, 6, 1], [15, 10, 4], ring), box([1, 6, 12], [15, 10, 15], ring),
                box([1, 6, 4], [4, 10, 12], ring), box([12, 6, 4], [15, 10, 12], ring),
                box([5, 9, 5], [11, 15, 11], all_faces('#core'), shade=not lit, glow=lit),
                box([7, 15, 7], [9, 16, 9], all_faces('#ring')),
            ]
        model = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
                 'textures': {'coil': tex('spire_crown_coil'), 'ring': tex('spire_crown_ring'), 'core': tex(core),
                              'particle': tex('spire_crown_ring')},
                 'elements': elements}
        write(ASSETS / 'models/block' / f'spire_crown{"_formed" if formed else ""}{"_lit" if lit else ""}.json', model)
write(ASSETS / 'blockstates/spire_crown.json', {'variants': {
    f'formed={str(f).lower()},lit={str(l).lower()}': {'model': f'robotica:block/spire_crown{"_formed" if f else ""}{"_lit" if l else ""}'}
    for f in (False, True) for l in (False, True)}})
write(ASSETS / 'models/item/spire_crown.json', {'parent': 'robotica:block/spire_crown_lit'})
loot('spire_crown')
BLOCKS.append('spire_crown')


# ---------- Tesla Spire column shell: drawn by SpireRenderer over each metal block while the spire is formed ----------
def flat(tex_var, uv, tint=False, glow=False):
    f = {'texture': tex_var, 'uv': uv}
    if tint:
        f['tintindex'] = 0
    if glow:
        f['neoforge_data'] = GLOW
    return f


E = 0.05      # the shell sits just outside the metal block, so the block itself never shows through
# No bottom faces (the column always stands on the base or another conductor). The tops sit a little above the block,
# so the last conductor's own top face never z-fights with them; below the crown they are hidden in the next block.
shell = {'from': [-E, 0, -E], 'to': [16 + E, 16, 16 + E],
         'faces': {d: flat('#coil', [0, 0, 16, 16], tint=True) for d in SIDES}}
cap = {'from': [-E, 16.03, -E], 'to': [16 + E, 16.03, 16 + E], 'faces': {'up': flat('#coil', [0, 0, 16, 16], tint=True)}}
rails = []
for x0, z0 in ((-0.45, -0.45), (12.8, -0.45), (-0.45, 12.8), (12.8, 12.8)):
    rails.append({'from': [x0, 0, z0], 'to': [x0 + 3.65, 16, z0 + 3.65], 'faces': {d: flat('#rail', [0, 0, 4, 16]) for d in SIDES}})
    rails.append({'from': [x0, 16.06, z0], 'to': [x0 + 3.65, 16.06, z0 + 3.65], 'faces': {'up': flat('#rail', [0, 0, 4, 4])}})
write(ASSETS / 'models/block/spire_coil.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
      'textures': {'coil': tex('spire_coil'), 'rail': tex('spire_rail'), 'particle': tex('spire_rail')},
      'elements': [shell, cap] + rails})
G = 0.15
strips = [{'from': [7, 0, -G], 'to': [9, 16, -G], 'faces': {'north': flat('#glow', [7, 0, 9, 16], True, True)}},
          {'from': [7, 0, 16 + G], 'to': [9, 16, 16 + G], 'faces': {'south': flat('#glow', [7, 0, 9, 16], True, True)}},
          {'from': [-G, 0, 7], 'to': [-G, 16, 9], 'faces': {'west': flat('#glow', [7, 0, 9, 16], True, True)}},
          {'from': [16 + G, 0, 7], 'to': [16 + G, 16, 9], 'faces': {'east': flat('#glow', [7, 0, 9, 16], True, True)}}]
for el in strips:
    el['shade'] = False
write(ASSETS / 'models/block/spire_coil_glow.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
      'textures': {'glow': tex('spire_coil_glow'), 'particle': tex('spire_coil_glow')}, 'elements': strips})

# ---------- Ring Collider (textures: Ring Collider section of scripts/textures/energy.py) ----------
# Controller: the machine casing look. The front's beam aperture shows the collision chamber 2 px deep (back plate,
# bore walls, the beam tube across it); the sides carry beam ports, the back the energy channel, the top a beam
# window. Formed: standby glow; running (formed + lit): animated full-bright overlays.
from machine_models import machine_model  # noqa: E402

CL_FACES = {'north': 'collider_controller_front', 'south': 'collider_controller_back',
            'west': 'collider_controller_port', 'east': 'collider_controller_port',
            'up': 'collider_controller_top', 'down': 'collider_controller_bottom'}


def collider_controller_model(state):
    lit = state != 'off'
    suffix = '_formed' if state == 'formed' else '_running'

    def flat(frm, to, d, uv, tex='#chamber', glow=False):
        return {'from': frm, 'to': to, 'faces': {d: {'texture': tex, 'uv': uv}}, 'glow': glow}
    els = [flat([3, 3, 2], [13, 13, 2], 'north', [3, 3, 13, 13]),               # chamber back
           flat([3, 3, 0], [3, 13, 2], 'east', [0, 15, 2, 16]),                  # bore walls
           flat([13, 3, 0], [13, 13, 2], 'west', [0, 15, 2, 16]),
           flat([3, 13, 0], [13, 13, 2], 'down', [3, 15, 13, 16]),
           flat([3, 3, 0], [13, 3, 2], 'up', [3, 15, 13, 16]),
           {'from': [3, 7, 0.5], 'to': [13, 9, 1.5],                             # beam tube
            'faces': {'north': {'texture': '#chamber', 'uv': [3, 0, 13, 2]},
                      'up': {'texture': '#chamber', 'uv': [3, 14, 13, 15]},
                      'down': {'texture': '#chamber', 'uv': [3, 14, 13, 15]}}}]
    textures = {'chamber': 'collider_controller_chamber'}
    glow = None
    if lit:
        textures['chamber_glow'] = f'collider_controller_chamber{suffix}'
        els.append(flat([3, 3, 2], [13, 13, 2], 'north', [3, 3, 13, 13], '#chamber_glow', True))
        els.append(flat([3, 7, 0.5], [13, 9, 0.5], 'north', [3, 0, 13, 2], '#chamber_glow', True))
        glow = {'north': f'collider_controller_front{suffix}', 'west': f'collider_controller_port{suffix}',
                'east': f'collider_controller_port{suffix}', 'up': f'collider_controller_top{suffix}'}
        if state == 'on':
            glow['south'] = 'collider_controller_back_running'
    return machine_model(CL_FACES, glow, els, textures)


for state in ('off', 'formed', 'on'):
    write(ASSETS / 'models/block' / f'collider_controller_{state}.json', collider_controller_model(state))
write(ASSETS / 'blockstates/collider_controller.json', {'variants': {
    f'facing={facing},formed={str(formed).lower()},lit={str(lit).lower()}':
        {'model': f'robotica:block/collider_controller_{"on" if formed and lit else "formed" if formed else "off"}'}
        | ({'y': y} if y else {})
    for facing, y in FACING_Y.items() for formed in (False, True) for lit in (False, True)}})
write(ASSETS / 'models/item/collider_controller.json', {'parent': 'robotica:block/collider_controller_formed'})
loot('collider_controller')
BLOCKS.append('collider_controller')

# Ring segments: slab, glass beam pipe joining the neighbours (multipart) with steel flanges at the block edges, a
# magnet on straights (two yoke plates round a coil pack) and a bending magnet on corners. The pipe is centred at y 8
# (ColliderBlockEntity.ringCentres: y + 0.5); ColliderRenderer lights the slot on a magnet's top (y 11, 4 x 2 px)
# and a corner's (y 12, 2 x 2 px).
# Atlases: <name>_parts (slab side 0,0-16,4; yoke face 0,4-10,12; coil 10,4-16,12, u along the beam) and
# <name>_corner (bending magnet side 0,0-8,8 and top 8,0-16,8; flange 0,8-6,14).
OPPOSITE = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}
ARMS = {'north': ([6, 6, 0], [10, 10, 6]), 'south': ([6, 6, 10], [10, 10, 16]),
        'west': ([0, 6, 6], [6, 10, 10]), 'east': ([10, 6, 6], [16, 10, 10])}
FLANGES = {'north': ([5, 5, 0], [11, 11, 1]), 'south': ([5, 5, 15], [11, 11, 16]),
           'west': ([0, 5, 5], [1, 11, 11]), 'east': ([15, 5, 5], [16, 11, 11])}
YOKE_FACE, YOKE_EDGE, YOKE_TOP = [0, 4, 10, 12], [0, 4, 2, 12], [0, 4, 10, 6]
COIL_TOP, COIL_SIDE = [11, 4, 15, 12], [11, 4, 15, 11]


def uv_face(tex_var, uv, rotation=0):
    f = {'texture': tex_var, 'uv': uv}
    if rotation:
        f['rotation'] = rotation
    return f


def flange(d):
    """Steel flange at the outer end of an arm: its face looks back along the pipe, its edges are 1 px strips."""
    a, b = FLANGES[d]
    faces = {OPPOSITE[d]: uv_face('#corner', [0, 8, 6, 14])}
    for f in DIRS:
        if f in (d, OPPOSITE[d]):
            continue
        u0, v0, u1, v1 = default_uv(f, a, b)
        faces[f] = uv_face('#corner', [0, 8, 6, 9] if u1 - u0 > v1 - v0 else [0, 8, 1, 14])
    return {'from': a, 'to': b, 'faces': faces}


def magnet(axis):
    """Two yoke plates (2 thick, 8 tall, 10 across) round a coil pack (7 tall, 8 across, 4 along) on a straight."""
    def el(frm, to, faces):
        return {'from': frm, 'to': to, 'faces': faces}
    if axis == 'x':
        plates = [el([4, 4, 3], [6, 12, 13], {'west': uv_face('#parts', YOKE_FACE), 'east': uv_face('#parts', YOKE_FACE),
                                               'north': uv_face('#parts', YOKE_EDGE), 'south': uv_face('#parts', YOKE_EDGE),
                                               'up': uv_face('#parts', YOKE_TOP, 90)}),
                  el([10, 4, 3], [12, 12, 13], {'west': uv_face('#parts', YOKE_FACE), 'east': uv_face('#parts', YOKE_FACE),
                                                 'north': uv_face('#parts', YOKE_EDGE), 'south': uv_face('#parts', YOKE_EDGE),
                                                 'up': uv_face('#parts', YOKE_TOP, 90)})]
        coil = el([6, 4, 4], [10, 11, 12], {'up': uv_face('#parts', COIL_TOP), 'north': uv_face('#parts', COIL_SIDE),
                                             'south': uv_face('#parts', COIL_SIDE)})
    else:
        plates = [el([3, 4, 4], [13, 12, 6], {'north': uv_face('#parts', YOKE_FACE), 'south': uv_face('#parts', YOKE_FACE),
                                               'west': uv_face('#parts', YOKE_EDGE), 'east': uv_face('#parts', YOKE_EDGE),
                                               'up': uv_face('#parts', YOKE_TOP)}),
                  el([3, 4, 10], [13, 12, 12], {'north': uv_face('#parts', YOKE_FACE), 'south': uv_face('#parts', YOKE_FACE),
                                                 'west': uv_face('#parts', YOKE_EDGE), 'east': uv_face('#parts', YOKE_EDGE),
                                                 'up': uv_face('#parts', YOKE_TOP)})]
        coil = el([4, 4, 6], [12, 11, 10], {'up': uv_face('#parts', COIL_TOP, 90), 'west': uv_face('#parts', COIL_SIDE),
                                             'east': uv_face('#parts', COIL_SIDE)})
    return plates + [coil]


SLAB = {'from': [0, 0, 0], 'to': [16, 4, 16],
        'faces': {'up': {'texture': '#base'}, 'down': {'texture': '#base', 'cullface': 'down'},
                  **{d: uv_face('#parts', [0, 0, 16, 4]) for d in SIDES}}}
PEDESTAL = box([7, 4, 7], [9, 6, 9], {d: '#base' for d in SIDES})
CORNER = {'from': [4, 4, 4], 'to': [12, 12, 12],
          'faces': {'up': uv_face('#corner', [8, 0, 16, 8]), **{d: uv_face('#corner', [0, 0, 8, 8]) for d in SIDES}}}

for name in ('accelerator_segment', 'resonant_segment'):
    textures = {'base': tex(f'{name}_base'), 'parts': tex(f'{name}_parts'), 'corner': tex(f'{name}_corner'),
                'pipe': tex(f'{name}_pipe'), 'particle': tex(f'{name}_base')}

    def part(suffix, elements):
        write(ASSETS / 'models/block' / f'{name}_{suffix}.json',
              {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': textures,
               'elements': elements})
        return f'robotica:block/{name}_{suffix}'

    # The glass has no faces inside the pipe: the centre piece closes only the sides without a neighbour, an arm has
    # neither its end (it meets the neighbour's arm) nor its root.
    core = part('core', [SLAB, PEDESTAL, box([6, 6, 6], [10, 10, 10], {'up': '#pipe', 'down': '#pipe'})])
    ends = {d: part(f'pipe_end_{d}', [box([6, 6, 6], [10, 10, 10], {d: '#pipe'})]) for d in SIDES}
    arms = {d: part(f'pipe_{d}', [box(a, b, {f: '#pipe' for f in DIRS if f not in (d, OPPOSITE[d])}), flange(d)])
            for d, (a, b) in ARMS.items()}
    coils = {axis: part(f'coil_{axis}', magnet(axis)) for axis in ('x', 'z')}
    corner = part('corner', [CORNER])
    parts = [{'apply': {'model': core}}]
    parts += [{'when': {d: 'true'}, 'apply': {'model': arms[d]}} for d in SIDES]
    parts += [{'when': {d: 'false'}, 'apply': {'model': ends[d]}} for d in SIDES]
    parts.append({'when': {'north': 'false', 'south': 'false'}, 'apply': {'model': coils['x']}})
    parts.append({'when': {'OR': [{'east': 'false', 'west': 'false', 'north': 'true'},
                                  {'east': 'false', 'west': 'false', 'south': 'true'}]}, 'apply': {'model': coils['z']}})
    parts.append({'when': {'OR': [{'north': 'true', 'east': 'true'}, {'north': 'true', 'west': 'true'},
                                  {'south': 'true', 'east': 'true'}, {'south': 'true', 'west': 'true'}]},
                  'apply': {'model': corner}})
    write(ASSETS / 'blockstates' / f'{name}.json', {'multipart': parts})
    # Item: a straight piece along x.
    item = [SLAB, box([0, 6, 6], [16, 10, 10], all_faces('#pipe')), flange('west'), flange('east')] + magnet('x')
    write(ASSETS / 'models/item' / f'{name}.json',
          {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': textures, 'elements': item})
    loot(name)
    BLOCKS.append(name)

write(FRAG / 'mineable/pickaxe.json', {'replace': False, 'values': [f'robotica:{b}' for b in sorted(BLOCKS)]})
print(f'{len(BLOCKS)} energy blocks written')

# ---------- items ----------
write(ASSETS / 'models/item/strange_matter.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'robotica:item/strange_matter'}})
