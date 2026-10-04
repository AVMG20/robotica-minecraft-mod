# Robotica design spec

Source of truth for implementation. NeoForge 1.21.1, Java 21, mod id `robotica`, base package `com.arno.robotica`.

## Direction

- Faster than vanilla, on purpose. Built for modpack pace next to Refined Storage, Thermal, Mekanism.
- Starts on day one with wood, stone and copper. No iron needed for Age 0.
- Cheap to start, expensive to upgrade. Base machines and tools are affordable. Upgrades pull in later-age components, so the total cost climbs with every tier.
- Multiplayer first. Everything must run on a dedicated server. Server is authoritative, clients only render and send input.
- Forge Energy (FE) is the only energy unit. Items move through standard `IItemHandler` capabilities.

## Ages

| Age | Name | Gate | Raw materials |
|---|---|---|---|
| 0 | Clockwork | none | wood, stone, copper |
| 1 | Wired | none | iron, redstone, gold |
| 2 | Servo | Servo Core | diamond, obsidian, quartz |
| 3 | Deep | Magma Core (+ Swarm, Tide later) | netherite scrap, blaze, prismarine |
| 4 | Antigrav | Antigrav Core | ender pearls, nether star, shulker shell |

Bosses are not implemented yet. Until they are, each core has a temporary expensive crafting recipe (marked `temp_` in the file name).

## Balance system: component ladder

Every age has three intermediate parts: a casing, a mechanism (moving part) and a circuit. Each age's parts consume several parts of the age before, so cost multiplies roughly 3-4x per age. Final items (machines, tools, robots, upgrade cards) are built from these parts, never mostly from raw materials.

Plates: `c:plates/iron` etc. Hand recipe: Tinker's Hammer + 2 ingots → 1 plate (hammer loses durability). Metal Press: 1 ingot → 1 plate. Any mod's plates with the common tag also work (Thermal, Mekanism, Immersive Engineering).

### Age 0 parts
- Copper Gear: 4 copper ingot (plus shape) + 1 cobblestone → 1
- Mainspring (Age 0 battery, 576,000 FE, wound at the Winding Crank): 8 copper ingot ring + 1 copper gear → 1
- Clockwork Mechanism: 4 copper gear + 4 planks + 1 cobblestone center → 1 (raw: 16 copper)
- Wooden Chassis: 4 logs + 4 cobblestone + 1 copper block → 1

### Age 1 parts
- Iron Plate (see plates)
- Copper Coil: 8 copper ingot + 1 iron ingot → 1
- Iron Casing: 8 iron ingot + 1 redstone → 1
- Basic Circuit: 3 redstone / copper ingot, gold ingot, copper ingot / 3 iron plate → 1
- Electric Motor: 4 iron plate + 2 copper coil + 2 redstone + 1 clockwork mechanism → 1
- Copper Cell (2,304,000 FE): 4 copper ingot + 2 iron plate + 2 redstone + 1 basic circuit

### Age 2 parts
- Reinforced Casing: 4 iron casing + 4 obsidian + 1 diamond → 1 (raw: 32 iron)
- Advanced Circuit: 4 basic circuit + 2 gold ingot + 2 quartz + 1 diamond → 1
- Servo Actuator: 2 electric motor + 4 gold ingot + 2 iron plate + 1 advanced circuit → 1
- Redstone Cell (6,912,000 FE): 2 copper cell + 4 redstone block + 1 reinforced casing + 1 advanced circuit
- Servo Core: boss drop (temp recipe: 4 diamond + 4 servo actuator + 1 advanced circuit)

### Age 3 parts
- Blazing Casing: 4 reinforced casing + 4 netherite scrap + 1 magma core → 2
- Quantum Circuit: 4 advanced circuit + 4 blaze rod + 1 netherite ingot → 1
- Plasma Actuator: 2 servo actuator + 4 prismarine crystals + 2 blaze powder + 1 quantum circuit → 1
- Magma Core: boss drop (temp: 4 servo actuator + 4 magma block + 1 netherite ingot)

### Age 4 parts
- Null Casing: 4 blazing casing + 4 shulker shell + 1 antigrav core → 2
- Null Circuit: 4 quantum circuit + 4 ender pearl + 1 nether star → 2
- Ender Cell (20,736,000 FE): 2 redstone cell + 2 null casing + 4 ender pearl + 1 null circuit
- Antigrav Core: boss drop (temp: 1 nether star + 4 plasma actuator + 4 end crystal)

Rough raw cost of one casing: Age 1 = 8 iron, Age 2 = 32 iron, Age 3 = 64 iron + netherite, Age 4 = 128 iron + shulkers. Final recipes at Age 3-4 typically need 2-4 of those parts each.

