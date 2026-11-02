# Specialist Dance — Development Roadmap

> Living document. Update it whenever a phase changes state.
> If this file and the code disagree, the code wins and this file is wrong.

**Latest release:** `v1.5.0` (2026-11-01, tagged `79c3907`) · **In development:** `v1.6.0` Phase 7, uncommitted on `main`
**Branch:** `main` · **History:** 23 commits, 2026-10-10 → 2026-11-01 (22 days)

## What this project is

A client-side Fabric mod (Minecraft 1.21.11, Java 21) that plays an on-screen dance
when you mine a valuable ore and pick up its drop, with optional music, an in-game
config menu and user-supplied dance packs and tracks.

## Status legend

| Mark | Meaning |
|---|---|
| ⬜ | planned — not started |
| 🛠 | built — compiles or ships, but nobody has seen it work |
| 🧪 | verified — evidence recorded (log line, screenshot, written repro) |
| ✅ | released — version bumped, CHANGELOG entry, tagged in git |

`[x]` says the work exists; the mark says whether it has been proven. A release can be
✅ while items inside it stay 🛠 — those are **open debt** and belong in that list.

## Timeline

| # | Phase | Version | Worked on | Span | Commits | State | Progress |
|---|---|---|---|---|---|---|---|
| 1 | First Dance | 1.0.0 | 10-10 → 10-11 | 2 d | 4 | ✅ | 100% |
| 2 | The Dance Awakens | 1.1.0 | 10-12 | 1 d | 4 | ✅ | 100% |
| 3 | Better Detection | 1.2.0 | 10-15 → 10-18 | 4 d | 3 | ✅ | shipped, QA 1/7 🧪 |
| 4 | Sound On | 1.3.0 | 10-19 → 10-23 | 5 d | 5 | ✅ | 100% |
| 4b | More Music Formats | 1.3.1 | 10-24 | 1 d | 1 | ✅ | 100% |
| 5 | Dance Packs + Layout | 1.4.0 | 10-25 → 10-28 | 3 d | 9 | ✅ | 10/10 |
| 6 | Animation Control | 1.5.0 | 10-30 → 11-01 | 2 d | 4 | ✅ | released |
| 6b | Dance Packages | 1.5.0 | 10-30 → 11-01 | 2 d | 1 | ✅ | shipped inside v1.5.0 |
| 7 | Solid Ground + Speed | 1.6.0 | 11-01 | 1 d | 0 | 🛠 | 4/4 items built, 0 observed |
| 8 | Your Rules | 1.7.0 | — | — | — | ⬜ | 0% |
| 9 | Provable | 1.8.0 | — | — | — | ⬜ | 0% |
| 10 | Stability | 1.9.0 | — | — | — | ⬜ | 0% |
| 11 | Final Mix | 2.0.0 | — | — | — | ⬜ | 0% |
| 12 | The Control Room | 2.1.0 | — | — | — | ⬜ | 0% |

**Overall: 14 phases · 8 released across 7 tags · 1 built · 5 not started.**

## Current reality (2026-11-01)

