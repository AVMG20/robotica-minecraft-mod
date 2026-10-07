# Robotica

NeoForge 1.21.1 Minecraft mod (Java 21), mod id `robotica`. Tech mod about robots, drones, machines, power, armor and
tools, built as a grind loop: better gear needs machine-made parts, which need more power. Played in modpacks next to
Mekanism/Thermal/Create, so balance should be similar to those mods (ours may be a bit better or worse) and use `c:` tags.
The user is the only developer.

## Find things fast
- `docs/DESIGN.md`: the spec, one section per module. Read the section you need, not the whole file.
- `docs/AGENT_BRIEF.md`: engineering rules (server safety, payloads, config, textures, tests).
- Code: `src/main/java/com/arno/robotica/<module>/` (`core` is shared; `client/` subpackages are client-only).
  Every module has `<Module>Module.init` and `client/<Module>Client.init`.
- Lang and tags: `src/main/fragments/<module>/` (merged at build time), never in `src/main/resources`.
- Generated data: `scripts/data/*.py` (recipes, models, guide steps) and `scripts/textures/*.py`. Edit the script, run
  it, commit both. Don't hand-edit generated JSON.
- Codex (in-game book): `src/main/resources/assets/robotica/codex/chapters.json`; guide steps in
  `scripts/data/codex_guide.py`.
- Wiki site (GitHub Pages, `docs/`): sources in `scripts/wiki/` (`items/*.json`, `upgrades.json`, `guides/*.md`,
  `multiblocks.json`, format in `FORMAT.txt`); run `python3 scripts/wiki/build_wiki.py`.
- Save tokens: grep for a symbol before opening files, read line ranges, and don't read generated files
  (`docs/wiki/data.json`, recipe/model JSON, `docs/COSTS.md`) unless needed.

## Build and test
- No local JDK: `scripts/docker-build.sh runGameTestServer` (all required game tests must pass) and
  `python3 scripts/audit_assets.py`. Stale `run/config/robotica-*-server.toml` files can make tests fail; delete them.

## Every change
- Keep the Codex and the wiki up to date with the feature: Codex chapter, guide unlocks, wiki item/upgrade/guide
  sources, `docs/DESIGN.md` section. Rebuild the wiki.
- Text everywhere (tooltips, Codex, wiki, lang) is short and direct. Item descriptions only add useful info:
  "Right-click to X", "Does Y", "Used for Z". No reasoning, design decisions or what it does not do
  ("never drops items", "will not X").
- Every balance number goes in the module's server config.

## Agents
- Use subagents for parallel, independent work (separate modules), each in its own worktree, at most 3 at a time. Tell
  them not to spawn their own subagents and to keep tool calls short.
- Before every release, audit the changes for bugs and issues (a subagent or the main thread), then fix what it finds.

## Git
- Commit and push to `main` freely. Plain imperative commit subjects, no `Co-Authored-By` or tool advertising.
- Releases (version bump in `gradle.properties` and README, tag `vX.Y.Z`) only when the user asks.
