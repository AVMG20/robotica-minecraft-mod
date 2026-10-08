"""Automation data: custom robot models, blockstates, item models, loot tables, recipes.
Run: python3 scripts/data/automation_data.py   (overwrites only files of automation blocks and items)

Robot models face north (-Z); the blockstate rotates them to face the player. Elements carry no UVs: vanilla
derives them from the element position, which the 8-pixel-period panel textures are designed for.
"""
import json
import pathlib
import sys

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


SAW_UV = {'east': [0, 0, 8, 8], 'west': [0, 0, 8, 8]}
GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}

# Texel regions of the 32x32 <robot>_body atlas, shared with scripts/textures/automation.py
sys.path.insert(0, str(ROOT / 'scripts'))
sys.path.insert(0, str(ROOT / 'scripts/textures'))
from automation import ATLAS  # noqa: E402


def at(name, mirror=False, w=None, h=None):
    """uv of an atlas region (the atlas is 32 px, so uv = texel / 2); mirror flips it left-right."""
    u, v, rw, rh = ATLAS[name]
    rw, rh = w or rw, h or rh
    return [(u + rw) / 2, v / 2, u / 2, (v + rh) / 2] if mirror else [u / 2, v / 2, (u + rw) / 2, (v + rh) / 2]


def atlas_box(a, b, faces, glow=False):
    """faces: direction -> (atlas region name, mirror). Texture '#atlas'."""
    el = {'from': list(a), 'to': list(b), 'faces': {}}
    for d, (name, mirror) in faces.items():
        el['faces'][d] = {'texture': '#atlas', 'uv': at(name, mirror)}
        if glow:
            el['faces'][d]['neoforge_data'] = dict(GLOW)
    if glow:
        el['shade'] = False
    return el