## Upgrade cards

One item per kind and level: `robotica:upgrade_<kind>_<level>` (level 1-4). Level N requires Age N parts. The recipe for level N+1 consumes the level N card, so a level 4 card contains every tier's cost.

Kinds and level effects (machines read the highest installed level of each kind):
- `speed`: action rate ×2 / ×4 / ×10 / ×20. Energy per action +25% per level.
- `range`: area radius steps defined per machine (see machine).
- `efficiency`: energy per action −15% per level (min 40% of base).
- `fortune`: cards exist at levels 2, 3 and 4 only and give Fortune I, II and III. There is no `upgrade_fortune_1`, so fortune needs Age 2.
- `silk`: single level card (`upgrade_silk_1`, made with Age 2 parts). Mutually exclusive with fortune.
- `growth`: crop/sapling growth boost (farm bots).
- `void`: deletes items matching `robotica:voidable` tag (cobblestone, cobbled deepslate, dirt, gravel, netherrack, tuff, diorite, andesite, granite). Single level, Age 1.

Card catalysts per kind: speed = sugar + redstone, range = lapis at level 1 and ender pearl from level 2, efficiency = gold, fortune = lapis block + diamond, silk = slime ball + emerald, growth = bone block, void = cactus + obsidian.

## Machines and power (module `power`)

- Winding Crank (Age 0): right-click with empty hand winds a Mainspring placed in it (+2,000 FE per click, 4 click/s cap). Accepts redstone-signal-free automation: if an FE source pushes into it, it winds at 200 FE/t. Mainsprings only charge here.
- Combustion Generator (Age 1): burns furnace fuel, 40 FE/t, buffer 40,000.
- Solar Panel Mk1 (Age 1) 8 FE/t, Mk2 (Age 2) 32 FE/t daytime with sky access.
- Accumulator I/II/III: 1M / 4M / 16M FE, I/O 1,000 / 4,000 / 16,000 FE/t.
- Copper Conduit 256 FE/t, Gold Conduit 1,024 FE/t: connect to any block with the FE capability.
- Charger (Age 1): charges FE items, 400 FE/t, one slot.
- Metal Press (Age 1): 1 ingot → 1 plate, 20 FE/t, 100 ticks. Inputs: iron, copper, gold ingot.

## Automation (module `automation`)

Area workers are block entities, never mobs. All of them: battery slot (cell or Mainspring), upgrade slots, output to inventories on any adjacent side (chest, RS Interface, AE2 interface, Supply Crate), internal 9-slot buffer when outputs are full, work stops when full or out of energy. Idle drain 0.2 FE/t (rounded: 1 FE every 5 ticks).

- Stumpy (lumber bot, Age 0). Mk1 9×9, 1 action / 40 ticks, growth ×1.5, 4 FE/t while working. Fells a whole tree as one action (up to 256 logs, includes leaves only with a toggle), replants saplings from its buffer, collects item drops in the area.
- Sprout (crop bot, Age 0). Harvests mature crops (`CropBlock`, nether wart, sweet berries, cocoa), replants, tills dirt with water nearby. 3 FE/t.
- Farm tiers via Mk kits used on the placed bot: Mk2 (Age 1 parts) 13×13, every 20 ticks, growth ×2; Mk3 (Age 2) 17×17, 5 ticks, ×3; Mk4 (Age 4) 25×25, 1 tick, ×5. Range cards add +2 radius per level on top.
- Growth boost is applied as extra random ticks on crops/saplings in the area (cheap: N random positions per second, not every block).
- Supply Crate (Age 0): 27 slots, plain inventory with item capability.
- Excavator (Age 1, starts cheap): mines a real hole below itself. Base 8×8 area, 1 block / 40 ticks, 40 FE per block, stops at bedrock and leaves fluids alone (replaces fluid source blocks with cobblestone as it goes, so no flooding). Upgrades: range 16/32/48/64 square (level 1-4), speed, efficiency, fortune, silk, void. Never breaks blocks with an unbreakable hardness or block entities. Area outline shown client side when you look at the machine.

## Tools and weapons (module `gear`)

Tools from Age 1 on use FE instead of durability and never break. Empty tool: mining speed of a wooden pickaxe, no area mode, weapons deal 1 damage. Upgrades go through the smithing table: template = the tier's upgrade kit, base = previous tool, addition = tier core material. Smithing keeps data components, so energy, mode and toggles carry over.

