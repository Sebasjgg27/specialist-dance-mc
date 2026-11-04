package com.sebas.specialistdance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.sebas.specialistdance.audio.MusicManager;
import com.sebas.specialistdance.config.SpecialistDanceConfig;
import com.sebas.specialistdance.config.menu.DanceLayoutScreen;
import com.sebas.specialistdance.config.menu.SpecialistDanceConfigScreen;
import com.sebas.specialistdance.dance.DanceRegistry;
import com.sebas.specialistdance.dance.DanceTextures;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import org.lwjgl.glfw.GLFW;

public class SpecialistDanceClient implements ClientModInitializer {

    private SpecialistDanceConfig config;
    private MusicManager musicManager;
    private DanceRegistry danceRegistry;
    private final DanceTextures danceTextures = new DanceTextures();
    private KeyMapping configKey;
    private long lastTriggerMillis = -1;

    private record PendingOre(Item expectedItem, long createdAtMillis) {}

    private record DropLink(@Nullable BlockPos orePos, long linkedAtMillis) {}

    private final Map<@NonNull BlockPos, PendingOre> pendingOres = new HashMap<>();
    private final Map<Integer, DropLink> dropLinks = new HashMap<>();
    private final Map<Integer, Long> pendingDrops = new HashMap<>();
    private final Map<@NonNull BlockPos, Long> triggeredOres = new HashMap<>();

    private ClientLevel lastLevel;

    private static SpecialistDanceClient instance;

    private long animationStartMillis = -1;
    private boolean musicDrivesAnimationEnd;
    private DanceRegistry.Dance playingDance;
    private final Deque<String> pendingLayout = new ArrayDeque<>();

    public static SpecialistDanceClient getInstance() {
        return instance;
    }

