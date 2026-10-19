"""Writes blockstates, block/item models and loot tables of the replicator module.
Run: python3 scripts/data/replicator_models.py   (overwrites files named after replicator blocks/items only)"""
import json
import pathlib

import connected_glass

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tex(name):
    return f'robotica:block/{name}'


def loot(name):
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
                   'entries': [{'type': 'minecraft:item', 'name': f'robotica:{name}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


# ---------- frame and glass ----------
GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')
SIDES = ('north', 'south', 'west', 'east')


def glow_cube(faces, glow):
    """Cube with face -> texture and a coplanar full-bright overlay (face -> texture; none when empty)."""
    textures = {f'f_{d}': tex(t) for d, t in faces.items()}
    textures.update({f'g_{d}': tex(t) for d, t in glow.items()})
    textures['particle'] = tex(faces['south'])
    elements = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: {'texture': f'#f_{d}', 'cullface': d} for d in faces}}]
    if glow:
        elements.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                         'faces': {d: {'texture': f'#g_{d}', 'cullface': d, 'neoforge_data': GLOW} for d in glow}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}


# Formed look: the controller sets frame=wall|x|y|z|corner on its frames (FramedPartBlock) and formed=true on its
# glass: beams with a teal conduit along the edges, corner caps, plain panels in the faces, windows joined into the
# frame. Loose, a frame shows its circuit plate.
m = 'robotica:block/replicator_frame'
write(ASSETS / 'models/block/replicator_frame.json', glow_cube({d: 'replicator_frame' for d in DIRS},
                                                               {d: 'replicator_frame_glow' for d in DIRS}))
write(ASSETS / 'models/block/replicator_frame_wall.json', {'parent': 'minecraft:block/cube_all',
                                                            'textures': {'all': tex('replicator_panel')}})
write(ASSETS / 'models/block/replicator_frame_beam.json', glow_cube(
    {d: 'replicator_frame_beam' for d in SIDES} | {'up': 'replicator_frame_corner', 'down': 'replicator_frame_corner'},
    {d: 'replicator_frame_beam_glow' for d in SIDES}))
write(ASSETS / 'models/block/replicator_frame_corner.json', glow_cube({d: 'replicator_frame_corner' for d in DIRS},
                                                                      {d: 'replicator_frame_corner_glow' for d in DIRS}))
write(ASSETS / 'blockstates/replicator_frame.json', {'variants': {
    'frame=none': {'model': m}, 'frame=wall': {'model': m + '_wall'}, 'frame=corner': {'model': m + '_corner'},
    'frame=y': {'model': m + '_beam'}, 'frame=z': {'model': m + '_beam', 'x': 90},
    'frame=x': {'model': m + '_beam', 'x': 90, 'y': 90}}})
write(ASSETS / 'models/item/replicator_frame.json', {'parent': m})
loot('replicator_frame')

connected_glass.write_glass(write, ASSETS, 'replicator_glass')
loot('replicator_glass')

# ---------- controller: three faces (not formed / formed / working), six facings ----------
for state in ('off', 'formed', 'on'):
    faces = {'north': f'replicator_controller_front_{state}', 'up': 'replicator_controller_top',
             'down': 'replicator_controller_top', 'south': 'replicator_controller_side',
             'west': 'replicator_controller_side', 'east': 'replicator_controller_side'}
    write(ASSETS / f'models/block/replicator_controller_{state}.json',
          glow_cube(faces, {'north': f'replicator_controller_front_{state}_glow'}))

# model north faces -z; y turns it around the vertical axis, x 270 turns the front up and x 90 down
ROTATION = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
variants = {}
for facing, rot in ROTATION.items():
    for formed in (False, True):
        for lit in (False, True):
            state = 'on' if (formed and lit) else 'formed' if formed else 'off'
            variants[f'facing={facing},formed={str(formed).lower()},lit={str(lit).lower()}'] = dict(
                {'model': f'robotica:block/replicator_controller_{state}'}, **rot)
