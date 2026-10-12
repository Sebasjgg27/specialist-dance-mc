# Specialist Dance Minecraft Mod: Project Summary

## 1. Project Overview

- Project name: Specialist Dance
- Minecraft version: Java Edition 1.21.11
- Mod loader: Fabric (Loader 0.18.1, Fabric API 0.141.5+1.21.11)
- Language: Java 21
- Build system: Gradle 9.2.1 with Loom (declared as net.fabricmc.fabric-loom-remap 1.14.10)
- Maven group: com.sebas.specialistdance · Mod version: 1.1.0 · Archive name: specialist-dance
- Mod manifest id: specialist-dance · Environment: client

Goal: A client-side mod inspired by the Persona 4 and The Binding of Isaac "Specialist Dance" meme. When the player picks up a valuable item (diamond, emerald, gold, iron, netherite — ingots and blocks), a frame-by-frame dance animation is drawn as a HUD overlay in the center of the screen.

## 2. Current Features

- Valuable-item detection: the client counts each tracked item in the player's inventory every tick and triggers the animation when any tracked count increases.
- Tracked items: diamond, emerald, gold ingot, iron ingot, netherite ingot, and the block variants of each.
- Dance animation: a 147-frame PNG sequence played back as a HUD overlay (centered, 160×160 px, 50 ms per frame).
- Configurable-in-code constants: `TRACKED_ITEMS`, `FRAME_COUNT`, `FRAME_DURATION_MS`, and `DRAW_SIZE` in `SpecialistDanceClient`.

## 3. Current Project Structure

Actual layout (verified):

```text
specialist-dance-mc/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/ (gradle-wrapper.jar, gradle-wrapper.properties)
├── .gitignore
├── LICENSE
├── README.md
├── extract_gif.py        (helper, gitignored — extracts GIF frames to PNG)
├── src/main/
│   ├── java/com/sebas/specialistdance/SpecialistDanceClient.java
│   └── resources/
│       ├── fabric.mod.json
│       └── assets/specialist-dance/
│           └── textures/gui/frames/frame_000.png … frame_146.png (147 frames)
├── build/   (generated, untracked)
├── bin/     (VS Code build output, untracked)
├── .gradle/ (generated, untracked)
└── run/     (generated dev-run data, untracked)
```

The code loads textures from the `textures/gui/frames/` path via `Identifier.fromNamespaceAndPath("specialist-dance", "textures/gui/frames/frame_XXX.png")`, which is the only frame location in the project.

## 4. Current Implementation

### Java entry point

`src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java`

```java
package com.sebas.specialistdance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SpecialistDanceClient implements ClientModInitializer {

    private static final List<Item> TRACKED_ITEMS = List.of(
        Items.DIAMOND,
        Items.EMERALD,
        Items.GOLD_INGOT,
        Items.IRON_INGOT,
        Items.NETHERITE_INGOT,
        Items.DIAMOND_BLOCK,
        Items.EMERALD_BLOCK,
        Items.GOLD_BLOCK,
        Items.IRON_BLOCK,
        Items.NETHERITE_BLOCK
    );

    private final Map<Item, Integer> previousCounts = new HashMap<>();
    private long animationStartMillis = -1;

    private static final int FRAME_COUNT = 147;
    private static final int FRAME_DURATION_MS = 50;
    private static final int DRAW_SIZE = 160;

    @Override
    public void onInitializeClient() {
        System.out.println("Specialist Dance is Ready!");

        ClientTickEvents.END_CLIENT_TICK.register(this::checkForValuableItems);

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(
                "specialist-dance",
                "specialist_dance_overlay"
            ),
            (graphics, deltaTracker) -> {
                if (animationStartMillis < 0) {
                    return;
                }

                long elapsed = System.currentTimeMillis()
                    - animationStartMillis;

                int frameIndex = (int) (elapsed / FRAME_DURATION_MS);

                if (frameIndex >= FRAME_COUNT) {
                    animationStartMillis = -1;
                    return;
                }

                Identifier texture = Identifier.fromNamespaceAndPath(
                        "specialist-dance",
                        "textures/gui/frames/frame_%03d.png"
                            .formatted(frameIndex)
                    );

                int x = (graphics.guiWidth() - DRAW_SIZE) / 2;
                int y = (graphics.guiHeight() - DRAW_SIZE) / 2;

                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    x, y, 0.0f, 0.0f,
                    DRAW_SIZE, DRAW_SIZE, DRAW_SIZE, DRAW_SIZE
                );
            }
        );
    }

    private void checkForValuableItems(Minecraft client) {
        if (client.player == null || client.level == null) {
            previousCounts.clear();
            return;
        }

        Map<Item, Integer> currentCounts = new HashMap<>();

        for (Item item : TRACKED_ITEMS) {
            currentCounts.put(item, 0);
        }

        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            Item item = stack.getItem();

            if (currentCounts.containsKey(item)) {
                currentCounts.merge(item, stack.getCount(), Integer::sum);
            }
        }

        boolean gainedValuableItem = false;

        for (Item item : TRACKED_ITEMS) {
            int previousCount = previousCounts.getOrDefault(item, 0);
            int currentCount = currentCounts.getOrDefault(item, 0);

            if (currentCount > previousCount) {
                gainedValuableItem = true;
                break;
            }
        }

        if (gainedValuableItem) {
            animationStartMillis = System.currentTimeMillis();
            previousCounts.clear();
            previousCounts.putAll(currentCounts);
        }
    }
}
```

