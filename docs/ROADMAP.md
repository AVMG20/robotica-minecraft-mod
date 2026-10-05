# Robotica roadmap

Last updated 2026-10-05. Main builds, `./gradlew runGameTestServer` passes 101/101 and `python3 scripts/audit_assets.py` reports 0 problems. Nothing has been play-tested in a real client yet.

## What's in the mod

| Area | Contents |
|---|---|
| Core | Parts ladder per age, cells, stackable upgrade cards, energy items, shared GUI helpers, 52+ sound events |
| Power | Winding Crank + Mainspring, Combustion Generator, Solar Mk1/Mk2, Accumulator I-III, Copper/Gold Conduits, Charger, Metal Press |
| Automation | Stumpy (trees), Sprout (crops), Mk2-Mk4 farm kits, Excavator (real quarry), Survey Rig (virtual quarry), Supply Crate |
| Drones | Mining Drone (3x3 tunnels), Sentry Drone (guard/follow/stay), Courier Drone (routes + filters) |
| Gear | Tinker's Hammer, Felling Axe, Bore/Servo/Magma/Null Drill, Chainsaw, 5 weapons, smithing upgrades, V/B/G keys |
| Exo-Frame | Four armor pieces with modules (speed, night vision, rebreather, double jump, shield, flight, magnet), Mk2 |
| Architect | Architect Table: 9x9 building shells on a plot grid, 4 styles, doors where buildings touch |
| Replicator | Essence Vial, 3x3x3 Mob Replicator, harvest/spawn modes |
| Warp | Warp Pads, Rift Upgrade, Recall/Rift Remote, Portal Projector |
| Codex | Guide book, 36-step advancement guide, OP Creative Lab, `/robotica` commands |

## Next

1. **Play-test.** Run `./gradlew runClient` (or `runShowcase` for screenshots) and check GUIs, models, glow layers, keybinds, the Codex guide page, HUDs and drones. Fix what looks or feels wrong.
2. **Tesla Coil wireless power** (replaces the conduits):
   - Torch-sized coil, placeable on floors, walls and ceilings. High transfer rate, limited number of links.
   - A network starts at a coil placed on a Robotica storage block (Accumulator). Coils link to machines or to other coils.
   - Coil-to-coil chaining extends range at 5% power loss per hop; each coil-to-coil link uses one link slot.
   - Tiers by link count: 4, 8, 12, 16, 32, each more expensive.
   - Linking tool: click a coil, then a machine. The clicked face is the side power enters.
   - Remove Copper/Gold Conduits once this works.
3. **Bosses**, Scrap Colossus first. Boss cores then replace the temporary core recipes.
4. **Art leftovers:** redraw the warp remotes, linking card, rift upgrade, sentry and courier drone icons.
5. **Compat:** JEI and Jade are done (optional, see README). Still open: EMI, Refined Storage API for the Architect Table, Create rotation for the Winding Crank, opening JEI from the Codex.
6. **Multiplayer test** on a dedicated server with friends.

## Working on it

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runGameTestServer && python3 scripts/audit_assets.py   # health check
./gradlew runClient                                              # play
./gradlew runShowcase                                            # screenshots into run-showcase/screenshots
```

Agents read `docs/AGENT_BRIEF.md` (rules) and `docs/DESIGN.md` (spec) first. Balance numbers live in `docs/COSTS.md` (`python3 scripts/cost_report.py`).
