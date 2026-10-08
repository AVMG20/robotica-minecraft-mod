---
title: Animal Farm with the Rancher
icon: rancher
order: 33
summary: Place a Rancher in your pen: it breeds, culls, shears and milks, and fills a chest with the loot.
---
The [[rancher]] is a walking robot for passive mobs. It works every animal in the area around its home, modded ones too.

{{items rancher rancher_mk2}}

## Set up

1. Build a pen. A Mk1 works 9x9 around its home (Mk2 13x13), from 2 blocks below to 3 above.
2. Put a chest (or any inventory) within 3 blocks of the spot where the Rancher will stand.
3. Fill the chest with feed for your animals (wheat, carrots, seeds...) and empty buckets.
4. Right-click a block in the pen with the Rancher: that spot is its home.
5. Right-click the Rancher: put a charged cell in its battery slot and set the herd size.

## What it does

- **Herd size** counts adults. With several species it is split evenly: herd size 12 with cows, pigs and sheep keeps 4 of each.
- Below the share it feeds two adults with feed from the chest; above it, it culls one adult at a time.
- Drops, wool and milk go into the chest. When the chest is full it stops culling, shearing and milking.
- It culls plain adults only: babies, named, leashed, tamed and ridden animals stay.
- **Shear** (on by default): sheep are sheared and kept.
- **Milk** (on by default): cows and goats fill one empty bucket each, once a minute per animal, and are kept.
- Turn Shear or Milk off and sheep or cows are bred and culled like the others.

## Power

| | Mk1 | Mk2 |
|---|---|---|
| Area | 9x9 | 13x13 |
| Rest between actions | 2 s | 1 s |
| Buffer | 400k FE | 1.6M FE |

Each feed, shear or milk costs 200 FE, each cull 500 FE. Charge it from its battery slot, by right-clicking it with a cell, or carry it to a [[charger]].

## Mk2

Smithing table: [[advanced_circuit]] template + Rancher Mk1 + [[servo_actuator]]. Energy and settings stay.

> Status in its menu and in Jade: Working, Idle, No energy, Storage full, No feed or No storage.
