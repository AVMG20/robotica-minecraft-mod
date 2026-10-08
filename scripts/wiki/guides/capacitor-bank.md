---
title: Build the Capacitor Bank
icon: bank_controller
order: 45
summary: Multiblock FE storage, 4x3x3 to 9x9x9 outside.
---
## Parts

{{items bank_controller bank_casing bank_glass bank_port capacitor_copper capacitor_redstone capacitor_ender capacitor_resonant transfer_coil_basic transfer_coil_advanced transfer_coil_elite}}

## Steps

1. Build the frame (edges and corners) from [[bank_casing]].
2. Fill the walls with [[bank_casing]], [[bank_glass]] and at least one [[bank_port]].
3. Put the [[bank_controller]] in a side wall.
4. Fill the inside with any mix of Capacitors and Transfer Coils (at least one of each); the rest stays air.

Capacitors set how much it stores, Transfer Coils how fast FE moves in and out. Rebuilding it smaller loses the FE above the new capacity.

{{image shots/20_capacitor_bank.jpg|A formed 5x5x5 Capacitor Bank}}

{{multiblock capacitor_bank_small}}

{{multiblock capacitor_bank_5}}