Mining tools:
| Tool | Age | Energy | Modes | Notes |
|---|---|---|---|---|
| Tinker's Hammer | 0 | durability 600 | 1×1, 3×3 | 3×3 mines at 50% speed. Also makes plates. |
| Felling Axe | 0 | durability 500 | whole tree | 1 durability per log, max 64 logs |
| Bore Drill | 1 | 400k FE | 1×1, 3×3 | 40 FE per block |
| Chainsaw | 1 | 400k FE | whole tree | 30 FE per log, max 256 logs, toggle leaves, toggle replant |
| Servo Drill | 2 | 2M FE | + 5×5, vein mine (64) | |
| Magma Drill | 3 | 8M FE | + 3×3×3, 9×9 | auto-smelt toggle |
| Null Drill | 4 | 32M FE | + 5×5×5, 12×12, 12×12×12 | drops go to linked inventory |

3D modes (3×3×3 and up) are mid-to-endgame on purpose. Area breaks of more than 27 blocks run through a server-side break queue (max 64 blocks per tick per player, configurable) so large modes never freeze the server.

Toggles (stored per tool as data components, changed through a client → server payload):
- V cycles mode (only modes the tool has). Sneak and scroll also cycles.
- Holding sneak always mines 1×1.
- Keep floor: area modes never dig below the player's feet level.
- Auto-pickup: drops go directly to the player inventory.
- Void filter: delete items in `robotica:voidable`.
- Auto-smelt (Magma and Null).
- Silk / Fortune swap (Servo and up): one key (B).
- Light placer: right-click on a block face places a torch from the inventory.
- Area outline rendered client side before breaking.

Weapons:
| Weapon | Age | Energy | Behaviour |
|---|---|---|---|
| Gearblade | 0 | durability 400 | copper sword, 6 damage, fast swing |
| Shock Baton | 1 | 200k FE | 7 damage, Slowness II 2s, 250 FE per hit |
| Rivet Gun | 2 | 1M FE | right-click fires a fast arrow-like rivet, 8 damage, 4 shots/s, 400 FE per shot, no ammo |
| Arc Blade | 3 | 4M FE | 11 damage, arcs 50% damage to 3 nearby hostiles, 800 FE per hit |
| Null Lance | 4 | 16M FE | hold to charge 1s, beam pierces all mobs in 32 blocks for 30 damage, 20,000 FE per shot |

Weapon upgrade path through smithing: Gearblade → Shock Baton → Arc Blade. Rivet Gun → Null Lance.

## Base builder (module `architect`)

The player never crafts building blocks one by one. The Architect Table turns cheap bulk materials into **matter**, then fabricates Robotica building blocks out of matter + FE while it builds.

Matter grades (stored as numbers in the table, shown as three bars):
- Rustic matter: cobblestone/stone/deepslate/dirt/sand/gravel 1, planks 1, logs 4, cobbled anything 1.
- Refined matter: copper ingot 4, iron ingot 8, gold ingot 8, glass 2, bricks 2, quartz 4, clay ball 1.
- Exotic matter: obsidian 4, amethyst shard 4, prismarine crystals 4, glowstone dust 2, diamond 32, ender pearl 16.
Any item handler can feed the input slot (hoppers, RS exporters, Thermal servos).

Block styles, each a family of 6 roles (wall, floor, roof, pillar/trim, window, light):
| Style | Age | Matter per block | Look |
|---|---|---|---|
| Timberframe | 0 | 2 rustic | oak beams, plaster, lanterns |
| Copper Works | 1 | 2 rustic + 1 refined | riveted copper plates, bolted floor, amber lamps |
| Steel Lab | 2 | 1 rustic + 2 refined + small exotic | white steel panels, grated floor, framed glass, cyan light strips |
| Null Spire | 4 | 2 refined + 2 exotic | dark glossy panels, glowing teal seams, energy glass |
Higher styles need the matching age part in the table's style slot to unlock (Iron Casing, Reinforced Casing, Null Casing). All 24 blocks also have normal crafting recipes (8 bulk blocks + 1 tier material → 8), so players can repair or extend by hand.

Architect Table (Age 1, FE buffer 200k): 27-slot material input, style slot, 5×5 plot grid centred on the table's plot (each plot 9×9×6). The GUI shows the grid; click a plot, pick a module, confirm. Builds one block per N ticks (base 4 ticks, speed cards apply), 50 FE + matter per block. Optional "clear terrain" removes blocks in the footprint (no block entities, no unbreakables) and turns them into rustic matter. Missing matter pauses the build and the GUI says which grade is short.

Modules (procedural, built bottom-up, doors open automatically toward neighbouring modules): Corridor, Hall, Workshop, Storage Room, Machine Hall, Greenhouse (glass roof, farmland), Hangar (open roof pad), Stairwell (second floor later).