| Claim | State |
|---|---|
| Ore → drop → dance trigger path | 🧪 diamond / deepslate diamond only (`run/logs/latest.log` 20:55:32) |
| Bundled `specialist` dance renders | ✅ shipped since v1.0.0 |
| Music plays with the dance, all formats | ✅ released v1.3.0 / v1.3.1 |
| Config screen edits and persists | 🧪 `musicTrack`, volume and `activeDance` all observed reaching disk (`run/config/specialist-dance.json`, 2026-10-30 18:17) |
| A **user** dance pack renders in-game | 🧪 the 29-frame `Zelda` pack drew a full pass, `hold` and `loop` × music off (`run/logs/latest.log` 18:08) |
| Position/zoom editor | 🛠 shipped in `v1.4.0`; `place()` is proven by the frame logs, but no observation records dragging or zooming in the **Position dance** screen |
| Drop a raw GIF / new pack and open the editor | 🧪 2026-10-30 19:18: `quicktest.gif` → 21 frames at 100 ms, only the new pack queued, `opening layout editor for new dance pack quicktest` after quick-play (`run/logs/specialist-dance-quickplay-2026-10-30.log`) |
| Existing packs do not nag on upgrade | 🧪 a config without `seenDances` seeded the two valid existing packs; only `quicktest` was queued (`run/logs/specialist-dance-upgrade-2026-10-30.log`) |
| Per-pack `pack.json` timing / audio | 🛠 built; no client log yet shows a pack playing at its own delay or with its own track |
| `animationEndBehavior` | 🧪 `stop` / `hold` / `loop` × music on and off all logged on 2026-10-30 (17:47, 18:08, 18:17) |
| `cooldownMs` | 🛠 consumed in `triggerDance()`, never tested — only `cooldownMs: 0` has an observation |
| `animationSpeed` slider changes playback rate | 🛠 built; the formula was checked offline in a throwaway runner, but no client log yet shows a dance running at a non-`1.0` speed |
| `pack.json` `speedMultiplier` overrides the slider | 🛠 built; never observed |
| Config clamps an out-of-range value on load | 🛠 built; no client log yet shows a clamped field or the `configVersion` upgrade |
| Mod Menu opens the config screen | 🛠 built; `modRuntimeOnly` puts ModMenu on the dev classpath but no dev-client session has clicked the button |
| Mod Menu is safe when `"enabled": false` | 🛠 the null guard is in `ModMenuIntegration`; the NPE it prevents has not been reproduced either way |
| Automated tests | ⬜ none; `./gradlew test` is `NO-SOURCE` |
| ModMenu entrypoint | ⬜ none |

---

## Phase 1 — v1.0.0: First Dance ✅

**Time:** 2026-10-10 → 2026-10-11 (2 days) · **Commits:** `a39c544` `26f7ac1` `f6068f3` `8fe1c6f` · **Tag:** `v1.0.0` 2026-10-11
**Progress:** 4/4 done.

- Fabric mod scaffold: Gradle + wrapper, `fabric.mod.json`, `SpecialistDanceClient` entrypoint.
- Diamond-in-inventory detection and the 147-frame HUD overlay (`HudElementRegistry.addLast`).
- Animation frames packaged under `assets/specialist-dance/textures/gui/frames/`.
- Icon and mod metadata for the release.

## Phase 2 — v1.1.0: The Dance Awakens ✅

**Time:** 2026-10-12 (1 day) · **Commits:** `f2c1de4` `c0c1eeb` `3ead64c` `f38b839` · **Tag:** `v1.1.0` 2026-10-12
**Progress:** 3/3 done.

- Diamond-only check refactored into multi-item tracking: diamond, emerald, raw gold,
  raw iron, gold nugget, ancient debris.
- First pickup hook: `ClientPacketListenerMixin` + `specialist-dance.client.mixins.json`.
- Version bump and README rewritten for multi-item behavior.

## Phase 3 — v1.2.0: Better Detection ✅

**Time:** 2026-10-15 → 2026-10-18 (4 days) · **Commits:** `2c251f9` `bda6080` `2bc9bb2` · **Tag:** `v1.2.0` 2026-10-18
**Progress:** 9/9 built · QA 1 of 7 scenarios verified → open debt.

- Origin-aware trigger: `ClientPlayerBlockBreakEvents.AFTER` records the broken ore,
  `expectedDrop()` maps ore → drop (deepslate + nether variants), proximity + expected
  item links the drop, mixin pickup fires the dance.
- Drop resolution deferred to a later client tick (spawn packet precedes item data).
- Dedup per ore position (`triggeredOres`), timeouts, full clear on world/dimension change.
- `config/specialist-dance.json` with `enabled`/`debug`, timeouts, radius, animation
  duration/size/position, cooldown; diagnostics gated behind `"debug": true`.
- Docs: README rewritten, CHANGELOG and CONTRIBUTING added.

## Phase 4 — v1.3.0: Sound On ✅

**Time:** 2026-10-19 → 2026-10-23 (5 days) · **Commits:** `8da1d3e` `26ad4fb` `bd9678a` `d9f34ea` `bb05212` · **Tag:** `v1.3.0` 2026-10-23
**Progress:** 7/7 done.

- `audio/MusicManager` — OpenAL + STB Vorbis playback started from `triggerDance()`.
- Bundled `assets/specialist-dance/sounds/default.ogg`; user tracks scanned from
  `config/specialist-dance/music/`, `default` reserved.
