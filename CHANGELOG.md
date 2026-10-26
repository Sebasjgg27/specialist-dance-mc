# Changelog

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

- Multi-item tracking (diamond, emerald, gold, iron, netherite ingots/blocks) and README updates.

## 1.0.0

- Initial release: diamond detection and a 147-frame Specialist Dance HUD overlay.
