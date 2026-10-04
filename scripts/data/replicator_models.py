"""Writes blockstates, block/item models and loot tables of the replicator module.
Run: python3 scripts/data/replicator_models.py   (overwrites files named after replicator blocks/items only)"""
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


def loot(name):
    write(DATA / 'loot_table/blocks' / f'{name}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
                   'entries': [{'type': 'minecraft:item', 'name': f'robotica:{name}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'robotica:blocks/{name}',
    })


# ---------- frame and glass ----------
write(ASSETS / 'models/block/replicator_frame.json', {
    'parent': 'minecraft:block/cube_all', 'textures': {'all': tex('replicator_frame')}})
write(ASSETS / 'blockstates/replicator_frame.json', {'variants': {'': {'model': 'robotica:block/replicator_frame'}}})
write(ASSETS / 'models/item/replicator_frame.json', {'parent': 'robotica:block/replicator_frame'})
loot('replicator_frame')

write(ASSETS / 'models/block/replicator_glass.json', {
    'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:translucent',
    'textures': {'all': tex('replicator_glass')}})
write(ASSETS / 'blockstates/replicator_glass.json', {'variants': {'': {'model': 'robotica:block/replicator_glass'}}})
write(ASSETS / 'models/item/replicator_glass.json', {'parent': 'robotica:block/replicator_glass'})
loot('replicator_glass')

# ---------- controller: three faces (not formed / formed / working), six facings ----------
for state in ('off', 'formed', 'on'):
    write(ASSETS / f'models/block/replicator_controller_{state}.json', {
        'parent': 'minecraft:block/orientable',
        'textures': {'top': tex('replicator_controller_top'), 'side': tex('replicator_controller_side'),
                     'front': tex(f'replicator_controller_front_{state}'), 'particle': tex('replicator_controller_side')},
    })

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
