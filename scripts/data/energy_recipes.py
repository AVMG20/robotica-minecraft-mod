"""Writes the crafting recipes of the energy module (balance ladder, see docs/DESIGN.md "Big energy").
Run: python3 scripts/data/energy_recipes.py   (overwrites data/robotica/recipe/<name>.json for energy items only)

Ages: Capacitor Bank from first circuits (Age 1, copper cells); Tesla Spire and Core Reactor at Age 2 (ferrothorium,
Advanced Circuit), Pyro Amplifiers, Cryo Coolant and Elite coils at Age 3; Ender Capacitors, Resonant Amplifiers and the
Ring Collider at Age 4. Each capacitor, coil, amplifier and segment tier consumes the one before.
"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/data/robotica/recipe'


def ing(x):
    """'#tag', 'item' or a list of those (any of them)."""
    if isinstance(x, (list, tuple)):
        return [ing(i) for i in x]
    if x.startswith('#'):
        return {'tag': x[1:]}
    return {'item': x if ':' in x else f'robotica:{x}'}


def shaped(name, pattern, key, count=1, category='building'):
    OUT.mkdir(parents=True, exist_ok=True)
    data = {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
            'key': {k: ing(v) for k, v in key.items()},
            'result': {'id': f'robotica:{name}', 'count': count}}
    (OUT / f'{name}.json').write_text(json.dumps(data, indent=2) + '\n')


IRON_PLATE, COPPER_PLATE = '#c:plates/iron', '#c:plates/copper'
COPPER, GOLD = '#c:ingots/copper', '#c:ingots/gold'
REDSTONE, REDSTONE_BLOCK = '#c:dusts/redstone', '#c:storage_blocks/redstone'
GLASS, QUARTZ, OBSIDIAN, PEARL = '#c:glass_blocks', '#c:gems/quartz', '#c:obsidians', '#c:ender_pearls'

# ---- Capacitor Bank (Age 1 start, tiers climb the ladder) ----
shaped('bank_casing', ['PCP', 'CXC', 'PCP'], {'P': IRON_PLATE, 'C': COPPER, 'X': 'iron_casing'}, count=8)
shaped('bank_glass', ['CGC', 'GRG', 'CGC'], {'C': 'bank_casing', 'G': GLASS, 'R': REDSTONE}, count=4)
shaped('bank_controller', ['CBC', 'RXR', 'CBC'], {'C': 'bank_casing', 'B': 'basic_circuit', 'R': REDSTONE_BLOCK, 'X': 'copper_cell'})
shaped('bank_port', ['RKR', 'KCK', 'RBR'], {'R': REDSTONE, 'K': 'copper_coil', 'C': 'bank_casing', 'B': 'basic_circuit'})
shaped('capacitor_copper', ['PCP', 'CXC', 'PCP'], {'P': COPPER_PLATE, 'C': 'copper_cell', 'X': 'bank_casing'})
shaped('capacitor_redstone', ['RCR', 'CXC', 'RAR'],
       {'R': REDSTONE_BLOCK, 'C': 'redstone_cell', 'X': 'capacitor_copper', 'A': 'advanced_circuit'})
shaped('capacitor_resonant', ['RLR', 'GXG', 'RLR'],
       {'R': '#c:plates/resonant_alloy', 'L': 'resonant_lattice', 'G': ['#c:gems/resonite', '#c:dusts/resonite'], 'X': 'capacitor_ender'})
shaped('capacitor_ender', ['ECE', 'SXS', 'ENE'],
       {'E': PEARL, 'C': 'ender_cell', 'S': 'minecraft:shulker_shell', 'X': 'capacitor_redstone', 'N': 'null_circuit'})
shaped('transfer_coil_basic', ['KRK', 'PBP', 'KRK'], {'K': 'copper_coil', 'R': REDSTONE, 'P': IRON_PLATE, 'B': 'basic_circuit'})
shaped('transfer_coil_advanced', ['GAG', 'QTQ', 'GAG'], {'G': GOLD, 'A': 'advanced_circuit', 'Q': QUARTZ, 'T': 'transfer_coil_basic'})
shaped('transfer_coil_elite', ['BQB', 'STS', 'BQB'],
       {'B': '#c:rods/blaze', 'Q': 'quantum_circuit', 'S': 'superconductor_coil', 'T': 'transfer_coil_advanced'})

# ---- Tesla Spire (Age 2) ----
FERRO = '#c:plates/ferrothorium'
shaped('spire_base', ['PHP', 'KAK', 'PXP'],
       {'P': FERRO, 'H': 'minecraft:hopper', 'K': 'copper_coil', 'A': 'advanced_circuit', 'X': 'iron_casing'})
shaped('spire_crown', ['GTG', 'KAK', 'GKG'], {'G': GOLD, 'T': 'tesla_coil_2', 'K': 'copper_coil', 'A': 'advanced_circuit'})

# ---- Core Reactor (Age 2, modulators up to Age 4) ----
shaped('reactor_casing', ['POP', 'OXO', 'POP'], {'P': FERRO, 'O': OBSIDIAN, 'X': 'reinforced_casing'}, count=8)
shaped('reactor_glass', ['CGC', 'GQG', 'CGC'], {'C': 'reactor_casing', 'G': GLASS, 'Q': QUARTZ}, count=4)
shaped('reactor_controller', ['CAC', 'RXR', 'CSC'],
       {'C': 'reactor_casing', 'A': 'advanced_circuit', 'R': REDSTONE_BLOCK, 'X': 'reinforced_casing', 'S': 'servo_actuator'})
shaped('reactor_power_port', ['RKR', 'KCK', 'RAR'], {'R': REDSTONE, 'K': 'copper_coil', 'C': 'reactor_casing', 'A': 'advanced_circuit'})
shaped('reactor_access_port', ['PHP', 'BCB', 'PTP'],
       {'P': IRON_PLATE, 'H': 'minecraft:hopper', 'B': 'basic_circuit', 'C': 'reactor_casing', 'T': '#c:chests'})
shaped('flux_amplifier', ['PRP', 'RAR', 'PRP'], {'P': FERRO, 'R': REDSTONE_BLOCK, 'A': 'advanced_circuit'}, count=2)
shaped('graphite_damper', ['PGP', 'GOG', 'PGP'], {'P': IRON_PLATE, 'G': '#c:dusts/graphite', 'O': OBSIDIAN}, count=4)
shaped('pyro_amplifier', ['SPS', 'FQF', 'SPS'],
       {'S': '#c:plates/pyrosteel', 'P': 'pyrolite_shard', 'F': 'flux_amplifier', 'Q': 'quantum_circuit'}, count=2)
shaped('resonant_amplifier', ['RGR', 'PNP', 'RGR'],
       {'R': '#c:plates/resonant_alloy', 'G': ['#c:gems/resonite', '#c:dusts/resonite'], 'P': 'pyro_amplifier', 'N': 'null_circuit'}, count=2)
# Age 3 coolant: a stronger stabilizer than blue ice
shaped('cryo_coolant', ['BPB', 'PSP', 'BPB'], {'B': 'minecraft:blue_ice', 'P': '#c:gems/prismarine', 'S': 'minecraft:snow_block'}, count=4)

# ---- Ring Collider (Age 4) ----
shaped('collider_controller', ['CNC', 'AXA', 'CRC'],
       {'C': 'reactor_casing', 'N': 'null_circuit', 'A': 'plasma_actuator', 'X': 'null_casing', 'R': 'reactor_controller'})
shaped('accelerator_segment', ['PSP', 'GQG', 'PSP'],
       {'P': '#c:plates/pyrosteel', 'S': 'superconductor_coil', 'G': GLASS, 'Q': 'quantum_circuit'}, count=4)
shaped('resonant_segment', ['RLR', 'AAA', 'RLR'], {'R': '#c:plates/resonant_alloy', 'L': 'resonant_lattice', 'A': 'accelerator_segment'}, count=3)
print('energy recipes written')
