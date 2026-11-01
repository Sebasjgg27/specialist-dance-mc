# Changelog

## Unreleased

Dance packs can now carry their own timing and music, and a raw GIF can be imported
without running the offline converter.

- Added: drop a `.gif` into `config/specialist-dance/dances/` and press **Rescan**.
  The mod composites the GIF frames, writes a `frame_NNN.png` pack, reads the GIF's
  frame delay into `pack.json`, and treats the result as a new dance.
- Added: optional sidecar files next to a dropped GIF — `<name>.ogg` (or another
  supported audio format) and `<name>.json` (copied as `pack.json`).
- Added: `pack.json` with `frameDurationMs` and `track`; per-pack timing overrides the
  global `frameDurationMs`, and a pack's own audio is used while the track selector is
  on `default`.
- Added: pack audio appears in the music selector as `<pack>/<track>`.
- Added: new dance packs open the **Position dance** editor once, both at startup and
  after **Rescan**. The list of packs already shown is persisted in `seenDances`; on
  upgrade, packs already in the folder are marked seen so they do not all pop at once.
- Added: **Fit** now uses the current frame's aspect ratio, and the layout editor
  previews the middle frame instead of a possibly blank frame 0.
- Changed: **Rescan** releases cached pack textures so a replaced GIF or pack reloads.
- Changed: **Play preview** in the K menu plays the track the active pack would use,
  including its own audio, instead of always previewing the global selection.
- Changed: a `pack.json` larger than 1 MB is ignored; the pack still plays with the
  global timing. Unsupported pack audio formats (`.opus`, `.m4a`, …) are logged and
  skipped instead of failing silently.
- Changed: a pack folder named `specialist` is rejected because it would shadow the
  bundled dance.
- Fixed: raw `.gif` files in the dances root were ignored by the scanner.
- Note: no proprietary `.specialgif` format. A GIF is converted to PNG frames because
  `NativeImage.read` only accepts PNG; a custom container would still need the same
  conversion and would not open in any image viewer.

## 1.5.0

Animation control. `animationEndBehavior` is no longer dead config: the HUD reads it,
and the dance now has a defined end when there is no music to end it.

- Added: `stop`, `hold` and `loop` are consumed by the animation — `stop` plays one
  pass and clears the overlay, `hold` keeps the last frame, `loop` wraps around until
  the music ends.
- Added: `MusicManager.isPlaying()`, so the animation can tell when its track has
  finished instead of guessing from a timer.
- Added: one diagnostic line when a dance ends, naming the reason, the configured
  behavior, `musicEnabled` and whether music was driving the end (`debug: true`).
- Changed: with `musicEnabled: false`, or when no audio device is available, there is
  no end signal for `hold` and `loop`, so both fall back to `stop` — one finite pass —
  instead of cancelling on the first frame.
- Changed: world change, player death, an invalid pack and a missing frame now end the
  animation through the same path, which also stops the music.
- Changed: an invalid dance pack says why — unreadable, no `frame_NNN.png` files, or a
  frame run with gaps — instead of the same silent `-1` for all three.
- Fixed: `hold` and `loop` with music off drew nothing at all; the animation was
  cancelled while rendering frame 0.

## 1.4.0

Dance packs + layout editor. User-supplied PNG frame folders become selectable dances;
the animation can be positioned and resized in-game.

- Added: user dance packs from `.minecraft/config/specialist-dance/dances/<name>/` —
  a folder of `frame_000.png … frame_NNN.png` becomes a selectable dance.
- Added: `activeDance` config field and a **Dance pack** selector in the **K** menu.
- Added: `dance/DanceRegistry` (pack scanning, frame-count detection) and
  `dance/DanceTextures` (loads pack frames as dynamic textures, releases them on pack
  change and shutdown).
- Added: **Position dance** screen — drag the animation anywhere, scroll to zoom, with
  **Center**, **Fit** and **reset** buttons; the choice is saved on close.
- Added: **Open music folder** and **Open dances folder** buttons in the **K** menu, so
  custom content is one click away.
- Added: **Rescan** button — picks up music files and dance packs added since launch,
  without restarting the game.
- Added: "Preview dance on close" button in the config screen.
- Added: `tools/GifToFrames.java`, which converts an animated GIF into a dance pack.
- Changed: the frame count is detected per pack instead of hardcoded to 147.
- Changed: the config screen lays out every row from a computed cursor, so the dance
  controls no longer collide with **Done** at short window heights.
