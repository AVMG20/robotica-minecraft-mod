---
title: Build the Ring Collider
icon: collider_controller
order: 50
summary: The Age 4 particle ring: the biggest generator, and the only source of Strange Matter.
---
## Parts

{{items collider_controller accelerator_segment resonant_segment fusion_fuel_pellet strange_matter}}

## Build

1. Lay a flat closed loop of [[accelerator_segment]]s, 24 to 256 blocks long. Any shape works.
2. Put the [[collider_controller]] in the loop. Every loop block touches exactly two others.
3. Keep parallel stretches at least one block apart, or they join and branch.

{{multiblock ring_collider}}

{{multiblock ring_collider_resonant}}

## Running it

1. Charge it: 500,000 FE per loop block into the controller. A full Capacitor Bank does it in seconds.
2. Add [[fusion_fuel_pellet]]s and press Running. The charge is spent and the beam ramps up to full over 10 s.
3. Keep it fed. Out of fuel for 5 s, or switched off, the beam collapses and needs a new charge.

A pellet lasts 5 minutes in a 64 block ring; longer rings burn faster. Fuel needs [[radiant_isotope]] from centrifuged [[depleted_fuel_pellet]]s, so a Core Reactor or Tesla Spire feeds it.

## Output

The controller counts as one loop block.

| Ring | Output | One Strange Matter every |
|---|---|---|
| 24 blocks, Accelerator Segments | 23,000 FE/t | 28 minutes |
| 64 blocks, Accelerator Segments | 63,000 FE/t | 10 minutes |
| 128 blocks, Resonant Segments | 317,500 FE/t | 2.5 minutes |
| 256 blocks, Resonant Segments | 637,500 FE/t | 75 s |

While the buffer is full the beam pauses: no fuel burns and no Strange Matter forms.

[[strange_matter]] goes into [[null_circuit]]s without a nether star and into [[antigrav_core]]s in the Assembler.

{{guide capacitor-bank}}
