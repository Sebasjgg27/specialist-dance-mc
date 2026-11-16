# AGENTS.md

Client-only Fabric mod for Minecraft 1.21.11. It plays a 147-frame dance overlay when an item the player configured reaches their inventory — a mined ore drop by default — with optional music and an in-game config screen.

## Working style: review and debug first

- The human writes the code; your default role is to **audit, debug and verify**, not to author features.
- Start by reading the current code and diff, reproducing the failure, and locating the root cause. Prioritize finding bugs, crashes, logic errors, edge cases, regressions and sloppy mistakes over writing new code.
- Report findings as `file:line`, the root cause, the evidence (log line, crash report, stack trace) and the smallest correct fix. Check `run/logs/latest.log`, `run/crash-reports/` and `run/hs_err_pid*.log` before guessing.
- Prefer minimal patches over rewrites. Preserve the human's naming, structure and style; do not reformat or "clean up" unrelated code.
- Do not implement proactively. If a change is needed, state the proposed fix and wait; only write code when explicitly asked, and then limit it to the agreed fix.
- When reviewing, focus on: null/empty states, config compatibility, thread safety and OpenAL state, mixin injection points, resource paths, and version/metadata consistency.

## Commands

```bash
./gradlew build                          # full jar (bundled dance + track) in build/libs/
./gradlew build -PmodVariant=lite        # lite jar, ships without bundled content
./gradlew runClient                      # dev client
./gradlew runClient -PmodVariant=lite    # dev client as the lite build ships
./gradlew compileJava                    # fast compile check
```

- There are **no automated tests** (`test` is `NO-SOURCE`); validation is `./gradlew build` plus in-game QA.
- Every release builds **both** variants; `full` goes to GitHub, `lite` goes to CurseForge/Modrinth.
- Always use the bundled wrapper, target Java 21 (Gradle toolchain handles it; the machine default JDK may be newer).
- If a stale `~/.gradle/caches/fabric-loom/*.lock` contains `disowned`, delete it and let the next build rebuild the Loom cache.

## Layout

- `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java` — entrypoint, keybind, detection pipeline, tick cleanup, HUD animation.
- `src/main/java/com/sebas/specialistdance/config/SpecialistDanceConfig.java` — Gson config, defaults, `save()`/`load()`, `sanitize()`/`migrate()`.
- `src/main/java/com/sebas/specialistdance/audio/MusicManager.java` — OpenAL/STBVorbis music playback.
- `src/main/java/com/sebas/specialistdance/dance/DanceRegistry.java` — scans `config/specialist-dance/dances/<pack>/` for `frame_NNN.png` packs, imports dropped GIFs, dedupes by SHA-256; the bundled dance is `specialist`.
- `src/main/java/com/sebas/specialistdance/dance/DancePackMeta.java` — reads a pack's `pack.json` (`frameDurationMs`, `speedMultiplier`, `track`, `source`).
- `src/main/java/com/sebas/specialistdance/dance/GifImporter.java` — composites a dropped `.gif` into a `frame_NNN.png` pack at runtime.
- `src/main/java/com/sebas/specialistdance/dance/DanceTextures.java` — loads pack frames as `DynamicTexture`s and caches per-frame dimensions.
- `src/main/java/com/sebas/specialistdance/config/menu/SpecialistDanceConfigScreen.java` — config screen (keybind `K`).
- `src/main/java/com/sebas/specialistdance/config/menu/DanceLayoutScreen.java` — **Position dance** editor: drag to move, scroll to zoom.
- `src/main/java/com/sebas/specialistdance/config/menu/OreTriggersScreen.java` — **Your rules** editor: the `rules` list with per-row toggle/delete, a **Trigger** cycle (`mined`/`pickup`/`mob`/`container`), **Source** and **Item** fields with TAB suggestions, and **Add rule** / **Reset** / **Clear** / **Undo** / **Redo**.
- `src/main/java/com/sebas/specialistdance/config/menu/ItemGridScreen.java` — creative-style picker over a supplied id list with an `iconOf` function, so one grid serves `BuiltInRegistries.ITEM`, `BLOCK` and `ENTITY_TYPE`: filter box, paged icon grid, returns the chosen id through a callback.
- `src/main/java/com/sebas/specialistdance/integration/ModMenuIntegration.java` — ModMenu entrypoint that opens the config screen.
- `src/main/java/com/sebas/specialistdance/mixin/ClientPacketListenerMixin.java` — player pickup detection.
- `src/main/resources/` — `fabric.mod.json`, mixin config, lang files, animation frames, bundled `sounds/default.ogg`.
- `tools/` — dev-only, never shipped: `GifToFrames.java` (`java tools/GifToFrames.java <in.gif> <out-dir> [maxSize]` exports a GIF into a `frame_%03d.png` dance pack).
- `ROADMAP.md` / `CHANGELOG.md` / `README.md` — phase plan, release history, user docs.

