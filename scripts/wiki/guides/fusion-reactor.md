---
title: Build the Fusion Reactor
icon: fusion_controller
order: 50
summary: The fixed 7x3x7 Age 4 Fusion Reactor: ignition charge, Fusion Fuel Pellets and how the plasma behaves.
---
Fixed 7x3x7 (Age 4). Needs a 100M FE charge to start; once lit it burns fuel nonstop.

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

1. **Build** it (above).
2. **Charge**: feed **100,000,000 FE** into a Power Port (at most 1,000,000 FE/t in total). The same port sends the power out. Use a Capacitor Bank or Tesla Coils.
3. **Fuel**: put [[fusion_fuel_pellet]] in the controller or an Access Port.
4. **Switch on**: set the GUI button to **Running**. The charge is spent and the plasma lights. Only the owner, their team or an operator can switch it.

It runs as long as it has fuel: one pellet lasts **5 minutes**. With no fuel for 5 s, or when switched off, the plasma collapses and needs a new charge.

| Fuel | Output | Burn time |
|---|---|---|
| [[fusion_fuel_pellet]] | 200,000 FE/t | 6,000 ticks (5 min) |

- **Fuel source**: [[radiant_isotope]] comes from centrifuging [[depleted_fuel_pellet]] (20% chance): run a fission reactor first.
- **Warmup**: output ramps from 0 to full over 10 s.
- It burns fuel whether or not the power is taken (20M FE buffer).
- Breaking the structure collapses the plasma.

{{image shots/gui_fusion_formed.jpg|Fusion Reactor Controller, formed}}

{{guide capacitor-bank}}
