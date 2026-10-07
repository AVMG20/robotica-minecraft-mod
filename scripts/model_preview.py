"""Renders Robotica block models (isometric, north front on the left, east on the right) and item textures into one PNG,
in day light and at night (only full-bright faces lit), so model and texture work can be judged without a client.

Usage: python3 scripts/model_preview.py out.png block/combustion_generator_on block/stumpy_mk2 item/null_drill ...
       python3 scripts/model_preview.py out.png --blocks charger,excavator  (shorthand for block/<name>)
Vanilla textures (an ore's host rock) come from the Minecraft client jar when one is found (or MC_CLIENT_JAR);
--standins draws the wiki's stand-ins instead.
A model path is relative to assets/robotica/models; an 'item/<name>' without elements in its model chain is drawn flat.
"""
import json
import math
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources/assets'

VANILLA = {
    'minecraft:block/block': {},
    'minecraft:block/cube': {'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
        'down': {'texture': '#down'}, 'up': {'texture': '#up'}, 'north': {'texture': '#north'},
        'south': {'texture': '#south'}, 'west': {'texture': '#west'}, 'east': {'texture': '#east'}}}]},
    'minecraft:block/cube_all': {'parent': 'minecraft:block/cube', 'textures': {
        'down': '#all', 'up': '#all', 'north': '#all', 'south': '#all', 'west': '#all', 'east': '#all', 'particle': '#all'}},
    'minecraft:block/cube_column': {'parent': 'minecraft:block/cube', 'textures': {
        'down': '#end', 'up': '#end', 'north': '#side', 'south': '#side', 'west': '#side', 'east': '#side'}},
    'minecraft:block/cube_bottom_top': {'parent': 'minecraft:block/cube', 'textures': {
        'down': '#bottom', 'up': '#top', 'north': '#side', 'south': '#side', 'west': '#side', 'east': '#side'}},
    'minecraft:block/orientable_with_bottom': {'parent': 'minecraft:block/cube', 'textures': {
        'down': '#bottom', 'up': '#top', 'north': '#front', 'south': '#side', 'west': '#side', 'east': '#side'}},
    'minecraft:block/orientable': {'parent': 'minecraft:block/orientable_with_bottom', 'textures': {'bottom': '#top'}},
    'minecraft:item/generated': {},
    'minecraft:item/handheld': {'parent': 'minecraft:item/generated'},
}


