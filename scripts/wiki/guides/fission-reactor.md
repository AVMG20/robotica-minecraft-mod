---
title: Build the Fission Reactor
icon: reactor_controller
order: 40
summary: Step by step build of the 5x5x5 to 7x7x7 Fission Reactor, with coolant values and example outputs.
---
A variable-size cuboid from 5x5x5 to 7x7x7 (outside size). Age 2-3 power.

## Parts

{{items reactor_controller reactor_casing reactor_glass reactor_power_port reactor_access_port reactor_fuel_rod cryo_coolant thorium_fuel_pellet enriched_fuel_pellet}}

## Steps

1. Build the **frame** (all 12 edges and 8 corners) from [[reactor_casing]]. No glass on edges.
2. Fill the **walls** with any mix of [[reactor_casing]], [[reactor_glass]], [[reactor_power_port]] (at least one) and [[reactor_access_port]] (optional).
3. Put the [[reactor_controller]] in a side wall, screen facing out.
4. Place [[reactor_fuel_rod]] blocks in **full columns**, floor to roof. A gap in a column breaks the structure.
5. Fill the rest of the inside with air or coolant. Only the 4 horizontal neighbours of a rod count.
6. Put fuel in: [[thorium_fuel_pellet]] or [[enriched_fuel_pellet]] into the controller or an Access Port. Take power from a Power Port (cable or a [[tesla_coil_1]] on top).

| Coolant | Points |
|---|---|
| [[minecraft:water]] | 1 |
| [[minecraft:ice]] | 1.5 |
| [[minecraft:packed_ice]] | 2 |
| [[minecraft:blue_ice]] | 3 |
| [[cryo_coolant]] | 4 |

| Fuel | Heat | Burn time | Waste |
|---|---|---|---|
| [[thorium_fuel_pellet]] | 400 | 12,000 ticks | [[depleted_fuel_pellet]] |
| [[enriched_fuel_pellet]] | 1,000 | 12,000 ticks | [[depleted_fuel_pellet]] |

> Access Ports take fuel in and hand waste out: hoppers and pipes work.

{{multiblock fission_reactor_5}}

{{multiblock fission_reactor_7}}

## Heat

- Up to **1,000 C**: full output.
- Above it the output throttles down to 25%; at **1,800 C** it SCRAMs. Reset from the GUI below 200 C.
- It **never explodes** and never changes blocks.

Control rods (0-100%) are set with the GUI slider. Fuel only burns while the 5M FE buffer has room.

## Example outputs

| Build | Output |
|---|---|
| 5x5x5, one column of 3 thorium rods, 4 water around each | about 1,150 FE/t |
| Same with packed ice | about 1,350 FE/t |
| 7x7x7, 13 enriched columns in a Cryo Coolant checkerboard | about 45,000 FE/t at 32% load |

Water-only big reactors run hot: push some rods in.