## Conventions

- 4-space indentation; imports grouped Java, Fabric, Minecraft, third-party.
- Java 21, Mojang official mappings; write against named classes and let Loom remap.
- Keep the mod client-only: no server logic or network payloads.
- Do not add comments unless behavior is non-obvious. Match surrounding code style.
- Use the config-gated `log(...)` helper instead of `System.out.println` in normal paths.
- New config options go on `SpecialistDanceConfig` with sensible defaults so old files keep working.
- Do not commit `build/`, `run/`, `.gradle/`, or `bin/`, and do not commit, amend, push or tag unless explicitly asked.

## Critical gotchas

### Audio (OpenAL)

- `AL10` calls throw `No ALCapabilities instance has been set` unless `ALC.createCapabilities(device)` and `AL.createCapabilities(alcCaps)` run first, with a context current. Init does this in `MusicManager.init()`; keep that ordering.
- **Never** end a call with `ALC10.alcMakeContextCurrent(0L)`. Minecraft's sound engine keeps its context current process-wide; clearing it causes endless `Stop: Invalid operation` / `Allocate new source: Invalid operation` errors. Use `makeContextCurrent()` / `restoreContext(previous)`; they prefer `ALC_EXT_thread_local_context` so Minecraft's context is untouched.
- Do **not** free STB decode output with `MemoryUtil.memFree`; it is allocated by stb's allocator and causes a SIGSEGV in jemalloc. Decode with `stb_vorbis_open_memory` + `stb_vorbis_get_samples_short_interleaved` into `MemoryUtil.memAllocShort`, then free your own buffer.
- Music is gated by `config.musicEnabled` in `triggerDance()`, not in `play()`, so the menu preview always works. Closing the config screen stops playback and saves.

### GUI

- `Screen.renderWithTooltipAndSubtitles` already draws the background; do not call `renderBackground` in `render`.
- `Checkbox` has a lowercase `b`; use `Checkbox.builder(...)`.
- `CycleButton` composes its own label as `message + ": " + stringifier(value)`. The stringifier must return the bare value (`Component::literal`) or you get `Music track: Track: test-1`.
- The HUD still renders while a `Screen` is open (`Gui.render` only early-returns for `LevelLoadingScreen`), but the screen paints over it — so a dance preview cannot be seen from inside the config menu; it fires from `onClose()`.
- Re-validate the saved track and dance names before building their `CycleButton`s (a deleted user track or pack would otherwise fail).
- `GuiGraphics.blit`'s last two ints are **UV divisors**, not a target size: pass the texture's real dimensions and put the scaled size in the `width`/`height` args, otherwise the image is cropped or squashed.
- The config screen stacks its rows with a computed cursor (`ROWS`/`GAP`) and pushes `doneY` below the block. Anchoring rows at `height / 4` makes the last row collide with `Done` at the dev window's 240 logical pixels.
- `ObjectSelectionList` in 1.21.11 takes `(Minecraft, width, height, y, defaultEntryHeight)`; it has no x argument, so call `setX(x)` separately when positioning it. The 1-arg `addEntry` overload uses the entry's own measurement; use the 2-arg overload to set a specific row height.
- A tooltip set inside a list row's `renderContent` does appear: `Screen.renderWithTooltipAndSubtitles` calls `GuiGraphics.renderDeferredElements()` *after* `render`. `setTooltipForNextFrame` is therefore safe per-row, but only for the hovered row.
- Snapshot registries in the **constructor**, never in `init()`: `init()` re-runs on every resize and would rebuild (and re-sort) the whole id list. `ItemGridScreen` fills `allIds` in its ctor and only recomputes columns/rows in `init()`.
- Returning to a screen **re-runs `init()`**: `Minecraft.setScreen` calls `Screen.added()` then `init(width, height)` on whatever screen it installs, including the parent you came back from (verified in bytecode). That is why picking an id in `ItemGridScreen` appears in `OreTriggersScreen`'s `EditBox` — the boxes are rebuilt from the fields. Keep edits in fields, never in widget state.
- `BuiltInRegistries.ITEM` is a *defaulted* registry: `getValue` returns `minecraft:air` for an unknown id instead of `null`. Check `ItemStack.isEmpty()` (or the id) rather than trusting a null test.

### Dance frames

- PNG is the only format that loads: `NativeImage.read` calls `PngInfo.validateHeader`, which demands the PNG magic before STB ever sees the bytes. GIF/JPEG/WebP are not options.
- GIF frames are normally **deltas** — a sub-rect at an offset with `doNotDispose` and transparent pixels marking "keep what was here". `tools/GifToFrames.java` must composite each frame onto a logical-screen canvas and honour the disposal methods; writing `reader.read(i)` straight to PNG produces half-empty frames.