def read_png(path):
    """Any PNG of 8 bits or less per sample (grey, RGB, palette, grey+alpha, RGBA) -> (w, h, rows of RGBA)."""
    data = path.read_bytes() if hasattr(path, 'read_bytes') else path
    pos, idat, w, h, bits, ct, plte, trns = 8, b'', 0, 0, 8, 6, b'', b''
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if tag == b'IHDR':
            w, h, bits, ct = struct.unpack('>IIBB', body[:10])
        elif tag == b'PLTE':
            plte = body
        elif tag == b'tRNS':
            trns = body
        elif tag == b'IDAT':
            idat += body
        pos += 12 + ln
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    bpp = max(1, channels * bits // 8)
    stride = (w * channels * bits + 7) // 8
    px, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        if bits == 8:
            samples = line
        else:
            per = 8 // bits
            samples = [(line[k // per] >> (8 - bits * (k % per + 1))) & ((1 << bits) - 1) for k in range(w * channels)]
        row = []
        for x in range(w):
            s = tuple(samples[x * channels:(x + 1) * channels])
            if ct == 3:
                row.append(tuple(plte[s[0] * 3:s[0] * 3 + 3]) + (trns[s[0]] if s[0] < len(trns) else 255,))
            elif ct in (0, 4):
                v = s[0] * 255 // ((1 << bits) - 1)
                row.append((v, v, v, s[1] if ct == 4 else 255))
            else:
                row.append(s + ((255,) if ct == 2 else ()))
        px.append(row)
        prev = line
    return w, h, px


# Vanilla textures our models point at (the host rock under a two-layer ore). Mojang textures are not in the repo: the
# preview reads them from the Minecraft client jar when it finds one (find_vanilla_jar); without it (and always for
# the wiki) a stand-in is drawn: seamless noise in the texture's colours.
VANILLA_STANDINS = {
    'block/stone': ('#686868', '#747474', '#7F7F7F', '#7F7F7F', '#8F8F8F'),
    'block/deepslate': ('#2F2F37', '#3D3D43', '#515151', '#646464', '#797979'),
    'block/netherrack': ('#501B1B', '#572121', '#652828', '#723232', '#854242'),
    'block/end_stone': ('#CDC68B', '#D5DA94', '#DEE6A4', '#EEF6B4', '#EEF6B4'),
    'block/oak_planks': ('#6B5430', '#7E6238', '#9C7F4E', '#AF8F55', '#B8945F'),
}
VANILLA_JAR = None
MISSING = [[(255, 0, 255, 255)] * 16 for _ in range(16)]


def find_vanilla_jar():
    """The NeoForge client resources jar of this checkout (or of the main checkout for a worktree), or None."""
    import glob
    import os
    env = os.environ.get('MC_CLIENT_JAR')
    if env and pathlib.Path(env).exists():
        return env
    for r in [ROOT] + list(ROOT.parents):
        hits = sorted(glob.glob(str(r / 'build/moddev/artifacts/*client-extra*.jar')))
        if hits:
            return hits[-1]
    hits = sorted(glob.glob(os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar')))
    return hits[-1] if hits else None


def _hash(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535


def standin(path):
    """16x16 stand-in for a vanilla texture: blocky noise in its colour ramp (planks get board seams)."""
    ramp, seed, rows = VANILLA_STANDINS[path], sum(map(ord, path)), []
    for y in range(16):
        row = []
        for x in range(16):
            if path == 'block/oak_planks':
                n = 0.3 + 0.4 * _hash(x // 6 + y // 4 * 7, 0, seed) + 0.3 * _hash(x, y // 4, seed) - (0.6 if y % 4 == 3 else 0)
            else:
                n = 0.55 * _hash(x // 2, y // 2, seed) + 0.45 * _hash(x, y, seed + 1)
            c = ramp[max(0, min(4, int(n * 5)))]
            row.append((int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16), 255))
        rows.append(row)
    return rows


_tex_cache = {}


def texture(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    key = (ns, path)
    if key not in _tex_cache:
        p = RES / ns / 'textures' / f'{path}.png'
        px = None
        if p.exists():
            w, h, px = read_png(p)
        elif ns == 'minecraft' and VANILLA_JAR:
            import zipfile
            try:
                w, h, px = read_png(zipfile.ZipFile(VANILLA_JAR).read(f'assets/minecraft/textures/{path}.png'))
            except KeyError:
                px = None
        if px is None:
            w, h, px = 16, 16, standin(path) if ns == 'minecraft' and path in VANILLA_STANDINS else MISSING
        _tex_cache[key] = (w, min(h, w), px[:w])     # first animation frame
    return _tex_cache[key]


def load_model(ref):
    if ref in VANILLA:
        return dict(VANILLA[ref])
    ns, path = ref.split(':', 1) if ':' in ref else ('robotica', ref)
    return json.loads((RES / ns / 'models' / f'{path}.json').read_text())


def resolve(ref):
    """Returns (textures, elements) after walking the parent chain."""
    chain = []
    while ref:
        m = load_model(ref)
        chain.append(m)
        ref = m.get('parent')
        if ref and ':' not in ref:
            ref = 'minecraft:' + ref
    textures, elements = {}, None
    for m in reversed(chain):
        textures.update(m.get('textures', {}))
        if 'elements' in m:
            elements = m['elements']
    return textures, elements


def tex_ref(textures, var):
    seen = 0
    while var.startswith('#') and seen < 10:
        var = textures.get(var[1:], '?')
        seen += 1
    return var


def default_uv(d, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {'down': [x1, 16 - z2, x2, 16 - z1], 'up': [x1, z1, x2, z2], 'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1],
            'south': [x1, 16 - y2, x2, 16 - y1], 'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1]}[d]


def face_frame(d, a, b):
    """Origin, U (texture u direction) and V (texture v direction) of a face in model space."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    return {
        'north': ((x2, y2, z1), (-dx, 0, 0), (0, -dy, 0)),
        'south': ((x1, y2, z2), (dx, 0, 0), (0, -dy, 0)),
        'east': ((x2, y2, z2), (0, 0, -dz), (0, -dy, 0)),
        'west': ((x1, y2, z1), (0, 0, dz), (0, -dy, 0)),
        'up': ((x1, y2, z1), (dx, 0, 0), (0, 0, dz)),
        'down': ((x1, y1, z2), (dx, 0, 0), (0, 0, -dz)),
    }[d]


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
VIEW = (0.6, 0.75, -0.6)   # towards the camera: above, east, north


def rotate(p, rot):
    if not rot:
        return p
    o = rot.get('origin', [8, 8, 8])
    ang = math.radians(rot['angle'])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(ang), math.sin(ang)
    if rot['axis'] == 'y':
        x, z = x * c + z * s, -x * s + z * c
    elif rot['axis'] == 'x':
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def project(p, scale, ox, oy):
    x, y, z = p
    sx = (x + z) * 0.866
    sy = (x - z) * 0.5 - y
    return ox + sx * scale, oy + sy * scale


def depth(p):
    return p[0] * VIEW[0] + p[1] * VIEW[1] + p[2] * VIEW[2]


def render_model(img, zbuf, ref, ox, oy, scale, night=False):
    textures, elements = resolve(ref)
    for el in elements or []:
        a, b = el['from'], el['to']
        rot = el.get('rotation')
        for d, f in el['faces'].items():
            n = rotate(NORMALS[d], dict(rot, origin=[0, 0, 0]) if rot else None)
            dot = sum(n[i] * VIEW[i] for i in range(3))
            if dot <= 0.01:
                continue
            o, U, V = face_frame(d, a, b)
            uv = f.get('uv') or default_uv(d, a, b)
            tw, th, tpx = texture(tex_ref(textures, f['texture']))
            p0 = rotate(o, rot)
            pu = rotate((o[0] + U[0], o[1] + U[1], o[2] + U[2]), rot)
            pv = rotate((o[0] + V[0], o[1] + V[1], o[2] + V[2]), rot)
            s0, su, sv = project(p0, scale, ox, oy), project(pu, scale, ox, oy), project(pv, scale, ox, oy)
            ax, ay = su[0] - s0[0], su[1] - s0[1]
            bx, by = sv[0] - s0[0], sv[1] - s0[1]
            det = ax * by - ay * bx
            if abs(det) < 1e-6:
                continue
            xs = [s0[0], su[0], sv[0], su[0] + bx]
            ys = [s0[1], su[1], sv[1], su[1] + by]
            glow = f.get('neoforge_data', {}).get('block_light', 0)
            shade = 1.0 if el.get('shade') is False else (0.5 + 0.5 * max(0.0, n[1]) + 0.3 * abs(n[2]) + 0.1 * abs(n[0])) \
                if n[1] >= 0 else 0.5
            shade = min(1.0, shade)
            if night:
                shade = shade * 0.16 if not glow else glow / 15
            rotation = f.get('rotation', 0)
            d0, du, dv = depth(p0), depth(pu) - depth(p0), depth(pv) - depth(p0)
            for py in range(int(min(ys)), int(max(ys)) + 2):
                for px in range(int(min(xs)), int(max(xs)) + 2):
                    if not (0 <= px < len(img[0]) and 0 <= py < len(img)):
                        continue
                    qx, qy = px + 0.5 - s0[0], py + 0.5 - s0[1]
                    s = (qx * by - qy * bx) / det
                    t = (ax * qy - ay * qx) / det
                    if not (0 <= s < 1 and 0 <= t < 1):
                        continue
                    ss, tt = s, t
                    if rotation == 90:
                        ss, tt = t, 1 - s
                    elif rotation == 180:
                        ss, tt = 1 - s, 1 - t
                    elif rotation == 270:
                        ss, tt = 1 - t, s
                    u = uv[0] + (uv[2] - uv[0]) * ss
                    v = uv[1] + (uv[3] - uv[1]) * tt
                    tx = min(tw - 1, max(0, int(u * tw / 16)))
                    ty = min(th - 1, max(0, int(v * th / 16)))
                    r, g, bb, al = tpx[ty][tx]
                    if al < 128:
                        continue
                    z = d0 + du * s + dv * t + (0.001 if glow else 0)
                    if z < zbuf[py][px] - 1e-4:
                        continue
                    zbuf[py][px] = z
                    img[py][px] = (int(r * shade), int(g * shade), int(bb * shade), 255)


def render_flat(img, ref, ox, oy, scale):
    textures, _ = resolve(ref)
    layer = textures.get('layer0')
    if not layer:
        return
    tw, th, tpx = texture(layer)
    for y in range(th):
        for x in range(tw):
            r, g, b, a = tpx[y][x]
            if a < 128:
                continue
            for yy in range(scale):
                for xx in range(scale):
                    X, Y = int(ox + x * scale + xx), int(oy + y * scale + yy)
                    if 0 <= X < len(img[0]) and 0 <= Y < len(img):
                        img[Y][X] = (r, g, b, 255)


def render_tex(img, ref, ox, oy, scale, dim=False):
    """Flat texture (first frame); `dim` darkens it like an unlit face at night."""
    tw, th, tpx = texture(ref)
    scale = min(scale, 192 // tw)
    for y in range(th):
        for x in range(tw):
            r, g, b, a = tpx[y][x]
            if a < 128:
                continue
            k = 0.16 if dim else 1.0
            for yy in range(scale):
                for xx in range(scale):
                    X, Y = int(ox + x * scale + xx), int(oy + y * scale + yy)
                    if 0 <= X < len(img[0]) and 0 <= Y < len(img):
                        img[Y][X] = (int(r * k), int(g * k), int(b * k), 255)


def write_png(path, img):
    raw = bytearray()
    for row in img:
        raw.append(0)
        for p in row:
            raw.extend(p)

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    H, W = len(img), len(img[0])
    pathlib.Path(path).write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
                                   + chunk(b'IDAT', zlib.compress(bytes(raw))) + chunk(b'IEND', b''))


def main():
    global VANILLA_JAR
    if '--standins' in sys.argv:
        sys.argv.remove('--standins')
    else:
        VANILLA_JAR = find_vanilla_jar()
    out = sys.argv[1]
    refs = []
    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == '--blocks':
            refs += ['block/' + n for n in args[i + 1].split(',')]
            i += 2
        elif args[i] == '--tex':
            refs += ['tex:block/' + n.replace('+', '+block/') for n in args[i + 1].split(',')]
            i += 2
        else:
            refs.append(args[i])
            i += 1
    zoom = float(__import__('os').environ.get('ZOOM', '1'))
    scale, cell, cols = 5.5 * zoom, int(200 * zoom), min(int(6 / zoom) or 1, max(1, len(refs)))
    rows = (len(refs) + cols - 1) // cols
    W, H = cols * cell, rows * cell * 2
    img = [[(118, 160, 92, 255) if (y // cell) % 2 == 0 else (20, 22, 30, 255) for _ in range(W)] for y in range(H)]
    for k, ref in enumerate(refs):
        cx, cy = (k % cols) * cell, (k // cols) * cell * 2
        if ref.startswith('tex:'):
            for night in (False, True):
                oy = cy + (cell if night else 0)
                for layer in ref[4:].split('+'):
                    render_tex(img, 'robotica:' + layer, cx + 4, oy + 4, 12, night and '+' + layer not in ref)
            continue
        textures, elements = resolve('robotica:' + ref)
        for night in (False, True):
            oy = cy + (cell if night else 0)
            if elements:
                zbuf = [[-1e9] * W for _ in range(H)]
                render_model(img, zbuf, 'robotica:' + ref, cx + cell / 2 - 13.86 * scale, oy + cell / 2 + 8 * scale, scale, night)
            else:
                render_flat(img, 'robotica:' + ref, cx + (cell - 16 * 8) // 2, oy + (cell - 16 * 8) // 2, 8)
    write_png(out, img)
    print(len(refs), 'models ->', out)


if __name__ == '__main__':
    main()
