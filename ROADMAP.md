# Specialist Dance — Development Roadmap

> Living document. Update it whenever a phase changes state — not just at release.
> If this file and the code disagree, the code wins and this file is wrong.

**Current release:** 1.3.1 (tagged) · **Pre-release:** `v1.4.0-rc1` · **Branch:** `v1.4.0`

## What this project is

A client-side Fabric mod (Minecraft 1.21.11, Java 21) that plays an on-screen dance when
the player mines a valuable ore and picks up its drop. The long-term goal is a fully
configurable celebration system: multiple dances, music, user-supplied content, and one
in-game menu that reaches every setting.

## Status legend

| Mark | Meaning |
|---|---|
| ⬜ | planned — not started |
| 🛠 | built — compiles, but nobody has seen it work |
| 🧪 | verified — evidence recorded (log line, screenshot, repro) |
| ✅ | released — version bumped, CHANGELOG entry, tagged in git |

A release can be ✅ while individual checks inside it remain 🛠. Released means shipped,
not proven. Anything still 🛠 at release time is **open debt** and must be listed in
"Open debt" below, or it will be forgotten.

Checkboxes say whether work exists; the marks say whether it has been proven. `[x]` with
no mark means the verification is not the interesting question for that item.

---

## Architecture rules (apply to every phase)

1. **Every phase is one release.** When its checklist is done and tested: bump
   `mod_version` in `gradle.properties`, add a `CHANGELOG.md` entry, tag, mark ✅ here.
2. **Nothing ships uncommitted.** A release starts by committing its own tree. If
   unrelated work is sitting in the working tree, decide its fate before you bump the
   version — do not let it ride along silently.
3. **No orphan config.** A field lands in the same release as the code that reads it
   *and* the menu control that edits it. A field with no consumer does not get merged.
4. **Verification means a fresh run.** A cached `UP-TO-DATE` build, a successful
   compile, or a clean boot log is not evidence. The artifact is a log line, a
   screenshot, or a written repro that demonstrates the feature actually worked.
5. **Every release closes at least one open QA box** from "Open debt". Small and
   real beats a backlog nobody can clear.
6. Keep detection, animation, audio, configuration, and UI in separate classes.
7. Keep the mod client-side; no server dependency.
8. Preserve working fallback logic until its replacement is verified.

---

## Current reality (2026-10-25)

Tagged releases: `v1.0.0`, `v1.1.0`, `v1.2.0`, `v1.3.0`, `v1.3.1`.

Pre-released in `v1.4.0-rc1`: the user-dance-pack feature — `dance/DanceRegistry`,
`dance/DanceTextures`, `activeDance`, the dance selector in the config screen, and
`tools/GifToFrames`. The GIF converter was fixed the same day it shipped (GIF frames
are deltas, so the old export produced half-empty PNGs).

| Claim | State |
|---|---|
| Ore → drop → dance trigger path | 🧪 diamond / deepslate diamond only (`run/logs/latest.log` 20:55:32) |
| Bundled `specialist` dance renders | ✅ shipped since v1.0.0 |
| A **user** pack renders in-game | 🛠 never observed — the only release blocker |
| Config screen edits and persists | 🧪 `activeDance`, `musicTrack`, volume all written to disk |
| Music formats (ogg/wav/mp3/flac…) | ✅ released in v1.3.1 |
| `animationEndBehavior` | 🛠 **dead config** — declared, zero consumers |
| Automated tests | ⬜ none; `test` is `NO-SOURCE` |
| ModMenu entrypoint | ⬜ none |

---

## Phase 1 — v1.0.0: First Dance ✅

- [x] Detect diamonds entering the player's inventory.
- [x] Display the 147-frame dance animation as a HUD overlay (`HudElementRegistry.addLast`).
- [x] Client-side Fabric mod architecture (`SpecialistDanceClient` entrypoint).
- [x] Animation frames packaged at `assets/specialist-dance/textures/gui/frames/`.

## Phase 2 — v1.1.0: The Dance Awakens ✅