Builder drones: purely visual for now, a small flying entity that flies from the table to each placed block. Optional; the table works without them.

## Mob replicator (module `replicator`)

Mid-high tier, slow, powerful.
- Essence Vial (Age 2, consumable): right-click a hostile mob to take a sample (deals 2 damage, 3 s cooldown). The first sample binds the vial to that mob type. 8 samples completes it. Boss mobs and anything in `robotica:replicator_blacklist` cannot be sampled.
- Mob Replicator: 3×3×3 multiblock. Replicator Controller in the middle of one face, the rest Replicator Frame and Replicator Glass (at least 1 glass). The controller checks the structure every 40 ticks.
- Insert a complete vial, feed FE (base 256 FE/t). One cycle takes 1,200 ticks (1 min) at Age 2 speed. Speed cards (max level 3) and Plasma Actuator in the boost slot (×2) speed it up. Fortune cards act as Looting.
- Mode "Harvest" (default): rolls the mob's loot table as if a player killed it and puts drops in an 18-slot internal output. No entity is ever added to the world. A translucent hologram of the mob spins inside the frame and flashes on each cycle.
- Mode "Spawn": spawns the real mob in front of the controller instead (for your own mob grinders), max 8 nearby, then waits.
- Never duplicates boss drops, nether stars or anything in the blacklist tag.

## Warp (module `warp`)

Getting home and travelling between bases. All teleports run on the server, cost FE, and work for every player on a server.
- Warp Pad (Age 2 parts, FE buffer 1M): a block you name in a small GUI. Pads belong to their owner; the owner can mark a pad public so friends can use it. Stand on a pad and right-click it to open the destination list. Cost: 5,000 FE + 20 FE per block of distance, taken from the departure pad. Same dimension only, until the pad gets a Rift Upgrade (Age 3 parts + Magma Core) that unlocks cross-dimension travel for a flat 100,000 FE.
- Recall Remote (Age 1, 400k FE): sneak-right-click a Warp Pad to bind it. Hold right-click for 3 seconds (any damage cancels) to teleport to that pad. 20,000 FE, 30 s cooldown, same dimension. Smithing upgrade to Rift Remote (Age 3) works across dimensions for 150,000 FE.
- Portal Gate (Age 4): 4 wide × 5 tall frame of Gate Frame blocks with a Gate Controller in the bottom middle. Link two gates with a Linking Card (sneak-right-click controller A, then B). While powered (500 FE/t idle), the inside fills with a swirling portal block; players, mobs and items that walk in arrive at the other gate (10,000 FE per entity). Gates work across dimensions.
- The pad registry is a `SavedData` on the overworld, so pads keep working when their chunk is unloaded (the destination chunk is loaded on arrival).
- Safety: never teleport into solid blocks, look for the nearest safe 2-high spot within 3 blocks, otherwise refuse with a message and refund the FE.

## Later (not in this build)

Guard Drone, Wingman, Mole, Courier, Survey Rig, Exo-Frame armor, bosses, Magma Reactor, Ender conduit, RS API integration, Create compat.

## Engineering rules (all modules)

1. Dedicated server safe. Any class that touches `net.minecraft.client.*` or `com.mojang.blaze3d.*` lives in a `client` subpackage and is only reached from `RoboticaClient` (`@Mod(dist = Dist.CLIENT)`) or `@EventBusSubscriber(value = Dist.CLIENT)` classes. Never call `Minecraft.getInstance()` from common code. Item tooltips use `TooltipContext`, not the client player.
2. Server authoritative. Key presses send a payload; the server validates (item in hand, mode allowed for tier) and changes the item's data components.
3. Configs are `ModConfig.Type.SERVER` (synced, per world) for balance values. One config file per module: `robotica-<module>-server.toml`.
4. Each module registers its own `DeferredRegister`s and its own payloads. Never edit another module's files. Shared code lives in `com.arno.robotica.core` and is owned by the core.
5. Lang entries and tags go in `src/main/fragments/<module>/...` (same path layout as resources). Everything else (models, blockstates, textures, recipes, loot tables) goes in `src/main/resources` with unique file names.
6. Textures are generated by `scripts/textures/<module>.py` using `scripts/pixelart.py`. GUIs are drawn with `GuiGraphics.fill` through the core `MachineScreen` helpers, no GUI textures.
7. Block entities tick only on the server (`level.isClientSide` check in the ticker factory). Sync to client only what rendering needs.
8. Performance: area scans are spread over ticks (cursor-based), never full-area scans every tick.
