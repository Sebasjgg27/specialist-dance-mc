# Specialist Dance — Development Roadmap

> Living document. Update this file whenever a phase ships.
> Status legend: ✅ shipped · 🚧 in progress · ⬜ planned

**Current version:** 1.3.0 · **Next target:** 1.4.0 (Dance Floor)

---

## Overview

Specialist Dance is a client-side Fabric mod (Minecraft 1.21.11, Java 21) that plays an on-screen dance animation when the player mines a valuable ore and picks up its drop. The long-term goal is a fully configurable celebration system: music, multiple dances, persistent settings, and an in-game config menu.

**Architecture rules (apply to every phase):**

1. **Every phase is one release.** When a phase's checklist is done and tested: bump `mod_version` in `gradle.properties`, add a `CHANGELOG.md` entry, tag the release in git, and mark the phase ✅ here.
2. Keep detection, animation, audio, configuration, and UI in separate classes.
3. Ship and test each feature before building dependent features on top of it.
4. Diagnostic logging stays (`"debug": true` in config) until its system passes QA.
5. Do not remove working fallback logic until its replacement is verified.
6. Compile and test after meaningful changes.
7. Keep the mod client-side; no server dependency.

---

## Planned locations (where new systems will live)

| System | Location | Introduced in |
|---|---|---|
| Music assets (mod-bundled) | `src/main/resources/assets/specialist-dance/sounds/*.ogg` | v1.3.0 |
| Music loader & playback | `com.sebas.specialistdance.audio.MusicManager` | v1.3.0 |
| Config model (existing) | `com.sebas.specialistdance.SpecialistDanceConfig` → move to `com.sebas.specialistdance.config.SpecialistDanceConfig` | v1.5.0 (repackage) |
| Config menu (in-game screen) | `com.sebas.specialistdance.config.menu.SpecialistDanceConfigScreen`, opened via **ModMenu** entrypoint (`com.sebas.specialistdance.config.menu.ConfigMenuIntegration` registered in `fabric.mod.json`); built with a custom `Screen` (no cloth-config dependency) | v1.5.0 (ModMenu hook + placeholder) → v2.0.0 (full menu) |
| Animation registry | `com.sebas.specialistdance.dance.DanceRegistry` | v1.7.0 |
| **User music packs** | `.minecraft/config/specialist-dance/music/*.ogg` — any `.ogg` dropped here is scanned at startup and becomes selectable in config/menu | v1.3.0 |
| **User dance packs (GIF/frames)** | `.minecraft/config/specialist-dance/dances/<pack_name>/` — each folder is one dance: `frame_000.png … frame_NNN.png` (frame count auto-detected) plus optional `pack.json` (`{ "name", "fps" }`). Folders are scanned at startup; packs appear alongside bundled dances in the config/menu | v1.7.0 |

> User packs are read-only to the mod (never rewritten). Invalid packs are skipped with a debug log warning. Bundled assets always remain available as defaults.

---

## Phase 1 — v1.0.0: First Dance ✅

- [x] Detect diamonds entering the player's inventory.
- [x] Display the 147-frame dance animation as a HUD overlay (`HudElementRegistry.addLast`).
- [x] Client-side Fabric mod architecture (`SpecialistDanceClient` entrypoint).
- [x] Animation frames packaged at `assets/specialist-dance/textures/gui/frames/`.

## Phase 2 — v1.1.0: The Dance Awakens ✅

- [x] Expand tracking from diamonds to 6 valuable items (diamond, emerald, raw gold, raw iron, gold nugget, ancient debris).
- [x] Refactor detection logic into a multi-item tracking foundation.
- [x] Update README for multi-item behavior.

## Phase 3 — v1.2.0: Better Detection ✅ (code-complete; QA checklist below)

