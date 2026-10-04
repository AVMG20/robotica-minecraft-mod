"""Writes the 3x3x3 empty structure used by game tests (data/robotica/structure/empty.nbt)."""
import gzip, struct, pathlib

def tag_str(name):
    b = name.encode(); return struct.pack('>H', len(b)) + b

def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_str(name) + payload

def int_list(vals):
    return bytes([3]) + struct.pack('>i', len(vals)) + b''.join(struct.pack('>i', v) for v in vals)

def empty_list():
    return bytes([0]) + struct.pack('>i', 0)

body = (named(9, 'size', int_list([3, 3, 3])) + named(9, 'blocks', empty_list()) +
        named(9, 'palette', empty_list()) + named(9, 'entities', empty_list()) +
        named(3, 'DataVersion', struct.pack('>i', 3955)) + b'\x00')
root = named(10, '', body)
out = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/data/robotica/structure/empty.nbt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(root))
print('wrote', out)