### Mixin

- `handleTakeItemEntity` must be injected at `RETURN`: it hops threads at the start, so `HEAD` fires twice.
- Item entities arrive with an empty stack; the stack is filled by a later `SetEntityData` packet, so linking happens on the client tick.

### Config

- Gson runs the field initialisers **before** binding JSON keys, so a field defaulting to `CURRENT_VERSION` reads back as current even from a file written before versioning existed. `load()` therefore parses the raw `JsonObject` first and treats a missing `configVersion` key as version `0`.
- `migrate()` runs **after** `sanitize()`, so the version stamp that eventually reaches `save()` holds clamped values, not the raw ones from disk.
- A `pack.json` number that means "no override" uses the `-1` sentinel (`frameDurationMs`, `speedMultiplier`), not `null` and not `0` — `"frameDurationMs": 0` and `"speedMultiplier": 0` must both fall back to the global, and `0` would otherwise clamp to the slowest legal speed.
- A pack's `speedMultiplier` **overrides** the global **Animation speed** slider rather than multiplying with it, matching the `frameDurationMs` precedent. A user whose slider "does nothing" is playing a pack that declares its own speed.
- `ORE_PATH` must include `_`: nearly every vanilla id contains an underscore, and a path class that rejects it makes `normalizeOreId()` return `null`, so `sanitize()` **deletes the rule** at load. That removed all ten presets on every launch until 2026-11-12.
- `sanitize()` substitutes `defaultRules()` only when `rules` is `null`. An empty list from **Clear** is a valid state and survives reload, so nothing restores the presets except **Reset**.
- The undo/redo stacks are `transient` (Gson must not serialize them) and `Rule` is mutable, so a snapshot has to deep-copy each rule — `copyOfRules()` — or undo rewires the same objects it just saved.
- `load()` migrates a v3 `blockRules`/`pickupRules` split into `rules` **before** `sanitize()` runs, from the raw `JsonObject`. There is no v2 (`oreDrops`/`disabledOres`) path — such a file yields `rules == null`, so `sanitize()` fills in `defaultRules()`; raise `CURRENT_VERSION` in the same change as a new field and add the branch if a shipped version ever needs it.
- A new trigger belongs to `RULE_TRIGGERS`, `SpecialistDanceClient` and `OreTriggersScreen` in the same change; `hasTrigger(trigger)` gates the whole scan, so a trigger no rule uses costs nothing.

### ModMenu

- `onInitializeClient()` returns early when `config.enabled` is false, **before** `musicManager` is constructed. `getModConfigScreenFactory()` must null-guard `getMusicManager()` too, or Mod Menu's Config button throws an NPE in `init()`; returning `null` is Mod Menu's "no config screen" signal.
- `modCompileOnly` keeps ModMenu off the dev runtime classpath, so the entrypoint is dead code in `runClient`. `modRuntimeOnly` is also declared so the integration is actually exercisable in the dev client without shipping ModMenu as a dependency.

### Build variants

- `modVariant` is build-time only — nothing about it reaches the jar. The code detects content with a resource probe (`DanceRegistry.BUNDLED_PRESENT` on `frame_000.png`, `MusicManager.BUNDLED_PRESENT` on `default.ogg`). Do not add a generated flag, a `fabric.mod.json` field or a config key for it.
- `base.archivesName` carries the version, the loader, the variant and `minecraft_version` (`specialist-dance-v1.6.1-fabric-1.21.11.jar`); the archive tasks clear `archiveVersion` so Gradle does not append the project version a second time.
- `processResources` takes `inputs.property "variant"`, so switching variants invalidates the cache. A jar with the wrong content set means an exclude pattern broke, not the fingerprint.
- `DanceRegistry.find()` must stay total: it answers with the zero-frame `NO_DANCE` instead of `null` or a bundled dance that is not in the jar, which is what keeps every caller free of null checks. A caller that assumes `frameCount() > 0` re-opens the lite crash.
- On a lite install with no packs, `getAvailableDances()` and `getAvailableTracks()` are empty; guard `get(0)` before building a `NameList`, and never let a trigger write `lastTriggerMillis` before a dance is known.

## Release process

Follow `ROADMAP.md`: one release per phase. Update `mod_version` in `gradle.properties`, add a `CHANGELOG.md` entry, update `README.md`, mark the phase complete in `ROADMAP.md`, run `./gradlew build` **and** `./gradlew build -PmodVariant=lite`, test in-game in **Survival**, and only then tag.