- Config screen on **K**: enable, volume, track selector, preview, stop; saves on close.
- Config fields `musicEnabled`, `musicVolume`, `musicTrack`; fallback to `default` when a
  track is missing.
- Stop/cleanup on animation end, death, world change, disconnect; no overlapping playback.
- OpenAL context fixes: never clear Minecraft's context, guard context and object IDs,
  downmix >2 channels to stereo (`bd9678a`).
- Also landed here: `AGENTS.md`, `ROADMAP.md`, the local Loom metadata mirror workaround.

## Phase 4b — v1.3.1: More Music Formats ✅

**Time:** 2026-10-24 (1 day) · **Commit:** `4de2f09` · **Tag:** `v1.3.1` 2026-10-24
**Progress:** 5/5 done.

- `javasound-mp3`, `javasound-flac`, `tritonus-share` bundled jar-in-jar.
- Scan `.ogg` `.wav` `.aiff` `.aif` `.au` `.mp3` `.flac`; non-OGG decoded through
  `javax.sound.sampled.AudioSystem` and uploaded to OpenAL; OGG path unchanged.

---

## Phase 5 — v1.4.0: Dance Packs + Layout ✅

**Time:** 2026-10-25 → 2026-10-28 (3 days) · **Commits:** 9 · **Tag:** `v1.4.0` 2026-10-28
**Progress:** 10/10 done.

**What shipped:**
- `frameDurationMs` stayed global in 1.4.0; `pack.json` metadata was deliberately deferred.
  Phase 6b reintroduced it because a dropped GIF needs its own delay to play at the right
  speed, so per-pack timing now has a source that is not the global config.
- Hardcoded `FRAME_COUNT = 147` removed; frame count derived per pack by folder scan.
- `dance/DanceRegistry` — bundled `specialist` plus user packs from
  `config/specialist-dance/dances/<pack>/`.
- `dance/DanceTextures` — pack frames as `DynamicTexture`s, cached, released on pack
  change and shutdown.
- `activeDance` config field + a **Dance pack** selector in the K menu; one shared
  playback/cleanup path for bundled and user dances; "Preview dance on close".
- `tools/GifToFrames` — exports a GIF to a `frame_%03d.png` pack. 🧪 29/29 frames uniform
  256×210 from `zelda.gif`. Fixed the same day it shipped: GIF frames are deltas, so the
  first export produced half-empty PNGs; it now composites onto the logical screen canvas
  and honours disposal methods.
- Frames draw at their true aspect ratio (the bundled 498×351 frames had been stretched
  into a square box since v1.0.0) and the screen lays rows out from a computed cursor.
- `config/menu/DanceLayoutScreen` — **Position dance** screen: drag to move, scroll to
  zoom, Center / Fit / reset / Done, saves on close.
- `DanceTextures.place()` + `Placement.contains()` — shared aspect-fit placement math.
- K menu: **Open music folder**, **Open dances folder**, **Rescan** (rows 6 → 9);
  `MusicManager.scanUserTracks()` and `SpecialistDanceClient.getDanceTextures()` opened up.

## Phase 6 — v1.5.0: Animation Control ✅

**Time:** 2026-10-30 → 2026-11-01 (2 days) · **Commits:** `df4ad33..79c3907` · **Tag:** `v1.5.0` 2026-11-01
**Progress:** released. `cooldownMs` shipped still unobserved — it stays in Open debt.

- 🧪 `animationEndBehavior` consumers in the HUD: `stop` plays one pass then clears the
  overlay, `hold` keeps the last frame, `loop` wraps. The **K** menu selector offers these
  three and an unknown value falls back to `stop`. `loop-until-music-ends` stays a
  load-time migration alias rather than a fourth value — `loop` now means exactly that.
- 🧪 Prerequisite met: `MusicManager.isPlaying()` is the end signal for `hold` and `loop`.
- 🧪 No-music fallback: `triggerDance()` reads `isPlaying()` once, after `play()`, into
  `musicDrivesAnimationEnd`, and the HUD uses `behavior = drivesEnd ? config : "stop"`.
  With music off there is no end signal, so `hold` and `loop` run one finite pass instead
  of cancelling while rendering frame 0. All 6 cells (stop/hold/loop × music on/off)
  logged on 2026-10-30; `music ended while holding` is observed, `music ended while
  looping` is not.
