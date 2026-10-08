---
title: Build the Fusion Reactor
icon: fusion_controller
order: 50
summary: The fixed 7x3x7 Age 4 reactor: a 100M FE charge lights it, fuel keeps it burning.
---
## Parts

{{items fusion_controller fusion_casing fusion_coil reactor_glass reactor_power_port reactor_access_port fusion_fuel_pellet}}

## Build

1. Frame of [[fusion_casing]].
2. Walls of [[fusion_casing]], [[reactor_glass]], [[reactor_power_port]] (at least one) and [[reactor_access_port]] (fuel in only).
3. [[fusion_controller]] in a side wall.
4. Middle layer: a ring of **16** [[fusion_coil]] blocks around an empty 3x3 plasma chamber.

{{multiblock fusion_reactor}}

{{image shots/21_fusion_reactor.jpg|A formed Fusion Reactor}}

## Running it

The start-up steps are on the [[fusion_controller]] page.

- Keep it fed. With no fuel for 5 s, switched off or broken, the plasma collapses and needs a new 100M FE charge.
- It burns fuel whether or not the power is taken.
- Only the owner, their team or an operator can switch it.
- Fuel needs [[radiant_isotope]], which comes from centrifuging [[depleted_fuel_pellet]]: run a fission reactor first.

{{guide capacitor-bank}}