    public SpecialistDanceConfig getConfig() {
        return config;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public DanceRegistry getDanceRegistry() {
        return danceRegistry;
    }

    public DanceTextures getDanceTextures() {
        return danceTextures;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = SpecialistDanceConfig.load();
        danceRegistry = new DanceRegistry(config);
        queueLayoutFor(danceRegistry.scan());

        System.out.println("Specialist Dance is Ready!");

        if(!config.enabled) {
            System.out.println("Specialist Dance: disabled via config");
            return;
        }

        musicManager = new MusicManager(config);
        musicManager.init();

        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.specialist-dance.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.MISC
        ));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            musicManager.shutdown();
            danceTextures.release(client);
        });

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

                log(
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
                if (animationStartMillis < 0 || playingDance == null) {
                    return;
                }

                long elapsed = System.currentTimeMillis() - animationStartMillis;

                DanceRegistry.Dance dance = playingDance;

                if (dance.frameCount() <= 0) {
                    endAnimation("invalid pack " + dance.name());
                    return;
                }

                int frameDurationMs = effectiveFrameDurationMs(dance);
                int frameIndex = (int) (elapsed / frameDurationMs);

                String behavior = musicDrivesAnimationEnd ? config.animationEndBehavior : "stop";

                if ("loop".equals(behavior)) {
                    if (!musicManager.isPlaying()) {
                        endAnimation("music ended while looping");
                        return;
                    }
                    frameIndex = frameIndex % dance.frameCount();
                } else if ("hold".equals(behavior)) {
                    if (!musicManager.isPlaying()) {
                        endAnimation("music ended while holding");
                        return;
                    }
                    if (frameIndex >= dance.frameCount()) {
                        frameIndex = dance.frameCount() - 1;
                    }
                } else {
                    if (frameIndex >= dance.frameCount()) {
                        endAnimation("one pass complete");
                        return;
                    }
                }

                DanceTextures.Frame frame = danceTextures.get(
                    Minecraft.getInstance(),
                    dance,
                    frameIndex
                );

                if (frame == null) {
                    log("Specialist Dance: missing frame " + frameIndex + " of dance " + dance.name());
                    endAnimation("missing frame " + frameIndex);
                    return;
                }

                DanceTextures.Placement placement = DanceTextures.place(
                    frame,
                    config.stageWidthPercent,
                    config.stageHeightPercent,
                    config.positionX,
                    config.positionY,
                    graphics.guiWidth(),
                    graphics.guiHeight()
                );

                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    frame.texture(),
                    placement.x(),
                    placement.y(),
                    0.0f,
                    0.0f,
                    placement.width(),
                    placement.height(),
                    frame.width(),
                    frame.height(),
                    frame.width(),
                    frame.height()
                );
            }
        );
    }

    private void log(String message) {
        if (config.debug) {
            System.out.println(message);
        }
    }

    public int effectiveFrameDurationMs(DanceRegistry.Dance dance) {
        double speedMultiplier = dance.speedMultiplier(config.animationSpeed);
        return Math.max(1, Math.round((float) (dance.frameDurationMs(config.frameDurationMs) / speedMultiplier)));
    }

    private void endAnimation(String reason) {
        if (animationStartMillis < 0) {
            return;
        }

        log("Specialist Dance: animation ended (" + reason + ")"
            + " dance=" + (playingDance == null ? "?" : playingDance.name())
            + " behavior=" + config.animationEndBehavior
            + " musicEnabled=" + config.musicEnabled
            + " drivesEnd=" + musicDrivesAnimationEnd);
        animationStartMillis = -1;
        playingDance = null;
        musicManager.stop();
    }

    private void tick(Minecraft client) {
        if (client.level != lastLevel) {
            pendingOres.clear();
            dropLinks.clear();
            pendingDrops.clear();
            triggeredOres.clear();
            lastLevel = client.level;
            endAnimation("world change");
            return;
        }

        if (client.player != null && client.player.isDeadOrDying()) {
            endAnimation("player death");
        }

        while (configKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(
                    new SpecialistDanceConfigScreen(config, musicManager, Objects.requireNonNull(danceRegistry), client.screen)
                );
            }
        }

        openPendingLayout(client);

        long now = System.currentTimeMillis();

        pendingOres.entrySet().removeIf(
            entry -> now - entry.getValue().createdAtMillis() > config.oreTimeoutMs
        );

        dropLinks.entrySet().removeIf(
            entry -> now - entry.getValue().linkedAtMillis() > config.linkTimeoutMs
        );
        triggeredOres.entrySet().removeIf(
            entry -> now - entry.getValue() > config.linkTimeoutMs
        );

        resolvePendingDrops(client, now);
    }

    private void resolvePendingDrops(Minecraft client, long now) {
        ClientLevel level = client.level;

        if (level == null) {
            return;
        }

        Iterator<Map.Entry<Integer, Long>> iterator =
            pendingDrops.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());

            if (!(entity instanceof ItemEntity itemEntity)) {
                iterator.remove();
                continue;
            }

            ItemStack stack = itemEntity.getItem();

            if (stack.isEmpty()) {
                if (now - entry.getValue() > config.dropResolveTimeoutMs) {
                    iterator.remove();
                }
                continue;
            }

            iterator.remove();

            Item droppedItem = stack.getItem();

            BlockPos sourceOre = findSourceOre(
                itemEntity.blockPosition(),
                droppedItem
            );

            if (sourceOre == null) {
                if (isPickupTrigger(droppedItem)) {
                    long linkedAt = System.currentTimeMillis();
                    dropLinks.put(itemEntity.getId(), new DropLink(null, linkedAt));
                    log(
                        "Specialist Dance: linked drop "
                        + droppedItem
                        + " (entity " + itemEntity.getId() + ")"
                        + " as pickup-only trigger"
                    );
                }
                continue;
            }

            long linkedAt = System.currentTimeMillis();
            dropLinks.put(itemEntity.getId(), new DropLink(sourceOre, linkedAt));
            triggeredOres.computeIfPresent(sourceOre, (orePos, previous) -> linkedAt);

            log(
                "Specialist Dance: linked drop "
                + droppedItem
                + " (entity " + itemEntity.getId() + ")"
                + " to ore at " + sourceOre
            );
        }
    }

    private BlockPos findSourceOre(@NonNull BlockPos itemPos, Item droppedItem) {
        long now = System.currentTimeMillis();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, PendingOre> entry : pendingOres.entrySet()) {
            PendingOre pending = entry.getValue();

            if (now - pending.createdAtMillis() > config.oreTimeoutMs) {
                continue;
            }

            if (pending.expectedItem() != droppedItem) {
                continue;
            }

            @NonNull Vec3i orePos = Objects.requireNonNull(entry.getKey());
            @NonNull Vec3i itemPosition = itemPos;
            double distance = orePos.distSqr(itemPosition);

            if (distance <= config.searchRadiusSquared && distance < bestDistance) {
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

        if (link.orePos() == null) {
            log("Specialist Dance: activated for pickup-only trigger");
            triggerDance();
        } else if (triggeredOres.putIfAbsent(link.orePos(), System.currentTimeMillis()) == null) {
            log(
                "Specialist Dance: activated for ore at " + link.orePos()
            );
            triggerDance();
        }

        if (link.orePos() == null) {
            log("Specialist Dance: activates for pickup-only trigger");
            triggerDance();
        } else if (triggeredOres.putIfAbsent(link.orePos(), System.currentTimeMillis()) == null) {
            log("Specialist Dance: activated for ore at " + link.orePos());
            triggerDance();
        }
    }

    public void queueLayoutFor(List<String> names) {
        if (names == null) {
            return;
        }

        for (String name : names) {
            if (danceRegistry.needsLayout(name) && !pendingLayout.contains(name)) {
                pendingLayout.add(name);
            }
        }

        if (!pendingLayout.isEmpty()) {
            log("queued layout editor for " + pendingLayout);
        }
    }

    private void openPendingLayout(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }

        Screen current = client.screen;

        if (current != null && !(current instanceof SpecialistDanceConfigScreen)) {
            return;
        }

        while (!pendingLayout.isEmpty()) {
            String name = pendingLayout.poll();

            if (name == null || !danceRegistry.needsLayout(name)) {
                continue;
            }

            config.activeDance = name;
            danceRegistry.markLayoutSeen(name);
            log("opening layout editor for new dance pack " + name);
            client.setScreen(new DanceLayoutScreen(
                config,
                danceRegistry,
                danceTextures,
                current
            ));
            return;
        }
    }

    private Item expectedDrop(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);

        if (blockId == null || config.blockRules == null) {
            return null;
        }

        String oreId = blockId.toString();

        if (config.disabledBlockRules != null && config.disabledBlockRules.contains(oreId)) {
            return null;
        }

        String dropId = config.blockRules.get(oreId);

        if (dropId == null) {
            return null;
        }

        Identifier dropKey = Identifier.tryParse(dropId);

        if (dropKey == null) {
            log("Specialist Dance: ore " + oreId + " has invalid drop id " + dropId);
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(dropKey);

        if (item == null) {
            log("Specialist Dance: ore " + oreId + " points at unknown item " + dropId);
        }

        return item;
    }

    private boolean isPickupTrigger(Item item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

        if (itemId == null || config.pickupRules == null) {
            return false;
        }

        String id = itemId.toString();

        if (config.disabledPickupRules != null && config.disabledPickupRules.contains(id)) {
            return false;
        }

        return config.pickupRules.contains(id);
    }

    public void triggerDance() {
        long now = System.currentTimeMillis();

        if (config.cooldownMs > 0
                && lastTriggerMillis >= 0
                && now - lastTriggerMillis < config.cooldownMs) {
            return;
        }

        lastTriggerMillis = now;
        animationStartMillis = now;

        DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
        playingDance = dance;
        String track = config.musicEnabled
            ? musicManager.resolveTrackForDance(dance.name(), dance.track())
            : null;

        if (config.musicEnabled) {
            musicManager.play(track);
        }

        musicDrivesAnimationEnd = config.musicEnabled && musicManager.isPlaying() && !"stop".equals(config.animationEndBehavior);

        log("Specialist Dance: trigger dance=" + dance.name()
            + " frames=" + dance.frameCount()
            + " behavior=" + config.animationEndBehavior
            + " track=" + track
            + " musicEnabled=" + config.musicEnabled
            + " frameDurationMs=" + effectiveFrameDurationMs(dance)
            + " speed=" + dance.speedMultiplier(config.animationSpeed)
            + " drivesEnd=" + musicDrivesAnimationEnd);
    }
}
