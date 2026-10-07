---
title: Build the Capacitor Bank
icon: bank_controller
order: 45
summary: Multiblock FE storage from 3x3x3 to 9x9x9, its capacitor tiers, transfer coils and ports.
---
A variable-size cuboid from 3x3x3 to 9x9x9 (outside size) that stores FE. Age 1 on.

## Parts

{{items bank_controller bank_casing bank_glass bank_port capacitor_copper capacitor_redstone capacitor_ender capacitor_resonant transfer_coil_basic transfer_coil_advanced transfer_coil_elite}}

## Steps

1. Build the frame (edges and corners) from [[bank_casing]].
2. Fill the walls with [[bank_casing]], [[bank_glass]] and at least one [[bank_port]].
3. Put the [[bank_controller]] in a side wall.
4. Fill the inside with any mix of Capacitors and Transfer Coils (at least one of each); the rest stays air.

> Smallest working bank: 4x3x3. A 3x3x3 has room for one part only.

| Capacitor | FE per block | Age |
|---|---|---|
| [[capacitor_copper]] | 4,000,000 | 1 |
| [[capacitor_redstone]] | 64,000,000 | 2 |
| [[capacitor_ender]] | 512,000,000 | 4 |
| [[capacitor_resonant]] | 4,096,000,000 | 4 |

| Transfer Coil | Adds FE/t | Age |
|---|---|---|
| [[transfer_coil_basic]] | 16,000 | 1 |
| [[transfer_coil_advanced]] | 512,000 | 2 |
| [[transfer_coil_elite]] | 4,000,000 | 3 |

The sum of all coils limits input and, separately, output per tick, shared by all ports.

{{image shots/20_capacitor_bank.jpg|A formed 5x5x5 Capacitor Bank}}

## Ports

- A [[bank_port]] is input or output: **sneak + right-click** toggles it (the face shows an arrow).
- Output ports push into the block outside them and can be pulled from; a [[tesla_coil_1]] on top works.

## Keeping the energy

- The energy lives in the controller: it stays when the structure breaks.
- Mined, the controller item keeps its energy (it always drops, explosions included).
- Formed smaller than its energy, the part above the new capacity is lost.

{{image shots/gui_bank_formed.jpg|Capacitor Bank Controller, formed}}

{{multiblock capacitor_bank_small}}

{{multiblock capacitor_bank_5}}