- [x] Expand tracking to 6 valuable items (diamond, emerald, raw gold, raw iron, gold nugget, ancient debris).
- [x] Refactor detection into a multi-item tracking foundation.
- [x] Update README for multi-item behavior.

## Phase 3 — v1.2.0: Better Detection ✅ (released; QA incomplete → see Open debt)

- [x] Detect ore block breaks via `ClientPlayerBlockBreakEvents.AFTER` (local player only).
- [x] Ore → expected-drop mapping including deepslate and nether variants (`expectedDrop()`).
- [x] Link dropped item entities to their source ore via proximity + expected-item matching.
- [x] Delay drop resolution to a later tick (spawn packet arrives before item data).
- [x] Pickup detection via mixin on `ClientPacketListener.handleTakeItemEntity` at RETURN.
- [x] Deduplication: one dance per ore position (`triggeredOres`).
- [x] Timeout/cleanup of pending state; full clear on world/dimension change.
- [x] JSON config at `config/specialist-dance.json`.
- [x] Diagnostic logging gated behind `"debug": true`.

## Phase 4 — v1.3.0: Sound On ✅

- [x] Bundle a default track at `assets/specialist-dance/sounds/*.ogg`.
- [x] `audio/MusicManager`; play on `triggerDance()`.
- [x] Load user tracks from `config/specialist-dance/music/` and expose them as options.
- [x] Stop/cleanup on animation end, death, world change, disconnect.
- [x] Config fields `musicEnabled`, `musicVolume`, `musicTrack`.
- [x] Prevent overlapping tracks and duplicate playback.
- [x] Release: 1.3.0 tagged.

## Phase 4b — v1.3.1: More Music Formats ✅

- [x] Bundle `javasound-mp3`, `javasound-flac`, `tritonus-share` via jar-in-jar.
- [x] Scan `.ogg`, `.wav`, `.aiff`, `.aif`, `.au`, `.mp3`, `.flac`.
- [x] Decode non-OGG with `javax.sound.sampled.AudioSystem`, upload to OpenAL.
- [x] Keep OGG Vorbis decoding via STB unchanged.
- [x] Release: 1.3.1 tagged.

---

## Phase 5 — v1.4.0: Dance Packs 🛠

**Goal:** users drop in their own animation and it plays. *(Was Phase 8; pulled
forward because the work already exists in the tree and shipping it uncommitted
violates rule 2.)*

**Status:** pre-released as `v1.4.0-rc1` on 2026-10-25 for in-game testing. Not ✅
until the release blocker below is 🧪 and the version is re-tagged without the `-rc`
suffix. The position/zoom editor is **not** in the rc — it was never typed.

**Scope change from the old plan:** `pack.json` (`name`, `fps`) is **dropped**.
`frameDurationMs` is already global; a per-pack fps would be a second source of
truth nobody has asked for. Re-add only if a real pack needs it.

- [x] Remove hardcoded `FRAME_COUNT = 147`; derive frame count per pack by folder scan.
- [x] `dance/DanceRegistry` — bundled `specialist` + user packs from `config/specialist-dance/dances/<pack>/`.
- [x] `dance/DanceTextures` — load pack frames as `DynamicTexture`s, cache, release on pack change and shutdown.
- [x] Config field `activeDance`, selectable in the config screen. 🛠 the screen saves on
      close (observed for `musicTrack`), but no `activeDance` change has ever reached disk
- [x] One shared playback/cleanup path for bundled and user dances.
- [x] `tools/GifToFrames` — composite GIF frame deltas onto the logical screen canvas. 🧪 verified against `zelda.gif`: 29/29 frames uniform 256×210
- [ ] **Position/zoom editor** — drag the dance anywhere, scroll to zoom, Center / Fit / Reset.
      User packs have arbitrary dimensions, which is what makes the fixed
      `drawSize` + centered placement inadequate; this is the old Phase 11 "Dance
      Settings" item, moved here on purpose.
