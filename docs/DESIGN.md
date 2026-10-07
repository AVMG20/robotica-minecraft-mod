# Robotica design spec

Source of truth for implementation. NeoForge 1.21.1, Java 21, mod id `robotica`, base package `com.arno.robotica`.

## Direction

- Start quickly, scale all the way to the late game. Faster than vanilla, on purpose. Built for modpack pace next to Refined Storage, Thermal, Mekanism.
- Starts on day one with wood, stone and copper. No iron needed for Age 0.
- The first iron hour gives power: a burner generator, a cheap Charger, the first battery (Copper Cell, needs redstone) and the first FE tools (Bore Drill, Chainsaw). No gold or diamonds for any of them.
- Diamonds open the Excavator (the real quarry) and Age 2. Later ages follow the component ladder.
- Cheap to start, expensive to upgrade. Base machines and tools are affordable. Tool tiers pull in later-age components; speed costs grow steeply in FE.
- Multiplayer first. Everything must run on a dedicated server. Server is authoritative, clients only render and send input.
- Forge Energy (FE) is the only energy unit. Items move through standard `IItemHandler` capabilities.
- A new player knows what to do without a wiki: guide advancements, a "Next steps" Codex page, chat tips, short tooltips with Shift details (see Onboarding).

## Ages

| Age | Name | Gate | Raw materials |
|---|---|---|---|
| 0 | Clockwork | none | wood, stone, copper |
| 1 | Wired | none | iron, redstone, gold |
| 2 | Servo | Servo Core | diamond, obsidian, quartz |
| 3 | Deep | Magma Core (+ Swarm, Tide later) | netherite scrap, blaze, prismarine |
| 4 | Antigrav | Antigrav Core | ender pearls, nether star, shulker shell |

The Servo Core drops from the Scrap Colossus (module `boss`, see Bosses). It keeps a pricier fallback recipe (`temp_servo_core`) for Peaceful worlds and servers that turn the Rusted Foundry off. The Magma and Antigrav Cores keep their temporary expensive recipe (marked `temp_` in the file name) until their bosses exist.

- Fairness rules: the Colossus only takes damage caused by a living attacker (no suffocation, cactus, drowning or dispenser cheese), it throws scrap at targets it can't reach in melee, its loot is locked to the killer for 2 minutes (or until they log off), and an altar that is cooling down can't be mined.

## Balance system: component ladder

Every age has three intermediate parts: a casing, a mechanism (moving part) and a circuit. Each age's parts consume several parts of the age before, so cost multiplies roughly 3-4x per age. Final items (machines, tools, robots, upgrade cards) are built from these parts, never mostly from raw materials.

Plates: `c:plates/iron` etc. Hand recipe: Tinker's Hammer + 2 ingots → 1 plate (hammer loses durability). Metal Press: 1 ingot → 1 plate. Any mod's plates with the common tag also work (Thermal, Mekanism, Immersive Engineering).

### Age 0 parts
- Copper Gear: 4 copper ingot (plus shape) + 1 cobblestone → 1
- Mainspring (Age 0 battery, 240,000 FE, wound at the Winding Crank): 8 copper ingot ring + 1 copper gear → 1
- Clockwork Mechanism: 4 copper gear + 4 planks + 1 cobblestone center → 1 (raw: 16 copper)
- Wooden Chassis: 4 logs + 4 cobblestone + 1 copper block → 1

### Age 1 parts
- Iron Plate (see plates)
- Copper Coil: 8 copper ingot + 1 iron ingot → 1
- Iron Casing: 8 iron ingot + 1 redstone → 1
- Basic Circuit: 3 redstone / copper ingot, gold ingot, copper ingot / 3 iron plate → 1
- Electric Motor: 4 iron plate + 2 copper coil + 2 redstone + 1 clockwork mechanism → 1
- Copper Cell (800,000 FE): 4 copper ingot + 2 iron plate + 3 redstone (first-iron battery, no gold)

### Age 2 parts
- Reinforced Casing: 4 iron casing + 4 obsidian + 1 diamond → 1 (raw: 32 iron)
- Advanced Circuit: 4 basic circuit + 2 gold ingot + 2 quartz + 1 diamond → 1
- Servo Actuator: 2 electric motor + 4 gold ingot + 2 iron plate + 1 advanced circuit → 1
- Redstone Cell (3,200,000 FE): 2 copper cell + 4 redstone block + 1 reinforced casing + 1 advanced circuit
- Servo Core: Scrap Colossus drop (fallback recipe: 4 diamond blocks + 4 servo actuator + 1 reinforced casing, about 830 IE)

### Age 3 parts
- Blazing Casing: 4 reinforced casing + 4 netherite scrap + 1 magma core → 2
- Quantum Circuit: 4 advanced circuit + 4 blaze rod + 1 netherite ingot → 1
- Plasma Actuator: 2 servo actuator + 4 prismarine crystals + 2 blaze powder + 1 quantum circuit → 1
- Magma Core: boss drop (temp: 4 servo actuator + 2 quantum circuit + 2 magma block + 1 netherite ingot)

### Age 4 parts
- Null Casing: 4 blazing casing + 4 shulker shell + 1 antigrav core → 2
- Null Circuit: 4 quantum circuit + 4 ender pearl + 1 nether star → 2
- Ender Cell (20,736,000 FE): 2 redstone cell + 2 null casing + 4 ender pearl + 1 null circuit
- Antigrav Core: boss drop (temp: 1 nether star + 4 plasma actuator + 4 end crystal)

Rough raw cost of one casing: Age 1 = 8 iron, Age 2 = 32 iron, Age 3 = 64 iron + netherite, Age 4 = 128 iron + shulkers. Final recipes at Age 3-4 typically need 2-4 of those parts each.

## Upgrade cards

One card item per kind: `robotica:upgrade_<kind>`. A machine has a few upgrade slots; each kind takes one slot. Stackable kinds stack in that slot and every card adds one step, up to the machine's cap. Silk and void are single cards; silk and fortune exclude each other.

