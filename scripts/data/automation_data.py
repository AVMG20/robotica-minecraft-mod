"""Automation data: custom robot models, blockstates, item models, loot tables, recipes.
Run: python3 scripts/data/automation_data.py   (overwrites only files of automation blocks and items)

Robot models face north (-Z); the blockstate rotates them to face the player. Elements carry no UVs: vanilla
derives them from the element position, which the 8-pixel-period panel textures are designed for.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/robotica'
DATA = ROOT / 'src/main/resources/data/robotica'

ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def box(a, b, tex, faces=ALL, uv=None):
    """Element from corner a to corner b. uv maps face -> [u1, v1, u2, v2] when the default is not wanted."""
    el = {'from': list(a), 'to': list(b), 'faces': {}}
    for f in faces:
        face = {'texture': '#' + tex}
        if uv and f in uv:
            face['uv'] = uv[f]
        el['faces'][f] = face
    return el


SAW_UV = {'east': [0, 0, 16, 16], 'west': [0, 0, 16, 16]}


def chassis():
    """Treads, hip, body, belt in the tier colour, head, visor, eyes, antenna with tier bulb, battery pack."""
    return [
        box((2.5, 0, 3), (5.5, 3, 13), 'dark'),
        box((10.5, 0, 3), (13.5, 3, 13), 'dark'),
        box((5.5, 1, 4.5), (10.5, 3, 11.5), 'brass'),
        box((4, 3, 4), (12, 9.5, 12), 'body'),
        box((3.8, 6, 3.8), (12.2, 7.5, 12.2), 'accent'),
        box((5.5, 3.8, 3.75), (10.5, 5.3, 4), 'dark', faces=('north',)),
        box((5, 9.5, 5), (11, 14.5, 11), 'body'),
        box((5.5, 10.8, 4.7), (10.5, 13.2, 5), 'dark', faces=('north',)),
        box((6.2, 11.4, 4.5), (7.8, 12.6, 4.7), 'eye', faces=('north',)),
        box((8.2, 11.4, 4.5), (9.8, 12.6, 4.7), 'eye', faces=('north',)),
        box((5, 3.5, 12), (11, 8.5, 13.5), 'dark'),
        box((5.5, 5.5, 13.4), (10.5, 6.3, 13.6), 'brass', faces=('south',)),
    ]


def stumpy_elements():
    els = chassis()
    els += [
        # antenna with tier coloured bulb
        box((7.6, 14.5, 7.6), (8.4, 15.2, 8.4), 'brass'),
        box((7, 15.2, 7), (9, 16, 9), 'accent'),
        # right arm with a circular saw
        box((12, 6, 6.5), (13.4, 8.5, 9.5), 'brass'),
        box((13.4, 6.2, 7.2), (14.2, 7.4, 8.8), 'steel'),
        box((14.2, 2.5, 4), (15.2, 10.5, 12), 'steel', uv=SAW_UV),
        # left arm with an axe
        box((2.6, 6, 6.5), (4, 8.5, 9.5), 'brass'),
        box((1.2, 3, 7.6), (2.2, 10, 8.4), 'wood'),
        box((0.4, 7.5, 6), (2.2, 10, 10), 'steel'),
    ]
    return els


def sprout_elements():
    els = chassis()
    els += [
        # sprout on the head, bud in the tier colour
        box((7.6, 14.5, 7.6), (8.4, 15.4, 8.4), 'leaf'),
        box((5.6, 14.9, 7.2), (7.6, 15.5, 8.8), 'leaf'),
        box((8.4, 14.9, 7.2), (10.4, 15.5, 8.8), 'leaf'),
        box((7.1, 15.4, 7.1), (8.9, 16, 8.9), 'accent'),
        # harvest basket on the back
        box((4.5, 2.5, 13.5), (11.5, 7.5, 15.5), 'wood'),
        box((4.2, 7.5, 13.2), (11.8, 8.3, 15.8), 'wood'),
        # right arm with a scythe
        box((12, 6, 6.5), (13.4, 8.5, 9.5), 'brass'),
        box((13.4, 2, 7.5), (14.4, 12.5, 8.5), 'wood'),
        box((13.2, 11.8, 3.5), (14.6, 12.8, 8.5), 'steel'),
        box((13.2, 11, 2), (14.6, 11.9, 4.5), 'steel'),
        box((13.2, 10.2, 1), (14.6, 11.1, 2.6), 'steel'),
        # left arm with a pouch
        box((2.6, 6, 6.5), (4, 8.5, 9.5), 'brass'),
        box((1.6, 4.5, 6.8), (3.2, 7, 9.2), 'dark'),
    ]
    return els


def excavator_elements():
    return [
        box((1, 5, 1), (15, 13, 15), 'steel'),
        box((0, 5, 0), (2.5, 13, 2.5), 'dark'),
        box((13.5, 5, 0), (16, 13, 2.5), 'dark'),
        box((0, 5, 13.5), (2.5, 13, 16), 'dark'),
        box((13.5, 5, 13.5), (16, 13, 16), 'dark'),
        # motor on top
        box((3, 13, 3), (13, 14, 13), 'dark'),
        box((4, 14, 4), (12, 16, 12), 'brass'),
        # vents and indicator light
        box((3, 6, 0.7), (13, 8, 1), 'dark', faces=('north',)),
        box((3, 6, 15), (13, 8, 15.3), 'dark', faces=('south',)),
        box((15, 6, 3), (15.3, 8, 13), 'dark', faces=('east',)),
        box((0.7, 6, 3), (1, 8, 13), 'dark', faces=('west',)),
        box((6.5, 9.5, 0.6), (9.5, 11.5, 1), 'light', faces=('north',)),
        box((3, 9.5, 0.6), (5, 11.5, 1), 'brass', faces=('north',)),
        box((11, 9.5, 0.6), (13, 11.5, 1), 'brass', faces=('north',)),
        # drill head underneath
        box((4, 3, 4), (12, 5, 12), 'dark'),
        box((5, 1.8, 5), (11, 3, 11), 'drill'),
        box((6.2, 0.8, 6.2), (9.8, 1.8, 9.8), 'drill'),
        box((7.3, 0, 7.3), (8.7, 0.8, 8.7), 'steel'),
    ]


def model(textures, elements, particle):
    return {
        'render_type': 'minecraft:cutout',
        'parent': 'minecraft:block/block',
        'textures': dict(textures, particle=particle),
        'elements': elements,
    }


def robot(name, elements, body, mk_tex):
    base = f'robotica:block/{name}_base'
    textures = {'body': f'robotica:block/{body}', 'brass': 'robotica:block/automation_brass',
                'dark': 'robotica:block/automation_dark', 'steel': 'robotica:block/automation_steel',
                'wood': 'robotica:block/automation_wood', 'eye': 'robotica:block/automation_eye',
                'saw': 'robotica:block/automation_saw', 'leaf': 'robotica:block/automation_leaf',
                'accent': 'robotica:block/automation_mk1'}
    write(ASSETS / f'models/block/{name}_base.json', model(textures, elements, f'robotica:block/{body}'))
    variants = {}
    for tier in range(1, 5):
        write(ASSETS / f'models/block/{name}_mk{tier}.json',
              {'parent': base, 'textures': {'accent': f'robotica:block/automation_mk{tier}'}})
        for i, facing in enumerate(('north', 'east', 'south', 'west')):
            v = {'model': f'robotica:block/{name}_mk{tier}'}
            if i:
                v['y'] = 90 * i
            variants[f'facing={facing},tier={tier}'] = v
    write(ASSETS / f'blockstates/{name}.json', {'variants': variants})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{name}_mk1'})


def single(name, model_name):
    write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': f'robotica:block/{model_name}'}}})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{model_name}'})


def loot(name):
    write(DATA / f'loot_table/blocks/{name}.json', {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0,
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{'type': 'minecraft:item', 'name': f'robotica:{name}'}],
        }],
        'random_sequence': f'robotica:blocks/{name}',
    })


def ing(x):
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: ing(v) for k, v in key.items()},
        'result': {'id': f'robotica:{name}', 'count': count}})


def shapeless(name, items):
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [ing(i) for i in items],
        'result': {'id': f'robotica:{name}', 'count': 1}})


def main():
    # Robots: copper Stumpy, brass Sprout
    robot('stumpy', stumpy_elements(), 'automation_copper', 'automation_mk1')
    robot('sprout', sprout_elements(), 'automation_brass', 'automation_mk1')

    # Excavator
    tex = {'steel': 'robotica:block/automation_steel', 'dark': 'robotica:block/automation_dark',
           'brass': 'robotica:block/automation_brass', 'drill': 'robotica:block/excavator_drill',
           'light': 'robotica:block/excavator_light'}
    write(ASSETS / 'models/block/excavator.json', model(tex, excavator_elements(), 'robotica:block/automation_steel'))
    single('excavator', 'excavator')

    # Supply crate
    write(ASSETS / 'models/block/supply_crate.json', {
        'parent': 'minecraft:block/cube_column',
        'textures': {'end': 'robotica:block/supply_crate_top', 'side': 'robotica:block/supply_crate_side'}})
    single('supply_crate', 'supply_crate')

    for name in ('stumpy', 'sprout', 'excavator', 'supply_crate'):
        loot(name)

    # Recipes (component ladder: Age 0 bots, Age 1 excavator and Mk2 kit, Age 2 Mk3 kit, Age 4 Mk4 kit)
    shapeless('stumpy', ['wooden_chassis', 'clockwork_mechanism', 'minecraft:stone_axe'])
    shapeless('sprout', ['wooden_chassis', 'clockwork_mechanism', 'minecraft:stone_hoe'])
    # The real quarry waits for the first diamonds: a diamond pickaxe is its drill head.
    shaped('excavator', ['PBP', 'MXM', 'PIP'], {'P': '#c:plates/iron', 'B': 'basic_circuit', 'M': 'electric_motor',
                                             'X': 'minecraft:diamond_pickaxe', 'I': 'iron_casing'})
    shaped('supply_crate', ['PPP', 'C C', 'PPP'], {'P': '#minecraft:planks', 'C': '#c:ingots/copper'})
    shaped('farm_kit_mk2', ['BMB', 'PIP'], {'B': 'basic_circuit', 'M': 'electric_motor', 'P': '#c:plates/iron', 'I': 'iron_casing'})
    shaped('farm_kit_mk3', ['ASA', 'GRG'], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'G': '#c:plates/gold', 'R': 'reinforced_casing'})
    shaped('farm_kit_mk4', ['CPC', 'NEN'], {'C': 'null_circuit', 'P': 'plasma_actuator', 'N': 'null_casing', 'E': '#c:ender_pearls'})
    print('automation data written')


if __name__ == '__main__':
    main()
