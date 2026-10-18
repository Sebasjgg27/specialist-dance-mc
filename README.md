# Specialist Dance

A **client-side** Fabric mod. When you mine a valuable ore and pick up its drop, a 147-frame dance animation plays in the middle of your screen — the "Specialist" dance meme from *Persona 4* and *The Binding of Isaac*.

The trigger is origin-aware on purpose: digging up a diamond ore and grabbing the diamond celebrates. Pulling a diamond out of a chest, trading with a villager, or walking over loot someone else dropped does **not**.

- **Minecraft:** Java Edition 1.21.11
- **Loader:** Fabric 0.18.1
- **API:** Fabric API 0.141.5+1.21.11
- **Language:** Java 21
- **Environment:** client only

---

## Features

- **Mining-origin detection** — remembers the ore you break, links the drop to it, and triggers only on pickup of that drop.
- **Tracked ores** — the six valuable ones below, deepslate variants included.
- **No false positives** — chests, mobs, trades and old ground loot are ignored.
- **No double triggers** — a Fortune vein can drop several items but only dances once.
- **Configurable** — timings, radius, animation size/position/speed and an on/off switch live in a JSON file.
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

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.11 and **Fabric API**.
2. Drop `specialist-dance-1.2.0.jar` into your `mods/` folder.
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
| `debug` | `false` | Log the detection pipeline to the console. |
| `oreTimeoutMs` | `10000` | How long a broken ore can still claim a drop. |
| `linkTimeoutMs` | `30000` | How long a linked drop can still trigger a dance. |
| `searchRadiusSquared` | `16.0` | Max distance (squared blocks) from drop to ore — `16` = 4 blocks. |
| `dropResolveTimeoutMs` | `2000` | How long to wait for an item entity to receive its item data. |
| `frameDurationMs` | `50` | Milliseconds per animation frame. |
| `drawSize` | `160` | Size of the animation on screen, in pixels. |
| `positionX` | `-1` | X position; `-1` centres it horizontally. |
| `positionY` | `-1` | Y position; `-1` centres it vertically. |
| `cooldownMs` | `0` | Minimum gap between dances; `0` = none. |

**Tip:** set `"debug": true` to watch the pipeline — it prints three lines per ore:

```
registered ore -> linked drop -> activated for ore
```

## How it works

1. **Break** — breaking a tracked ore records its position and expected drop for a few seconds.
2. **Link** — the item entity spawns empty (the server sends its contents in a later packet), so the mod waits a tick, reads the item, and links it back to the nearby broken ore.
3. **Pickup** — a Mixin notices when *you* collect that entity; if it's linked, the dance starts. Each ore position can only trigger once.

State is cleared when you change world or dimension, and entries expire on their own.

## Testing

Run in **Survival** — Creative mining drops nothing.

| Scenario | Expected |
|---|---|
| Mine and collect a tracked ore | Three log lines (with `debug`) + dance |
| Break dirt or stone | Nothing |
| Open a chest / trade / grab old ground loot | Nothing |
| Mine with Fortune | Several `linked drop`, one `activated` |
| Re-mine the same spot | Triggers again |
| Switch world or dimension | No stale trigger |

## Troubleshooting

**The dance never plays.** Check, in order:

1. You're in **Survival** — Creative mining drops nothing.
2. The item came from a chest, trade, mob or old ground loot instead of the ore you just broke.
3. The drop landed more than 4 blocks from the ore, or you picked it up more than 30 s later.
4. `"enabled"` is `false` in the config.
5. `"debug"` is `false`, so the pipeline prints nothing — turn it on and watch for `registered` → `linked` → `activated`.

## Building & running

Prerequisites: JDK 21 and Git. Gradle is included in the repo.

```
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # launch a dev client
./gradlew build --offline  # when your dependencies are already cached
```

The first build needs network access to Fabric's Maven and Mojang's version manifest.

## Project files

- `SpecialistDanceClient.java` — entry point, detection pipeline, tick cleanup and HUD animation.
- `SpecialistDanceConfig.java` — the JSON config model.
- `mixin/ClientPacketListenerMixin.java` — reports pickups of the local player.
- `assets/specialist-dance/` — icon plus the 147 animation frames.

## Technical notes

- `handleTakeItemEntity` is hooked at `RETURN`, not `HEAD` — the method hops threads at its start, so `HEAD` would fire twice.
- An item entity's stack is only filled in by a later `SetEntityData` packet, which is why linking happens on the client tick instead of at entity spawn.
- Block drops carry a 10-tick pickup delay, so the link is always ready before you can collect them.

## Limitations

- The ore list is hardcoded in `SpecialistDanceClient.expectedDrop()` — adding ores means a code change.
- Drops knocked more than 4 blocks from the ore, or collected more than 30 s later, won't link.
- The frame count is fixed at 147 (`FRAME_COUNT`), so extra frames need a code change too.
- Association state intentionally doesn't survive a world or dimension change.

## Contributing

Bug reports, tests and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for setup, coding and testing guidelines. Release history lives in [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
