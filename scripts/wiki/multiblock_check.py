"""Checks the example structures of multiblocks.json against Robotica's real structure rules.

Mirrors the Java code (src/main/java/com/arno/robotica/...):
  core/multiblock/CuboidScanner.java   scan / walk / check / checkFrame / checkWall / faces
  core/multiblock/CuboidSpec.java      frame / wall / controller predicates and size limits
  core/multiblock/CuboidVisitor.java   order: shell, interior, finish
  energy/block/StructureControllerBlockEntity.java  Visitor.wall (collects ports)
  energy/block/BankControllerBlockEntity.java       SPEC + BankVisitor
  energy/block/ReactorControllerBlockEntity.java    SPEC + ReactorVisitor
  energy/block/FusionControllerBlockEntity.java     SPEC + FusionVisitor
  energy/EnergyConfig.java                          size defaults (reactor 5..7, bank 3..9)
  replicator/logic/ReplicatorStructure.java         validate (fixed 3x3x3)

Grid convention (multiblocks.json): layers[y][z][x]; y=0 bottom, z=0 north, x=0 west.
Pure stdlib. `check(mb)` returns a list of problems (empty = valid); `parts(mb)` counts blocks per id.
"""

import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
COOLANT_JSON = os.path.join(ROOT, "src/main/resources/data/robotica/data_maps/block/reactor_coolant.json")

AIR = {"air", "minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

# Directions as (dx, dy, dz); north = -z, east = +x (Minecraft).
NORTH, SOUTH, EAST, WEST, UP, DOWN = (0, 0, -1), (0, 0, 1), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0)
# Direction.getClockWise() seen from above.
CLOCKWISE = {NORTH: EAST, EAST: SOUTH, SOUTH: WEST, WEST: NORTH}


def _neg(d):
    return (-d[0], -d[1], -d[2])


def _add(p, d, n=1):
    return (p[0] + d[0] * n, p[1] + d[1] * n, p[2] + d[2] * n)


def _bare(block_id):
    return block_id[len("robotica:"):] if block_id.startswith("robotica:") else block_id


def _coolant_ids():
    """Blocks with a robotica:reactor_coolant data map entry (EnergyDataMaps.isCoolant)."""
    try:
        with open(COOLANT_JSON, encoding="utf-8") as f:
            return {_bare(k) for k in json.load(f)["values"]}
    except (OSError, ValueError, KeyError):
        return {"minecraft:water", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "cryo_coolant"}


COOLANT = _coolant_ids()
CAPACITORS = {"capacitor_copper", "capacitor_redstone", "capacitor_ender", "capacitor_resonant"}
COILS = {"transfer_coil_basic", "transfer_coil_advanced", "transfer_coil_elite"}

# PortBlock.Kind of each port block (energy/block/PortBlock.java, registered in EnergyRegistry).
PORT_KIND = {"reactor_power_port": "REACTOR_POWER", "reactor_access_port": "REACTOR_ACCESS", "bank_port": "BANK"}

# CuboidSpec of each energy structure: frame / wall / controller predicates and size limits (min, max).
SPECS = {
    # BankControllerBlockEntity.SPEC; sizes EnergyConfig.bankMinSize/bankMaxSize defaults 3..9 (every axis).
    "bank": {
        "name": "Capacitor Bank",
        "frame": {"bank_casing"},
        "wall": {"bank_casing", "bank_glass", "bank_port"},
        "controller": "bank_controller",
        "width": (3, 9), "height": (3, 9),
    },
    # ReactorControllerBlockEntity.SPEC; sizes EnergyConfig.reactorMinSize/reactorMaxSize defaults 5..7.
    "fission": {
        "name": "Fission Reactor",
        "frame": {"reactor_casing"},
        "wall": {"reactor_casing", "reactor_glass", "reactor_power_port", "reactor_access_port"},
        "controller": "reactor_controller",
        "width": (5, 7), "height": (5, 7),
    },
    # FusionControllerBlockEntity.SPEC: fixed SIZE=7 wide/deep, HEIGHT=3; walls take Reactor Glass and Reactor Ports.
    "fusion": {
        "name": "Fusion Reactor",
        "frame": {"fusion_casing"},
        "wall": {"fusion_casing", "reactor_glass", "reactor_power_port", "reactor_access_port"},
        "controller": "fusion_controller",
        "width": (7, 7), "height": (3, 3),
    },
}