- [ ] Re-check the bundled `specialist` dance visually. Its frames are 498×351; from v1.0.0
      through v1.3.1 they were stretched into a square 160×160 box, and the aspect fix in
      this release draws the same content at 160×113 — geometrically correct, but about 30%
      less screen area than it has always occupied. Accept the new size or raise `drawSize`;
      do not re-introduce the distortion.
- [ ] **Release blocker:** a hand-made user pack observed rendering in Survival.
- [ ] Update README + CHANGELOG, bump `mod_version` to 1.4.0, `./gradlew build`, tag.

## Phase 6 — v1.5.0: Animation Control ⬜

**Goal:** finish what the old Phase 5 promised. *(Was Phase 5; moved after the
packs ship, because `loop` interacts with the editor's live preview.)*

- [ ] `animationEndBehavior` consumers in the HUD branch. Four values:
      `stop` — play the dance once and clear it (today's behavior, stays the default).
      `hold` — play once, keep the last frame on screen.
      `loop` — repeat the dance until something else ends it.
      `loop-until-music-ends` — repeat while the track plays, clear when it finishes.
- [ ] **Prerequisite:** `MusicManager` exposes only `init`, `getAvailableTracks`, `play`,
      `applyVolume`, `stop` and `shutdown` — nothing reports whether the source is still
      playing. `loop-until-music-ends` cannot exist until it gains `isPlaying()` (or a
      decoded track duration to compare against `animationStartMillis`).
- [ ] Define the no-music fallback: with `musicEnabled` false there is no end signal, so
      `loop-until-music-ends` must degrade to `stop` or to `loop`. Pick one and document it.
- [ ] Unknown or typo'd value falls back to `stop` without throwing.
- [ ] Guard against overlapping restarts mid-animation — pick one: ignore, restart, or queue.
      Note the conflict: under `loop` an animation never ends, so a naive guard blocks
      every future dance. The guard must know whether the current animation is finite.
- [ ] Verify `cooldownMs` (consumed today, never tested): check `lastTriggerMillis`
      surviving a world change, and that the default `0` skips the check entirely.
- [ ] Release: 1.5.0, CHANGELOG, tag.

## Phase 7 — v1.6.0: Solid Ground ⬜

**Goal:** make the config layer trustworthy. *(Was Phase 6.)*

**Scope change:** the ModMenu **placeholder screen** is deleted from the plan. A real,
editing `config.menu.SpecialistDanceConfigScreen` already ships and is opened by the
**K** keybind; a read-only preview of it would be wasted work. ModMenu opens the
screen that exists.

- [ ] Validate and clamp every config value on load; fall back to defaults on nonsense.
- [ ] Merge cleanly when new fields appear in an old file (no silent resets).
- [ ] Repackage `SpecialistDanceConfig` into `com.sebas.specialistdance.config`.
- [ ] Add ModMenu dependency + `ConfigMenuIntegration` opening the existing screen.
- [ ] Keep JSON as the source of truth; menu and file never diverge.
- [ ] Release: 1.6.0, CHANGELOG, tag.

## Phase 8 — v1.7.0: Your Rules ⬜

**Goal:** make triggers configurable. *(Was Phase 7.)*

- [ ] Move the hardcoded ore → drop map from `expectedDrop()` into config, defaults matching today.
- [ ] Enable/disable individual ores.
- [ ] Keep trigger logic independent of animation and audio.
- [ ] (Stretch) celebration counter per ore type, stored in config.
- [ ] Release: 1.7.0, CHANGELOG, tag.

## Phase 9 — v1.8.0: Provable ⬜

**Goal:** stop hand-verifying logic that needs no game running. *(New — this existed
in no earlier plan and is the highest-leverage gap in the project.)*

- [ ] JUnit setup for Minecraft-free classes only; no Loom test harness, no mocks of `Minecraft`.
- [ ] `DanceRegistry.countFrames` — empty dir, gap in sequence, non-contiguous, junk filenames, valid pack.
- [ ] Placement math — aspect fit, oversized `drawSize`, negative/auto-centre positions.
- [ ] GIF compositing — a delta frame with `doNotDispose` must produce a full canvas; disposal
      `restoreToBackgroundColor` and `restoreToPrevious` must each behave. This is the
      regression that shipped inside `tools/GifToFrames` until 2026-10-25.
- [ ] Release: 1.8.0, CHANGELOG, tag.

## Phase 10 — v1.9.0: Stability ⬜

**Goal:** reliability and performance pass. *(Was Phase 9.)*

- [ ] Fix detection/playback edge cases found in the field.
- [ ] Audit entity-tracking maps for leaks (entity removal, world change).
- [ ] Review client event lifecycle for double-registration.
- [ ] Retire diagnostic logs whose systems have passed QA.
- [ ] Extended-session check (memory, tick cost), including user-pack texture caching.
- [ ] Release: 1.9.0, CHANGELOG, tag.

## Phase 11 — v2.0.0: Final Mix ⬜

**Goal:** full integration sweep before the menu is called complete. *(Was Phase 10.)*

- [ ] Detection + animation + music + config verified together under every config combination.
- [ ] Audio/animation start-stop synchronisation re-checked after the end-behavior work.
- [ ] Regression test the complete QA matrix.
- [ ] README "Your content" section for `music/` and `dances/`.
- [ ] Release: 2.0.0, CHANGELOG, tag.

## Phase 12 — v2.1.0: The Control Room ⬜

**Goal:** every feature configurable from one menu. *(Was Phase 11, re-targeted off
v2.0.0: the music and dance sections of that menu already shipped, so the remaining
work is not a major-version rewrite.)*

- [ ] **Music** — playback behavior on dance start/end. *(enable/volume/track: shipped v1.3.0)*
- [ ] **Dance Settings** — duration and end-of-animation behavior. *(size/position: v1.4.0)*
- [ ] **Dances & Characters** — "open folder" button; preview inside the screen.
- [ ] **Triggers** — ore toggles, live-synced with detection.
- [ ] **General** — save/restore, reset to defaults, mod version info.
- [ ] Keybind remapping for the **K** config key.
- [ ] Release: 2.1.0, CHANGELOG, tag.

---

## Open debt

Anything 🛠 at release time lands here until it is 🧪.

- [ ] **Phase 3 QA (v1.2.0), 6 of 7 never run** — mine each tracked ore type (only
      diamond/deepslate diamond is proven); Fortune multi-drop → exactly one dance;
      two ores mined rapidly side by side → no mis-association; chest/trade/mob/pre-existing
      ground drops → no trigger; drop knocked > 4 blocks → no trigger; re-mine the same
      spot later → triggers again; dimension change/disconnect → no stale triggers.
- [ ] `animationEndBehavior` is dead config in a released version (breaks rule 3).
- [ ] `DanceRegistry.countFrames` returns `-1` for empty, gapped and unreadable packs
      alike, so the debug log cannot tell them apart.
- [ ] The `putIfAbsent` duplicate-name branch in `DanceRegistry.scan` is unreachable.

## Out of scope / future ideas

- Server-side sync (multiple players dancing together).
- Localization (lang files).
- Loading animated GIF files directly — `NativeImage.read` calls `PngInfo.validateHeader`,
  which demands the PNG magic before STB sees the bytes, so PNG is the only decodable
  frame format. Users export GIFs to PNG sequences via `tools/GifToFrames`.
- `pack.json` per-dance metadata (dropped from v1.4.0; see that phase).
- Cloud/shared dance pack browser.

## Versioning & release rules

- One phase = one release = one tag. A phase is ✅ only after the tag exists.
- Every release: bump `mod_version`, add a `CHANGELOG.md` entry, update README if
  behavior changed, verify `./gradlew build` with a fresh run, then tag.
- Reordering this file is allowed; releasing out of order is not. If reality beats
  the plan, fix the plan the same day — that is what happened to the 2026-10-25 revision.
