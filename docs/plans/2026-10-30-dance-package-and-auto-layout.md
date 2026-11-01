# Dance Packages + Auto-Layout Implementation Plan

> **For agentic workers:** steps use checkbox (`- [ ]`) syntax. This project has **no automated tests** (`./gradlew test` is `NO-SOURCE`), so every "verify" step is a build plus a recorded in-game observation from `run/logs/latest.log` with `"debug": true`. A compiled fix is not proof it works: 🛠 ≠ 🧪.

**Goal:** make a new dance arrive in the game with as little typing as possible — drop a GIF (or a bundle of frames + audio) into the dances folder, have **Rescan** pick it up, convert it if needed, and immediately open the **Position dance** editor for it.

**Architecture:** three layers, each usable alone. (1) `DanceRegistry.scan()` becomes an *add-aware* scan that returns the packs it has not seen before, and the client queues those names and opens `DanceLayoutScreen` for each. (2) Each pack folder gains an optional `pack.json` holding `frameDurationMs` and a track name, which the registry reads into the `Dance` record; that is what makes speed and music travel with the dance. (3) A bundle is a **folder** (or a zip of that folder) containing `frame_NNN.png` + `pack.json` + `audio.<ext>` — no new file type.

**Tech Stack:** Java 21, Fabric + Loom (Minecraft 1.21.11), Mojang mappings, Gson for `pack.json`, `javax.imageio` for GIF decode, `java.util.zip` for bundle import, OpenAL/STB for audio.

**Spec:** this file. Facts it argues from, all measured on 2026-10-30:
- GIFs in the wild here are **100 ms per frame** (`zelda.gif` 29 frames, `abu-twinkle-eyes.gif` 21 frames, delay min = max = 100 ms). The shipped `frameDurationMs` default is **50**, so a dropped GIF plays 2× too fast; the QA value 200 was 2× too slow.
- `NativeImage.read` demands the PNG magic before STB sees the bytes, so **frames must be PNG at render time**. A container holding a raw GIF still needs conversion — see Task 7 for who does it.
- `DanceRegistry.countFrames` only counts `frame_*.png`, so a pack folder may already hold `pack.json` and an audio file with **zero** registry changes. That is why the bundle is a folder, not a format.
- `MusicManager.scanUserTracks()` (`MusicManager.java:170`) reads one global folder, `config/specialist-dance/music`, and `play()` resolves names only from that map (`:326`). Per-pack audio needs a resolver, not a new decoder.
- Layout lives in **global** config (`positionX`, `positionY`, `stageWidthPercent`, `stageHeightPercent`) — one stage for every dance. `DanceLayoutScreen.currentFrame()` always previews `config.activeDance` frame 0 (`DanceLayoutScreen.java:175`).

## Global Constraints

- Client-side only. No server logic, no network payloads.
- Keep the human's naming and structure; 4-space indent; no new comments unless behavior is non-obvious.
- New config fields go on `SpecialistDanceConfig` with defaults so a 1.4.0 file still loads.
- Do not decode GIF at runtime without a verified fallback path.
- Never trust a file extension to decide a format: sniff magic bytes (`PK\x03\x04` for zip, PNG `\x89PNG`).
- Do not commit `build/`, `run/`, `.gradle/`, `bin/`. No commit, tag or push without an explicit ask.
- Build needs the metadata mirror: `./tools/loom-mirror.sh` in a second terminal (Mojang endpoints unreachable from this machine).

## Release placement

Phase 6 (`v1.5.0`) is 🧪 and uncommitted with three observations still owed (`cooldownMs` above 0, `music ended while looping`, the `countFrames` reason lines). Rule 1 is one phase = one release, and rule 4 needs a debt box closed. **Close 1.5.0 first.** This plan is Phase 7 (`v1.6.0`) work: Tasks 1-4 land the auto-layout, Tasks 5-8 land the package. Ship A without B if B stalls.

## Format decision (answer to "should we invent `.mcgif` / `.specialgif`?")

**No.** A proprietary extension buys nothing here and costs three real things:

1. **It does not solve the actual problem.** The mod cannot render GIF bytes, whatever wraps them. A `.mcgif` would still store PNG frames, so it is a folder or a zip with a different name — the conversion step stays.
2. **Nobody can make or open one.** No file association, no preview, no viewer, no sharing on any site. Users already hold GIFs; a format they must produce with your tooling is a second gate, not a convenience.
3. **You own the parser bug forever.** Magic numbers, offsets, truncation, path traversal, size bombs — every hand-rolled container is where those live.

What to do instead, in the order that pays:

| Option | Verdict |
|---|---|
| Folder `dances/<name>/` + `frame_NNN.png` + `pack.json` + `audio.ogg` | **Do this.** Zero new format, already tolerated by `countFrames`, ships a name you can zip. |
| Zip of that folder, imported by **Rescan** | Do this second. `java.util.zip`, opens in Finder/Explorer, same contents as the folder. If you want branding, rename the *suggested* extension to `.dancepack` and still sniff `PK\x03\x04`. |
| Drop a raw `.gif` into `dances/` and let the mod convert it | **Best user experience** and no format at all — see Task 7, with the runtime-decode caveat. |
| `.mcgif` / `.specialgif` | Rejected. |

## Review Focus

The spec implies these inputs and no task's happy path exercises them. Each line gets pinned in the task that owns the code:

1. **A pack added while a screen is open** — **Rescan** must open the layout editor over the config screen and return to the config screen on **Done**, not close the game menu or open it twice.
2. **A pack that is already placed** — startup must not nag: launching the game ten times with the same packs opens the editor zero times.
3. **`pack.json` that is missing, empty, or full of nonsense** (`frameDurationMs: 0`, `"music": "../../secret"`, 10 MB of JSON) — the pack must still play with global defaults, and no path outside the pack folder may be read.
4. **A bundle whose audio is unsupported** (`.opus`, `.m4a`) or whose file was deleted — the dance still plays, silently, or with one clear log line; never a crash and never a silent `null` in the selector.
5. **Two packs whose names collide after sanitizing** (`Abu` and `abu`, or `my dance` and `my_dance`) — one must not render the other's frames; `DanceTextures.safeName` (`DanceTextures.java:112`) lowercases while `DanceRegistry` keys on the exact folder name (`:50`).

---

## Plan A — auto-open the Position editor for new packs

### Task 1: Make the scan report additions

