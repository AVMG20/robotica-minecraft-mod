---
title: Ore Processing
icon: grinder_mk1
order: 30
summary: Double your ores with the Grinder and Electric Furnace, grinding media, Mk tiers and cards.
---
Two machine lines, Mk1 (Age 1, needs a Basic Circuit) to Mk4. Both work on `c:` tags, so other mods' ores work too.

{{items grinder_mk1 electric_furnace_mk1 iron_dust iron_grinding_balls}}

## Grinder

200 ticks at 20 FE/t on Mk1 (4,000 FE per ore).

| Input | Output |
|---|---|
| ore (`c:ores`) | 2 dust |
| gem ore | 2 gems |
| raw ore | 1 dust + 25% chance of one more |
| ingot | 1 dust |

- Dusts smelt into ingots.

{{image shots/gui_grinder.jpg|Grinder Mk2 menu}}

## Electric Furnace

- Every vanilla smelting recipe at half the time, 20 FE/t per lane and item.
- Lanes in parallel: Mk1 1, Mk2 2, Mk3 4, Mk4 8.

## Grinding media

Optional second input. Each item lasts its uses (media bar). The bonus applies to ores and raw ores.

| Media | Bonus | Byproduct | Uses | Min Mk |
|---|---|---|---|---|
| [[minecraft:flint]] | +10% | 2% | 8 | any |
| [[iron_grinding_balls]] | +25% | 5% | 16 | Mk1 |
| [[ferrothorium_grinding_balls]] | +50% | 10% | 32 | Mk2 |
| [[pyrosteel_grinding_balls]] | +100% | 20% | 64 | Mk3 |
| [[resonant_grinding_balls]] | +120% | 25% | 128 | Mk4 |

{{image shots/16_grinder_furnace.jpg|Grinders and Electric Furnaces, Mk1 to Mk4}}

## Mk tiers

Right-click a placed machine with the next Mk ([the Mk rule](#/guide/upgrade-cards)), or craft the next Mk around the previous one.

| | Mk1 | Mk2 | Mk3 | Mk4 |
|---|---|---|---|---|
| Speed | x1 | x1.5 | x2 | x3 |
| Card slots | 2 | 3 | 4 | 5 |
| Buffer | 20k | 80k | 320k | 1.28M FE |
| Input | 4k | 16k | 64k | 256k FE/t |

## Cards

{{upgrades grinder_mk1}}
{{upgrades electric_furnace_mk1}}

## By hand

The [[tinkers_hammer]] cracks raw thorium, pyrolite shards, resonite crystals and coal into one dust each. The Grinder doubles.

{{guide industry-alloys}}
