"""Connected multiblock glass (StructureGlassBlock), shared by energy_models.py and replicator_models.py.

The block has a boolean per side (north, east, south, west, up, down). Each face picks one of 16 textures
(textures/block/<name>/<joined>.png, from scripts/textures/*.py) from the four sides around it: bit 1 top, 2 right,
4 bottom, 8 left in texture space, set when that side joins. The textures are never rotated, so the glints stay put.
"""

# Texture edges (top, right, bottom, left) of each face, as world sides (default face UVs).
FACE_EDGES = {
    'north': ('up', 'west', 'down', 'east'), 'south': ('up', 'east', 'down', 'west'),
    'east': ('up', 'north', 'down', 'south'), 'west': ('up', 'south', 'down', 'north'),
    'up': ('north', 'east', 'south', 'west'), 'down': ('south', 'east', 'north', 'west'),
}


def write_glass(write, assets, name):
    """96 one-face models (models/block/<name>/<face>_<joined>), a multipart blockstate and the item model."""
    parts = []
    for d, edges in FACE_EDGES.items():
        for joined in range(16):
            model = f'{name}/{d}_{joined}'
            write(assets / 'models/block' / f'{model}.json', {
                'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
                'textures': {'particle': f'robotica:block/{name}/0', 'pane': f'robotica:block/{name}/{joined}'},
                'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: {'texture': '#pane', 'cullface': d}}}]})
            parts.append({'when': {edges[i]: 'true' if joined >> i & 1 else 'false' for i in range(4)},
                          'apply': {'model': f'robotica:block/{model}'}})
    write(assets / 'blockstates' / f'{name}.json', {'multipart': parts})
    write(assets / 'models/block' / f'{name}.json', {
        'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:translucent',
        'textures': {'all': f'robotica:block/{name}/0'}})
    write(assets / 'models/item' / f'{name}.json', {'parent': f'robotica:block/{name}'})
