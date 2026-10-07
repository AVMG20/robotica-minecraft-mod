---
title: Build the Fusion Reactor
icon: fusion_controller
order: 50
summary: The fixed 7x3x7 Age 4 Fusion Reactor: ignition charge, fusion fuel and how the plasma behaves.
---
A fixed 7x3x7 structure (Age 4). It needs a big charge to start and burns fuel nonstop once lit.

## Parts

{{items fusion_controller fusion_casing fusion_coil reactor_glass reactor_power_port reactor_access_port fusion_fuel_pellet}}

## Build

1. Frame of [[fusion_casing]].
2. Walls of [[fusion_casing]], [[reactor_glass]], [[reactor_power_port]] (at least one) and [[reactor_access_port]] (fuel in only).
3. [[fusion_controller]] in a side wall.
4. Middle layer: a ring of **16** [[fusion_coil]] blocks around an empty 3x3 plasma chamber.

{{multiblock fusion_reactor}}

## Running it

1. **Charge**: Power Ports take FE in, at most **1,000,000 FE/t** in total, until the **20,000,000 FE** ignition charge is full. Use a Capacitor Bank or Tesla Coils.
2. **Fuel**: put [[fusion_fuel_pellet]] in the controller or an Access Port.
3. **Ignite**: switch it on; the charge is spent and the plasma lights.

| Fuel | Output | Burn time |
|---|---|---|
| [[fusion_fuel_pellet]] | 200,000 FE/t | 2,400 ticks |

- **Warmup**: output ramps from 0 to full over 10 s.
- **No throttle**: it burns fuel whether or not the power is taken (20M FE buffer).
- **Starve**: without fuel the plasma survives 5 s, then collapses and needs a new charge. Switching off or breaking the structure collapses it too.
- No explosions, no block damage.

{{guide capacitor-bank}}
