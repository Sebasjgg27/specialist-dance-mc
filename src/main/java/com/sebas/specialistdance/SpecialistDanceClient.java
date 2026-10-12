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
    private boolean inventoryInitialized = false;
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
    
private void checkForValuableItems(Minecraft client) {
        if (client.player == null || client.level == null) {
            previousCounts.clear();
            inventoryInitialized = false;
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

    ItemStack carriedStack =client.player.containerMenu.getCarried();

    if (!carriedStack.isEmpty()) {
        Item carriedItem = carriedStack.getItem();

        if (currentCounts.containsKey(carriedItem)) {
            currentCounts.merge(
                carriedItem,
                carriedStack.getCount(),
                Integer::sum
            );
        }
    }

    // Establish the initial inventor without triggering the dance animation
    if (!inventoryInitialized) {
        previousCounts.clear();
        previousCounts.putAll(currentCounts);
        inventoryInitialized = true;
        return;
    }

    //detect an increase in the total quantity of any tracked item
    for (Item item : TRACKED_ITEMS) {
        int previousCount = previousCounts.getOrDefault(item, 0);
        int currentCount = currentCounts.getOrDefault(item, 0);

        if (currentCount > previousCount) {
            animationStartMillis = System.currentTimeMillis();
            break;
        }
    }

    //always refresh the baseline, even when items where removed
    previousCounts.clear();
    previousCounts.putAll(currentCounts);

    }
}