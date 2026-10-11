# Specialist Dance Minecraft Mod: Project Summary

## 1. Project Overview

- Project name: Specialist Dance
- Minecraft version: Java Edition 1.21.11
- Mod loader: Fabric (Loader 0.18.1, Fabric API 0.141.5+1.21.11)
- Language: Java 21
- Build system: Gradle 9.2.1 with Loom (declared as net.fabricmc.fabric-loom-remap 1.14.10)
- Maven group: com.sebas.specialistdance · Mod version: 1.0.0 · Archive name: specialist-dance
- Mod manifest id: specialist-dance · Environment: client

Goal: A client-side mod inspired by the Persona 4 "Specialist Dance" meme. When the player picks up a valuable item (starting with diamonds), a frame-by-frame dance animation is drawn as a HUD overlay in the center of the screen.

## 2. Current Features

- Diamond detection: the client counts diamonds in the player's inventory every tick and triggers the animation when the count increases.
- Dance animation: a 147-frame PNG sequence played back as a HUD overlay (centered, 160×160 px, 50 ms per frame).
- Configurable-in-code constants: `FRAME_COUNT`, `FRAME_DURATION_MS`, and `DRAW_SIZE` in `SpecialistDanceClient`.

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

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SpecialistDanceClient implements ClientModInitializer {

    private int previousDiamondCount = -1;
    private long animationStartMillis = -1;

    private static final int FRAME_COUNT = 147;
    private static final int FRAME_DURATION_MS = 50;
    private static final int DRAW_SIZE = 160;

    @Override
    public void onInitializeClient() {
        System.out.println("Specialist Dance is Ready!");

        ClientTickEvents.END_CLIENT_TICK.register(this::checkForDiamonds);

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

    private void checkForDiamonds(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        int currentDiamondCount = 0;

        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = client.player.getInventory().getItem(i);

            if (stack.is(Items.DIAMOND)) {
                currentDiamondCount += stack.getCount();
            }
        }

        if (previousDiamondCount == -1) {
            previousDiamondCount = currentDiamondCount;
            return;
        }

        if (currentDiamondCount > previousDiamondCount) {
            animationStartMillis = System.currentTimeMillis();
        }

        previousDiamondCount = currentDiamondCount;
    }
}
```

### How it works

- `onInitializeClient()` registers a client tick callback and a HUD render callback.
- `checkForDiamonds()` runs every client tick, sums all diamond stacks in the inventory, and starts the animation when the total increases. The first run just records the baseline (`previousDiamondCount == -1`).
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
- Additional trigger items (gold, emeralds, iron, netherite, etc.) — Not implemented
- In-game config (duration/size/position/cooldown) — Not implemented
- Character/config options — Not implemented

## 7. Known Issues / Notes

- The animation triggers on a diamond count increase; dropping and re-picking up diamonds will re-trigger it.
- Item detection is hardcoded to `Items.DIAMOND` and reads the inventory every tick.

## 8. Immediate Next Steps

1. Run `./gradlew clean build` and confirm `BUILD SUCCESSFUL`.
2. Run `./gradlew runClient`, confirm the client reaches the main menu, and verify `run/logs/latest.log` contains `Specialist Dance is Ready!`.
3. Test in-game: obtain a diamond and confirm the dance animation plays centered on screen.
4. Generalize detection into a configurable item set instead of the hardcoded diamond.
5. Add animation controls (duration, size, position, cooldown) and a config file.
6. Add optional sound and additional characters/animations.

## 9. Learning Approach

Build manually as a learning exercise, work in small testable steps, and verify each feature in the dev client before moving on.

## 10. Overall Status

The mod now detects diamonds and plays a 147-frame dance animation HUD overlay. It compiles into a jar (`build/libs/specialist-dance-1.0.0.jar`). Remaining work is in-game verification plus generalizing the trigger to more items and adding config options.
