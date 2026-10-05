# Brief for module agents

You are building one module of Robotica, a NeoForge 1.21.1 Minecraft mod (Java 21). You work in a git worktree of the repo. Other agents build other modules in parallel, so the ownership rules below are hard rules.

## Read first
1. `docs/DESIGN.md`: the whole spec. Your module's section and "Engineering rules" are binding. Balance numbers are starting points; you may tune them if you note why.
2. `src/main/java/com/arno/robotica/core/**`: energy (`EnergyItem`, `ItemEnergy`, `MachineEnergyStorage`, `EnergyUtil`), parts (`CoreItems`), upgrade cards (`Upgrades`, `UpgradeKind`), `MachineMenu`, `client/MachineScreen`, `SyncedBlockEntity`, `CoreConfig`, `RoboticaTab`.
3. `scripts/pixelart.py`, `scripts/textures/core.py`, `scripts/data/core_recipes.py` for texture and recipe conventions.

## Build and test
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew compileJava --console=plain -q
./gradlew runGameTestServer --console=plain     # headless server, runs all game tests, ~30 s
```
Nobody can run the game client here. Rely on compiling, game tests and careful reading of vanilla/NeoForge sources (decompiled sources are in the Gradle cache; `find ~/.gradle -name "*neoforge*sources*.jar"` or read classes via `javap`).

The game test `everyitemhasarecipe` lists Robotica items without a recipe. Until every module is merged it fails for `iron_plate`, `copper_plate`, `gold_plate` (hand plate recipes belong to `gear`, Metal Press recipes to `power`) and for items of other modules. None of YOUR items may appear in that list.

## Ownership
- Create or edit only:
  - `src/main/java/com/arno/robotica/<module>/**`
  - `src/main/fragments/<module>/**` (lang + tags, merged at build time)
  - `scripts/textures/<module>.py`, `scripts/data/<module>_*.py`
  - new files under `src/main/resources/assets/robotica/**` and `src/main/resources/data/robotica/**` named after your own items/blocks
- Never edit core, `Robotica.java`, `RoboticaClient.java`, `build.gradle`, `gradle.properties`, `docs/**`, or another module's files. Need something from core? Put a local helper in your module and mention it in your report.
- Entry points exist already: `<Module>Module.init(modBus, container)` (common) and `client/<Module>Client.init(modBus, container)` (client only). Register DeferredRegisters, config, payloads, capabilities and listeners from there. Add creative tab entries with `RoboticaTab.add(...)` during init, in a sensible order.
- Lang and tags only in `src/main/fragments/<module>/assets/robotica/lang/en_us.json` and `src/main/fragments/<module>/data/<namespace>/tags/...`. Never in `src/main/resources`.

## Engineering musts
- Dedicated server safe. Anything that touches `net.minecraft.client.*` or `com.mojang.blaze3d.*` lives in `<module>/client/**` and is only reached from `<Module>Client` or `@EventBusSubscriber(modid = Robotica.MODID, value = Dist.CLIENT)` classes. No `Minecraft.getInstance()` in common code, not even behind an `isClientSide` check.
- Server authoritative. Client input goes through `CustomPacketPayload` + `RegisterPayloadHandlersEvent` (`event.registrar("1")`, payload ids `robotica:<module>_<name>`), validated on the server.
- Multiplayer: store owners as UUIDs, never assume a single player, make per-player state (cooldowns, queues) keyed by UUID and cleaned up on logout.
- Balance values in a server config: `ModConfig.Type.SERVER`, file `robotica-<module>-server.toml`, with translation keys `robotica.configuration.<key>` in your lang fragment.
- Interop: FE through `Capabilities.EnergyStorage.BLOCK/ITEM`, items through `Capabilities.ItemHandler.BLOCK`, registered in `RegisterCapabilitiesEvent`. That is what makes Thermal, Mekanism, Refined Storage and AE2 work.
- Energy items implement `EnergyItem` (capability is automatic), machine buffers use `MachineEnergyStorage`, upgrade slots use `Upgrades`. Pass base costs through `CoreConfig.scaleEnergy` / `scaleInterval` / `scaleGeneration`.
- GUIs extend `MachineMenu` and `MachineScreen` (procedural, no GUI textures). Open menus with `serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos))`, menu types with `IMenuTypeExtension.create`. Register screens in your client class via `RegisterMenuScreensEvent`.
- Block entities tick on the server only. Spread area scans over ticks with a cursor. No full-area scans per tick.
- Recipes follow the component ladder: cheap to start, expensive to upgrade. Use `c:` tags for vanilla materials (`#c:ingots/iron`, `#c:plates/iron`, `#c:gems/diamond`...). Each tier's recipe should consume the previous tier's item when it is an upgrade.
- Every item and block you add needs: a recipe (or the obtain path from the spec), texture (generate with `scripts/textures/<module>.py` using `pixelart.Canvas`; run it), model (blocks: hand-written blockstate + block model + item model; custom element models are welcome for robots and machines), lang name, block loot table (drop itself), `minecraft:mineable/pickaxe` (or axe) tag via fragments.
- Sounds: use vanilla `SoundEvents` creatively, no custom sound files.
- Add at least two GameTests for your module's core logic in `<module>/test`, using `@GameTestHolder(Robotica.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"` (a 3x3x3 empty structure).

## Finish
Compile clean, none of your game tests failing, none of your items in the no-recipe list. Then commit in your worktree: `git add -A && git commit -m "<short imperative subject>"`. No `Co-Authored-By` trailer, no tool advertising (the repo owner's rule).

Final report, short: what works, what is stubbed or skipped, known risks, anything you need from core.