- [x] Detect ore block breaks via `ClientPlayerBlockBreakEvents.AFTER` (local player only).
- [x] Ore → expected-drop mapping including deepslate and nether variants (`expectedDrop()`).
- [x] Link dropped item entities to their source ore via proximity + expected-item matching (`findSourceOre()`).
- [x] Delay drop resolution to a later tick (spawn packet arrives before item data).
- [x] Pickup detection via mixin on `ClientPacketListener.handleTakeItemEntity` at RETURN (local player only, matched by entity id).
- [x] Deduplication: one dance per ore position (`triggeredOres`).
- [x] Timeout/cleanup of pending ores, drop links, and pending drops; full state clear on world/dimension change.
- [x] JSON config at `config/specialist-dance.json` (enabled, debug, timeouts, radius, animation size/position/duration, cooldown).
- [x] Diagnostic logging gated behind `"debug": true`.

> **Note:** the `registered ore` log only prints with `"debug": true` in `config/specialist-dance.json` (default `false`). This is intentional, not a bug.

### Phase 3 QA checklist (manual, with `"debug": true`)

- [ ] Mine and pick up each tracked ore type (overworld, deepslate, nether gold ore, ancient debris).
- [ ] Fortune enchantment: multiple drops from one ore → exactly **one** dance.
- [ ] Rapidly mine two ores close together → no mis-association.
- [ ] Old ground drops, chest loot, villager trades, mob drops → no trigger.
- [ ] Drop knocked > 4 blocks away → no trigger (by design).
- [ ] Re-mine the same spot later → triggers again.
- [ ] Dimension change / disconnect → no stale triggers, no errors.

**Completion criteria:** valid mining pickups always trigger exactly one dance; nothing else does.

## Phase 4 — v1.3.0: Sound On ✅

**Goal:** Music playback synchronized with the dance. **Release: v1.3.0**

- [x] Package at least one default track at `assets/specialist-dance/sounds/*.ogg`.
- [x] Create `com.sebas.specialistdance.audio.MusicManager`; register/play music when a dance starts (`triggerDance()`).
- [x] Load user tracks from `config/specialist-dance/music/*.ogg` at startup and expose them as selectable options.
- [x] Stop/cleanup playback when the animation ends or the mod is disabled.
- [x] Config fields: `musicEnabled`, `musicVolume`, `musicTrack` (name of bundled or user track).
- [x] Prevent overlapping tracks and duplicate playback.
- [x] Handle interruption: world change, death, disconnect mid-dance.
- [x] Release: bump to 1.3.0, CHANGELOG entry, tag.

## Phase 5 — v1.4.0: Dance Floor (polish) ⬜

**Goal:** Finish animation behavior options. *(Size, position, and duration already shipped in 1.2.0.)* **Release: v1.4.0**

- [ ] Config option for end-of-animation behavior: stop (default) / loop / hold last frame.
- [ ] Cooldown verification and sensible repeated-trigger behavior (config `cooldownMs` exists; test and document).
- [ ] Guard against overlapping dance restarts while an animation is playing.
- [ ] Release: bump to 1.4.0, CHANGELOG entry, tag.

## Phase 6 — v1.5.0: Basic Controls ⬜

**Goal:** Harden the config system and open the door to the in-game menu. *(Persistence already ships in 1.2.0.)* **Release: v1.5.0**

- [ ] Validate config values (clamp sizes/timeouts, fall back to defaults on nonsense input).
- [ ] Migrate/merge configs cleanly when new fields are added.
- [ ] Repackage config classes under `com.sebas.specialistdance.config`.
- [ ] Add **ModMenu** dependency and entrypoint: `com.sebas.specialistdance.config.menu.ConfigMenuIntegration` (declared in `fabric.mod.json`) opening a **placeholder** `SpecialistDanceConfigScreen` (shows current values read-only + "full menu in v2.0.0"). The real screen is built in v2.0.0 at `com.sebas.specialistdance.config.menu.SpecialistDanceConfigScreen`.
- [ ] Keep JSON as the source of truth; the menu edits the same `config/specialist-dance.json`.
- [ ] Release: bump to 1.5.0, CHANGELOG entry, tag.

## Phase 7 — v1.6.0: Your Rules ⬜

**Goal:** Make triggers configurable. **Release: v1.6.0**

- [ ] Move the hardcoded ore → drop map from `expectedDrop()` into config (list of `block → item` pairs, with defaults matching current behavior).
- [ ] Enable/disable individual ores.
- [ ] Keep trigger logic independent of animation and audio systems.
- [ ] (Optional stretch) celebration statistics counter per ore type, stored in config.
- [ ] Release: bump to 1.6.0, CHANGELOG entry, tag.

