"""Writes the data maps of the energy module that are not fuel (fuel maps: scripts/data/industry_data.py) and the
Tesla Spire conductor tags for other mods' metals.
Run: python3 scripts/data/energy_data.py

- data_maps/block/spire_conductor.json  {"power": FE/t, "efficiency": x}       Tesla Spire column blocks
- data_maps/block/core_modulator.json   {"power": fraction, "burn": fraction}  Core Reactor inside blocks
- data_maps/item/reactor_core.json      {"power": x, "life": ticks}            Core Reactor cores
- tags/block/spire_conductors/<metal>   optional #c:storage_blocks/<metal>     (fragment, merged at build time)
Numbers: docs/DESIGN.md "Big energy".
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = ROOT / 'src/main/resources/data/robotica'
FRAG = ROOT / 'src/main/fragments/energy/data/robotica/tags/block'


def write(path, obj):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


COPPER = ['copper_block', 'exposed_copper', 'weathered_copper', 'oxidized_copper',
          'waxed_copper_block', 'waxed_exposed_copper', 'waxed_weathered_copper', 'waxed_oxidized_copper']

# power FE/t per block, efficiency (FE out of a fuel item = its energy x the column's efficiency)
CONDUCTORS = {
    '#c:storage_blocks/iron': (45, 1.25),
    '#c:storage_blocks/gold': (90, 0.75),
    '#c:storage_blocks/emerald': (60, 1.75),
    '#c:storage_blocks/diamond': (120, 2.0),
    'robotica:pyrolite_block': (140, 0.8),
    'robotica:resonite_block': (200, 3.0),
    '#c:storage_blocks/netherite': (500, 3.0),
}
# other mods' metal blocks, only when present
MODDED = {
    'tin': (35, 1.1), 'aluminum': (35, 1.0), 'lead': (40, 1.5), 'nickel': (45, 1.3), 'bronze': (45, 1.2),
    'constantan': (55, 1.3), 'invar': (55, 1.4), 'steel': (70, 1.5), 'osmium': (75, 1.6), 'silver': (80, 1.0),
    'electrum': (100, 1.0), 'signalum': (160, 1.5), 'lumium': (180, 1.6), 'enderium': (300, 2.5),
}

# power and burn as fractions; amplifiers trade fuel and core life for power, stabilizers the other way
MODULATORS = {
    'minecraft:redstone_block': (0.03, 0.06),
    'robotica:flux_amplifier': (0.08, 0.12),
    'robotica:pyro_amplifier': (0.15, 0.18),
    'robotica:resonant_amplifier': (0.25, 0.22),
    'minecraft:packed_ice': (-0.02, -0.04),
    'robotica:graphite_damper': (-0.03, -0.08),
    'minecraft:blue_ice': (-0.03, -0.07),
    'robotica:cryo_coolant': (-0.04, -0.12),
}

CORE_LIFE = 12_096_000  # 7 days of running at burn x1
CORES = {'robotica:servo_core': 2.0, 'robotica:magma_core': 6.0, 'robotica:antigrav_core': 16.0}


def main():
    conductors = {f'minecraft:{b}': {'power': 30, 'efficiency': 1.0} for b in COPPER}
    for key, (power, eff) in CONDUCTORS.items():
        conductors[key] = {'power': power, 'efficiency': eff}
    for metal, (power, eff) in MODDED.items():
        write(FRAG / 'spire_conductors' / f'{metal}.json',
              {'replace': False, 'values': [{'id': f'#c:storage_blocks/{metal}', 'required': False}]})
        conductors[f'#robotica:spire_conductors/{metal}'] = {'power': power, 'efficiency': eff}
    write(DATA / 'data_maps/block/spire_conductor.json', {'values': conductors})
    write(DATA / 'data_maps/block/core_modulator.json',
          {'values': {k: {'power': p, 'burn': b} for k, (p, b) in MODULATORS.items()}})
    write(DATA / 'data_maps/item/reactor_core.json',
          {'values': {k: {'power': p, 'life': CORE_LIFE} for k, p in CORES.items()}})
    old = DATA / 'data_maps/block/reactor_coolant.json'
    if old.exists():
        old.unlink()
    print(f'{len(conductors)} conductors, {len(MODULATORS)} modulators, {len(CORES)} cores written')


if __name__ == '__main__':
    main()