- 🛠 Overlapping restart: `triggerDance()` overwrites `animationStartMillis`, so a new ore
  restarts a running `loop` instead of being blocked by it. A restart logs no end line —
  a restart is not an end. Deliberately left without a separate guard.
- ⬜ `cooldownMs` — consumed in `triggerDance()`, only ever observed at `0`. It is also
  never reset on world change, and the no-audio-device path (`isPlaying()` false because
  OpenAL is unavailable) has no observation.

## Phase 6b — Dance Packages ✅

**Time:** 2026-10-30 → 2026-11-01 (2 days) · **Commit:** `f735bf5` · **Tag:** shipped inside `v1.5.0` 2026-11-01
**Progress:** released with Phase 6 as v1.5.0 rather than as its own version, so the
`v1.6.0` number stayed with Phase 7. Items still 🛠 below are Open debt.

- 🧪 `DanceRegistry.scan()` is add-aware and returns new pack names; startup and
  **Rescan** queue them; the **Position dance** editor opens once per pack and returns to
  its parent screen. Observed 2026-10-30 19:18: `quicktest.gif` imported as 21 frames at
  100 ms, only `quicktest` was queued, and `opening layout editor for new dance pack
  quicktest` appeared after quick-play
  (`run/logs/specialist-dance-quickplay-2026-10-30.log`).
- 🧪 `seenDances` persists packs the editor has shown. An old config without the key seeds
  the folders already present, so upgrading does not nag for every existing pack; a newly
  dropped GIF is still queued. Observed: `found new dance packs: [Zelda, abu, quicktest]`
  but `queued layout editor for [quicktest]`.
- 🧪 Raw `.gif` in the dances root is composited to `frame_NNN.png` by `GifImporter`
  (logical-screen canvas, disposal methods honoured); caps are 2000 frames, 50 MB, 16M
  pixels and 512 px per side; the GIF's average delay is written to `pack.json`.
- 🧪 Sidecars `<stem>.<audio-ext>` and `<stem>.json` next to a dropped GIF are copied into
  the new pack. Observed 2026-10-30 19:21: `sidecartest.gif` + `.mp3` + `.json` produced
  21 PNGs, the MP3, and `pack.json` `{"frameDurationMs":250,"track":"test-1"}`
  (`run/logs/specialist-dance-sidecar-2026-10-30.log`).
- 🛠 `pack.json` `frameDurationMs` overrides the global timing; `track` names the pack's
  own audio. Pack tracks appear in the selector as `<pack>/<stem>` and play when
  `musicTrack` is `default`.
- 🧪 A `pack.json` over 1 MB is refused and the pack still loads with global timing.
  Observed 2026-10-30 19:26: `pack.json for bigjson is too large`
  (`run/logs/specialist-dance-bigjson-2026-10-30.log`).
- 🛠 **Rescan** releases cached pack textures so a replaced pack reloads.
- 🛠 **Fit** uses the current frame's aspect ratio; the editor previews the middle frame.
- ⬜ Zip/`.dancepack` import (plan Task 8) is not implemented.
- ⬜ Per-pack layout still does not exist; the stage remains global.

## Phase 7 — v1.6.0: Solid Ground + Speed 🛠

**Time:** 2026-11-01 · **Commits:** 0 (working tree on `main`) · **Tag:** none yet.
**Progress:** 4/4 planned items built + one unscoped addition · 0 observed. ModMenu
placeholder screen dropped from the plan — a real editing screen already ships on **K**;
ModMenu just opens it.

- 🛠 `sanitize()` now bounds every numeric field on both sides, not just the minimum:
  `frameDurationMs` 10–10000, timeouts 100–60000, `cooldownMs` 0–60000,
  `searchRadiusSquared` 0–100, `animationSpeed` 0.1–10.0. An `animationEndBehavior`
  outside `stop`/`hold`/`loop` falls back to `stop`; the whitelist is one
  `ANIMATION_END_BEHAVIORS` constant shared with the config screen instead of two copies.
- 🛠 `configVersion` + `migrate()`. Gson runs field initialisers before binding, so a
  pre-versioning file would read back as current; `load()` parses the raw `JsonObject`
  first and treats a missing key as version `0`. `migrate()` runs after `sanitize()` so
  the stamp that reaches `save()` holds clamped values.