**Files:**
- Modify: `src/main/java/com/sebas/specialistdance/dance/DanceRegistry.java` (`scan()` at `:34`, `userDances` at `:27`)
- Modify: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java` (`:86-87` construction and startup scan, `tick()` at `:256`)

**Interfaces:**
- Produces: `List<String> DanceRegistry.scan()` — the same scan, now returning the pack names present before this call, in folder-name order. `SpecialistDanceConfigScreen` (`:222`) and `SpecialistDanceClient` (`:87`) are its only callers.
- Produces: `Set<String> DanceRegistry.knownNames()` — current pack names, for the log line.
- Produces: `void SpecialistDanceClient.queueLayoutFor(List<String> added)` and a `Deque<String> pendingLayout` field; on the first client tick where `client.screen == null && client.level != null && client.player != null`, the head is consumed: `config.activeDance = name`, then `client.setScreen(new DanceLayoutScreen(config, danceRegistry, danceTextures, null))`. One pack per tick-loop, so closing the editor shows the next.
- Consumes: nothing from Task 2.

- [ ] **Step 1: Reproduce the absence.** With the client closed, add a pack folder by copying an existing one: `cp -r run/config/specialist-dance/dances/Zelda run/config/specialist-dance/dances/Copytest`. Launch, press **K**, click **Rescan**, and record that `Copytest` appears in the **Dance pack** selector and nothing else happens. This is the baseline the task changes.
- [ ] **Step 2: Return the additions from `scan()`.** Snapshot `userDances.keySet()` before `clear()`, rebuild as today, and return the names in the new set that were not in the snapshot. Keep the invalid-pack reason logs untouched.
- [ ] **Step 3: Log the additions.** In `scan()`, after the loop, when the returned list is not empty: `log("found new dance packs: " + added)`. With `debug: true` this is the line that proves a scan was add-aware.
- [ ] **Step 4: Queue at startup.** `SpecialistDanceClient` keeps the startup `scan()` result and calls `queueLayoutFor(...)`. Do not open a screen from `onInitializeClient` — there is no client screen stack yet, and the game is not loaded.
- [ ] **Step 5: Consume in `tick()`.** Add the flush to the existing `END_CLIENT_TICK` handler, after the level-change cleanup so a world switch does not open an editor into a unloaded level. Note `tick()` currently `return`s early on a level change (`:262-270`); the flush must sit after that branch.
- [ ] **Step 6: Build.** `./gradlew clean build` — expect `BUILD SUCCESSFUL` and a non-cached task list.
- [ ] **Step 7: Verify both paths, record the log lines.**
  - Cold path: quit, `rm -rf run/config/specialist-dance/dances/Copytest`, launch a world with **no** new pack → the editor must **not** open (Review Focus 2).
  - Warm path: from inside a world, add `Copytest` again, **K** → **Rescan** → the editor opens on `Copytest` over the config screen and **Done** returns to the config screen (Review Focus 1).
  - Startup path: quit, add `Copytest2` the same way, launch → the editor opens once the world is in, `trigger behavior=... activeDance=Copytest2` is not expected yet, but `found new dance packs` is.

### Task 2: Do not nag about a pack already shown

Depends on Task 1. Decision needed first — see the open questions: session-only, or persisted.

**Files:**
- Modify: `src/main/java/com/sebas/specialistdance/SpecialistDanceConfig.java` (new field near `activeDance`, `sanitize()`, `save()`/`load()`)
- Modify: `src/main/java/com/sebas/specialistdance/dance/DanceRegistry.java`
- Modify: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java`

**Interfaces:**
- Produces: `List<String> SpecialistDanceConfig.seenDances = new ArrayList<>()` — pack names the layout editor has already been opened for. A 1.4.0 config file has no such key, and Gson leaves the default empty list.
- Produces: `boolean DanceRegistry.needsLayout(String name)` — in `userDances`, not in `config.seenDances`.
- Produces: `void SpecialistDanceClient.markLayoutSeen(String name)` — appends to `config.seenDances` and calls `config.save()`.

- [ ] **Step 1: State the rule you are testing.** A pack shows the editor exactly once per install, no matter how many launches or **Rescan** clicks it survives. Write that sentence in the commit message body later.
- [ ] **Step 2: Add the field** with the default so old files keep working, and keep `sanitize()` from dropping it.
- [ ] **Step 3: Filter the queue** — `queueLayoutFor` pushes only names where `danceRegistry.needsLayout(name)`.
- [ ] **Step 4: Mark it seen** when the editor opens (not when it closes), so a crash between the two does not re-ask forever.
- [ ] **Step 5: Verify the two halves.** Relaunch with `Copytest` already shown → no editor. Delete `run/config/specialist-dance.json`, relaunch with the same packs → the editor opens once, because the seen list is gone with the config. Record which of the two happened.
- [ ] **Step 6: Pin Review Focus 5.** Put folders `Abu` and `abu` in the dances dir, run **Rescan**, and record what the selector and the editor show. Expected: the two names are distinct in `userDances` but map to one texture namespace path, so one of them renders the other's frames. If confirmed, file it in `ROADMAP.md` Open debt rather than fixing it here.

### Task 3: Make the editor useful for a pack it was not expecting

**Files:**
- Modify: `src/main/java/com/sebas/specialistdance/config/menu/DanceLayoutScreen.java` (`:175` frame choice, `:50-56` **Fit**, `:160-165` the two info lines)

**Interfaces:**
- Consumes: `config.activeDance` (Task 1 sets it before opening).
- Produces: unchanged constructor `DanceLayoutScreen(SpecialistDanceConfig, DanceRegistry, DanceTextures, Screen parent)`.

