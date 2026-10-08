---
title: Build the Fission Reactor
icon: reactor_controller
order: 40
summary: Step by step build of the 5x5x5 to 7x7x7 Fission Reactor, with coolant values and example outputs.
---
5x5x5 to 7x7x7 (outside size). Age 2-3.

## Parts

{{items reactor_controller reactor_casing reactor_glass reactor_power_port reactor_access_port reactor_fuel_rod cryo_coolant thorium_fuel_pellet enriched_fuel_pellet}}

## Steps

1. Build the **frame** (all 12 edges and 8 corners) from [[reactor_casing]]. No glass on edges.
2. Fill the **walls** with any mix of [[reactor_casing]], [[reactor_glass]], [[reactor_power_port]] (at least one) and [[reactor_access_port]] (optional).
3. Put the [[reactor_controller]] in a side wall, screen facing out.
4. Place [[reactor_fuel_rod]] blocks in **full columns**, floor to roof. A gap in a column breaks the structure.
5. Fill the rest of the inside with air or coolant. Coolant counts on the 4 horizontal sides of a rod.
6. Put fuel in: [[thorium_fuel_pellet]] or [[enriched_fuel_pellet]] into the controller or an Access Port. Take power from a Power Port (cable or a [[tesla_coil_1]] on top).

| Coolant | Points |
|---|---|
| [[minecraft:water]] | 1 |
| [[minecraft:ice]] | 1.5 |
| [[minecraft:packed_ice]] | 2 |
| [[minecraft:blue_ice]] | 3 |
| [[cryo_coolant]] | 4 |

| Fuel | Heat per rod | Lasts | Waste |
|---|---|---|---|
| [[thorium_fuel_pellet]] | 400 | 12,000 ticks / active rods | [[depleted_fuel_pellet]] |
| [[enriched_fuel_pellet]] | 1,000 | 12,000 ticks / active rods | [[depleted_fuel_pellet]] |

> Access Ports take fuel in and hand waste out: hoppers and pipes work. A full waste slot stops the reactor.

Every rod needs coolant beside it. In a 5x5x5, put rods in an X (corners and center) with coolant in the four gaps.

{{multiblock fission_reactor_5}}

{{multiblock fission_reactor_7}}

{{image shots/19_fission_reactor.jpg|A formed 5x5x5 Fission Reactor}}

{{image shots/gui_fission_formed.jpg|Fission Reactor Controller, formed}}

## Heat

- Up to **1,000 C**: full output.
- Above it the output throttles down to 25%; at **1,800 C** it SCRAMs. Reset from the GUI below 200 C.
- A SCRAM loses the burning pellet: it turns into [[depleted_fuel_pellet]].

Control rods (0-100%): GUI slider; 100% stops the reactor. It pauses while the 5M FE buffer is full.

After a SCRAM: push rods in or add coolant, then Reset.

Only the owner, their team or an operator can move the rods or Reset.

JEI shows every fuel and coolant.

## Example outputs

| Build | Output |
|---|---|
| 5x5x5, one column of 3 thorium rods, 4 water around each | about 1,100 FE/t |
| Same with packed ice | about 1,280 FE/t |
| 7x7x7, 13 enriched columns in a Cryo Coolant checkerboard | about 37,000 FE/t at 32% load |

Water-only big reactors run hot: push some rods in.
