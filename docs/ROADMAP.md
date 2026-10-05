# Robotica roadmap

Last updated 2026-10-05. Main builds, `./gradlew runGameTestServer` passes 105/105 and `python3 scripts/audit_assets.py` reports 0 problems. Nothing has been play-tested in a real client yet.

## What's in the mod

| Area | Contents |
|---|---|
| Core | Parts ladder per age, cells, stackable upgrade cards, energy items, shared GUI helpers, 52+ sound events |
| Power | Winding Crank + Mainspring, Combustion Generator, Solar Mk1/Mk2, Accumulator I-III, Tesla Coil I-V + Tesla Linker (wireless power), Charger, Metal Press |
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
2. **Bosses**, Scrap Colossus first. Boss cores then replace the temporary core recipes.
3. **Art leftovers:** redraw the warp remotes, linking card, rift upgrade, sentry and courier drone icons.
4. **Compat:** JEI/EMI and Jade plugins, Refined Storage API for the Architect Table, Create rotation for the Winding Crank.
5. **Multiplayer test** on a dedicated server with friends.

Done: **Tesla Coil wireless power** replaced the conduits (coils on Accumulators or generators, Tesla Linker, 5 tiers by link count, 5% loss per coil hop, face-specific insertion, arcs). Play-test the arcs and the coil model in a client.

## Working on it

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runGameTestServer && python3 scripts/audit_assets.py   # health check
./gradlew runClient                                              # play
./gradlew runShowcase                                            # screenshots into run-showcase/screenshots
```

Agents read `docs/AGENT_BRIEF.md` (rules) and `docs/DESIGN.md` (spec) first. Balance numbers live in `docs/COSTS.md` (`python3 scripts/cost_report.py`).
