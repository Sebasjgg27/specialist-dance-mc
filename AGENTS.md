# AGENTS.md

Client-only Fabric mod for Minecraft 1.21.11. It plays a 147-frame dance overlay when the player picks up a drop from an ore they just mined, with optional music and an in-game config screen.

## Working style: review and debug first

- The human writes the code; your default role is to **audit, debug and verify**, not to author features.
- Start by reading the current code and diff, reproducing the failure, and locating the root cause. Prioritize finding bugs, crashes, logic errors, edge cases, regressions and sloppy mistakes over writing new code.
- Report findings as `file:line`, the root cause, the evidence (log line, crash report, stack trace) and the smallest correct fix. Check `run/logs/latest.log`, `run/crash-reports/` and `run/hs_err_pid*.log` before guessing.
- Prefer minimal patches over rewrites. Preserve the human's naming, structure and style; do not reformat or "clean up" unrelated code.
- Do not implement proactively. If a change is needed, state the proposed fix and wait; only write code when explicitly asked, and then limit it to the agreed fix.
- When reviewing, focus on: null/empty states, config compatibility, thread safety and OpenAL state, mixin injection points, resource paths, and version/metadata consistency.

## Commands

```bash
./gradlew build        # compile + jar in build/libs/
./gradlew runClient    # dev client (needs the metadata mirror below)
./gradlew compileJava  # fast compile check
```

- There are **no automated tests** (`test` is `NO-SOURCE`); validation is `./gradlew build` plus in-game QA.
- Always use the bundled wrapper, target Java 21 (Gradle toolchain handles it; the machine default JDK may be newer).
- `./gradlew build --offline` only works when the metadata mirror is **not** configured. The mirror is reached over localhost and Gradle offline mode blocks it.

### Network workaround (temporary)

`gradle.properties` sets `loomCustomMetadataUrl=http://127.0.0.1:8765/1.21.11.json` because the Mojang endpoints are unreachable from this machine. Start the mirror with:

```bash
./tools/loom-mirror.sh   # serves the cached version metadata on 127.0.0.1:8765
```

If builds fail with a connection error to port 8765, the mirror is not running. If a stale `~/.gradle/caches/fabric-loom/*.lock` contains `disowned`, delete it. Remove `loomCustomMetadataUrl`, `tools/loom-mirror.sh` and the `customMinecraftMetadata` block in `build.gradle` once Mojang is reachable again.

## Layout

- `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java` — entrypoint, keybind, detection pipeline, tick cleanup, HUD animation.
- `src/main/java/com/sebas/specialistdance/SpecialistDanceConfig.java` — Gson config, defaults, `save()`/`load()`.
- `src/main/java/com/sebas/specialistdance/audio/MusicManager.java` — OpenAL/STBVorbis music playback.
- `src/main/java/com/sebas/specialistdance/config/menu/SpecialistDanceConfigScreen.java` — music config screen (keybind `K`).
- `src/main/java/com/sebas/specialistdance/mixin/ClientPacketListenerMixin.java` — player pickup detection.
- `src/main/resources/` — `fabric.mod.json`, mixin config, lang files, animation frames, bundled `sounds/default.ogg`.
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
- Re-validate the saved track name before building the `CycleButton` (a deleted user track would otherwise fail).

### Mixin

- `handleTakeItemEntity` must be injected at `RETURN`: it hops threads at the start, so `HEAD` fires twice.
- Item entities arrive with an empty stack; the stack is filled by a later `SetEntityData` packet, so linking happens on the client tick.

## Release process

Follow `ROADMAP.md`: one release per phase. Update `mod_version` in `gradle.properties`, add a `CHANGELOG.md` entry, update `README.md`, mark the phase complete in `ROADMAP.md`, run `./gradlew build`, test in-game in **Survival**, and only then tag.
