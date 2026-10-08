---
title: The Power Ladder
icon: tesla_coil_1
order: 20
summary: Every generator from the crank to fusion, where to store FE and how to move it without cables.
---
## Generators

| Source | Age | FE/t |
|---|---|---|
| [[winding_crank]] | 0 | 100, by hand |
| [[combustion_generator]] | 1 | 80 |
| [[solar_panel_mk1]] to [[solar_panel_mk4]] | 1-4 | 20 to 320, by day |
| [[rtg]] | 2 | 150 |
| [Fission Reactor](#/guide/fission-reactor) | 2-3 | about 1,100 to 37,000 |
| [Fusion Reactor](#/guide/fusion-reactor) | 4 | 200,000 |

{{image shots/26_solar_panels.jpg|Solar Panels Mk1 to Mk4}}

## Storage

Cells ([[copper_cell]]) power robots and gear. For a base, use [[accumulator_1]] blocks, then a Capacitor Bank.

{{guide capacitor-bank}}

## Tesla Coils

Put a [[tesla_coil_1]] on a generator or battery and link it to machines with the [[tesla_linker]]: no cables needed. A coil anywhere else relays. Link machines straight to the source coil when you can: every relay hop loses some FE.

{{items tesla_coil_1 tesla_coil_2 tesla_coil_3 tesla_coil_4 tesla_coil_5 tesla_linker}}

{{image shots/11_tesla_network.jpg|A Tesla Coil network}}

{{image shots/18_energy_multiblocks.jpg|Fission Reactor, Capacitor Bank and Fusion Reactor}}

## Charging your gear

A [[charger]] does one item at a time. The [[wireless_charger]] (Age 2) charges everything you and your team carry while you work nearby.

{{image shots/gui_wireless_charger.jpg|Wireless Charger menu}}