## Phase 8 — v1.7.0: More Dances ⬜

**Goal:** Multiple animations with a resource-management layer — including **user-supplied dance packs**. **Release: v1.7.0**

- [ ] Remove hardcoded `FRAME_COUNT = 147`; derive frame count per dance pack (folder scan / `pack.json`).
- [ ] Animation registry `com.sebas.specialistdance.dance.DanceRegistry`:
  - Bundled dances from `assets/specialist-dance/textures/gui/dances/<name>/frame_%03d.png`.
  - **User dances** from `config/specialist-dance/dances/<pack_name>/frame_%03d.png` (+ optional `pack.json` with `name` and `fps`) — users drop their own GIF-exported frame sequences here, no rebuild needed.
- [ ] Config field `activeDance` to select the bundled or user dance.
- [ ] All dances share the same playback/cleanup path.
- [ ] Ship at least one additional bundled dance; verify a hand-made user pack loads.
- [ ] Release: bump to 1.7.0, CHANGELOG entry, tag.

## Phase 9 — v1.8.0: Stability ⬜

**Goal:** Reliability and performance pass. **Release: v1.8.0**

- [ ] Fix detection/playback edge cases found in the field.
- [ ] Audit entity-tracking maps for leaks (entity removal, world change).
- [ ] Review client event lifecycle for double-registrations.
- [ ] Remove obsolete code; retire diagnostic logs whose systems have passed QA.
- [ ] Extended-session performance check (memory, tick cost), including user-pack rescans.
- [ ] Release: bump to 1.8.0, CHANGELOG entry, tag.

## Phase 10 — v1.9.0: Final Mix ⬜

**Goal:** Full integration before the menu release. **Release: v1.9.0**

- [ ] Verify detection + animation + music + config all interact correctly.
- [ ] Confirm audio/animation start/stop stay synchronized under all config combinations.
- [ ] Regression test the complete QA matrix.
- [ ] Update README (including a "Your content" section explaining the `music/` and `dances/` folders), CHANGELOG, and release notes.
- [ ] Release: bump to 1.9.0, CHANGELOG entry, tag.

## Phase 11 — v2.0.0: The Control Room ⬜

**Goal:** Replace the v1.5.0 placeholder with the full in-game configuration menu. **Release: v2.0.0**

**Where it lives:** `com.sebas.specialistdance.config.menu.SpecialistDanceConfigScreen` — a custom Fabric `Screen` (no cloth-config dependency), opened from ModMenu via `ConfigMenuIntegration` and from the in-game Mods screen. It edits the same `config/specialist-dance.json` (JSON remains the source of truth).

Sections:

- [ ] **Music** — enable/disable, volume, pick a bundled or user track from `config/specialist-dance/music/`, playback behavior on dance start/end.
- [ ] **Dance Settings** — size, screen position, duration, end-of-animation behavior.
- [ ] **Dances & Characters** — browse bundled + user packs from `config/specialist-dance/dances/`, select the active dance, preview, "open folder" button.
- [ ] **Triggers** — which ores/conditions activate the dance; live-synced with the detection system.
- [ ] **General** — save/restore, reset to defaults, mod version info.

**Completion criteria:** every feature configurable through one menu; settings persist across sessions; no regressions in normal gameplay.
**Release:** bump to 2.0.0, CHANGELOG entry, tag.

---

## Out of scope / future ideas

- Server-side sync (multiple players dancing together).
- Localization (lang files).
- Animated GIF files loaded directly (users export GIFs to PNG frame sequences; see v1.7.0 packs).
- Cloud/shared dance pack browser.

---

## Versioning & release rules

- **Every phase produces exactly one release** (Phase N → vX.Y.Z). A phase is only marked ✅ after its release is tagged.
- Version numbers follow the phase table above (`gradle.properties` → `mod_version`, `CHANGELOG.md` entry per release).
- Every release: bump version, update CHANGELOG, tag in git, verify `./gradlew build` succeeds.
- This file is committed to the repo — update the checkboxes as work lands so it always reflects reality.