- Changed: animation end behaviors renamed to `stop`, `hold`, `loop` (all clear when
  music stops); old `loop-until-music-ends` migrates to `loop`.
- Fixed: blit overload now scales frames to the stage instead of cropping the top-left.
- Fixed: text colors use ARGB so they render in 1.21.11.
- Fixed: MP3 decode no longer uploads an empty OpenAL buffer.

## 1.4.0-rc1

Pre-release for testing user dance packs. The pack render has not been observed
in-game yet — see `ROADMAP.md` Phase 5.

- Added: user dance packs from `.minecraft/config/specialist-dance/dances/<name>/` —
  a folder of `frame_000.png … frame_NNN.png` becomes a selectable dance.
- Added: `activeDance` config field and a **Dance pack** selector in the **K** menu.
- Added: `dance/DanceRegistry` (pack scanning, frame-count detection) and
  `dance/DanceTextures` (loads pack frames as dynamic textures, releases them on pack
  change and shutdown).
- Added: "Preview dance on close" button in the config screen.
- Added: `tools/GifToFrames.java`, which converts an animated GIF into a dance pack.
- Changed: the frame count is detected per pack instead of hardcoded to 147.
- Changed: the config screen lays out every row from a computed cursor, so the dance
  controls no longer collide with **Done** at short window heights.
- Fixed: GIF conversion now composites frame deltas onto the logical screen canvas and
  honours the disposal methods. Previously every frame after the first exported as a
  mostly-empty PNG, because GIF frames usually carry only the pixels that changed.
- Fixed: dance frames draw at their true aspect ratio. The bundled `specialist` frames
  (498×351) had been stretched into a square box since v1.0.0; at the default
  `drawSize` of 160 they now occupy 160×113.
- Fixed: the animation is cleared on world change and on death.
- Known: PNG is the only loadable frame format — `NativeImage.read` rejects anything
  without a PNG header, so GIF, JPEG and WebP must be converted first.
- Known: dance packs are scanned at startup, so the game must be restarted after
  adding files.
- Known: `animationEndBehavior` is still unread by any code; it is implemented in
  v1.5.0.

## 1.3.1

- Changed: custom music supports Ogg Vorbis, WAV, AIFF (`.aiff`/`.aif`), AU,
  MP3 and FLAC files.
- Known: Ogg Opus and ALAC are not supported.

## 1.3.0

- Added: dance-triggered music with a bundled `default` Ogg Vorbis track.
- Added: music settings screen opened with **K** — enable/disable music, adjust volume, select a track, preview it and stop playback.
- Added: custom music support from `.minecraft/config/specialist-dance/music/`. Filenames become track names and `default` is reserved for the bundled track.
- Added: `musicEnabled`, `musicVolume` and `musicTrack` config fields.
- Added: fallback to the bundled `default` track when the selected custom track is missing or cannot be loaded.
- Changed: music restores the previous OpenAL context after playback and shutdown instead of clearing it.
- Changed: buffer loading and shutdown now guard OpenAL context and object IDs.
- Fixed: shutdown no longer clears Minecraft's active OpenAL context.
- Fixed: tracks with more than two channels are downmixed to stereo for playback.
- Known: custom music supports Ogg Vorbis only; Ogg Opus, MP3, WAV, FLAC and ALAC are not supported yet.

## 1.2.0

- Changed: the trigger is now origin-aware — the dance only plays when you mine a valuable ore and pick up that ore's drop (chests, trades, mobs and pre-existing ground items no longer trigger it).
- Added: pickup detection via a Mixin on `ClientPacketListener.handleTakeItemEntity` (local player only, matched by entity id).
- Fixed: drop association now reads the item stack on a later client tick, because the spawn packet arrives before the item data.
- Added: `config/specialist-dance.json` — enabled/debug toggles, association timeouts and radius, and animation duration/size/position/cooldown.
- Changed: diagnostic logging is now gated behind `"debug": true`.

## 1.1.0

- Changed: the diamond check became multi-item tracking — diamond, emerald, raw gold,
  raw iron, gold nugget and ancient debris.
- Added: first pickup hook, a Mixin on `ClientPacketListener.handleTakeItemEntity`.
- Changed: README updated for multi-item behavior.

## 1.0.0

- Initial release: diamond detection and the 147-frame Specialist Dance HUD overlay,
  with the animation frames, icon and mod metadata packaged in the jar.