def chassis():
    """Chibi proportions: treads, hip, a compact body with the tier belt low on the hips, a big head with a dark visor
    and two glowing eyes, battery pack on the back, antenna with a tier coloured bulb."""
    tread = {'north': ('tread_end', False), 'south': ('tread_end', False), 'west': ('tread_side', False),
             'east': ('tread_side', True), 'up': ('tread_top', False), 'down': ('tread_top', False)}
    belt_uv = [3.8, 6, 12.2, 7.5]
    belt = {'from': [3.8, 3, 3.8], 'to': [12.2, 4.5, 12.2],
            'faces': {d: {'texture': '#accent', 'uv': belt_uv} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    return [
        atlas_box((2.5, 0, 3), (5.5, 3, 13), tread),
        atlas_box((10.5, 0, 3), (13.5, 3, 13), tread),
        box((5.5, 1, 4.5), (10.5, 3, 11.5), 'brass'),
        atlas_box((4, 3, 4), (12, 9, 12), {
            'north': ('body_front', False), 'south': ('body_back', False), 'west': ('body_side', False),
            'east': ('body_side', True), 'up': ('body_top', False), 'down': ('body_bottom', False)}),
        belt,
        atlas_box((3.5, 9, 4.5), (12.5, 15, 11.5), {
            'north': ('head_front', False), 'south': ('head_back', False), 'west': ('head_side', False),
            'east': ('head_side', True), 'up': ('head_top', False), 'down': ('head_bottom', False)}),
        atlas_box((5, 11, 4.3), (7, 13, 4.5), {'north': ('eye', False)}, glow=True),
        atlas_box((9, 11, 4.3), (11, 13, 4.5), {'north': ('eye', True)}, glow=True),
        atlas_box((5, 3.5, 12), (11, 8.5, 13.5), {
            'south': ('pack_back', False), 'west': ('pack_side', False), 'east': ('pack_side', True),
            'up': ('pack_top', False)}),
    ]


def antenna(stalk='brass'):
    return [
        box((7.5, 15, 7.5), (8.5, 16, 8.5), stalk),
        {'from': [7, 16, 7], 'to': [9, 17.5, 9], 'faces': {
            d: {'texture': '#accent', 'uv': [6, 1, 8, 2.5] if d not in ('up', 'down') else [6, 1, 8, 3]}
            for d in ('north', 'south', 'east', 'west', 'up', 'down')}},
    ]


# Arms are separate models drawn by the block entity renderer (FarmBotRenderer), which swings them around the shoulder
# pivot on every action. The block models carry the body only; the item models show the whole robot.
# Pivots (model px) must match FarmBotRenderer.
STUMPY_ARMS = {
    # right arm: shoulder and hub; the saw blade is its own part so it can spin
    'saw_arm': [
        box((12, 6, 6.5), (13.4, 8.5, 9.5), 'brass'),
        box((13.4, 6.2, 7.2), (14.2, 7.4, 8.8), 'steel'),
    ],
    'saw_blade': [
        box((14.2, 2.5, 4), (15.2, 10.5, 12), 'saw', faces=('east', 'west'), uv=SAW_UV),
    ],
    # left arm with an axe
    'axe_arm': [
        box((2.6, 6, 6.5), (4, 8.5, 9.5), 'brass'),
        box((1.2, 3, 7.6), (2.2, 10, 8.4), 'wood'),
        box((0.4, 7.5, 6), (2.2, 10, 10), 'steel'),
    ],
}

SPROUT_ARMS = {
    # right arm with a scythe
    'scythe_arm': [
        box((12, 6, 6.5), (13.4, 8.5, 9.5), 'brass'),
        box((13.4, 2, 7.5), (14.4, 12.5, 8.5), 'wood'),
        box((13.2, 11.8, 3.5), (14.6, 12.8, 8.5), 'steel'),
        box((13.2, 11, 2), (14.6, 11.9, 4.5), 'steel'),
        box((13.2, 10.2, 1), (14.6, 11.1, 2.6), 'steel'),
    ],
    # left arm with a pouch
    'pouch_arm': [
        box((2.6, 6, 6.5), (4, 8.5, 9.5), 'brass'),
        box((1.6, 4.5, 6.8), (3.2, 7, 9.2), 'dark'),
    ],
}


def stumpy_elements():
    return chassis() + antenna()


def sprout_elements():
    els = chassis()
    els += [
        # sprout on the head, bud in the tier colour
        box((7.6, 15, 7.6), (8.4, 16, 8.4), 'leaf'),
        box((5.4, 15.5, 7.2), (7.6, 16.1, 8.8), 'leaf'),
        box((8.4, 15.5, 7.2), (10.6, 16.1, 8.8), 'leaf'),
        {'from': [7.1, 16, 7.1], 'to': [8.9, 17.4, 8.9], 'faces': {
            d: {'texture': '#accent', 'uv': [6, 1, 7.8, 2.4]} for d in ('north', 'south', 'east', 'west', 'up', 'down')}},
        # harvest basket on the back
        box((4.5, 2.5, 13.5), (11.5, 7.5, 15.5), 'wood'),
        box((4.2, 7.5, 13.2), (11.8, 8.3, 15.8), 'wood'),
    ]
    return els


def excavator_elements():
    return [
        {'from': [1, 5, 1], 'to': [15, 13, 15], 'faces': {
            'north': {'texture': '#side'}, 'south': {'texture': '#side'}, 'east': {'texture': '#side'},
            'west': {'texture': '#side'}, 'up': {'texture': '#steel'}, 'down': {'texture': '#dark'}}},
        box((0, 5, 0), (2.5, 13, 2.5), 'dark'),
        box((13.5, 5, 0), (16, 13, 2.5), 'dark'),
        box((0, 5, 13.5), (2.5, 13, 16), 'dark'),
        box((13.5, 5, 13.5), (16, 13, 16), 'dark'),
        # motor on top
        box((3, 13, 3), (13, 14, 13), 'dark'),
        box((4, 14, 4), (12, 16, 12), 'accent'),    # motor housing: brass on Mk1, the Mk colour above
        # vents and indicator light
        box((3, 6, 0.7), (13, 8, 1), 'dark', faces=('north',)),
        box((3, 6, 15), (13, 8, 15.3), 'dark', faces=('south',)),
        box((15, 6, 3), (15.3, 8, 13), 'dark', faces=('east',)),
        box((0.7, 6, 3), (1, 8, 13), 'dark', faces=('west',)),
        {'from': [6.5, 9.5, 0.6], 'to': [9.5, 11.5, 1], 'shade': False, 'faces': {
            'north': {'texture': '#light', 'uv': [5, 5, 8, 7], 'neoforge_data': dict(GLOW)}}},
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


def robot(name, elements, arms, body):
    base = f'robotica:block/{name}_base'
    textures = {'atlas': f'robotica:block/{name}_body', 'brass': 'robotica:block/automation_brass',
                'dark': 'robotica:block/automation_dark', 'steel': 'robotica:block/automation_steel',
                'wood': 'robotica:block/automation_wood', 'eye': 'robotica:block/automation_eye',
                'saw': 'robotica:block/automation_saw', 'leaf': 'robotica:block/automation_leaf',
                'accent': 'robotica:block/automation_mk1'}
    particle = f'robotica:block/{body}'
    write(ASSETS / f'models/block/{name}_base.json', model(textures, elements, particle))
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
    # Arm parts carry no accent, so one model each serves every Mk.
    all_arms = []
    for part, els in arms.items():
        write(ASSETS / f'models/block/{name}_{part}.json', model(textures, els, particle))
        all_arms += els
    # The item shows the whole robot, arms included.
    write(ASSETS / f'models/item/{name}.json', model(textures, elements + all_arms, particle))


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
    robot('stumpy', stumpy_elements(), STUMPY_ARMS, 'automation_copper')
    robot('sprout', sprout_elements(), SPROUT_ARMS, 'automation_brass')

    # Excavator Mk1-4: one base model; the Mk swaps the motor housing colour, LIT swaps in the spinning drill and the
    # blinking lamp. Mk1 keeps the id "excavator".
    tex = {'steel': 'robotica:block/automation_steel', 'dark': 'robotica:block/automation_dark',
           'brass': 'robotica:block/automation_brass', 'drill': 'robotica:block/excavator_drill',
           'light': 'robotica:block/excavator_light', 'side': 'robotica:block/excavator_side',
           'accent': 'robotica:block/automation_brass'}
    write(ASSETS / 'models/block/excavator.json', model(tex, excavator_elements(), 'robotica:block/automation_steel'))
    working = {'drill': 'robotica:block/excavator_drill_on', 'light': 'robotica:block/excavator_light_on'}
    for tier in range(1, 5):
        name = 'excavator' if tier == 1 else f'excavator_mk{tier}'
        accent = {} if tier == 1 else {'accent': f'robotica:block/automation_mk{tier}'}
        if tier > 1:
            write(ASSETS / f'models/block/{name}.json', {'parent': 'robotica:block/excavator', 'textures': accent})
        write(ASSETS / f'models/block/{name}_on.json', {'parent': 'robotica:block/excavator', 'textures': dict(accent, **working)})
        write(ASSETS / f'blockstates/{name}.json', {'variants': {
            'lit=false': {'model': f'robotica:block/{name}'}, 'lit=true': {'model': f'robotica:block/{name}_on'}}})
        write(ASSETS / f'models/item/{name}.json', {'parent': f'robotica:block/{name}'})
        loot(name)

    # Supply crate
    write(ASSETS / 'models/block/supply_crate.json', {
        'parent': 'minecraft:block/cube_column',
        'textures': {'end': 'robotica:block/supply_crate_top', 'side': 'robotica:block/supply_crate_side'}})
    single('supply_crate', 'supply_crate')

    for name in ('stumpy', 'sprout', 'supply_crate'):
        loot(name)

    # Recipes (component ladder: Age 0 bots, Age 1 excavator and Mk2 kit, Age 2 Mk3 kit, Age 4 Mk4 kit)
    shapeless('stumpy', ['wooden_chassis', 'clockwork_mechanism', 'minecraft:stone_axe'])
    shapeless('sprout', ['wooden_chassis', 'clockwork_mechanism', 'minecraft:stone_hoe'])
    # The real quarry waits for the first diamonds: a diamond pickaxe is its drill head.
    shaped('excavator', ['PBP', 'MXM', 'PIP'], {'P': '#c:plates/iron', 'B': 'basic_circuit', 'M': 'electric_motor',
                                             'X': 'minecraft:diamond_pickaxe', 'I': 'iron_casing'})
    # Excavator Mk2-4: each consumes the Mk before it plus that age's circuit, actuator and casing. Right-click the
    # placed machine with the next Mk to upgrade it in place (the old block comes back).
    shaped('excavator_mk2', [' A ', 'SXS', ' R '], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'X': 'excavator',
                                                  'R': 'reinforced_casing'})
    shaped('excavator_mk3', [' Q ', 'PXP', ' B '], {'Q': 'quantum_circuit', 'P': 'plasma_actuator', 'X': 'excavator_mk2',
                                                  'B': 'blazing_casing'})
    shaped('excavator_mk4', [' N ', 'PXP', 'ECE'], {'N': 'null_circuit', 'P': 'plasma_actuator', 'X': 'excavator_mk3',
                                                  'E': '#c:ender_pearls', 'C': 'null_casing'})
    shaped('supply_crate', ['PPP', 'C C', 'PPP'], {'P': '#minecraft:planks', 'C': '#c:ingots/copper'})
    shaped('farm_kit_mk2', ['BMB', 'PIP'], {'B': 'basic_circuit', 'M': 'electric_motor', 'P': '#c:plates/iron', 'I': 'iron_casing'})
    shaped('farm_kit_mk3', ['ASA', 'GRG'], {'A': 'advanced_circuit', 'S': 'servo_actuator', 'G': '#c:plates/gold', 'R': 'reinforced_casing'})
    shaped('farm_kit_mk4', ['CPC', 'NEN'], {'C': 'null_circuit', 'P': 'plasma_actuator', 'N': 'null_casing', 'E': '#c:ender_pearls'})
    print('automation data written')


if __name__ == '__main__':
    main()
