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
| `range` | 2 | 4 | machine specific (robots +2 radius, Excavator 16/32/48/64) |
| `fortune` | 2 | 3 | Fortune I-III (Looting in the replicator) |
| `silk` | 2 | 1 | Silk Touch |

Energy per action with speed cards: normal machines `(1 + 0.25s + 0.05s^2)`, the Excavator and Survey Rig `(1 + 0.5s + 0.25s^2)` (x1.75, x3, x7, x21 per block for 1, 2, 4, 8 cards): every card costs more than the one before.

Caps per machine: Stumpy/Sprout speed, range and growth = Mk tier (1-4), efficiency 4. Excavator: speed 8, range 4, efficiency 4, fortune 3, silk, void. Survey Rig: speed 8, efficiency 4, fortune 3, silk, void. Metal Press: speed 4, efficiency 4. Replicator: speed 3, fortune 3, efficiency 4. Architect Table: kind defaults.

API (`core.upgrade`): `new Upgrades(slots, Set<UpgradeKind>, onChanged)` (kinds capped at `maxStack`), `new Upgrades(slots, Map<UpgradeKind,Integer> caps, onChanged)` or `new Upgrades(slots, kinds, ToIntFunction<UpgradeKind> caps, onChanged)`; `level(kind)` returns the number of cards that count; `insertOne(stack, simulate)` for right-click insert; static `speedMultiplier`, `energyMultiplier`, `steepEnergyMultiplier`, `fortuneEnchantLevel`, `growthBonus`. `CoreItems.card(kind)` and `CoreItems.cards(kind, count)`; `card(kind, level)` is deprecated and returns the single card.

## Machines and power (module `power`)