- 🛠 `SpecialistDanceConfig` moved to `com.sebas.specialistdance.config`; five import
  sites updated, config file path unchanged.
- 🛠 `integration/ModMenuIntegration` implements `ModMenuApi` and opens the existing
  screen; ModMenu is `modCompileOnly` + `modRuntimeOnly` and only **recommends** in
  `fabric.mod.json`, so it is never a hard dependency. The factory null-guards
  `getMusicManager()` because `onInitializeClient()` returns early when
  `"enabled": false`, before the manager exists — without the guard Mod Menu's Config
  button throws an NPE in `init()`.
- 🛠 **Animation speed** (not in the original Phase 7 scope, added on request): a
  dropped GIF plays at its own delay and there was no way to change it, so
  `animationSpeed` (global, on a **K** menu slider) and `speedMultiplier` (per pack) both
  divide the frame duration. Timing uses `Math.round` and floors at 1 ms so a large
  multiplier cannot divide a duration down to `0`. A pack's `speedMultiplier` overrides
  the slider, matching the existing `frameDurationMs` precedent. The formula was checked
  against 13 cases in a throwaway runner (1.0x/2.0x/10.0x, clamping a hand-authored
  `1000`, the 1 ms floor, the `-1` no-override sentinel, slider round-trip and the
  `0.1`/`10.0` endpoints); the runner was not committed, so this is reasoning, not
  in-game evidence.

## Phase 8 — v1.7.0: Your Rules ⬜

**Progress:** 0%
- Move the hardcoded ore → drop map from `expectedDrop()` into config, defaults = today.
- Per-ore enable/disable toggles; trigger logic stays independent of animation and audio.
- (Stretch) celebration counter per ore type.

## Phase 9 — v1.8.0: Provable ⬜

**Progress:** 0% · highest-leverage gap in the project: nothing is automated.
- JUnit for Minecraft-free classes only — no Loom test harness, no `Minecraft` mocks.
- `DanceRegistry.countFrames`: empty dir, gap, non-contiguous, junk names, valid pack.
- Placement math: aspect fit, oversized `drawSize`, `-1` auto-centre, editor positions.
- GIF compositing: a `doNotDispose` delta must fill the canvas; both restore disposals
  must behave. That regression shipped in `tools/GifToFrames` until 2026-10-25.

## Phase 10 — v1.9.0: Stability ⬜

**Progress:** 0%
- Fix detection/playback edge cases found in the field; audit entity-tracking maps for
  leaks on removal and world change; check event double-registration.
- Retire diagnostic logs whose systems passed QA; extended-session check (memory, tick
  cost) including user-pack texture caching.

## Phase 11 — v2.0.0: Final Mix ⬜

**Progress:** 0%
- Detection + animation + music + config verified together under every config combination.
- Re-check audio/animation sync after the end-behavior work; run the full QA matrix.

## Phase 12 — v2.1.0: The Control Room ⬜

**Progress:** 0% — one menu for everything. Music, size/position and pack selection land
before this; what remains is the glue.
- Music playback behavior on dance start/end; duration and end-of-animation behavior.
- "Open folder" + in-screen preview (folder buttons are already in the tree for 1.4.0).
- Live-synced ore toggles; save/restore/reset; mod version info; keybind remapping for K.

---

## Open debt

Anything 🛠 at release time sits here until it is 🧪.

- [ ] **Phase 3 QA (v1.2.0), 6 of 7 never run** — mine each tracked ore type (only
      diamond/deepslate diamond proven); Fortune multi-drop → one dance; two ores mined
      side by side → no mis-association; chest/trade/mob/old ground drop → no trigger;
      drop > 4 blocks → no trigger; re-mine the same spot → triggers again;
      dimension change/disconnect → no stale trigger.
- [x] 🧪 `animationEndBehavior` is dead config in a released version — consumed by the HUD
      in `v1.5.0`; `stop` / `hold` / `loop` × music on and off logged 2026-10-30.
- [x] 🧪 `DanceRegistry.countFrames` returns `-1` for empty, gapped and unreadable packs
      alike — each case now logs its own reason. Observed 2026-10-30 19:11:
      `empty-dance has no frame_NNN.png files` and `gapped-dance has 2 numbered frames
      from frame_000 to frame_002`
      (`run/logs/specialist-dance-import-2026-10-30.log`).
