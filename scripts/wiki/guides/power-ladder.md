---
title: The Power Ladder
icon: tesla_coil_1
order: 20
summary: Every generator from the crank to fusion, where to store FE and how Tesla Coils move it wirelessly.
---
Forge Energy (FE) is the only unit. Every port works with other mods' cables too. Numbers are the config defaults.

## Generators

| Source | Age | Output | Notes |
|---|---|---|---|
| [[winding_crank]] | 0 | 100 FE/t | only while you hold right-click; winds a Mainspring |
| [[combustion_generator]] | 1 | 80 FE/t | furnace fuel, 40,000 FE buffer; speed and efficiency cards |
| [[solar_panel_mk1]] | 1 | 20 FE/t | daytime, sky access |
| [[solar_panel_mk2]] | 2 | 80 FE/t | daytime, sky access |
| [[solar_panel_mk3]] | 3 | 200 FE/t | daytime, sky access |
| [[solar_panel_mk4]] | 4 | 500 FE/t | daytime, sky access |
| [[rtg]] | 2 | 150 FE/t | one Thorium Fuel Pellet lasts 24,000 ticks |
| Fission Reactor | 2-3 | about 1,150 to 45,000 FE/t | 5x5x5 to 7x7x7, see [fission](#/guide/fission-reactor) |
| Fusion Reactor | 4 | 200,000 FE/t | per fusion pellet, see [fusion](#/guide/fusion-reactor) |

## Storage

| Store | Capacity |
|---|---|
| [[mainspring]] | 240,000 FE (crank only) |
| [[copper_cell]] / [[redstone_cell]] / [[ender_cell]] | 800k / 3.2M / 20,736,000 FE |
| [[accumulator_1]] / [[accumulator_2]] / [[accumulator_3]] | 1M / 4M / 16M FE, I/O 1,000 / 4,000 / 16,000 FE/t |
| [[bank_controller]] | multiblock, 8M to 4G per capacitor, see [Capacitor Bank](#/guide/capacitor-bank) |

## Tesla Coils

{{items tesla_coil_1 tesla_coil_2 tesla_coil_3 tesla_coil_4 tesla_coil_5 tesla_linker}}

| Tier | Links | Range | FE/t |
|---|---|---|---|
| [[tesla_coil_1]] | 4 | 8 | 4,000 |
| [[tesla_coil_2]] | 8 | 12 | 16,000 |
| [[tesla_coil_3]] | 12 | 16 | 64,000 |
| [[tesla_coil_4]] | 16 | 24 | 256,000 |
| [[tesla_coil_5]] | 32 | 32 | 1,000,000 |

- A coil on a block that outputs FE (generator, Accumulator, panel) is a source. Any other coil is a relay.
- Each coil-to-coil hop loses 5%.

### Linking

1. Place a coil on the generator or battery.
2. Sneak-right-click the coil with the [[tesla_linker]].
3. Right-click a machine face (power enters there) or another coil.
4. Click the same face again to unlink. Sneak-right-click air clears the selection.

{{image shots/11_tesla_network.jpg|A Tesla Coil network}}

## Charging your gear

- [[charger]]: one item at 2,000 FE/t. Right-click it with the item.
- [[wireless_charger]] (Age 2): everything you carry within 8 blocks, 1,000 FE/t per player. Worn armor first, then your hands, then the inventory. Only you and your team. Range cards +4 blocks, speed cards up to x6. Wireless costs 10% extra FE.