class Grid:
    """The example as a block lookup; everything outside the grid is air."""

    def __init__(self, mb):
        self.problems = []
        legend = dict(mb.get("legend", {}))
        legend.setdefault(".", "air")
        layers = mb.get("layers") or []
        self.h = len(layers)
        self.d = len(layers[0]) if layers else 0
        self.w = len(layers[0][0]) if self.d else 0
        self.blocks = {}
        for y, layer in enumerate(layers):
            if len(layer) != self.d:
                self.problems.append(f"layer y={y} has {len(layer)} rows, expected {self.d}")
            for z, row in enumerate(layer):
                if len(row) != self.w:
                    self.problems.append(f"row y={y} z={z} has {len(row)} columns, expected {self.w}")
                for x, ch in enumerate(row):
                    if ch not in legend:
                        self.problems.append(f"unknown legend char {ch!r} at {x},{y},{z}")
                        continue
                    self.blocks[(x, y, z)] = legend[ch]

    def get(self, p):
        return self.blocks.get(p, "air")

    def find(self, block_id):
        return [p for p, b in self.blocks.items() if b == block_id]


def is_air(block_id):
    return block_id in AIR


def faces(box, p):
    """CuboidScanner.faces: how many box faces a position lies on (0 inside, 1 wall, 2 edge, 3 corner)."""
    (x0, y0, z0), (x1, y1, z1) = box
    return sum((p[0] in (x0, x1), p[1] in (y0, y1), p[2] in (z0, z1)))


def wall_outward(box, p):
    """CuboidScanner.outward: outward direction of a wall position."""
    (x0, y0, z0), (x1, y1, z1) = box
    if p[0] == x0: return WEST
    if p[0] == x1: return EAST
    if p[1] == y0: return DOWN
    if p[1] == y1: return UP
    if p[2] == z0: return NORTH
    return SOUTH


def _walk(grid, spec, start, direction, inside, limit):
    """CuboidScanner.walk: (run, stop, gap problem or None). Stops at the frame edge (shell right behind, inside)."""
    def shell(p):  # CuboidSpec.isShell
        b = grid.get(p)
        return b in spec["frame"] or b in spec["wall"] or b == spec["controller"]

    p = start
    run = 0
    while run < limit:
        p = _add(p, direction)
        if not shell(p):
            break
        run += 1
        if shell(_add(p, inside)):
            return run, _add(p, direction), None
    if run >= limit:
        return run, p, None
    if shell(_add(p, direction)):
        what = "a gap" if is_air(grid.get(p)) else grid.get(p)
        return run, p, f"{what} at {p} in the wall line through the controller (multiblock.robotica.gap/stranger)"
    return run, p, None


