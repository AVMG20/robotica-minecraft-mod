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


# ---------- casings, glass, coolant ----------
for name in ('reactor_casing', 'bank_casing', 'fusion_casing'):
    cube_all(name)
for name in ('reactor_glass', 'bank_glass'):
    cube_all(name, render_type='minecraft:translucent')
cube_all('cryo_coolant')
cube_all('reactor_access_port')

# ---------- columns: fuel rod, capacitors, coils, fusion coil ----------
column('reactor_fuel_rod', 'reactor_fuel_rod', 'reactor_fuel_rod_top', 'reactor_fuel_rod_glow', 'reactor_fuel_rod_top_glow')
for n in ('copper', 'redstone', 'ender', 'resonant'):
    column(f'capacitor_{n}', f'capacitor_{n}', f'capacitor_{n}_top', 'capacitor_glow')
for n in ('basic', 'advanced', 'elite'):
    column(f'transfer_coil_{n}', f'transfer_coil_{n}', f'transfer_coil_{n}_top', f'transfer_coil_{n}_glow')
column('fusion_coil', 'fusion_coil', 'fusion_coil_top', 'fusion_coil_glow')

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
for prefix in ('reactor', 'bank', 'fusion'):
    name = f'{prefix}_controller'
    for state in ('off', 'formed', 'on'):
        faces = {d: f'{prefix}_casing' for d in DIRS} | {'north': f'{name}_{state}'}
        model = glow_cube(faces, {'north': f'{name}_{state}_glow'})
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

write(FRAG / 'mineable/pickaxe.json', {'replace': False, 'values': [f'robotica:{b}' for b in sorted(BLOCKS)]})
print(f'{len(BLOCKS)} energy blocks written')
