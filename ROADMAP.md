# Specialist Dance — Development Roadmap

> Living document. Update it whenever a phase changes state.
> If this file and the code disagree, the code wins and this file is wrong.

**Latest release:** `v1.3.1` (2026-10-24) · **Pre-release:** `v1.4.0-rc1` (2026-10-25)
**Branch:** `v1.4.0` · **History:** 18 commits, 2026-10-10 → 2026-10-26 (17 days)

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
| 6 | Animation Control | 1.5.0 | — | — | — | ⬜ | 0% |
| 7 | Solid Ground | 1.6.0 | — | — | — | ⬜ | 0% |
| 8 | Your Rules | 1.7.0 | — | — | — | ⬜ | 0% |
| 9 | Provable | 1.8.0 | — | — | — | ⬜ | 0% |
| 10 | Stability | 1.9.0 | — | — | — | ⬜ | 0% |
| 11 | Final Mix | 2.0.0 | — | — | — | ⬜ | 0% |
| 12 | The Control Room | 2.1.0 | — | — | — | ⬜ | 0% |

**Overall: 13 phases · 6 released · 7 not started.**

## Current reality (2026-10-26)

| Claim | State |
|---|---|
| Ore → drop → dance trigger path | 🧪 diamond / deepslate diamond only (`run/logs/latest.log` 20:55:32) |
| Bundled `specialist` dance renders | ✅ shipped since v1.0.0 |
| Music plays with the dance, all formats | ✅ released v1.3.0 / v1.3.1 |
| Config screen edits and persists | 🧪 `musicTrack`, volume written to disk; `activeDance` never observed reaching disk |
| A **user** dance pack renders in-game | 🛠 never seen — the release blocker for 1.4.0 |
| Position/zoom editor | 🛠 in the working tree on 2026-10-26, uncommitted, unverified, not in the rc jar |
| `animationEndBehavior` | 🛠 dead config — declared in a released version, zero consumers |
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

🛠

**Time:** 2026-10-25 → in progress · `v1.4.0-rc1` tagged 2026-10-25 (`e654167`) · **Working tree:** 2026-10-26, uncommitted
**Progress:** 7 of 10 items exist · 1 🧪 · **not ✅ until a user pack is seen rendering in Survival.**

**Advances in the rc** (`e654167`, 889 insertions across 11 files):
- `frameDurationMs` stays global; `pack.json` metadata dropped from the plan — a per-pack
  fps would be a second source of truth nobody asked for.
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

**Advances in the tree, not committed or built into a jar** (2026-10-26):
- `config/menu/DanceLayoutScreen` — **Position dance** screen: drag to move, scroll to
  zoom, Center / Fit / reset / Done, saves on close.
- `DanceTextures.place()` + `Placement.contains()` — shared aspect-fit placement math.
- K menu: **Open music folder**, **Open dances folder**, **Rescan** (rows 6 → 9);
  `MusicManager.scanUserTracks()` and `SpecialistDanceClient.getDanceTextures()` opened up.

## Phase 6 — v1.5.0: Animation Control ⬜

**Progress:** 0% · **Starts when 1.4.0 is tagged.**
- `animationEndBehavior` consumers in the HUD: `stop` (default, today's behavior),
  `hold`, `loop`, `loop-until-music-ends`; unknown value falls back to `stop`.
- Prerequisite: `MusicManager` exposes no playback status — it needs `isPlaying()` or a
  decoded duration before `loop-until-music-ends` can exist.
- Define the no-music fallback (`musicEnabled` false = no end signal).
- Overlapping-restart guard must know whether the current animation is finite, or `loop`
  blocks every future dance.
- Verify `cooldownMs` (consumed in code, never tested) incl. default `0` and world change.

## Phase 7 — v1.6.0: Solid Ground ⬜

**Progress:** 0%. ModMenu placeholder screen dropped from the plan — a real editing screen
already ships on **K**; ModMenu just opens it.
- Validate and clamp every config value on load; fall back to defaults on nonsense.
- Merge cleanly when new fields appear in an old file (no silent resets).
- Repackage `SpecialistDanceConfig` into `com.sebas.specialistdance.config`.
- ModMenu dependency + `ConfigMenuIntegration`.

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
- [ ] `animationEndBehavior` is dead config in a released version.
- [ ] `DanceRegistry.countFrames` returns `-1` for empty, gapped and unreadable packs
      alike, so the debug log cannot tell them apart.
- [ ] The `putIfAbsent` duplicate-name branch in `DanceRegistry.scan` is unreachable.
- [ ] The layout screen and folder/Rescan buttons exist only in the working tree.

## Out of scope / future ideas

- Server-side sync (multiple players dancing together).
- Localization (lang files) beyond the current keybind entries.
- Loading GIF/JPEG/WebP frames directly — `NativeImage.read` calls `PngInfo.validateHeader`,
  which demands the PNG magic before STB sees the bytes, so PNG is the only decodable
  frame format; users convert with `tools/GifToFrames`.
- `pack.json` per-dance metadata (dropped from v1.4.0).
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