- [ ] **Step 1: Show something other than frame 0 for a pack whose frame 0 is empty.** Many GIFs open on a nearly blank frame. Draw frame `dance.frameCount() / 2` instead of 0, and say so in the info line. Keep the `null` guard.
- [ ] **Step 2: Make Fit fit.** **Fit** hardcodes 40/40 percent (`:51-52`). Compute the percentages that fill 80% of the smaller screen axis for the current frame's aspect ratio, so a 220x182 pack and a 498x351 pack both land reasonable without wheel-scrolling 20 steps.
- [ ] **Step 3: Verify by measurement, not by eye alone.** With the new pack active, record the `Stage N% x N% (W x H px)` line for `abu` (220x182) and the bundled `specialist` (498x351) after **Fit**, and confirm neither exceeds the window.

### Task 4: Docs and close-out for Plan A

- [ ] **Step 1:** README **Custom dances**: add the drop-a-GIF or drop-a-folder flow, the **Rescan** → editor behaviour, and the fact that speed is global (`frameDurationMs`, JSON only) until Task 6.
- [ ] **Step 2:** `ROADMAP.md`: Phase 7 items, the new debt boxes this exposes (per-pack layout does not exist; a new pack cannot be placed per-pack), and one Open debt box ticked.
- [ ] **Step 3:** `CHANGELOG.md` entry under the version this ships in.

---

## Plan B — the package: frames + music + speed in one folder

### Task 5: `pack.json`, read-only

**Files:**
- Create: `src/main/java/com/sebas/specialistdance/dance/DancePackMeta.java`
- Modify: `DanceRegistry.java` (`countFrames` call site `:51`, the `Dance` record `:19`)
- Modify: `SpecialistDanceClient.java` (`:169` frame timing, `triggerDance()` music start `:426`)

**Interfaces:**
- Produces: `record DancePackMeta(int frameDurationMs, String track)` — `frameDurationMs` `-1` = not set, `track` `null` = not set.
- Produces: `static Optional<DancePackMeta> DancePackMeta.read(Path packDir)` — reads `packDir/resolve("pack.json")` with Gson; on any `IOException`, malformed JSON, or out-of-range value it logs the reason and returns empty. Never throws.
- Consumes: `DanceRegistry.Dance.folder()`; a bundled dance (`folder == null`) has no metadata.
- Produces: `int effectiveFrameDurationMs()` on the client — `meta.frameDurationMs() > 0 ? meta : config.frameDurationMs`, used in the HUD frame index (`:169`) instead of the raw config value.

- [ ] **Step 1: Pin Review Focus 3 before writing the reader.** Hand-create `run/config/specialist-dance/dances/Copytest/pack.json` in three states and record the outcome of each launch: `{}`; `{"frameDurationMs": 0, "track": "../../../other"}`; a valid `{"frameDurationMs": 100, "track": "default"}`. Expected: all three play; the third is visibly 2× slower than the default 50; no path outside the pack folder is touched; the log names which state happened.
- [ ] **Step 2: Write the reader** with the same clamp style as `SpecialistDanceConfig.sanitize()` (floor 10 ms, ceiling a stated number), and a track name restricted to `[A-Za-z0-9._-]` with no `..`, resolved only inside the pack folder.
- [ ] **Step 3: Feed the HUD** so the GIF's real speed wins over the global value; log the chosen source once per dance start, not per frame.
- [ ] **Step 4: Verify the speed claim in the log**, at 50 ms global and 100 ms in `pack.json`, for a pack of 21 frames: one pass is 1050 ms versus 2100 ms.

### Task 6: Per-pack music, and a track selector that can see it

**Files:**
- Modify: `audio/MusicManager.java` (`scanUserTracks()` `:170`, `getAvailableTracks()` `:166`, `play()` resolution `:326`)
- Modify: `config/menu/SpecialistDanceConfigScreen.java` (track `CycleButton` `:92`)

