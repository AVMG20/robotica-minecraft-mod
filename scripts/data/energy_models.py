"""Writes blockstates, block/item models, loot tables and the pickaxe tag of the energy module.
Run: python3 scripts/data/energy_models.py   (overwrites files named after energy blocks only)"""
import json
import pathlib

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


def box(frm, to, faces, shade=True, glow=False):
    """Element with face -> texture variable; faces touching the block edge cull against that side."""
    edge = {'down': frm[1] == 0, 'up': to[1] == 16, 'north': frm[2] == 0, 'south': to[2] == 16,
            'west': frm[0] == 0, 'east': to[0] == 16}
    out = {}
    for d, t in faces.items():
        face = {'texture': t}
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
for name in ('reactor_casing', 'bank_casing'):
    cube_all(name)
for name in ('reactor_glass', 'bank_glass'):
    cube_all(name, render_type='minecraft:translucent')
cube_all('cryo_coolant')
cube_all('reactor_access_port')

# ---------- columns: amplifiers, damper, capacitors, coils ----------
for n in ('flux', 'pyro', 'resonant'):
    column(f'{n}_amplifier', f'{n}_amplifier', f'{n}_amplifier_top', f'{n}_amplifier_glow', f'{n}_amplifier_top_glow')
column('graphite_damper', 'graphite_damper', 'graphite_damper_top')
for n in ('copper', 'redstone', 'ender', 'resonant'):
    column(f'capacitor_{n}', f'capacitor_{n}', f'capacitor_{n}_top', 'capacitor_glow')
for n in ('basic', 'advanced', 'elite'):
    column(f'transfer_coil_{n}', f'transfer_coil_{n}', f'transfer_coil_{n}_top', f'transfer_coil_{n}_glow')

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
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
CONTROLLERS = {
    'reactor_controller': ('reactor', {}),
    'bank_controller': ('bank', {}),
    # Spire Base: copper winding band on the sides, the terminal plate on top.
    'spire_base': ('spire', {'faces': {'south': 'spire_base', 'west': 'spire_base', 'east': 'spire_base',
                                       'up': 'spire_base_top'},
                             'glow': {'south': 'spire_base_glow', 'west': 'spire_base_glow', 'east': 'spire_base_glow'}}),
    'collider_controller': ('collider', {}),
}
for name, (prefix, extra) in CONTROLLERS.items():
    for state in ('off', 'formed', 'on'):
        faces = {d: f'{prefix}_casing' for d in DIRS} | extra.get('faces', {}) | {'north': f'{prefix}_controller_{state}'}
        glow = extra.get('glow', {}) | {'north': f'{prefix}_controller_{state}_glow'}
        model = glow_cube(faces, glow)
        model['textures']['particle'] = tex(f'{prefix}_casing')
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
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}_formed'})
    loot(name, ['robotica:bank_energy'] if prefix == 'bank' else None)
    BLOCKS.append(name)

# ---------- Spire Crown: copper stem and toroid, a caged glass orb with a core that lights up ----------
for lit in (False, True):
    core = 'spire_crown_core_lit' if lit else 'spire_crown_core'
    model = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'ambient_occlusion': False,
             'textures': {'copper': tex('spire_crown_copper'), 'coil': tex('spire_crown_coil'),
                          'orb': tex('spire_crown_orb'), 'core': tex(core), 'particle': tex('spire_crown_copper')},
             'elements': [
                 box([5, 0, 5], [11, 4, 11], all_faces('#copper')),
                 box([1, 4, 1], [15, 8, 15], {'down': '#copper', 'up': '#copper', 'north': '#coil', 'south': '#coil',
                                              'west': '#coil', 'east': '#coil'}),
                 box([6, 9, 6], [10, 13, 10], all_faces('#core'), shade=not lit, glow=lit),
                 box([3, 8, 3], [13, 16, 13], all_faces('#orb')),
             ]}
    write(ASSETS / 'models/block' / f'spire_crown{"_lit" if lit else ""}.json', model)
write(ASSETS / 'blockstates/spire_crown.json', {'variants': {
    'lit=false': {'model': 'robotica:block/spire_crown'},
    'lit=true': {'model': 'robotica:block/spire_crown_lit'}}})
