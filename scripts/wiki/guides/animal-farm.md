---
title: Animal Farm with the Rancher
icon: rancher
order: 33
summary: Place a Rancher in your pen: it breeds, culls, shears and milks, and fills a chest with the loot.
---
The [[rancher]] works every animal around its home, modded ones too.

{{items rancher rancher_mk2}}

{{image shots/rancher_pen.jpg|Two Ranchers feeding and shearing in a fenced pen with a chest}}

## Set up

1. Build a pen. A Mk1 works 9x9 around its home (Mk2 13x13), from 2 blocks below to 3 above.
2. Put a chest (or any inventory) within 3 blocks of where the Rancher will stand. With several, it uses the biggest.
3. Fill the chest with feed for your animals (wheat, carrots, seeds...) and empty buckets.
4. Right-click a block in the pen with the Rancher: that spot is its home.
5. Right-click the Rancher: put a charged cell in its battery slot and set the herd size.

## What it does

- **Herd size** counts adults, split evenly over species with 2+ adults: 12 with cows, pigs and sheep keeps 4 of each.
- Below the share it breeds two adults with feed from the chest; above it, it culls one adult.
- Name or leash an animal to keep it from being culled.
- Drops, wool and milk go into the chest. Chest full: it stops culling, shearing and milking.
- **Shear** and **Milk** (on by default): sheep are sheared, cows and goats fill an empty bucket once a minute each. Turned off, those animals are bred and culled like the rest.

## Power

| | Mk1 | Mk2 |
|---|---|---|
| Area | 9x9 | 13x13 |
| Rest between actions | 2 s | 1 s |
| Buffer | 400k FE | 1.6M FE |

Each feed, shear or milk costs 200 FE, each cull 500 FE. Charge it from its battery slot, by right-clicking it with a cell, or carry it to a [[charger]].

## Mk2

Smithing table: [[advanced_circuit]] template + Rancher Mk1 + [[servo_actuator]]. Energy and settings stay.

> Status in its menu and in Jade: Working, Idle, No energy, Storage full, No feed or No storage. The lamp on its chest shows it too: green working, dim green idle, amber no feed, blinking red for the rest.