- Winding Crank (Age 0): holds a Mainspring (or any FE item: cell, drill). Hold right-click with an empty hand: +400 FE per turn, 5 turns/s (100 FE/t, a bit more than a Combustion Generator but you have to stand there), a Mainspring is full in about 2 minutes. Only a Mainspring winds by hand; other FE items only charge from an FE source. Left-click pops the item out; sneak-right-click shows the charge, a second sneak-click within 2 s also takes it out. Accepts FE from any source at 200 FE/t. Mainsprings only charge here; the Mainspring tooltip shows how far it is wound in percent.
- Combustion Generator (first iron: copper shell, furnace, 1 iron ingot): burns furnace fuel, 80 FE/t (in line with other mods' coal generators), buffer 40,000. Right-click it with fuel.
- Solar Panel Mk1 (Age 1) 20 FE/t, Mk2 (Age 2) 80 FE/t daytime with sky access.
- Accumulator I/II/III: 1M / 4M / 16M FE, I/O 1,000 / 4,000 / 16,000 FE/t.
- Tesla Coils (wireless power, replaced the Copper/Gold Conduits): a torch-sized coil placed on floors, walls or ceilings (FACING, 6 ways) with a full-bright tip.
  - Tiers I-V: 4 / 8 / 12 / 16 / 32 links, range 8 / 12 / 16 / 24 / 32 blocks, 4,000 / 16,000 / 64,000 / 256,000 / 1,000,000 FE/t per coil (server config `teslaRate1-5`, `teslaRange1-5`). Tier I is a first-iron item (copper coil, iron plate, redstone; 6 IE, no gold) so the first generator can feed machines; every later tier consumes the one before plus that age's circuit and casing (II basic circuit + gold, III advanced circuit + Reinforced Casing, IV quantum circuit + Blazing Casing, V null circuit + Null Casing).
  - Source: a coil pulls from the block it sits on when that block gives FE out (through the touching face, else its internal storage): Accumulators, but also the Combustion Generator, Solar Panels and other mods' generators and batteries, so early players are not stuck before Accumulator I (Basic Circuit). Any other coil is a relay.
  - Tesla Linker (first iron): sneak-right-click a coil to select it, right-click a machine (any block with the FE capability on the clicked face; the clicked face is the side the power enters) or another coil. Clicking a linked block on the same face unlinks it, on another face moves the link there. Sneak-right-click the air clears the selection. The action bar shows links used / max. Every link, machine or coil, uses one slot. A coil cannot link the block it sits on, or a coil that already sends to it.
  - Flow: each tick a source coil asks its block for up to its rate and splits it fairly over its links (then offers the rest to machines that still take energy). A coil-to-coil hop forwards the share minus `teslaHopLoss` (5%) and the next coil splits it again; a visited set per push stops loops, so one push is O(links). Each coil sends at most its rate per tick in total. Only what was delivered leaves the source.
  - Links live in the coil's block entity (target pos + face, or coil pos) with the owner UUID. Only the owner, the owner's team or an operator may link; the target must be interactable (spawn protection) and in the same dimension and range. Unloaded targets are skipped, chunks are never loaded; links to blocks that are gone (no coil, no FE capability) are dropped every 2 s.
  - Right-click with an empty hand: status screen with links used / max, FE/t sent and whether it sends or relays. The client draws thin animated arcs from the tip to every target (bright while energy flows, faint when idle) and sparks; client config `teslaArcs` / `teslaParticles` turn them off. Charger hum and zap sounds every few seconds while active.
- Charger (first iron: copper, 1 iron plate, redstone): charges FE items, 2,000 FE/t (never more than the item takes), one slot. Right-click it with the item.
- Metal Press (Age 1): 1 ingot → 1 plate, 20 FE/t, 100 ticks. Right-click with ingots or upgrade cards.
- Cells in a player's inventory recharge the FE tool or weapon in their hands at the cell's output rate.

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
- Cost per tick = recipe power x Mk speed x speed card multiplier x `energyMultiplier(speed, efficiency)` x (1 + 0.25 per fortune card); time = recipe time / (Mk speed x speed card multiplier). So FE per craft stays flat over the Mks and only the cards' penalty costs more.
- Mk1-Mk4: speed x1 / x2 / x3 / x5 (config `tierSpeedMk1-4`), card slots 2 / 3 / 4 / 5, caps speed 2 / 4 / 6 / 8, efficiency 1 / 2 / 3 / 4, fortune 0 / 1 / 2 / 3 (each card: 10% chance of one more main result, config `fortuneBonus`), void 1 (Centrifuge only: chance results that do not fit vanish instead of stalling). Each Mk is one age later than the one before (capped at Age 4) and is crafted from the previous Mk + 5 plates of that age's alloy + that age's circuit + its Assembler-only part (Age 2 Thermocouple, Age 3 Superconductor Coil, Age 4 Resonant Lattice). Right-clicking a placed machine with the next Mk swaps it in place, keeping inputs, outputs, battery, cards, energy and progress, and gives the old Mk back.
- Centrifuge recipes: Depleted Fuel Pellet -> Radiant Isotope (10%) + Thorium Dust + Graphite Dust (50%); magma cream -> slime ball + blaze powder; pyrolite dust -> 2 glowstone dust + blaze powder (25%); glistering melon slice -> melon slice + 4 gold nuggets.
- Assembler-only parts: Thermocouple (Age 2: 2 thorium plates, 2 copper plates, Basic Circuit), Superconductor Coil (Age 3: 4 pyrosteel plates, 2 copper coils, 2 graphite dust, Quantum Circuit; 400 FE/t), Resonant Lattice (Age 4: 4 resonant alloy plates, 2 resonite crystals, Superconductor Coil, Null Circuit; 1,500 FE/t). The Assembler also makes the ladder parts for about a quarter less raw material than the crafting table (Basic and Advanced Circuit, Electric Motor, Servo Actuator, Reinforced Casing, Quantum Circuit, Plasma Actuator, Blazing Casing, Null Circuit, Null Casing), the later ones with the alloy plates as the sink.

Fuel (Assembler): Thorium Fuel Pellet (Age 2: 2 thorium dust, graphite dust, ferrothorium plate), Enriched Fuel Pellet (Age 3: pellet, pyrolite, magma cream), Fusion Fuel Pellet (Age 4: 2 resonite dust, Radiant Isotope). Burned pellets leave a Depleted Fuel Pellet (waste, only from burning; the Centrifuge recycles it). Reactor stats live in the item data maps of the `energy` module: `data/robotica/data_maps/item/reactor_fuel.json` (thorium heat 400 / 12,000 ticks, enriched heat 1,000 / 12,000 ticks, both waste `robotica:depleted_fuel_pellet`) and `fusion_fuel.json` (fusion pellet 200,000 FE/t / 2,400 ticks).

Radioisotope Generator `rtg` (Age 2: 4 ferrothorium plates, 2 Thermocouples, Reinforced Casing, glass): a single block that burns `#robotica:rtg_fuel` (Thorium Fuel Pellets) one at a time, 150 FE/t for 24,000 ticks (3.6M FE per pellet; a reactor gets more out of one), pausing while its 100k buffer is full or the waste slot has no room. Quiet. Pushes up to 1,000 FE/t into neighbours, Tesla Coils on it send it on. More power = more RTGs. Config `rtgPower`, `rtgPelletTicks`, `rtgBuffer`, `rtgOutput`.

Generator cards (power module): the Combustion Generator takes up to 3 speed cards (FE/t x2 / x3 / x4, fuel burns `speedMultiplier x energyMultiplier(speed, 0)` times as fast, so FE per fuel item drops to 45% at 3 cards) and up to 4 efficiency cards (+20% FE per fuel item each, the burn slows). Solar Panels take up to 4 speed cards (+25% daylight output each, `solarSpeedBonus`) and 4 efficiency cards (10% of the daylight output at night under open sky each, `solarNightPerCard`); both have a GUI with two card slots now.

Server config `robotica-industry-server.toml`: `alloySmelterPower` 40, `centrifugePower` 60, `assemblerPower` 80, `machineBuffer`, `machineInput`, `tierSpeedMk1-4`, `fortuneBonus`, and the RTG values. Guide steps: Green Glow (thorium, after Wired), Alloyed, Machine Made (Assembler, after Servo Age), Slow Burn (RTG), Fire Stone (pyrolite, after Deep Age), Echoes (resonite, after Antigrav Age). Codex chapter "Industry".

Balance (IE, `docs/COSTS.md`): Alloy Smelter Mk1 25, Centrifuge Mk1 113, Assembler Mk1 204, RTG 83 (150 FE/t vs the Combustion Generator's 80 for 6.4), Mk2 / Mk3 / Mk4 of the Alloy Smelter 102 / 751 / 2.6k, of the Assembler 853 / 2.7k / 4.5k. A Thorium Fuel Pellet costs about 6 IE for 3.6M FE in an RTG (28 coal). Raw weights: thorium 1.5, pyrolite 3, resonite 10, radiant isotope 40.

## Automation (module `automation`)

Area workers are block entities, never mobs. All of them: battery slot (cell or Mainspring), upgrade slots, output to inventories on any adjacent side (chest, RS Interface, AE2 interface, Supply Crate), internal 9-slot buffer when outputs are full, work stops when full or out of energy. Idle drain 0.2 FE/t (rounded: 1 FE every 5 ticks).

Usability: right-click with a battery swaps it in, with a card installs one card. Sneak-right-click with an empty hand shows a status line and the work area. The work area outline shows for 10 s after placing, upgrading or a Mk kit. A stalled robot puffs smoke (no energy) or shows a sign (output full) every 2 s and tells its owner once on the action bar (when within 32 blocks).

- Stumpy (lumber bot, Age 0). Mk1 7×7 (one block less per side than Sprout), rests 10 ticks per log after each tree, 1 action / 40 ticks, growth ×1.5, 4 FE/t while working plus 250 FE per log felled (a tree never costs more than half the 20k buffer; a Mainspring is roughly 850 logs). Fells a whole tree as one action (up to 256 logs, always with its natural leaves), replants saplings from its buffer, collects item drops in the area.
- Sprout (crop bot, Age 0). Harvests mature crops (`CropBlock`, nether wart, sweet berries, cocoa), replants, tills dirt with water nearby. 3 FE/t.
- Farm tiers via Mk kits used on the placed bot: Mk2 (Age 1 parts) 13×13, every 20 ticks, growth ×2; Mk3 (Age 2) 17×17, 5 ticks, ×3; Mk4 (Age 4) 25×25, 1 tick, ×5. Each Mk raises the speed/range/growth card cap by one. Range cards add +2 radius each.
- Growth boost is applied as extra random ticks on crops/saplings in the area (cheap: N random positions per second, not every block).
- Supply Crate (Age 0): 27 slots, plain inventory with item capability.
- Excavator (Age 2: a diamond pickaxe as drill head, plus Electric Motors, Iron Casing, Basic Circuit): mines a real hole below itself. Base 8×8 area, 1 block / 60 ticks, 40 FE per block, stops at bedrock and leaves fluids alone (replaces fluid source blocks with cobblestone as it goes, so no flooding). Up to 8 speed cards at a steep FE price (see Upgrade cards), range 16/32/48/64, efficiency, fortune, silk, void. Never breaks blocks with an unbreakable hardness or block entities. The GUI shows depth, percent dug and a progress bar.
- Survey Rig (Age 2: Servo Actuators, Reinforced Casings, an Advanced Circuit, a diamond pickaxe, ender pearls; 407 IE vs the Excavator's 90): a lag-free virtual quarry that leaves no hole. Its work area is its own chunk.
  - Scan: on first power it reads its chunk from its own Y down to the world bottom, `surveyRigSectionsPerTick` (2) section slices per tick, skipping sections whose palette has no ore and never loading another chunk. Every block in `c:ores` (without a block entity) goes into the ore ledger as a count per block.
  - Mining: each operation takes one ore off the ledger (random, weighted by count) and rolls that ore's loot table with a pickaxe carrying the Fortune or Silk Touch of its cards. Drops go to adjacent inventories (chests, RS/AE2 interfaces) or the 9-slot buffer. No block changes, no block updates. Ancient debris (`c:ores/netherite_scrap`) is only taken with a Magma Core in its core slot (never consumed); when only debris is left it waits with "Needs Magma Core". `surveyRigFillerPerOre` (default 0) adds that many host rock drops per ore for players who want cobblestone.
  - Rate: 1 ore / 100 ticks, 2,000 FE per ore, times `steepEnergyMultiplier` (x1.75 / x3 / x7 / x21 for 1 / 2 / 4 / 8 speed cards). Upgrades: speed 8, efficiency 4, fortune 3 or silk, void. Buffer 500k FE, input 20,000 FE/t (x20 speed costs 8,400 FE/t, 3,360 with 4 efficiency cards).
  - Ledger and surveyed set: `SurveyLedgers`, a `SavedData` per dimension keyed by chunk. Breaking and replacing a rig resumes the ledger instead of scanning again; a second rig in a chunk that a live rig works waits ("Chunk taken"). A mined-out chunk keeps only its "surveyed" mark and a new rig there refuses ("Chunk already surveyed").
  - Double-dip guard, server config `stripOresFromWorld` (default true): the ore positions found by the scan are swapped for their host (`c:ores_in_ground/stone|deepslate|netherrack`, else stone, deepslate below Y 0, netherrack in the Nether) `surveyRigStripPerTick` (4) per tick, only when loaded, with neighbour updates off and the BreakEvent fired (claims can veto). An ore that is gone (mined by hand, dug by an Excavator) or vetoed when its turn comes is taken off the ledger. With false the ores stay in the world and the ledger is purely virtual: the chunk can be mined twice, by hand or by an Excavator, so only use it on servers that want that.
  - Owner, team members and operators may open or upgrade it. GUI: energy bar (FE per ore in its tooltip), battery and core slots, a small scanner screen (sweep while scanning, ore blips while mining, a tick when finished, a cross when refused), a ledger bar (ores left of the total in its tooltip), 4 upgrade slots, buffer, status dot and word.

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
- GUI: the grid (click a plot to queue or unqueue it, click the edge of a planned plot to add or remove a door there, click the seam between two joined plots to step its inner wall, shift-click a built plot to forget it), 4 style chips, Build and Cancel, matter and energy bars, progress, a clear-terrain toggle. Built plots are solid, queued plots outlined, the plot being built pulses; joined plots are drawn as one shape, inner walls as a line across the seam (a door mark for a doorway). Words live in tooltips.
- Nothing is built until Build is pressed. The table works plot by plot, bottom-up, one block per N ticks (base 4, speed cards apply), matter + FE per block. Missing matter or FE pauses and the status names it.
- Shell: floor with a trim line along every outside wall; walls with a 5-wide window band on layers 2-3 (and 6-7 on tall buildings); roof with 4 light panels at local 2 and 6 (a grid 4 apart, continuous across plots) and a trim edge outside; corner posts at outer corners only.
- Joining: a side shared with a planned neighbour has no wall at all, so a group of plots is one hall. Every plot covering a shared column wants the same block there, so build order never matters; a shared block already built in a neighbour's style counts as done. Where an outside wall continues into a neighbour the corner is plain wall, the middle of a 2×2 block is open (wall when an inner wall meets there), and the inner corner of an L closes the wall. When a neighbour is added or removed or an inner wall changes, built plots are re-walked on the next Build and only the blocks that differ change.
- Inner walls: a shared side can be closed again, one block thick, either with a doorway (same frame as a door) or solid; clicking the seam steps open, doorway, wall, open. The wall stands in the shared column, exactly between the two 7-wide rooms (both plots build it), has floor and roof above and below and no windows, and goes away when either plot leaves the plan. Rooms closed off by solid walls are the player's choice: the one-door rule only counts outside doors.
- Doors: 3 wide, 3 high, framed by two posts and a lintel, only in outside walls. Every group keeps at least one: a lone building gets one facing the table (the table's own plot faces south), and when a change leaves a group without a door, the plot nearest the table gets one. The last door of a group cannot be removed.
- The table never replaces itself or unbreakable blocks. Without clear terrain it only builds into air and replaceable blocks (grass, water, its own blocks); with clear terrain it removes plain blocks in the footprint (first builds only: re-passes of a finished building never clear, so furniture inside stays), slowly (one per `clearInterval`, default 10 ticks, speed cards do not help, so it is no quarry); chests and other block entities in the way are broken too, their contents kept; junk (tag robotica:voidable: cobble, dirt, gravel...) is voided, everything else goes into a container touching the table (never cleared), else on top of it. Spawn protection, the world border and BreakEvent/EntityPlaceEvent (claims) are respected: such blocks are skipped for free.
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

## Warp (module `warp`)

Getting home and travelling between bases. All teleports run on the server, cost FE, and work for every player on a server.
- Warp Pad (Age 1: Basic Circuit, Iron Casing, ender pearls; FE buffer 1M): a block you name in a small GUI. Pads belong to their owner; the owner can mark a pad public so friends can use it. Stand on a pad and right-click it to open the destination list. Cost: 5,000 FE + 20 FE per block of distance, taken from the departure pad. Same dimension only, until the pad gets a Rift Upgrade (Age 3 parts + Magma Core) that unlocks cross-dimension travel for a flat 100,000 FE.
- Recall Remote (Age 1, 400k FE): sneak-right-click a Warp Pad to bind it. Hold right-click for 3 seconds (any damage cancels) to teleport to that pad. 20,000 FE, 30 s cooldown, same dimension. Smithing upgrade to Rift Remote (Age 3) works across dimensions for 150,000 FE.
- Portal Projector (Age 4, registry id `gate_controller`): a single squat emitter block with a glowing lens, placed on the ground (it faces the player). Link two with a Linking Card (one Null Circuit) (sneak-right-click projector A, then B). While powered (200 FE/t idle) it projects a floating, swirling elliptical portal (2 wide, 3 tall, bottom edge 1 block above the projector, light level 15 while active, 6 idle). The portal is not made of blocks: it is drawn by a block entity renderer and detected by an AABB query every 2 ticks. Players, mobs and items that touch it arrive in front of the other projector, facing out (10,000 FE per entity). Works across dimensions.
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

Guard Drone, Wingman, Mole, Courier, Exo-Frame armor, the Magma and Antigrav bosses, Magma Reactor, Ender conduit, RS API integration, Create compat.

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
- Survey Rig (Age 2, 407 IE, 4.5x the Excavator): the lag-free quarry is the stronger one, so it is pricier, slower at base (1 ore / 5 s) and costs 2,000 FE per ore before the same steep speed curve. A typical overworld chunk holds a few hundred ores: about half an hour at base, a minute or two at x20 for roughly 40,000 FE per ore. Recipe unlocked by the Servo Age guide step; guide step "No Holes Barred" after "Dig Deep".
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
- Sprout pays 30 FE per harvested crop (`sproutFePerHarvest`), like Stumpy's per-log cost. Null Lance 20,000 → 8,000 FE per shot.
- Tool modules: auto-pickup and the void filter need an Auto-Pickup or Void Filter Upgrade installed at a Tinker's Bench; the Age 0 hammer and axe take none.

Earlier passes, still valid: Age 0 uses no iron (Stumpy and Sprout 25 copper, Tinker's Hammer 9, Felling Axe 6). Each ladder step costs 4-9x the one before (tool kits 8.6 / 312 / 2.7k / 9.3k IE, accumulators 28 / 220 / 1.7k, solar 12 / 102, Mk farm kits 46 / 259 / 6.6k). Magma Core temp recipe takes 2 Quantum Circuits so every core costs more than the one before. Portal Projector ~10k IE in line with the Null Drill (~16k IE). Config `replicatorEnergyPerTick` 160, `gateIdleCost` 200. Left alone on purpose: Combustion Generator 40 FE/t, Metal Press 20 FE/t, Stumpy 4 FE/t, Sprout 3 FE/t, drills 40-80 FE per block, Null Lance 20k FE per shot. Rift Remote cost stays 150,000 FE because a game test pins it.
