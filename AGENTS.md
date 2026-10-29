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
- `src/main/java/com/sebas/specialistdance/dance/DanceRegistry.java` — scans `config/specialist-dance/dances/<pack>/` for `frame_NNN.png` packs; the bundled dance is `specialist`.
- `src/main/java/com/sebas/specialistdance/dance/DanceTextures.java` — loads pack frames as `DynamicTexture`s and caches per-frame dimensions.
- `src/main/java/com/sebas/specialistdance/config/menu/SpecialistDanceConfigScreen.java` — music config screen (keybind `K`).
- `src/main/java/com/sebas/specialistdance/mixin/ClientPacketListenerMixin.java` — player pickup detection.
- `src/main/resources/` — `fabric.mod.json`, mixin config, lang files, animation frames, bundled `sounds/default.ogg`.
- `tools/` — dev-only, never shipped: `loom-mirror.sh` (metadata mirror) and `GifToFrames.java` (`java tools/GifToFrames.java <in.gif> <out-dir> [maxSize]` exports a GIF into a `frame_%03d.png` dance pack).
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

### Dance frames

- PNG is the only format that loads: `NativeImage.read` calls `PngInfo.validateHeader`, which demands the PNG magic before STB ever sees the bytes. GIF/JPEG/WebP are not options.
- GIF frames are normally **deltas** — a sub-rect at an offset with `doNotDispose` and transparent pixels marking "keep what was here". `tools/GifToFrames.java` must composite each frame onto a logical-screen canvas and honour the disposal methods; writing `reader.read(i)` straight to PNG produces half-empty frames.

### Mixin

- `handleTakeItemEntity` must be injected at `RETURN`: it hops threads at the start, so `HEAD` fires twice.
- Item entities arrive with an empty stack; the stack is filled by a later `SetEntityData` packet, so linking happens on the client tick.

## Release process

Follow `ROADMAP.md`: one release per phase. Update `mod_version` in `gradle.properties`, add a `CHANGELOG.md` entry, update `README.md`, mark the phase complete in `ROADMAP.md`, run `./gradlew build`, test in-game in **Survival**, and only then tag.