def _check_cuboid(mb, kind):
    spec = SPECS[kind]
    grid = Grid(mb)
    if grid.problems:
        return grid.problems
    out = []
    # Exactly one controller (CuboidScanner.checkWall: a second one is "second_controller"; none means no scan at all).
    ctrls = grid.find(spec["controller"])
    if len(ctrls) != 1:
        return [f"needs exactly one {spec['controller']}, found {len(ctrls)}"]
    c = ctrls[0]
    full = ((0, 0, 0), (grid.w - 1, grid.h - 1, grid.d - 1))
    # The controller sits in a SIDE wall with a horizontal front (ControllerBlock.FACING = HORIZONTAL_FACING;
    # CuboidScanner.scanFacing forces a horizontal outward).
    if c[0] == 0: outward = WEST
    elif c[0] == grid.w - 1: outward = EAST
    elif c[2] == 0: outward = NORTH
    elif c[2] == grid.d - 1: outward = SOUTH
    else:
        return [f"controller at {c} is not in a side wall (top, bottom or inside)"]
    inward = _neg(outward)
    right = CLOCKWISE[outward]
    left = _neg(right)
    (min_w, max_w), (min_h, max_h) = spec["width"], spec["height"]

    # CuboidScanner.scanFacing: walk right/left/up/down over shell blocks from the controller.
    r = _walk(grid, spec, c, right, inward, max_w)
    l = _walk(grid, spec, c, left, inward, max_w)
    u = _walk(grid, spec, c, UP, inward, max_h)
    d = _walk(grid, spec, c, DOWN, inward, max_h)
    for w in (r, l, u, d):
        if w[2]:
            return [w[2]]
    if r[0] == 0 and l[0] == 0 and u[0] == 0 and d[0] == 0:
        return ["controller stands alone (multiblock.robotica.alone)"]
    # Controller on an edge: one of the walks has no run (multiblock.robotica.controller_edge).
    if 0 in (r[0], l[0], u[0], d[0]):
        return [f"controller at {c} is on an edge of the box, it must be inside a side wall (controller_edge)"]
    width = r[0] + l[0] + 1
    height = u[0] + d[0] + 1
    if width > max_w: return [f"too wide: max {max_w}"]
    if height > max_h: return [f"too tall: max {max_h}"]
    if width < min_w: return [f"too narrow: {width} < {min_w}"]
    if height < min_h: return [f"too low: {height} < {min_h}"]
    # Depth: from the right front edge back along the right side wall (same limits as width).
    edge = _add(c, right, r[0])
    back = _walk(grid, spec, edge, inward, left, max_w)
    if back[2]:
        return [back[2]]
    depth = back[0] + 1
    if depth > max_w: return [f"too deep: max {max_w}"]
    if depth < min_w:
        return [f"too shallow: {depth} < {min_w}" if back[0] else "no side wall behind the front edge (no_side_wall)"]
    a = _add(_add(c, left, l[0]), DOWN, d[0])
    b = _add(_add(_add(c, right, r[0]), UP, u[0]), inward, depth - 1)
    box = (tuple(min(a[i], b[i]) for i in range(3)), tuple(max(a[i], b[i]) for i in range(3)))
    if box != full:
        return [f"the scan finds box {box}, but the example grid is {full}: shell blocks outside or a short wall"]

    # CuboidScanner.check: shell first (y, z, x order), then interior, then the visitor's finish.
    (x0, y0, z0), (x1, y1, z1) = box
    ports = []  # StructureControllerBlockEntity.Visitor.wall collects PortBlocks
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                p = (x, y, z)
                n = faces(box, p)
                if n == 0:
                    continue
                blk = grid.get(p)
                if n >= 2:  # CuboidScanner.checkFrame: edges and corners take only the frame block
                    if blk in spec["frame"]:
                        continue
                    if is_air(blk):
                        return [f"frame missing at {p} (frame_missing)"]
                    return [f"{blk} at {p} is on the frame, only {sorted(spec['frame'])} there (frame_wrong/frame_wall_block)"]
                if p == c:
                    continue
                # CuboidScanner.checkWall
                if blk == spec["controller"]:
                    return [f"second controller at {p} (second_controller)"]
                if blk not in spec["wall"]:
                    return [f"wall {'missing' if is_air(blk) else 'wrong: ' + blk} at {p}, allowed {sorted(spec['wall'])}"]
                if blk in PORT_KIND:
                    ports.append((p, wall_outward(box, p), PORT_KIND[blk]))

    interior = [(x, y, z) for y in range(y0 + 1, y1) for z in range(z0 + 1, z1) for x in range(x0 + 1, x1)]
    port_kinds = [k for _, _, k in ports]

    if kind == "bank":
        # BankControllerBlockEntity.BankVisitor.interior: Capacitors and Transfer Coils, the rest air.
        caps = coils = 0
        for p in interior:
            blk = grid.get(p)
            if blk in CAPACITORS: caps += 1
            elif blk in COILS: coils += 1
            elif not is_air(blk):
                return [f"{blk} at {p} does not belong inside a bank (bank_inside)"]
        # BankVisitor.finish
        if caps == 0: out.append("no Capacitor inside (bank_no_capacitor)")
        elif coils == 0: out.append("no Transfer Coil inside (bank_no_coil)")
        elif "BANK" not in port_kinds: out.append("no Bank Port in the walls (bank_no_port)")
    elif kind == "fission":
        # ReactorControllerBlockEntity.ReactorVisitor.interior: fuel rods, air, or coolant (data map).
        rods = set()
        for p in interior:
            blk = grid.get(p)
            if blk == "reactor_fuel_rod": rods.add(p)
            elif is_air(blk) or blk in COOLANT: pass
            else:
                return [f"{blk} at {p} does not belong inside a reactor (reactor_inside)"]
        # ReactorVisitor.finish: at least one rod, each rod column full from floor to roof, a Power Port.
        if not rods:
            out.append("no Fuel Rods inside (reactor_no_rods)")
        else:
            for rod in sorted(rods):
                missing = [(rod[0], y, rod[2]) for y in range(y0 + 1, y1) if (rod[0], y, rod[2]) not in rods]
                if missing:
                    out.append(f"Fuel Rod column at x={rod[0]} z={rod[2]} is not full, missing {missing[0]} (reactor_rod_column)")
                    break
            if not out and "REACTOR_POWER" not in port_kinds:
                out.append("no Reactor Power Port (reactor_no_power_port)")
    elif kind == "fusion":
        # FusionControllerBlockEntity.FusionVisitor.interior: outer ring of the inside layer = Fusion Coils, 3x3 middle air.
        for p in interior:
            blk = grid.get(p)
            ring = p[0] in (x0 + 1, x1 - 1) or p[2] in (z0 + 1, z1 - 1)
            if ring and blk != "fusion_coil":
                return [f"{blk} at {p}: the ring must be Fusion Coils (fusion_ring)"]
            if not ring and not is_air(blk):
                return [f"{blk} at {p}: the plasma chamber must be empty (fusion_chamber)"]
        # FusionVisitor.finish
        if "REACTOR_POWER" not in port_kinds:
            out.append("no Reactor Power Port (reactor_no_power_port)")
    return out


