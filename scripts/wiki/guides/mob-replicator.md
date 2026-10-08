---
title: Mob Replicator
icon: replicator_controller
order: 55
summary: Sample a mob with an Essence Vial, then farm its drops (or spawn it) in a 3x3x3 Mob Replicator.
---
Farms a mob's drops without the mob. Age 2 on.

{{image shots/08_replicator.jpg|A formed Mob Replicator with its mob hologram}}

## 1. Take a sample

{{items essence_vial}}

- Right-click a hostile mob with an [[essence_vial]]: 2 damage, 3 s cooldown.
- The first sample binds the vial to that mob type.
- **8 samples** complete it.

## 2. Build the structure

{{items replicator_controller replicator_frame replicator_glass}}

- A 3x3x3 cube: [[replicator_controller]] in the middle of one face.
- The rest of the shell is [[replicator_frame]] and [[replicator_glass]] (at least 1 glass).
- The center block is air.

{{multiblock mob_replicator}}

## 3. Run it

- Insert the complete vial and feed FE: **160 FE/t** base.
- One cycle takes **1,200 ticks** (1 min).
- Speed cards: max 3. A [[plasma_actuator]] in the boost slot doubles speed for extra FE.
- Fortune cards act as **Looting** (up to III).

| Mode | What it does |
|---|---|
| Harvest (default) | Rolls the mob's loot into an 18-slot output. No entity spawns. The mob's XP is stored (up to 1,000,000 points). |
| Spawn | Spawns the real mob in front of the controller. Waits while 8 of that type (or 32 mobs) are within 8 blocks. |

{{image shots/gui_replicator_formed.jpg|Replicator Controller GUI, formed}}

## Experience and output

- The row above the output shows the stored XP.
- Set a target level with **-** and **+** (shift-click: 10 at a time, default 30), then press **To Lv** to get exactly enough XP to reach it.
- **All** claims everything.
- Breaking the controller drops the stored XP as orbs.
- Sides tab: every face is Output. Turn on auto-eject to push the output into a chest or pipe next to it.

## Tier gates

Catalyst slot (kept):

| Mobs | Needs |
|---|---|
| Blaze, enderman, guardian, ghast, wither skeleton, piglin brute | [[magma_core]] |
| Shulker | [[antigrav_core]] |

## Blocked drops

Boss drops, [[minecraft:nether_star]], [[minecraft:dragon_egg]] and the blacklist tags.

{{upgrades replicator_controller}}
