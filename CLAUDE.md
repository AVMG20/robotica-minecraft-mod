# Robotica

NeoForge 1.21.1 Minecraft mod (Java 21), mod id `robotica`. Tech mod about robots, drones, machines, power, armor and
tools, built as a grind loop: better gear needs machine-made parts, which need more power. Played in modpacks next to
Mekanism/Thermal/Create, so balance should be similar to those mods (ours may be a bit better or worse) and use `c:` tags.
The user is the only developer.

## Project structure
- Code: `src/main/java/com/arno/robotica/<module>/` (`core` is shared; `client/` subpackages are client-only).
  Every module has `<Module>Module.init` and `client/<Module>Client.init`.
- Lang and tags: `src/main/fragments/<module>/` (merged at build time), never in `src/main/resources`.
- Generated data: `scripts/data/*.py` (recipes, models, guide steps) and `scripts/textures/*.py`. Edit the script, run
  it, commit both. Don't hand-edit generated JSON.
- Codex (in-game book): `src/main/resources/assets/robotica/codex/chapters.json`; guide steps in
  `scripts/data/codex_guide.py`. Layout pages (`"layout": "<id>"`) draw a build from `scripts/wiki/multiblocks.json`,
  copied by `scripts/data/codex_multiblocks.py`.
- Wiki site (GitHub Pages, `docs/`): sources in `scripts/wiki/` (`items/*.json`, `upgrades.json`, `guides/*.md`,
  `multiblocks.json`, format in `FORMAT.txt`); run `python3 scripts/wiki/build_wiki.py`.

## Build and test
- JDK 21 (Homebrew, not on the default path): `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
  Tests: `./gradlew runGameTestServer` (or `scripts/docker-build.sh runGameTestServer`; all required game tests must
  pass) and `python3 scripts/audit_assets.py`. Screenshots: `scripts/showcase.sh` (real client, ~8 min, add new shots to `scripts/shots.py`);
  `scripts/showcase.sh core_reactor bank` only builds and shoots the shots whose name contains one of the words and
  leaves the other `docs/shots` alone. Stale `run/config/robotica-*-server.toml` files can make tests fail; delete them.

## Engineering rules
- Dedicated-server safe: `net.minecraft.client.*` and `com.mojang.blaze3d.*` only in `client/` packages, reached from
  `<Module>Client` or `Dist.CLIENT` subscribers. No `Minecraft.getInstance()` in common code.
- Server authoritative: client input goes through payloads (`robotica:<module>_<name>`), validated on the server.
- Multiplayer: owners and per-player state keyed by UUID, cleaned up on logout.
- Block entities tick on the server; spread area scans over ticks, never load chunks from a scan, no per-frame or
  per-tick allocations in hot paths.
- Interop through NeoForge capabilities (FE, items). Energy items implement `EnergyItem`, buffers use
  `MachineEnergyStorage`, GUIs extend `MachineMenu`/`MachineScreen`.
- Server config (`robotica-<module>-server.toml`) only for main knobs; other numbers stay constants.
- Every item/block: recipe on the component ladder, generated texture and model, lang name, loot table, mineable tag.
  Sounds reuse vanilla sound files through `sounds.json`. Game tests in `<module>/test` for core logic.
- Balance numbers live in the data scripts and code; `python3 scripts/cost_report.py` prints raw-material costs.

## Every change
- Keep the Codex and the wiki up to date with the feature: Codex chapter, guide unlocks, wiki item/upgrade/guide
  sources. Rebuild the wiki. No other docs: the only Markdown files are this one and `README.md`.
- Text everywhere (tooltips, Codex, wiki, lang) is short and direct. Item descriptions only add useful info:
  "Right-click to X", "Does Y", "Used for Z". No reasoning, design decisions or what it does not do
  ("never drops items", "will not X").
- Say each thing once. The Codex and the wiki never repeat what a tooltip, the recipe view or another page already
  says: no stat lists, recipes or "works with other mods" lines. Keep only real tips; an empty page beats filler.

## Git
- Commit and push to `main` freely. Plain imperative commit subjects, no `Co-Authored-By` or tool advertising.
- Releases (version bump in `gradle.properties` and README, tag `vX.Y.Z`) only when the user asks.
- Before every release, audit the changes for bugs and issues, then fix what the audit finds.