write(ASSETS / 'blockstates/replicator_controller.json', {'variants': variants})
write(ASSETS / 'models/item/replicator_controller.json', {'parent': 'robotica:block/replicator_controller_formed'})
loot('replicator_controller')

# ---------- essence vial: bottle fills with the sample count (predicate registered in ReplicatorClient) ----------
write(ASSETS / 'models/item/essence_vial.json', {
    'parent': 'minecraft:item/generated',
    'textures': {'layer0': 'robotica:item/essence_vial'},
    'overrides': [
        {'predicate': {'robotica:essence_fill': 0.01}, 'model': 'robotica:item/essence_vial_partial'},
        {'predicate': {'robotica:essence_fill': 1.0}, 'model': 'robotica:item/essence_vial_full'},
    ],
})
for name in ('essence_vial_partial', 'essence_vial_full'):
    write(ASSETS / f'models/item/{name}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'robotica:item/{name}'}})

# ---------- mob farms: shared 5x5x5 shell (casing, glass, ports, rams), a roof matrix and a controller per farm ----------
# Casing takes the formed look like the replicator frame; the farmers face one of four sides.
m = 'robotica:block/farm_casing'
write(ASSETS / 'models/block/farm_casing.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex('farm_casing')}})
write(ASSETS / 'models/block/farm_casing_wall.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex('farm_panel')}})
write(ASSETS / 'models/block/farm_casing_beam.json', glow_cube(
    {d: 'farm_frame' for d in SIDES} | {'up': 'farm_frame_corner', 'down': 'farm_frame_corner'},
    {d: 'farm_frame_glow' for d in SIDES}))
write(ASSETS / 'models/block/farm_casing_corner.json', glow_cube({d: 'farm_frame_corner' for d in DIRS},
                                                                 {d: 'farm_frame_corner_glow' for d in DIRS}))
write(ASSETS / 'blockstates/farm_casing.json', {'variants': {
    'frame=none': {'model': m}, 'frame=wall': {'model': m + '_wall'}, 'frame=corner': {'model': m + '_corner'},
    'frame=y': {'model': m + '_beam'}, 'frame=z': {'model': m + '_beam', 'x': 90},
    'frame=x': {'model': m + '_beam', 'x': 90, 'y': 90}}})
write(ASSETS / 'models/item/farm_casing.json', {'parent': m})
loot('farm_casing')

connected_glass.write_glass(write, ASSETS, 'farm_glass')
loot('farm_glass')


def farm_part(name, faces, glow):
    write(ASSETS / 'models/block' / f'{name}.json', glow_cube(faces, glow))
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': {'': {'model': f'robotica:block/{name}'}}})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})
    loot(name)


farm_part('farm_port', {d: 'farm_port' for d in DIRS}, {d: 'farm_port_glow' for d in DIRS})
farm_part('impact_ram', {d: 'impact_ram' for d in SIDES} | {'up': 'impact_ram_top', 'down': 'impact_ram_top'}, {})
for matrix in ('golem_matrix', 'wither_matrix'):
    farm_part(matrix, {d: matrix for d in DIRS}, {d: f'{matrix}_glow' for d in DIRS})

FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
for name in ('iron_golem_farmer', 'wither_farmer'):
    for state in ('off', 'formed', 'on'):
        faces = {d: 'farm_casing' for d in DIRS} | {'north': f'{name}_{state}'}
        glow = {} if state == 'off' else {'north': f'{name}_{state}_glow'}
        model = glow_cube(faces, glow)
        model['textures']['particle'] = tex('farm_casing')
        write(ASSETS / f'models/block/{name}_{state}.json', model)
    variants = {}
    for facing, y in FACING_Y.items():
        for formed in (False, True):
            for lit in (False, True):
                state = 'on' if formed and lit else 'formed' if formed else 'off'
                entry = {'model': f'robotica:block/{name}_{state}'}
                if y:
                    entry['y'] = y
                variants[f'facing={facing},formed={str(formed).lower()},lit={str(lit).lower()}'] = entry
    write(ASSETS / f'blockstates/{name}.json', {'variants': variants})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{name}_formed'})
    loot(name)
