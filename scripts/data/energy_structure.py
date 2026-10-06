"""Writes the empty 9x9x9 arena used by the energy game tests (data/robotica/structure/energy_arena.nbt).
Multiblocks up to 7 blocks wide plus a row of machines around them fit inside.
Run: python3 scripts/data/energy_structure.py
"""
import gzip
import pathlib
import struct

SIZE = (9, 9, 9)


def tag_str(name):
    b = name.encode()
    return struct.pack('>H', len(b)) + b


def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_str(name) + payload


def int_list(vals):
    return bytes([3]) + struct.pack('>i', len(vals)) + b''.join(struct.pack('>i', v) for v in vals)


def empty_list():
    return bytes([0]) + struct.pack('>i', 0)


body = (named(9, 'size', int_list(list(SIZE))) + named(9, 'blocks', empty_list()) +
        named(9, 'palette', empty_list()) + named(9, 'entities', empty_list()) +
        named(3, 'DataVersion', struct.pack('>i', 3955)) + b'\x00')
root = named(10, '', body)
out = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/structure/energy_arena.nbt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(root))
print('wrote', out)
