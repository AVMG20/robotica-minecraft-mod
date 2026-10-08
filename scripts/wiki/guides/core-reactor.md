---
title: Build the Core Reactor
icon: reactor_controller
order: 45
summary: The fixed 5x5x5 reactor that burns fuel pellets and a boss core. Amplifiers trade run time for power.
---
## Parts

{{items reactor_controller reactor_casing reactor_glass reactor_power_port reactor_access_port flux_amplifier graphite_damper servo_core}}

## Build

1. Build a 5x5x5 frame of [[reactor_casing]].
2. Fill the walls with casing, [[reactor_glass]], at least one [[reactor_power_port]] and any [[reactor_access_port]]s.
3. Put the [[reactor_controller]] in a side wall, screen facing out.
4. Leave the middle block empty: the core floats there. The other 26 inside blocks are air or modulators.
5. Add a core and fuel pellets. It works anywhere, underground included.

{{multiblock core_reactor}}

## Cores

| Core | Power |
|---|---|
| [[servo_core]] | x2 |
| [[magma_core]] | x6 |
| [[antigrav_core]] | x16 |

A core lasts 168 hours of running at burn x1, then breaks. Cores drop from bosses and can also be crafted. A core taken out keeps its wear. Put a spare in the core slot: it moves in when the active one breaks.

## Modulators

| Block | Power | Burn |
|---|---|---|
| [[minecraft:redstone_block]] | +3% | +6% |
| [[flux_amplifier]] | +8% | +12% |
| [[pyro_amplifier]] | +15% | +18% |
| [[resonant_amplifier]] | +25% | +22% |
| [[minecraft:packed_ice]] | -2% | -4% |
| [[graphite_damper]] | -3% | -8% |
| [[minecraft:blue_ice]] | -3% | -7% |
| [[cryo_coolant]] | -4% | -12% |

Burn speeds up both the pellet and the core. Power and burn never drop below x0.25.

## Example outputs

| Build | Output | Core lasts |
|---|---|---|
| Servo Core, thorium, empty | 1,200 FE/t | 168 h |
| Servo Core, thorium, 26 Flux Amplifiers | 3,700 FE/t | 41 h |
| Magma Core, enriched, empty | 9,000 FE/t | 168 h |
| Magma Core, enriched, 26 Pyro Amplifiers | 44,000 FE/t | 30 h |
| Antigrav Core, enriched, 26 Resonant Amplifiers | 180,000 FE/t | 25 h |

It pauses while its buffer is full, so a core never wears out unused.
