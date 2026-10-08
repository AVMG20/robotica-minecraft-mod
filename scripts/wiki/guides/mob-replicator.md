---
title: Mob Replicator
icon: replicator_controller
order: 55
summary: Sample a mob with an Essence Vial, then farm its drops (or spawn it) in a 3x3x3 Mob Replicator.
---
{{image shots/08_replicator.jpg|A formed Mob Replicator with its mob hologram}}

1. Fill an [[essence_vial]] from the mob you want.
2. Build the 3x3x3: [[replicator_controller]] in the middle of one face, the rest of the shell [[replicator_frame]] and at least one [[replicator_glass]], the center air.
3. Put the full vial in the controller and power it.

{{items essence_vial replicator_controller replicator_frame replicator_glass}}

{{multiblock mob_replicator}}

## Modes

| Mode | What it does |
|---|---|
| Harvest (default) | Rolls the mob's loot into an 18-slot output. No entity spawns. The mob's XP is stored (up to 1,000,000 points). |
| Spawn | Spawns the real mob in front of the controller. Waits while 8 of that type (or 32 mobs) are within 8 blocks. |

{{image shots/gui_replicator_formed.jpg|Replicator Controller GUI, formed}}

- **To Lv** gives exactly enough XP to reach the target level (default 30).
- Breaking the controller drops the stored XP as orbs.
- A [[plasma_actuator]] in the boost slot doubles speed for extra FE.

## Tier gates

Catalyst slot (kept):

| Mobs | Needs |
|---|---|
| Blaze, enderman, guardian, ghast, wither skeleton, piglin brute | [[magma_core]] |
| Shulker | [[antigrav_core]] |

## Blocked drops

Boss drops, [[minecraft:nether_star]], [[minecraft:dragon_egg]] and the blacklist tags.
