"""Renders a multiblock of scripts/wiki/multiblocks.json the way the game draws it once it has formed: casings turn
into frame beams and wall panels, glass loses its frame, controllers face out and glow, the Tesla Spire's metal
column gets its coil shell (tinted like the metal), collider segments join up. Seen from the north-east, above, like
the wiki's 3D view.

Usage: python3 scripts/scene_preview.py out.png <multiblock id> [--plain] [--scale 3]
--plain draws the blocks as placed (before the structure forms). The wiki's formed views (docs/wiki/formed/<id>.png)
come from build_wiki.py through render_png().
Keep FRAMED / frame_shape() in step with FramedPartBlock and StructureControllerBlockEntity.applyFormedLook.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import model_preview as mp  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
STATES = ROOT / 'src/main/resources/assets/robotica/blockstates'
MBS = json.loads((ROOT / 'scripts/wiki/multiblocks.json').read_text())

FRAMED = {'reactor_casing', 'bank_casing'}          # FramedPartBlock: frame=none|wall|x|y|z|corner
FORMED_GLASS = {'reactor_glass', 'bank_glass'}       # formed=true drops the pane frame
CONTROLLERS = {'reactor_controller', 'bank_controller', 'spire_base', 'collider_controller'}
RING = {'accelerator_segment', 'resonant_segment', 'collider_controller'}
# MapColor of the vanilla conductor blocks (the Spire renderer tints the coil shell with the block's map colour)
MAP_COLORS = {
    'copper_block': 0xD87F33, 'waxed_copper_block': 0xD87F33, 'exposed_copper': 0x878787, 'weathered_copper': 0x3A8E8C,
    'oxidized_copper': 0x167E86, 'iron_block': 0xA7A7A7, 'gold_block': 0xFAEE4D, 'diamond_block': 0x5CDBD5,
    'emerald_block': 0x00D93A, 'netherite_block': 0x191919, 'redstone_block': 0xFF0000, 'lapis_block': 0x4A80FF,
}
ARC_TINT = (0.55, 0.95, 1.0)                         # SpireRenderer's arc colour on the coil slits
FORMED_CHECKS = {'bank', 'core_reactor', 'spire', 'collider'}      # multiblocks with a formed look


def coil_tint(rgb):
    """Same as SpireRenderer.coilTint: the map colour lifted so dark metals still show their windings."""
    c = [(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255]
    return tuple(min(1.0, 0.3 + 0.85 * v / 255) for v in c)


class Xf:
    """Variant rotation (x then y, about the block centre) and the block's place in the scene, in model units."""

    def __init__(self, bx, by, bz, xrot=0, yrot=0):
        self.o, self.xr, self.yr = (bx * 16, by * 16, bz * 16), xrot % 360, yrot % 360

    def _rot(self, p, c):
        x, y, z = p[0] - c, p[1] - c, p[2] - c
        for _ in range(self.xr // 90):          # x rotation: up turns to north
            y, z = z, -y
        for _ in range(self.yr // 90):          # y rotation: north turns to east
            x, z = -z, x
        return x + c, y + c, z + c

    def p(self, p):
        x, y, z = self._rot(p, 8)
        return x + self.o[0], y + self.o[1], z + self.o[2]

    def n(self, n):
        return self._rot(n, 0)


def matches(cond, props):
    if 'OR' in cond:
        return any(matches(c, props) for c in cond['OR'])
    return all(str(props.get(k, '')) in str(v).split('|') for k, v in cond.items())


def models_for(block, props):
    """[(model, x, y)] for a robotica block id in the given state (variants: first key whose values all match)."""
    data = json.loads((STATES / f'{block}.json').read_text())
    out = []
    if 'variants' in data:
        for key, v in data['variants'].items():
            want = dict(kv.split('=') for kv in key.split(',')) if key else {}
            if all(str(props.get(k, want[k])) == want[k] for k in want):
                v = v[0] if isinstance(v, list) else v
                out.append((v['model'], v.get('x', 0), v.get('y', 0)))
                break
    else:
        for part in data['multipart']:
            if 'when' not in part or matches(part['when'], props):
                v = part['apply']
                v = v[0] if isinstance(v, list) else v
                out.append((v['model'], v.get('x', 0), v.get('y', 0)))
    return out


def frame_shape(x, y, z, size):
    """Frame state of a casing at (x, y, z) in a box of `size`: corner, an edge beam along its axis, or wall."""
    on = [v in (0, s - 1) for v, s in zip((x, y, z), size)]
    n = sum(on)
    if n == 3:
        return 'corner'
    if n == 2:
        return 'xyz'[on.index(False)]
    return 'wall'


def scene(mb_id, formed=True):
    """[(block id, (x, y, z), props, tint)] in world-style coordinates: x east, y up, z south (layout rows go north
    to south)."""
    mb = MBS[mb_id]
    layers, legend = mb['layers'], mb['legend']
    H, D, W = len(layers), len(layers[0]), len(layers[0][0])
    grid = {}
    for y in range(H):
        for z in range(D):
            for x in range(W):
                b = legend.get(layers[y][z][x], 'air')
                if b not in ('air', 'minecraft:air'):
                    grid[(x, y, z)] = b
    kind = mb.get('check')
    out = []
    for (x, y, z), b in sorted(grid.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        props, tint, block = {}, None, b
        if b in CONTROLLERS:
            if kind in ('bank', 'core_reactor', 'replicator'):
                props['facing'] = 'north' if z == 0 else 'south' if z == D - 1 else 'west' if x == 0 else 'east'
            else:
                props['facing'] = 'north'
            props.update(formed=str(formed).lower(), lit=str(formed).lower())
        if formed and b in FRAMED:
            props['frame'] = frame_shape(x, y, z, (W, H, D))
        if b in FORMED_GLASS:
            props['formed'] = str(formed).lower()
        if b == 'spire_crown':
            props.update(formed=str(formed).lower(), lit=str(formed).lower())
        if b in RING:
            for d, (dx, dz) in {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}.items():
                props[d] = str(grid.get((x + dx, y, z + dz)) in RING).lower()
        if formed and kind == 'spire' and b.startswith('minecraft:'):
            block, tint = 'spire_coil', coil_tint(MAP_COLORS.get(b.split(':')[1], 0x888888))
        out.append((block, (x, y, z), props, tint))
    return out, (W, H, D)


# a stand-in for vanilla blocks: light noise, tinted with the block's map colour
mp._tex_cache[('minecraft', 'scene/vanilla')] = (16, 16, [[tuple([int(200 + 55 * mp._hash(x // 2, y // 2, 7))] * 3) + (255,)
                                                         for x in range(16)] for y in range(16)])
mp.VANILLA['minecraft:scene/vanilla'] = {'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
    d: {'texture': 'minecraft:scene/vanilla', 'tintindex': 0} for d in mp.NORMALS}}]}


def render(mb_id, formed=True, scale=3.0, night=False):
    """The scene as rows of RGBA pixels, transparent around the build."""
    blocks, (W, H, D) = scene(mb_id, formed)
    # screen bounds of the box corners
    m = 1.5 if MBS[mb_id].get('check') == 'spire' else 0       # the formed crown's toroid reaches past the column
    pts = [mp.project((x * 16, y * 16, z * 16), scale, 0, 0) for x in (-m, W + m) for y in (0, H + 1) for z in (-m, D + m)]
    pad = 12
    minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
    miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
    w, h = int(maxx - minx + 2 * pad), int(maxy - miny + 2 * pad)
    img = [[(0, 0, 0, 0)] * w for _ in range(h)]
    zbuf = [[-1e9] * w for _ in range(h)]
    for block, (x, y, z), props, tint in blocks:
        if block.startswith('minecraft:'):          # a vanilla block: noise in its map colour
            col = MAP_COLORS.get(block.split(':')[1], 0x888888)
            mp.render_model(img, zbuf, 'minecraft:scene/vanilla', pad - minx, pad - miny, scale, night,
                            Xf(x, y, z), tuple(((col >> s) & 255) / 255 for s in (16, 8, 0)))
            continue
        if block == 'spire_coil':
            mp.render_model(img, zbuf, 'robotica:block/spire_coil', pad - minx, pad - miny, scale, night, Xf(x, y, z), tint)
            mp.render_model(img, zbuf, 'robotica:block/spire_coil_glow', pad - minx, pad - miny, scale, night, Xf(x, y, z),
                            ARC_TINT)
            continue
        else:
            refs = models_for(block, props)
        for ref, xr, yr in refs:
            mp.render_model(img, zbuf, ref, pad - minx, pad - miny, scale, night, Xf(x, y, z, xr, yr), tint)
    return img


def render_png(mb_id, formed=True, scale=3.0):
    """PNG bytes of the scene (the wiki's formed view)."""
    import struct
    import zlib
    img = render(mb_id, formed, scale)
    raw = bytearray()
    for row in img:
        raw.append(0)
        for px in row:
            raw.extend(px)

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', len(img[0]), len(img), 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


def main():
    args = sys.argv[1:]
    mp.VANILLA_JAR = mp.find_vanilla_jar()
    formed = '--plain' not in args
    args = [a for a in args if a != '--plain']
    scale = 3.0
    if '--scale' in args:
        i = args.index('--scale')
        scale = float(args[i + 1])
        del args[i:i + 2]
    pathlib.Path(args[0]).write_bytes(render_png(args[1], formed, scale))
    print(args[1], '->', args[0])


if __name__ == '__main__':
    main()
