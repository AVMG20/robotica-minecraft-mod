"""Renders every robotica item/block texture into one upscaled PNG for a quick visual check.
Usage: python3 scripts/contact_sheet.py out.png"""
import pathlib, struct, sys, zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX = ROOT / 'src/main/resources/assets/robotica/textures'


def read_png(path):
    data = path.read_bytes()
    pos, idat, w, h, ct = 8, b'', 0, 0, 6
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos + 4])[0]; tag = data[pos + 4:pos + 8]; body = data[pos + 8:pos + 8 + ln]
        if tag == b'IHDR':
            w, h, _, ct = struct.unpack('>IIBB', body[:10])
        elif tag == b'IDAT':
            idat += body
        pos += 12 + ln
    raw = zlib.decompress(idat); bpp = 4 if ct == 6 else 3; stride = w * bpp
    px, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[i]; line = bytearray(raw[i + 1:i + 1 + stride]); i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0; b = prev[x]; c = prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        px.append([tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)]); prev = line
    return w, h, px


files = sorted(TEX.rglob('*.png'))
scale, cols, cell = 6, 12, 16 * 6 + 8
rows_n = (len(files) + cols - 1) // cols
W, H = cols * cell, rows_n * cell
img = [[(48, 52, 56, 255)] * W for _ in range(H)]
for i, f in enumerate(files):
    w, h, px = read_png(f)
    ox, oy = (i % cols) * cell + 4, (i // cols) * cell + 4
    for y in range(min(h, 16)):
        for x in range(min(w, 16)):
            r, g, b, a = px[y][x]
            if a < 128: continue
            for yy in range(scale):
                for xx in range(scale):
                    img[oy + y * scale + yy][ox + x * scale + xx] = (r, g, b, 255)
raw = bytearray()
for row in img:
    raw.append(0)
    for p in row: raw.extend(p)
def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
out = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(raw))) + chunk(b'IEND', b'')
pathlib.Path(sys.argv[1]).write_bytes(out)
print(len(files), 'textures ->', sys.argv[1])
for i, f in enumerate(files): print(i, f.stem, end='  ')
