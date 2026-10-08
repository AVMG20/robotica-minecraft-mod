---
title: Build a Tesla Spire
icon: spire_crown
order: 40
summary: The Age 2 lightning tower: a column of metal blocks that burns thorium and catches storms.
---
## Parts

{{items spire_base spire_crown minecraft:copper_block minecraft:iron_block minecraft:diamond_block thorium_ingot thorium_fuel_pellet}}

## Build

1. Place the [[spire_base]] where nothing will ever sit above the column.
2. Stack 8 to 24 conductor blocks on it. Mix metals freely.
3. Put a [[spire_crown]] on top. It needs open sky.
4. Feed the base thorium ingots, thorium blocks or fuel pellets (hoppers work). FE leaves its sides and bottom.

{{multiblock tesla_spire}}

{{multiblock tesla_spire_mixed}}

## Conductors

Each block adds its power. The fuel efficiency of the column is the power-weighted average of its blocks: it sets how much FE one fuel item gives.

| Block | FE/t | Fuel efficiency |
|---|---|---|
| [[minecraft:copper_block]] (any oxidation) | 30 | 100% |
| [[minecraft:iron_block]] | 45 | 125% |
| [[minecraft:emerald_block]] | 60 | 175% |
| [[minecraft:gold_block]] | 90 | 75% |
| [[minecraft:diamond_block]] | 120 | 200% |
| [[pyrolite_block]] | 140 | 80% |
| [[resonite_block]] | 200 | 300% |
| [[minecraft:netherite_block]] | 500 | 300% |

Blocks of other mods' metals count too: tin, lead, nickel, aluminum, bronze, constantan, invar, steel, osmium, silver, electrum, signalum, lumium, enderium.

## Placement

- Output rises +0.25% per block the crown sits above Y 64, up to +50%.
- Rain gives x1.25, thunder x1.5.
- Every other spire within 64 blocks lowers it. Two spires side by side make as much as one.

## Lightning

A strike lands about every 6 minutes in clear weather, every 2 minutes in rain and every 30 s in a thunderstorm. It adds 30 s of full output to the buffer, fuel or not.

## Example outputs

| Column (crown at Y 72, clear sky) | Output |
|---|---|
| 16 copper | about 490 FE/t, a thorium pellet lasts about 7 minutes |
| 16 iron | about 730 FE/t |
| 16 diamond | about 1,950 FE/t |
| 24 netherite on a mountain, thunderstorm | about 27,000 FE/t |