### How it works

- `onInitializeClient()` registers a client tick callback and a HUD render callback.
- `checkForValuableItems()` runs every client tick, counts each tracked item in the inventory, and starts the animation when any tracked total increases. The baseline is reset when no player/level is present.
- The HUD element computes the current frame from elapsed time and blits the corresponding PNG centered on screen, then stops once `FRAME_COUNT` is exceeded.

### Mod manifest

`src/main/resources/fabric.mod.json`

- id: specialist-dance
- environment: client
- entrypoint (client): com.sebas.specialistdance.SpecialistDanceClient
- depends: fabricloader >=0.18.1, minecraft 1.21.11 (pinned exact), java >=21, fabric-api *

## 5. Asset Pipeline

`extract_gif.py` (gitignored helper) reads `~/Desktop/specialist.gif` and writes individual RGBA frames to `~/Desktop/specialist_frames/` as `frame_000.png`, `frame_001.png`, etc. Those frames are then copied into the mod's asset directory. The animation currently contains 147 frames.

## 6. Development Progress

### Task Status

- Install and configure Java 21 — Completed
- Download Gradle 9.2.1 / generate wrapper — Completed
- Configure Loom — Completed
- Organize source and resource directories — Completed
- Correct the Fabric initializer import / package declaration — Completed
- Compile the project (clean build) — Completed
- Diagnose client crash (ClassNotFoundException) — Completed (fixed)
- Detecting diamonds — Completed
- Rendering a dancing animation — Completed
- In-game verification on a real dev client — Pending
- Additional trigger items (gold, emeralds, iron, netherite, etc.) — Completed
- In-game config (duration/size/position/cooldown) — Not implemented
- Character/config options — Not implemented

## 7. Known Issues / Notes

- The animation triggers on a tracked-item count increase; dropping and re-picking up a tracked item will re-trigger it.
- The tracked item set is hardcoded in the `TRACKED_ITEMS` list and the inventory is read every tick.

## 8. Immediate Next Steps

1. Run `./gradlew clean build` and confirm `BUILD SUCCESSFUL`.
2. Run `./gradlew runClient`, confirm the client reaches the main menu, and verify `run/logs/latest.log` contains `Specialist Dance is Ready!`.
3. Test in-game: obtain a tracked item and confirm the dance animation plays centered on screen.
4. Make the tracked item set user-configurable instead of the hardcoded `TRACKED_ITEMS` list.
5. Add animation controls (duration, size, position, cooldown) and a config file.
6. Add optional sound and additional characters/animations.

## 9. Learning Approach

Build manually as a learning exercise, work in small testable steps, and verify each feature in the dev client before moving on.

## 10. Overall Status

The mod now detects multiple valuable items (diamond, emerald, gold, iron, netherite — ingots and blocks) and plays a 147-frame dance animation HUD overlay. It compiles into a jar (`build/libs/specialist-dance-1.1.0.jar`). Remaining work is in-game verification plus making the trigger set configurable and adding config options.