def _check_replicator(mb):
    """replicator/logic/ReplicatorStructure.validate: 3x3x3, controller in a face centre (FACING is any of the six
    directions), the other 26-1 shell blocks frames or glass (at least one glass), the centre block air."""
    grid = Grid(mb)
    if grid.problems:
        return grid.problems
    if (grid.w, grid.h, grid.d) != (3, 3, 3):
        return [f"the Mob Replicator is exactly 3x3x3, got {grid.w}x{grid.h}x{grid.d}"]
    ctrls = grid.find("replicator_controller")
    if len(ctrls) != 1:
        return [f"needs exactly one replicator_controller, found {len(ctrls)}"]
    box = ((0, 0, 0), (2, 2, 2))
    c = ctrls[0]
    if faces(box, c) != 1:
        return [f"controller at {c} must be the centre of a face (center = controller.relative(facing.getOpposite()))"]
    glass = 0
    for p, blk in sorted(grid.blocks.items()):
        if p == (1, 1, 1) or p == c:
            continue
        if blk == "replicator_glass": glass += 1
        elif blk != "replicator_frame":
            return [f"{blk} at {p}: shell must be Replicator Frame or Glass (INCOMPLETE)"]
    if not is_air(grid.get((1, 1, 1))):
        return ["the centre block must be air (CENTER_BLOCKED)"]
    if glass == 0:
        return ["needs at least one Replicator Glass (NO_GLASS)"]
    return []


def check(mb: dict) -> list:
    kind = mb.get("check")
    if kind in SPECS:
        return _check_cuboid(mb, kind)
    if kind == "replicator":
        return _check_replicator(mb)
    return [f"unknown check kind {kind!r}"]


def parts(mb) -> dict:
    """Blocks per id (air excluded), most first."""
    counts = {}
    for blk in Grid(mb).blocks.values():
        if not is_air(blk):
            counts[blk] = counts.get(blk, 0) + 1
    return dict(sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])))


# ---------------------------------------------------------------- self test

def _set(mb, x, y, z, ch):
    mb = copy.deepcopy(mb)
    row = mb["layers"][y][z]
    mb["layers"][y][z] = row[:x] + ch + row[x + 1:]
    return mb


def _negatives(data):
    """Broken copies of the examples; each must fail."""
    cases = []
    f5 = data.get("fission_reactor_5")
    if f5:
        cases.append(("fission_reactor_5 without a corner", _set(f5, 0, 0, 0, ".")))
        cases.append(("fission_reactor_5 with a broken rod column", _set(f5, 2, 2, 2, "W")))
        cases.append(("fission_reactor_5 with glass on an edge", _set(f5, 0, 2, 0, "G")))
        w = len(f5["layers"][0][0])
        cases.append(("fission_reactor_5 with dirt inside", _set({**f5, "legend": {**f5["legend"], "D": "minecraft:dirt"}}, 1, 1, 1, "D")))
        no_power = copy.deepcopy(f5)
        no_power["layers"] = [[row.replace("P", "C") for row in layer] for layer in f5["layers"]]
        cases.append(("fission_reactor_5 without a power port", no_power))
        del w
    b3 = data.get("capacitor_bank_small")
    if b3:
        no_coil = copy.deepcopy(b3)
        no_coil["layers"] = [[row.replace("T", ".") for row in layer] for layer in b3["layers"]]
        cases.append(("capacitor_bank_small without a coil", no_coil))
    fu = data.get("fusion_reactor")
    if fu:
        cases.append(("fusion_reactor with a coil missing", _set(fu, 1, 1, 1, ".")))
        cases.append(("fusion_reactor with a blocked chamber", _set(fu, 3, 1, 3, "F")))
    rep = data.get("mob_replicator")
    if rep:
        no_glass = copy.deepcopy(rep)
        no_glass["layers"] = [[row.replace("G", "F") for row in layer] for layer in rep["layers"]]
        cases.append(("mob_replicator without glass", no_glass))
        cases.append(("mob_replicator with a blocked centre", _set(rep, 1, 1, 1, "F")))
    return cases


def main():
    path = os.path.join(HERE, "multiblocks.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    bad = 0
    for key, mb in data.items():
        problems = check(mb)
        if problems:
            bad += 1
            print(f"FAIL {key}: " + "; ".join(problems))
        else:
            print(f"OK   {key}  {parts(mb)}")
    for name, mb in _negatives(data):
        problems = check(mb)
        if problems:
            print(f"OK   negative: {name} -> {problems[0]}")
        else:
            bad += 1
            print(f"FAIL negative: {name} passed but must fail")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
