"""Shared block model builder of the processing machines (Grinder, Electric Furnace, Metal Press, Alloy Smelter,
Centrifuge, Assembler), used by processing_models.py, industry_data.py and power_models.py.

A machine is the casing cube (per-face textures, culled against neighbours), plus optional detail elements (inset
bays, fins, lids; full-bright when they carry 'glow': True) and, while lit, a coplanar full-bright overlay: the given
face overlays and the energy channel on the three casing sides (machine_side_glow).
"""

GLOW = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
DIRS = ('down', 'up', 'north', 'south', 'west', 'east')
SIDE_GLOW = {'south': 'machine_side_glow', 'west': 'machine_side_glow', 'east': 'machine_side_glow'}


def tex(name):
    return name if ':' in name else f'robotica:block/{name}'


def machine_model(faces, glow=None, elements=(), textures=None, lit=False):
    """faces: dir -> texture of the casing cube; glow: dir -> full-bright overlay texture; elements: extra model
    elements (an element with 'glow': True gets full-bright faces); textures: extra texture vars they use;
    lit adds the side channel glow."""
    tv = {d: tex(t) for d, t in faces.items()}
    tv['particle'] = tex(faces['south'])
    for k, t in (textures or {}).items():
        tv[k] = tex(t)
    els = [{'from': [0, 0, 0], 'to': [16, 16, 16],
            'faces': {d: {'texture': '#' + d, 'cullface': d} for d in DIRS if d in faces}}]
    for el in elements:
        el = dict(el)
        if el.pop('glow', False):
            el['shade'] = False
            el['faces'] = {d: dict(f, neoforge_data=dict(GLOW)) for d, f in el['faces'].items()}
        els.append(el)
    overlay = dict(SIDE_GLOW) if lit else {}
    overlay.update(glow or {})
    if overlay:
        for d, t in overlay.items():
            tv['glow_' + d] = tex(t)
        els.append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                    'faces': {d: {'texture': '#glow_' + d, 'cullface': d, 'neoforge_data': dict(GLOW)} for d in overlay}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': tv, 'elements': els}
