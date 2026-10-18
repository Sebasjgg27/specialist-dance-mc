# Contributing to Specialist Dance

Thanks for your interest in improving Specialist Dance. This is a small, client-only Fabric mod, so the contribution process is lightweight. Bug reports, test results, documentation fixes, and pull requests are all welcome.

## Prerequisites

- JDK 21 (the build targets Java 21).
- Git.
- No separate Gradle install is needed — use the bundled wrapper (`./gradlew`).

The first build also requires network access to Fabric's Maven (`maven.fabricmc.net`) and to Mojang's version manifest host (`piston-meta.mojang.com`) so Loom can set up Minecraft. After that, `--offline` builds reuse the cache.

## Getting started

1. Fork the repository and clone your fork.
2. Create a feature branch off the default branch (for example, `feature/configurable-ores`).
3. Build with `./gradlew build`. The mod jar is produced in `build/libs/`.
4. Run the development client with `./gradlew runClient`.

If your dependencies are already cached and the network is unavailable, use `./gradlew build --offline`.

## Project layout

- `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java` — the `ClientModInitializer`; detection pipeline, tick cleanup, and HUD animation.
- `src/main/java/com/sebas/specialistdance/SpecialistDanceConfig.java` — the Gson-backed config model.
- `src/main/java/com/sebas/specialistdance/mixin/ClientPacketListenerMixin.java` — pickup detection.
- `src/main/resources/fabric.mod.json` — mod metadata and entrypoints.
- `src/main/resources/specialist-dance.client.mixins.json` — client mixin list.
- `src/main/resources/assets/specialist-dance/textures/gui/frames/` — the 147 animation frames.

## Coding guidelines

- Target Java 21 and match the existing formatting: 4-space indentation, imports grouped (Java, Fabric, Minecraft, then third-party), and the current brace style.
- Keep the mod **client-only**. Do not add server-side logic, network payloads, or anything that would require a server install.
- Do not add comments unless they clarify non-obvious behavior. Prefer clear names over comments.
- Preserve the origin-aware behavior: the dance should only trigger for a drop linked to an ore the player just broke. Do not broaden detection to chests, mob drops, trades, or pre-existing ground items.
- Use the existing `log(...)` helper for diagnostics (gated behind the config `debug` flag). Avoid leaving bare `System.out.println` calls in the hot path.

## Configuration

- Add new options as fields on `SpecialistDanceConfig` with sensible defaults. Because the class keeps a no-arg constructor, new keys are written to `config/specialist-dance.json` automatically and missing keys fall back to defaults.
- In the dev client the file is at `run/config/specialist-dance.json`; in a normal install it is `.minecraft/config/specialist-dance.json`.
- Set `"debug": true` to see `registered ore` → `linked drop` → `activated for ore` messages, which make the detection pipeline easy to trace.

## Mixin guidelines

- Inject at `RETURN` for `handleTakeItemEntity`. The method starts with a thread hop, so a `HEAD` inject runs twice (once on the Netty thread, once on the main thread); `RETURN` runs once on the main thread.
- Remember that an item entity spawned by the server has an empty stack until a later `SetEntityData` packet arrives. Do not read the stack directly in the entity-load handler.
- The project uses Mojang official mappings and modern Loom remapping, so write mixins against the named classes/methods; Loom remaps them at build time.
- Register any new mixin in `specialist-dance.client.mixins.json`.

## Testing

1. Run `./gradlew build` and confirm it reports `BUILD SUCCESSFUL`.
2. Run `./gradlew runClient` and confirm the client reaches the main menu.
3. Test in **Survival** — Creative mode spawns no drops, so detection cannot be verified there.
4. Verify the expected behavior:

- Break a tracked ore and collect the drop → the animation plays.
- Break a non-ore block → nothing.
- Pick up a tracked item from a chest, a mob drop, or off the ground → nothing.
- Mine with Fortune (multiple drops) → the ore still triggers only one dance.
- Change world/dimension → no stale trigger.

Report which steps you ran and what you observed in your pull request.

## Submitting changes

- Keep pull requests focused on a single change.
- Use clear, imperative commit messages (for example, `Make ore set configurable`).
- Do not commit generated output: `build/`, `run/`, `.gradle/`, and `bin/` are ignored and should stay out of history.
- Do not leave `"debug": true` as the default, and update `README.md` when behavior or configuration changes.
- In the pull request, describe the change, why it is needed, and the exact test steps and results.

## Reporting issues

Include the Minecraft version, the mod version, clear steps to reproduce, what you expected, what happened, and the relevant lines from `run/logs/latest.log`.