| kind | card age | max stack | one step |
|---|---|---|---|
| `speed` | 1 | 8 | rate x2, x3, x4, x6, x8, x11, x15, x20 |
| `efficiency` | 1 | 4 | -15% energy per action (floor 40%) |
| `growth` | 1 | 4 | +50% crop/sapling growth |
| `void` | 1 | 1 | deletes `robotica:voidable` (also the void filter module of power tools) |
| `pickup` | 1 | 1 | tool module only: auto-pickup on power tools (Tinker's Bench) |
| `range` | 2 | 4 | machine specific (robots +2 radius, Excavator +10 wide, Wireless Charger +4 blocks) |
| `fortune` | 2 | 3 | Fortune I-III (Looting in the replicator) |
| `silk` | 2 | 1 | Silk Touch |

Energy per action with speed cards: normal machines `(1 + 0.25s + 0.05s^2)`, the Excavator and Survey Rig `(1 + 0.5s + 0.25s^2)` (x1.75, x3, x7, x21 per block for 1, 2, 4, 8 cards): every card costs more than the one before.

Caps per machine: Stumpy/Sprout speed, range and growth = Mk tier (1-4), efficiency 4. Excavator Mk1-4: speed 2/4/6/8, range and efficiency 1/2/3/4, fortune 1/2/3/3, silk, void (2/3/4/5 slots). Survey Rig Mk1-4: speed 2/4/6/8, efficiency 1/2/3/4, fortune 1/2/3/3, silk, void (1/2/3/4 slots). Wireless Charger: speed 4, range 4. Metal Press: speed 4, efficiency 4. Replicator: speed 3, fortune 3, efficiency 4. Architect Table: kind defaults.

API (`core.upgrade`): `new Upgrades(slots, Set<UpgradeKind>, onChanged)` (kinds capped at `maxStack`), `new Upgrades(slots, Map<UpgradeKind,Integer> caps, onChanged)` or `new Upgrades(slots, kinds, ToIntFunction<UpgradeKind> caps, onChanged)`; `level(kind)` returns the number of cards that count; `insertOne(stack, simulate)` for right-click insert; static `speedMultiplier`, `energyMultiplier`, `steepEnergyMultiplier`, `fortuneEnchantLevel`, `growthBonus`. `CoreItems.card(kind)` and `CoreItems.cards(kind, count)`; `card(kind, level)` is deprecated and returns the single card.

## Machines and power (module `power`)

- Winding Crank (Age 0): holds a Mainspring (or any FE item: cell, drill). Hold right-click with an empty hand: +400 FE per turn, 5 turns/s (100 FE/t, a bit more than a Combustion Generator but you have to stand there), a Mainspring is full in about 2 minutes. Only a Mainspring winds by hand; other FE items only charge from an FE source. Left-click pops the item out; sneak-right-click shows the charge, a second sneak-click within 2 s also takes it out. Accepts FE from any source at 200 FE/t. Mainsprings only charge here; the Mainspring tooltip shows how far it is wound in percent.
- Combustion Generator (first iron: copper shell, furnace, 1 iron ingot): burns furnace fuel, 80 FE/t (in line with other mods' coal generators), buffer 40,000. Right-click it with fuel.
- Solar Panel Mk1 (Age 1) 20 FE/t, Mk2 (Age 2) 80 FE/t daytime with sky access.
- Accumulator I/II/III: 1M / 4M / 16M FE, I/O 1,000 / 4,000 / 16,000 FE/t. The front gauge shows the charge: block state `charge` 0-5 (lit cells, 0 only when empty), checked every 10 ticks and set only when it changes (client update, no neighbour updates). A placed item shows its charge at once, the item model too (item property `robotica:charge`).
- Tesla Coils (wireless power, replaced the Copper/Gold Conduits): a torch-sized coil placed on floors, walls or ceilings (FACING, 6 ways) with a full-bright tip.
  - Tiers I-V: 4 / 8 / 12 / 16 / 32 links, range 8 / 12 / 16 / 24 / 32 blocks, 4,000 / 16,000 / 64,000 / 256,000 / 1,000,000 FE/t per coil (server config `teslaRate1-5`, `teslaRange1-5`). Tier I is a first-iron item (copper coil, iron plate, redstone; 6 IE, no gold) so the first generator can feed machines; every later tier consumes the one before plus that age's circuit and casing (II basic circuit + gold, III advanced circuit + Reinforced Casing, IV quantum circuit + Blazing Casing, V null circuit + Null Casing).
  - Source: a coil pulls from the block it sits on when that block gives FE out (through the touching face, else its internal storage): Accumulators, but also the Combustion Generator, Solar Panels and other mods' generators and batteries, so early players are not stuck before Accumulator I (Basic Circuit). Any other coil is a relay.
  - Tesla Linker (first iron): sneak-right-click a coil to select it, right-click a machine (any block with the FE capability on the clicked face; the clicked face is the side the power enters) or another coil. Clicking a linked block on the same face unlinks it, on another face moves the link there. Sneak-right-click the air clears the selection. The action bar shows links used / max. Every link, machine or coil, uses one slot. A coil cannot link the block it sits on, or a coil that already sends to it.
  - Flow: each tick a source coil asks its block for up to its rate and splits it fairly over its links (then offers the rest to machines that still take energy). A coil-to-coil hop forwards the share minus `teslaHopLoss` (5%) and the next coil splits it again; a visited set per push stops loops, so one push is O(links). Each coil sends at most its rate per tick in total. Only what was delivered leaves the source.
  - Links live in the coil's block entity (target pos + face, or coil pos) with the owner UUID. Only the owner, the owner's team or an operator may link; the target must be interactable (spawn protection) and in the same dimension and range. Unloaded targets are skipped, chunks are never loaded; links to blocks that are gone (no coil, no FE capability) are dropped every 2 s.
  - Right-click with an empty hand: status screen with links used / max, FE/t sent and whether it sends or relays. The client draws thin animated arcs from the tip to every target (bright while energy flows, faint when idle) and sparks; client config `teslaArcs` / `teslaParticles` turn them off. Charger hum and zap sounds every few seconds while active.
- Charger (first iron: copper, 1 iron plate, redstone): charges FE items, 2,000 FE/t (never more than the item takes), one slot. Right-click it with the item.
- Wireless Charger (Age 2: a Charger, a Tesla Coil II as emitter, 2 Advanced Circuits, 2 quartz, a Reinforced Casing): charges the FE items of players within 8 blocks at 1,000 FE/t each: worn armor first (the Exo-Frame), then the main and off hand, then the rest of the inventory. Only the owner and the owner's team (vanilla teams), config `wirelessChargeAnyone` for everyone. Every FE that reaches an item costs 10% more from its buffer (`wirelessLoss`). Range cards +4 blocks (4 cards: 24), speed cards x2/x3/x4/x6 rate. Buffer 200,000 FE, input 20,000 FE/t from cables or a Tesla Coil link. Players are scanned every 10 ticks; the list syncs to clients only when it changes. Lit while charging: animated front, a glowing ball on its mast, soft jagged arcs to every player it charges and sparks on them (client config `teslaArcs` / `teslaParticles`), a hum, a zap when charging starts. GUI: energy, range and rate, FE/t delivered, the nearest players with charging / full / not allowed / no power. Owner, team and operators open it.
- Item sides: the Charger and the Combustion Generator take the core side config (see Machine sides) like the Metal Press. Jade shows the Charger's status and item charge, the Wireless Charger's status and owner.
- Metal Press (Age 1): 1 ingot → 1 plate, 20 FE/t, 100 ticks. Right-click with ingots or upgrade cards.
- Cells in a player's inventory recharge the FE tool or weapon in their hands at the cell's output rate.

## Exo-Frame (module `exo`)

Powered armor: four pieces in four marks, modules that are real items installed in the pieces, a core socket for boss cores. The armor never breaks and keeps its base protection when empty; only the modules stop. Every number below is a server config value in `robotica-exo-server.toml` (energy values pass through the global energy multiplier, Solar Weave and the Kinetic Generator through the generation multiplier).

Marks (Mk2-Mk4 by smithing; smithing keeps energy, modules, switches and the core):
| Mark | Age | Recipe | Protection (boots/legs/chest/helmet) | Module slots | Battery |
|---|---|---|---|---|---|
| Mk1 | 1 | iron armor piece + Iron Casing, Electric Motors, Copper Coils | 2/5/6/2 | 1 | helmet 200k, chest 1M, legs 400k, boots 200k |
| Mk2 | 2 | template Reinforced Casing + Mk1 + Servo Actuator | 3/6/8/3, toughness 2 | 2 | x4 |
| Mk3 | 3 | template Blazing Casing + Mk2 + Superconductor Coil (Assembler) | 3/6/8/3, toughness 3, knockback resistance 0.1 | 3 | x16 |
| Mk4 | 4 | template Null Casing + Mk3 + Resonant Lattice (Assembler) | 4/7/9/4, toughness 4, knockback resistance 0.2, does not burn | 4 | x64 |

Raw cost per full set: Mk1 about 225 IE, Mk2 840, Mk3 5,450, Mk4 17,700. Protection values live in the armor materials (registry data), the batteries in the config (`capacity*`, `markCapacityMultiplier`).

Energy: with all four pieces worn every module draws from all four batteries (most charged first); a partial set draws from the module's own piece. Cells and Mainsprings in the inventory top the suit up (2,000 FE/s for all cells together). Other mods' chargers fill the pieces at any rate.

Modules: one item per kind and level. A kind fits certain pieces and needs a minimum mark per level; the module screen refuses anything else and says why (wrong piece, mark too low, already in the suit). A kind works once per suit (the highest switched-on level counts if two get in anyway); Capacitor Plating is the exception, it works per piece. Level upgrades are crafted from the level below plus the next age's circuits (Age 2 Advanced, Age 3 Quantum, Age 4 Null Circuits).
| Module | Piece | Levels: min. mark | Effect | Energy (I / II / III) |
|---|---|---|---|---|
| Night Vision | helmet | Mk1 / Mk2 / Mk3 | night vision; II and III thermal sight: hostile mobs within 24 / 48 blocks show through walls (client outline) | 10 / 20 / 30 FE/s |
| Rebreather | helmet | Mk2 | breathe under water | 60 FE/s submerged |
| Robot HUD | helmet | Mk2 | nearby drones on the HUD (distance, health) | 5 FE/s |
| Auto-Feeder | helmet | Mk1 | eats the best fitting plain food (no effects) at food level 14 or less | 500 FE per food |
| Solar Weave | helmet | Mk2 | charges the suit by day under open sky, not in rain | makes 200 FE/s |
| Sonar Pulse | helmet | Mk2 / Mk3 / Mk4 | key N: ores (`c:ores`) and mobs within 16 / 24 / 32 blocks are outlined for 10 s; the client scans its loaded sections, the server charges and cools down (5 s) | 4,000 / 6,000 / 8,000 per ping |
| Jet Assist | chest | Mk2 / Mk3 / Mk4 | slows long falls; 1 / 2 / 3 extra jumps in the air | 40 / 30 / 20 FE/s gliding, 400 / 350 / 300 per jump |
| Flight | chest | Mk3 | creative flight, K toggles | 2,400 FE/s while flying |
| Kinetic Shield | chest | Mk3 / Mk4 / Mk4 | absorbs 75 / 85 / 95 % of each hit with FE | 2,000 / 1,600 / 1,200 FE per point |
| Med Injector | chest | Mk2 / Mk3 / Mk4 | at 40 % health heals 2 / 3 / 4 hearts, cooldown 60 / 45 / 30 s | 20,000 / 30,000 / 40,000 per shot |
| Hazard Seal | chest | Mk3 | clears poison, wither, hunger, nausea, blindness | 2,000 per effect |
| Servo Stride | legs | Mk1 / Mk2 / Mk3 | +20 / 40 / 60 % speed | 30 / 60 / 100 FE/s moving |
| Kinetic Generator | legs | Mk1 | walking charges the suit | makes 5 FE per block (8 sprinting) |
| Dash Thrusters | legs or boots | Mk3 | key R: horizontal burst where you look (1.6 blocks/tick), cooldown 3 s | 3,000 per dash |
| Step Assist | boots | Mk1 | step height 1.0: walk up full blocks (Age 1, about 12 IE) | 10 FE/s moving |
| Spring Heels | boots | Mk1 / Mk2 / Mk3 | jump strength +0.15 / 0.25 / 0.35 | 100 / 150 / 200 per jump |
| Fall Dampener | boots | Mk1 / Mk2 / Mk3 | absorbs fall damage beyond 3 blocks | 150 / 100 / 60 per block |
| Magnet | boots | Mk1 / Mk2 / Mk3 | pulls items within 6 / 10 / 16 blocks | 40 / 60 / 80 FE/s pulling |
| Hydro Fins | boots | Mk2 | swim speed +50 %, full mining speed under water | 20 FE/s in water |
| Capacitor Plating | any | Mk1 / Mk2 / Mk3 | +50 / 100 / 200 % battery for its piece (removing it caps the stored energy) | none |
| Power Regulator | any | Mk2 / Mk3 / Mk4 | every module in the suit uses 10 / 20 / 30 % less FE | none |

Core socket: chestplates from Mk2 on hold one Servo, Magma or Antigrav Core (never consumed, removable, kept through smithing). The bonus needs all four pieces worn at Mk2 or better (`setBonusMinMark`):
- Servo Core, Overclock: key O gives Haste II and Speed I for 10 s, 60 s cooldown, 50,000 FE.
- Magma Core: fire immunity (ambient Fire Resistance, fire damage ignored), melee hits set the target on fire for 4 s, lava does not slow you (client movement).
- Antigrav Core: no fall damage, Flight costs half, Dash cooldown halved.

Screen and controls: J opens the module screen for all four worn pieces (rows; absent pieces show an empty row), sneak + right-click a piece in hand opens that piece alone. Per row: the piece, its battery, four slot positions (slots the mark does not have yet are locked and say which mark opens them), a switch and level pips per module, the core socket on the chestplate; the pooled suit battery on the right and the set bonus next to the title. Tooltips give cost, requirement and whether a module is shadowed by a duplicate. Switches are menu buttons validated on the server. Keys: J screen, K flight, R dash, N sonar, O overclock (no clash with V, B, G, H or vanilla defaults); a second jump in mid air is the Jet Assist double jump.

HUD (client config `robotica-exo-client.toml`: on/off, corner, offsets, outlines, flight sound): suit energy bar, one icon per installed module (dimmed when off, empty or a duplicate), cooldown rings (Dash, Med Injector, Sonar, Overclock; vanilla item cooldowns, so they sync by themselves), sonar results, the Robot HUD's drone list.

Juice: jet glide clouds, flight flames and smoke plus a client thruster loop, shield sparks and zap, dash whoosh (wind burst) and puff, sonar chime, injector hiss and hearts, hazard seal fizz, overclock spark burst. Only vanilla sounds and the core sound events.

Engineering: per-player runtime state keyed by UUID and dropped on logout, death and when the suit comes off; costs are booked per piece and written to the items every 10 ticks; attribute modifiers are transient, our mob effects ambient and hidden, flight is a granted `mayfly` that is revoked again (never in creative or spectator).

## Industry (module `industry`)

The resource and machine layer between the ladder parts and big power. Power is the currency: every machine eats FE and takes speed / efficiency cards (`Upgrades.energyMultiplier`). Modpack rule: Robotica does not compete on generic ore processing. Ores, raw materials and ingots are ground by the Grinder of the `processing` module purely by `c:` tags; this module ships only its special grinding outputs (`robotica:grinding`: coal to graphite dust, pyrolite and resonite gems to dust). Every material carries its `c:` tags (`ores/`, `ores_in_ground/`, `ore_rates/singular`, `raw_materials/`, `ingots/`, `dusts/`, `plates/`, `gems/`, `storage_blocks/`), so other mods' machines take them and their iron dust works in our recipes. The unique pull is the Robotica alloys (only the Alloy Smelter makes them) and the Assembler-only parts.

Ores and materials (worldgen is data, written by `scripts/data/industry_worldgen.py`: configured + placed features and NeoForge biome modifiers; a pack switches a deposit off by overriding its biome modifier with `neoforge:none`):
| Ore | Where | Vein | Drops | Materials |
|---|---|---|---|---|
| Thorium (stone, deepslate) | Overworld, trapezoid Y -48..32, 9 per chunk | 8 | Raw Thorium (fortune) | raw block, ingot, block, dust, plate |
| Pyrolite | Nether netherrack Y 10..117, 10 per chunk, +8 in basalt deltas (also in basalt and blackstone) | 9 | Pyrolite Shard, 2-5 XP, iron pickaxe | dust, block |
| Resonite | outer End islands (highlands, midlands, barrens) end stone Y 16..80, 5 per chunk, rare | 5 | Resonite Crystal, 3-7 XP, diamond pickaxe | dust, block |
- Raw thorium, thorium dust and the ores smelt or blast into ingots; the ores' gems smelt out too. A Tinker's Hammer in the crafting grid cracks raw thorium, a shard, a crystal or coal into one dust (no gain; the Grinder doubles). Graphite Dust is ground coal or charcoal.
- Survey Rig, Excavator and the Grinder pick the ores up through `c:ores`.

Alloys (Alloy Smelter only; plates through the Metal Press, 1 ingot, or the hammer, 2 ingots):
- Ferrothorium (Age 2): iron ingot or dust + thorium ingot or dust, 200 ticks at 40 FE/t.
- Pyrosteel (Age 3): ferrothorium + pyrolite shard or dust + blaze powder, 300 ticks at 120 FE/t.
- Resonant Alloy (Age 4): pyrosteel + resonite crystal or dust + ender pearl, 400 ticks at 400 FE/t.

Processing machines: Alloy Smelter (Mk1 Age 1; 3 inputs, 1 output), Centrifuge (Mk1 Age 2; 1 input, 4 outputs with chances), Assembler (Mk1 Age 2; 6 inputs with counts, 1 output). One block entity class (`ProcessingBlockEntity`), recipe types `robotica:alloying`, `robotica:centrifuging`, `robotica:assembling` (`ProcessingRecipe`: `inputs` = list of `{ingredient, count}`, shapeless, one ingredient per input slot and no stray items; `results` = list of `{id, count, chance}`; `time` ticks; optional `power` FE/t, else the machine's config value). JEI: one category each, all Mks as catalysts (`industry/client/jei`).
- Every machine: FE buffer 200k (input 20k FE/t), battery slot (cell or Mainspring), progress arrow, status and live FE/t readout. Pipes and hoppers on every face insert into the inputs (only items some recipe uses, one item kind per input slot so a hopper of plates cannot clog a multi-input recipe) and extract from the outputs. Right-click with an input, battery or card puts it in.
- Cost per tick = recipe power x Mk speed x speed card multiplier x `energyMultiplier(speed, efficiency)` x (1 + 0.25 per fortune card, only on recipes where fortune works: alloying by default, recipe field `fortune`); time = recipe time / (Mk speed x speed card multiplier). Progress is counted in FE (power x ticks per craft): a machine draws at most what its buffer and input can sustain per tick (both scale x1 to x4 with Mk), and a craft above that takes longer instead of stalling, so FE per craft never changes. So FE per craft stays flat over the Mks and only the cards' penalty costs more.
- Mk1-Mk4: speed x1 / x2 / x3 / x5 (config `tierSpeedMk1-4`), card slots 2 / 3 / 4 / 5, caps speed 2 / 4 / 6 / 8, efficiency 1 / 2 / 3 / 4, fortune 0 / 1 / 2 / 3 (each card: 10% chance of one more main result, config `fortuneBonus`), void 1 (Centrifuge only: chance results that do not fit vanish instead of stalling). Each Mk is one age later than the one before (capped at Age 4) and is crafted from the previous Mk + 5 plates of that age's alloy + that age's circuit + its Assembler-only part (Age 2 Thermocouple, Age 3 Superconductor Coil, Age 4 Resonant Lattice). Right-clicking a placed machine with the next Mk swaps it in place, keeping inputs, outputs, battery, cards, energy and progress, and gives the old Mk back.
- Centrifuge recipes: Depleted Fuel Pellet -> Radiant Isotope (10%) + Thorium Dust + Graphite Dust (50%); magma cream -> slime ball + blaze powder; pyrolite dust -> 2 glowstone dust + blaze powder (25%); glistering melon slice -> melon slice + 4 gold nuggets.
- Assembler-only parts: Thermocouple (Age 2: 2 thorium plates, 2 copper plates, Basic Circuit), Superconductor Coil (Age 3: 4 pyrosteel plates, 2 copper coils, 2 graphite dust, Quantum Circuit; 400 FE/t), Resonant Lattice (Age 4: 4 resonant alloy plates, 2 resonite crystals, Superconductor Coil, Null Circuit; 1,500 FE/t). The Assembler also makes the ladder parts for about a quarter less raw material than the crafting table (Basic and Advanced Circuit, Electric Motor, Servo Actuator, Reinforced Casing, Quantum Circuit, Plasma Actuator, Blazing Casing, Null Circuit, Null Casing), the later ones with the alloy plates as the sink.

Fuel (Assembler): Thorium Fuel Pellet (Age 2: 2 thorium dust, graphite dust, ferrothorium plate), Enriched Fuel Pellet (Age 3: pellet, pyrolite, magma cream), Fusion Fuel Pellet (Age 4: 2 resonite dust, Radiant Isotope). Burned pellets leave a Depleted Fuel Pellet (waste, only from burning; the Centrifuge recycles it). Reactor stats live in the item data maps of the `energy` module: `data/robotica/data_maps/item/reactor_fuel.json` (thorium heat 400 / 12,000 ticks, enriched heat 1,000 / 12,000 ticks, both waste `robotica:depleted_fuel_pellet`) and `fusion_fuel.json` (fusion pellet 200,000 FE/t / 2,400 ticks).

Radioisotope Generator `rtg` (Age 2: 4 ferrothorium plates, 2 Thermocouples, Reinforced Casing, glass): a single block that burns `#robotica:rtg_fuel` (Thorium Fuel Pellets) one at a time, 150 FE/t for 24,000 ticks (3.6M FE per pellet; a reactor gets more out of one), pausing while its 100k buffer is full or the waste slot has no room. Quiet. Pushes up to 1,000 FE/t into neighbours, Tesla Coils on it send it on. More power = more RTGs. Config `rtgPower`, `rtgPelletTicks`, `rtgBuffer`, `rtgOutput`.

Generator cards (power module): the Combustion Generator takes up to 3 speed cards (FE/t x2 / x3 / x4, fuel burns `speedMultiplier x energyMultiplier(speed, 0)` times as fast, so FE per fuel item drops to 45% at 3 cards) and up to 4 efficiency cards (+20% FE per fuel item each, the burn slows). Solar Panels take up to 4 speed cards (+25% daylight output each, `solarSpeedBonus`) and 4 efficiency cards (10% of the daylight output at night under open sky each, `solarNightPerCard`); both have a GUI with two card slots now.

Server config `robotica-industry-server.toml`: `alloySmelterPower` 40, `centrifugePower` 60, `assemblerPower` 80, `machineBuffer`, `machineInput`, `tierSpeedMk1-4`, `fortuneBonus`, and the RTG values. Guide steps: Green Glow (thorium, after Wired), Alloyed, Machine Made (Assembler, after Servo Age), Slow Burn (RTG), Fire Stone (pyrolite, after Deep Age), Echoes (resonite, after Antigrav Age). Codex chapter "Industry".

Balance (IE, `docs/COSTS.md`): Alloy Smelter Mk1 25, Centrifuge Mk1 113, Assembler Mk1 204, RTG 83 (150 FE/t vs the Combustion Generator's 80 for 6.4), Mk2 / Mk3 / Mk4 of the Alloy Smelter 102 / 751 / 2.6k, of the Assembler 853 / 2.7k / 4.5k. A Thorium Fuel Pellet costs about 6 IE for 3.6M FE in an RTG (28 coal). Raw weights: thorium 1.5, pyrolite 3, resonite 10, radiant isotope 40.

## Big energy (module `energy`)

Multiblock power for packs next to Mekanism, Thermal and Immersive Engineering: a Capacitor Bank (Age 1 on), a Fission Reactor (Age 2-3) and a Fusion Reactor (Age 4). Everything moves FE through the standard capability, so other mods' cables, generators and consumers, and our Tesla Coils, work with every port. Server config `robotica-energy-server.toml` holds every number below.

**Multiblock framework** (`core.multiblock`, reusable): a variable-size cuboid found from its controller.
- `CuboidSpec`: frame rule (12 edges, 8 corners), wall rule (faces), controller rule, min/max width (both horizontal axes) and height as suppliers (config). A fixed shape is min == max.
- `CuboidScanner.scan(level, controller, outward, spec, visitorFactory)`: the controller sits in a side wall, screen facing out (placed facing the player; the scan also tries the opposite and both sideways directions and turns the controller to the face it forms with). Walks along the front wall over shell blocks for width and height, back along a side wall for depth; each walk stops at the frame edge (the first block with shell right behind it), so structures built in a row or decoration touching a corner do not matter. A walk that leaves the build height is an error of its own. Then it checks every position: frame, walls (the visitor collects ports), interior (`CuboidVisitor.interior`), whole-structure rules (`finish`). The first failing position is the result's `StructureProblem` (a translatable sentence naming the block and its x, y, z). A gap or a stranger in the walked line is named directly instead of a size error; too small/large, a second controller, a hole, glass on an edge each have their own message.
- `MultiblockWatcher`: a controller registers its box (or its search area while unformed); any block change with a neighbour update inside marks it dirty, it re-scans on its next tick at most every `multiblockScanCooldown` (10) ticks. A slow periodic re-scan (`multiblockRescanInterval`, 200 ticks) catches changes without updates. One scan reads at most 729 blocks. Watches are dropped when a level unloads or the server stops.
- GUI: every controller shows "Formed WxHxD" or the problem (position in the tooltip); the spyglass button closes the GUI and outlines the structure box (cyan) and the wrong block (pulsing red) for 12 s, or the smallest legal shape behind the controller (amber) when nothing was found yet. The numbers reach the GUI as one small tag (`robotica:energy_controller_sync`) a few times a second, only to players with it open.
- The controller links the ports it finds on each scan. A port saves its link (so it works right after its chunk reloads) but only trusts it while that controller is formed and lists the port, and asks the controller for a re-scan when it loads. A port's FE / item capability objects are fixed and forward to the controller (nothing moves while unlinked, no capability invalidation). Ports open the controller GUI on right-click (16 block reach for big structures).
- Jade: a controller shows working while it glows (bank flow, reactor heat, lit plasma) or idle, its owner, the bank's fill and the fusion ignition charge as a bar. The fission controller plays the machine start / stop sounds when its glow turns on or off (at most every 2 s).

**Capacitor Bank** (3x3x3 to 9x9x9): Bank Casing frame; walls of casing, Bank Glass, Bank Ports; the Capacitor Bank Controller in a side wall. Inside: any mix of Capacitors and Transfer Coils, the rest air; at least one of each and one port.
- Capacitors: Copper 8M (4 Copper Cells, Age 1), Redstone 64M (2 Redstone Cells + the Copper one, Age 2), Ender 512M (Ender Cell + the Redstone one, Age 4), Resonant 4G (resonant alloy plates, resonite, a Resonant Lattice + the Ender one, Age 4). Transfer Coils: Basic 64k, Advanced 512k, Elite 4M FE/t (Age 1/2/3, each consumes the one before). The sum of the coils limits input and, separately, output per tick, shared by all ports. A 9x9x9 of Ender Capacitors holds about 175G FE.
- Energy is a long in the controller (capacity sums saturate); ports clamp every transfer to an int. Beyond an int a port reports the int maximum as capacity and the stored energy scaled by the same factor, and never reports a bank that is not full as full. A Bank Port is input or output (sneak + right-click, the face shows an arrow). Output ports push into the block outside them and can be pulled from (a Tesla Coil on top works).
- The energy stays in the controller when the structure breaks, and travels with the controller item (`robotica:bank_energy`, a long; the controller always drops, explosions included). Formed smaller than its energy (capacitors removed, or a full controller in a small bank), the energy above the new capacity is lost.
- GUI: big gauge, stored / capacity and percent, average in / out FE/t over the last second, a 60 s sparkline of the net flow per second (green up = charging, red down = draining), the rate limit and part count, structure line.

**Fission Reactor** (5x5x5 to 7x7x7, `reactorMinSize`/`reactorMaxSize`; a 3x3x3 would have no room for coolant next to a rod, so about 100 FE/t at best): Reactor Casing frame; walls of casing, Reactor Glass, Reactor Power Ports (FE out, at least one) and Reactor Access Ports (fuel in on slots 0-2, waste out on slots 3-5; hoppers and pipes welcome); the Fission Reactor Controller in a side wall (3 fuel slots, 3 waste slots). Inside: Fuel Rods in full vertical columns (floor to roof), the rest air or coolant.
- Fuel: item data map `robotica:reactor_fuel` (filled by the industry module: thorium pellet heat 400 / 12,000 ticks, enriched pellet heat 1,000 / 12,000 ticks, both waste `robotica:depleted_fuel_pellet`). Coolant: block data map `robotica:reactor_coolant`: water 1, ice 1.5, packed ice 2, blue ice 3, Cryo Coolant 4 (Age 3 block).
- Heat model: n rods count as `n^0.8` effective rods (`reactorRodExponent`). One fuel unit burns in one effective rod for its ticks; the controller burns one unit at a time, `ticks / effective rods` game ticks per pellet. Heat `H = fuel heat x effective rods x (1 - rod insertion)`. Coolant points per rod = the coolant values of its 4 horizontal neighbours; cooling `C = effective rods x (100 + 250 x average points)`; efficiency `1 + 0.05 x average points`. Output `min(H, C) x efficiency x throttle` FE/t (times the global generation multiplier).
- Temperature moves towards `20 + 980 x H / C` C (2% of the gap per tick). Up to 1,000 C (`reactorSafeTemp`) full output; above it the output throttles linearly to 25% at 1,800 C (`reactorScramTemp`), where it SCRAMs: rods in, no heat, it cools down and waits for Reset in the GUI (below 200 C). No explosion, no block changes, ever.
- Fuel burns only as far as the 5M FE buffer (`reactorBuffer`) has room: with room for less than one tick's output it burns just that share of fuel (and makes that share of heat), full it burns nothing. The next pellet's waste must fit. The controller's glow stays on for 2 s after the last heat, so a brim-full buffer does not flicker it. Power Ports push into the block outside them and can be pulled from (Tesla Coils on top).
- Scale: a 5x5x5 with one column of 3 thorium rods and 4 water around each makes about 1,150 FE/t (packed ice 1,350). A 7x7x7 with 13 columns of enriched fuel in a Cryo Coolant checkerboard makes about 45,000 FE/t at 32% load. Water-only big reactors run hot and need some rods in.
- Control rods 0-100% from the GUI slider (drag, click or scroll). GUI: temperature gauge (safe and SCRAM marks), FE buffer, FE/t, heat vs cooling, rods / coolant / efficiency line, the burn bar, status (running, running hot, no fuel, waste full, buffer full, rods in, SCRAM) and the structure line.

**Fusion Reactor** (Age 4, fixed 7x3x7): Fusion Casing frame; walls of Fusion Casing, Reactor Glass and Reactor Power / Access Ports (Access Ports only take fuel in); the Fusion Reactor Controller in a side wall. Inside (one layer): a ring of 16 Fusion Coils around an empty 3x3 plasma chamber.
- Ignition: Power Ports take FE in (at most 1M FE/t in total, `fusionChargeRate`) until the 20M FE ignition charge (`fusionIgnitionEnergy`) is full: a deliberate sink, a Capacitor Bank or Tesla Coils deliver it. With fusion fuel in the controller (item data map `robotica:fusion_fuel`, fusion pellet 200,000 FE/t for 2,400 ticks) and the reactor switched on, the charge is spent and the plasma lights.
- Output ramps from 0 to the fuel's power over 10 s (`fusionWarmupTicks`). The plasma can not throttle: it burns fuel whether or not the power is taken (20M buffer, `fusionBuffer`). Without fuel it survives 5 s (`fusionStarveTicks`), then collapses and needs a new charge; switching off or breaking the structure collapses it too. No explosions.
- GUI: ignition charge bar, plasma bar, FE/t and buffer, fuel slots with burn bar, On/Off, status and structure line.

**Data maps** (registered by this module, synced, optional on clients):
- `robotica:reactor_fuel` (items): `{"heat": int, "ticks": int, "waste": "<item id>"}` (waste optional); `data/<ns>/data_maps/item/reactor_fuel.json`.
- `robotica:fusion_fuel` (items): `{"power": int, "ticks": int}`; `.../item/fusion_fuel.json`.
- `robotica:reactor_coolant` (blocks): `{"cooling": float}`; `.../block/reactor_coolant.json` (shipped here).

Recipes use the ladder parts: Bank Casing (iron plates, copper, Iron Casing, x8), Reactor Casing (ferrothorium plates, obsidian, Reinforced Casing, x8), Fuel Rods (thorium plates, glass, graphite dust), Fusion Casing (Reactor Casing, resonant alloy plates, Blazing Casing, x8), Fusion Coils and Elite Transfer Coils (Superconductor Coils); controllers take that age's circuit and casing; Fusion Coils a Null Circuit and Plasma Actuators. Guide steps: "Bank on It" (bank formed), "Split the Atom" (reactor formed), "Star in a Jar" (fusion ignited).

## Ore processing (module `processing`)

Classic ore doubling, on by default and fully configurable (`robotica-processing-server.toml`). Two machine lines in four Mk tiers; every number below is a server config default.

- Tiers: Mk1 (Age 1, unlocked with the Basic Circuit) to Mk4 (Age 4). Mk2-4 are crafted around the previous Mk plus two casings, a circuit and two age materials (grinder: diamonds, netherite scrap, shulker shells; furnace: obsidian, blaze rods, ender pearls). The recipe type `robotica:machine_upgrade` is shaped crafting that keeps the machine's stored energy. Right-clicking a placed machine with the next Mk swaps it in place like the industry machines: inputs, outputs, media, battery, cards, side config, energy, progress and stored experience stay, the old Mk goes back to the player (adventure mode and protected spots may not). Per Mk: speed x1 / x1.5 / x2 / x3 (FE/t rises with it, FE per item stays), upgrade slots 2 / 3 / 4 / 5, Speed cap 2 / 4 / 6 / 8, Efficiency cap 2 / 3 / 4 / 4, Fortune cap 0 / 1 / 2 / 3, Range cap 0 / 1 / 2 / 3, buffer 20k / 80k / 320k / 1.28M FE, input 4k / 16k / 64k / 256k FE/t.
- Both machines: battery slot, upgrade slots (five internal slots, the Mk opens 2-5), status for the GUI and Jade. Breaking one (any tool, explosion, creative) spills everything inside like a furnace; only the stored energy stays in the dropped item. No Carry card. Pipes and hoppers insert on the top and sides and extract outputs (and empty batteries) from any side, a null side gets the side rules; the bottom only extracts (the default side config, see Machine sides). Right-click quick-inserts inputs, media and batteries (cells, Mainspring), never a charged tool. The machine stays lit for 1.5 s after work stops, so weak power does not flicker it. Cards or furnace lanes left beyond a lowered `upgradeSlotsMk` / `lanesMk` move to open slots or pop out on top. Card tooltips in the GUI say what each card does in that machine.
- Grinder: 200 ticks and 20 FE/t at Mk1 (4,000 FE per ore, like Thermal's Pulverizer). Generic by `c:` tags, so every mod's ores work: `c:ores/<m>` -> 2 `c:dusts/<m>` (`oreDustCount`, 1 turns doubling off), ores with a `c:gems/<m>` tag -> 2 gems (`gemOreCount`; gems win over a dust, so diamond ore stays diamonds with Mekanism or Thermal installed), `c:raw_materials/<m>` -> 1 dust + 25% one more, `c:ingots/<m>` -> 1 dust. The output is picked from the dust tag: Robotica first, then Minecraft, then alphabetical. `robotica:grinding_blacklist` excludes items. Ancient debris has no rule and does not grind.
- `robotica:grinding` recipes beat the tag rules: `{"type": "robotica:grinding", "ingredient": <Ingredient>, "result": <ItemStack>, "extras": [{"result": <ItemStack>, "chance": float}], "min_tier": int}` (extras, min_tier, `time` optional). `"by_tag": true` marks a recipe that only documents a tag rule (our dusts, for recipe lists and the Codex); the Grinder then uses the tag rule. Shipped: cobblestone -> gravel -> sand (+10% flint), blaze rod -> 3 powder +50%, bone -> 6 bone meal, glowstone -> 4 dust, wool -> 4 string, redstone ore -> 6 redstone, lapis ore -> 8 lapis, coal ore -> 2 coal +25%.
- Dusts: iron, gold and copper dust (`c:dusts/<metal>`), smelt and blast into ingots for 0.1 experience (an ingot grinds back into dust, so more would be an experience farm).
- Grinding media (optional second input, item data map `robotica:grinding_media`, `{"bonus", "secondary", "uses", "tier"}`): the bonus only on inputs in `c:ores` or `c:raw_materials`, not on gem ores (a silk-touched diamond ore is always 2 diamonds) and never on ingots (no dust loop). One media item is loaded into the Grinder straight away (it leaves the slot) and lasts `uses` ores, shown by the media bar; when it runs out the next item is loaded. The slot holds plain stackable items the player can add or take any time. A loaded item only wears when it does something (bonus or a possible byproduct); an untouched loaded item drops when the Grinder breaks, a worn one is gone. Old stacks with the legacy `robotica:media_wear` component load with what they had left and the rest stacks again. Flint +10% / 2% / 8 ores / any Mk; Iron Grinding Balls +25% / 5% / 16 / Mk1; Ferrothorium +50% / 10% / 32 / Mk2; Pyrosteel +100% / 20% / 64 / Mk3; Resonant +150% / 25% / 128 / Mk4. Bonus multiplies the main output (fractions rolled), secondary is the chance of a byproduct per ore from the item data map `robotica:grinding_byproducts` (ordered list of ids or #tags, first that exists; e.g. iron -> nickel, tin, gold), else, for metal ores and raw ores only (material with a `c:ingots/<m>` tag), a random dust from `fallbackByproducts`; gem, coal, redstone and lapis ores get a byproduct only from an entry. The Grinder only finishes an item when its best possible roll (most main output, every extra, any byproduct) fits the output, so a full output waits instead of re-rolling. A media above the Grinder's Mk does not fit. Alloy balls: 4 alloy ingots (`#c:ingots/ferrothorium`, `pyrosteel`, `resonant_alloy`) + 1 ball of the tier below -> 4; iron balls: ingot + 4 nuggets -> 2.
- Grinder cards: Speed, Efficiency, Fortune (+10% main output per card on ores and raw ores, not gem ores), Void (extra outputs that do not fit are deleted instead of stalling).
- Electric Furnace: every vanilla `smelting` recipe at half its cooking time (`furnaceTimeFactor`), 20 FE/t per lane and item at Mk1 (2,000 FE per item). Lanes in parallel 1 / 2 / 4 / 8 (own input, output and progress each). The batch is fixed when a lane starts a cycle (topping it up late smelts nothing extra), and FE per tick is power x batch. Cards: Speed, Efficiency, Range (+1 item per lane and cycle), Fortune (+50% experience, never for inputs in `c:dusts`). Experience is stored (cap 5,000) and paid out when a player takes the output by hand, or when the furnace breaks.
- JEI (optional, `processing/client/jei`): a Grinder category with the recipes and every tag rule of the pack, the Electric Furnaces as smelting catalysts, an info page per media item.

## Automation (module `automation`)

Area workers are block entities, never mobs. All of them: battery slot (cell or Mainspring), upgrade slots, output to inventories on any adjacent side (chest, RS Interface, AE2 interface, Supply Crate), internal 9-slot buffer when outputs are full, work stops when full or out of energy. Idle drain 0.2 FE/t (rounded: 1 FE every 5 ticks).

Usability: right-click with a battery swaps it in, with a card installs one card. Sneak-right-click with an empty hand shows a status line and the work area. The work area outline shows for 10 s after placing, upgrading or a Mk kit. A stalled robot puffs smoke (no energy) or shows a sign (output full) every 2 s and tells its owner once on the action bar (when within 32 blocks).

- Stumpy (lumber bot, Age 0). Mk1 7×7 (one block less per side than Sprout), rests 10 ticks per log after each tree, 1 action / 40 ticks, growth ×1.5, 4 FE/t while working plus 250 FE per log felled (a tree never costs more than half the 20k buffer; a Mainspring is roughly 850 logs). Fells a whole tree as one action (up to 256 logs, always with its natural leaves), replants saplings from its buffer, collects item drops in the area.
- Sprout (crop bot, Age 0). Harvests mature crops (`CropBlock`, nether wart, sweet berries, cocoa), replants, tills dirt with water nearby. 3 FE/t.
- Farm tiers via Mk kits used on the placed bot: Mk2 (Age 1 parts) 13×13, every 20 ticks, growth ×2; Mk3 (Age 2) 17×17, 5 ticks, ×3; Mk4 (Age 4) 25×25, 1 tick, ×5. Each Mk raises the speed/range/growth card cap by one. Range cards add +2 radius each.
- Growth boost is applied as extra random ticks on crops/saplings in the area (cheap: N random positions per second, not every block).
- Supply Crate (Age 0): 27 slots, plain inventory with item capability.
- Excavator (Age 2: a diamond pickaxe as drill head, plus Electric Motors, Iron Casing, Basic Circuit): mines a real hole below itself. 40 FE per block, stops at bedrock and leaves fluids alone (replaces fluid source blocks with cobblestone as it goes, so no flooding). Speed cards at a steep FE price (see Upgrade cards), range cards +10 wide each, efficiency, fortune, silk, void. Never breaks blocks with an unbreakable hardness or block entities. The GUI shows depth, percent dug and a progress bar.
  - Mk1-4 (one block each; Mk2 Age 2: Advanced Circuit, 2 Servo Actuators, Reinforced Casing; Mk3: Quantum Circuit, 2 Plasma Actuators, Blazing Casing; Mk4: Null Circuit, 2 Plasma Actuators, 2 ender pearls, Null Casing; each consumes the Mk before): base hole 8/12/16/24 wide, 60/40/30/20 ticks per block, 2/3/4/5 card slots, caps above (Mk4 with 4 range and 8 speed cards: 64 wide, a block per tick). Buffer 20,000 FE and input 1,000 FE/t times the Mk.
  - Upgrade in place (Excavator and Survey Rig): right-click the placed machine with the next Mk. Battery, cards, buffer, energy, owner and progress stay, the old block comes back to the player (the recipe consumed one). Skipping a Mk is refused. A save with more cards than the Mk has slots keeps the first ones and sends the rest out like any output.
  - Working look: LIT while it worked in the last 2 s (spinning drill, blinking lamp, rock dust); the Survey Rig sweeps its scan window, pulses its band and sparks. Area workers take the core side config (their buffer is the item face; they only push output to faces that allow output). Jade shows status, progress, Mk and owner.
- Survey Rig (Age 2: Servo Actuators, Reinforced Casings, an Advanced Circuit, a diamond pickaxe, ender pearls; 407 IE vs the Excavator's 90): placed once and powered, it slowly turns a lot of FE into random ores. No area, no scan, no hole, nothing in the world changes (the old chunk-ledger version is gone; old rigs load and their stale tags are ignored).
  - Ore pool (`SurveyOrePool`): every item in the `c:ores` item tag, so vanilla, Robotica's (thorium, pyrolite, resonite) and other mods' ores. Items are grouped into kinds by their `ores/<name>` tag, so stone, deepslate and nether variants share one weight and are picked evenly. Server config `surveyRigOreWeights` ("#tag=weight" or "item=weight", 0 = never): coal 100, copper 90, iron 80, tin 60, thorium / zinc / aluminum 50, lead 45, nickel / osmium 40, redstone 35, gold / quartz 30, lapis / silver 25, fluorite 20, pyrolite 15, uranium 12, diamond 6, emerald 4, resonite 2, ancient debris 1; unlisted kinds `surveyRigDefaultOreWeight` (15). With vanilla alone a roll is a diamond about 1.5% of the time. Kinds in `surveyRigCoreOres` (ancient debris, resonite) only come out with a Magma Core in its core slot (never consumed). The pool is rebuilt after a tag or config reload.
  - Each roll rolls that ore's loot table with a pickaxe carrying the Fortune or Silk Touch of its cards (an ore item without a block comes out as is). Drops go to adjacent inventories (chests, RS/AE2 interfaces) or the 9-slot buffer, which pipes and hoppers can pull from.
  - Rate: 1 ore / 400 ticks (20 s) at 200 FE/t (80,000 FE per ore). Speed cards divide the time by `speedMultiplier` but multiply the FE/t by the speed and by `steepEnergyMultiplier`, so FE/t goes x3.5 / x9 / x42 / x420 and FE per ore x1.75 / x3 / x7 / x21 for 1 / 2 / 4 / 8 cards (x20 speed draws 84,000 FE/t, a big reactor). Efficiency cards take 15% each off the steep factor. Buffer 2M FE, input 100,000 FE/t (times the Mk).
  - Mk1-4 (Mk2 Age 2: 2 diamonds, Advanced Circuit, 2 Servo Actuators, Reinforced Casing; Mk3 and Mk4 like the Excavator's): x1 / x1.5 / x2 / x3 speed at the same FE per ore (FE/t grows with it), 1/2/3/4 card slots, caps above, and rare ores weigh more: kinds of weight 20 or less get +0 / +50 / +100 / +200% (`surveyRigRareBonusMk1-4`, `surveyRigRareWeight`), so a Mk4 makes diamonds about 2.7x as often (3.5% of rolls with vanilla ores).
  - Owner, team members and operators may open or upgrade it. GUI: energy bar (FE/t in its tooltip), battery and core slots, a small scanner screen showing the last ore (its chance per roll in the tooltip), a progress bar to the next ore, its upgrade slots, buffer, status dot and word. Jade shows status, progress and the last ore.

## Tools and weapons (module `gear`)

Tools from Age 1 on use FE instead of durability and never break. Empty tool: mining speed of a wooden pickaxe, no area mode, weapons deal 1 damage. Every tool tier upgrades at the smithing table: template = the tier's upgrade kit, base = previous tool, addition = a part or core. Smithing keeps data components, so energy, mode and toggles carry over. Tools take normal enchantments (tags `minecraft:enchantable/mining`, `/durability`; batons `/sharp_weapon`, `/weapon`, `/fire_aspect`); Unbreaking lowers the FE per block (III: 40%), Mending is not offered on FE tools.

Mining tools:
| Tool | Age | Energy | Modes (default first in bold) | Notes |
|---|---|---|---|---|
| Tinker's Hammer | 0 | durability 600 | 1×1, **3×3** | 3×3 at 50% speed. Also makes plates. |
| Felling Axe | 0 | durability 500 | **whole tree**, 1×1 | max 64 logs, replants |
| Bore Drill | 1 (hammer + Kit I + Electric Motor) | 400k FE | 1×1, **3×3** | iron pickaxe speed and tier, 3×3 at 50%, 40 FE per block |
| Chainsaw | 1 (felling axe + Kit I + motor) | 400k FE | **whole tree**, 1×1 | 30 FE per log, max 256 logs, clears leaves, replants |
| Servo Drill | 2 | 2M FE | + 5×5, vein (64) | area 70% speed |
| Magma Drill | 3 | 8M FE | + 3×3×3, 9×9 | area 85% speed, auto-smelt |
| Null Drill | 4 | 32M FE | + 5×5×5, 12×12, 12×12×12 | full speed |

Tool Upgrade Kit I is cheap (iron plates, redstone, a copper coil): the Bore Drill and Chainsaw are first-iron-hour tools.

3D modes (3×3×3 and up) are mid-to-endgame on purpose. Area breaks of more than 27 blocks run through a server-side break queue (max 64 blocks per tick per player, configurable) so large modes never freeze the server.

Controls and settings (stored per tool as data components, changed through a client → server payload validated against the held tool):
- V cycles mode (sneak + V or sneak + scroll goes back). The HUD shows "1x1 [3x3] 5x5 [V]"; the tooltip shows every mode with the current one highlighted. A mode tick sounds higher for bigger modes.
- Holding sneak always mines 1×1.
- B: Silk Touch / Fortune / off (Servo and up).
- G: settings screen. Only four toggles: keep floor (off by default), auto-pickup (on by default once installed), void filter, auto-smelt (Magma and Null). The Age 0 hammer and axe only have keep floor (hammer).
- Tool modules (power tools only): auto-pickup needs a Auto-Pickup Upgrade (`upgrade_pickup`: ender pearls, redstone, iron plates, Basic Circuit) and the void filter a Void Filter Upgrade, installed at a Tinker's Bench (Age 1: iron plates, copper gears, crafting table, planks). The bench stores nothing: one tool slot and one slot per module; a card put in is used up and stored on the tool (`gear_modules` component, kept through smithing), taking it out gives the card back. Machines do not take the Auto-Pickup card. Light placer, leaves and replant toggles were removed: tree tools always replant from your saplings and the Chainsaw always clears leaves.
- Area outline rendered client side before breaking.
- Sounds scale with the break: 3×3 crunch, 5×5/3×3×3 heavy crunch, more than 27 blocks a drill spin-up, a rumble and debris while the queue drains, a crash for whole trees.

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

The player never crafts building blocks one by one. The Architect Table turns cheap bulk materials into **matter**, then fabricates Robotica building blocks out of matter + FE while it builds. It is a day-one machine: get a base up fast, then grow it.

Matter grades (stored as numbers in the table, shown as three bars):
- Rustic matter: cobblestone/stone/deepslate/dirt/sand/gravel 1, planks 1, logs 4, cobbled anything 1.
- Refined matter: copper ingot 4, iron ingot 8, gold ingot 8, glass 2, bricks 2, quartz 4, clay ball 1.
- Exotic matter: obsidian 4, amethyst shard 4, prismarine crystals 4, glowstone dust 2, diamond 32, ender pearl 16.
Any item handler can feed the input (hoppers, RS exporters, Thermal servos). Values are item tags `robotica:matter/<grade>_<value>`.

Block styles, each a family of 6 roles (wall, floor, roof, pillar/trim, window, light panel):
| Style | Age | Matter per block | FE per block | Look |
|---|---|---|---|---|
| Clean Stone | 0 | 2 rustic | 10 | white plaster, oak boards and beams, clay tiles, paper light panels |
| Smooth Panel | 1 | 2 rustic + 1 refined | 20 | copper plate courses with rivets, floor plates, standing seam roof, amber panels |
| Detailed Stone | 2 | 1 rustic + 2 refined + 1 exotic | 40 | white panels with staggered seams, grated floor, ribbed roof, cyan panels |
| Tech Stone | 4 | 2 refined + 2 exotic | 80 | dark glossy panels with a teal seam line, polished tiles, teal panels |
Clean Stone is always available; higher styles need the matching casing in the table's style slot (Iron, Reinforced, Null Casing). FE per block is `fePerBlock` (20) times the style factor (50/100/200/400 %). All 24 blocks also have normal crafting recipes (8 bulk blocks + 1 tier material → 8), so players can repair or extend by hand. Walls, floors and roofs tile seamlessly (no frame per block) and mix 3 weighted texture variants; roof blocks show the wall surface on their underside, so the ceiling reads as a ceiling; light panels have a full-bright overlay.

Architect Table (Age 0: 2 copper ingot + crafting table + 2 copper gear + Clockwork Mechanism + 3 cobblestone, no iron; FE buffer 200k). Slots: 9 material inputs, casing slot, battery slot (a wound Mainspring or any FE cell, so it runs without cables; a Winding Crank or a Combustion Generator is plenty), 2 upgrade slots (speed, efficiency, height: pick two).

The table is the middle floor block of its own plot: the centre plot spans table.x-4..+4 and table.z-4..+4 with its floor at table.y, and the 5×5 plot grid continues 8 blocks apart, so neighbours share their edge column (the grid spans 41×41). Every plot holds the same building, a 9×9×6 shell (7×7 inside) (Height cards: +1 layer each, up to 6 cards for 12 high, built plots grow or shrink to match); the player designs the layout by choosing plots.
- GUI: the grid (click a plot to queue or unqueue it, click the edge of a planned plot to add or remove a door there, click the seam between two joined plots to step its inner wall, shift-click a built plot to forget it), 4 style chips, Build and Cancel, Demolish, matter and energy bars, progress, a clear-terrain toggle. Built plots are solid, queued plots outlined, the plot being built pulses; joined plots are drawn as one shape, inner walls as a line across the seam (a door mark for a doorway). Words live in tooltips.
- Nothing is built until Build is pressed. The table works plot by plot, bottom-up, one block per N ticks (base 4, speed cards apply), matter + FE per block. Missing matter stops the build: the status names the grade ("Needs rustic"), the builder drone flies off, and the table waits until it is fed and Build is pressed again (no retry every tick). Missing FE pauses and resumes on its own once energy is back. The status only changes when a block is really attempted, so it never flickers between "Building" and a stall reason.
- Shell: floor with a trim line along every outside wall; walls with a 5-wide window band on layers 2-3 (and 6-7 on tall buildings); roof with 4 light panels at local 2 and 6 (a grid 4 apart, continuous across plots) and a trim edge outside; corner posts at outer corners only.
- Joining: a side shared with a planned neighbour has no wall at all, so a group of plots is one hall. Every plot covering a shared column wants the same block there, so build order never matters; a shared block already built in a neighbour's style counts as done. Where an outside wall continues into a neighbour the corner is plain wall, the middle of a 2×2 block is open (wall when an inner wall meets there), and the inner corner of an L closes the wall. When a neighbour is added or removed or an inner wall changes, built plots are re-walked on the next Build and only the blocks that differ change.
- Inner walls: a shared side can be closed again, one block thick, either with a doorway (same frame as a door) or solid; clicking the seam steps open, doorway, wall, open. The wall stands in the shared column, exactly between the two 7-wide rooms (both plots build it), has floor and roof above and below and no windows, and goes away when either plot leaves the plan. Rooms closed off by solid walls are the player's choice: the one-door rule only counts outside doors.
- Doors: 3 wide, 3 high, framed by two posts and a lintel, only in outside walls. Every group keeps at least one: a lone building gets one facing the table (the table's own plot faces south), and when a change leaves a group without a door, the plot nearest the table gets one. The last door of a group cannot be removed.
- The table never replaces itself or unbreakable blocks. Without clear terrain it only builds into air and replaceable blocks (grass, water, its own blocks); with clear terrain it removes plain blocks in the footprint (first builds only: re-passes of a finished building never clear, so furniture inside stays), slowly (one per `clearInterval`, default 10 ticks, speed cards do not help, so it is no quarry); chests and other block entities in the way are broken too, their contents kept; junk (tag robotica:voidable: cobble, dirt, gravel...) is voided, everything else goes into a container touching the table (never cleared), else on top of it. Spawn protection, the world border and BreakEvent/EntityPlaceEvent (claims) are respected: such blocks are skipped for free.
- Demolish (two clicks: the button asks "Click again" for 3 s; the request carries a confirm code and goes through the same checks as every table action: menu open, within 8 blocks, owner, team or operator): takes down every built plot (and a plot a stopped build had started) over its whole footprint, from its built height down to the floor, everything inside included. Queued plots leave the plan at once, each plot leaves it when it is down, so the plan is empty at the end; the plan is locked while it runs, Cancel stops it (a half taken down plot is rebuilt by the next Build). Plots go farthest from the table first, top layer first, one block per `demolishInterval` (1 tick, 4x faster than building; speed cards apply) for `demolishEnergy` (5 FE, energy multiplier and cards apply; air and liquids free). Robotica building blocks go back into the table as `demolishRefund` (75%) of their style's matter price (fractions carried over; a full grade gives the block item instead, 0 always does); any other block breaks with its drops as if mined with a Silk Touch diamond pickaxe (glass, furniture and ender chests come back), a container's contents first; doors, beds and tall plants come down with their lower half or head. Everything is stashed like clear terrain: junk voided by tag, the rest into a container touching the table, else on top of it; when a plot is done, items lying in it (popped torches, anything left) are stashed too. Never the table, the container touching it, unbreakable blocks, spawn protection, the world border or blocks a BreakEvent listener (claims) refuses. Config `allowDemolish`.
- Owner, team members and operators may use the table. Picking the table up keeps everything (matter, energy, slots, the plan); placed elsewhere, built plots are forgotten and queued ones stay queued.

Builder drones: purely visual, a small flying entity that flies from the table to each placed block. Optional (config).

## Mob replicator (module `replicator`)

Mid-high tier, slow, powerful.
- Essence Vial (Age 2, consumable; a sample item, so it uses a Basic Circuit instead of an Advanced one): right-click a hostile mob to take a sample (deals 2 damage, 3 s cooldown). The first sample binds the vial to that mob type. 8 samples completes it. Boss mobs and anything in `robotica:replicator_blacklist` cannot be sampled.
- Mob Replicator: 3×3×3 multiblock. Replicator Controller in the middle of one face, the rest Replicator Frame and Replicator Glass (at least 1 glass). The controller checks the structure every 40 ticks.
- Insert a complete vial, feed FE (base 160 FE/t). One cycle takes 1,200 ticks (1 min) at Age 2 speed. Speed cards (max level 3) and Plasma Actuator in the boost slot (×2) speed it up. Fortune cards act as Looting.
- Mode "Harvest" (default): rolls the mob's loot table as if a player killed it and puts drops in an 18-slot internal output. No entity is ever added to the world. A translucent hologram of the mob spins inside the frame and flashes on each cycle.
- Mode "Spawn": spawns the real mob in front of the controller instead (for your own mob grinders), max 8 nearby, then waits.
- Never duplicates boss drops, nether stars or anything in the blacklist tag.
- Ladder gate: mobs in `robotica:replicator_tier3` (blaze, enderman, guardian, ghast, wither skeleton, piglin brute) need a Magma Core in the catalyst slot, `robotica:replicator_tier4` (shulker) an Antigrav Core. Never consumed.

## Machine sides (core `SideConfig`)

Item machines (Grinder, Electric Furnace, Alloy Smelter, Centrifuge, Assembler, RTG, Metal Press) have a per-face item config, like Thermal's. Faces are relative to the machine's front (Front, Back, Left, Right as seen facing the front, Top, Bottom), each None / Input / Output / Input + Output:

- The item capability on a face follows it: None gives no capability (pipes do not connect), Input is insert-only, Output extract-only, Input + Output the machine's normal slot rules. A null side always gets the normal rules. Changing a face calls `invalidateCapabilities`.
- Defaults keep the old behaviour: every face both ways, the Grinder and Electric Furnace bottom Output only.
- Auto-input pulls from inventories on Input faces, auto-eject pushes outputs into inventories on Output faces. Both are off by default, run every `sideTransferInterval` ticks (10) and move up to `sideTransferItems` (16) items each way (core config). Only loaded neighbours.
- GUI: a "Sides" tab right of the machine screen (drawn by `MachineScreen`, JEI keeps clear of it). Click a face for the next mode, right-click for the previous one; two boxes toggle auto-input and auto-eject. Clicks go through `robotica:core_side_config`, applied only to the menu the player has open and only while it is still valid (in reach). The config syncs to the open menu in one data slot.
- Opting in: a `SideConfig` field built from the machine's automation rules, `access(side)` in the capability, `tick(level)`, save/load, `trackSides` in the menu.

## Logistics (module `logistics`)

Item pipes, the Robotica answer to Thermal itemducts and Ender IO conduits. Items move instantly, nothing is rendered in the pipe.

- Item Pipe (first iron: 6 copper plates, 2 glass, 1 redstone -> 8) and Item Pipe Mk2 (8 pipes around an Electric Motor -> 8). Server config `robotica-logistics-server.toml`: Item Pipe 8 items every 20 ticks, Mk2 32 every 10 (`pipeItemsN`, `pipeIntervalN`), networks up to `pipeNetworkMax` (4096) pipes.
- A pipe links to every neighbouring pipe (any tier) and to every block with `Capabilities.ItemHandler.BLOCK` on the touching face (chests, machines as their side config allows, other mods). Block state per face: none / pipe / insert / extract (multipart model: core, arms, a blue Insert or orange Extract flange).
- Inventory links are Insert (default), Extract or Off: sneak-right-click the arm (or the core face toward it) with an empty hand; a plain right-click shows the mode. Off links draw no arm.
- Every interval each Extract link pulls up to its pipe's items and hands them to the network's Insert links in turn (round robin), through their capabilities, never back into the block they came from. At most 64 insert tries per pull; a stuck slot rotates the start slot.
- The network (pipes and Insert links) is found by one walk over the loaded pipes and cached; placing, removing, relinking, loading or unloading a pipe marks it stale and the next pull rebuilds it. A neighbour's capability change (capability cache listener) rechecks that pipe's arms on its next tick.
- No filters yet.

## Warp (module `warp`)

Getting home and travelling between bases. All teleports run on the server, cost FE, and work for every player on a server.
- Warp Pad (Age 1: Basic Circuit, Iron Casing, ender pearls; FE buffer 1M): a block you name in a small GUI. Pads belong to their owner; the owner can mark a pad public so friends can use it. Stand on a pad and right-click it to open the destination list; its owner (or an operator) gets a name box at the top of that list, and right-clicking the pad from beside it (or sneak-right-click with an empty hand) opens the settings (name, public / private). Names are 1-24 printable characters, checked on the server. Cost: 5,000 FE + 20 FE per block of distance, taken from the departure pad. Same dimension only, until the pad gets a Rift Upgrade (Age 3 parts + Magma Core) that unlocks cross-dimension travel for a flat 100,000 FE.
- Recall Remote (Age 1, 400k FE): sneak-right-click a Warp Pad to bind it. Hold right-click for 3 seconds (any damage cancels) to teleport to that pad. 20,000 FE, 30 s cooldown, same dimension. Smithing upgrade to Rift Remote (Age 3) works across dimensions for 150,000 FE.
- Rift Remote list: it stores up to 10 pads (`bound_pads` component: pad id plus a name and position snapshot). Sneak-right-click a pad to add it (a stored pad only refreshes its snapshot; a full remote refuses). Right-click opens a small GUI listing the pads with their current name (looked up in the pad registry, the snapshot when the pad is gone), position, dimension and trip cost; click a pad to start the 3 second charge, which runs on the server (damage or putting the remote away cancels it), click the cross to forget it. The trip uses the same checks, costs and cooldown as the Recall Remote. An old Rift Remote (or a Recall Remote smithed into one) moves its single `bound_pad` into the list.
- Portal Projector (Age 4, registry id `gate_controller`): a single squat emitter block with a glowing lens, placed on the ground (it faces the player). Link two with a Linking Card (one Null Circuit) (sneak-right-click projector A, then B). While powered (200 FE/t idle) it projects a floating, swirling elliptical portal (2 wide, 3 tall, bottom edge 1 block above the projector, light level 15 while active, 6 idle). The portal is not made of blocks: it is drawn by a block entity renderer and detected by an AABB query every 2 ticks. Players, mobs and items that touch it arrive in front of the other projector, facing out (10,000 FE per entity). Works across dimensions. Jade shows projecting, no energy (linked but below the idle cost) or idle, and the owner.
- The pad registry is a `SavedData` on the overworld, so pads keep working when their chunk is unloaded (the destination chunk is loaded on arrival).
- Safety: never teleport into solid blocks, look for the nearest safe 2-high spot within 3 blocks, otherwise refuse with a message and refund the FE.

## Bosses (module `boss`)

- Scrap Colossus: a 3 block tall, 2 wide rusted copper robot (300 HP, armor 10, toughness 4, full knockback resistance), boss bar for every player tracking it. Never breaks blocks, whatever `mobGriefing` says. Stays within 20 blocks of its altar.
- Attacks, all telegraphed: ground slam (1 s wind-up with raised arms, then a shockwave that hurts 14 and throws back everything on the ground within 5 blocks; jumping dodges it), a lobbed chunk of scrap (8 damage) at targets 6-28 blocks away, plain punches (10).
- Phase 2 below half health, once: up to 3 Scrap Drones (small flying minions, fall apart after 90 s or when the Colossus is gone) and an overheat every 20 s: it stands still for 4 s venting steam with its furnace hatch open and takes double damage.
- Drops: 1 Servo Core always, copper and iron ingots, raw copper, nuggets, redstone, gears, sometimes an Electric Motor; 150 XP. The loot moves to the killer and only they can pick it up. Everyone within 64 blocks who had the boss bar gets the "Scrap Heap" guide step.
- Colossus Altar: right-click it with a Signal Flare to wake a Colossus on top (needs 3x3x4 air). One Colossus per altar at a time, then the altar cools down (5 min) and goes dark. Not on Peaceful. Creative players skip the cooldown.
- Signal Flare (Age 1: 4 copper, 2 redstone, 1 gunpowder, 1 Electric Motor, about 24 IE): the summon item. The real price is the fight.
- Colossus Altar recipe (Age 2: 4 Reinforced Casing, Advanced Circuit, Servo Actuator, 2 obsidian, copper block, about 360 IE): build your own arena. No Servo Core needed, so worlds without foundries can still farm the boss. The altar in a ruin drops 2 copper blocks instead of itself.
- Rusted Foundry: a ruined 25x25 hall of stone bricks and rusted copper (template `rusted_foundry.nbt`, written by `scripts/data/boss_structure.py`) around the altar, with a chest holding a Signal Flare. Plains, sunflower plains, desert, badlands; random spread 48 chunks, separation 16, kept 4 chunks from villages; skipped on slopes over 7 blocks and on water.
- Server config `robotica-boss-server.toml`: `bossHealthMultiplier`, `bossDamageMultiplier`, `colossusMinionCap`, `altarCooldownSeconds`, `foundryEnabled`.

## Onboarding (module `codex`)

- Guide advancements `robotica:guide/*` (tab "Robotica", written by `scripts/data/codex_guide.py`): copper gear → craft a robot → wind a Mainspring → place a robot → robot working; hammer → first iron → generator, Charger, Copper Cell, Tesla Coil, power tools → Basic Circuit → press, cards, farm kit, warp → diamonds → Excavator → Survey Rig, Servo Age → Rusted Foundry or Signal Flare → Scrap Colossus → Servo Core → replicator, Deep Age → Magma Core → Antigrav Age → Antigrav Core → Null Drill, portal. Rewards unlock the next recipes in the vanilla recipe book (the script fails if a Robotica recipe, except the Architect's, has no step).
- Custom trigger `robotica:milestone` (core `Milestones`) for wind_spring, robot_working, farm_kit, vial_complete, replicator_formed, warp, portal. Machines award their owner when online and nearby.
- The server syncs finished guide steps to the client (`robotica:codex_guide_progress`); the Codex's first chapter "Next steps" lists the steps you can do now and a checklist.
- When a guide step is done, chat names the next one or two steps (config `guideChatTips`).
- Tooltips: one or two short lines; Shift shows details (keys as bound, FE per block, settings) from `HasDetails` or lang keys `tooltip.robotica.<id>.details`.

## Later (not in this build)

Guard Drone, Wingman, Mole, the Magma and Antigrav bosses, an item storage network, RS API integration, Create compat.

## Engineering rules (all modules)

1. Dedicated server safe. Any class that touches `net.minecraft.client.*` or `com.mojang.blaze3d.*` lives in a `client` subpackage and is only reached from `RoboticaClient` (`@Mod(dist = Dist.CLIENT)`) or `@EventBusSubscriber(value = Dist.CLIENT)` classes. Never call `Minecraft.getInstance()` from common code. Item tooltips use `TooltipContext`, not the client player.
2. Server authoritative. Key presses send a payload; the server validates (item in hand, mode allowed for tier) and changes the item's data components.
3. Configs are `ModConfig.Type.SERVER` (synced, per world) for balance values. One config file per module: `robotica-<module>-server.toml`.
4. Each module registers its own `DeferredRegister`s and its own payloads. Never edit another module's files. Shared code lives in `com.arno.robotica.core` and is owned by the core.
5. Lang entries and tags go in `src/main/fragments/<module>/...` (same path layout as resources). Everything else (models, blockstates, textures, recipes, loot tables) goes in `src/main/resources` with unique file names.
6. Textures are generated by `scripts/textures/<module>.py` using `scripts/pixelart.py`. GUIs are drawn with `GuiGraphics.fill` through the core `MachineScreen` helpers, no GUI textures.
7. Block entities tick only on the server (`level.isClientSide` check in the ticker factory). Sync to client only what rendering needs.
8. Performance: area scans are spread over ticks (cursor-based), never full-area scans every tick.

## Balance notes

Raw-material costs of every item are in `docs/COSTS.md` (regenerate with `python3 scripts/cost_report.py`; iron-equivalent "IE" = iron 1, copper 0.4, redstone 0.4, gold 3, diamond 10, ender pearl 8, netherite 60, nether star 80).

Progression pass (start quickly, scale to the late game):
- First iron hour, all without gold or diamonds: Combustion Generator 6.4 IE (1 iron), Charger 5.4 IE (1 iron), Copper Cell 4.8 IE (2 iron, 3 redstone), Tool Upgrade Kit I 8.6 IE, Bore Drill and Chainsaw about 32 IE through smithing (11 iron, mostly copper). A Copper Cell holds 5.7 drill charges (2.3M vs 400k FE); kept in the inventory it recharges the drill in hand.
- Winding Crank 2,000 → 6,000 FE per turn and 5 turns/s when holding right-click (later lowered again, see the power pass below).
- Excavator moved to Age 2 (diamond pickaxe drill head, 90 IE) and slowed to 1 block / 60 ticks. Speed cards cost x1.75 / x3 / x7 / x21 FE per block for 1 / 2 / 4 / 8 cards, so x20 needs about 280 FE/t (7 generators).
- Survey Rig (Age 2, 407 IE, 4.5x the Excavator): an endless ore source anywhere is the strongest miner, so it is slow (1 ore / 20 s) and costly (200 FE/t, 80,000 FE per ore) at base, and speed cards make the FE/t climb far faster than the speed (x420 FE/t for x20). Recipe unlocked by the Servo Age guide step; guide step "No Holes Barred" after "Dig Deep".
- Warp Pad moved to Age 1 (41 IE): the Age 1 Recall Remote needs a pad to bind to and was a dead end.
- Upgrade cards are one item per kind (13-24 IE at Age 1, about 80-90 IE at Age 2). The cost of going far now comes from stacking, the steep FE price of speed and the caps (robots: one speed/range/growth card per Mk tier, so x6 speed needs a Mk4 robot).
- Area tools pay for size with speed early on: hammer and Bore Drill 50% in 3×3 (still 4.5x faster than nine single blocks), Servo 70%, Magma 85%, Null 100%.
- Boss core temp recipes stay expensive and rise per age: Servo 505, Magma 1,039, Antigrav 2,198 IE.
- Boss pass: the Scrap Colossus is the main Servo Core source (a Signal Flare costs about 24 IE plus the fight and a 5 minute altar cooldown). The Servo Core fallback recipe went from 505 to about 830 IE (diamond blocks and a Reinforced Casing) and stays only for Peaceful and foundry-less servers.

Power pass (early power in line with other mods, 40-80 FE/t; the crank made everything feel infinite at 1,500 FE/t):
- Winding Crank 6,000 → 400 FE per turn (100 FE/t while held), only a Mainspring winds by hand. A Mainspring takes 2 minutes; a Combustion Generator (40 → 80 FE/t, 128k FE per coal) wins because you can walk away.
- Mainspring 576k → 240k, Copper Cell 2.3M → 800k, Redstone Cell 6.9M → 3.2M (cells were cheaper storage than accumulators). Ender Cell unchanged.
- Solar Mk1 8 → 20 FE/t, Mk2 32 → 80 FE/t. Charger 400 → 2,000 FE/t.
- Stumpy pays 250 FE per log on top of 4 FE/t while working, rests 10 ticks per log after a tree (speed cards shorten it), works a 7×7 area at Mk1 (Sprout 9×9), and always clears the natural leaves.
- Storage Terminal: without power nothing new goes in (taking out and crafting still work). JEI can fill its crafting grid from the terminal and your inventory. A Carry Upgrade (right-click the terminal, stays in for good) makes it keep every item when picked up.
- Kinetic Shield 400 → 2,000 FE per damage point, absorbs at most 75% of each hit (`kineticShieldAbsorb`). Cells top up a worn suit at most 2,000 FE per second in total (`cellRechargePerSecond`).
- Mining Drone 60 → 80 FE per block, one block every 8 ticks instead of 3 (it outdug the Age 2 Excavator).
- Mining Drone Mk3 (Age 3: smithing a Mk2 with a Quantum Circuit template and a Plasma Actuator): 5x5 tunnels (floor at foot level, 2 blocks to each side, 5 high) instead of 3x3, drops rolled with a netherite pickaxe carrying Fortune I, 2.5x dig speed (`mk3Speed`; Mk2 1.5x), 8M FE buffer, double health. Per metre it digs 25 blocks in about 75 ticks against the Mk2's 9 in 45, still 80 FE per block, so it beats the Mk2 without outdigging an Excavator with speed cards.
- Sprout pays 30 FE per harvested crop (`sproutFePerHarvest`), like Stumpy's per-log cost. Null Lance 20,000 → 8,000 FE per shot.
- Tool modules: auto-pickup and the void filter need an Auto-Pickup or Void Filter Upgrade installed at a Tinker's Bench; the Age 0 hammer and axe take none.

Earlier passes, still valid: Age 0 uses no iron (Stumpy and Sprout 25 copper, Tinker's Hammer 9, Felling Axe 6). Each ladder step costs 4-9x the one before (tool kits 8.6 / 312 / 2.7k / 9.3k IE, accumulators 28 / 220 / 1.7k, solar 12 / 102, Mk farm kits 46 / 259 / 6.6k). Magma Core temp recipe takes 2 Quantum Circuits so every core costs more than the one before. Portal Projector ~10k IE in line with the Null Drill (~16k IE). Config `replicatorEnergyPerTick` 160, `gateIdleCost` 200. Left alone on purpose: Combustion Generator 40 FE/t, Metal Press 20 FE/t, Stumpy 4 FE/t, Sprout 3 FE/t, drills 40-80 FE per block, Null Lance 20k FE per shot. Rift Remote cost stays 150,000 FE because a game test pins it.
