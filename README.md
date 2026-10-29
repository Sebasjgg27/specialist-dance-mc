# Specialist Dance

A **client-side** Fabric mod. When you mine a valuable ore and pick up its
drop, a dance animation plays in the middle of your screen — by default the
147-frame "Specialist" dance meme from *Persona 4* and *The Binding of Isaac*,
or any animation you add as your own [dance pack](#custom-dances).

The trigger is origin-aware on purpose: digging up a diamond ore and grabbing
the diamond celebrates. Pulling a diamond out of a chest, trading with a
villager, or walking over loot someone else dropped does **not**.

- **Minecraft:** Java Edition 1.21.11
- **Loader:** Fabric 0.18.1
- **API:** Fabric API 0.141.5+1.21.11
- **Language:** Java 21
- **Environment:** client only

---

## Features

- **Mining-origin detection** — remembers the ore you break, links the drop to
  it, and triggers only on pickup of that drop.
- **Tracked ores** — the six valuable ones below, deepslate variants included.
- **No false positives** — chests, mobs, trades and old ground loot are
  ignored.
- **No double triggers** — a Fortune vein can drop several items but only
  dances once.
- **Dance music** — plays a bundled track while the animation is on screen.
- **Custom music** — add supported audio files and pick one in the settings
  screen.
- **Custom dances** — drop a folder of PNG frames in and select it as your dance.
- **Configurable** — timings, radius, animation size/position/speed, music and
  an on/off switch.
- **Zero noise** — debug logging is off by default.

## Tracked ores

| Ore | Drop |
|---|---|
| Diamond Ore / Deepslate Diamond Ore | Diamond |
| Emerald Ore / Deepslate Emerald Ore | Emerald |
| Gold Ore / Deepslate Gold Ore | Raw Gold |
| Nether Gold Ore | Gold Nugget |
| Iron Ore / Deepslate Iron Ore | Raw Iron |
| Ancient Debris | Ancient Debris |

Anything else — including copper, lapis, redstone and coal — is ignored.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.11
   and **Fabric API**.
2. Drop `specialist-dance-1.4.0-rc1.jar` into your `mods/` folder.
3. Launch the game — you should see `Specialist Dance is Ready!` in the log.

The mod does not need to be installed on the server.

## Configuration

A config file is created on first launch:

- Dev client: `run/config/specialist-dance.json`
- Normal install: `.minecraft/config/specialist-dance.json`

Missing keys fall back to defaults and are added back to the file automatically.

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Turn the whole mod on or off. |
| `debug` | `false` | Log the detection and music pipeline to the console. |
| `oreTimeoutMs` | `10000` | How long a broken ore can still claim a drop. |
| `linkTimeoutMs` | `30000` | How long a linked drop can still trigger a dance. |
| `searchRadiusSquared` | `16.0` | Max distance (squared blocks) from drop to ore — `16` = 4 blocks. |
| `dropResolveTimeoutMs` | `2000` | How long to wait for an item entity to receive its item data. |
| `frameDurationMs` | `50` | Milliseconds per animation frame. |
| `drawSize` | `160` | Size of the animation on screen, in pixels. |
| `positionX` | `-1` | X position; `-1` centres it horizontally. |
| `positionY` | `-1` | Y position; `-1` centres it vertically. |
| `cooldownMs` | `0` | Minimum gap between dances; `0` = none. |
| `musicEnabled` | `true` | Play music while the dance animation is active. |
| `musicVolume` | `1.0` | Music volume from `0.0` to `1.0`. |
| `musicTrack` | `"default"` | Track name: `default` or a custom filename without its extension. |
| `activeDance` | `"specialist"` | Dance to play: `specialist` (bundled) or a folder name from the dances folder. |

**Tip:** set `"debug": true` to watch the pipeline — it prints three lines
per ore:

```text
registered ore -> linked drop -> activated for ore
```

## Music

Music starts when a dance triggers and stops when the animation ends.

- `default` is the bundled track.
- To add your own music, put `.ogg` (Vorbis), `.wav`, `.aiff`, `.aif`, `.au`,
  `.mp3`, or `.flac` files in:

  ```text
  .minecraft/config/specialist-dance/music/
  ```

- The filename becomes the track name, without its extension. For example,
  `victory.ogg` becomes `victory`.
- `default` is reserved for the bundled track.
- Press **K** in-game with no other screen open to change the track, volume or
  music setting.
- The settings screen also has **Play preview** and **Stop** buttons.

Ogg Opus and ALAC are not supported. Files added while the game is running are picked
up by **Rescan** (see below) or on the next launch.

## Custom dances

The bundled animation is named `specialist`. To add your own, create a folder
per dance:

```text
.minecraft/config/specialist-dance/dances/<name>/frame_000.png
.minecraft/config/specialist-dance/dances/<name>/frame_001.png
...
```

- The folder name becomes the dance name, selectable with **K** → **Dance pack**.
- Frames must be numbered from `000` with no gaps; the count is detected
  automatically. A pack with a hole in the sequence is skipped.
- PNG is the only image format the mod can decode — `NativeImage.read` checks the
  PNG header before handing the bytes to the texture loader, so `.gif`, `.jpg`
  and `.webp` files cannot be used as frames directly.
- A GIF usually stores only the pixels that change between frames, so the
  converter rebuilds every frame on top of the previous one before writing it.
  Check the result: a pack whose frames look torn or half-empty came from a
  badly exported GIF, and some GIFs simply will not look good as a dance.
- A GIF with its own background paints an opaque box over the game; only GIFs
  with a real transparent background blend in.
- GIFs are not loaded directly — export them to PNG frames first:

  ```sh
  java tools/GifToFrames.java mydance.gif ../config/specialist-dance/dances/mydance 256
  ```

- Like music, the dances folder is scanned at startup, so restart after adding
  files. (The in-progress **Rescan** button covers music tracks only; dance packs
  still need a restart.)

## Settings screen

Press **K** (with no other screen open) for: music on/off, volume, track selector,
**Play preview** / **Stop**, the **Dance pack** selector, **Preview dance on close**
and **Done**. Everything is written to `specialist-dance.json` when the screen closes.

Not in the `1.4.0-rc1` jar yet — it is in the working tree, unverified: a **Position
dance** editor (drag to move, scroll to zoom, **Center** / **Fit** / **reset**),
**Open music folder** and **Open dances folder** shortcuts, and a **Rescan** button
that picks up files added since launch without a restart.

## How it works

1. **Break** — breaking a tracked ore records its position and expected drop
   for a few seconds.
2. **Link** — the item entity spawns empty (the server sends its contents in a
   later packet), so the mod waits a tick, reads the item, and links it back to
   the nearby broken ore.
3. **Pickup** — a Mixin notices when *you* collect that entity; if it's
   linked, the dance starts.
4. **Music** — if music is enabled, the selected track starts with the
   animation and stops when it ends.

State is cleared when you change world or dimension, and entries expire on
their own.

## Testing

Run in **Survival** — Creative mining drops nothing.

| Scenario | Expected |
|---|---|
| Mine and collect a tracked ore | Three log lines (with `debug`) + dance + music |
| Break dirt or stone | Nothing |
| Open a chest / trade / grab old ground loot | Nothing |
| Mine with Fortune | Several `linked drop`, one `activated` |
| Re-mine the same spot | Triggers again |
| Switch world or dimension | No stale trigger and music stops |
| Open settings with K | Music controls and track list appear |
| Preview a custom track | That track plays until stopped |
| Select a custom dance pack | Not yet verified in-game — see `ROADMAP.md` Phase 5 |

## Troubleshooting

**The dance never plays.** Check, in order:

1. You're in **Survival** — Creative mining drops nothing.
2. The item came from a chest, trade, mob or old ground loot instead of the
   ore you just broke.
3. The drop landed more than 4 blocks from the ore, or you picked it up more
   than 30 s later.
4. `"enabled"` is `false` in the config.
5. `"debug"` is `false`, so the pipeline prints nothing — turn it on and watch
   for `registered` → `linked` → `activated`.

**The dance plays but there is no music.**

1. Check that `"musicEnabled"` is `true`.
2. Check that `"musicVolume"` is above `0`.
3. If you use a custom track, make sure it is in the music folder, uses a
   supported extension, and contains audio in the corresponding format.
4. Restart the game after adding or renaming music files.
5. Open the settings screen and try **Play preview**. If the custom track
   fails, the mod falls back to `default`.

**The music keeps playing after the dance.** Press **Stop** in the settings
screen, or check that the animation is actually finishing.

## Building & running

Prerequisites: JDK 21 and Git. Gradle is included in the repo.

```sh
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # launch a dev client
./gradlew build --offline  # when your dependencies are already cached
```

The first build needs network access to Fabric's Maven and Mojang's version
manifest.

## Project files

- `SpecialistDanceClient.java` — entry point, detection pipeline, tick cleanup
  and HUD animation.
- `SpecialistDanceConfig.java` — the JSON config model.
- `audio/MusicManager.java` — music scanning, decoding, playback and
  lifecycle.
- `dance/DanceRegistry.java` — bundled dance plus user packs, per-pack frame count.
- `dance/DanceTextures.java` — loads pack frames as dynamic textures and places them.
- `config/menu/SpecialistDanceConfigScreen.java` — music and mod settings
  screen.
- `config/menu/DanceLayoutScreen.java` — drag/zoom position editor (working tree).
- `mixin/ClientPacketListenerMixin.java` — reports pickups of the local
  player.
- `assets/specialist-dance/` — icon, bundled music and the 147 animation
  frames.
- `tools/GifToFrames.java` — converts an animated GIF into a dance pack (dev-only).

## Technical notes

- `handleTakeItemEntity` is hooked at `RETURN`, not `HEAD` — the method hops
  threads at its start, so `HEAD` would fire twice.
- An item entity's stack is only filled in by a later `SetEntityData` packet,
  which is why linking happens on the client tick instead of at entity spawn.
- Block drops carry a 10-tick pickup delay, so the link is always ready before
  you can collect them.
- Music uses its own OpenAL context and restores the previous context after
  playback and shutdown operations. It prefers
  `ALC_EXT_thread_local_context` so Minecraft's context is not left cleared.
- The music source is created during init, the buffer is loaded on demand, and
  both are released on shutdown.

## Limitations

- The ore list is hardcoded in `SpecialistDanceClient.expectedDrop()` —
  adding ores means a code change.
- Drops knocked more than 4 blocks from the ore, or collected more than 30 s
  later, won't link.
- The frame count is detected per dance pack, so adding frames only means
  adding files — but every frame in a pack is drawn on its own canvas, so
  mixed frame sizes will make the animation shift.
- Association state intentionally doesn't survive a world or dimension change.
- Custom music supports `.ogg` (Vorbis), `.wav`, `.aiff`, `.aif`, `.au`,
  `.mp3`, and `.flac` files in the top level of the music folder. Ogg Opus and
  ALAC are not supported.
- Custom track names come from filenames; metadata tags and subdirectories
  are not used.
- The music folder is scanned at startup, so restart the game after adding
  files.

## Contributing

Bug reports, tests and pull requests are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md) for setup, coding and testing guidelines.
Release history lives in [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
