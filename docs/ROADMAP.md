# Robotica roadmap

Last updated 2026-10-08 (0.6). `./gradlew runGameTestServer` passes and `python3 scripts/audit_assets.py` reports 0
problems. Specs per module in `docs/DESIGN.md`; plans per release in `docs/plans/`.

## What's in the mod

| Area | Contents |
|---|---|
| Core | Parts ladder per age, cells, upgrade cards (Mk rule), one module framework, multiblock framework, side config |
| Power | Winding Crank, Combustion Generator, Solar Mk1-Mk4, RTG, Accumulators, Tesla Coils, Charger, Wireless Charger, Metal Press |
| Big energy | Capacitor Bank, Fission Reactor, Fusion Reactor |
| Industry | Thorium, pyrolite and resonite ores, alloys, Alloy Smelter / Centrifuge / Assembler Mk1-Mk4 |
| Ore processing | Grinder and Electric Furnace Mk1-Mk4, grinding media |
| Logistics | Item pipes Mk1-Mk4 |
| Automation | Stumpy, Sprout (farm kits), Rancher Mk1-Mk2, Excavator and Survey Rig Mk1-Mk4, Supply Crate |
| Drones | Mining Drone Mk1-Mk3, Sentry, Courier and Hauler Mk1-Mk2 |
| Gear | Drills, Chainsaw, five FE weapons, Rivet Gun projectiles, Tinker's Bench modules, Lamp Rod and Spark Lamps |
| Exo-Frame | Armor Mk1-Mk4, modules with levels, core socket set bonuses, HUD |
| Architect | Architect Table: plot grid, inner walls and doorways, four styles, demolish |
| Replicator, Warp, Storage | Mob Replicator, Warp Pads / remotes / portals, Storage Terminal |
| Bosses | Scrap Colossus (Servo Core), Forge Tyrant (Magma Core) |
| Codex and site | In-game book with guide steps; GitHub Pages wiki generated from the repo |

## Next

1. **End boss** for the Antigrav Core (the last temporary core recipe).
2. **Item storage network**: link terminals and chests, request crafts from the Assembler and Alloy Smelter.
3. **Robots you can see working** and Program cards; a **Drone Hub** with a route map.
4. **Compat**: EMI, Create rotation for the Winding Crank, Refined Storage API for the Architect Table.
5. Small: Exo armor trims, a better Architect showcase shot.
