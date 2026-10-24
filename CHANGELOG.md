# Changelog

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
