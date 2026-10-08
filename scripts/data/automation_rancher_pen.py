"""Writes the 11x5x11 pen used by the Rancher game tests (data/robotica/structure/rancher_pen.nbt): a stone floor and a
2-high stone wall around a 9x9 inside, exactly the Rancher Mk1's area around the centre, so test animals never leave it.
Run: python3 scripts/data/automation_rancher_pen.py
"""
import gzip
import pathlib
import struct

SIZE = (11, 5, 11)


def tag_str(name):
    b = name.encode()
    return struct.pack('>H', len(b)) + b


def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_str(name) + payload


def int_list(vals):
    return bytes([3]) + struct.pack('>i', len(vals)) + b''.join(struct.pack('>i', v) for v in vals)


def compound_list(items):
    return bytes([10]) + struct.pack('>i', len(items)) + b''.join(items)


def empty_list():
    return bytes([0]) + struct.pack('>i', 0)


def block(x, y, z, state):
    return named(9, 'pos', int_list([x, y, z])) + named(3, 'state', struct.pack('>i', state)) + b'\x00'


blocks = []
sx, sy, sz = SIZE
for x in range(sx):
    for z in range(sz):
        blocks.append(block(x, 0, z, 0))
        if x in (0, sx - 1) or z in (0, sz - 1):
            blocks.append(block(x, 1, z, 0))
            blocks.append(block(x, 2, z, 0))
palette = [named(8, 'Name', tag_str('minecraft:stone')) + b'\x00']

body = (named(9, 'size', int_list(list(SIZE))) + named(9, 'blocks', compound_list(blocks)) +
        named(9, 'palette', compound_list(palette)) + named(9, 'entities', empty_list()) +
        named(3, 'DataVersion', struct.pack('>i', 3955)) + b'\x00')
root = named(10, '', body)
out = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/structure/rancher_pen.nbt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(root))
print('wrote', out)
