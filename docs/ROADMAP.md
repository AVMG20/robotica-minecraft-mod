# Robotica roadmap and handoff

Last updated 2026-10-04. Main branch builds, `./gradlew runGameTestServer` passes 68/68 (one replicator test is flaky, see below), `python3 scripts/audit_assets.py` reports 0 problems.

## Done (on main)

- Core: parts ladder per age, cells, upgrade cards, FE capability for items, machine menu/screen helpers, fragment merging for lang/tags.
- Power: Winding Crank + Mainspring, Combustion Generator, Solar Mk1/Mk2, Accumulator I-III, Copper/Gold Conduit networks, Charger, Metal Press.
- Automation: Stumpy, Sprout, Mk2-Mk4 farm kits, Excavator with upgrade cards, Supply Crate.
- Gear: Tinker's Hammer, Felling Axe, Bore/Servo/Magma/Null Drill, Chainsaw, 5 weapons, smithing upgrade chains, mode keys (V, B, G), area outline, HUD.
- Architect: Architect Table, matter, 24 style blocks (4 styles), 8 room types on a 9x9 plot grid, cosmetic drones.
- Replicator: Essence Vial, 3x3x3 Mob Replicator, harvest/spawn modes, core gate for Age 3/4 mobs.
- Warp: Warp Pads, Rift Upgrade, Recall/Rift Remote, Portal Projector (single block projecting a floating portal).
- Codex book, OP Creative Lab, `/robotica` commands.
- Two audits (27 issues fixed), sound design (52 custom events from vanilla sounds), balance pass (`docs/COSTS.md`, `scripts/cost_report.py`), GUI simplification, texture touch-ups.
- Dev tools: `./gradlew runShowcase` (real client screenshots of every block/GUI into `run-showcase/screenshots`), `scripts/contact_sheet.py`, `scripts/audit_assets.py`, Docker build.

## Unfinished work (stopped when usage ran out)

1. **Block texture overhaul (Opus)**: WIP commit on branch `worktree-agent-afc43a27d89f396c1` (worktree `.claude/worktrees/agent-afc43a27d89f396c1`). Not verified; only 3 files changed. Probably easier to restart than to merge. Goal: seamless building blocks without per-block borders, 2-4 random variants, light blocks as glowing panels (no lantern picture), emissive overlays (`neoforge_data` per face) for lights/screens, machines with proper front/side/top faces, shared material helpers in `scripts/pixelart.py`, `scripts/tile_preview.py`. Do not touch the Portal Projector assets.
2. **Flaky replicator test**: `replicatorHarvestsZombieIntoOutput` failed 1 in 4 runs (cycles ran, no rotten flesh in the output). WIP commit on branch `worktree-agent-ab1173a95fc27e115`, unverified. Find the root cause in `replicator/logic/Harvest.java` or test interference.
3. **Base builder expansion (Opus)**: lost when stopped, restart from scratch. Player's wishes:
   - room sizes 9x9, 12x12 and 15x15 on a 3-block grid, doors where footprints touch;
   - no pillars inside rooms, no vanilla furniture (no furnaces, chests);
   - purpose-built spaces: Hub/atrium, Machine Bay (machine pads + cable trenches), Farm Hall (beds + empty irrigation channels), Storage Wing (wall niches), Hangar/drone deck, Observatory dome, Courtyard, Workshop, Tower/stairwell; nice roofs, light strips, trim;
   - ghost blocks of queued buildings in the world;
   - drones that really build: fly out, remove blocks, place, return; more drones = faster;
   - table add-ons: Drone Bay (+1 drone), Foundation (fill under floors), Supply Link (pull from nearby inventories/RS), Range Extender;
   - simple visual GUI with little text.

## Next (after the above)

- v0.2 Defense: Guard Drone + Perimeter Post, Wingman, Mole, Command Tablet.
- v0.3 Exo-Frame armor (speed, water breathing, flight) and bosses (Scrap Colossus first); boss cores replace the temporary core recipes.
- Later: Survey Rig (virtual quarry), JEI/EMI + Jade plugins, Refined Storage API for the builder, Create crank compat, real play-testing on a server with friends.

## How to resume

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runGameTestServer && python3 scripts/audit_assets.py   # health check
./gradlew runShowcase                                            # screenshots
./gradlew runClient                                              # play
```

Agents should read `docs/AGENT_BRIEF.md` (rules) and `docs/DESIGN.md` (spec) first.
