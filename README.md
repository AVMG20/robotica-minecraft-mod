# Robotica

A NeoForge 1.21.1 mod about robots, drones, farms, machines and power tools, from the stone age to the End. Uses Forge Energy, so it works next to Thermal, Mekanism, Refined Storage and AE2. Built for multiplayer servers.

Design: [docs/DESIGN.md](docs/DESIGN.md).

## Run it

Needs Java 21 (`brew install openjdk@21` on a Mac).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runClient          # dev client with the mod loaded (offline player "Dev")
./gradlew build              # mod jar in build/libs/
./gradlew runGameTestServer  # headless server + game tests (also proves server safety)
```

### Without a local Java: Docker

```sh
scripts/docker-build.sh                    # jar in build/libs/
scripts/docker-build.sh runGameTestServer  # headless game tests
```

The first run takes a few minutes; downloads are cached in the `robotica-gradle` Docker volume. Starting the game client still needs Java on your machine (or use the jar in a normal launcher).

## Testing in game

Every player gets the Robotica Codex on first join. Operators see a Creative Lab page in it (all items, age kits, charge, spawn mobs, weather), and can use:

```
/robotica kit <0-4>    /robotica charge    /robotica charge_target
/robotica give <id> [count]    /robotica spawn <entity>    /robotica codex
```

## Layout

| Path | What |
|---|---|
| `src/main/java/com/arno/robotica/core` | shared energy, parts ladder, upgrade cards, menu/screen bases |
| `.../power` `.../automation` `.../gear` `.../architect` `.../replicator` `.../warp` `.../codex` | feature modules, each with its own registries and `client/` package |
| `src/main/fragments/<module>/` | lang and tag fragments, merged at build time |
| `scripts/textures/*.py` | pixel-art texture generators (`python3 scripts/textures/<module>.py`) |
| `scripts/data/core_recipes.py` | core recipe generator |
| `scripts/contact_sheet.py` | renders all textures into one PNG for a visual check |
