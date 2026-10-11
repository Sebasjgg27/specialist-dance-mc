
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
                    x,
                    y,
                    0.0f,
                    0.0f,
                    DRAW_SIZE,
                    DRAW_SIZE,
                    DRAW_SIZE,
                    DRAW_SIZE
                );
            }
        );
    }

    private void checkForDiamonds(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        int currentDiamondCount = 0;

        for (
            int i = 0;
            i < client.player.getInventory().getContainerSize();
            i++
        ) {
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
