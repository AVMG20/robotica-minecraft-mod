---
title: The Power Ladder
icon: tesla_coil_1
order: 20
summary: Every generator from the crank to the Ring Collider, where to store FE and how to move it without cables.
---
## Generators

| Source | Age | FE/t |
|---|---|---|
| [[winding_crank]] | 0 | 100, by hand |
| [[combustion_generator]] | 1 | 60 |
| [[solar_panel_mk1]] to [[solar_panel_mk4]] | 1-4 | 20 to 800, by day |
| [[rtg]] | 2 | 200 |
| [Tesla Spire](#/guide/tesla-spire) | 2+ | about 500 to 27,000, plus lightning |
| [Core Reactor](#/guide/core-reactor) | 2-4 | 1,200 to 180,000 |
| [Ring Collider](#/guide/ring-collider) | 4 | 24,000 to 640,000 |

{{image shots/26_solar_panels.jpg|Solar Panels Mk1 to Mk4}}

## Storage

Cells ([[copper_cell]]) power robots and gear. For a base, use [[accumulator_1]] blocks, then a Capacitor Bank.

{{guide capacitor-bank}}

## Tesla Relays

Put a [[tesla_emitter_1]] on a generator or battery and link it to a machine with the [[tesla_linker]], or to a [[tesla_coil_1]] to power many. Link the relay to machines and to other relays: no cables needed. Linked relays share one network, whichever side you link from.

{{items tesla_coil_1 tesla_coil_2 tesla_coil_3 tesla_coil_4 tesla_coil_5 tesla_linker}}

{{items tesla_emitter_1 tesla_emitter_2 tesla_emitter_3 tesla_emitter_4 tesla_emitter_5}}

{{image shots/11_tesla_network.jpg|A Tesla Relay network}}


## Charging your gear

A [[charger]] does one item at a time. The [[wireless_charger]] (Age 2) charges everything players carry while they work nearby.

{{image shots/gui_wireless_charger.jpg|Wireless Charger menu}}
