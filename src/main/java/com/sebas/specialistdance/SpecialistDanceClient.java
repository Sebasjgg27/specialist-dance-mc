package com.sebas.specialistdance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class SpecialistDanceClient implements ClientModInitializer {

    private static final List<Item> TRACKED_ITEMS = List.of(
        Items.DIAMOND,
        Items.EMERALD,
        Items.RAW_GOLD,
        Items.RAW_IRON,
        Items.GOLD_NUGGET,
        Items.ANCIENT_DEBRIS
    );

    private static final long ORE_TIMEOUT_MS = 10_000L;
    private static final long LINK_TIMEOUT_MS = 30_000L;
    private static final double ORE_SEARCH_RADIUS_SQUARE = 16.0;

    private record PendingOre(Item expectedItem, long createdAtMillis) {}

    private record DropLink(BlockPos orePos, long linkedAtMillis) {}

    private final Map<BlockPos, PendingOre> pendingOres = new HashMap<>();
    private final Map<Integer, DropLink> dropLinks = new HashMap<>();
    private final Map<Integer, Long> pendingDrops = new HashMap<>();
    private final Set<BlockPos> triggeredOres = new HashSet<>();

    private ClientLevel lastLevel;

    private static SpecialistDanceClient instance;

    private long animationStartMillis = -1;

    private static final int FRAME_COUNT = 147;
    private static final int FRAME_DURATION_MS = 50;
    private static final int DRAW_SIZE = 160;

    public static SpecialistDanceClient getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        System.out.println("Specialist Dance is Ready!");

        ClientPlayerBlockBreakEvents.AFTER.register(
            (world, player, pos, state) -> {
                if (player != Minecraft.getInstance().player) {
                    return;
                }

                Item expectedItem = expectedDrop(state.getBlock());

                if (expectedItem == null) {
                    return;
                }

                BlockPos orePos = pos.immutable();

                triggeredOres.remove(orePos);
                pendingOres.put(
                    orePos,
                    new PendingOre(expectedItem, System.currentTimeMillis())
                );

                System.out.println(
                    "Specialist Dance: registered ore "
                    + state.getBlock()
                    + " -> " + expectedItem
                    + " at " + orePos
                );
            }
        );

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity itemEntity) {
                pendingDrops.put(
                    itemEntity.getId(),
                    System.currentTimeMillis()
                );
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(
                "specialist-dance",
                "specialist_dance_overlay"
            ),
            (graphics, deltaTracker) -> {
                if (animationStartMillis < 0) {
                    return;
                }

                long elapsed = System.currentTimeMillis() - animationStartMillis;

                int frameIndex = (int) (elapsed / FRAME_DURATION_MS);

                if (frameIndex >= FRAME_COUNT) {
                    animationStartMillis = -1;
                    return;
                }

                Identifier texture = Identifier.fromNamespaceAndPath(
                    "specialist-dance",
                    "textures/gui/frames/frame_%03d.png".formatted(frameIndex)
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

    private void tick(Minecraft client) {
        if (client.level != lastLevel) {
            pendingOres.clear();
            dropLinks.clear();
            pendingDrops.clear();
            triggeredOres.clear();
            lastLevel = client.level;
            return;
        }

        long now = System.currentTimeMillis();

        pendingOres.entrySet().removeIf(
            entry -> now - entry.getValue().createdAtMillis() > ORE_TIMEOUT_MS
        );

        dropLinks.entrySet().removeIf(
            entry -> now - entry.getValue().linkedAtMillis() > LINK_TIMEOUT_MS
        );

        resolvePendingDrops(client, now);
    }

    private void resolvePendingDrops(Minecraft client, long now) {
        Iterator<Map.Entry<Integer, Long>> iterator =
            pendingDrops.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            Entity entity = client.level.getEntity(entry.getKey());

            if (!(entity instanceof ItemEntity itemEntity)) {
                iterator.remove();
                continue;
            }

            ItemStack stack = itemEntity.getItem();

            if (stack.isEmpty()) {
                if (now - entry.getValue() > 2000L) {
                    iterator.remove();
                }
                continue;
            }

            iterator.remove();

            Item droppedItem = stack.getItem();

            if (!TRACKED_ITEMS.contains(droppedItem)) {
                continue;
            }

            BlockPos sourceOre = findSourceOre(
                itemEntity.blockPosition(),
                droppedItem
            );

            if (sourceOre == null) {
                continue;
            }

            dropLinks.put(
                itemEntity.getId(),
                new DropLink(sourceOre, System.currentTimeMillis())
            );

            System.out.println(
                "Specialist Dance: linked drop "
                + droppedItem
                + " (entity " + itemEntity.getId() + ")"
                + " to ore at " + sourceOre
            );
        }
    }

    private BlockPos findSourceOre(BlockPos itemPos, Item droppedItem) {
        long now = System.currentTimeMillis();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, PendingOre> entry : pendingOres.entrySet()) {
            PendingOre pending = entry.getValue();

            if (now - pending.createdAtMillis() > ORE_TIMEOUT_MS) {
                continue;
            }

            if (pending.expectedItem() != droppedItem) {
                continue;
            }

            double distance = entry.getKey().distSqr(itemPos);

            if (distance <= ORE_SEARCH_RADIUS_SQUARE && distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }

        return best;
    }

    public void onItemPickup(int entityId) {
        DropLink link = dropLinks.get(entityId);

        if (link == null) {
            return;
        }

        if (triggeredOres.add(link.orePos())) {
            System.out.println(
                "Specialist Dance: activated for ore at " + link.orePos()
            );
            triggerDance();
        }
    }

    private Item expectedDrop(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return Items.DIAMOND;
        }
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            return Items.EMERALD;
        }
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
            return Items.RAW_GOLD;
        }
        if (block == Blocks.NETHER_GOLD_ORE) {
            return Items.GOLD_NUGGET;
        }
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            return Items.RAW_IRON;
        }
        if (block == Blocks.ANCIENT_DEBRIS) {
            return Items.ANCIENT_DEBRIS;
        }
        return null;
    }

    public void triggerDance() {
        animationStartMillis = System.currentTimeMillis();
    }
}