write(ASSETS / 'models/item/spire_crown.json', {'parent': 'robotica:block/spire_crown_lit'})
loot('spire_crown')
BLOCKS.append('spire_crown')

# ---------- Ring segments: slab, glass beam pipe joining the neighbours (multipart), magnets on straights ----------
# The pipe is centred at y 8 (ColliderRenderer.PIPE_Y = 0.5).
ARMS = {'north': ([6, 6, 0], [10, 10, 6]), 'south': ([6, 6, 10], [10, 10, 16]),
        'west': ([0, 6, 6], [6, 10, 10]), 'east': ([10, 6, 6], [16, 10, 10])}
COILS = {'x': [([4, 4, 1], [12, 12, 5]), ([4, 4, 11], [12, 12, 15])],     # beam along x: magnets north and south
         'z': [([1, 4, 4], [5, 12, 12]), ([11, 4, 4], [15, 12, 12])]}     # beam along z: magnets west and east
for name in ('accelerator_segment', 'resonant_segment'):
    textures = {'base': tex(f'{name}_base'), 'coil': tex(f'{name}_coil'), 'glow': tex(f'{name}_coil_glow'),
                'pipe': tex(f'{name}_pipe'), 'particle': tex(f'{name}_base')}

    def part(suffix, elements):
        write(ASSETS / 'models/block' / f'{name}_{suffix}.json',
              {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': textures,
               'elements': elements})
        return f'robotica:block/{name}_{suffix}'

    core = part('core', [box([0, 0, 0], [16, 4, 16], all_faces('#base')),
                         box([7, 4, 7], [9, 6, 9], all_faces('#base')),
                         box([6, 6, 6], [10, 10, 10], all_faces('#pipe'))])
    arms = {d: part(f'pipe_{d}', [box(a, b, {f: '#pipe' for f in DIRS if f != {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[d]})])
            for d, (a, b) in ARMS.items()}
    coils = {}
    for axis, boxes in COILS.items():
        elements = [box(a, b, all_faces('#coil')) for a, b in boxes]
        elements += [box(a, b, all_faces('#glow'), shade=False, glow=True) for a, b in boxes]
        coils[axis] = part(f'coil_{axis}', elements)
    corner = part('corner', [box([5, 5, 5], [11, 11, 11], all_faces('#coil')),
                             box([5, 5, 5], [11, 11, 11], all_faces('#glow'), shade=False, glow=True)])
    parts = [{'apply': {'model': core}}]
    parts += [{'when': {d: 'true'}, 'apply': {'model': arms[d]}} for d in SIDES]
    parts.append({'when': {'north': 'false', 'south': 'false'}, 'apply': {'model': coils['x']}})
    parts.append({'when': {'OR': [{'east': 'false', 'west': 'false', 'north': 'true'},
                                  {'east': 'false', 'west': 'false', 'south': 'true'}]}, 'apply': {'model': coils['z']}})
    parts.append({'when': {'OR': [{'north': 'true', 'east': 'true'}, {'north': 'true', 'west': 'true'},
                                  {'south': 'true', 'east': 'true'}, {'south': 'true', 'west': 'true'}]},
                  'apply': {'model': corner}})
    write(ASSETS / 'blockstates' / f'{name}.json', {'multipart': parts})
    # Item: a straight piece along x.
    item = [box([0, 0, 0], [16, 4, 16], all_faces('#base')), box([7, 4, 7], [9, 6, 9], all_faces('#base')),
            box([0, 6, 6], [16, 10, 10], all_faces('#pipe'))]
    item += [box(a, b, all_faces('#coil')) for a, b in COILS['x']]
    item += [box(a, b, all_faces('#glow'), shade=False, glow=True) for a, b in COILS['x']]
    write(ASSETS / 'models/item' / f'{name}.json',
          {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': textures, 'elements': item})
    loot(name)
    BLOCKS.append(name)

write(FRAG / 'mineable/pickaxe.json', {'replace': False, 'values': [f'robotica:{b}' for b in sorted(BLOCKS)]})
print(f'{len(BLOCKS)} energy blocks written')

# ---------- items ----------
write(ASSETS / 'models/item/strange_matter.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'robotica:item/strange_matter'}})
