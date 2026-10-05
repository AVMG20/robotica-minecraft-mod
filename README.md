# Robotica

A NeoForge 1.21.1 mod about robots, drones, farms, machines, power tools and powered armor, from the stone age to the End. It uses Forge Energy, so it works next to Thermal, Mekanism, Refined Storage and AE2, and it runs on multiplayer servers.

- Design: [docs/DESIGN.md](docs/DESIGN.md)
- Roadmap: [docs/ROADMAP.md](docs/ROADMAP.md)
- Costs per item: [docs/COSTS.md](docs/COSTS.md)

## Play it

1. Build the jar: `./gradlew build` → `build/libs/robotica-0.1.0.jar`.
2. Install NeoForge 21.1.252 for Minecraft 1.21.1 (installer from neoforged.net, "Install client").
3. Copy the jar into `~/Library/Application Support/minecraft/mods` (Windows: `%APPDATA%\.minecraft\mods`).
4. Start the "neoforge" installation in the Minecraft Launcher.

New players get the Robotica Codex on first join. Its "Next steps" page and the Robotica advancement tab guide you through the ages.

## Develop

Needs Java 21 (`brew install openjdk@21`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runClient           # dev client with the mod (offline player "Dev")
./gradlew runGameTestServer   # headless server + game tests
./gradlew runShowcase         # screenshots of every block and GUI, then quits
python3 scripts/audit_assets.py   # models, textures, names, loot tables (run after the game tests)
```

No local Java? `scripts/docker-build.sh` builds the jar in Docker, `scripts/docker-build.sh runGameTestServer` runs the tests.

Operators can test in game with the Codex's Creative Lab page or:

```
/robotica kit <0-4>    /robotica charge    /robotica charge_target
/robotica give <id> [count]    /robotica spawn <entity>    /robotica codex
```

## Layout

| Path | What |
|---|---|
| `src/main/java/com/arno/robotica/core` | Energy, parts ladder, upgrade cards, sounds, GUI helpers |
| `.../power` `automation` `drones` `gear` `exo` `architect` `replicator` `warp` `codex` | Feature modules, each with its own registries and a `client/` package |
| `src/main/fragments/<module>/` | Lang and tag fragments, merged at build time |
| `scripts/textures/*.py` | Pixel-art texture generators |
| `scripts/data/*.py` | Recipe, model and guide generators |
| `scripts/contact_sheet.py`, `scripts/model_preview.py` | Visual checks of textures and models |
