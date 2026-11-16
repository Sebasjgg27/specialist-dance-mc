# Specialist Dance

A **client-side** Fabric mod. When something valuable reaches your inventory, a dance
animation plays in the middle of your screen — by default the
147-frame "Specialist" dance meme from *Persona 4* and *The Binding of Isaac*,
or any animation you add as your own [dance pack](#custom-dances).

The default rules are origin-aware: digging up a diamond ore and grabbing the diamond
celebrates, while pulling a diamond out of a chest, trading with a villager or walking
over loot someone else dropped does **not**. Those defaults are just rules — **K** →
**Rules** → **Your rules** lets you write your own, and a rule can fire on mining a
block, picking up an item, killing a mob or opening a container.

- **Minecraft:** Java Edition 1.21.11
- **Loader:** Fabric 0.18.1
- **API:** Fabric API 0.141.5+1.21.11
- **Language:** Java 21
- **Environment:** client only

---

## Features

- **Your rules** — a rule is a trigger, an optional source and an item: `mine` (this block
  yields this item), `pickup` (this item from anywhere), `kill` (this mob drops it) and
  `open` (this block gives it to you when you loot it).
- **Ten preset rules** — the valuable ores below, deepslate and nether variants included.
- **Mining-origin detection** — remembers the ore you break, links the drop to it, and
  triggers only on pickup of that drop.
- **No false positives** — chests, mobs, trades and old ground loot stay silent until you
  write a rule that asks for them.
- **No double triggers** — a Fortune vein can drop several items but only dances once.
- **Dance music** — plays a bundled track while the animation is on screen.
- **Custom music** — add supported audio files and pick one in the settings
  screen.
- **Custom dances** — drop a folder of PNG frames in and select it as your dance.
- **Configurable** — timings, radius, animation size/position/speed, music and
  an on/off switch.
- **Zero noise** — debug logging is off by default.

## Default rules

| Ore | Drop |
| --- | --- |
| Diamond Ore / Deepslate Diamond Ore | Diamond |
| Emerald Ore / Deepslate Emerald Ore | Emerald |
| Gold Ore / Deepslate Gold Ore | Raw Gold |
| Nether Gold Ore | Gold Nugget |
| Iron Ore / Deepslate Iron Ore | Raw Iron |
| Ancient Debris | Ancient Debris |

Anything else — including copper, lapis, redstone and coal — is ignored until you add a
rule for it. **Reset** writes these ten back; **Clear** removes every rule, and the file
then holds an empty list, which nothing re-seeds.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.11
   and **Fabric API**.
2. Drop `specialist-dance-v1.6.1-fabric-1.21.11.jar` into your `mods/` folder.
3. Launch the game — you should see `Specialist Dance is Ready!` in the log.

Two artifacts ship, and only the first one carries content:

| Jar | Contains | Needs |
|---|---|---|
| `specialist-dance-v1.6.1-fabric-1.21.11.jar` | the bundled `specialist` dance (147 frames) and the `default` track | nothing |
| `specialist-dance-v1.6.1-fabric_lite-1.21.11.jar` | the mod only | your own dance pack and track in the config folders |

The mod is otherwise identical. With the lite jar and nothing installed yet, the **K** menu says
which folder to drop files into instead of offering a dance that cannot play.

The mod does not need to be installed on the server.

## Configuration

A config file is created on first launch:

- Dev client: `run/config/specialist-dance.json`
- Normal install: `.minecraft/config/specialist-dance.json`

Missing keys fall back to defaults and are added back to the file automatically.

| Setting | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Turn the whole mod on or off. |
| `debug` | `false` | Log the detection and music pipeline to the console. |
| `rules` | the ten presets | What makes the dance fire — see [Writing rules](#writing-rules). |
| `oreTimeoutMs` | `10000` | How long a broken ore can still claim a drop. |
| `linkTimeoutMs` | `30000` | How long a linked drop can still trigger a dance. |
| `searchRadiusSquared` | `16.0` | Max distance (squared blocks) from drop to ore — `16` = 4 blocks. |
| `dropResolveTimeoutMs` | `2000` | How long to wait for an item entity to receive its item data. |
| `frameDurationMs` | `100` | Milliseconds per animation frame. |
| `animationSpeed` | `1.0` | Playback multiplier applied to every dance: `2.0` plays twice as fast, `0.5` half speed. Clamped to `0.1`–`10.0`. A pack that sets its own `speedMultiplier` overrides this. |
| `stageWidthPercent` | `35` | Share of the screen width the dance stage may use; each frame is scaled to fit inside it. |
| `stageHeightPercent` | `33` | Share of the screen height the dance stage may use. |
| `positionX` | `-1` | X of the stage's top-left; `-1` centres it horizontally. |
| `positionY` | `-1` | Y of the stage's top-left; `-1` centres it vertically. |
| `cooldownMs` | `0` | Minimum gap between dances; `0` = none. |
| `musicEnabled` | `true` | Play music while the dance animation is active. |
| `musicVolume` | `1.0` | Music volume from `0.0` to `1.0`. |
| `musicTrack` | `"default"` | Track name: `default` or a custom filename without its extension. |
| `animationEndBehavior` | `"stop"` | What happens when the frames run out: `stop` clears the dance, `hold` keeps the last frame, `loop` repeats until the music ends. An unknown value falls back to `stop`. With music off there is no end signal, so `hold` and `loop` both play exactly one pass. |
| `activeDance` | `"specialist"` | Dance to play: `specialist` (bundled) or a folder name from the dances folder. |
| `configVersion` | `4` | Schema stamp for the file. A file written before this key existed is detected as version `0` and upgraded on load; a `blockRules`/`pickupRules` split from an interim build is folded into `rules`. You do not need to touch it. |

`drawSize`, `stageWidth` and `stageHeight` are still written to the file, but nothing
reads them: since `v1.4.0` the stage is sized from the percentages above and each frame
keeps its own aspect ratio.

**Tip:** set `"debug": true` to watch the pipeline. A `mine` rule prints one line per stage
it clears:

```text
registered mined minecraft:diamond_ore -> minecraft:diamond at BlockPos{...}
linked drop minecraft:diamond (entity 123) to ore at BlockPos{...}
activated for mined ore at BlockPos{...}
```

The other triggers end with `activated for pickup-only trigger`, `activated for mob death at
BlockPos{...}` or `activated for container minecraft:chest`; an `open` rule also logs
`opened container minecraft:chest` when you right-click it.

## Writing rules

**K** → **Rules** → **Your rules** opens the editor. Each row is one rule: an enable box,
the source's icon and id on the left, the item's icon and id on the right. Left-click a
row to toggle it, right-click to delete it.

| Trigger | Source | Fires when |
| --- | --- | --- |
| `mine` | a block | you break that block and pick up the item the rule names |
| `pickup` | *(none)* | that item reaches your inventory from anywhere |
| `kill` | a mob | that mob dies and its drop reaches you |
| `open` | a container block | you take that item out of that container |

- Type ids by hand or use **Pick block** / **Pick mob** / **Pick item** — a paged grid over
  everything the game registers, with a filter box. `diamond` finds `minecraft:diamond_ore`,
  because the filter matches the part after the `:`.
- **TAB** accepts the grey suggestion in a field. The field shows the whole id once the
  namespace is typed, and offers `-> <id>` while you type only the path.
- **Add rule** refuses a duplicate ("that rule is already in the list") and a malformed id,
  and says which field it wanted: `mob id must look like minecraft:zombie`.
- **Undo** / **Redo** (also `Ctrl`/`Cmd` + `Z` and `Y`) walk back 40 steps over add, delete,
  toggle, clear and reset.
- **Reset** restores the ten presets, **Clear** empties the list, and **Done** saves.

A rule can only fire in the game modes that produce the item. `mine` and `pickup` need the
item to reach you, so breaking a block in Creative does nothing — **Creative gives no drop
entity**. The ten presets need **Survival** to fire at all.

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
- A dance pack can also carry its own audio file; see
  [Custom dances](#custom-dances). Pack tracks appear in the selector as
  `<pack>/<track>`.
- `default` is reserved for the bundled track.
- Press **K** in-game with no other screen open to change the track, volume or
  music setting.
- The settings screen also has **Play preview** and **Stop** buttons.

Ogg Opus and ALAC are not supported. Files added while the game is running are picked
up by **Rescan** (see below) or on the next launch.

## Custom dances

The bundled animation is named `specialist`. The easiest way to add your own is to
drop an animated GIF into the dances folder:

```text
.minecraft/config/specialist-dance/dances/mydance.gif
```

Press **K** → **Content** → **Rescan**. The mod converts the GIF into a pack, reads the GIF's own
frame delay, and opens the **Position dance** editor for it. The folder it creates is:

```text
.minecraft/config/specialist-dance/dances/mydance/
  frame_000.png
  frame_001.png
  ...
  pack.json
```

- The file or folder name becomes the dance name, selectable on the **Dance** pane.
- Frames are written numbered from `000` with no gaps; the count is detected
  automatically. A pack with a hole in the sequence is skipped.
- The first time a new pack is found, the **Position dance** editor opens. Move the
  stage with the mouse, resize it with the wheel, then press **Done**. The editor does
  not reopen for that pack on later launches or rescans.
- A GIF is recognised by its SHA-256, recorded as `source` in the pack's `pack.json`. Dropping the
  same animation again — under any filename — is skipped and the loose copy is deleted. Dropping a
  **different** GIF under a name you already used replaces that pack in place; a folder you authored
  by hand has no `source`, so the importer never touches it.
- PNG is the only image format the mod decodes at render time. GIFs are converted to
  PNG when imported; `.jpg` and `.webp` are not supported.
- A GIF usually stores only the pixels that change between frames, so the importer
  rebuilds every frame on top of the previous one. Some GIFs simply will not look good
  as a dance, and a GIF with its own background paints an opaque box over the game.
- You can still add a folder of PNG frames by hand. Frames must be numbered from `000`
  with no gaps.
- `pack.json` is optional and can override the GIF delay, the playback speed or the track:

  ```json
  {
    "frameDurationMs": 100,
    "speedMultiplier": 2.0,
    "track": "mydance"
  }
  ```

  `frameDurationMs` is clamped to 10–10000 ms and `speedMultiplier` to 0.1–10.0. An
  invalid or missing `speedMultiplier` leaves the pack on the global
  `animationSpeed` from the **K** menu; a valid one wins over that slider, so a pack
  that sets its own speed ignores what you drag the slider to.
- To give a pack its own music, put an audio file next to the frames (or next to the
  GIF before importing) with the same name:

  ```text
  .minecraft/config/specialist-dance/dances/mydance/mydance.ogg
  ```

  The pack's audio is used automatically while the track selector is on `default`.
  Choosing another track in the **K** menu overrides it.
- Both folders are scanned at startup, and **K** → **Rescan** picks up GIFs, music tracks
  and dance packs added since launch without restarting the game.
- Offline conversion is still available:

  ```sh
  java tools/GifToFrames.java mydance.gif ../config/specialist-dance/dances/mydance 256
  ```

## Settings screen

Press **K** (with no other screen open). The screen is two panes: a sidebar of **Music**,
**Dance**, **Animation**, **Rules** and **Content**, and the rows for the selected one.

| Pane | Controls |
| --- | --- |
| **Music** | **Music Enabled**, the volume slider, **Play preview** / **Stop**, a scrollable list of every track in `config/specialist-dance/music`, **Open music folder** |
| **Dance** | **Preview dance on close**, **Position dance**, a scrollable list of the bundled `specialist` plus your packs, and a live preview of the selected one |
| **Animation** | **End behavior** (`stop` / `hold` / `loop`) and the **Animation speed** slider |
| **Rules** | **Your rules** — the rule editor, see [Writing rules](#writing-rules) |
| **Content** | **Open music folder**, **Open dances folder**, **Rescan** |

**Position dance** drags the stage with the mouse and zooms it with the wheel, with
**Center** / **Fit** / **reset**. Everything is written to `specialist-dance.json` when a
screen closes; `enabled` and `debug` have no control here — they live in the file.

If [Mod Menu](https://modrinth.com/mod/modmenu) is installed, the same screen opens from
**Mods → Specialist Dance → Config**; the **K** keybind still works either way.

**Preview dance on close** starts the animation as the screen shuts: the HUD paints
behind any open screen, so a preview could not be seen from inside the menu.

## How it works

1. **Match** — the mod keeps one list of rules and asks it what it cares about. Nothing is
   hardcoded, and a disabled row is skipped before any state is recorded.
2. **Register the source** — breaking a block that a `mine` rule names records the position
   and the expected drop; a mob that dies records its death position for `kill` rules;
   right-clicking a container that an `open` rule names snapshots your inventory.
3. **Link** — an item entity spawns empty (the server sends its contents in a later packet),
   so the mod waits a tick, reads the item, and links it to the nearby ore or corpse. A
   `pickup` rule needs no source at all: the item alone is enough.
4. **Fire** — a Mixin notices when *you* collect that entity, or a tick loop compares your
   inventory against the container snapshot and fires on a positive delta.
5. **Music** — if music is enabled, the selected track starts with the animation and stops
   when it ends.

State is cleared when you change world or dimension, and entries expire on
their own.

## Testing

Run in **Survival** — Creative mining drops nothing, so no `mine` or `pickup` rule can fire
there.

| Scenario | Expected |
| --- | --- |
| Mine and collect a preset ore | `registered mined` → `linked drop` → `activated for mined ore` (with `debug`) + dance + music |
| Break dirt or stone | Nothing |
| Grab a diamond from a chest, a trade or old ground loot | Nothing — until you add an `open` or `pickup` rule |
| Mine with Fortune | Several `linked drop`, one `activated` |
| Re-mine the same spot | Triggers again |
| Kill a mob a `kill` rule names | `activated for mob death at …` |
| Take an item a rule names out of a matching container | `opened container …` then `activated for container …` |
| Toggle a rule off and repeat | Nothing |
| Switch world or dimension | No stale trigger and music stops |
| Open settings with K | The five panes and the track list appear |
| Preview a custom track | That track plays until stopped |
| Select a custom dance pack | The **Dance** pane previews it; see `ROADMAP.md` Phase 6b |

## Troubleshooting

**The dance never plays.** Check, in order:

1. No rule matches. **K** → **Rules** → **Your rules** — every rule needs its enable box on,
   and the ids must be exact (`minecraft:diamond_ore`, not `diamond_ore`).
2. You're in **Creative** — mining there drops no item entity, so `mine` and `pickup` cannot
   fire.
3. The item came from a chest, trade, mob or old ground loot while you only have `mine` rules.
   Add an `open`, `kill` or `pickup` rule for that case.
4. The drop landed more than 4 blocks from the ore or corpse, or you picked it up more
   than 30 s later.
5. `"enabled"` is `false` in the config.
6. `"debug"` is `false`, so the pipeline prints nothing — turn it on and watch
   for `registered` / `opened container`, then `linked drop`, then `activated for`.

**The dance plays but there is no music.**

1. Check that `"musicEnabled"` is `true`.
2. Check that `"musicVolume"` is above `0`.
3. If you use a custom track, make sure it is in the music folder, uses a
   supported extension, and contains audio in the corresponding format.
4. Press **K** → **Content** → **Rescan** after adding or renaming music files.
5. Open the settings screen and try **Play preview**. If the custom track
   fails, the mod falls back to `default`.

**The music keeps playing after the dance.** Press **Stop** in the settings
screen, or check that the animation is actually finishing.

## Building & running

Prerequisites: JDK 21 and Git. Gradle is included in the repo.

```sh
./gradlew build                          # full jar lands in build/libs/
./gradlew build -PmodVariant=lite        # same, without the bundled dance and track
./gradlew runClient                      # launch a dev client
./gradlew runClient -PmodVariant=lite    # dev client as the lite build ships
./gradlew build --offline                # when your dependencies are already cached
```

The first build needs network access to Fabric's Maven and Mojang's version
manifest.

## Project files

- `SpecialistDanceClient.java` — entry point, detection pipeline, tick cleanup
  and HUD animation.
- `config/SpecialistDanceConfig.java` — the JSON config model.
- `audio/MusicManager.java` — music scanning, decoding, playback and
  lifecycle.
- `dance/DanceRegistry.java` — bundled dance plus user packs, per-pack frame count.
- `dance/DancePackMeta.java` — reads a pack's `pack.json` overrides.
- `dance/GifImporter.java` — converts a dropped GIF into a frame pack at runtime.
- `dance/DanceTextures.java` — loads pack frames as dynamic textures and places them.
- `config/menu/SpecialistDanceConfigScreen.java` — the two-pane **K** settings screen.
- `config/menu/OreTriggersScreen.java` — **Your rules** editor: the rule list, the trigger
  selector, the id fields and the footer actions.
- `config/menu/ItemGridScreen.java` — the paged creative-style picker behind **Pick
  block** / **Pick mob** / **Pick item**.
- `config/menu/DanceLayoutScreen.java` — drag/zoom position editor.
- `integration/ModMenuIntegration.java` — opens the settings screen from Mod Menu.
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

- Rules match ids exactly and the namespace is part of the id — `minecraft:diamond` is a
  different string from `modpack:diamond`, so a modded ore needs its own rule.
- Drops knocked more than 4 blocks from the ore, or collected more than 30 s
  later, won't link.
- The frame count is detected per dance pack, so adding frames only means
  adding files — but every frame in a pack is drawn on its own canvas, so
  mixed frame sizes will make the animation shift.
- The stage is global: **Position dance** moves and zooms every dance together, not per
  pack.
- Association state intentionally doesn't survive a world or dimension change.
- Custom music supports `.ogg` (Vorbis), `.wav`, `.aiff`, `.aif`, `.au`,
  `.mp3`, and `.flac` files in the top level of the music folder. Ogg Opus and
  ALAC are not supported.
- Custom track names come from filenames; metadata tags and subdirectories
  are not used.
- Music and dance packs are read at startup and by **Content** → **Rescan**; a GIF dropped
  while the game runs is converted on the render thread, so a large one can hitch.

## Contributing

Bug reports, tests and pull requests are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md) for setup, coding and testing guidelines.
Release history lives in [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