- [x] 🛠 The `putIfAbsent` duplicate-name branch in `DanceRegistry.scan` is unreachable —
      deleted (`Files.list` cannot yield one name twice); nothing to observe.
- [x] 🛠 The layout screen and folder/Rescan buttons exist only in the working tree —
      committed and tagged in `v1.4.0`.
- [ ] `music ended while looping` has never been logged: `loop` × music on is proven to
      wrap, but its end path has no observation.
- [ ] The **Position dance** screen has never been observed dragging or zooming a dance;
      only `DanceTextures.place()` output is in the logs.
- [ ] `cooldownMs` has no observation above `0`, and `lastTriggerMillis` is never cleared
      on world change.
- [ ] `drawSize`, `stageWidth` and `stageHeight` are written to every config file and read
      by nothing since `v1.4.0` — rule 3 wants a field to ship with the code that reads it
      and the control that edits it.
- [ ] `pack.json` `frameDurationMs` and per-pack audio are built but not observed: no
      client log yet shows a pack playing at its own delay or with its own track.
- [ ] Per-pack layout does not exist; moving a new pack still moves every dance.
- [ ] GIF conversion runs on the render thread during **Rescan**. Caps bound the work, but
      a large GIF can still hitch the client.
- [ ] `DanceTextures.safeName` lowercases pack names, so `Abu` and `abu` still collide in
      the texture namespace even though `DanceRegistry` keeps them distinct.
- [ ] Zip/`.dancepack` bundle import is unbuilt; a raw GIF or a hand-made folder is the
      only drop-in path.
- [ ] The **Rescan** path (editor over the config screen, then **Done** returns to it) is
      not observed; only the startup + quick-play path is proven.
- [ ] `tools/GifToFrames.java` still carries its own compositing copy. The runtime
      `GifImporter` duplicates it because the documented single-file launch on Java 21
      cannot import the mod class.
- [ ] Phase 7 is entirely 🛠: no client log has shown a dance running at a non-`1.0`
      `animationSpeed`, a pack's `speedMultiplier` taking effect, a config value being
      clamped on load, or the `configVersion` upgrade of a pre-versioning file.
- [ ] Mod Menu has never been clicked. `modRuntimeOnly` puts it on the dev classpath, so
      the entrypoint and the `"enabled": false` null guard are both unexercised — the NPE
      the guard prevents has never been reproduced either.
- [ ] A pack's `speedMultiplier` overrides the **Animation speed** slider rather than
      multiplying with it, so the slider silently does nothing for such a pack. The
      semantics match `frameDurationMs` and are documented, but no user-facing control
      shows which is in force.
- [ ] `MIN/MAX_SPEED_MULTIPLIER` in `DancePackMeta` duplicate
      `MIN/MAX_ANIMATION_SPEED` in `SpecialistDanceConfig` as literals rather than one
      shared constant, to keep the `dance` package from depending on `config`. Changing
      one without the other lets a pack speed escape the range the slider can reach.

## Out of scope / future ideas

- Server-side sync (multiple players dancing together).
- Localization (lang files) beyond the current keybind entries.
- Loading JPEG/WebP frames directly — `NativeImage.read` calls `PngInfo.validateHeader`,
  which demands the PNG magic before STB sees the bytes, so PNG is the only decodable
  frame format. Raw GIF is converted to PNG at import; other formats stay offline.
- Proprietary `.specialgif` / `.mcgif` containers — rejected. A GIF or a folder covers the
  use case, and a custom container would still need the same PNG conversion.
- Cloud/shared dance pack browser.

## Release rules

1. One phase = one release = one tag. A phase is ✅ only after the tag exists.
2. Every release: bump `mod_version` in `gradle.properties`, add a CHANGELOG entry,
   update README if behavior changed, prove `./gradlew build` with a **fresh** run
   (a cached `UP-TO-DATE` is not evidence), then tag. Nothing ships uncommitted.
3. A config field lands in the same release as the code that reads it *and* the menu
   control that edits it.
4. Every release closes at least one Open debt box.
5. Keep the mod client-side; keep detection, animation, audio, config and UI in
   separate classes; preserve working fallbacks until their replacement is verified.
6. If reality beats this plan, fix the plan the same day.