**Interfaces:**
- Produces: `void MusicManager.registerPackTrack(String danceName, Path audioFile)` — adds `<danceName>/<file-stem>` to the track map so a bundle's audio is selectable and playable without copying it into the global music folder.
- Produces: `String MusicManager.defaultTrackFor(String danceName)` — the pack's own audio if present, else the global `musicTrack`.
- Consumes: `DancePackMeta.track()`.

- [ ] **Step 1: Pin Review Focus 4.** Put an unsupported `song.opus` and a `.mp3` in the pack folder, and a `pack.json` pointing at a name with no file. Record: the dance still plays, the log says which audio was refused, and the selector never lists a track that cannot load.
- [ ] **Step 2:** Implement the two methods on the existing `userTracks` map — do not add a second lookup path in `play()`.
- [ ] **Step 3:** `triggerDance()` uses `defaultTrackFor(config.activeDance)` when the pack declares audio and the user has not overridden it; the config screen shows which track the pack would use.
- [ ] **Step 4:** Verify with `abu`: dance plays, audio is the bundle's own, `musicEnabled: false` still yields one finite pass (Phase 6 behavior must not regress — check the `drivesEnd=false` end line).

### Task 7: Drop a raw GIF, and the mod converts it

**Files:**
- Create: `src/main/java/com/sebas/specialistdance/dance/GifImporter.java` (move the compositing core out of `tools/GifToFrames.java`)
- Modify: `tools/GifToFrames.java` (becomes a thin `main` over `GifImporter`)
- Modify: `DanceRegistry.java` (`scan()`)

**Interfaces:**
- Produces: `int GifImporter.convert(Path gif, Path packDir, int maxSize)` — same behaviour as the tool: logical-screen canvas, disposal methods honoured, `frame_%03d.png` names, returns the frame count or `-1` with a reason in the log.
- Produces: `scan()` also lists `*.gif` in the dances root and converts each into `dances/<stem>/`, then treats the result as a new pack (Task 1 picks it up, so the editor opens).

- [ ] **Step 1: Prove the runtime can decode GIF at all.** Before writing the importer, log the result of `ImageIO.getImageReadersByFormatName("gif").hasNext()` from the dev client, not from `java` on the JDK. The client runs on a launcher JRE whose module set is not yours to assume; `java.desktop` + the GIF plugin is the whole premise of this task.
- [ ] **Step 2: If Step 1 says false,** stop the task and record it as a debt box: conversion stays offline, and the drop-in path becomes "the log tells you to run `tools/GifToFrames`".
- [ ] **Step 3:** Implement with a frame-count ceiling and a byte ceiling read from the GIF header before decoding, so a 4000-frame wallpaper GIF cannot freeze the client. Refuse to write outside the dances root.
- [ ] **Step 4: Verify with `~/Downloads/abu-twinkle-eyes.gif`** dropped into `run/config/specialist-dance/dances/`: expect `frames: 21`, 21 PNGs, the new-pack log, the editor opening, and the same 21-frame pass as the offline converter. Diff a frame from each path; they should be byte-identical or explained.

### Task 8: A bundle is a zip of that folder (optional, last)

**Files:**
- Modify: `DanceRegistry.java` (`scan()`), plus a `PathSniffer` if it earns its keep

- [ ] **Step 1: Sniff, do not trust.** A dances-root entry starting `PK\x03\x04` is a bundle, whatever its extension — `.zip` or a branded `.dancepack`. Anything else stays as today.
- [ ] **Step 2: Reject before extracting.** No entry whose normalized path escapes the target pack dir, no entry count above a stated cap, no uncompressed total above a stated cap. Log the refusal, skip the file.
- [ ] **Step 3: Verify** with a bundle zipped from `dances/Copytest`, and with a zip that contains `../evil.txt`. Expected: the first becomes a new pack and opens the editor; the second is refused with a line you can read.

---

## Self-review notes

- Coverage: the two asks map to Task 1-4 (auto-layout) and Task 5-8 (package). The speed bug measured today is fixed only by Task 5; A alone does not fix it.
- Open questions with no default picked are listed in the chat reply, not resolved silently here.
- Task 7 Step 1 gates Task 7; a plan that assumed the runtime GIF decoder exists would be a transcript of a hope.
